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

[[ ! -e .github/workflows/publish-access.yml ]] || {
    echo "publishing access must be checked inside release.yml, not by a separate workflow" >&2
    exit 65
}
[[ "$(grep -Fc 'environment: release' .github/workflows/release.yml)" -ge 4 ]] || {
    echo "release credential jobs must use the protected release environment" >&2
    exit 65
}

for release_guard in \
    'release:' \
    'types: [published]' \
    'Return manually published release to draft' \
    'Identify Modrinth publisher and validate distribution access' \
    'uses: ./.github/workflows/self-test.yml' \
    'needs: [metadata, publishing-access]'; do
    grep -Fq "$release_guard" .github/workflows/release.yml || {
        echo "release.yml is missing required integrated gate: ${release_guard}" >&2
        exit 65
    }
done

for draft_api_contract in \
    'repos/${GITHUB_REPOSITORY}/releases?per_page=100' \
    'https://uploads.github.com/repos/${GITHUB_REPOSITORY}/releases/${RELEASE_ID}/assets?name=${name}' \
    'repos/${GITHUB_REPOSITORY}/releases/${RELEASE_ID}'; do
    grep -Fq "$draft_api_contract" .github/workflows/release.yml || {
        echo "release.yml cannot safely operate on unpublished drafts: ${draft_api_contract}" >&2
        exit 65
    }
done

metadata_job=$(awk '
    /^  metadata:/ { in_metadata = 1 }
    in_metadata && /^  [a-zA-Z0-9_-]+:/ && !/^  metadata:/ { exit }
    in_metadata { print }
' .github/workflows/release.yml)
grep -Fq '    permissions:' <<<"$metadata_job" &&
grep -Fq '      contents: write' <<<"$metadata_job" || {
    echo "release metadata job must have contents: write so its token can list draft releases" >&2
    exit 65
}
grep -Fq '          persist-credentials: false' <<<"$metadata_job" || {
    echo "release metadata checkout must keep Git credentials disabled" >&2
    exit 65
}

grep -Fq 'The draft body must exactly match' .github/workflows/release.yml || {
    echo "release.yml must reject a draft body that differs from the checked-in changelog" >&2
    exit 65
}

for immutable_tag_contract in \
    'tag_commit: ${{ steps.release.outputs.tag_commit }}' \
    'tag_object: ${{ steps.release.outputs.tag_object }}' \
    'Verify the signed tag still identifies the tested commit'; do
    grep -Fq "$immutable_tag_contract" .github/workflows/release.yml || {
        echo "release.yml is missing immutable signed-tag binding: ${immutable_tag_contract}" >&2
        exit 65
    }
done
[[ "$(grep -Fc 'ref: ${{ needs.metadata.outputs.tag_commit }}' \
    .github/workflows/release.yml)" -ge 4 ]] || {
    echo "release jobs must check out the immutable tested commit, not a movable tag name" >&2
    exit 65
}

for publishing_name_contract in \
    'loader_name: (.loader | loader_name)' \
    'name: TrueUUID ${{ needs.metadata.outputs.version }} for ${{ matrix.loader_name }} ${{ matrix.game_version }}'; do
    grep -Fq "$publishing_name_contract" .github/workflows/release.yml || {
        echo "release.yml is missing human-readable publishing names: ${publishing_name_contract}" >&2
        exit 65
    }
done
grep -Fq 'display_name="TrueUUID ${version} for ${loader_name} ${minecraft_version}"' \
    scripts/release/publish-modrinth.sh || {
    echo "Modrinth publishing must use the human-readable loader display name" >&2
    exit 65
}

for workflow in .github/workflows/verify.yml .github/workflows/self-test.yml; do
    grep -Fq './scripts/ci/build-target.sh "${{ matrix.target }}"' "$workflow" || {
        echo "$workflow must use the manifest-aware target builder" >&2
        exit 65
    }
done

for smoke_contract in \
    'release/targets.json' \
    'standalone=$(jq -r' \
    'elif [[ "$loader" == fabric || "$loader" == neoforge ]]' \
    '"$root/platform/$target_id/runs/server"'; do
    grep -Fq "$smoke_contract" scripts/ci/runtime-smoke.sh || {
        echo "runtime-smoke.sh is missing manifest/plugin compatibility contract: ${smoke_contract}" >&2
        exit 65
    }
done

echo "Verified pinned GitHub Actions, manifest-aware builds, and release-environment bindings."
