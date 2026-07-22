#!/usr/bin/env bash
# Launch a REAL premium (Microsoft/Mojang) Minecraft client for one TrueUUID
# target, with the freshly built mod jar, so the premium session path
# (joinServer / hasJoined) can actually be exercised. Unlike
# run-dev-target.sh, whose Gradle runClient uses a throwaway dev account, this
# authenticates a real account through portablemc.
#
# Auth is Microsoft device-code: portablemc prints a code, you approve it in a
# browser once, and it caches a REFRESH token (never your password). The token
# store lives OUTSIDE the repo under ~/.local/share/trueuuid-testclient and is
# chmod 700. Never commit it; never paste it anywhere.
#
# Pair this with the offline-mode dev server from run-dev-target.sh:
#   Terminal 1:  scripts/run-dev-target.sh fabric-1.20.1 server
#   Terminal 2:  scripts/test-premium-client.sh fabric-1.20.1
# Then in-game: Multiplayer -> Direct Connect -> 127.0.0.1, or pass
# --server 127.0.0.1:25565 to let portablemc use Minecraft's quick-play
# server auto-connect arguments.
set -euo pipefail

# Any file this script (or portablemc) creates under the test home — above all
# the cached auth/refresh token — stays private (files 600, dirs 700).
umask 077

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
targets_file="$root/release/targets.json"

# All test state (auth db, shared asset cache, per-target run dirs) stays out of
# the repo. Override the base with TRUEUUID_TEST_HOME if you must.
test_home="${TRUEUUID_TEST_HOME:-$HOME/.local/share/trueuuid-testclient}"
main_dir="$test_home/shared"   # assets, libraries, versions, portablemc auth db (shared across targets)

usage() {
    cat <<'EOF'
Usage:
  scripts/test-premium-client.sh [options] <target>   Launch a premium client
  scripts/test-premium-client.sh login                Sign in once
  scripts/test-premium-client.sh logout               Forget the cached account

Registered targets come from release/targets.json:
EOF
    if command -v jq >/dev/null 2>&1 && [[ -f "$targets_file" ]]; then
        jq -r '.targets[] | "  \(.id)  \(.loader) / Minecraft \(.game_version)"' "$targets_file"
    fi
    cat <<'EOF'

Account: set TRUEUUID_MS_EMAIL=you@example.com, or put it in
  ~/.local/share/trueuuid-testclient/email
Use a dedicated/secondary Mojang account if you have one.

Env overrides:
  TRUEUUID_MS_EMAIL       Microsoft account email (else read from the file above)
  TRUEUUID_PORTABLEMC     portablemc invocation (default: portablemc)
  TRUEUUID_GAME_JVM       JDK path to run the game (else chosen from the target's Java level)
  TRUEUUID_LOADER_VERSION Override the manifest-pinned loader build (normally unnecessary)
  TRUEUUID_FABRIC_API_JAR Path to a Fabric API jar (fabric targets need it; else auto-located)
  TRUEUUID_BUILD_JAVA     JDK 21 path used only if the mod jar must be built (default: java-21-openjdk)
  TRUEUUID_REBUILD_MOD    Set to 1 to rebuild even when the artifact exists
  TRUEUUID_MOD_JAR        Use this exact prebuilt target jar; do not rebuild it
  TRUEUUID_TEST_HOME      Base dir for auth/cache/run state (default: ~/.local/share/trueuuid-testclient)

Options:
  --server HOST[:PORT]    Auto-connect to a local test server
  --server-port PORT      Server port when --server omits it
  --offline-name NAME     Use a fake offline account instead of Microsoft login
  --offline-uuid UUID     Optional fake account UUID; random if omitted
EOF
}

server_host=""
server_port=""
offline_name=""
offline_uuid=""
while [[ $# -gt 0 ]]; do
    case "$1" in
        -h|--help|help)
            usage
            exit 0
            ;;
        --server)
            [[ $# -ge 2 ]] || { echo "--server needs HOST[:PORT]" >&2; exit 64; }
            server_host="$2"
            if [[ "$server_host" == *:* ]]; then
                server_port="${server_host##*:}"
                server_host="${server_host%:*}"
            fi
            shift 2
            ;;
        --server-port)
            [[ $# -ge 2 ]] || { echo "--server-port needs PORT" >&2; exit 64; }
            server_port="$2"
            shift 2
            ;;
        --offline-name|--username)
            [[ $# -ge 2 ]] || { echo "$1 needs NAME" >&2; exit 64; }
            offline_name="$2"
            shift 2
            ;;
        --offline-uuid|--uuid)
            [[ $# -ge 2 ]] || { echo "$1 needs UUID" >&2; exit 64; }
            offline_uuid="$2"
            shift 2
            ;;
        --)
            shift
            break
            ;;
        -*)
            echo "Unknown option: $1" >&2
            usage >&2
            exit 64
            ;;
        *)
            break
            ;;
    esac
done

cmd="${1:-}"
case "$cmd" in ''|-h|--help|help) usage; exit 0 ;; esac
shift || true
[[ $# -eq 0 ]] || { echo "Unexpected extra arguments: $*" >&2; exit 64; }

command -v jq >/dev/null 2>&1 || { echo 'jq is required.' >&2; exit 69; }

# portablemc may be a bare command or "python3 -m portablemc".
read -r -a portablemc <<<"${TRUEUUID_PORTABLEMC:-portablemc}"
if ! command -v "${portablemc[0]}" >/dev/null 2>&1; then
    cat >&2 <<EOF
portablemc is not installed. Install it once (it is a small, maintained CLI):
  pipx install portablemc      # recommended
  # or: python3 -m pip install --user portablemc
Then re-run this script. Override the command with TRUEUUID_PORTABLEMC if needed.
EOF
    exit 69
fi

# PortableMC 4.4.1 predates NeoForge 21.11's {ROOT} install-processor
# variable. Run the installed package through our narrow compatibility entry
# point when its Python interpreter can be identified. This retains the same
# PortableMC cache and authentication database; it does not patch site files.
portablemc_compat="$root/scripts/portablemc-compat.py"
if [[ ${#portablemc[@]} -eq 1 ]]; then
    portablemc_executable="$(command -v "${portablemc[0]}")"
    IFS= read -r portablemc_shebang < "$portablemc_executable" || true
    if [[ "$portablemc_shebang" == '#!'*python* ]]; then
        read -r -a portablemc_interpreter <<<"${portablemc_shebang#\#!}"
        portablemc=("${portablemc_interpreter[@]}" "$portablemc_compat")
    fi
elif [[ ${#portablemc[@]} -ge 3 && "${portablemc[1]}" == '-m' && "${portablemc[2]}" == 'portablemc' ]]; then
    portablemc=("${portablemc[0]}" "$portablemc_compat" "${portablemc[@]:3}")
fi

mkdir -p "$main_dir"
chmod 700 "$test_home" "$main_dir" 2>/dev/null || true

canonical_auth="$main_dir/portablemc_auth.json"

# PortableMC resolves portablemc_auth.json relative to --work-dir when one is
# supplied. Without this consolidation every target silently gets a different
# refresh-token database and Microsoft asks for device authorization again.
# Promote the newest same-account database, then link every isolated target to
# the one private canonical file. Never print database contents or token data.
consolidate_auth_databases() {
    if [[ -L "$canonical_auth" ]]; then
        echo "Refusing symlinked canonical auth database: $canonical_auth" >&2
        return 65
    fi

    local candidates=()
    [[ -f "$canonical_auth" ]] && candidates+=("$canonical_auth")
    local candidate
    for candidate in "$test_home"/work/*/portablemc_auth.json; do
        [[ -f "$candidate" && ! -L "$candidate" ]] && candidates+=("$candidate")
    done
    [[ ${#candidates[@]} -gt 0 ]] || return 0

    local session_keys="" keys latest=""
    for candidate in "${candidates[@]}"; do
        if ! keys=$(jq -ce '.microsoft.sessions | objects | keys | sort' "$candidate" 2>/dev/null); then
            printf 'Invalid PortableMC auth database; refusing to consolidate: %s\n' "$candidate" >&2
            return 65
        fi
        if [[ -n "$session_keys" && "$keys" != "$session_keys" ]]; then
            echo 'PortableMC auth databases contain different account sets; refusing to discard either one.' >&2
            echo 'Run scripts/test-premium-client.sh logout, then login once with the intended test account.' >&2
            return 65
        fi
        session_keys="$keys"
        if [[ -z "$latest" || "$candidate" -nt "$latest" ]]; then
            latest="$candidate"
        fi
    done

    if [[ "$latest" != "$canonical_auth" ]]; then
        local staged_auth="$main_dir/.portablemc_auth.json.$$"
        install -m 600 "$latest" "$staged_auth"
        mv -f "$staged_auth" "$canonical_auth"
    else
        chmod 600 "$canonical_auth"
    fi

    local work_auth
    for work_auth in "$test_home"/work/*/portablemc_auth.json; do
        [[ -e "$work_auth" || -L "$work_auth" ]] || continue
        if [[ -L "$work_auth" && "$(readlink -f "$work_auth" 2>/dev/null || true)" == "$canonical_auth" ]]; then
            continue
        fi
        unlink "$work_auth"
        ln -s "$canonical_auth" "$work_auth"
    done
}

link_shared_auth() {
    local work_dir="$1" work_auth="$1/portablemc_auth.json"
    mkdir -p "$work_dir"
    chmod 700 "$work_dir" 2>/dev/null || true
    if [[ -e "$work_auth" || -L "$work_auth" ]]; then
        if [[ -L "$work_auth" && "$(readlink -f "$work_auth" 2>/dev/null || true)" == "$canonical_auth" ]]; then
            return 0
        fi
        unlink "$work_auth"
    fi
    ln -s "$canonical_auth" "$work_auth"
}

consolidate_auth_databases

resolve_email() {
    if [[ -n "${TRUEUUID_MS_EMAIL:-}" ]]; then
        printf '%s' "$TRUEUUID_MS_EMAIL"; return 0
    fi
    local file="$test_home/email"
    if [[ -f "$file" ]]; then
        tr -d '[:space:]' < "$file"; return 0
    fi
    local auth_file="$main_dir/portablemc_auth.json"
    if [[ -f "$auth_file" ]]; then
        local cached
        cached="$(jq -r '.microsoft.sessions | keys | first // empty' "$auth_file" 2>/dev/null || true)"
        if [[ -n "$cached" ]]; then
            printf '%s' "$cached"; return 0
        fi
    fi
    echo 'No account email. Set TRUEUUID_MS_EMAIL or write it to '"$test_home/email" >&2
    return 1
}

account_display() {
    local value="$1"
    if [[ "$value" == *@* ]]; then
        printf '%s***@%s' "${value:0:1}" "${value#*@}"
    else
        printf '%s' "$value"
    fi
}

case "$cmd" in
    login)
        email="$(resolve_email)"
        "${portablemc[@]}" --main-dir "$main_dir" login "$email"
        chmod 600 "$main_dir"/*auth*.json 2>/dev/null || true
        exit 0
        ;;
    logout)
        email="$(resolve_email)"
        exec "${portablemc[@]}" --main-dir "$main_dir" logout "$email"
        ;;
esac

target="$cmd"
if ! meta=$(jq -ce --arg id "$target" '
    [.targets[] | select(.id == $id)] as $m |
    if ($m|length)==1 then $m[0] else error("target must appear exactly once") end
' "$targets_file" 2>/dev/null); then
    printf 'Target %q is not registered.\n\n' "$target" >&2
    usage >&2
    exit 64
fi

loader=$(jq -r '.loader' <<<"$meta")
game_version=$(jq -r '.game_version' <<<"$meta")
game_java=$(jq -r '.java' <<<"$meta")
manifest_loader_version=$(jq -r '.runtime_loader_version' <<<"$meta")
artifact_tmpl=$(jq -r '.artifact' <<<"$meta")

mod_version=$(sed -n 's/^mod_version=//p' "$root/gradle.properties" | tr -d '[:space:]')
[[ -n "$mod_version" ]] || { echo 'Could not read mod_version from gradle.properties.' >&2; exit 70; }
artifact="$root/${artifact_tmpl//%VERSION%/$mod_version}"
artifact_override="${TRUEUUID_MOD_JAR:-}"
if [[ -n "$artifact_override" ]]; then
    [[ -f "$artifact_override" ]] || { echo "TRUEUUID_MOD_JAR does not exist: $artifact_override" >&2; exit 66; }
    artifact="$(readlink -f "$artifact_override")"
fi

source_newer_than_artifact() {
    [[ -f "$artifact" ]] || return 0
    local roots=(
        "$root/build.gradle"
        "$root/settings.gradle"
        "$root/gradle.properties"
        "$root/shared/protocol/build.gradle"
        "$root/shared/protocol/src"
        "$root/platform/common-assets/src"
        "$root/platform/$target/build.gradle"
        "$root/platform/$target/src"
    )
    local loader_common="$root/platform/${loader}-common"
    if [[ -d "$loader_common" ]]; then
        # Include loader-wide Gradle composition as well as source roots. A
        # target artifact must be rebuilt when an API-era source selection or
        # dependency wiring change is newer than the staged JAR.
        roots+=("$loader_common")
    fi
    local root_path
    for root_path in "${roots[@]}"; do
        [[ -e "$root_path" ]] || continue
        if [[ -f "$root_path" ]]; then
            [[ "$root_path" -nt "$artifact" ]] && return 0
            continue
        fi
        if find "$root_path" -type f \( -name '*.java' -o -name '*.json' -o -name '*.toml' -o -name '*.properties' -o -name '*.gradle' -o -name '*.gradle.kts' \) \
            -newer "$artifact" -print -quit | grep -q .; then
            return 0
        fi
    done
    return 1
}

# Build the mod jar on demand or when source is newer than the artifact (Gradle
# needs a Java 21 launcher; the game JVM is chosen separately below).
if [[ -z "$artifact_override" ]] && { [[ ! -f "$artifact" || "${TRUEUUID_REBUILD_MOD:-}" == "1" ]] || source_newer_than_artifact; }; then
    build_java="${TRUEUUID_BUILD_JAVA:-/usr/lib/jvm/java-21-openjdk-amd64}"
    [[ -x "$build_java/bin/java" ]] || { echo "Need a JDK 21 to build; set TRUEUUID_BUILD_JAVA." >&2; exit 78; }
    echo "Building fresh mod jar for $target ..."
    ( cd "$root" && JAVA_HOME="$build_java" PATH="$build_java/bin:$PATH" \
        ./scripts/ci/build-target.sh "$target" )
fi
[[ -f "$artifact" ]] || { echo "Build did not produce $artifact" >&2; exit 70; }

# Pick a game JVM matching the target's Java level (the shell default may be a
# JDK the game cannot run on).
if [[ -n "${TRUEUUID_GAME_JVM:-}" ]]; then
    game_jvm="$TRUEUUID_GAME_JVM"
elif [[ "$game_java" == "17" ]]; then
    game_jvm="/usr/lib/jvm/jdk-17.0.12-oracle-x64"
else
    game_jvm="/usr/lib/jvm/java-21-openjdk-amd64"
fi
[[ -x "$game_jvm/bin/java" ]] || { echo "Game JDK $game_java not found; set TRUEUUID_GAME_JVM." >&2; exit 78; }

# portablemc version spec. A per-target work dir isolates mods/saves while the
# big asset/library cache and the auth db stay shared in main_dir.
loader_version="${TRUEUUID_LOADER_VERSION:-$manifest_loader_version}"
[[ -n "$loader_version" && "$loader_version" != "null" ]] || {
    echo "Target $target has no pinned runtime loader version." >&2
    exit 65
}
case "$loader" in
    # PortableMC separates Fabric's game and loader versions with a second
    # colon; a hyphen makes the entire string look like a vanilla version.
    fabric) version_spec="fabric:${game_version}:${loader_version}" ;;
    forge)  version_spec="forge:${game_version}-${loader_version}" ;;
    neoforge)
        # PortableMC's NeoForge resolver takes the artifact version directly
        # for 20.2+ (for example neoforge:21.3.56). Only the legacy 1.20.1
        # compatibility line is published/resolved as a Forge-style full
        # Minecraft-loader coordinate.
        if [[ "$game_version" == "1.20.1" ]]; then
            version_spec="neoforge:${game_version}-${loader_version}"
        else
            version_spec="neoforge:${loader_version}"
        fi
        ;;
    *) echo "Unsupported loader: $loader" >&2; exit 65 ;;
esac

work_dir="$test_home/work/$target"
mods_dir="$work_dir/mods"
mkdir -p "$mods_dir"
link_shared_auth "$work_dir"

# PortableMC's quick-play arguments are not processed until Minecraft reaches
# its normal title flow. Pre-answer first-launch UI that would otherwise block
# an unattended matrix run before it can connect to the server. Preserve every
# unrelated user option in this isolated test-client work directory.
options_file="$work_dir/options.txt"
touch "$options_file"
set_client_option() {
    local key="$1" value="$2"
    if grep -q "^${key}:" "$options_file"; then
        sed -i -E "s|^${key}:.*|${key}:${value}|" "$options_file"
    else
        printf '%s:%s\n' "$key" "$value" >> "$options_file"
    fi
}
set_client_option narrator 0
set_client_option narratorHotkey false
set_client_option onboardAccessibility false
set_client_option skipMultiplayerWarning true
set_client_option joinedFirstServer true

# NeoForge may stop at its non-fatal mod-loading warning screen before
# Minecraft processes quick play (older lines do this for harmless resource
# metadata warnings). Acceptance logs still fail on real ERROR/FATAL events;
# suppress only that interactive warning screen in this isolated test client.
if [[ "$loader" == "neoforge" ]]; then
    neoforge_client_config="$work_dir/config/neoforge-client.toml"
    mkdir -p "$(dirname "$neoforge_client_config")"
    if [[ ! -f "$neoforge_client_config" ]]; then
        printf '[client]\nshowLoadWarnings = false\n' > "$neoforge_client_config"
    elif grep -qE '^[[:space:]]*showLoadWarnings[[:space:]]*=' "$neoforge_client_config"; then
        sed -i -E 's/^[[:space:]]*showLoadWarnings[[:space:]]*=.*/showLoadWarnings = false/' "$neoforge_client_config"
    elif grep -q '^\[client\]$' "$neoforge_client_config"; then
        sed -i '/^\[client\]$/a showLoadWarnings = false' "$neoforge_client_config"
    else
        printf '\n[client]\nshowLoadWarnings = false\n' >> "$neoforge_client_config"
    fi
fi

# Only this target's mods for this run: clear then stage.
rm -f "$mods_dir"/*.jar
cp "$artifact" "$mods_dir/"

# Fabric clients also need the Fabric API mod present.
if [[ "$loader" == "fabric" ]]; then
    fabric_api="${TRUEUUID_FABRIC_API_JAR:-}"
    if [[ -z "$fabric_api" ]]; then
        coord=$(sed -nE "s/^[[:space:]]*fabricApiVersion:[[:space:]]*'([^']+)'.*/\\1/p" \
            "$root/platform/$target/build.gradle" | head -1)
        if [[ -z "$coord" ]]; then
            coord=$(grep -oE "net\.fabricmc\.fabric-api:fabric-api:[0-9A-Za-z.+-]+" \
                "$root/platform/$target/build.gradle" | head -1 | awk -F: '{print $3}')
        fi
        if [[ -n "$coord" ]]; then
            fabric_api=$(find "$HOME/.gradle/caches" -name "fabric-api-$coord.jar" 2>/dev/null | head -1)
        fi
    fi
    if [[ -n "$fabric_api" && -f "$fabric_api" ]]; then
        cp "$fabric_api" "$mods_dir/"
        echo "Staged Fabric API: $(basename "$fabric_api")"
    else
        echo "WARNING: Fabric API jar not found. Set TRUEUUID_FABRIC_API_JAR to it, or the client will not load the mod." >&2
    fi
fi

login_args=()
account_label=""
if [[ -n "$offline_name" ]]; then
    if [[ ${#offline_name} -gt 16 ]]; then
        echo "Offline Minecraft names must be at most 16 characters: $offline_name" >&2
        exit 64
    fi
    login_args=(--username "$offline_name")
    [[ -n "$offline_uuid" ]] && login_args+=(--uuid "$offline_uuid")
    account_label="$offline_name (offline throwaway account; no token used)"
else
    email="$(resolve_email)"
    login_args=(--login "$email")
    account_label="$(account_display "$email") (Microsoft; token cached in $main_dir, 700)"
fi
server_args=()
connect_label="manual: Multiplayer -> Direct Connect -> 127.0.0.1"
if [[ -n "$server_host" ]]; then
    server_args=(-s "$server_host")
    [[ -n "$server_port" ]] && server_args+=(-p "$server_port")
    connect_label="auto-connect: ${server_host}${server_port:+:$server_port}"
fi
echo "----------------------------------------------------------------"
echo "Target        : $target ($loader $game_version, game JDK $game_java)"
echo "Mod jar       : $(basename "$artifact")"
echo "Account       : $account_label"
echo "portablemc    : ${version_spec}"
echo "Connect       : $connect_label"
echo "----------------------------------------------------------------"

set +e
"${portablemc[@]}" --main-dir "$main_dir" --work-dir "$work_dir" \
    start "${login_args[@]}" "${server_args[@]}" --jvm "$game_jvm/bin/java" "$version_spec"
portablemc_status=$?
set -e

# PortableMC may replace a symlink atomically when rotating a refresh token.
# Promote that updated database and restore the shared link before returning.
consolidate_auth_databases
exit "$portablemc_status"
