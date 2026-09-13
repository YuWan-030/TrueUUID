#!/usr/bin/env bash
set -euo pipefail

development=false
if [[ $# -eq 1 && $1 == "--development" ]]; then
    development=true
    shift
elif [[ $# -ne 0 && $# -ne 2 ]]; then
    echo "usage: $0 [--development | <version> <release-changelog.md>]" >&2
    exit 64
fi

./scripts/release/validate-targets.sh
./scripts/ci/validate-server-plugin-targets.sh

project_version=$(sed -n 's/^mod_version=//p' gradle.properties)
version=${1:-$project_version}
changelog=${2:-docs/development/release-changelog-${version}.md}

[[ "$version" == "$project_version" ]] || {
    echo "release version ${version} does not match mod_version ${project_version}" >&2
    exit 65
}
[[ "$(jq -r '.release_version' release/targets.json)" == "$version" ]] || {
    echo "release target approval is not bound to version ${version}" >&2
    exit 65
}

if [[ "$(jq -r '.release_ready' release/targets.json)" != true ]]; then
    if [[ "$development" == true ]]; then
        echo "Verified development manifest for ${version}; publication is explicitly blocked."
        exit 0
    fi
    echo "release ${version} is explicitly blocked by release_ready=false" >&2
    exit 65
fi

jq -e '
  all(.targets[]; .release == true) and
  ((["forge", "fabric", "neoforge"] - ([.targets[].loader] | unique)) | length == 0)
' release/targets.json >/dev/null || {
    echo "the release requires every declared Forge, Fabric, and NeoForge target to be approved" >&2
    exit 65
}

# Release jobs consume --approved stdout as JSON. Exercise that interface here
# so a human-readable validation message cannot break artifact collection or
# either external publisher after the expensive self-test matrix completes.
first_approved_target=$(jq -r '.targets[] | select(.release == true) | .id' \
    release/targets.json | head -n 1)
approved_target_json=$(./scripts/release/validate-targets.sh --approved \
    "$first_approved_target")
jq -se --arg id "$first_approved_target" \
    'length == 1 and .[0].id == $id and .[0].release == true' \
    <<<"$approved_target_json" >/dev/null || {
    echo "validate-targets.sh --approved must emit exactly one target JSON object" >&2
    exit 65
}

# Server plugins are approved individually and may be absent from a release.
# Every approved plugin must still resolve through the same interface that the
# attach and publish jobs use after the self-test matrix.
while IFS= read -r approved_plugin; do
    jq -se --arg id "$approved_plugin" \
        'length == 1 and .[0].id == $id and .[0].kind == "plugin"' \
        <(./scripts/release/resolve-release-target.sh "$approved_plugin") >/dev/null || {
        echo "approved server plugin does not resolve for publication: $approved_plugin" >&2
        exit 65
    }
done < <(jq -r '.targets[] | select(.release == true) | .id' ci/server-plugin-targets.json)

./scripts/release/validate-changelog.sh "$changelog"

# Every store receives this exact changelog. An approved plugin must therefore
# be named in both language sections, so no store lists a download the notes
# never mention.
english_notes=$(awk '$0 == "## English" { section = 1; next } $0 == "## 中文" { section = 0 } section' "$changelog")
chinese_notes=$(awk '$0 == "## 中文" { section = 1; next } section' "$changelog")
while IFS=$'\t' read -r plugin_name plugin_game_version
do
    for notes in "$english_notes" "$chinese_notes"
    do
        grep -Fq "$plugin_name $plugin_game_version" <<<"$notes" || {
            echo "the changelog must name the approved $plugin_name $plugin_game_version plugin in both languages" >&2
            exit 65
        }
    done
done < <(jq -r '.targets[] | select(.release == true) | [.platform_name, .game_version] | @tsv' \
    ci/server-plugin-targets.json)

echo "Verified complete ${version} release configuration for $(jq '.targets | length' release/targets.json) mod targets and $(jq '[.targets[] | select(.release == true)] | length' ci/server-plugin-targets.json) approved server plugins."
