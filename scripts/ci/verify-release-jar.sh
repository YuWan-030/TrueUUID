#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 2 ]]; then
    echo "usage: $0 <target-id> <output-directory>" >&2
    exit 64
fi

target_id=$1
output_dir=$2
version=$(sed -n 's/^mod_version=//p' gradle.properties)
[[ "$version" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || { echo "invalid mod_version" >&2; exit 65; }

./scripts/release/validate-targets.sh
target=$(jq -ce --arg id "$target_id" '.targets[] | select(.id == $id)' release/targets.json)
artifact=$(jq -r --arg version "$version" '.artifact | gsub("%VERSION%"; $version)' <<<"$target")
loader=$(jq -r '.loader' <<<"$target")

[[ -f "$artifact" ]] || { echo "missing built artifact: $artifact" >&2; exit 66; }
unzip -tqq "$artifact"

case "$loader" in
    forge)
        entry_class=cn/alini/trueuuid/Trueuuid.class
        mixins=trueuuid.mixins.json
        metadata=META-INF/mods.toml
        ;;
    neoforge)
        entry_class=cn/alini/trueuuid/Trueuuid.class
        mixins=trueuuid.mixins.json
        metadata=META-INF/neoforge.mods.toml
        ;;
    fabric)
        entry_class=cn/alini/trueuuid/fabric/TrueuuidFabric.class
        mixins=trueuuid.fabric.mixins.json
        metadata=fabric.mod.json
        ;;
    *) echo "unsupported loader for JAR verification: $loader" >&2; exit 65 ;;
esac

entries=$(jar tf "$artifact")
for required in \
    "$entry_class" \
    cn/alini/trueuuid/protocol/AuthWireCodec.class \
    assets/trueuuid/lang/en_us.json \
    "$mixins" \
    "$metadata"; do
    grep -Fxq "$required" <<<"$entries" || { echo "missing JAR entry: $required" >&2; exit 65; }
done

duplicate_classes=$(grep '\.class$' <<<"$entries" | sort | uniq -d)
[[ -z "$duplicate_classes" ]] || { echo "duplicate classes in $artifact:" >&2; printf '%s\n' "$duplicate_classes" >&2; exit 65; }
if grep -Eq '(^|/)(test|tests)/|Test\.class$' <<<"$entries"; then
    echo "test classes leaked into $artifact" >&2
    exit 65
fi

# Forge 47/48 and the legacy NeoForge 47.1 coordinate run SRG names in
# production. Their Mixin classes therefore require both a refmap and actual
# SRG-reobfuscated member references. A successful Gradle task is insufficient:
# missing MixinGradle outputs can otherwise leave an official-named JAR behind.
case "$target_id" in
    forge-1.20.1|neoforge-1.20.1)
        ./scripts/ci/verify-srg-mixin-jar.sh \
            "$artifact" \
            cn.alini.trueuuid.mixin.server.ServerLoginMixin
        ;;
    forge-1.20.2)
        ./scripts/ci/verify-srg-mixin-jar.sh \
            "$artifact" \
            cn.alini.trueuuid.mixin.server.ForgeServerLoginMixin
        ;;
esac

mkdir -p "$output_dir"
cp "$artifact" "$output_dir/"
(
    cd "$output_dir"
    sha256sum "$(basename "$artifact")" > SHA256SUMS
)

echo "Verified release JAR: $artifact"
