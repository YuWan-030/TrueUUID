#!/usr/bin/env bash
set -euo pipefail

root=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
cd "$root"
./scripts/release/validate-targets.sh

gradle_flags=(--no-daemon --stacktrace)
[[ -z "${TRUEUUID_OFFLINE:-}" ]] || gradle_flags+=(--offline)

# The root Gradle 8.14 build owns every ordinary module.
./gradlew build "${gradle_flags[@]}"

# Build islands declare their own wrapper because their loader plugin requires
# a Gradle version incompatible with the root build (Forge 1.21.11 today).
while IFS= read -r target_id; do
    module="$root/platform/$target_id"
    "$module/gradlew" -p "$module" build "${gradle_flags[@]}"
done < <(jq -r '.targets[] | select(.standalone == true) | .id' release/targets.json)
