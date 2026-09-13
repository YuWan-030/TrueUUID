# Release automation

> **Release status:** version 1.3.0 is approved. `release/targets.json` sets
> `release_ready=true` and approves all 52 targets from the 2026-09-13
> installed-JAR matrix recorded in the target matrix. Version 1.2.1 remains
> permanently withheld. Do not create or reuse a `v1.2.1` tag or draft.

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
- Both workflows also run one job per server-plugin target, named like the
  native loaders: `Spigot / 1.20.1 (JDK 17)` and `Paper / 1.20.1 (JDK 17)`.
  Verify prepares the pinned server, runs the shared and plugin tests, checks
  every login seam against the real mapped server JAR, compares the plugin wire
  bytes with the Fabric 1.20.1 JAR, and inspects the plugin JAR. Full Self-Test
  additionally installs that JAR with ProtocolLib 5.4.0, reaches the secure
  bridge ready marker, requests shutdown once, and verifies clean world saving,
  port release, world-lock release, and a log free of ANSI escape codes.

The current mod inventory is 52 targets: sixteen Forge, eighteen Fabric, and
eighteen NeoForge. Forge 1.21.11 is a standalone build island whose own wrapper
is selected by manifest-driven scripts and workflows. All 52 native targets are
approved for 1.3.0 from the 2026-09-13 installed-JAR matrix recorded in the
target matrix.

Server plugins live in `ci/server-plugin-targets.json`, not in the mod
inventory. The strict validator exports that matrix to Verify and Full
Self-Test, and `scripts/ci/run-server-plugin-target.sh` dispatches each
target's `build`, `verify`, and `smoke` to its pinned script in
`scripts/ci/server-plugins/`. The implementation lives once in
`scripts/ci/server-plugins/lib.sh`. Adding a server version adds a manifest
entry, a pinned script, and its modules, never a copied workflow job. A plugin's
tested artifact is uploaded as `tested-<target>` until the target is approved
for the current version, and as `release-<target>` afterwards.

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
   not invent one. When a server plugin is approved, the same job also probes
   the CurseForge Bukkit plugin project and, for Paper, the Hangar project,
   its `create_version` permission, and the configured channel. None of these
   probes creates anything.
6. The full self-test passes for all 52 declared targets and every server-plugin
   job. Each plugin job gates the workflow, and only an approved plugin's JAR is
   collected for release.

After those gates pass, the workflow freezes the draft body, collects only
approved JARs, verifies their per-target checksums, creates one aggregate
`SHA256SUMS`, and attaches the JARs and checksums to the draft. It then
publishes each approved mod JAR to Modrinth and CurseForge, and each approved
plugin JAR to every store listed in the server-plugin distribution path below.
Only after every external upload succeeds does it publish the GitHub Release.
Every store therefore receives the same frozen changelog file.

## Server-plugin distribution path

The CI job name describes the platform under test. It is not a support claim.

Server plugins have their own inventory, `ci/server-plugin-targets.json`
(schema 3), bound to the same `release_version` as the mod manifest. They never
enter `release/targets.json`, so a plugin can never block or widen a mod
release. The manifest holds:

- `distribution`: the CurseForge Bukkit plugin project ID and the Hangar owner,
  project slug, and channel shared by every plugin target.
- `protocollib`: the tested minimum ProtocolLib version, the compile version,
  the version installed on test servers, and the SHA-256 of every verified
  release. Gradle and the CI scripts read the same pins, and the validator
  checks that the Java startup gate enforces the same minimum.
- One entry per target with its engine module, its pinned CI script, and two
  switches. `support_status` is `unsupported-candidate` or `supported` and
  selects the JAR name, the `TrueUUID-Support-Status` manifest marker, and the
  embedded `trueuuid-target.properties`. A candidate builds
  `trueuuid-paper-1.20.1-candidate.jar`, and a supported target builds the
  public name `trueuuid-X.Y.Z-paper-1.20.1.jar`. `release` approves the target
  for the current version. The validator refuses `release: true` on a
  candidate, or on any plugin while `distribution.curseforge_project_id` is
  unset, and `set-version.sh` resets it.

When `release` is true, Full Self-Test uploads the plugin as `release-<target>`.
The Release workflow attaches it to the GitHub Release, includes it in
`SHA256SUMS`, and publishes the same checksum-verified JAR with the same frozen
changelog to every store that accepts it:

| Store | Spigot JAR | Paper JAR | How |
|---|---|---|---|
| GitHub Release | yes | yes | release asset listed in `SHA256SUMS` |
| Modrinth | loader `spigot` | loader `paper` | `publish-modrinth.sh`, environment `server_only` |
| CurseForge | yes | yes | `publish-curseforge-plugin.sh` to the Bukkit plugin project through the dev.bukkit.org upload API |
| Hangar | no | platform `PAPER` | `publish-hangar.sh` to the manifest channel |
| SpigotMC | manual | no | the workflow summary names the exact file and SHA-256 |

Names match on every store: `TrueUUID X.Y.Z for Paper 1.20.1` for display and
`X.Y.Z+paper-1.20.1` as the stable version identifier. Hangar hosts only Paper,
Velocity, and Waterfall plugins, so the Spigot JAR never goes there. SpigotMC
documents no upload API, so the workflow never automates a browser login or
stores forum credentials. `sign-release-assets.sh` expects approved plugin JARs
as well.

Every publisher is safe to rerun. Modrinth and Hangar accept an existing
version only when its file hash and changelog match exactly. CurseForge is
checked with `check-curseforge-artifact.sh` first and then receives exactly one
upload attempt. For a Paper target, the Hangar upload runs before the Modrinth
and CurseForge uploads.

The CurseForge plugin endpoint, game-version names, and Hangar channel lookup
have been checked against published API documentation and public projects, but
not yet with this repository's credentials. The pre-flight probes exercise
exactly those calls before any artifact is uploaded.

Spigot 1.20.1 and Paper 1.20.1 are `supported`. They passed the local gate
agreed for the first plugin release, recorded in
[`server-plugin-1.20.1-evidence.md`](server-plugin-1.20.1-evidence.md). Their
`release` switch stays `false` until the CurseForge Bukkit plugin project ID is
set. To approve them for a version, in one commit:

1. Set `distribution.curseforge_project_id`.
2. Set `release` to `true` on each approved plugin target.
3. Name every approved plugin, such as `Paper 1.20.1`, in both language
   sections of the changelog. `validate-release-config.sh` enforces this.
4. Update the target matrix and both READMEs.
5. Run `./scripts/release/validate-release-config.sh` and
   `./scripts/release/validate-workflows.sh`.

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
   (`1539688`) is versioned in `release/targets.json`. The same token uploads to
   the CurseForge Bukkit plugin project, whose ID is versioned in
   `ci/server-plugin-targets.json`.
5. Add `HANGAR_API_TOKEN` as a `release` secret. Its API key needs the
   `create_version` permission on the Hangar project named in
   `ci/server-plugin-targets.json`, and the configured channel must already
   exist in that project.
6. Protect `main` with required review, signed commits, and every Verify job.
7. Protect matching `v*` tags with a tag ruleset restricting creation,
   updates, and deletion to release maintainers.

The 2026-09-07 GitHub API audit found a `release` environment, but its
`protection_rules` were empty and its `deployment_branch_policy` was null.
There were no repository rulesets, and `main` reported `protected: false`.
The authenticated collaborator had push access but no admin or maintain access.
An owner should configure the environment approval and ref restrictions, main
protection, and release-tag ruleset. They are recommended, and the Release
workflow does not require them. Credential
validity was not rechecked by this read-only audit; the guarded publishing
probes remain required. Do not use the withheld `v1.2.1` version.

### Release readiness audit: 2026-09-07

The audited implementation commit was
`ad7ad1388d625904b7a35a5a940be32383cfc4f5`, matching upstream `main`.
Its [Verify run](https://github.com/YuWan-030/TrueUUID/actions/runs/32562837947)
passed all 55 jobs, including all 52 native targets and the Spigot candidate.
The latest completed
[Full Self-Test](https://github.com/YuWan-030/TrueUUID/actions/runs/30368274440)
was for `2cefde2` on 2026-07-28, before the current authentication changes.
The latest published release was `v1.2.0`; no `v1.3.0` tag or draft existed.

Lightweight manifest, source-sharing, workflow, and bilingual changelog
validation passed. Publication validation refused 1.3.0 with exit 65 because
`release_ready=false`; all 52 target approvals were false. Local shared
protocol tests passed (84 tests), but the Forge test run was stopped before
completion. This audit does not provide fresh native-artifact acceptance or
plugin real-login evidence.

Before publication, complete the current-artifact acceptance in the target
matrix, obtain all target approvals, and finalize the bilingual changelog. The
owner protections above are recommended when the owner is available. Then
follow the signed-tag, matching-draft, publishing-access, and Full Self-Test
gates described here. Successful compilation alone does not remove the veto.

### Access recheck: 2026-09-12

A read-only API recheck found the same state as the 2026-09-07 audit. The
`release` environment has no protection rules or deployment branch policy,
`main` is unprotected, the repository has no rulesets, and `F1xGOD` holds write
access without admin or maintain. Verify passed for `6f87f99`. No Full
Self-Test has run since `2cefde2`.

### Runbook: cutting 1.3.0

1.3.0 carries the 52 manifest mod targets and every server plugin approved in
`ci/server-plugin-targets.json`. Each step depends on the one before it. Stop
at the first failure.

1. **Signing key, then optional owner protections.** Confirm that the tagger's
   GPG or SSH signing key is registered on GitHub so the tag reports as
   verified. The repository setup above is recommended defense in depth, but
   the Release workflow does not require it. On 2026-09-12 the maintainer chose
   to cut 1.3.0 with write access alone while the owner was unavailable. Add
   the protections once the owner can apply them.
2. **Fresh installed-JAR acceptance.** From a clean checkout of `main`:

   ```bash
   scripts/test-premium-client.sh login
   TRUEUUID_PREMIUM_NAME=YourMinecraftName \
     scripts/test-runtime-matrix.sh --targets all \
     --scenarios premium,offline,migrate,known-deny
   ```

   Resume an interrupted run with `--start-at <target>` or
   `--resume-from build/runtime-acceptance/<run>`, reusing only runs of the
   same commit. Every target needs four `PASS` or `REUSED_PASS` rows in the new
   `summary.tsv`. These scenarios do not exercise the timeout, silence, or
   malformed-answer denials, which remain unit-tested.
3. **Record evidence and approve, in one commit.** Add an "Evidence level for
   1.3.0" section to the target matrix with the run directory, change each
   runtime state to "Core accepted (1.3.0)", and update the release notice in
   both READMEs. Then approve the manifest:

   ```bash
   jq '.release_ready = true | .targets |= map(.release = true)' \
     release/targets.json > release/targets.json.new
   mv release/targets.json.new release/targets.json
   ./scripts/release/validate-release-config.sh
   ./scripts/release/validate-workflows.sh
   git diff --check
   ```

   The publication validator must now exit 0. Reread
   [`release-changelog-1.3.0.md`](release-changelog-1.3.0.md) before
   committing. Its exact bytes become the GitHub, Modrinth, and CurseForge
   notes.
4. **Push and wait for CI.** Push the signed commit to `main`, wait for every
   Verify job, and then run Full Self-Test on `main`:

   ```bash
   gh workflow run self-test.yml --ref main
   ```

   A failure here costs a fix commit. The same failure after tagging requires
   a new patch version.
5. **Tag and draft.**

   ```bash
   git tag -s v1.3.0 -m "TrueUUID 1.3.0"
   git push origin v1.3.0
   gh release create v1.3.0 --draft --verify-tag \
     --title "v1.3.0-TrueUUID" \
     --notes-file docs/development/release-changelog-1.3.0.md
   gh api repos/YuWan-030/TrueUUID/releases \
     | jq -j '.[] | select(.tag_name == "v1.3.0") | .body' \
     | cmp - docs/development/release-changelog-1.3.0.md
   ```

   The last command must print nothing. The Release workflow repeats the same
   byte comparison.
6. **Publish.** Start the guarded workflow and approve the `release`
   deployment:

   ```bash
   gh workflow run release.yml --ref main -f tag=v1.3.0
   ```

   Do not press **Publish release** in the GitHub UI. The workflow publishes
   the draft itself after the Modrinth and CurseForge uploads succeed.
7. **Optional endorsements.** Each maintainer may add a detached signature
   with `sign-release-assets.sh`, described below.

### Credential handling and publication guard

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

For 1.3.0, every target passed its exact current-artifact matrix and received
fresh approval before `release_ready` was set to true. The server-plugin
contract and its open gates are in
[`server-plugin-bridge.md`](../architecture/server-plugin-bridge.md).

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
