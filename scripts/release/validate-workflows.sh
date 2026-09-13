#!/usr/bin/env bash
set -euo pipefail

mapfile -t workflow_files < <(find .github/workflows -maxdepth 1 -type f \( -name '*.yml' -o -name '*.yaml' \) | sort)
(( ${#workflow_files[@]} > 0 )) || {
    echo "no GitHub Actions workflows found" >&2
    exit 66
}
./scripts/ci/validate-server-plugin-targets.sh >/dev/null

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

for workflow in .github/workflows/verify.yml .github/workflows/self-test.yml; do
    for server_matrix_contract in \
        'server_plugins: ${{ steps.targets.outputs.server_plugins }}' \
        './scripts/ci/validate-server-plugin-targets.sh' \
        'ci/server-plugin-targets.json' \
        'server-plugin:' \
        'name: ${{ matrix.platform_name }} / ${{ matrix.game_version }} (JDK ${{ matrix.java }})' \
        'matrix: ${{ fromJSON(needs.matrix.outputs.server_plugins) }}' \
        './scripts/ci/run-server-plugin-target.sh'; do
        grep -Fq "$server_matrix_contract" "$workflow" || {
            echo "$workflow is missing server-plugin matrix contract: ${server_matrix_contract}" >&2
            exit 65
        }
    done
done
if grep -Fq 'Unsupported Spigot 1.20.1 candidate' .github/workflows/verify.yml; then
    echo 'verify.yml must use the standard platform/version/JDK job name' >&2
    exit 65
fi
for server_self_test_contract in \
    'name: ${{ matrix.artifact_name }}' \
    'name: self-test-logs-${{ matrix.target }}' \
    '"${{ matrix.target }}" smoke'; do
    grep -Fq "$server_self_test_contract" .github/workflows/self-test.yml || {
        echo "self-test.yml is missing server-plugin runtime contract: ${server_self_test_contract}" >&2
        exit 65
    }
done
grep -Fq 'artifact_name: ((if .release then "release-" else "tested-" end) + .id)' \
    .github/workflows/self-test.yml || {
    echo 'self-test.yml must derive the server-plugin artifact namespace from its release approval' >&2
    exit 65
}
for plugin_release_contract in \
    '--slurpfile plugins ci/server-plugin-targets.json' \
    './scripts/release/resolve-release-target.sh "$target_id"' \
    './scripts/release/resolve-release-target.sh "$TARGET_ID"' \
    "if: matrix.kind == 'mod' && steps.curseforge-preflight.outputs.upload == 'true'" \
    "if: matrix.kind == 'plugin' && steps.curseforge-preflight.outputs.upload == 'true'" \
    './scripts/release/publish-curseforge-plugin.sh' \
    'if: matrix.hangar' \
    './scripts/release/publish-hangar.sh' \
    'HANGAR_API_TOKEN: ${{ secrets.HANGAR_API_TOKEN }}' \
    'CURSEFORGE_ID_MOD: ${{ vars.CURSEFORGE_ID_MOD }}' \
    'CURSEFORGE_ID_BUKKIT: ${{ vars.CURSEFORGE_ID_BUKKIT }}' \
    'MODRINTH_PROJECT_ID: ${{ vars.MODRINTH_PROJECT_ID }}' \
    'run: ./scripts/release/validate-plugin-publishing-access.sh' \
    'if: matrix.spigotmc'
do
    grep -Fq -- "$plugin_release_contract" .github/workflows/release.yml || {
        echo "release.yml is missing server-plugin release contract: ${plugin_release_contract}" >&2
        exit 65
    }
done
if grep -Eq 'secrets\.(MODRINTH_PROJECT_ID|CURSEFORGE_ID_)|\.curseforge_project_id' .github/workflows/release.yml ||
    jq -e 'has("curseforge_project_id")' release/targets.json >/dev/null
then
    echo 'store project IDs must come from GitHub Actions variables, not secrets or manifests' >&2
    exit 65
fi
grep -Fq './scripts/release/resolve-release-target.sh "$target_id"' \
    scripts/release/publish-modrinth.sh || {
    echo 'Modrinth publishing must resolve mod and server-plugin targets through one interface' >&2
    exit 65
}
grep -Fq 'pattern: release-*' .github/workflows/release.yml || {
    echo 'release.yml must collect only explicitly release-namespaced artifacts' >&2
    exit 65
}
if grep -Eq 'release-(spigot|paper)-' .github/workflows/self-test.yml
then
    echo 'server-plugin artifacts may enter the release namespace only through their manifest approval' >&2
    exit 65
fi
for server_plugin_library_contract in \
    '"$sp_engine_module:serverRuntimeContractTest"' \
    '"$sp_engine_module:crossLoaderCompatibilityTest"' \
    'server AUTO DENY CONSENT_REQUIRED' \
    'Secure login bridge enabled: mode=AUTO, offline=DENY, admission=CONSENT_REQUIRED, platform=$sp_platform_name, server=' \
    'Server exited cleanly.' \
    '"TrueUUID-Support-Status: $sp_manifest_status"' \
    'trueuuid-target.properties does not match ci/server-plugin-targets.json' \
    'latest.log contains ANSI escape codes from console styling'
do
    grep -Fq -- "$server_plugin_library_contract" scripts/ci/server-plugins/lib.sh || {
        echo "server-plugin CI library is missing contract: ${server_plugin_library_contract}" >&2
        exit 65
    }
done
for pinned_runtime_contract in \
    "scripts/ci/server-plugins/spigot-1.20.1.sh|buildtools_sha256='b61fa90158f594ee95bea1a27399eb64d439b4c8ae9345bd4476a02ce49b06ff'" \
    "scripts/ci/server-plugins/spigot-1.20.1.sh|ThreadedAnvilChunkStorage: All dimensions are saved" \
    "scripts/ci/server-plugins/paper-1.20.1.sh|paper_sha256='234a9b32098100c6fc116664d64e36ccdb58b5b649af0f80bcccb08b0255eaea'" \
    "scripts/ci/server-plugins/paper-1.20.1.sh|Closing Server" \
    "plugin/craftbukkit/1.20.1/src/main/java/cn/alini/trueuuid/craftbukkit/v1_20_1/TrueuuidPlugin.java|\"Secure login bridge enabled: mode=\"" \
    "plugin/craftbukkit/1.20.1/src/main/java/cn/alini/trueuuid/craftbukkit/v1_20_1/TrueuuidPlugin.java|\", platform=\" + platform.platform().displayName()"
do
    grep -Fq -- "${pinned_runtime_contract#*|}" "${pinned_runtime_contract%%|*}" || {
        echo "server-plugin runtime contract is missing: ${pinned_runtime_contract}" >&2
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
