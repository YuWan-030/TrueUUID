#!/usr/bin/env bash
set -euo pipefail

repo_root=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd -P)
expected_target='spigot-1.20.1'
[[ "${TRUEUUID_SERVER_PLUGIN_TARGET:-$expected_target}" == "$expected_target" ]] || {
    echo "This exact builder only supports $expected_target." >&2
    exit 65
}
work_root="$repo_root/build/spigot-candidate"
buildtools_jar="$work_root/BuildTools-200.jar"
buildtools_url='https://hub.spigotmc.org/jenkins/job/BuildTools/200/artifact/target/BuildTools.jar'
buildtools_sha256='b61fa90158f594ee95bea1a27399eb64d439b4c8ae9345bd4476a02ce49b06ff'
protocol_sha256='562c3ef79391e25f71b23359adb6becae7bcee36b0dfe2621b2c679013116769'

java17_home=${JAVA_HOME_17_X64:-/usr/lib/jvm/jdk-17.0.12-oracle-x64}
java21_home=${JAVA_HOME_21_X64:-/usr/lib/jvm/java-21-openjdk-amd64}
[[ -x "$java17_home/bin/java" ]] || { echo "Java 17 runtime not found at $java17_home" >&2; exit 66; }
[[ -x "$java21_home/bin/java" ]] || { echo "Java 21 launcher not found at $java21_home" >&2; exit 66; }

mkdir -p "$work_root" "$work_root/output" "$work_root/m2" "$work_root/reports"
if [[ ! -f "$buildtools_jar" ]]; then
    curl --fail --location --proto '=https' --tlsv1.2 \
        --output "$buildtools_jar" "$buildtools_url"
fi
printf '%s  %s\n' "$buildtools_sha256" "$buildtools_jar" | sha256sum --check --strict

if [[ ! -f "$work_root/.build-complete" ]]; then
    (
        cd "$work_root"
        MAVEN_OPTS="-Dmaven.repo.local=$work_root/m2" \
            "$java17_home/bin/java" -Xmx3G -jar "$buildtools_jar" \
            --rev 1.20.1 --compile SPIGOT --remapped --generate-source \
            --output-dir "$work_root/output" --nogui
    )
    touch "$work_root/.build-complete"
fi

declare -A expected_refs=(
    [BuildData]='221903b51701960ac778d8641b31cebcf411caa8'
    [Bukkit]='69c7ce23f295a5bf1b1b7128bc1daece4ead768e'
    [CraftBukkit]='3f9263ba3a726846a9466e12da95d73229af4ad9'
    [Spigot]='d2eba2c820b52b742eb542c6d2c4d76e3d743570'
)
for repository in BuildData Bukkit CraftBukkit Spigot; do
    actual=$(git -C "$work_root/$repository" rev-parse HEAD)
    [[ "$actual" == "${expected_refs[$repository]}" ]] || {
        echo "$repository revision mismatch: $actual" >&2
        exit 65
    }
done

spigot_runtime="$work_root/m2/org/spigotmc/spigot/1.20.1-R0.1-SNAPSHOT/spigot-1.20.1-R0.1-SNAPSHOT.jar"
protocol_runtime="$repo_root/plugin/spigot/1.20.1/build/pinned/ProtocolLib-5.1.0.jar"
mod_version=$(sed -n 's/^mod_version=//p' "$repo_root/gradle.properties" | tr -d '[:space:]')
[[ -n "$mod_version" ]] || { echo "Could not read mod_version from gradle.properties" >&2; exit 65; }
fabric_runtime="$repo_root/platform/fabric/1.20.1/build/libs/trueuuid-${mod_version}-fabric-1.20.1.jar"
[[ -f "$spigot_runtime" ]] || { echo "BuildTools did not produce the inner Spigot runtime JAR" >&2; exit 66; }

JAVA_HOME="$java21_home" PATH="$java21_home/bin:$PATH" \
    "$repo_root/gradlew" \
    :shared:protocol:test \
    :shared:server-core:test \
    :platform:fabric-1.20.1:test \
    :platform:fabric-1.20.1:remapJar \
    :plugin:bukkit-common:test \
    :plugin:spigot:1.20.1:test \
    :plugin:spigot:1.20.1:exactRuntimeCompatibilityTest \
    :plugin:spigot:1.20.1:crossLoaderCompatibilityTest \
    :plugin:spigot:1.20.1:jar \
    -PspigotRuntimeJar="$spigot_runtime" \
    --configure-on-demand --no-daemon --max-workers=2

printf '%s  %s\n' "$protocol_sha256" "$protocol_runtime" | sha256sum --check --strict
sha256sum \
    "$buildtools_jar" \
    "$work_root/output/spigot-1.20.1.jar" \
    "$spigot_runtime" \
    "$protocol_runtime" \
    "$fabric_runtime" \
    "$repo_root/plugin/spigot/1.20.1/build/libs/trueuuid-spigot-1.20.1-candidate.jar" \
    > "$work_root/reports/artifacts.sha256"

echo "Spigot 1.20.1 candidate build and exact runtime descriptor checks passed."
