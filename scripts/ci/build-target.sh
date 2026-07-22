#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 1 ]]; then
    echo "usage: $0 <target-id>" >&2
    exit 64
fi

root=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
target_id=$1
cd "$root"
./scripts/release/validate-targets.sh

target=$(jq -ce --arg id "$target_id" '
  [.targets[] | select(.id == $id)] as $matches |
  if ($matches | length) == 1 then $matches[0]
  else error("target must appear exactly once")
  end
' release/targets.json) || {
    echo "unknown build target: $target_id" >&2
    exit 65
}

build_task=$(jq -r '.build_task' <<<"$target")
standalone=$(jq -r '.standalone // false' <<<"$target")
gradle_flags=(--no-daemon --stacktrace)
[[ -z "${TRUEUUID_OFFLINE:-}" ]] || gradle_flags+=(--offline)
[[ "${TRUEUUID_ACCEPTANCE_HOOKS:-}" != 1 ]] || gradle_flags+=(-PtrueuuidAcceptanceHooks=true)

if [[ "$standalone" == true ]]; then
    module="$root/platform/$target_id"
    [[ -x "$module/gradlew" && -f "$module/settings.gradle" ]] || {
        echo "standalone target has no executable wrapper/settings: $target_id" >&2
        exit 66
    }
    ./gradlew :shared:protocol:test "${gradle_flags[@]}"
    "$module/gradlew" -p "$module" "$build_task" "${gradle_flags[@]}"
else
    ./gradlew :shared:protocol:test "$build_task" "${gradle_flags[@]}"
fi
