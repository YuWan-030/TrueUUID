# Release automation

TrueUUID has two separate automation paths:

- `.github/workflows/verify.yml` runs the declared-JDK build/test command for
  every implemented adapter on pull requests and pushes to `main`.
- `.github/workflows/release.yml` can create a GitHub Release and publish only
  one release-approved adapter to Modrinth when an upstream
  `<target>-vX.Y.Z` tag is pushed.

The release workflow is committed in a disarmed state. It skips the release job
unless the upstream repository variable `RELEASE_PUBLISH_ENABLED` is exactly
`true`. Independently, `release/targets.json` is the per-target gate. Every
target starts with `"release": false`; a reviewed change may set one to `true`
only after its complete real client/server matrix has passed and
`docs/architecture/target-matrix.md` has been updated.

## One-time repository setup

An owner/admin must complete this setup in the upstream GitHub repository:

1. Create a GitHub Actions environment named `release`. Restrict deployments to
   tags matching `*-v*`, require a maintainer reviewer, and prevent self-review.
2. In that environment, add `MODRINTH_TOKEN` as a secret. Use a Modrinth
   personal access token owned by a project member and grant only
   `VERSION_CREATE`; the workflow does not need project-edit, version-edit,
   version-delete, user, notification, payout, or analytics scopes.
3. In the same environment, add `MODRINTH_PROJECT_ID` as a variable using the
   project's stable ID, not its mutable slug.
4. Add a repository variable named `RELEASE_PUBLISH_ENABLED`, but leave it
   unset or set to `false` until the owner is ready to publish. No manually
   created GitHub token is needed; GitHub supplies the scoped `GITHUB_TOKEN`.
5. Protect `main` with a branch ruleset that requires pull-request review,
   signed commits, and every `Verify` matrix check. Protect matching `*-v*`
   tags with a tag ruleset that restricts creation, updates, and deletion to
   the release maintainers. The workflow also rejects lightweight tags and
   tags whose commit is not contained in `origin/main`.
6. After acceptance evidence is recorded, review the change that sets only the
   approved target entries to `"release": true`. Then set
   `RELEASE_PUBLISH_ENABLED=true` and create a signed annotated tag after the
   release version in `gradle.properties` has been updated and merged:

   ```bash
   git tag -s forge-1.20.1-v1.1.1 -m 'TrueUUID 1.1.1 for Forge 1.20.1'
   git push origin forge-1.20.1-v1.1.1
   ```

The workflow requires the tag prefix to match exactly one target ID whose
manifest entry is `"release": true`, and checks that the tag version is exactly
`mod_version`. It refuses to release with missing Modrinth configuration. It
creates the GitHub Release as a draft, publishes that target to Modrinth, and
only then makes the GitHub Release public. Modrinth and GitHub do not offer one
cross-service transaction, so a Modrinth outage can still require a maintainer
to inspect the draft and rerun or clean up the partial release. On rerun, the
publisher accepts an existing Modrinth version only when its project, version
number, and primary-file SHA-512 match the local artifact exactly.

## Artifact integrity and multiple maintainers

The workflow attaches the selected approved JAR and `SHA256SUMS` to the GitHub
Release. A signed Git commit or tag has one cryptographic signer. For a release
that needs signatures from multiple maintainers, each signer should produce a
detached signature for the identical JAR and checksum manifest, for example
`trueuuid-...jar.alice.asc`. Attach all `.asc` files to the release. Do not
re-sign a JAR repeatedly with different keystores: that mutates the artifact
and makes independent verification harder.

The workflow does not hold private signing keys. Add release-asset signing only
after the owner chooses a key custody model; never store a personal long-lived
private key in the repository.
