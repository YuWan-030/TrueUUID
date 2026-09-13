#!/usr/bin/env bash
# Prepare or run one server-plugin target on loopback with its installed JAR.
# Server runtimes, worlds, and logs stay under ignored build/server-plugins storage.
set -euo pipefail

repo_root=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd -P)

usage() {
    cat <<'EOF'
Usage: scripts/run-server-plugin.sh <target> [prepare|server|paths] [TRANSPORT] [OFFLINE_CLIENT] [ADMISSION]

<target> is an id from ci/server-plugin-targets.json, for example
spigot-1.20.1 or paper-1.20.1.

TRANSPORT controls how premium users prove their account:
  AUTO (default)       Modded premium users use TrueUUID. Unmodified premium
                       users use native Mojang login. Recommended.
  CLIENT_ASSISTED      Premium users must have a matching TrueUUID client mod.
  VANILLA_HYBRID       Premium users use native Mojang login. No mod required.

OFFLINE_CLIENT independently controls which offline clients are admitted:
  ALLOW_VANILLA (default)      Modded and unmodified offline users may join.
                               Offline names are intentionally impersonable.
  REQUIRE_TRUEUUID_CLIENT      Offline users must have the TrueUUID client mod.
                               This is not password or ownership authentication.
  DENY                         All offline users are rejected. Premium users,
                               modded or not, may still join.

ADMISSION controls premium and offline name collisions:
  CONSENT_REQUIRED (default)   The first accepted identity keeps the base name.
                               Later collisions need a one-use administrator
                               approval. No silent rename occurs.
  SAFE_PARALLEL                Premium keeps the canonical name. A colliding
                               offline identity gets a stable alias such as -Name.
  PREMIUM_RESERVED             Every Mojang-existing name requires premium proof.
  FIRST_CLAIM                  UNSAFE. The first accepted identity kind owns the
                               base name and can squat a premium name.

Environment:
  TRUEUUID_SERVER_TEST_HOME           Server directory
                                      (default build/server-plugins/<target>/manual-server)
  TRUEUUID_SERVER_IP                  Bind address (default 127.0.0.1)
  TRUEUUID_SERVER_PORT                Port (default 25565)
  TRUEUUID_PROTOCOLLIB_VERSION        Verified ProtocolLib version to install
                                      (default: protocollib.runtime_version)
  TRUEUUID_SERVER_JAVA_HOME           Java runtime for the server
  TRUEUUID_SERVER_PLUGIN_AUTO_BUILD=0 Refuse instead of preparing missing artifacts
  TRUEUUID_ALLOW_REMOTE_SERVER_TEST=1 Allow a non-loopback bind

When running through this script, typing stop and pressing Ctrl+C both request
the same clean Minecraft console shutdown. Do not launch the server JAR directly.
EOF
}

die() {
    local status=$1
    shift
    printf '%s\n' "$*" >&2
    exit "$status"
}

normalize() {
    printf '%s' "$1" | tr '[:lower:]-' '[:upper:]_'
}

target_id=${1:-}
if [[ -z "$target_id" || "$target_id" == -h || "$target_id" == --help || "$target_id" == help ]]
then
    usage
    [[ -n "$target_id" ]] && exit 0
    exit 64
fi
action=${2:-server}
mode=$(normalize "${3:-AUTO}")
offline_mode=$(normalize "${4:-ALLOW_VANILLA}")
admission_mode=$(normalize "${5:-CONSENT_REQUIRED}")

[[ "$action" =~ ^(prepare|server|paths)$ ]] || {
    usage >&2
    exit 64
}
[[ "$mode" =~ ^(AUTO|CLIENT_ASSISTED|VANILLA_HYBRID)$ ]] || die 64 "Invalid transport: $mode"
[[ "$offline_mode" =~ ^(DENY|REQUIRE_TRUEUUID_CLIENT|ALLOW_VANILLA)$ ]] || die 64 "Invalid offline mode: $offline_mode"
[[ "$admission_mode" =~ ^(CONSENT_REQUIRED|SAFE_PARALLEL|PREMIUM_RESERVED|FIRST_CLAIM)$ ]] ||
    die 64 "Invalid admission mode: $admission_mode"

declare -A premium_summaries=(
    [AUTO]='premium users with or without TrueUUID may join'
    [CLIENT_ASSISTED]='premium users must have the matching TrueUUID client mod'
    [VANILLA_HYBRID]='premium users use native Mojang login and need no client mod'
)
declare -A offline_summaries=(
    [ALLOW_VANILLA]='offline users with or without TrueUUID may join under unprotected offline-name semantics'
    [REQUIRE_TRUEUUID_CLIENT]='offline users must have TrueUUID and unmodified offline clients are rejected'
    [DENY]='all offline users are rejected, verified premium users are not'
)
declare -A admission_summaries=(
    [CONSENT_REQUIRED]='first accepted identity keeps the base name and collisions require one-use admin approval'
    [SAFE_PARALLEL]='premium keeps its canonical name and colliding offline identities receive stable aliases'
    [PREMIUM_RESERVED]='Mojang-existing names are premium-only and colliding offline identities are denied'
    [FIRST_CLAIM]='UNSAFE first accepted identity kind owns the base name and can squat a premium name'
)
first_claim_risk=false
[[ "$admission_mode" == FIRST_CLAIM ]] && first_claim_risk=true

command -v jq >/dev/null 2>&1 || die 69 'jq is required'
target_json=$("$repo_root/scripts/ci/validate-server-plugin-targets.sh" --target "$target_id")
ci_script="$repo_root/$(jq -r '.ci_script' <<<"$target_json")"
platform_name=$(jq -r '.platform_name' <<<"$target_json")
game_version=$(jq -r '.game_version' <<<"$target_json")
module=$(jq -r '.module' <<<"$target_json")
engine_module=$(jq -r '.engine_module' <<<"$target_json")
expected_support_status=$(jq -r '.manifest_status' <<<"$target_json")
plugin_version=$(sed -n 's/^mod_version=//p' "$repo_root/gradle.properties" | tr -d '[:space:]')
plugin_jar="$repo_root/$(jq -r --arg version "$plugin_version" \
    '.artifact | split("%VERSION%") | join($version)' <<<"$target_json")"
module_dir="${module#:}"
module_dir="$repo_root/${module_dir//://}"
engine_dir="${engine_module#:}"
engine_dir="$repo_root/${engine_dir//://}"

manifest="$repo_root/ci/server-plugin-targets.json"
protocollib_version=${TRUEUUID_PROTOCOLLIB_VERSION:-$(jq -r '.protocollib.runtime_version' "$manifest")}
protocollib_sha256=$(jq -r --arg version "$protocollib_version" \
    '.protocollib.verified[] | select(.version == $version) | .sha256' "$manifest")
[[ "$protocollib_sha256" =~ ^[0-9a-f]{64}$ ]] ||
    die 65 "ProtocolLib $protocollib_version is not a verified version in ci/server-plugin-targets.json"
protocollib_jar="$repo_root/build/server-plugins/protocollib/ProtocolLib-$protocollib_version.jar"

server_ip=${TRUEUUID_SERVER_IP:-127.0.0.1}
server_port=${TRUEUUID_SERVER_PORT:-25565}
if [[ ! "$server_port" =~ ^[0-9]+$ ]] || (( server_port < 1024 || server_port > 65535 ))
then
    die 64 'TRUEUUID_SERVER_PORT must be between 1024 and 65535.'
fi
if [[ "$server_ip" != 127.0.0.1 && "$server_ip" != ::1 && "${TRUEUUID_ALLOW_REMOTE_SERVER_TEST:-}" != 1 ]]
then
    die 65 'Refusing a non-loopback bind without TRUEUUID_ALLOW_REMOTE_SERVER_TEST=1.'
fi
test_home=${TRUEUUID_SERVER_TEST_HOME:-$repo_root/build/server-plugins/$target_id/manual-server}
java_home=${TRUEUUID_SERVER_JAVA_HOME:-${JAVA_HOME_17_X64:-/usr/lib/jvm/jdk-17.0.12-oracle-x64}}
build_java_home=${JAVA_HOME_21_X64:-/usr/lib/jvm/java-21-openjdk-amd64}
world_name=trueuuid-test-world

if [[ "$action" == server ]]
then
    world_lock="$test_home/$world_name/session.lock"
    if [[ -e "$world_lock" ]] && command -v fuser >/dev/null 2>&1 && fuser -s "$world_lock"
    then
        holder_pids=$(fuser "$world_lock" 2>/dev/null | xargs)
        die 73 "Refusing to start a second server: the test world is in use by PID(s) ${holder_pids:-unknown}.
Return to that server console and type stop. Do not delete session.lock while it runs."
    fi
    if command -v ss >/dev/null 2>&1 && [[ -n "$(ss -H -ltn "sport = :$server_port" 2>/dev/null | head -n 1)" ]]
    then
        die 73 "Refusing to start: another process is already listening on TCP port $server_port."
    fi
fi

runtime_output=$(TRUEUUID_SERVER_PLUGIN_TARGET="$target_id" "$ci_script" prepare-runtime)
server_launch_jar=
server_home_seed=()
while IFS='=' read -r key value
do
    if [[ "$key" == server_launch_jar ]]
    then
        server_launch_jar=$value
    elif [[ "$key" == server_home_seed ]]
    then
        server_home_seed+=("$value")
    fi
done <<<"$runtime_output"
[[ -f "$server_launch_jar" ]] || die 66 "Missing $platform_name server JAR: $server_launch_jar"

if [[ ! -f "$protocollib_jar" ]]
then
    TRUEUUID_SERVER_PLUGIN_TARGET="$target_id" "$ci_script" fetch-protocollib
fi
printf '%s  %s\n' "$protocollib_sha256" "$protocollib_jar" | sha256sum --check --strict --quiet >/dev/null ||
    die 65 "ProtocolLib checksum mismatch: $protocollib_jar"

# Repackage when live source is newer than the plugin JAR. This is a JAR build
# only. The seam contract and cross-loader tests remain part of the CI build.
needs_jar=false
if [[ ! -f "$plugin_jar" ]]
then
    needs_jar=true
elif find \
        "$repo_root/settings.gradle" \
        "$repo_root/ci/server-plugin-targets.json" \
        "$repo_root/plugin/server-plugin-target.gradle" \
        "$repo_root/plugin/bukkit-common/build.gradle" \
        "$repo_root/plugin/bukkit-common/src/main" \
        "$engine_dir/build.gradle" \
        "$engine_dir/src/main" \
        "$engine_dir/src/template" \
        "$module_dir/build.gradle" \
        "$module_dir/src/main" \
        "$repo_root/shared/protocol/build.gradle" \
        "$repo_root/shared/protocol/src/main" \
        "$repo_root/shared/server-core/build.gradle" \
        "$repo_root/shared/server-core/src/main" \
        -type f -newer "$plugin_jar" -print -quit | grep -q .
then
    needs_jar=true
fi
if [[ "$needs_jar" == true ]]
then
    [[ "${TRUEUUID_SERVER_PLUGIN_AUTO_BUILD:-1}" == 1 ]] ||
        die 66 "The plugin JAR is missing or stale and TRUEUUID_SERVER_PLUGIN_AUTO_BUILD=0: $plugin_jar"
    [[ -x "$build_java_home/bin/java" ]] || die 78 "Missing Java 21 build launcher: $build_java_home"
    (
        cd "$repo_root"
        JAVA_HOME="$build_java_home" PATH="$build_java_home/bin:$PATH" \
            ./gradlew "$module:jar" --configure-on-demand --no-daemon --max-workers=2
    )
fi
[[ -f "$plugin_jar" ]] || die 66 "Missing plugin JAR: $plugin_jar"

if [[ "$action" == paths ]]
then
    printf 'server_dir=%s\nserver_jar=%s\nprotocollib_jar=%s\nplugin_jar=%s\n' \
        "$test_home" "$server_launch_jar" "$protocollib_jar" "$plugin_jar"
    exit 0
fi

[[ -x "$java_home/bin/java" ]] || die 78 "Missing Java runtime: $java_home"
command -v unzip >/dev/null 2>&1 || die 69 'unzip is required to inspect the plugin JAR.'
unzip -p "$plugin_jar" META-INF/MANIFEST.MF | tr -d '\r' |
    grep -Fxq "TrueUUID-Support-Status: $expected_support_status" ||
    die 65 "Plugin JAR does not carry the $expected_support_status marker from ci/server-plugin-targets.json."

mkdir -p "$test_home/plugins/ProtocolLib" "$test_home/plugins/TrueUUID"
install -m 600 "$server_launch_jar" "$test_home/server.jar"
for seed in "${server_home_seed[@]}"
do
    [[ -d "$seed" ]] || die 66 "Missing server runtime directory: $seed"
    rm -rf -- "${test_home:?}/$(basename -- "$seed")"
    cp -al -- "$seed" "$test_home/" 2>/dev/null || cp -a -- "$seed" "$test_home/"
done
rm -f -- "$test_home"/plugins/ProtocolLib*.jar "$test_home"/plugins/TrueUUID*.jar
install -m 600 "$protocollib_jar" "$test_home/plugins/ProtocolLib.jar"
install -m 600 "$plugin_jar" "$test_home/plugins/TrueUUID.jar"
printf 'eula=true\n' > "$test_home/eula.txt"
printf '%s\n' \
    'online-mode=false' \
    "server-ip=$server_ip" \
    "server-port=$server_port" \
    "level-name=$world_name" \
    'max-players=8' \
    'max-tick-time=-1' \
    'view-distance=3' \
    'simulation-distance=2' \
    "motd=TrueUUID $platform_name $game_version test server" \
    > "$test_home/server.properties"
printf '%s\n' \
    'authentication:' \
    "  transport: $mode" \
    '  timeout-ms: 30000' \
    '  maximum-pending-logins: 64' \
    '  custom-endpoint-allowlist: []' \
    '' \
    'admission:' \
    "  mode: $admission_mode" \
    "  offline-client: $offline_mode" \
    "  first-claim-risk-accepted: $first_claim_risk" \
    '' \
    'aliases:' \
    '  prefix: "-"' \
    '' \
    'feedback:' \
    '  private-chat: true' \
    '  vanilla-action-bar: true' \
    '  title: false' \
    '  modded-overlay: true' \
    '  vanilla-action-bar-delay-ticks: 20' \
    '' \
    'permissions:' \
    '  provider: AUTO' \
    > "$test_home/plugins/TrueUUID/config.yml"
cat > "$test_home/plugins/ProtocolLib/config.yml" <<'EOF'
global:
  # The test server installs one verified ProtocolLib release and never updates it.
  auto updater:
    notify: false
    download: false
    delay: 43200
  metrics: false
  chat warnings: true
  background compiler: true
  ignore version check:
  debug: false
  detailed error: false
  script engine: JavaScript
  suppressed reports:
EOF

{
    printf '%s  %s\n' "$(sha256sum "$test_home/server.jar" | awk '{print $1}')" 'server.jar'
    printf '%s  %s\n' "$protocollib_sha256" 'plugins/ProtocolLib.jar'
    printf '%s  %s\n' "$(sha256sum "$test_home/plugins/TrueUUID.jar" | awk '{print $1}')" 'plugins/TrueUUID.jar'
} > "$test_home/server-artifacts.sha256"
plugin_hash=$(sha256sum "$plugin_jar" | awk '{print $1}')

cat <<EOF
Prepared TrueUUID $platform_name $game_version test server:
  directory     : $test_home
  bind          : $server_ip:$server_port
  ProtocolLib   : $protocollib_version
  auth mode     : $mode
  offline mode  : $offline_mode
  admission     : $admission_mode
  plugin        : $plugin_hash
  premium users : ${premium_summaries[$mode]}
  offline users : ${offline_summaries[$offline_mode]}
  collisions    : ${admission_summaries[$admission_mode]}

Fabric $game_version premium client (separate terminal):
  scripts/test-premium-client.sh --server $server_ip:$server_port fabric-$game_version

Fabric $game_version offline client (separate terminal):
  scripts/test-premium-client.sh --server $server_ip:$server_port --offline-name TUOff8472Qz fabric-$game_version

Unmodified premium client:
  Start Minecraft $game_version without TrueUUID and connect to $server_ip:$server_port

Successful authentication emits one of:
  TrueUUID login_complete outcome=PREMIUM_VERIFIED
  TrueUUID login_complete outcome=OFFLINE_FALLBACK

Clean shutdown: type stop or press Ctrl+C once.
EOF

if [[ "$action" == prepare ]]
then
    exit 0
fi

cd "$test_home"
command -v setsid >/dev/null 2>&1 || die 69 'setsid is required for safe console signal handling.'

# A server SIGINT shutdown hook can race the live server thread and wedge in
# native world shutdown. Keep Java in a separate session and relay console
# input through a private FIFO. Ctrl+C, TERM, and HUP become the ordinary
# console stop command, so the server thread owns shutdown and world saving.
console_dir=$(mktemp -d "$test_home/.trueuuid-console.XXXXXX")
console_fifo="$console_dir/stdin"
mkfifo -m 600 "$console_fifo"
exec 3<>"$console_fifo"
server_pid=
stop_requested=0

cleanup_console() {
    exec 3>&- || true
    if [[ -p "$console_fifo" ]]
    then
        rm -f -- "$console_fifo"
    fi
    rmdir -- "$console_dir" 2>/dev/null || true
}

request_clean_stop() {
    if [[ -z "$server_pid" ]] || ! kill -0 "$server_pid" 2>/dev/null
    then
        return
    fi
    if [[ "$stop_requested" == 0 ]]
    then
        stop_requested=1
        printf '\nSignal received. Requesting a clean Minecraft console stop...\n' >&2
        printf 'stop\n' >&3 || true
    else
        printf '\nClean shutdown already requested. Still waiting for Minecraft to exit...\n' >&2
    fi
}

trap cleanup_console EXIT
trap request_clean_stop INT TERM HUP

# The startup delay for old builds is a reminder to update production servers.
# A pinned, loopback-only test server skips it so CI timing stays deterministic.
setsid "$java_home/bin/java" -Xms512M -Xmx1G -DIReallyKnowWhatIAmDoingISwear=true \
    -jar server.jar nogui <&3 3>&- &
server_pid=$!

# Keep the wrapper in the foreground so console input remains usable. Every
# command in this loop is conditional or has an explicit fallback, so a trapped
# signal cannot escape through set -e and detach the still-running JVM.
while kill -0 "$server_pid" 2>/dev/null
do
    if IFS= read -r -t 0.25 console_line
    then
        if [[ "${console_line,,}" == stop ]]
        then
            stop_requested=1
        fi
        printf '%s\n' "$console_line" >&3 || true
    else
        # Avoid an EOF spin when a non-interactive caller closes stdin.
        sleep 0.05 || true
    fi
done

server_status=0
while true
do
    waited_status=0
    wait "$server_pid" || waited_status=$?
    if kill -0 "$server_pid" 2>/dev/null
    then
        continue
    fi
    server_status=$waited_status
    break
done

trap - INT TERM HUP
if [[ "$server_status" == 0 ]]
then
    printf 'Server exited cleanly.\n'
else
    printf 'Server exited with status %s.\n' "$server_status" >&2
fi
exit "$server_status"
