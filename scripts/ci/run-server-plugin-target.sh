#!/usr/bin/env bash
set -euo pipefail

if [[ $# -lt 2 || $# -gt 3 ]]; then
    echo "usage: $0 <target-id> <build|verify|smoke> [output-directory]" >&2
    exit 64
fi

repo_root=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd -P)
target_id=$1
operation=$2
output_dir=${3:-}
target=$("$repo_root/scripts/ci/validate-server-plugin-targets.sh" --target "$target_id")

case "$operation" in
    build)
        [[ $# -eq 2 ]] || { echo 'build does not accept an output directory' >&2; exit 64; }
        script_field=build_script
        ;;
    verify|smoke)
        [[ $# -eq 3 && -n "$output_dir" ]] || {
            echo "$operation requires an output directory" >&2
            exit 64
        }
        script_field="${operation}_script"
        ;;
    *)
        echo "invalid server-plugin operation: $operation" >&2
        exit 64
        ;;
esac

script=$(jq -r --arg field "$script_field" '.[$field]' <<<"$target")
export TRUEUUID_SERVER_PLUGIN_TARGET="$target_id"
if [[ "$operation" == build ]]; then
    exec "$repo_root/$script"
fi
exec "$repo_root/$script" "$output_dir"
