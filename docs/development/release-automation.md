# Release automation

TrueUUID uses three automation levels:

- `.github/workflows/verify.yml` runs lightweight manifest, shell, shared
  protocol, and target unit tests on every push and pull request.
- `.github/workflows/self-test.yml` is an on-demand full matrix. It builds and
  verifies every declared JAR, then boots a localhost development server and a
  headless development client for every implemented target.
- `.github/workflows/release.yml` runs only when a stable GitHub Release is
  published in `YuWan-030/TrueUUID`. It repeats the full matrix and publishes
  only the exact release-approved target named by the release tag.

There is no separate `RELEASE_PUBLISH_ENABLED` switch. Publishing requires all
of these independent gates:

1. A non-draft, non-prerelease GitHub Release was published upstream.
2. Its signed annotated tag matches `<target>-vX.Y.Z` and points to a commit
   contained in `main`.
3. The tag version equals `mod_version` in `gradle.properties`.
4. The exact target exists once in `release/targets.json` with
   `"release": true`.
5. Every full self-test matrix job succeeds.
6. The Modrinth token and stable project ID are configured.

Compiling and booting do not approve a target for release. A reviewed change
may set `"release": true` only after the complete real client/server acceptance
matrix has passed and `docs/architecture/target-matrix.md` records the result.

## Repository setup

Configure these values in the upstream repository:

1. Add `MODRINTH_TOKEN` as a GitHub Actions repository secret. Use a Modrinth
   personal access token owned by a project member and grant only
   `VERSION_CREATE`; this workflow does not need project-edit, version-edit,
   version-delete, user, notification, payout, or analytics scopes.
2. Add `MODRINTH_PROJECT_ID` preferably as a repository variable using the
   stable eight-character project ID, not the mutable slug. For compatibility,
   the workflow also accepts it as a repository secret.
3. Protect `main` with required review, signed commits, and every Verify job.
4. Protect matching `*-v*` tags with a tag ruleset that restricts creation,
   updates, and deletion to release maintainers.

No manually created GitHub token is needed. GitHub supplies a job-scoped
`GITHUB_TOKEN`, and the workflow exposes the Modrinth token only to the final
publishing step.

## Full self-test coverage

Run `Full Self-Test` from the Actions tab before publishing a release. Select
the signed tag as the workflow ref, or enter it in the optional `ref` input.
For each target in `release/targets.json`, the workflow:

1. Runs shared fixtures and the target's complete Gradle `build` task on its
   declared JDK.
2. Checks the actual release JAR as a ZIP, verifies required entrypoint,
   protocol, Mixin, and loader metadata files, rejects duplicate/test classes,
   and records `SHA256SUMS`.
3. Boots an offline-mode server bound to `127.0.0.1` and requires both the
   TrueUUID loader message and Minecraft's server-ready marker.
4. Boots a software-rendered client under Xvfb and requires both the TrueUUID
   loader message and a render-thread startup marker.
5. Preserves the verified JAR and runtime logs as workflow artifacts.

The runtime smokes use loader development runs, while the separately verified
artifact is the production JAR. They detect packaging, classpath, Mixin, and
basic client/server bootstrap failures. They cannot use a maintainer's
Minecraft credentials and therefore do not test Mojang/Yggdrasil login,
offline fallback, disconnect handling, or migration rollback. Those scenarios
remain manual release gates.

## Publishing a release

After the on-demand full self-test and manual acceptance matrix pass:

1. Update `mod_version`, the target matrix, and only the approved target's
   `release` flag through reviewed commits on `main`.
2. Create and push a signed annotated target tag:

   ```bash
   git tag -s forge-1.20.1-v1.1.1 -m 'TrueUUID 1.1.1 for Forge 1.20.1'
   git push origin forge-1.20.1-v1.1.1
   ```

3. Create a draft GitHub Release for that existing tag. Publish it only after
   the on-demand full self-test for the tag is green.

Publishing the GitHub Release triggers the Release workflow. After the repeated
full matrix passes, it downloads the tested target artifact, verifies its
checksum, attaches the JAR and `SHA256SUMS` to the existing GitHub Release, and
publishes the same JAR to Modrinth.

On rerun, the Modrinth publisher accepts an existing version only when its
project, version number, and primary-file SHA-512 match the tested artifact.
GitHub and Modrinth do not provide one cross-service transaction, so a failure
still requires inspecting the Release workflow and the published release state.

## Artifact signatures

A signed Git commit or tag has one cryptographic signer. If multiple
maintainers must sign the artifact, each signer should create a detached
signature for the identical JAR and checksum manifest, such as
`trueuuid-...jar.alice.asc`, and attach it separately. Do not repeatedly
re-sign a JAR with different keystores because that mutates the artifact.

The workflow does not hold private signing keys. Add automatic asset signing
only after the owner chooses a key-custody model; never store a personal
long-lived private key in the repository.
