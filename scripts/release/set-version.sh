#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 1 ]]; then
    echo "usage: $0 <major.minor.patch>" >&2
    exit 64
fi

version=$1
properties=gradle.properties
targets=release/targets.json

[[ "$version" =~ ^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$ ]] || {
    echo "version must be strict SemVer major.minor.patch: $version" >&2
    exit 65
}
[[ -f "$properties" && -f "$targets" && -x ./gradlew ]] || {
    echo "run this script from the repository root" >&2
    exit 66
}

version_lines=$(grep -Ec '^mod_version=' "$properties" || true)
[[ "$version_lines" == 1 ]] || {
    echo "expected exactly one mod_version in $properties" >&2
    exit 65
}

current_version=$(sed -n 's/^mod_version=//p' "$properties")
manifest_version=$(jq -r '.release_version // empty' "$targets")
[[ "$manifest_version" =~ ^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$ ]] || {
    echo "release manifest must contain a strict SemVer release_version" >&2
    exit 65
}

if [[ "$current_version" == "$version" && "$manifest_version" == "$version" ]]; then
    echo "TrueUUID is already version $version; release approvals were not changed."
    exit 0
fi

properties_temporary=$(mktemp "${properties}.XXXXXX")
targets_temporary=$(mktemp "${targets}.XXXXXX")
cleanup() {
    rm -f "$properties_temporary" "$targets_temporary"
}
trap cleanup EXIT

awk -v version="$version" '
    /^mod_version=/ { print "mod_version=" version; next }
    { print }
' "$properties" > "$properties_temporary"
jq --arg version "$version" '
    .release_version = $version |
    .release_ready = false |
    .targets |= map(.release = false)
' "$targets" > "$targets_temporary"

chmod --reference="$properties" "$properties_temporary"
chmod --reference="$targets" "$targets_temporary"
mv "$targets_temporary" "$targets"
mv "$properties_temporary" "$properties"

echo "TrueUUID version set to $version; publication and all target approvals were reset."
