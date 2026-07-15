#!/usr/bin/env bash
set -euo pipefail

targets_file=release/targets.json

if [[ $# -ne 0 && ($# -ne 2 || $1 != "--approved") ]]; then
    echo "usage: $0 [--approved <target-id>]" >&2
    exit 64
fi

[[ -f "$targets_file" ]] || { echo "missing target manifest: $targets_file" >&2; exit 66; }

jq -e '
  .schema_version == 1 and
  (.curseforge_project_id | type == "number" and floor == . and . > 0) and
  (.targets | type == "array" and length > 0) and
  ([.targets[].id] | length == (unique | length)) and
  ([.targets[].artifact] | length == (unique | length)) and
  all(.targets[];
    ((keys | sort) == (["artifact", "build_task", "game_version", "id", "java", "loader", "release"] | sort)) and
    (.id | type == "string" and test("^[a-z0-9]+(?:[.-][a-z0-9]+)*$")) and
    (.build_task == (":platform:" + .id + ":build")) and
    (.id as $id |
      .artifact | type == "string" and
      startswith("platform/" + $id + "/build/libs/") and
      endswith(".jar") and
      contains("%VERSION%")) and
    (.loader | type == "string" and test("^[a-z0-9-]+$")) and
    (.game_version | type == "string" and test("^[0-9]+\\.[0-9]+(?:\\.[0-9]+)?$")) and
    (.java == 17 or .java == 21) and
    (.release | type == "boolean")
  )
' "$targets_file" >/dev/null || { echo "invalid release target manifest" >&2; exit 65; }

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
