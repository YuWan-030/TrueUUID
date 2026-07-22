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

# Modrinth exposes the account represented by a personal access token through
# the USER_READ-scoped endpoint. Report that server-provided identity before
# probing VERSION_CREATE so a maintainer can catch a valid token belonging to
# the wrong publisher without revealing any credential material.
if ! modrinth_user_response=$(curl --silent --show-error \
    --proto '=https' \
    --tlsv1.2 \
    --connect-timeout 10 \
    --max-time 30 \
    --header "User-Agent: ${user_agent}" \
    --header "Authorization: ${MODRINTH_TOKEN}" \
    --write-out $'\n%{http_code}' \
    https://api.modrinth.com/v2/user); then
    echo "Modrinth publisher identity lookup could not reach the API" >&2
    exit 69
fi
modrinth_user_status=${modrinth_user_response##*$'\n'}
modrinth_user_body=${modrinth_user_response%$'\n'*}
if [[ "$modrinth_user_status" != 200 ]] || ! jq -e '
    (.username | type == "string" and length > 0) and
    (.id | type == "string" and length > 0)
' <<<"$modrinth_user_body" >/dev/null; then
    echo "Modrinth rejected MODRINTH_TOKEN during the USER_READ publisher identity lookup (HTTP ${modrinth_user_status})." >&2
    echo "Create a current PAT with both USER_READ and VERSION_CREATE scopes." >&2
    exit 77
fi
modrinth_username=$(jq -r '.username' <<<"$modrinth_user_body")
modrinth_user_id=$(jq -r '.id' <<<"$modrinth_user_body")
if [[ "$modrinth_username" == *$'\n'* || "$modrinth_username" == *$'\r'* ]]; then
    echo "Modrinth returned an unsafe publisher username" >&2
    exit 69
fi
echo "Publishing to Modrinth as @${modrinth_username} (user ${modrinth_user_id})."
if [[ -n "${GITHUB_OUTPUT:-}" ]]; then
    printf 'modrinth_username=%s\n' "$modrinth_username" >> "$GITHUB_OUTPUT"
fi

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
    echo "Create a CurseForge upload token and store it as the CURSEFORGE_TOKEN release-environment secret." >&2
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
echo "CurseForge does not expose the upload token owner's username; identity is reported only as an accepted token with upload access to project ${curseforge_project_id}."
