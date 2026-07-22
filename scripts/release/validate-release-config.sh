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

./scripts/release/validate-changelog.sh "$changelog"

echo "Verified complete ${version} release configuration for $(jq '.targets | length' release/targets.json) targets."
