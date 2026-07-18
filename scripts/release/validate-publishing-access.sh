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

# A listed Modrinth version must contain a file. A complete version request with
# no file is therefore a non-creating authorization probe: an authorized token
# reaches the file validation error, while a bad token/scope/project role is
# rejected first.
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
modrinth_body=${modrinth_response%$'\n'*}
if [[ "$modrinth_status" != 400 ]] || ! jq -e '
    .error == "invalid_input" and
    ((.description // "") | test("file"; "i"))
' <<<"$modrinth_body" >/dev/null; then
    echo "Modrinth rejected the publishing credential or project permission probe (HTTP ${modrinth_status})." >&2
    echo "Required: a token with VERSION_CREATE and permission to upload versions to the configured project ID." >&2
    exit 77
fi
echo "Verified Modrinth VERSION_CREATE access without creating a version."

# The CurseForge upload API requires its publisher token even for this read.
# This catches missing, malformed, expired, and revoked upload tokens before the
# project-specific no-file authorization probe below.
if ! curseforge_versions=$(curl --silent --show-error \
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
curseforge_versions_status=${curseforge_versions##*$'\n'}
curseforge_versions_body=${curseforge_versions%$'\n'*}
if [[ "$curseforge_versions_status" != 200 ]] ||
    ! jq -e 'type == "array" and length > 0' <<<"$curseforge_versions_body" >/dev/null; then
    echo "CurseForge rejected CURSEFORGE_TOKEN (HTTP ${curseforge_versions_status})." >&2
    echo "Create a CurseForge upload token and store it as the CURSEFORGE_TOKEN repository secret." >&2
    exit 77
fi
echo "Verified that CurseForge accepts CURSEFORGE_TOKEN."

# CurseForge has no documented permission-introspection endpoint for publisher
# tokens. Its upload endpoint still checks the token and project role before it
# rejects this deliberately incomplete request for omitting the file field.
curseforge_probe=$(jq -cn '{
    changelog: "Permission probe only; this request intentionally has no file.",
    changelogType: "markdown",
    displayName: "TrueUUID publishing permission probe - no file",
    gameVersions: [],
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
    echo "Required: an accepted upload token whose owner can upload files to project ${curseforge_project_id}." >&2
    exit 77
fi
echo "Verified CurseForge project upload access without creating a file."
