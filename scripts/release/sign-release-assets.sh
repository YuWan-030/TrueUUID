#!/usr/bin/env bash
set -euo pipefail

if [[ $# -lt 1 || $# -gt 2 ]]; then
    echo "usage: $0 <vX.Y.Z> [gpg-key-fingerprint]" >&2
    exit 64
fi

tag=$1
signing_key=${2:-}
repository=${TRUEUUID_GITHUB_REPOSITORY:-YuWan-030/TrueUUID}

[[ "$tag" =~ ^v([0-9]+\.[0-9]+\.[0-9]+)$ ]] || {
    echo "release tag must be vX.Y.Z: $tag" >&2
    exit 64
}
version=${BASH_REMATCH[1]}
[[ "$repository" =~ ^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$ ]] || {
    echo "invalid GitHub repository: $repository" >&2
    exit 64
}

root=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
cd "$root"

for command in gh gpg jq sha256sum; do
    command -v "$command" >/dev/null || {
        echo "required command is unavailable: $command" >&2
        exit 69
    }
done

./scripts/release/validate-targets.sh
project_version=$(sed -n 's/^mod_version=//p' gradle.properties)
[[ "$version" == "$project_version" ]] || {
    echo "tag version $version does not match project version $project_version" >&2
    exit 65
}

release_json=$(gh release view "$tag" --repo "$repository" \
    --json tagName,isDraft,isPrerelease,assets)
jq -e --arg tag "$tag" '
    .tagName == $tag and .isPrerelease == false
' <<<"$release_json" >/dev/null || {
    echo "release must exist and must not be a prerelease: $tag" >&2
    exit 65
}

github_login=$(gh api user --jq '.login')
[[ "$github_login" =~ ^[A-Za-z0-9-]+$ ]] || {
    echo "could not determine a safe GitHub signer name" >&2
    exit 65
}
signature_name="SHA256SUMS.${github_login}.asc"
if jq -e --arg name "$signature_name" 'any(.assets[]; .name == $name)' \
    <<<"$release_json" >/dev/null; then
    echo "release already has a signature from @${github_login}: ${signature_name}" >&2
    echo "Delete it explicitly before replacing a published endorsement." >&2
    exit 73
fi

work_dir=$(mktemp -d)
trap 'rm -rf -- "$work_dir"' EXIT

gh release download "$tag" --repo "$repository" --dir "$work_dir" \
    --pattern SHA256SUMS --pattern 'trueuuid-*.jar'
[[ -s "$work_dir/SHA256SUMS" ]] || {
    echo "release is missing SHA256SUMS; wait for the Release workflow to attach assets" >&2
    exit 66
}

expected_names=$(jq -r --arg version "$version" '
    .targets[] | select(.release == true) |
    (.artifact | gsub("%VERSION%"; $version) | split("/")[-1])
' release/targets.json | sort)
actual_names=$(find "$work_dir" -maxdepth 1 -type f -name 'trueuuid-*.jar' \
    -printf '%f\n' | sort)
[[ "$actual_names" == "$expected_names" ]] || {
    echo "release JAR set does not match the approved target manifest" >&2
    diff -u <(printf '%s\n' "$expected_names") \
        <(printf '%s\n' "$actual_names") >&2 || true
    exit 65
}

manifest_names=$(awk '
    NF == 2 && $1 ~ /^[0-9a-f]{64}$/ &&
      $2 ~ /^trueuuid-[0-9]+\.[0-9]+\.[0-9]+-(forge|fabric|neoforge)-[0-9]+\.[0-9]+\.[0-9]+\.jar$/ {
        print $2
        next
    }
    { exit 65 }
' "$work_dir/SHA256SUMS" | sort) || {
    echo "SHA256SUMS has malformed or unsafe entries" >&2
    exit 65
}
[[ "$manifest_names" == "$expected_names" ]] || {
    echo "SHA256SUMS does not list the exact approved release JAR set" >&2
    exit 65
}
(cd "$work_dir" && sha256sum --check --strict SHA256SUMS)

gpg_args=(--armor --detach-sign --output "$work_dir/$signature_name")
if [[ -n "$signing_key" ]]; then
    [[ "$signing_key" =~ ^[0-9A-Fa-f]{16,64}$ ]] || {
        echo "GPG key must be a long key ID or full fingerprint" >&2
        exit 64
    }
    gpg_args+=(--local-user "$signing_key")
fi
gpg "${gpg_args[@]}" "$work_dir/SHA256SUMS"

signer_fingerprint=$(gpg --status-fd 1 --verify \
    "$work_dir/$signature_name" "$work_dir/SHA256SUMS" 2>/dev/null |
    awk '$2 == "VALIDSIG" { print $3; exit }')
[[ "$signer_fingerprint" =~ ^[0-9A-F]{40,64}$ ]] || {
    echo "could not verify the newly created detached signature" >&2
    exit 70
}

gh release upload "$tag" \
    "$work_dir/$signature_name#OpenPGP signature by @${github_login} (${signer_fingerprint})" \
    --repo "$repository"

echo "Uploaded ${signature_name} for ${tag}."
echo "Signer: @${github_login} (${signer_fingerprint})"
