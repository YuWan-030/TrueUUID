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
metadata=$(jq -r '.metadata' <<<"$target")
srg_probe=$(jq -r '.srg_probe // empty' <<<"$target")

[[ -f "$artifact" ]] || { echo "missing built artifact: $artifact" >&2; exit 66; }
unzip -tqq "$artifact"

case "$loader" in
    forge)
        entry_class=cn/alini/trueuuid/Trueuuid.class
        mixins=trueuuid.mixins.json
        ;;
    neoforge)
        entry_class=cn/alini/trueuuid/Trueuuid.class
        mixins=trueuuid.mixins.json
        ;;
    fabric)
        entry_class=cn/alini/trueuuid/fabric/TrueuuidFabric.class
        mixins=trueuuid.fabric.mixins.json
        ;;
    *) echo "unsupported loader for JAR verification: $loader" >&2; exit 65 ;;
esac

entries=$(jar tf "$artifact")
for required in \
    "$entry_class" \
    cn/alini/trueuuid/protocol/AuthWireCodec.class \
    cn/alini/trueuuid/protocol/HasJoinedProfileParser.class \
    cn/alini/trueuuid/protocol/PersistentVerifiedNameStore.class \
    cn/alini/trueuuid/presentation/ClientStatusState.class \
    cn/alini/trueuuid/presentation/BadgeArtwork.class \
    assets/trueuuid/lang/en_us.json \
    "$mixins" \
    "$metadata"; do
    grep -Fxq "$required" <<<"$entries" || { echo "missing JAR entry: $required" >&2; exit 65; }
done

mixin_json=$(unzip -p "$artifact" "$mixins")
jq -e '.client | type == "array" and any(.[]; endswith("PauseScreenMixin"))' \
    <<<"$mixin_json" >/dev/null || {
    echo "pause-screen badge mixin is not client-scoped in $artifact" >&2
    exit 65
}
if [[ "$loader" == forge || "$loader" == neoforge ]]; then
    jq -e '.client | any(.[]; endswith("ClientStatusMixin"))' <<<"$mixin_json" >/dev/null || {
        echo "server-confirmed status mixin is not client-scoped in $artifact" >&2
        exit 65
    }
fi

case "$loader" in
    fabric)
        embedded_version=$(unzip -p "$artifact" "$metadata" | jq -er '.version')
        ;;
    forge|neoforge)
        embedded_version=$(unzip -p "$artifact" "$metadata" |
            sed -nE 's/^[[:space:]]*version[[:space:]]*=[[:space:]]*"([^"]+)".*/\1/p' |
            head -n 1)
        ;;
esac
[[ "$embedded_version" == "$version" ]] || {
    echo "metadata version mismatch in $artifact: expected $version, found ${embedded_version:-<missing>}" >&2
    exit 65
}

duplicate_classes=$(grep '\.class$' <<<"$entries" | sort | uniq -d)
[[ -z "$duplicate_classes" ]] || { echo "duplicate classes in $artifact:" >&2; printf '%s\n' "$duplicate_classes" >&2; exit 65; }
if grep -Eq '(^|/)(test|tests)/|Test\.class$' <<<"$entries"; then
    echo "test classes leaked into $artifact" >&2
    exit 65
fi
if grep -Eq '(^|/)scripts/|\.sh$|(^|/)build/runtime-acceptance/' <<<"$entries"; then
    echo "development scripts or runtime evidence leaked into $artifact" >&2
    exit 65
fi

# These names exist only in the compile-time acceptance implementation. Fabric
# nests shared/protocol inside the mod JAR, so inspect both the unpacked outer
# archive and every embedded JAR rather than searching compressed bytes.
scan_dir=$(mktemp -d "${TMPDIR:-/tmp}/trueuuid-release-scan.XXXXXX")
trap 'rm -rf "$scan_dir"' EXIT
mkdir -p "$scan_dir/outer" "$scan_dir/nested"
unzip -qq "$artifact" -d "$scan_dir/outer"
nested_index=0
while IFS= read -r -d '' nested_jar; do
    nested_index=$((nested_index + 1))
    nested_dir="$scan_dir/nested/$nested_index"
    mkdir -p "$nested_dir"
    unzip -qq "$nested_jar" -d "$nested_dir"
done < <(find "$scan_dir/outer" -type f -name '*.jar' -print0)
if grep -aRqE --exclude='*.jar' \
        'TRUEUUID_ACCEPTANCE_LOG|TRUEUUID_TEST_AUTO_CONFIRM_MIGRATION' \
        "$scan_dir/outer" "$scan_dir/nested"; then
    echo "matrix-only acceptance hooks leaked into $artifact" >&2
    exit 65
fi

if [[ -n "$srg_probe" ]]; then
    ./scripts/ci/verify-srg-mixin-jar.sh "$artifact" "$srg_probe"
fi

mkdir -p "$output_dir"
cp "$artifact" "$output_dir/"
(
    cd "$output_dir"
    sha256sum "$(basename "$artifact")" > SHA256SUMS
)

echo "Verified release JAR: $artifact"
