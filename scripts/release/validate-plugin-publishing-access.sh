#!/usr/bin/env bash
# Non-creating credential probes for the server-plugin stores.
#
# The Release workflow runs this before the self-test matrix, beside the mod
# store probes. Nothing is uploaded or created. When no plugin is approved for
# the release, no plugin store is contacted.
set -euo pipefail

die() {
    local status=$1
    shift
    printf '%s\n' "$*" >&2
    exit "$status"
}

manifest=ci/server-plugin-targets.json
mapfile -t approved < <(jq -r '.targets[] | select(.release == true) | .id' "$manifest")
if (( ${#approved[@]} == 0 ))
then
    echo 'No server plugin is approved for this release. Plugin store probes were skipped.'
    exit 0
fi

user_agent="YuWan-030/TrueUUID publishing access check (https://github.com/YuWan-030/TrueUUID)"
umask 077
work_dir=$(mktemp -d)
trap 'rm -rf -- "$work_dir"' EXIT

# CurseForge keeps Bukkit plugins in their own project class, served by the
# dev.bukkit.org upload API. Probe it with the same token as the mod project.
: "${CURSEFORGE_TOKEN:?CURSEFORGE_TOKEN must be set}"
curseforge_project_id=${CURSEFORGE_ID_BUKKIT:-}
[[ "$curseforge_project_id" =~ ^[1-9][0-9]*$ ]] ||
    die 65 'approved server plugins require the CURSEFORGE_ID_BUKKIT Actions variable'
versions_status=$(curl --silent --show-error --proto '=https' --tlsv1.2 \
    --connect-timeout 10 --max-time 30 \
    --header "User-Agent: ${user_agent}" \
    --header "X-Api-Token: ${CURSEFORGE_TOKEN}" \
    --output "$work_dir/game-versions.json" \
    --write-out '%{http_code}' \
    https://dev.bukkit.org/api/game/versions) || die 69 'CurseForge plugin API could not be reached'
[[ "$versions_status" == 200 ]] || die 77 "CurseForge plugin API rejected CURSEFORGE_TOKEN (HTTP ${versions_status})"
first_version_id=
for target_id in "${approved[@]}"
do
    game_version=$(jq -r --arg id "$target_id" '.targets[] | select(.id == $id) | .game_version' "$manifest")
    version_id=$(./scripts/release/curseforge-plugin-game-version.sh "$game_version" "$work_dir/game-versions.json") ||
        die 77 "CurseForge plugins cannot represent Minecraft ${game_version} for ${target_id}"
    first_version_id=${first_version_id:-$version_id}
done
jq -n --argjson version_id "$first_version_id" \
    '{changelog: "Permission probe only. This request intentionally has no file.", changelogType: "markdown",
      displayName: "TrueUUID publishing permission probe - no file", gameVersions: [$version_id],
      releaseType: "release"}' > "$work_dir/metadata.json"
probe=$(curl --silent --show-error --proto '=https' --tlsv1.2 \
    --connect-timeout 10 --max-time 30 \
    --header "User-Agent: ${user_agent}" \
    --header "X-Api-Token: ${CURSEFORGE_TOKEN}" \
    --form "metadata=<${work_dir}/metadata.json;type=application/json" \
    --write-out $'\n%{http_code}' \
    "https://dev.bukkit.org/api/projects/${curseforge_project_id}/upload-file") ||
    die 69 'CurseForge plugin upload probe could not reach the API'
probe_status=${probe##*$'\n'}
probe_body=${probe%$'\n'*}
if [[ "$probe_status" != 400 ]] || ! jq -e '(.errorMessage // "") | test("file"; "i")' <<<"$probe_body" >/dev/null
then
    die 77 "CurseForge rejected the plugin project upload probe for ${curseforge_project_id} (HTTP ${probe_status})"
fi
echo "Verified CurseForge plugin project ${curseforge_project_id} upload access without creating a file."

mapfile -t hangar_targets < <(jq -r '.targets[] | select(.release == true and .hangar_platform != null) | .id' "$manifest")
if (( ${#hangar_targets[@]} == 0 ))
then
    echo 'No approved plugin is distributed on Hangar.'
    exit 0
fi

# shellcheck source=scripts/release/hangar-api.sh
source ./scripts/release/hangar-api.sh
owner=$(jq -r '.distribution.hangar_owner' "$manifest")
slug=$(jq -r '.distribution.hangar_slug' "$manifest")
channel=$(jq -r '.distribution.hangar_channel' "$manifest")
jwt=$(hangar_authenticate "$user_agent")

project=$(hangar_get "$user_agent" "$jwt" "/v1/projects/${slug}")
[[ "${project##*$'\n'}" == 200 ]] || die 77 "Hangar project ${owner}/${slug} is not visible to HANGAR_API_TOKEN"
jq -e --arg owner "$owner" --arg slug "$slug" \
    '(.namespace.owner | ascii_downcase) == ($owner | ascii_downcase) and
     (.namespace.slug | ascii_downcase) == ($slug | ascii_downcase)' <<<"${project%$'\n'*}" >/dev/null ||
    die 77 "Hangar project ${slug} is not owned by ${owner}"

permissions=$(hangar_get "$user_agent" "$jwt" "/v1/permissions?project=${slug}")
[[ "${permissions##*$'\n'}" == 200 ]] || die 77 'Hangar permission lookup failed'
jq -e '.permissions | index("create_version") != null' <<<"${permissions%$'\n'*}" >/dev/null ||
    die 77 "HANGAR_API_TOKEN lacks create_version on ${owner}/${slug}"

channels=$(hangar_get "$user_agent" "$jwt" "/internal/channels/${slug}")
if [[ "${channels##*$'\n'}" == 200 ]]
then
    jq -e --arg channel "$channel" 'any(.[]; .name == $channel)' <<<"${channels%$'\n'*}" >/dev/null ||
        die 77 "Hangar project ${owner}/${slug} has no ${channel} channel"
    echo "Verified Hangar channel ${channel}."
else
    # The channel list is an internal endpoint that an API session may not be
    # allowed to read. Hangar still rejects an upload to a missing channel, and
    # the publisher is safe to rerun after the channel is created.
    echo "::warning title=Hangar channel not pre-verified::The ${channel} channel list for ${owner}/${slug} returned HTTP ${channels##*$'\n'}. The upload step verifies it instead."
fi
echo "Verified Hangar create_version access to ${owner}/${slug} without creating a version."
