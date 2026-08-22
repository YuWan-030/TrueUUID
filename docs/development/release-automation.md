# Release automation

> **Publication veto:** version 1.3.0 is the current development line. The
> machine-readable `release_ready=false` value and false per-target approvals
> in `release/targets.json` make `validate-release-config.sh` and the guarded
> Release workflow refuse it. Version 1.2.1 remains permanently withheld; do
> not create or reuse a `v1.2.1` tag or draft.

TrueUUID has one repository version and one complete target inventory:

- `mod_version` in `gradle.properties` is the build version source. Run
  `./scripts/release/set-version.sh X.Y.Z` to update it and the manifest's
  version binding together. A real version change also resets
  `release_ready=false` and every per-target approval; every module fails
  configuration if the property is missing.
- `release/targets.json` lists every platform module, binds its approvals to
  one `release_version`, and carries a version-wide `release_ready` veto. Its
  validator rejects a manifest that omits a module, invents one, or carries
  approvals across a version change.
- `./scripts/ci/build-all-targets.sh` builds all targets. The root Gradle build
  owns 51 target modules; the script then invokes the standalone Forge 1.21.11 Gradle
  9.5 wrapper. `.github/workflows/verify.yml` builds and tests all targets on
  every push and pull request.
- `.github/workflows/self-test.yml` builds and structurally verifies every JAR,
  then boots a localhost development server and headless client for every
  target.
- Both workflows also expose a user-facing `Spigot 1.20.1` job. Verify rebuilds
  the pinned BuildTools 200 server boundary, runs the exact descriptor and
  cross-loader tests, and inspects the production-shaped plugin JAR. Full
  Self-Test additionally installs that JAR with ProtocolLib 5.1.0 into exact
  Spigot 3871, reaches the secure bridge ready marker, requests shutdown once,
  and verifies clean world saving, port release, and world-lock release.

The current release inventory is 52 targets: sixteen Forge, eighteen Fabric,
and eighteen NeoForge. Forge 1.21.11 is a standalone build island whose own wrapper
is selected by manifest-driven scripts and workflows. The Spigot job is an
additional candidate gate, not a 53rd release target. Its tested artifact is
uploaded as `tested-spigot-1.20.1`, deliberately outside the `release-*`
artifact namespace consumed by the Release workflow. CI coverage is separate
from release approval. The recorded 1.2.1 native results remain historical
evidence, but all target approvals were reset for 1.3.0 and the version-wide
publication veto prevents artifacts from being attached to a GitHub Release or
sent to a distribution service.

Public JARs use the unambiguous all-hyphen pattern
`trueuuid-{mod-version}-{loader}-{minecraft-version}.jar`, for example
`trueuuid-1.3.0-fabric-1.21.11.jar`. Modrinth and CurseForge use the matching
human-readable display name `TrueUUID 1.3.0 for Fabric 1.21.11`; stable API
version identifiers remain machine-oriented, such as
`1.3.0+fabric-1.21.11`. These examples illustrate naming only; the maintainer
chooses the actual next version after its gates pass.

Every matrix job installs the target's declared JDK for the Java toolchain and
JDK 21 as the Gradle launcher. The launcher must be 21 even for Java 17 targets
because Fabric Loom is configured on every Gradle invocation.

Run the aggregate `./scripts/ci/build-all-targets.sh` with dependency access
enabled. The legacy
NeoGradle 7 task used by NeoForge 1.20.2 treats Gradle's offline state as a cache
input; switching that task to `--offline` can remove its cached Minecraft client
artifact and then fail because it is forbidden to restore it. This is a plugin
cache limitation, not a source validation mode. Focused tests may use
`--offline` only when their loader toolchain is known to support it.

## Publishing gates

Publishing starts only when a maintainer manually runs the `Release` workflow
from `main` and supplies the tag of an existing draft GitHub Release. The
workflow requires all of these independent gates:

GitHub gives an unpublished draft an `untagged-...` browser URL and its normal
release-by-tag endpoint returns 404 until publication. The workflow therefore
resolves the authenticated draft through the releases list by its stored
`tag_name`, then uses the immutable release ID for asset uploads and final
publication.

GitHub also omits draft releases from that authenticated list when the API
identity has only `contents: read`. Consequently, only the `metadata` job has
`contents: write`; its checkout still uses `persist-credentials: false`, and
later operations remain bound to the resolved immutable release ID. Workflow
validation pins this narrow permission so a future global/read-only change
cannot silently break draft discovery again.

1. The GitHub Release is still a draft, is not a prerelease, and its body is
   byte-for-byte identical to the checked-in English-first bilingual changelog
   for that version.
2. Its signed annotated tag is exactly `vX.Y.Z`, its version equals
   `mod_version`, and its commit is contained in `main`.
3. GitHub verifies the tag signature.
4. `release_ready` is true, `release_version`, `mod_version`, and the tag
   version agree, and every declared target has `"release": true`.
5. A temporary probe-asset upload and immediate deletion verifies GitHub
   Release write access without changing the draft metadata;
   the release log identifies the authenticated Modrinth username, and
   non-creating permission probes verify Modrinth `VERSION_CREATE`, the
   CurseForge upload token, and CurseForge project upload access. CurseForge's
   upload API does not expose the token owner's username, so the workflow does
   not invent one.
6. The full self-test passes for all 52 declared targets and the additional
   exact Spigot 1.20.1 candidate job. The Spigot result gates the workflow but
   is not collected or published as a release artifact.

After those gates pass, the workflow freezes the draft body, collects only
approved JARs, verifies their per-target checksums, creates one aggregate
`SHA256SUMS`, and attaches the JARs and checksums to the draft. It then
publishes each approved JAR to Modrinth and CurseForge. Only after every
external upload succeeds does it publish the GitHub Release. GitHub, Modrinth,
and CurseForge therefore receive the same frozen changelog file.

## Server-plugin distribution path

The CI job name describes the platform under test; it is not a support claim.
The plugin manifest continues to carry
`TrueUUID-Support-Status: UNSUPPORTED-CANDIDATE`, Spigot remains absent from
`release/targets.json`, and `release_ready=false` remains authoritative until
the installed production JAR passes the real-premium and malicious-client
acceptance gates in
[`spigot-paper-1.20.1-handoff.md`](spigot-paper-1.20.1-handoff.md).

Once those gates pass, promote one exact server target at a time:

1. Replace the candidate artifact name/status with the supported artifact only
   in the same change that records fresh runtime evidence.
2. Add the exact Spigot target to `release/targets.json` and extend its
   validator, Release loader/display mapping, asset-name allowlist, and
   publisher selection with Bukkit `plugin.yml` metadata instead of pretending
   it is a Forge/Fabric/NeoForge mod.
3. Change Full Self-Test's artifact name from `tested-spigot-1.20.1` to the
   target's `release-*` name. The Release workflow must continue collecting
   only approved, checksum-verified manifest targets.
4. Add a per-platform distribution project identifier and credential probe.
   Never silently reuse the current mod-project identifier for a plugin
   project.

Current official distribution capabilities are:

- [Modrinth's loader API](https://docs.modrinth.com/api/operations/loaderlist/)
  reports Bukkit, Paper, and Spigot loader tags as applicable to plugin/mod
  projects. The existing version publisher can support the plugin after its
  loader mapping, project compatibility, and approval metadata are validated.
- [CurseForge's upload API](https://support.curseforge.com/support/solutions/articles/9000197321)
  exposes Bukkit as a game dependency and supports automated project file
  uploads. Configure and probe a Bukkit plugin project ID separately; do not
  assume the existing mod project ID accepts plugin files.
- [PaperMC Hangar](https://docs.papermc.io/misc/hangar-publishing/) provides an
  official Gradle publisher and GitHub Actions flow using a
  `HANGAR_API_TOKEN` with `create_version`. Add it only with the independent
  Paper target and its exact Paper runtime evidence.
- [SpigotMC's documented resource flow](https://www.spigotmc.org/wiki/adding-plugins/)
  is an account-driven resource upload. Until SpigotMC documents a supported
  publishing API, use a manual post-release handoff with the already-tested
  GitHub asset and checksum; do not automate browser login or store forum
  credentials in Actions.

The changelog must begin with a nonempty `## English` section and then contain
a nonempty `## 中文` section. Start from
[`release-changelog-template.md`](release-changelog-template.md). English is
always the primary section and Chinese is the translation; do not put Chinese
release notes before the English source text.

Compiling and booting alone do not approve a target. The target-level 1.2.1
evidence is based on the recorded four-case installed-JAR matrix, aggregate
builds, unit tests, structural JAR checks, and explicit maintainer approval;
it does not override the version-wide publication veto. The target matrix
keeps the remaining extended runtime evidence limitations visible.

## Repository setup

Configure these values in the upstream repository:

1. Create a protected GitHub environment named `release`, require an owner or
   release maintainer to approve deployments, and restrict it to protected
   branches and tags. The credential-consuming workflow jobs are bound to this
   environment.
2. Add `MODRINTH_TOKEN` as a `release` environment secret. Grant the Modrinth
   `USER_READ` scope used to identify the publisher and the `VERSION_CREATE`
   scope needed to create versions; the token owner must also be allowed to
   upload versions to the project.
3. Add `MODRINTH_PROJECT_ID` preferably as a repository variable using the
   stable eight-character project ID, not the mutable slug. A `release`
   environment secret is accepted as a fallback.
4. Add `CURSEFORGE_TOKEN` as a `release` environment secret. The CurseForge project ID
   (`1539688`) is versioned in `release/targets.json`.
5. Protect `main` with required review, signed commits, and every Verify job.
6. Protect matching `v*` tags with a tag ruleset restricting creation,
   updates, and deletion to release maintainers.

The 2026-07-22 audit found the three credentials configured as repository
secrets, but no `release` environment, repository ruleset, or classic `main`
branch protection. Those owner-only settings must be completed before creating
any future release tag; a write-level collaborator cannot configure them
through the API. Do not use the withheld `v1.2.1` version.

No manually created GitHub token is needed. GitHub supplies a job-scoped
`GITHUB_TOKEN`; distribution credentials are exposed only to their publishing
steps.

Publishing access is checked only inside the guarded `Release` workflow; there
is no separate credential-check workflow that can drift from the real release
path. Immediately after validating the draft, Release identifies the initiating
GitHub actor and authenticated Modrinth username, uploads and immediately
deletes a uniquely named probe asset to prove GitHub Release write access, and
probes both distribution services. All checks run before the 52-target
self-test. Modrinth and CurseForge are probed with complete metadata but
deliberately no file; both upload APIs must authorize the request and then
reject it for the missing required file. The probe never creates a version or
uploads an artifact.

GitHub has no server-side pre-publication hook that can disable the **Publish
release** button for a maintainer who already has release write permission. As
a poka-yoke, the `Release` workflow also listens for manually published
releases and immediately changes them back to drafts, then leaves a failed
guard run naming the actor and tag. The guarded workflow's own final publication
uses `GITHUB_TOKEN`; GitHub does not recursively start workflows for events
created by that token. A manual publication may therefore be visible briefly,
but it cannot remain public or start Modrinth/CurseForge publishing through this
automation.

## Full self-test coverage

Run `Full Self-Test` from the Actions tab before publishing a release. Select
the signed version tag as the workflow ref, or enter it in the optional `ref`
input. The Release workflow repeats the same test at the draft release tag. For
each target, the workflow:

1. Runs shared fixtures and the target's complete Gradle `build` task.
2. Checks the actual release JAR as a ZIP, verifies the entrypoint, protocol,
   Mixin configuration, and era-correct loader metadata, rejects duplicate or
   test classes, packaged development scripts, and matrix-only acceptance
   hooks, and records `SHA256SUMS`.
3. For Forge 1.20.1, Forge 1.20.2, Forge 1.20.3, Forge 1.20.4, and NeoForge 1.20.1, additionally requires a
   nonempty refmap, a `MixinConfigs` manifest entry, SRG method references, and
   SRG-renamed shadow fields.
4. Boots an offline-mode server on `127.0.0.1` and requires both the TrueUUID
   loader message and Minecraft's server-ready marker.
5. Boots a software-rendered client under Xvfb and requires both the TrueUUID
   loader message and a render-thread startup marker. Forge-like targets
   disable the optional FML early splash in CI because it can race Xvfb; the
   real Minecraft window and render thread are still required.
6. Preserves the verified JAR and runtime logs as workflow artifacts.

The runtime smokes use loader development runs, while the separately verified
artifact is the production JAR. They detect packaging, classpath, Mixin, and
basic bootstrap failures; they are not join tests. The installed-JAR acceptance
harness separately recorded the four core login scenarios. Allowed Yggdrasil,
disconnect/grace, negative migration, commands, callbacks, HUD presentation,
and skin refresh retain the evidence limitations shown in the target matrix.

## Withheld version 1.2.1

Do not publish, tag, or create a draft release for 1.2.1. Its exact-target
builds and installed-JAR results remain a regression baseline, not a release
instruction. `./scripts/release/validate-release-config.sh` must fail for this
version; CI uses `--development` only to validate that the manifest is coherent
while publication remains blocked.

For 1.3.0, keep every target unapproved until its exact current-artifact matrix
passes, finish the bilingual changelog, and set `release_ready=true` only after
the complete declared inventory has fresh approval. The next implementation
sequence is documented in
[`spigot-paper-1.20.1-handoff.md`](spigot-paper-1.20.1-handoff.md).

### Adding independent developer signatures

Do not place developers' personal private GPG keys in repository or environment
secrets. After a future Release workflow attaches its approved JARs and
`SHA256SUMS`, each
developer can add an independent signature with one command:

```bash
./scripts/release/sign-release-assets.sh vX.Y.Z YOUR_FULL_GPG_FINGERPRINT
```

The helper identifies the developer by the account authenticated in `gh`,
downloads all release JARs and `SHA256SUMS`, requires the exact manifest-approved
filename set, verifies every checksum, signs the checksum manifest with the
developer's local GPG key, verifies the new signature, and uploads it as
`SHA256SUMS.<github-user>.asc`. It refuses to overwrite an existing signature.
The key argument may be omitted when the developer has only one default signing
key. It works for draft releases visible to the authenticated maintainer and
for published releases.

Three developers therefore create three small detached signatures rather than
156 per-JAR files. A signature over the exact `SHA256SUMS` binds all 52 JARs.
Anyone who imports the signer's public key can verify an endorsement with:

```bash
gpg --verify SHA256SUMS.<github-user>.asc SHA256SUMS
sha256sum --check SHA256SUMS
```

These are independent human endorsements and do not currently block automated
publication. A mandatory three-of-three gate requires the maintainers to first
check in the three trusted public-key fingerprints; accepting arbitrary keys or
storing all three private keys in Actions would not provide independent trust.

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
  increment the version and create a new signed tag and draft. Never create or
  reuse the withheld `v1.2.1` tag.
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
