#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 0 && $# -ne 2 ]]; then
    echo "usage: $0 [<version> <release-changelog.md>]" >&2
    exit 64
fi

./scripts/release/validate-targets.sh

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

./scripts/release/validate-changelog.sh "$changelog"

echo "Verified complete ${version} release configuration for $(jq '.targets | length' release/targets.json) targets."
