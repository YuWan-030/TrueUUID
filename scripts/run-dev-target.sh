#!/usr/bin/env bash
# Launch one TrueUUID development target. Run client and server in separate
# terminals; this avoids two long-lived Gradle run tasks contending for a build.
set -euo pipefail

target="${1:-forge-1.20.1}"
role="${2:-}"

usage() {
    cat <<'EOF'
Usage: scripts/run-dev-target.sh [target] <client|server>

Registered targets:
  forge-1.20.1  Forge 47.4.10 / Minecraft 1.20.1 / Java 17
  forge-1.21.1  Forge 52.1.0 / Minecraft 1.21.1 / Java 21 (test candidate)

Examples (use two terminals):
  scripts/run-dev-target.sh forge-1.20.1 server
  scripts/run-dev-target.sh forge-1.20.1 client
  TRUEUUID_JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 \\
    scripts/run-dev-target.sh forge-1.21.1 server

The script launches Gradle's development run configuration. It is not a
release-jar installer and does not claim that a planned target is supported.
EOF
}

if [[ "$role" != "client" && "$role" != "server" ]]; then
    usage >&2
    exit 64
fi

case "$target" in
    forge-1.20.1)
        required_java=17
        gradle_task=":platform:forge-1.20.1:run$(tr '[:lower:]' '[:upper:]' <<< "${role:0:1}")${role:1}"
        ;;
    forge-1.21.1)
        required_java=21
        gradle_task=":platform:forge-1.21.1:run$(tr '[:lower:]' '[:upper:]' <<< "${role:0:1}")${role:1}"
        ;;
    *)
        printf 'Target %q is not registered for local development runs.\n' "$target" >&2
        usage >&2
        exit 64
        ;;
esac

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
java_home="${TRUEUUID_JAVA_HOME:-${JAVA_HOME:-/usr/lib/jvm/jdk-17.0.12-oracle-x64}}"

if [[ ! -x "$java_home/bin/java" ]]; then
    printf 'A JDK %s is required. Set TRUEUUID_JAVA_HOME to its installation directory.\n' "$required_java" >&2
    exit 78
fi

actual_java="$($java_home/bin/java -version 2>&1 | sed -n '1s/.*"\([0-9][0-9]*\).*/\1/p')"
if [[ "$actual_java" != "$required_java" ]]; then
    printf 'Target %s requires Java %s; TRUEUUID_JAVA_HOME selects Java %s.\n' "$target" "$required_java" "${actual_java:-unknown}" >&2
    exit 78
fi

cd "$root"
export JAVA_HOME="$java_home"
export PATH="$JAVA_HOME/bin:$PATH"

if [[ "$role" == "server" ]]; then
    # TrueUUID's premium-session path is exercised only by an offline-mode
    # server. Keep the development server local by default; a production
    # server must be configured deliberately and must not inherit this script.
    server_properties="$root/platform/$target/run/server.properties"
    mkdir -p "$(dirname "$server_properties")"
    touch "$server_properties"
    for setting in 'online-mode=false' 'server-ip=127.0.0.1'; do
        key="${setting%%=*}"
        if grep -q "^${key}=" "$server_properties"; then
            sed -i -E "s|^${key}=.*|${setting}|" "$server_properties"
        else
            printf '\n%s\n' "$setting" >> "$server_properties"
        fi
    done
    printf 'TrueUUID local test server: online-mode=false, server-ip=127.0.0.1\n'
fi

exec ./gradlew "$gradle_task" --offline --no-daemon
