#!/usr/bin/env bash
set -euo pipefail

targets_file=release/targets.json

if [[ $# -ne 0 && ($# -ne 2 || $1 != "--approved") ]]; then
    echo "usage: $0 [--approved <target-id>]" >&2
    exit 64
fi

[[ -f "$targets_file" ]] || { echo "missing target manifest: $targets_file" >&2; exit 66; }
[[ -f gradle.properties ]] || { echo "missing gradle.properties" >&2; exit 66; }

project_version=$(sed -n 's/^mod_version=//p' gradle.properties)
[[ "$project_version" =~ ^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$ ]] || {
    echo "gradle.properties must contain one strict SemVer mod_version" >&2
    exit 65
}

jq -e --arg project_version "$project_version" '
  ((keys | sort) == (["curseforge_project_id", "release_version", "schema_version", "targets"] | sort)) and
  .schema_version == 4 and
  (.release_version | type == "string" and test("^(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)$")) and
  .release_version == $project_version and
  (.curseforge_project_id | type == "number" and floor == . and . > 0) and
  (.targets | type == "array" and length > 0) and
  ([.targets[].id] | length == (unique | length)) and
  ([.targets[].artifact] | length == (unique | length)) and
  all(.targets[];
    ((keys | sort) ==
      ((["artifact", "build_task", "game_version", "id", "java", "loader", "metadata", "release", "runtime_loader_version", "srg_probe"] +
        (if (.standalone // false) then ["standalone"] else [] end)) | sort)) and
    (.id | type == "string" and test("^[a-z0-9]+(?:[.-][a-z0-9]+)*$")) and
    ((.standalone // false) | type == "boolean") and
    (if (.standalone // false)
      then (.build_task == "build")
      else (.build_task == (":platform:" + .id + ":build"))
      end) and
    (.id as $id |
      .artifact | type == "string" and
      startswith("platform/" + $id + "/build/libs/") and
      endswith(".jar") and
      contains("%VERSION%")) and
    (.loader | type == "string" and test("^[a-z0-9-]+$")) and
    (.game_version | type == "string" and test("^[0-9]+\\.[0-9]+(?:\\.[0-9]+)?$")) and
    (.runtime_loader_version | type == "string" and test("^[0-9]+(?:\\.[0-9]+)+(?:-[A-Za-z0-9.-]+)?$")) and
    (.java == 17 or .java == 21) and
    (.metadata == "META-INF/mods.toml" or
      .metadata == "META-INF/neoforge.mods.toml" or
      .metadata == "fabric.mod.json") and
    (.srg_probe == null or
      (.srg_probe | type == "string" and
        test("^[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)+$"))) and
    (.id as $id |
      if (["forge-1.20.1", "forge-1.20.2", "forge-1.20.4", "neoforge-1.20.1"] | index($id))
      then (.srg_probe != null)
      else (.srg_probe == null)
      end) and
    (.release | type == "boolean")
  )
' "$targets_file" >/dev/null || { echo "invalid release target manifest" >&2; exit 65; }

manifest_targets=$(jq -r '.targets[].id' "$targets_file" | sort)
module_targets=$(find platform -mindepth 2 -maxdepth 2 -name build.gradle -printf '%h\n' |
    sed 's|^platform/||' | sort)
if [[ "$manifest_targets" != "$module_targets" ]]; then
    echo "release target manifest must list every platform module exactly once" >&2
    diff -u <(printf '%s\n' "$module_targets") <(printf '%s\n' "$manifest_targets") >&2 || true
    exit 65
fi

while IFS=$'\t' read -r target_id standalone; do
    module="platform/$target_id"
    if [[ "$standalone" == true ]]; then
        [[ -f "$module/settings.gradle" && -x "$module/gradlew" ]] || {
            echo "standalone target must own executable wrapper and settings: $target_id" >&2
            exit 65
        }
    elif [[ -f "$module/settings.gradle" ]]; then
        echo "target with its own settings.gradle must declare standalone=true: $target_id" >&2
        exit 65
    fi
done < <(jq -r '.targets[] | [.id, (.standalone // false)] | @tsv' "$targets_file")

if [[ $# -eq 0 ]]; then
    exit 0
fi

target_id=$2
jq -ce --arg id "$target_id" '
  [.targets[] | select(.id == $id)] as $matches |
  if ($matches | length) != 1 then
    error("target must appear exactly once")
  elif $matches[0].release != true then
    error("target is not release-approved")
  else
    $matches[0]
  end
' "$targets_file" || { echo "refusing unapproved target: $target_id" >&2; exit 65; }
