#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 1 ]]; then
    echo "usage: $0 <output-directory>" >&2
    exit 64
fi

repo_root=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd -P)
expected_target='spigot-1.20.1'
[[ "${TRUEUUID_SERVER_PLUGIN_TARGET:-$expected_target}" == "$expected_target" ]] || {
    echo "This exact plugin verifier only supports $expected_target." >&2
    exit 65
}
output_dir=$1
version=$(sed -n 's/^mod_version=//p' "$repo_root/gradle.properties" | tr -d '[:space:]')
artifact="$repo_root/plugin/spigot/1.20.1/build/libs/trueuuid-spigot-1.20.1-candidate.jar"
protocol_sha256='562c3ef79391e25f71b23359adb6becae7bcee36b0dfe2621b2c679013116769'

[[ "$version" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || {
    echo 'invalid mod_version' >&2
    exit 65
}
[[ -f "$artifact" ]] || {
    echo "missing built Spigot plugin: $artifact" >&2
    exit 66
}
command -v jar >/dev/null 2>&1 || { echo 'jar is required' >&2; exit 69; }
command -v unzip >/dev/null 2>&1 || { echo 'unzip is required' >&2; exit 69; }

unzip -tqq "$artifact"
entries=$(jar tf "$artifact")
for required in \
    META-INF/MANIFEST.MF \
    plugin.yml \
    config.yml \
    cn/alini/trueuuid/spigot/v1_20_1/TrueuuidSpigotPlugin.class \
    cn/alini/trueuuid/spigot/v1_20_1/ExactSpigot1201Bridge.class \
    cn/alini/trueuuid/bukkit/TrueuuidBukkitApi.class \
    cn/alini/trueuuid/protocol/AuthWireCodec.class \
    cn/alini/trueuuid/protocol/HybridLoginCoordinator.class \
    cn/alini/trueuuid/server/UnifiedAdmissionPolicy.class \
    cn/alini/trueuuid/server/PersistentIdentityRepository.class; do
    grep -Fxq "$required" <<<"$entries" || {
        echo "missing Spigot plugin entry: $required" >&2
        exit 65
    }
done

duplicate_classes=$(grep '\.class$' <<<"$entries" | sort | uniq -d)
[[ -z "$duplicate_classes" ]] || {
    echo "duplicate classes in $artifact:" >&2
    printf '%s\n' "$duplicate_classes" >&2
    exit 65
}
if grep -Eq '(^|/)(test|tests)/|Test\.class$|(^|/)scripts/|\.sh$' <<<"$entries"; then
    echo "test or development files leaked into $artifact" >&2
    exit 65
fi

# JAR manifests fold long attributes onto continuation lines. Unfold those
# lines before validating the exact dependency and server fingerprints.
manifest=$(unzip -p "$artifact" META-INF/MANIFEST.MF | tr -d '\r' | awk '
    /^ / { current = current substr($0, 2); next }
    { if (current != "") print current; current = $0 }
    END { if (current != "") print current }
')
for attribute in \
    "Implementation-Version: $version" \
    'TrueUUID-Support-Status: UNSUPPORTED-CANDIDATE' \
    'ProtocolLib-Version: 5.1.0' \
    "ProtocolLib-SHA-256: $protocol_sha256" \
    'Spigot-Implementation-Version: 3871-Spigot-d2eba2c-3f9263b'; do
    grep -Fxq "$attribute" <<<"$manifest" || {
        echo "missing or mismatched Spigot manifest attribute: $attribute" >&2
        exit 65
    }
done

plugin_yml=$(unzip -p "$artifact" plugin.yml | tr -d '\r')
for metadata in \
    'name: TrueUUID' \
    'main: cn.alini.trueuuid.spigot.v1_20_1.TrueuuidSpigotPlugin' \
    "version: '$version'" \
    "api-version: '1.20'" \
    'load: STARTUP' \
    'depend: [ProtocolLib]'; do
    grep -Fxq "$metadata" <<<"$plugin_yml" || {
        echo "missing or mismatched plugin.yml value: $metadata" >&2
        exit 65
    }
done

mkdir -p "$output_dir"
cp "$artifact" "$output_dir/"
(
    cd "$output_dir"
    sha256sum "$(basename "$artifact")" > SHA256SUMS
)

echo "Verified Spigot 1.20.1 plugin JAR: $artifact"
