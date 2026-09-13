#!/usr/bin/env bash
# Prints the one CurseForge Bukkit game-version ID for a Minecraft version.
#
# The dev.bukkit.org game-version list mixes several taxonomies. Type 1 is the
# Minecraft version list that plugin project pages use. Other types hold
# thousands of unrelated entries that can reuse the same names, so only type 1
# is considered. Within it the exact patch wins, the minor line is the
# fallback, and anything else is refused, so the publisher and the access
# probe always agree on the same ID.
set -euo pipefail

if [[ $# -ne 2 ]]
then
    echo "usage: $0 <minecraft-version> <game-versions.json>" >&2
    exit 64
fi
minecraft_version=$1
versions_file=$2
[[ "$minecraft_version" =~ ^[0-9]+\.[0-9]+(\.[0-9]+)?$ ]] || {
    echo "invalid Minecraft version: $minecraft_version" >&2
    exit 64
}

jq -er --arg exact "$minecraft_version" --arg line "${minecraft_version%.*}" '
    if type != "array" then error("CurseForge game versions must be an array") else . end |
    [.[] | select(.gameVersionTypeID == 1)] as $minecraft |
    [$minecraft[] | select(.name == $exact)] as $exact_matches |
    [$minecraft[] | select(.name == $line)] as $line_matches |
    if ($exact_matches | length) == 1 then $exact_matches[0].id
    elif ($exact_matches | length) == 0 and ($line_matches | length) == 1 then $line_matches[0].id
    else error("CurseForge has no single Bukkit Minecraft game version for " + $exact)
    end
' "$versions_file"
