#!/usr/bin/env bash
# Launch one TrueUUID development target. Run client and server in separate
# terminals; this avoids two long-lived Gradle run tasks contending for a build.
set -euo pipefail

target="${1:-forge-1.20.1}"
role="${2:-}"
root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
targets_file="$root/release/targets.json"

usage() {
    cat <<'EOF'
Usage: scripts/run-dev-target.sh [target] <client|server>

Registered targets come from release/targets.json. All need a Java 21 Gradle
launcher; each game JVM uses its module's declared Java toolchain:
EOF
    if command -v jq >/dev/null 2>&1 && [[ -f "$targets_file" ]]; then
        jq -r '.targets[] | "  \(.id)  \(.loader) / Minecraft \(.game_version) / Java \(.java) game toolchain"' \
            "$targets_file"
    else
        printf '  (install jq and verify release/targets.json to list targets)\n'
    fi
    cat <<'EOF'

Examples (use two terminals):
  scripts/run-dev-target.sh forge-1.20.1 server
  scripts/run-dev-target.sh forge-1.20.1 client
  TRUEUUID_JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 \
    scripts/run-dev-target.sh fabric-1.20.1 server
  TRUEUUID_JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 \
    scripts/run-dev-target.sh forge-1.21.8 server
  TRUEUUID_JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 \
    scripts/run-dev-target.sh neoforge-1.21.11 server

The first launch of a new Minecraft version downloads that version's assets, so
runs are online by default. Set TRUEUUID_OFFLINE=1 to force --offline once a
target's assets are cached.

The script launches Gradle's development run configuration. It is not a
release-jar installer and does not claim that a planned target is supported.

Memory defaults: Gradle 1G, Fabric client 3G, Fabric server 1536M. Override
them deliberately with TRUEUUID_GRADLE_XMX, TRUEUUID_CLIENT_XMX, or
TRUEUUID_SERVER_XMX (for example: TRUEUUID_CLIENT_XMX=4G).

Server env overrides:
  TRUEUUID_SERVER_IP      Bind address (default: 127.0.0.1)
  TRUEUUID_SERVER_PORT    Port (default: Minecraft default / existing file)
  TRUEUUID_SERVER_MOTD    MOTD written to server.properties for test runs
  TRUEUUID_SERVER_LEVEL   World directory name (default: trueuuid-ci-world)
EOF
}

if [[ "$role" != "client" && "$role" != "server" ]]; then
    usage >&2
    exit 64
fi

command -v jq >/dev/null 2>&1 || {
    echo 'jq is required to read the development target manifest.' >&2
    exit 69
}
(cd "$root" && ./scripts/release/validate-targets.sh)
if ! target_metadata=$(jq -ce --arg id "$target" '
    [.targets[] | select(.id == $id)] as $matches |
    if ($matches | length) == 1 then $matches[0]
    else error("target must appear exactly once")
    end
' "$targets_file" 2>/dev/null); then
    printf 'Target %q is not registered for local development runs.\n' "$target" >&2
    usage >&2
    exit 64
fi

# Every target needs a Java 21 GRADLE LAUNCHER because the Fabric Loom plugin
# is configured on every Gradle invocation. Gradle launches each game with the
# Java 17 or Java 21 toolchain declared by the selected module.
required_java=21
target_game_java=$(jq -r '.java' <<<"$target_metadata")
standalone=$(jq -r '.standalone // false' <<<"$target_metadata")
role_cap="$(tr '[:lower:]' '[:upper:]' <<< "${role:0:1}")${role:1}"
if [[ "$standalone" == true ]]; then
    gradle_project="$root/platform/$target"
    gradle_wrapper="$gradle_project/gradlew"
    gradle_task="run${role_cap}"
else
    gradle_project="$root"
    gradle_wrapper="$root/gradlew"
    gradle_task=":platform:${target}:run${role_cap}"
fi
[[ -x "$gradle_wrapper" ]] || { echo "Missing Gradle wrapper: $gradle_wrapper" >&2; exit 66; }

java_home="${TRUEUUID_JAVA_HOME:-${JAVA_HOME:-/usr/lib/jvm/java-21-openjdk-amd64}}"

if [[ ! -x "$java_home/bin/java" ]]; then
    printf 'A JDK %s is required. Set TRUEUUID_JAVA_HOME to its installation directory.\n' "$required_java" >&2
    exit 78
fi

actual_java="$($java_home/bin/java -version 2>&1 | sed -n '1s/.*"\([0-9][0-9]*\).*/\1/p')"
if [[ "$actual_java" != "$required_java" ]]; then
    printf 'Target %s requires a Java %s Gradle launcher; TRUEUUID_JAVA_HOME selects Java %s. The game uses its Java %s toolchain.\n' \
        "$target" "$required_java" "${actual_java:-unknown}" "$target_game_java" >&2
    exit 78
fi

cd "$root"
export JAVA_HOME="$java_home"
export PATH="$JAVA_HOME/bin:$PATH"

if [[ "$role" == "server" ]]; then
    # TrueUUID's premium-session path is exercised only by an offline-mode
    # server. Keep the development server local by default; a production
    # server must be configured deliberately and must not inherit this script.
    # Different Gradle plugins and target eras use different dev-server
    # working directories. Prepare both common locations; the actual run only
    # reads the one its Gradle run config selects.
    run_dirs=(
        "$root/platform/$target/run"
        "$root/platform/$target/run/server"
        # NeoGradle 7 (used only by NeoForge 1.20.2) defaults to runs/server.
        "$root/platform/$target/runs/server"
    )
    server_level="${TRUEUUID_SERVER_LEVEL:-trueuuid-ci-world}"
    if [[ ! "$server_level" =~ ^[A-Za-z0-9._-]+$ || "$server_level" == "." || "$server_level" == ".." ]]; then
        printf 'TRUEUUID_SERVER_LEVEL must be a simple world directory name, not a path: %q\n' "$server_level" >&2
        exit 64
    fi
    settings=(
        "online-mode=false"
        "server-ip=${TRUEUUID_SERVER_IP:-127.0.0.1}"
        "level-name=${server_level}"
        "max-tick-time=-1"
    )
    if [[ -n "${TRUEUUID_SERVER_PORT:-}" ]]; then
        settings+=("server-port=${TRUEUUID_SERVER_PORT}")
    fi
    if [[ -n "${TRUEUUID_SERVER_MOTD:-}" ]]; then
        settings+=("motd=${TRUEUUID_SERVER_MOTD}")
    fi
    for run_dir in "${run_dirs[@]}"; do
        mkdir -p "$run_dir"
        # Auto-accept the Minecraft EULA for this LOCAL development server only.
        # This is a throwaway test instance on your own machine; a production
        # server must agree to the EULA deliberately and must not inherit this.
        printf 'eula=true\n' > "$run_dir/eula.txt"
        server_properties="$run_dir/server.properties"
        touch "$server_properties"
        for setting in "${settings[@]}"; do
            key="${setting%%=*}"
            if grep -q "^${key}=" "$server_properties"; then
                sed -i -E "s|^${key}=.*|${setting}|" "$server_properties"
            else
                printf '\n%s\n' "$setting" >> "$server_properties"
            fi
        done
    done
    printf 'TrueUUID local test server: eula=true, online-mode=false, server-ip=%s' "${TRUEUUID_SERVER_IP:-127.0.0.1}"
    [[ -n "${TRUEUUID_SERVER_PORT:-}" ]] && printf ', server-port=%s' "$TRUEUUID_SERVER_PORT"
    printf ', level-name=%s\n' "$server_level"
fi

# Online by default: a new Minecraft version's assets must download on first run.
# Set TRUEUUID_OFFLINE=1 once cached to avoid re-resolving.
gradle_flags=(--no-daemon)
if [[ -n "${TRUEUUID_OFFLINE:-}" ]]; then
    gradle_flags+=(--offline)
fi
if [[ "${TRUEUUID_ACCEPTANCE_HOOKS:-}" == "1" ]]; then
    gradle_flags+=("-PtrueuuidAcceptanceHooks=true")
fi
# Do not let the project-wide build default reserve several gigabytes while a
# client/server run is loading assets. Loom applies the separate game-process
# caps in the Fabric adapter's run configuration.
gradle_jvmargs="-Xms256M -Xmx${TRUEUUID_GRADLE_XMX:-1G} -Dfile.encoding=UTF-8"
exec "$gradle_wrapper" -p "$gradle_project" "-Dorg.gradle.jvmargs=${gradle_jvmargs}" "$gradle_task" "${gradle_flags[@]}"
