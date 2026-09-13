#!/usr/bin/env bash
set -euo pipefail

repo_root=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd -P)
manifest="$repo_root/ci/server-plugin-targets.json"

if [[ $# -ne 0 && ! ( $# -eq 2 && ( $1 == --target || $1 == --approved ) ) ]]
then
    echo "usage: $0 [--target <target-id> | --approved <target-id>]" >&2
    exit 64
fi
command -v jq >/dev/null 2>&1 || { echo 'jq is required' >&2; exit 69; }
[[ -f "$manifest" ]] || { echo "missing server-plugin CI manifest: $manifest" >&2; exit 66; }

project_version=$(sed -n 's/^mod_version=//p' "$repo_root/gradle.properties" | tr -d '[:space:]')
[[ "$project_version" =~ ^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$ ]] || {
    echo 'gradle.properties must contain one strict SemVer mod_version' >&2
    exit 65
}

# A plugin approval is bound to one repository version, exactly like the mod
# manifest. Only a supported target may be approved, so an unsupported
# candidate can never enter the release-* artifact namespace.
jq -e --arg project_version "$project_version" '
    (keys | sort) == (["release_version", "schema_version", "targets"] | sort) and
    .schema_version == 2 and
    .release_version == $project_version and
    (.targets | type == "array" and length > 0) and
    all(.targets[];
      (keys | sort) == ([
        "build_script", "game_version", "id", "java", "modrinth_loaders",
        "module", "platform", "platform_name", "release", "smoke_script",
        "support_status", "verify_script"
      ] | sort) and
      (.id | type == "string" and test("^(spigot|paper)-[0-9]+\\.[0-9]+(\\.[0-9]+)?$")) and
      (.platform == "spigot" or .platform == "paper") and
      (.platform_name == (if .platform == "spigot" then "Spigot" else "Paper" end)) and
      (.game_version | type == "string" and test("^[0-9]+\\.[0-9]+(\\.[0-9]+)?$")) and
      (.id == (.platform + "-" + .game_version)) and
      (.java == 17 or .java == 21) and
      (.module == (":plugin:" + .platform + ":" + .game_version)) and
      (.support_status == "unsupported-candidate" or .support_status == "supported") and
      (.release | type == "boolean") and
      (.release == false or .support_status == "supported") and
      (.platform as $platform | .modrinth_loaders |
        type == "array" and length > 0 and
        (unique | length) == length and
        all(.[]; . == "spigot" or . == "paper" or . == "bukkit") and
        index($platform) != null) and
      all(.build_script, .verify_script, .smoke_script;
        type == "string" and test("^scripts/ci/[a-z0-9][a-z0-9.-]*\\.sh$"))
    ) and
    (([.targets[].id] | unique | length) == (.targets | length)) and
    (([.targets[].module] | unique | length) == (.targets | length))
' "$manifest" >/dev/null || {
    echo 'server-plugin CI manifest failed strict schema validation' >&2
    exit 65
}

while IFS= read -r target
do
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
    for field in build_script verify_script smoke_script
    do
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

if [[ $# -eq 0 ]]
then
    echo 'Verified server-plugin CI target inventory.'
    exit 0
fi

target_id=$2
[[ "$target_id" =~ ^(spigot|paper)-[0-9]+\.[0-9]+(\.[0-9]+)?$ ]] || {
    echo "invalid server-plugin target id: $target_id" >&2
    exit 64
}
# stdout is a machine-readable API. The derived artifact path, manifest marker,
# and Actions artifact namespace are defined here, and the plugin's
# build.gradle applies the same support_status rule.
jq -ce --arg id "$target_id" --arg mode "$1" '
    [.targets[] | select(.id == $id)] as $matches |
    if ($matches | length) != 1 then
        error("server-plugin target must appear exactly once")
    elif $mode == "--approved" and $matches[0].release != true then
        error("server-plugin target is not release-approved")
    else
        $matches[0] |
        ("plugin/" + .platform + "/" + .game_version + "/build/libs/trueuuid-") as $libs |
        . + {
            artifact: (if .support_status == "supported"
                then $libs + "%VERSION%-" + .id + ".jar"
                else $libs + .id + "-candidate.jar" end),
            manifest_status: (if .support_status == "supported"
                then "SUPPORTED" else "UNSUPPORTED-CANDIDATE" end),
            artifact_name: ((if .release then "release-" else "tested-" end) + .id)
        }
    end
' "$manifest" || { echo "refusing server-plugin target: $target_id" >&2; exit 65; }
