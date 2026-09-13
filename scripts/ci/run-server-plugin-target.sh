#!/usr/bin/env bash
# Dispatches one server-plugin CI operation to that target's pinned script.
# Workflows call only this entry point, so adding a target never copies a job.
set -euo pipefail

usage() {
    echo "usage: $0 <target-id> build | <target-id> verify <output-directory> | <target-id> smoke <output-directory>" >&2
    exit 64
}

repo_root=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd -P)
[[ $# -ge 2 ]] || usage
target_id=$1
operation=$2
if [[ "$operation" == build ]]
then
    [[ $# -eq 2 ]] || usage
elif [[ "$operation" == verify || "$operation" == smoke ]]
then
    [[ $# -eq 3 && -n "$3" ]] || usage
else
    usage
fi

target=$("$repo_root/scripts/ci/validate-server-plugin-targets.sh" --target "$target_id")
script="$repo_root/$(jq -r '.ci_script' <<<"$target")"
export TRUEUUID_SERVER_PLUGIN_TARGET="$target_id"
if [[ "$operation" == build ]]
then
    exec "$script" build
fi
exec "$script" "$operation" "$3"
