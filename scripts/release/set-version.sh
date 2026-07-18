#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 1 ]]; then
    echo "usage: $0 <major.minor.patch>" >&2
    exit 64
fi

version=$1
properties=gradle.properties

[[ "$version" =~ ^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$ ]] || {
    echo "version must be strict SemVer major.minor.patch: $version" >&2
    exit 65
}
[[ -f "$properties" && -x ./gradlew ]] || {
    echo "run this script from the repository root" >&2
    exit 66
}

version_lines=$(grep -Ec '^mod_version=' "$properties" || true)
[[ "$version_lines" == 1 ]] || {
    echo "expected exactly one mod_version in $properties" >&2
    exit 65
}

temporary=$(mktemp "${properties}.XXXXXX")
cleanup() {
    rm -f "$temporary"
}
trap cleanup EXIT

awk -v version="$version" '
    /^mod_version=/ { print "mod_version=" version; next }
    { print }
' "$properties" > "$temporary"
chmod --reference="$properties" "$temporary"
mv "$temporary" "$properties"

echo "TrueUUID version set to $version for every target."
