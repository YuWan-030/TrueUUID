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
  fabric-1.20.1  Fabric Loader 0.19.3 / Minecraft 1.20.1 / Java 17 target, Java 21 launcher (planned; no login run)
  forge-1.21.1  Forge 52.1.0  / Minecraft 1.21.1 / Java 21 (test candidate)
  forge-1.21.3  Forge 53.1.0  / Minecraft 1.21.3 / Java 21 (planned; no login run)
  forge-1.21.4  Forge 54.1.14 / Minecraft 1.21.4 / Java 21 (planned; no login run)
  forge-1.21.5  Forge 55.1.10 / Minecraft 1.21.5 / Java 21 (planned; no login run)
  forge-1.21.8  Forge 58.1.0  / Minecraft 1.21.8 / Java 21 (planned; no login run)
  neoforge-1.21.1  NeoForge 21.1.213 / Minecraft 1.21.1 / Java 21 (planned; no login run)
  neoforge-1.21.3  NeoForge 21.3.56  / Minecraft 1.21.3 / Java 21 (planned; no login run)
  neoforge-1.21.4  NeoForge 21.4.121 / Minecraft 1.21.4 / Java 21 (planned; no login run)
  neoforge-1.21.5  NeoForge 21.5.74  / Minecraft 1.21.5 / Java 21 (planned; no login run)
  neoforge-1.21.8  NeoForge 21.8.9   / Minecraft 1.21.8 / Java 21 (planned; no login run)

Examples (use two terminals):
  scripts/run-dev-target.sh forge-1.20.1 server
  scripts/run-dev-target.sh forge-1.20.1 client
  TRUEUUID_JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 \
    scripts/run-dev-target.sh fabric-1.20.1 server
  TRUEUUID_JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 \
    scripts/run-dev-target.sh forge-1.21.8 server
  TRUEUUID_JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 \
    scripts/run-dev-target.sh neoforge-1.21.8 server

The first launch of a new Minecraft version downloads that version's assets, so
runs are online by default. Set TRUEUUID_OFFLINE=1 to force --offline once a
target's assets are cached.

The script launches Gradle's development run configuration. It is not a
release-jar installer and does not claim that a planned target is supported.

Memory defaults: Gradle 1G, Fabric client 3G, Fabric server 1536M. Override
them deliberately with TRUEUUID_GRADLE_XMX, TRUEUUID_CLIENT_XMX, or
TRUEUUID_SERVER_XMX (for example: TRUEUUID_CLIENT_XMX=4G).
EOF
}

if [[ "$role" != "client" && "$role" != "server" ]]; then
    usage >&2
    exit 64
fi

case "$target" in
    forge-1.20.1)
        required_java=17
        ;;
    fabric-1.20.1|forge-1.21.1|forge-1.21.3|forge-1.21.4|forge-1.21.5|forge-1.21.8|neoforge-1.21.1|neoforge-1.21.3|neoforge-1.21.4|neoforge-1.21.5|neoforge-1.21.8)
        required_java=21
        ;;
    *)
        printf 'Target %q is not registered for local development runs.\n' "$target" >&2
        usage >&2
        exit 64
        ;;
esac
role_cap="$(tr '[:lower:]' '[:upper:]' <<< "${role:0:1}")${role:1}"
gradle_task=":platform:${target}:run${role_cap}"

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
    run_dir="$root/platform/$target/run/server"
    mkdir -p "$run_dir"
    # Auto-accept the Minecraft EULA for this LOCAL development server only.
    # This is a throwaway test instance on your own machine; a production
    # server must agree to the EULA deliberately and must not inherit this.
    printf 'eula=true\n' > "$run_dir/eula.txt"
    server_properties="$run_dir/server.properties"
    touch "$server_properties"
    for setting in 'online-mode=false' 'server-ip=127.0.0.1'; do
        key="${setting%%=*}"
        if grep -q "^${key}=" "$server_properties"; then
            sed -i -E "s|^${key}=.*|${setting}|" "$server_properties"
        else
            printf '\n%s\n' "$setting" >> "$server_properties"
        fi
    done
    printf 'TrueUUID local test server: eula=true, online-mode=false, server-ip=127.0.0.1\n'
fi

# Online by default: a new Minecraft version's assets must download on first run.
# Set TRUEUUID_OFFLINE=1 once cached to avoid re-resolving.
gradle_flags=(--no-daemon)
if [[ -n "${TRUEUUID_OFFLINE:-}" ]]; then
    gradle_flags+=(--offline)
fi
# Do not let the project-wide build default reserve several gigabytes while a
# client/server run is loading assets. Loom applies the separate game-process
# caps in the Fabric adapter's run configuration.
gradle_jvmargs="-Xms256M -Xmx${TRUEUUID_GRADLE_XMX:-1G} -Dfile.encoding=UTF-8"
exec ./gradlew "-Dorg.gradle.jvmargs=${gradle_jvmargs}" "$gradle_task" "${gradle_flags[@]}"
