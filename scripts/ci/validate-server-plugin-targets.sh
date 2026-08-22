#!/usr/bin/env bash
set -euo pipefail

repo_root=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd -P)
manifest="$repo_root/ci/server-plugin-targets.json"

if [[ $# -ne 0 && ! ( $# -eq 2 && $1 == --target ) ]]; then
    echo "usage: $0 [--target <target-id>]" >&2
    exit 64
fi
command -v jq >/dev/null 2>&1 || { echo 'jq is required' >&2; exit 69; }
[[ -f "$manifest" ]] || { echo "missing server-plugin CI manifest: $manifest" >&2; exit 66; }

jq -e '
    keys == ["schema_version", "targets"] and
    .schema_version == 1 and
    (.targets | type == "array" and length > 0) and
    all(.targets[];
      (keys | sort) == ([
        "artifact_name", "build_script", "game_version", "id", "java",
        "module", "platform", "platform_name", "publish", "smoke_script",
        "support_status", "verify_script"
      ] | sort) and
      (.id | type == "string" and test("^(spigot|paper)-[0-9]+\\.[0-9]+(\\.[0-9]+)?$")) and
      (.platform == "spigot" or .platform == "paper") and
      (.platform_name == (if .platform == "spigot" then "Spigot" else "Paper" end)) and
      (.game_version | type == "string" and test("^[0-9]+\\.[0-9]+(\\.[0-9]+)?$")) and
      (.id == (.platform + "-" + .game_version)) and
      (.java == 17 or .java == 21) and
      (.module == (":plugin:" + .platform + ":" + .game_version)) and
      (.artifact_name == ("tested-" + .id)) and
      .support_status == "unsupported-candidate" and
      .publish == false and
      all(.build_script, .verify_script, .smoke_script;
        type == "string" and test("^scripts/ci/[a-z0-9][a-z0-9.-]*\\.sh$"))
    ) and
    (([.targets[].id] | unique | length) == (.targets | length)) and
    (([.targets[].module] | unique | length) == (.targets | length)) and
    (([.targets[].artifact_name] | unique | length) == (.targets | length))
' "$manifest" >/dev/null || {
    echo 'server-plugin CI manifest failed strict schema validation' >&2
    exit 65
}

while IFS= read -r target; do
    target_id=$(jq -r '.id' <<<"$target")
    platform=$(jq -r '.platform' <<<"$target")
    game_version=$(jq -r '.game_version' <<<"$target")
    module=$(jq -r '.module' <<<"$target")
    settings_module=${module#:}
    [[ -d "$repo_root/plugin/$platform/$game_version" ]] || {
        echo "server-plugin module directory is missing for $target_id" >&2
        exit 66
    }
    grep -Fq "include '$settings_module'" "$repo_root/settings.gradle" || {
        echo "settings.gradle does not include $module" >&2
        exit 65
    }
    for field in build_script verify_script smoke_script; do
        script=$(jq -r --arg field "$field" '.[$field]' <<<"$target")
        [[ -f "$repo_root/$script" && -x "$repo_root/$script" ]] || {
            echo "$target_id references a missing or non-executable $field: $script" >&2
            exit 66
        }
        grep -Fq "expected_target='$target_id'" "$repo_root/$script" || {
            echo "$target_id references a $field that is not pinned to that exact target: $script" >&2
            exit 65
        }
    done
done < <(jq -c '.targets[]' "$manifest")

if [[ $# -eq 2 ]]; then
    target_id=$2
    [[ "$target_id" =~ ^(spigot|paper)-[0-9]+\.[0-9]+(\.[0-9]+)?$ ]] || {
        echo "invalid server-plugin target id: $target_id" >&2
        exit 64
    }
    jq -ce --arg id "$target_id" '
        [.targets[] | select(.id == $id)] as $matches |
        if ($matches | length) == 1 then $matches[0]
        else error("server-plugin target must appear exactly once")
        end
    ' "$manifest" || exit 65
else
    echo 'Verified server-plugin CI target inventory.'
fi
