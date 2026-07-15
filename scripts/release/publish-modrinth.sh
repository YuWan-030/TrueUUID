#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 3 ]]; then
  echo "usage: $0 <target-id> <version> <changelog-file>" >&2
  exit 64
fi

target_id=$1
version=$2
changelog_file=$3
targets_file=release/targets.json

[[ "$version" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || { echo "invalid release version: $version" >&2; exit 64; }
[[ -f "$targets_file" ]] || { echo "missing target manifest: $targets_file" >&2; exit 66; }
[[ -f "$changelog_file" ]] || { echo "missing changelog: $changelog_file" >&2; exit 66; }

target=$(jq -ce --arg id "$target_id" '
  [.targets[] | select(.id == $id)] as $matches |
  if ($matches | length) != 1 then
    error("target must appear exactly once in release/targets.json")
  elif $matches[0].release != true then
    error("target is not release-approved")
  else
    $matches[0]
  end
' "$targets_file") || { echo "refusing unapproved target: $target_id" >&2; exit 65; }

: "${MODRINTH_TOKEN:?MODRINTH_TOKEN must be set}"
: "${MODRINTH_PROJECT_ID:?MODRINTH_PROJECT_ID must be set}"
[[ "$MODRINTH_PROJECT_ID" =~ ^[0-9A-Za-z]{8}$ ]] || { echo "MODRINTH_PROJECT_ID must be the stable eight-character project ID" >&2; exit 64; }

jar=$(jq -r --arg version "$version" '.artifact | gsub("%VERSION%"; $version)' <<<"$target")
loader=$(jq -r '.loader' <<<"$target")
minecraft_version=$(jq -r '.game_version' <<<"$target")
[[ -f "$jar" ]] || { echo "missing release artifact: $jar" >&2; exit 66; }

umask 077
version_number="${version}+${target_id}"
user_agent="YuWan-030/TrueUUID/${version} (https://github.com/YuWan-030/TrueUUID)"
encoded_version=$(jq -rn --arg value "$version_number" '$value | @uri')
existing=$(curl --silent --show-error \
  --proto '=https' \
  --tlsv1.2 \
  --connect-timeout 10 \
  --max-time 30 \
  --header "User-Agent: ${user_agent}" \
  --header "Authorization: ${MODRINTH_TOKEN}" \
  --write-out $'\n%{http_code}' \
  "https://api.modrinth.com/v2/project/${MODRINTH_PROJECT_ID}/version/${encoded_version}")
existing_status=${existing##*$'\n'}
existing_body=${existing%$'\n'*}

case "$existing_status" in
  200)
    jar_sha512=$(sha512sum "$jar")
    jar_sha512=${jar_sha512%% *}
    if ! jq -e \
      --arg project_id "$MODRINTH_PROJECT_ID" \
      --arg version_number "$version_number" \
      --arg sha512 "$jar_sha512" \
      '.project_id == $project_id and
       .version_number == $version_number and
       any(.files[]; .primary == true and .hashes.sha512 == $sha512)' \
      <<<"$existing_body" >/dev/null; then
      echo "existing Modrinth version does not match the release artifact" >&2
      exit 73
    fi
    echo "Modrinth already has the identical ${target_id} artifact."
    exit 0
    ;;
  404) ;;
  *)
    echo "Modrinth preflight failed with HTTP ${existing_status}" >&2
    exit 69
    ;;
esac

changelog=$(<"$changelog_file")
metadata=$(jq -cn \
  --arg project_id "$MODRINTH_PROJECT_ID" \
  --arg target_id "$target_id" \
  --arg version "$version" \
  --arg loader "$loader" \
  --arg minecraft_version "$minecraft_version" \
  --arg changelog "$changelog" \
  '{
    name: ("TrueUUID " + $version + " for " + $target_id),
    version_number: ($version + "+" + $target_id),
    changelog: $changelog,
    dependencies: [],
    game_versions: [$minecraft_version],
    version_type: "release",
    loaders: [$loader],
    featured: true,
    status: "listed",
    project_id: $project_id,
    file_parts: ["file"],
    primary_file: "file",
    environment: "client_and_server"
  }')

curl --fail --silent --show-error \
  --proto '=https' \
  --tlsv1.2 \
  --connect-timeout 10 \
  --max-time 300 \
  --header "User-Agent: ${user_agent}" \
  --header "Authorization: ${MODRINTH_TOKEN}" \
  --form-string "data=${metadata}" \
  --form "file=@${jar}" \
  https://api.modrinth.com/v2/version >/dev/null

echo "Published ${target_id} to Modrinth."
