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

grep -Fq './scripts/release/validate-release-config.sh "$version" release-changelog.md' \
    .github/workflows/release.yml || {
    echo "release.yml must use publication-enforcing release validation" >&2
    exit 65
}
grep -Fq './scripts/release/validate-release-config.sh --development' \
    .github/workflows/verify.yml || {
    echo "verify.yml must validate an explicitly blocked development manifest" >&2
    exit 65
}

for spigot_verify_contract in \
    'spigot:' \
    'name: Spigot 1.20.1' \
    './scripts/ci/build-spigot-candidate.sh' \
    './scripts/ci/verify-spigot-plugin-jar.sh'; do
    grep -Fq "$spigot_verify_contract" .github/workflows/verify.yml || {
        echo "verify.yml is missing Spigot verification contract: ${spigot_verify_contract}" >&2
        exit 65
    }
done
if grep -Fq 'Unsupported Spigot 1.20.1 candidate' .github/workflows/verify.yml; then
    echo 'verify.yml must use the user-facing Spigot 1.20.1 job name' >&2
    exit 65
fi
for spigot_self_test_contract in \
    'name: Spigot 1.20.1' \
    './scripts/ci/build-spigot-candidate.sh' \
    './scripts/ci/verify-spigot-plugin-jar.sh' \
    './scripts/ci/runtime-smoke-spigot.sh' \
    'name: tested-spigot-1.20.1' \
    'name: self-test-logs-spigot-1.20.1'; do
    grep -Fq "$spigot_self_test_contract" .github/workflows/self-test.yml || {
        echo "self-test.yml is missing Spigot runtime contract: ${spigot_self_test_contract}" >&2
        exit 65
    }
done
grep -Fq 'pattern: release-*' .github/workflows/release.yml || {
    echo 'release.yml must collect only explicitly release-namespaced artifacts' >&2
    exit 65
}
if grep -Fq 'release-spigot-1.20.1' .github/workflows/self-test.yml; then
    echo 'unsupported Spigot artifact must not enter the release artifact namespace' >&2
    exit 65
fi
for pinned_spigot_contract in \
    "buildtools_sha256='b61fa90158f594ee95bea1a27399eb64d439b4c8ae9345bd4476a02ce49b06ff'" \
    "protocol_sha256='562c3ef79391e25f71b23359adb6becae7bcee36b0dfe2621b2c679013116769'" \
    ':plugin:spigot:1.20.1:exactRuntimeCompatibilityTest' \
    ':plugin:spigot:1.20.1:crossLoaderCompatibilityTest'; do
    grep -Fq "$pinned_spigot_contract" scripts/ci/build-spigot-candidate.sh || {
        echo "Spigot candidate builder is missing pin: ${pinned_spigot_contract}" >&2
        exit 65
    }
done
for spigot_artifact_contract in \
    'TrueUUID-Support-Status: UNSUPPORTED-CANDIDATE' \
    'ProtocolLib-Version: 5.1.0' \
    'Spigot-Implementation-Version: 3871-Spigot-d2eba2c-3f9263b'; do
    grep -Fq "$spigot_artifact_contract" scripts/ci/verify-spigot-plugin-jar.sh || {
        echo "Spigot plugin verifier is missing artifact contract: ${spigot_artifact_contract}" >&2
        exit 65
    }
done
for spigot_smoke_contract in \
    'server AUTO DENY CONSENT_REQUIRED' \
    'Secure login bridge enabled: mode=AUTO, offline=DENY, admission=CONSENT_REQUIRED' \
    'Server exited cleanly.' \
    'ThreadedAnvilChunkStorage: All dimensions are saved'; do
    grep -Fq "$spigot_smoke_contract" scripts/ci/runtime-smoke-spigot.sh || {
        echo "Spigot runtime smoke is missing lifecycle contract: ${spigot_smoke_contract}" >&2
        exit 65
    }
done

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
    [[ "$(grep -Fc '      max-parallel: 6' "$workflow")" -eq 3 ]] || {
        echo "$workflow must bound every loader matrix to six concurrent targets" >&2
        exit 65
    }
done

grep -Fq 'gradle_flags=(--no-daemon --stacktrace --configure-on-demand)' \
    scripts/ci/build-target.sh || {
    echo "build-target.sh must isolate the requested target during Gradle configuration" >&2
    exit 65
}

for smoke_contract in \
    'release/targets.json' \
    'standalone=$(jq -r' \
    'elif [[ "$loader" == fabric || "$loader" == neoforge ]]' \
    '"$target_dir/runs/server"'; do
    grep -Fq "$smoke_contract" scripts/ci/runtime-smoke.sh || {
        echo "runtime-smoke.sh is missing manifest/plugin compatibility contract: ${smoke_contract}" >&2
        exit 65
    }
done

echo "Verified pinned GitHub Actions, manifest-aware builds, and release-environment bindings."
