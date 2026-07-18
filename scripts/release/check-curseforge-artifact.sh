#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 2 ]]; then
    echo "usage: $0 <curseforge-project-id> <jar>" >&2
    exit 64
fi

project_id=$1
jar_path=$2
[[ "$project_id" =~ ^[1-9][0-9]*$ ]] || { echo "invalid CurseForge project ID" >&2; exit 64; }
[[ -f "$jar_path" ]] || { echo "missing CurseForge artifact: $jar_path" >&2; exit 66; }

filename=$(basename "$jar_path")
[[ "$filename" =~ ^[A-Za-z0-9._+-]+\.jar$ ]] || {
    echo "unsafe CurseForge artifact filename: $filename" >&2
    exit 64
}

api_root="https://www.curseforge.com/api/v1/mods/${project_id}"
user_agent="YuWan-030/TrueUUID release preflight (https://github.com/YuWan-030/TrueUUID)"
page_size=50
index=0
declare -a matches=()

while true; do
    response=$(curl --fail --silent --show-error --location \
        --proto '=https' \
        --proto-redir '=https' \
        --tlsv1.2 \
        --connect-timeout 10 \
        --max-time 30 \
        --header "User-Agent: ${user_agent}" \
        "${api_root}/files?pageSize=${page_size}&index=${index}")
    jq -e '
        (.data | type == "array") and
        (.data | all(.[]; (.id | type == "number") and .id > 0 and
                          (.fileName | type == "string"))) and
        (.pagination.index | type == "number") and
        (.pagination.pageSize | type == "number") and
        (.pagination.totalCount | type == "number")
    ' <<<"$response" >/dev/null || {
        echo "unexpected CurseForge files response" >&2
        exit 69
    }

    mapfile -t page_matches < <(jq -r --arg filename "$filename" \
        '.data[] | select(.fileName == $filename) | .id' <<<"$response")
    matches+=("${page_matches[@]}")

    total_count=$(jq -r '.pagination.totalCount' <<<"$response")
    index=$((index + page_size))
    (( index < total_count )) || break
done

case ${#matches[@]} in
    0)
        echo missing
        ;;
    1)
        remote_file=$(mktemp)
        cleanup() {
            rm -f "$remote_file"
        }
        trap cleanup EXIT
        curl --fail --silent --show-error --location \
            --proto '=https' \
            --proto-redir '=https' \
            --tlsv1.2 \
            --connect-timeout 10 \
            --max-time 300 \
            --header "User-Agent: ${user_agent}" \
            --output "$remote_file" \
            "${api_root}/files/${matches[0]}/download"
        if ! cmp --silent "$jar_path" "$remote_file"; then
            echo "CurseForge already has $filename with different bytes" >&2
            exit 73
        fi
        echo identical
        ;;
    *)
        echo "CurseForge returned multiple files named $filename" >&2
        exit 73
        ;;
esac
