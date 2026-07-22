# Handoff: complete Fabric 1.20.1-1.21.11 coverage

This is the canonical handoff for the next implementation session. Start from
the current `main`; verify the branch, worktree, target manifest, and target
matrix before editing. Do not rely on commit IDs or old session branches.

## Objective and current baseline

Keep `fabric-1.20.1` as the behavioural reference and add Fabric adapters for
the remaining legacy-version era through Minecraft 1.21.11. The existing
1.20.1 production JAR passed the four-case installed-JAR runtime matrix on
2026-07-22: premium, offline fallback, confirmed migration, and known-name
offline denial. Its Yggdrasil, timeout/grace, negative migration, admin-command,
addon-callback, and skin-refresh runtime cases are still pending.

Forge and NeoForge are useful API-era comparisons, not Fabric code templates.
Forge 1.21.11 is still an unintegrated build island and must not be cited as
release evidence for Fabric or for the root manifest.

## Required target inventory

Create one compile target at each known Minecraft/API seam, then widen its
declared Minecraft range only after a matching client and server pass on every
patch in that range.

| Module | Compile patch | Candidate patches after exact runtime proof |
|---|---:|---|
| `fabric-1.20.1` | 1.20.1 | 1.20.1 only |
| `fabric-1.20.2` | 1.20.2 | 1.20.2 only |
| `fabric-1.20.4` | 1.20.4 | 1.20.3, 1.20.4 |
| `fabric-1.20.6` | 1.20.6 | 1.20.5, 1.20.6 |
| `fabric-1.21.1` | 1.21.1 | 1.21, 1.21.1 |
| `fabric-1.21.3` | 1.21.3 | 1.21.2, 1.21.3 |
| `fabric-1.21.4` | 1.21.4 | 1.21.4 only |
| `fabric-1.21.5` | 1.21.5 | 1.21.5 only |
| `fabric-1.21.6` | 1.21.6 | 1.21.6 only |
| `fabric-1.21.8` | 1.21.8 | 1.21.7, 1.21.8 |
| `fabric-1.21.10` | 1.21.10 | 1.21.9, 1.21.10 |
| `fabric-1.21.11` | 1.21.11 | 1.21.11 only |

Protocol equality makes a widened range a candidate, never proof. If Fabric
Loader or Fabric API does not support an intermediate patch, record that exact
upstream limitation rather than silently claiming it.

## Before coding

Read these live files completely:

```text
AGENTS.md
docs/architecture/target-matrix.md
docs/architecture/version-consolidation-roadmap.md
docs/development/adding-adapter.md
docs/development/local-runtime-testing.md
release/targets.json
platform/fabric-1.20.1/
platform/fabric-common/
```

Retrieve current Fabric Loader, Fabric API, Yarn mappings, and Loom compatibility
from their official metadata/docs for each compile patch. Pin exact versions in
the module; do not copy a stale version from this handoff. Gradle must launch on
Java 21. Minecraft 1.20.1-1.20.4 targets Java 17; later Minecraft targets use
Java 21 unless the upstream toolchain proves otherwise.

## Architecture constraints

- Keep one behavioural Fabric implementation in `platform/fabric-common`.
- A version module owns only Gradle/metadata and the smallest unavoidable
  Minecraft, Yarn, networking, HUD, command, record, or identifier seam.
- Prefer source-set composition over copied trees. Add a named API-era source
  root when multiple versions share the same seam.
- `shared/protocol` stays plain Java and loader-neutral. Never move Minecraft
  profiles, packets, text, paths, commands, or lifecycle objects into it.
- Do not introduce runtime `if (minecraftVersion)` branches in shared behaviour.
- Keep the client token local. Preserve bounded decoding/state, async bounded
  verification, cancellation, allowlisted HTTPS endpoints, public-address and
  DNS-pinning checks, hostname verification, no redirects, response limits,
  known-name protection, and transactional migration rollback.
- The server owns the final account status. A client payload must never be able
  to manufacture `PREMIUM` or bypass offline policy.

Expected seams to investigate, not blindly assume:

- Fabric login query registration and payload codecs;
- `ResourceLocation`/`Identifier` construction and the 1.21.11 rename;
- authlib `GameProfile` accessor changes in the 1.21.9+ record era;
- HUD callback/draw-context changes around 1.21.6;
- command registration, server lifecycle, world save-path, and disconnect APIs;
- Fabric metadata dependency-range syntax for every exact patch.

## Implementation order

1. Add `fabric-1.20.2`, `fabric-1.20.4`, and `fabric-1.20.6`. Stabilize the
   1.20-era source roots and run the exact compile-patch matrix before moving on.
2. Add `fabric-1.21.1`, `fabric-1.21.3`, `fabric-1.21.4`, and `fabric-1.21.5`.
   Extract only the seams proven common by compilation and focused tests.
3. Add `fabric-1.21.6`, `fabric-1.21.8`, `fabric-1.21.10`, and
   `fabric-1.21.11`, keeping GUI, record-era, and identifier-era seams narrow.
4. Add every module to `settings.gradle`, `release/targets.json`, CI/self-test,
   runtime target selection, and release-JAR structural verification. New
   manifest entries start with `release: false`.
5. Run exact compile-patch runtime acceptance first. Only then test and declare
   candidate adjacent patch ranges one patch at a time.

Keep commits reviewable and signed: one API-era scaffold or source-sharing move
per commit, then its focused tests and documentation. Preserve archive history;
do not create permanent per-target branches.

## Validation and acceptance

For each module, require:

1. shared protocol tests and the module's complete `build`/`remapJar`;
2. production-JAR metadata, entrypoint, Mixin, duplicate-class, and client/server
   bootstrap checks;
3. the four automated scenarios with a fresh artifact and world:
   `premium,offline,migrate,known-deny`;
4. allowed Yggdrasil, rejected host/private address/redirect/oversize/timeout,
   disconnect cancellation, same-IP grace, migration reject/timeout/rollback,
   `/trueuuid cleanupuuid`, `/trueuuid migrateuuid`, addon status/callbacks,
   localized feedback/HUD, and skin refresh;
5. the same checks on every adjacent patch before widening metadata to cover it.

Useful repository-wide checks after each API era:

```bash
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 \
PATH=/usr/lib/jvm/java-21-openjdk-amd64/bin:$PATH \
./gradlew :shared:protocol:test :platform:<target>:build --offline --no-daemon

./scripts/release/validate-targets.sh
python3 -m unittest scripts/tests/test_runtime_matrix.py
git diff --check
```

For the real core matrix:

```bash
TRUEUUID_PREMIUM_NAME=YourMinecraftName \
  scripts/test-runtime-matrix.sh \
  --targets <target> \
  --scenarios premium,offline,migrate,known-deny
```

Record exact target, loader build, artifact SHA-256, scenarios, and failures in
the target matrix. Build success, a server boot, or one anchor version never
promotes a loader family. Set `release: true` only after all applicable runtime
features pass and a maintainer explicitly approves publication.

## Completion gate

The Fabric expansion is complete only when the root manifest and CI cover every
implemented module, all exact compile patches pass the full acceptance set, all
claimed adjacent patches have their own evidence, public docs match the actual
ranges, and no target relies on a copied behavioural implementation that should
live in `fabric-common`.
