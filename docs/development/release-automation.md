# Release automation

TrueUUID has one repository version and one complete target inventory:

- `mod_version` in `gradle.properties` is the only version source. Run
  `./scripts/release/set-version.sh X.Y.Z` to update it; every module fails
  configuration if the property is missing.
- `release/targets.json` lists every platform module. Its validator rejects a
  manifest that omits a module or invents one.
- `./gradlew build` builds all targets. `.github/workflows/verify.yml` builds
  and tests all targets on every push and pull request.
- `.github/workflows/self-test.yml` builds and structurally verifies every JAR,
  then boots a localhost development server and headless client for every
  target.

The current inventory is 20 modules: seven Forge, one Fabric, and twelve
NeoForge. CI coverage is separate from release approval. A target with
`"release": false` is still built and self-tested, but it is never attached to
a GitHub Release or sent to a distribution service.

Every matrix job installs the target's declared JDK for the Java toolchain and
JDK 21 as the Gradle launcher. The launcher must be 21 even for Java 17 targets
because Fabric Loom is configured on every Gradle invocation.

## Publishing gates

Publishing starts only when a maintainer manually runs the `Release` workflow
from `main` and supplies the tag of an existing draft GitHub Release. The
workflow requires all of these independent gates:

1. The GitHub Release is still a draft, is not a prerelease, and has a valid
   English-first bilingual body.
2. Its signed annotated tag is exactly `vX.Y.Z`, its version equals
   `mod_version`, and its commit is contained in `main`.
3. GitHub verifies the tag signature.
4. At least one target has a reviewed `"release": true` approval.
5. An idempotent no-change draft update verifies GitHub Release write access;
   non-creating permission probes verify Modrinth `VERSION_CREATE`, the
   CurseForge upload token, and CurseForge project upload access.
6. The full self-test passes for all declared targets, including targets that are not
   approved for publication.

After those gates pass, the workflow freezes the draft body, collects only
approved JARs, verifies their per-target checksums, creates one aggregate
`SHA256SUMS`, and attaches the JARs and checksums to the draft. It then
publishes each approved JAR to Modrinth and CurseForge. Only after every
external upload succeeds does it publish the GitHub Release. GitHub, Modrinth,
and CurseForge therefore receive the same frozen changelog file.

The changelog must begin with a nonempty `## English` section and then contain
a nonempty `## 中文` section. Start from
[`release-changelog-template.md`](release-changelog-template.md). English is
always the primary section and Chinese is the translation; do not put Chinese
release notes before the English source text.

Compiling and booting do not approve a target. Set `"release": true` only
after its complete real client/server acceptance matrix passes and
`docs/architecture/target-matrix.md` records the result. In particular, do not
flip all targets merely to make a synchronized version release larger.

## Repository setup

Configure these values in the upstream repository:

1. Add `MODRINTH_TOKEN` as a repository secret. Grant only the Modrinth scope
   needed to create versions (`VERSION_CREATE`); the token owner must also be
   allowed to upload versions to the project.
2. Add `MODRINTH_PROJECT_ID` preferably as a repository variable using the
   stable eight-character project ID, not the mutable slug. A repository secret
   is accepted as a fallback.
3. Add `CURSEFORGE_TOKEN` as a repository secret. The CurseForge project ID
   (`1539688`) is versioned in `release/targets.json`.
4. Protect `main` with required review, signed commits, and every Verify job.
5. Protect matching `v*` tags with a tag ruleset restricting creation,
   updates, and deletion to release maintainers.

No manually created GitHub token is needed. GitHub supplies a job-scoped
`GITHUB_TOKEN`; distribution credentials are exposed only to their publishing
steps.

Run the manual `Publish Access Check` workflow at any time to verify Modrinth
and CurseForge without creating a tag or release. The Release workflow repeats
those checks immediately after validating its draft, and also PATCHes the
draft with its existing body and flags to prove GitHub write access without a
semantic change. All checks run before the 20-target self-test. Modrinth and
CurseForge are probed with complete metadata but deliberately no file; both
upload APIs must authorize the request and then reject it for the missing
required file. The probe never creates a version or uploads an artifact.

## Full self-test coverage

Run `Full Self-Test` from the Actions tab before publishing a release. Select
the signed version tag as the workflow ref, or enter it in the optional `ref`
input. The Release workflow repeats the same test at the draft release tag. For
each target, the workflow:

1. Runs shared fixtures and the target's complete Gradle `build` task.
2. Checks the actual release JAR as a ZIP, verifies the entrypoint, protocol,
   Mixin configuration, and era-correct loader metadata, rejects duplicate or
   test classes, and records `SHA256SUMS`.
3. For Forge 1.20.1, Forge 1.20.2, and NeoForge 1.20.1, additionally requires a
   nonempty refmap, a `MixinConfigs` manifest entry, SRG method references, and
   SRG-renamed shadow fields.
4. Boots an offline-mode server on `127.0.0.1` and requires both the TrueUUID
   loader message and Minecraft's server-ready marker.
5. Boots a software-rendered client under Xvfb and requires both the TrueUUID
   loader message and a render-thread startup marker.
6. Preserves the verified JAR and runtime logs as workflow artifacts.

The runtime smokes use loader development runs, while the separately verified
artifact is the production JAR. They detect packaging, classpath, Mixin, and
basic bootstrap failures. They cannot use a maintainer's Minecraft credentials
and therefore do not test Mojang/Yggdrasil login, offline fallback, disconnect
handling, or migration rollback. Those scenarios remain manual release gates.

## Publishing version 1.2.0

At the time this document was updated, every target in `release/targets.json`
still had `"release": false`. The repository can build and self-test every
target, but the Release workflow will intentionally publish nothing until at
least one target's manual acceptance evidence is complete and that target is
explicitly approved.

After the manual acceptance matrix passes for each target being approved:

1. Confirm `./scripts/release/set-version.sh 1.2.0` has been committed on
   `main`, update the target matrix, and set only validated targets to
   `"release": true`.
2. Run `Full Self-Test` against the final `main` commit.
3. Create and push a signed annotated repository version tag:

   ```bash
   git tag -s v1.2.0 -m 'TrueUUID 1.2.0'
   git push origin v1.2.0
   ```

4. Create a draft GitHub Release for the existing `v1.2.0` tag. Copy
   `docs/development/release-changelog-1.2.0.md` into its body, verify that its
   platform list matches the approved targets, and leave the Release as a
   draft.
5. Open Actions, select `Release`, choose `main`, enter `v1.2.0`, and run the
   workflow. Do not click GitHub's `Publish release` button yourself.
6. Confirm that the workflow self-tested all declared targets, attached only the
   approved JARs, published those same JARs externally, and finally changed the
   GitHub Release from draft to public.

## Failure recovery

Do not delete or move a release tag as the first response to a failed run.
GitHub, Modrinth, and CurseForge do not provide one cross-service transaction,
so the correct recovery depends on whether external state was created:

- For a transient network or service failure with unchanged code, tag,
  artifacts, and changelog, rerun the entire failed `Release` workflow. The
  GitHub asset upload is replace-safe, Modrinth accepts an existing version
  only when its target metadata, exact changelog, and primary file SHA-512 all
  match, and CurseForge skips an exact-filename upload only after downloading
  the existing file and confirming byte equality. A same-name byte mismatch
  fails closed.
- If validation or self-test fails before any external upload, fix the code,
  increment the patch version (for example, `1.2.1`), and create a new signed
  tag and draft. Re-creating `v1.2.0` is acceptable only if it never left the
  draft/preflight stage and no GitHub asset, Modrinth version, or CurseForge
  file was created. A new version is still the safer and clearer choice.
- If any Modrinth or CurseForge upload exists, never move or re-create that
  version tag for changed code, artifacts, or release notes. Fix the problem,
  increment the patch version, and publish a new signed tag and draft release.
- If the draft body changes while publishing is in progress, final GitHub
  publication stops. Restore the exact frozen bilingual changelog and rerun,
  or use a new patch version if the externally published notes must change.

The CurseForge action is intentionally limited to one POST attempt per run;
the next full workflow run performs the byte-safe preflight before trying
again. This avoids an uploader retry creating a duplicate after an ambiguous
timeout.

## Artifact signatures

A signed Git commit or tag has one cryptographic signer. If multiple
maintainers must sign the artifact, each signer should create a detached
signature for the identical JAR and checksum manifest, such as
`trueuuid-...jar.alice.asc`, and attach it separately. Do not repeatedly
re-sign a JAR with different keystores because that mutates the artifact.

The workflow does not hold private signing keys. Add automatic asset signing
only after the owner chooses a key-custody model; never store a personal
long-lived private key in the repository.
