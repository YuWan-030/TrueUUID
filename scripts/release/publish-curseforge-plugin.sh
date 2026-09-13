#!/usr/bin/env bash
# Uploads one approved server-plugin JAR to the CurseForge Bukkit plugin project.
#
# CurseForge keeps Bukkit plugins in a separate project class from the mod
# project, served by the dev.bukkit.org upload API. The Release workflow runs
# check-curseforge-artifact.sh first and calls this script only for a missing
# file, and the script makes exactly one POST so an ambiguous timeout can never
# create a duplicate upload.
set -euo pipefail

die() {
    local status=$1
    shift
    printf '%s\n' "$*" >&2
    exit "$status"
}

[[ $# -eq 3 ]] || die 64 "usage: $0 <target-id> <version> <changelog-file>"
target_id=$1
version=$2
changelog_file=$3
[[ "$version" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || die 64 "invalid release version: $version"
[[ -f "$changelog_file" ]] || die 66 "missing changelog: $changelog_file"
./scripts/release/validate-changelog.sh "$changelog_file"
: "${CURSEFORGE_TOKEN:?CURSEFORGE_TOKEN must be set}"

target=$(./scripts/release/resolve-release-target.sh "$target_id")
[[ "$(jq -r '.kind' <<<"$target")" == plugin ]] || die 65 "$target_id is not a server plugin"
[[ "$(jq -r '.curseforge_project_variable' <<<"$target")" == CURSEFORGE_ID_BUKKIT ]] ||
    die 65 "$target_id is not published to the CurseForge plugin project"
project_id=${CURSEFORGE_ID_BUKKIT:-}
[[ "$project_id" =~ ^[1-9][0-9]*$ ]] ||
    die 65 'the CURSEFORGE_ID_BUKKIT Actions variable must hold the CurseForge plugin project ID'
jar=$(jq -r --arg version "$version" '.artifact | split("%VERSION%") | join($version)' <<<"$target")
[[ -f "$jar" ]] || die 66 "missing release artifact: $jar"
game_version=$(jq -r '.game_version' <<<"$target")
loader_name=$(jq -r '.loader_name' <<<"$target")
display_name="TrueUUID ${version} for ${loader_name} ${game_version}"
api_root=https://dev.bukkit.org/api
user_agent="YuWan-030/TrueUUID/${version} (https://github.com/YuWan-030/TrueUUID)"

umask 077
work_dir=$(mktemp -d)
trap 'rm -rf -- "$work_dir"' EXIT

curl --fail --silent --show-error --proto '=https' --tlsv1.2 \
    --connect-timeout 10 --max-time 30 \
    --header "User-Agent: ${user_agent}" \
    --header "X-Api-Token: ${CURSEFORGE_TOKEN}" \
    --output "$work_dir/game-versions.json" \
    "$api_root/game/versions"
game_version_id=$(./scripts/release/curseforge-plugin-game-version.sh "$game_version" "$work_dir/game-versions.json")

jq -n \
    --rawfile changelog "$changelog_file" \
    --arg display_name "$display_name" \
    --argjson game_version_id "$game_version_id" \
    '{changelog: $changelog, changelogType: "markdown", displayName: $display_name,
      gameVersions: [$game_version_id], releaseType: "release"}' > "$work_dir/metadata.json"

response=$(curl --silent --show-error --proto '=https' --tlsv1.2 \
    --connect-timeout 10 --max-time 300 \
    --header "User-Agent: ${user_agent}" \
    --header "X-Api-Token: ${CURSEFORGE_TOKEN}" \
    --form "metadata=<$work_dir/metadata.json;type=application/json" \
    --form "file=@${jar};type=application/java-archive" \
    --write-out $'\n%{http_code}' \
    "$api_root/projects/${project_id}/upload-file")
status=${response##*$'\n'}
body=${response%$'\n'*}
if [[ "$status" != 200 ]] || ! jq -e '.id | type == "number" and . > 0' <<<"$body" >/dev/null
then
    echo "CurseForge plugin upload failed with HTTP ${status}" >&2
    jq -r '.errorMessage // "No structured API error returned."' <<<"$body" >&2 2>/dev/null || true
    exit 69
fi
echo "Published ${target_id} to CurseForge plugin project ${project_id} as file $(jq -r '.id' <<<"$body")."
