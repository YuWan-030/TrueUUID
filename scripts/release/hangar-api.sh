# shellcheck shell=bash
# Hangar API helpers shared by the publisher and the access probe.
#
# The API key is exchanged for a short-lived JWT. It is passed to curl on
# standard input, never as a command-line argument, so it cannot appear in a
# process listing. Hangar expects the raw JWT in the Authorization header.

hangar_root=https://hangar.papermc.io/api
hangar_api="${hangar_root}/v1"

hangar_authenticate() {
    local user_agent=$1
    local response status body
    : "${HANGAR_API_TOKEN:?HANGAR_API_TOKEN must be set}"
    if ! response=$(printf '%s' "$HANGAR_API_TOKEN" | curl --silent --show-error \
        --proto '=https' --tlsv1.2 --connect-timeout 10 --max-time 30 \
        --header "User-Agent: ${user_agent}" \
        --request POST \
        --url-query 'apiKey@-' \
        --write-out $'\n%{http_code}' \
        "${hangar_api}/authenticate")
    then
        echo 'Hangar authentication could not reach the API' >&2
        return 69
    fi
    status=${response##*$'\n'}
    body=${response%$'\n'*}
    if [[ "$status" != 200 ]] || ! jq -e '.token | type == "string" and length > 0' <<<"$body" >/dev/null
    then
        echo "Hangar rejected HANGAR_API_TOKEN (HTTP ${status})." >&2
        return 77
    fi
    jq -r '.token' <<<"$body"
}

# Prints the response body followed by the HTTP status on its own last line.
# Paths are relative to the API root, for example /v1/projects/<slug>.
hangar_get() {
    local user_agent=$1
    local jwt=$2
    local path=$3
    curl --silent --show-error --proto '=https' --tlsv1.2 \
        --connect-timeout 10 --max-time 30 \
        --header "User-Agent: ${user_agent}" \
        --header "Authorization: ${jwt}" \
        --write-out $'\n%{http_code}' \
        "${hangar_root}${path}"
}
