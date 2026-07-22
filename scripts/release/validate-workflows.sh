#!/usr/bin/env bash
set -euo pipefail

mapfile -t workflow_files < <(find .github/workflows -maxdepth 1 -type f \( -name '*.yml' -o -name '*.yaml' \) | sort)
(( ${#workflow_files[@]} > 0 )) || {
    echo "no GitHub Actions workflows found" >&2
    exit 66
}

while IFS= read -r action_ref; do
    [[ -n "$action_ref" ]] || continue
    [[ "$action_ref" == ./* ]] && continue
    if [[ ! "$action_ref" =~ ^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+@[0-9a-f]{40}$ ]]; then
        echo "remote action must use an immutable 40-character commit SHA: ${action_ref}" >&2
        exit 65
    fi
done < <(sed -nE 's/^[[:space:]]*(-[[:space:]]+)?uses:[[:space:]]+([^[:space:]#]+).*/\2/p' "${workflow_files[@]}")

grep -Fq 'environment: release' .github/workflows/publish-access.yml || {
    echo "Publish Access Check must use the protected release environment" >&2
    exit 65
}
[[ "$(grep -Fc 'environment: release' .github/workflows/release.yml)" -ge 2 ]] || {
    echo "release credential jobs must use the protected release environment" >&2
    exit 65
}

for workflow in .github/workflows/verify.yml .github/workflows/self-test.yml; do
    grep -Fq './scripts/ci/build-target.sh "${{ matrix.target }}"' "$workflow" || {
        echo "$workflow must use the manifest-aware target builder" >&2
        exit 65
    }
done

echo "Verified pinned GitHub Actions, manifest-aware builds, and release-environment bindings."
