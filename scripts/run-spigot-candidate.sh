#!/usr/bin/env bash
# Prepare or run the exact unsupported Spigot 1.20.1 candidate on loopback.
# BuildTools output and server state remain under ignored build storage.
set -euo pipefail

repo_root=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd -P)
action=${1:-server}
mode=${2:-AUTO}
mode=$(printf '%s' "$mode" | tr '[:lower:]-' '[:upper:]_')
offline_mode=${3:-${TRUEUUID_SPIGOT_OFFLINE_MODE:-ALLOW_VANILLA}}
offline_mode=$(printf '%s' "$offline_mode" | tr '[:lower:]-' '[:upper:]_')
admission_mode=${4:-${TRUEUUID_SPIGOT_ADMISSION_MODE:-CONSENT_REQUIRED}}
admission_mode=$(printf '%s' "$admission_mode" | tr '[:lower:]-' '[:upper:]_')

usage() {
    cat <<'EOF'
Usage: scripts/run-spigot-candidate.sh [prepare|server|paths] [TRANSPORT] [OFFLINE_CLIENT] [ADMISSION]

TRANSPORT controls how premium users prove their account:
  AUTO (default)       Modded premium users use TrueUUID; unmodified premium
                       users use native Mojang login. Recommended.
  CLIENT_ASSISTED      Premium users must have a matching TrueUUID client mod.
  VANILLA_HYBRID       Premium users use native Mojang login; no mod required.

OFFLINE_CLIENT independently controls which offline clients are admitted:
  ALLOW_VANILLA (default)      Modded and unmodified offline users may join.
                               Offline names are intentionally impersonable.
  REQUIRE_TRUEUUID_CLIENT      Offline users must have the TrueUUID client mod;
                               unmodified offline clients are rejected. This is
                               not password or ownership authentication.
  DENY                         All offline users are rejected. With TRANSPORT=AUTO,
                               premium modded and unmodified users may still join.

ADMISSION controls premium/offline name collisions:
  CONSENT_REQUIRED (default)
                           The first accepted identity keeps the base name.
                           Later premium/offline collisions need a one-use
                           administrator approval; no silent rename occurs.
  SAFE_PARALLEL            Premium keeps the canonical name. A colliding
                           offline identity gets a stable server alias such as
                           -Name, so both can play. A short hash is added only
                           if that readable alias is already occupied.
  PREMIUM_RESERVED        Every Mojang-existing name requires premium proof;
                           colliding offline users are rejected.
  FIRST_CLAIM             First accepted identity kind owns the base name.
                           UNSAFE: permits local premium-name squatting.

Environment:
  TRUEUUID_SPIGOT_TEST_HOME     Ignored server directory
  TRUEUUID_SPIGOT_SERVER_IP     Bind address (default 127.0.0.1)
  TRUEUUID_SPIGOT_SERVER_PORT   Port (default 25565)
  TRUEUUID_SPIGOT_JAR           Exact BuildTools output override
  TRUEUUID_PROTOCOLLIB_JAR      Exact ProtocolLib JAR override
  TRUEUUID_SPIGOT_JAVA_HOME     Java 17 runtime
  TRUEUUID_SPIGOT_OFFLINE_MODE  Default OFFLINE_CLIENT when argument 3 is absent
  TRUEUUID_SPIGOT_ADMISSION_MODE Default ADMISSION when argument 4 is absent
  TRUEUUID_SPIGOT_AUTO_BUILD=0  Refuse instead of building missing artifacts

The server binds loopback unless TRUEUUID_ALLOW_REMOTE_SPIGOT_TEST=1 is set.
This remains an unsupported candidate and must not be exposed publicly.
When running through this script, `stop` and Ctrl+C both request the same clean
Minecraft console shutdown. Do not launch spigot.jar directly.
EOF
}

case "$action" in
    prepare|server|paths) ;;
    -h|--help|help) usage; exit 0 ;;
    *) usage >&2; exit 64 ;;
esac
case "$mode" in
    AUTO|CLIENT_ASSISTED|VANILLA_HYBRID) ;;
    *) echo "Invalid mode: $mode" >&2; usage >&2; exit 64 ;;
esac
case "$offline_mode" in
    DENY|REQUIRE_TRUEUUID_CLIENT|ALLOW_VANILLA) ;;
    *) echo "Invalid offline mode: $offline_mode" >&2; usage >&2; exit 64 ;;
esac
case "$admission_mode" in
    CONSENT_REQUIRED|SAFE_PARALLEL|PREMIUM_RESERVED|FIRST_CLAIM) ;;
    *) echo "Invalid admission mode: $admission_mode" >&2; usage >&2; exit 64 ;;
esac

case "$mode" in
    AUTO)
        premium_summary='premium users with or without TrueUUID may join'
        ;;
    CLIENT_ASSISTED)
        premium_summary='premium users must have the matching TrueUUID client mod'
        ;;
    VANILLA_HYBRID)
        premium_summary='premium users use native Mojang login; no client mod is required'
        ;;
esac
case "$admission_mode" in
    CONSENT_REQUIRED)
        admission_summary='first accepted identity keeps the base name; collisions require one-use admin approval'
        first_claim_risk=false
        ;;
    SAFE_PARALLEL)
        admission_summary='premium keeps its canonical name; colliding offline identities receive stable - aliases'
        first_claim_risk=false
        ;;
    PREMIUM_RESERVED)
        admission_summary='Mojang-existing names are premium-only; colliding offline identities are denied'
        first_claim_risk=false
        ;;
    FIRST_CLAIM)
        admission_summary='UNSAFE first accepted identity kind owns the base name and can squat a premium name'
        first_claim_risk=true
        ;;
esac
case "$offline_mode" in
    ALLOW_VANILLA)
        offline_summary='offline users with or without TrueUUID may join under unprotected offline-name semantics'
        ;;
    REQUIRE_TRUEUUID_CLIENT)
        offline_summary='offline users must have TrueUUID; unmodified offline clients are rejected'
        ;;
    DENY)
        offline_summary='all offline users are rejected; this does not reject verified premium users'
        ;;
esac

server_ip=${TRUEUUID_SPIGOT_SERVER_IP:-127.0.0.1}
server_port=${TRUEUUID_SPIGOT_SERVER_PORT:-25565}
if [[ ! "$server_port" =~ ^[0-9]+$ ]] || ((server_port < 1024 || server_port > 65535)); then
    echo "TRUEUUID_SPIGOT_SERVER_PORT must be between 1024 and 65535." >&2
    exit 64
fi
if [[ "$server_ip" != 127.0.0.1 && "$server_ip" != ::1
        && "${TRUEUUID_ALLOW_REMOTE_SPIGOT_TEST:-}" != 1 ]]; then
    echo "Refusing non-loopback Spigot candidate bind without TRUEUUID_ALLOW_REMOTE_SPIGOT_TEST=1." >&2
    exit 65
fi

test_home=${TRUEUUID_SPIGOT_TEST_HOME:-$repo_root/build/spigot-candidate/manual-server}
spigot_jar=${TRUEUUID_SPIGOT_JAR:-$repo_root/build/spigot-candidate/output/spigot-1.20.1.jar}
protocol_jar=${TRUEUUID_PROTOCOLLIB_JAR:-$repo_root/plugin/spigot/1.20.1/build/pinned/ProtocolLib-5.1.0.jar}
candidate_jar=$repo_root/plugin/spigot/1.20.1/build/libs/trueuuid-spigot-1.20.1-candidate.jar
java17_home=${TRUEUUID_SPIGOT_JAVA_HOME:-${JAVA_HOME_17_X64:-/usr/lib/jvm/jdk-17.0.12-oracle-x64}}
java21_home=${TRUEUUID_SPIGOT_BUILD_JAVA_HOME:-${JAVA_HOME_21_X64:-/usr/lib/jvm/java-21-openjdk-amd64}}
protocol_sha256=562c3ef79391e25f71b23359adb6becae7bcee36b0dfe2621b2c679013116769

if [[ "$action" == paths ]]; then
    printf 'server_dir=%s\nspigot_jar=%s\nprotocol_jar=%s\ncandidate_jar=%s\n' \
        "$test_home" "$spigot_jar" "$protocol_jar" "$candidate_jar"
    exit 0
fi

if [[ "$action" == server ]]; then
    world_lock="$test_home/trueuuid-spigot-test-world/session.lock"
    if [[ -e "$world_lock" ]] && command -v fuser >/dev/null 2>&1 \
            && fuser -s "$world_lock"; then
        holder_pids=$(fuser "$world_lock" 2>/dev/null | xargs)
        cat >&2 <<EOF
Refusing to start a second server: the test world is already in use.
  world : $test_home/trueuuid-spigot-test-world
  PID(s): ${holder_pids:-unknown}

Return to the existing server console and type "stop", then run this command
again. Do not delete session.lock while that process is running.
EOF
        exit 73
    fi
    if command -v ss >/dev/null 2>&1 \
            && [[ -n "$(ss -H -ltn "sport = :$server_port" 2>/dev/null | head -n 1)" ]]; then
        cat >&2 <<EOF
Refusing to start: another process is already listening on TCP port $server_port.
Stop that process cleanly or choose TRUEUUID_SPIGOT_SERVER_PORT with a free port.
EOF
        exit 73
    fi
fi

if [[ ! -f "$spigot_jar" || ! -f "$protocol_jar" || ! -f "$candidate_jar" ]]; then
    if [[ "${TRUEUUID_SPIGOT_AUTO_BUILD:-1}" != 1 ]]; then
        echo "Pinned candidate artifacts are missing. Run scripts/ci/build-spigot-candidate.sh first." >&2
        exit 66
    fi
    "$repo_root/scripts/ci/build-spigot-candidate.sh"
fi

# Repackage when live source is newer than the candidate. This is intentionally
# a JAR build only; the exact-runtime test remains part of the pinned CI build.
if [[ -f "$candidate_jar" ]] && find \
        "$repo_root/settings.gradle" \
        "$repo_root/plugin/bukkit-common/build.gradle" \
        "$repo_root/plugin/bukkit-common/src/main" \
        "$repo_root/plugin/spigot/1.20.1/build.gradle" \
        "$repo_root/plugin/spigot/1.20.1/src/main" \
        "$repo_root/shared/protocol/build.gradle" \
        "$repo_root/shared/protocol/src/main" \
        "$repo_root/shared/server-core/build.gradle" \
        "$repo_root/shared/server-core/src/main" \
        -type f -newer "$candidate_jar" -print -quit | grep -q .; then
    [[ -x "$java21_home/bin/java" ]] || { echo "Missing Java 21 build launcher: $java21_home" >&2; exit 78; }
    JAVA_HOME="$java21_home" PATH="$java21_home/bin:$PATH" \
        "$repo_root/gradlew" :plugin:spigot:1.20.1:jar \
        --configure-on-demand --no-daemon --max-workers=2
fi

[[ -f "$spigot_jar" ]] || { echo "Missing exact Spigot JAR: $spigot_jar" >&2; exit 66; }
[[ -f "$protocol_jar" ]] || { echo "Missing ProtocolLib JAR: $protocol_jar" >&2; exit 66; }
[[ -f "$candidate_jar" ]] || { echo "Missing candidate JAR: $candidate_jar" >&2; exit 66; }
[[ -x "$java17_home/bin/java" ]] || { echo "Missing Java 17 runtime: $java17_home" >&2; exit 78; }
command -v unzip >/dev/null 2>&1 || { echo "unzip is required to inspect the candidate JAR." >&2; exit 69; }

printf '%s  %s\n' "$protocol_sha256" "$protocol_jar" | sha256sum --check --strict
candidate_manifest=$(unzip -p "$candidate_jar" META-INF/MANIFEST.MF)
if ! grep -Fq 'TrueUUID-Support-Status: UNSUPPORTED-CANDIDATE' <<<"$candidate_manifest"; then
    echo "Candidate JAR does not carry the required unsupported-candidate manifest." >&2
    exit 65
fi

mkdir -p "$test_home/plugins/ProtocolLib" "$test_home/plugins/TrueUUID"
install -m 600 "$spigot_jar" "$test_home/spigot.jar"
install -m 600 "$protocol_jar" "$test_home/plugins/ProtocolLib.jar"
install -m 600 "$candidate_jar" "$test_home/plugins/TrueUUID.jar"
printf 'eula=true\n' > "$test_home/eula.txt"
printf '%s\n' \
    'online-mode=false' \
    "server-ip=$server_ip" \
    "server-port=$server_port" \
    'level-name=trueuuid-spigot-test-world' \
    'max-players=8' \
    'max-tick-time=-1' \
    'view-distance=3' \
    'simulation-distance=2' \
    'motd=TrueUUID unsupported Spigot 1.20.1 candidate' \
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
  # The candidate deliberately remains pinned to audited ProtocolLib 5.1.0.
  auto updater:
    notify: false
    download: false
    delay: 43200
  metrics: true
  chat warnings: true
  background compiler: true
  ignore version check:
  debug: false
  detailed error: false
  script engine: JavaScript
  suppressed reports:
EOF

spigot_hash=$(sha256sum "$test_home/spigot.jar" | awk '{print $1}')
candidate_hash=$(sha256sum "$test_home/plugins/TrueUUID.jar" | awk '{print $1}')
printf '%s  %s\n' "$spigot_hash" 'spigot.jar' > "$test_home/candidate-artifacts.sha256"
printf '%s  %s\n' "$protocol_sha256" 'plugins/ProtocolLib.jar' >> "$test_home/candidate-artifacts.sha256"
printf '%s  %s\n' "$candidate_hash" 'plugins/TrueUUID.jar' >> "$test_home/candidate-artifacts.sha256"

cat <<EOF
Prepared unsupported Spigot 1.20.1 candidate:
  directory : $test_home
  bind      : $server_ip:$server_port
  auth mode     : $mode
  offline mode  : $offline_mode
  admission     : $admission_mode
  plugin        : $candidate_hash
  premium users : $premium_summary
  offline users : $offline_summary
  collisions    : $admission_summary

Fabric 1.20.1 premium client (separate terminal):
  scripts/test-premium-client.sh --server $server_ip:$server_port fabric-1.20.1

Fabric 1.20.1 offline client (separate terminal):
  scripts/test-premium-client.sh --server $server_ip:$server_port --offline-name TUOff8472Qz fabric-1.20.1

Unmodified premium client:
  Start Minecraft 1.20.1 without TrueUUID and connect to $server_ip:$server_port

Successful authentication emits one of:
  TrueUUID login_complete outcome=PREMIUM_VERIFIED
  TrueUUID login_complete outcome=OFFLINE_FALLBACK

Clean shutdown: type stop or press Ctrl+C once.
EOF

if [[ "$action" == prepare ]]; then
    exit 0
fi

cd "$test_home"
command -v setsid >/dev/null 2>&1 || { echo "setsid is required for safe console signal handling." >&2; exit 69; }

# Spigot's SIGINT shutdown hook can race the live server thread and wedge in
# native world shutdown. Keep Java in a separate session and relay console
# input through a private FIFO. Ctrl+C/TERM/HUP are converted to the ordinary
# console `stop` command, so the server thread owns shutdown and world saving.
console_dir=$(mktemp -d "$test_home/.trueuuid-console.XXXXXX")
console_fifo="$console_dir/stdin"
mkfifo -m 600 "$console_fifo"
exec 3<>"$console_fifo"
server_pid=""
stop_requested=0

cleanup_console() {
    exec 3>&- || true
    if [[ -p "$console_fifo" ]]; then
        rm -f -- "$console_fifo"
    fi
    rmdir -- "$console_dir" 2>/dev/null || true
}

request_clean_stop() {
    if [[ "$stop_requested" == 0 && -n "$server_pid" ]] \
            && kill -0 "$server_pid" 2>/dev/null; then
        stop_requested=1
        printf '\nSignal received; requesting a clean Minecraft console stop...\n' >&2
        printf 'stop\n' >&3 || true
    elif [[ "$stop_requested" == 1 && -n "$server_pid" ]] \
            && kill -0 "$server_pid" 2>/dev/null; then
        printf '\nClean shutdown already requested; still waiting for Minecraft to exit...\n' >&2
    fi
}

trap cleanup_console EXIT
trap request_clean_stop INT TERM HUP

setsid "$java17_home/bin/java" -Xms512M -Xmx1G -jar spigot.jar nogui <&3 3>&- &
server_pid=$!

# Keep the wrapper in the foreground so interactive console input remains
# usable. Every command in this polling section is either a conditional or has
# an explicit non-fatal fallback: a trapped signal can interrupt read/sleep but
# can no longer escape through `set -e` and detach the still-running JVM.
while kill -0 "$server_pid" 2>/dev/null; do
    if IFS= read -r -t 0.25 console_line; then
        if [[ "${console_line,,}" == stop ]]; then
            stop_requested=1
        fi
        printf '%s\n' "$console_line" >&3 || true
    else
        # Avoid an EOF spin when a non-interactive caller closes stdin.
        sleep 0.05 || true
    fi
done

# Reap the real Java status before allowing the terminal prompt to return.
server_status=0
while true; do
    set +e
    wait "$server_pid"
    waited_status=$?
    set -e
    if kill -0 "$server_pid" 2>/dev/null; then
        continue
    fi
    server_status=$waited_status
    break
done

trap - INT TERM HUP
if [[ "$server_status" == 0 ]]; then
    printf 'Server exited cleanly.\n'
else
    printf 'Server exited with status %s.\n' "$server_status" >&2
fi
exit "$server_status"
