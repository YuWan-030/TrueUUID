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

Publishing starts only when a stable GitHub Release is published in
`YuWan-030/TrueUUID`. The workflow requires all of these independent gates:

1. The Release is non-draft and non-prerelease and has a nonempty body.
2. Its signed annotated tag is exactly `vX.Y.Z`, its version equals
   `mod_version`, and its commit is contained in `main`.
3. GitHub verifies the tag signature.
4. At least one target has a reviewed `"release": true` approval.
5. The full self-test passes for all 20 targets, including targets that are not
   approved for publication.
6. The Modrinth project ID/token and CurseForge token are configured.

After those gates pass, the workflow collects only approved JARs, verifies
their per-target checksums, creates one aggregate `SHA256SUMS`, and attaches
the JARs and checksums to the existing GitHub Release. It then publishes each
approved JAR to Modrinth and CurseForge. Every external upload receives the
same immutable workflow snapshot of the GitHub Release body as its changelog.

Compiling and booting do not approve a target. Set `"release": true` only
after its complete real client/server acceptance matrix passes and
`docs/architecture/target-matrix.md` records the result. In particular, do not
flip all targets merely to make a synchronized version release larger.

## Repository setup

Configure these values in the upstream repository:

1. Add `MODRINTH_TOKEN` as a repository secret. Grant only the Modrinth scope
   needed to create versions.
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

## Full self-test coverage

Run `Full Self-Test` from the Actions tab before publishing a release. Select
the signed version tag as the workflow ref, or enter it in the optional `ref`
input. The Release workflow repeats the same test at the published tag. For
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

4. Create a draft GitHub Release for the existing `v1.2.0` tag. Write the
   changelog that Modrinth and CurseForge should receive, then publish the
   GitHub Release.

Publishing triggers the Release workflow. On rerun, the Modrinth publisher
accepts an existing version only when its project, version number, and primary
file SHA-512 match the tested artifact. GitHub, Modrinth, and CurseForge do not
provide one cross-service transaction, so a later failure can leave an earlier
service updated; inspect the Release workflow before retrying.

## Artifact signatures

A signed Git commit or tag has one cryptographic signer. If multiple
maintainers must sign the artifact, each signer should create a detached
signature for the identical JAR and checksum manifest, such as
`trueuuid-...jar.alice.asc`, and attach it separately. Do not repeatedly
re-sign a JAR with different keystores because that mutates the artifact.

The workflow does not hold private signing keys. Add automatic asset signing
only after the owner chooses a key-custody model; never store a personal
long-lived private key in the repository.
