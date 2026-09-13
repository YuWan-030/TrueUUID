#!/usr/bin/env bash
# Publishes one approved Paper plugin target to Hangar with the frozen changelog.
#
# Hangar receives the same checksum-verified JAR and byte-identical changelog as
# GitHub, Modrinth, and CurseForge. A rerun is safe: an existing version must
# carry the identical file hash, channel, and description, or the run fails.
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

# shellcheck source=scripts/release/hangar-api.sh
source ./scripts/release/hangar-api.sh

target=$(./scripts/release/resolve-release-target.sh "$target_id")
jq -e '.hangar != null' <<<"$target" >/dev/null || die 65 "$target_id is not distributed on Hangar"
slug=$(jq -r '.hangar.slug' <<<"$target")
channel=$(jq -r '.hangar.channel' <<<"$target")
platform=$(jq -r '.hangar.platform' <<<"$target")
game_version=$(jq -r '.game_version' <<<"$target")
jar=$(jq -r --arg version "$version" '.artifact | split("%VERSION%") | join($version)' <<<"$target")
[[ -f "$jar" ]] || die 66 "missing release artifact: $jar"
jar_sha256=$(sha256sum "$jar" | awk '{print $1}')
version_name="${version}+${target_id}"
user_agent="YuWan-030/TrueUUID/${version} (https://github.com/YuWan-030/TrueUUID)"

umask 077
work_dir=$(mktemp -d)
trap 'rm -rf -- "$work_dir"' EXIT

jwt=$(hangar_authenticate "$user_agent")
encoded_version=$(jq -rn --arg value "$version_name" '$value | @uri')

matches_release() {
    jq -e \
        --arg name "$version_name" \
        --arg channel "$channel" \
        --arg platform "$platform" \
        --arg sha256 "$jar_sha256" \
        --rawfile changelog "$changelog_file" \
        '.name == $name and .channel.name == $channel and
         (.description | sub("\\s+$"; "")) == ($changelog | sub("\\s+$"; "")) and
         .downloads[$platform].fileInfo.sha256Hash == $sha256' >/dev/null
}

existing=$(hangar_get "$user_agent" "$jwt" "/v1/projects/${slug}/versions/${encoded_version}")
existing_status=${existing##*$'\n'}
existing_body=${existing%$'\n'*}
if [[ "$existing_status" == 200 ]]
then
    matches_release <<<"$existing_body" ||
        die 73 "existing Hangar version ${version_name} does not match the release artifact, channel, and changelog"
    echo "Hangar already has the identical ${target_id} artifact."
    exit 0
elif [[ "$existing_status" != 404 ]]
then
    die 69 "Hangar version preflight failed with HTTP ${existing_status}"
fi

jq -n \
    --arg version "$version_name" \
    --arg channel "$channel" \
    --arg platform "$platform" \
    --arg game_version "$game_version" \
    --rawfile changelog "$changelog_file" \
    '{version: $version, channel: $channel, description: $changelog,
      files: [{platforms: [$platform]}],
      platformDependencies: {($platform): [$game_version]},
      pluginDependencies: {($platform): [{name: "ProtocolLib", required: true,
          externalUrl: "https://github.com/dmulloy2/ProtocolLib/releases"}]}}' \
    > "$work_dir/version-upload.json"

response=$(curl --silent --show-error --proto '=https' --tlsv1.2 \
    --connect-timeout 10 --max-time 300 \
    --header "User-Agent: ${user_agent}" \
    --header "Authorization: ${jwt}" \
    --form "versionUpload=<${work_dir}/version-upload.json;type=application/json" \
    --form "files=@${jar};type=application/java-archive" \
    --write-out $'\n%{http_code}' \
    "${hangar_api}/projects/${slug}/upload")
status=${response##*$'\n'}
body=${response%$'\n'*}
if [[ "$status" != 200 ]]
then
    echo "Hangar upload failed with HTTP ${status}" >&2
    jq -r '.message // "No structured API error returned."' <<<"$body" >&2 2>/dev/null || true
    exit 69
fi

published=$(hangar_get "$user_agent" "$jwt" "/v1/projects/${slug}/versions/${encoded_version}")
[[ "${published##*$'\n'}" == 200 ]] || die 73 'Hangar accepted the upload but the version could not be read back'
matches_release <<<"${published%$'\n'*}" ||
    die 73 'Hangar returned a version that does not match the requested artifact and changelog'
echo "Published ${target_id} to Hangar channel ${channel} as ${version_name}."
