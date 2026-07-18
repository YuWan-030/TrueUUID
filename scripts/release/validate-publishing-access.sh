#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 1 ]]; then
    echo "usage: $0 <curseforge-project-id>" >&2
    exit 64
fi

curseforge_project_id=$1
: "${MODRINTH_TOKEN:?MODRINTH_TOKEN must be set}"
: "${MODRINTH_PROJECT_ID:?MODRINTH_PROJECT_ID must be set}"
: "${CURSEFORGE_TOKEN:?CURSEFORGE_TOKEN must be set}"

[[ "$MODRINTH_PROJECT_ID" =~ ^[0-9A-Za-z]{8}$ ]] || {
    echo "MODRINTH_PROJECT_ID must be the stable eight-character project ID" >&2
    exit 64
}
[[ "$curseforge_project_id" =~ ^[1-9][0-9]*$ ]] || {
    echo "invalid CurseForge project ID" >&2
    exit 64
}

user_agent="YuWan-030/TrueUUID publishing access check (https://github.com/YuWan-030/TrueUUID)"

# VERSION_CREATE is a token scope rather than a project permission. A listed
# version must contain a file, so this complete metadata request with no file is
# a non-creating scope probe: 400 means authorization passed and body validation
# rejected the intentional omission. Other statuses are credential failures.
modrinth_probe=$(jq -cn \
    --arg project_id "$MODRINTH_PROJECT_ID" \
    '{
        name: "TrueUUID publishing permission probe - no file",
        version_number: "0.0.0-trueuuid-publishing-permission-probe-no-file",
        changelog: "Permission probe only; this request intentionally has no file.",
        dependencies: [],
        game_versions: ["1.20.1"],
        version_type: "release",
        loaders: ["forge"],
        featured: false,
        status: "listed",
        project_id: $project_id,
        file_parts: [],
        environment: "client_and_server"
    }')
if ! modrinth_response=$(curl --silent --show-error \
    --proto '=https' \
    --tlsv1.2 \
    --connect-timeout 10 \
    --max-time 30 \
    --header "User-Agent: ${user_agent}" \
    --header "Authorization: ${MODRINTH_TOKEN}" \
    --write-out $'\n%{http_code}' \
    --request POST \
    --form-string "data=${modrinth_probe}" \
    https://api.modrinth.com/v2/version); then
    echo "Modrinth publishing access check could not reach the API" >&2
    exit 69
fi
modrinth_status=${modrinth_response##*$'\n'}
if [[ "$modrinth_status" != 400 ]]; then
    echo "Modrinth rejected the publishing credential or project permission probe (HTTP ${modrinth_status})." >&2
    echo "Required: a token with VERSION_CREATE whose owner can upload versions to the configured project." >&2
    exit 77
fi
echo "Verified Modrinth VERSION_CREATE and project upload access without creating a version."

# The CurseForge upload API requires its publisher token even for this read.
# This catches missing, malformed, expired, and revoked upload tokens before the
# project-specific no-file authorization probe below.
if ! curseforge_version_types=$(curl --silent --show-error \
    --proto '=https' \
    --tlsv1.2 \
    --connect-timeout 10 \
    --max-time 30 \
    --header "User-Agent: ${user_agent}" \
    --header "X-Api-Token: ${CURSEFORGE_TOKEN}" \
    --write-out $'\n%{http_code}' \
    'https://minecraft.curseforge.com/api/game/version-types?cache=true'); then
    echo "CurseForge token check could not reach the upload API" >&2
    exit 69
fi
curseforge_version_types_status=${curseforge_version_types##*$'\n'}
curseforge_version_types_body=${curseforge_version_types%$'\n'*}
if [[ "$curseforge_version_types_status" != 200 ]] ||
    ! jq -e 'type == "array" and length > 0' <<<"$curseforge_version_types_body" >/dev/null; then
    echo "CurseForge rejected CURSEFORGE_TOKEN (HTTP ${curseforge_version_types_status})." >&2
    echo "Create a CurseForge upload token and store it as the CURSEFORGE_TOKEN repository secret." >&2
    exit 77
fi
echo "Verified that CurseForge accepts CURSEFORGE_TOKEN."

# Use IDs from the same authenticated metadata API as the pinned publisher.
# This makes the upload probe structurally valid far enough to test the target
# project while keeping it non-creating by deliberately omitting the file.
if ! curseforge_game_versions=$(curl --silent --show-error \
    --proto '=https' \
    --tlsv1.2 \
    --connect-timeout 10 \
    --max-time 30 \
    --header "User-Agent: ${user_agent}" \
    --header "X-Api-Token: ${CURSEFORGE_TOKEN}" \
    --write-out $'\n%{http_code}' \
    'https://minecraft.curseforge.com/api/game/versions?cache=true'); then
    echo "CurseForge game-version lookup could not reach the upload API" >&2
    exit 69
fi
curseforge_game_versions_status=${curseforge_game_versions##*$'\n'}
curseforge_game_versions_body=${curseforge_game_versions%$'\n'*}
if [[ "$curseforge_game_versions_status" != 200 ]] ||
    ! jq -e 'type == "array" and length > 0' <<<"$curseforge_game_versions_body" >/dev/null; then
    echo "CurseForge game-version lookup failed (HTTP ${curseforge_game_versions_status})." >&2
    exit 77
fi

curseforge_minecraft_type_id=$(jq -r '
    [.[] | select(.slug == "minecraft-1-20") | .id][0] // empty
' <<<"$curseforge_version_types_body")
curseforge_loader_type_id=$(jq -r '
    [.[] | select((.slug // "") | startswith("modloader")) | .id][0] // empty
' <<<"$curseforge_version_types_body")
curseforge_minecraft_id=$(jq -r --argjson type_id "${curseforge_minecraft_type_id:-0}" '
    [.[] | select(.gameVersionTypeID == $type_id and .name == "1.20.1") | .id][0] // empty
' <<<"$curseforge_game_versions_body")
curseforge_forge_id=$(jq -r --argjson type_id "${curseforge_loader_type_id:-0}" '
    [.[] | select(.gameVersionTypeID == $type_id and ((.name // "") | ascii_downcase) == "forge") | .id][0] // empty
' <<<"$curseforge_game_versions_body")
if [[ ! "$curseforge_minecraft_id" =~ ^[1-9][0-9]*$ ]] ||
    [[ ! "$curseforge_forge_id" =~ ^[1-9][0-9]*$ ]]; then
    echo "CurseForge did not return the expected Minecraft 1.20.1 and Forge version IDs." >&2
    exit 77
fi

# CurseForge has no documented permission-introspection endpoint for publisher
# tokens. Its upload endpoint still checks the token and project role before it
# rejects this deliberately incomplete request for omitting the file field.
curseforge_probe=$(jq -cn \
    --argjson minecraft_id "$curseforge_minecraft_id" \
    --argjson loader_id "$curseforge_forge_id" \
    '{
    changelog: "Permission probe only; this request intentionally has no file.",
    changelogType: "markdown",
    displayName: "TrueUUID publishing permission probe - no file",
    gameVersions: [$minecraft_id, $loader_id],
    releaseType: "release"
}')
if ! curseforge_response=$(curl --silent --show-error \
    --proto '=https' \
    --tlsv1.2 \
    --connect-timeout 10 \
    --max-time 30 \
    --header "User-Agent: ${user_agent}" \
    --header "X-Api-Token: ${CURSEFORGE_TOKEN}" \
    --write-out $'\n%{http_code}' \
    --request POST \
    --form-string "metadata=${curseforge_probe}" \
    "https://minecraft.curseforge.com/api/projects/${curseforge_project_id}/upload-file"); then
    echo "CurseForge project access check could not reach the upload API" >&2
    exit 69
fi
curseforge_status=${curseforge_response##*$'\n'}
curseforge_body=${curseforge_response%$'\n'*}
if [[ "$curseforge_status" != 400 ]] || ! jq -e '
    ((.errorMessage // "") | test("file"; "i"))
' <<<"$curseforge_body" >/dev/null; then
    echo "CurseForge rejected the project upload permission probe (HTTP ${curseforge_status})." >&2
    echo "CurseForge error code: $(jq -r '.errorCode // "unknown"' <<<"$curseforge_body")" >&2
    echo "Required: an accepted upload token whose owner can upload files to project ${curseforge_project_id}." >&2
    exit 77
fi
echo "Verified CurseForge project upload access without creating a file."
