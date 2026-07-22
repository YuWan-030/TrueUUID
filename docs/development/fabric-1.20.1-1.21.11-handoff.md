# Handoff: exact Fabric targets from 1.20.1 through 1.21.11

This is the canonical handoff for the next implementation session. Start from
the current `main`; verify the branch, worktree, target manifest, and target
matrix before editing. Do not rely on commit IDs or old session branches.

## Completed exact-target baseline

The exact Fabric compile-patch expansion is implemented. Fabric 1.20.1,
1.20.2, 1.20.4, 1.20.6, 1.21.1, 1.21.3, 1.21.4, 1.21.5, 1.21.6, 1.21.8,
1.21.10, and 1.21.11 all build, pass release-JAR verification, reach
server/client bootstrap markers, and pass the four-case installed-JAR runtime
matrix: premium, offline fallback, confirmed migration, and known-name offline
denial. Yggdrasil, timeout/grace, negative migration, admin-command,
addon-callback, and skin-refresh runtime cases are still pending.

The Fabric modules share `target-matrix.gradle`; the 1.20 modules select the
named `legacy-1.20`, `session-profile`, `session-uuid`, `play-buffers`, and
`play-payloads` source roots. Do not collapse the 1.20.5 typed-payload
transition or the 1.20.2 session API transition back into runtime version
checks.

The latest exact-target Fabric evidence is split across the 1.20 run at
`build/runtime-acceptance/20260722T042021Z/summary.tsv`, Fabric 1.21.1 at
`20260722T043906Z/summary.tsv`, and the final seven 1.21 targets at
`20260722T051512Z/summary.tsv`. The runtime harness is
the only owner of `-PtrueuuidAcceptanceHooks=true`; it snapshots the
instrumented JAR under the ignored run directory and removes it from the normal
module output. Production builds use the release implementation, and
`scripts/ci/verify-release-jar.sh` rejects instrumented JARs and packaged
development scripts.

The 1.21.11 target pins Loader 0.19.3, Yarn `1.21.11+build.6`, Fabric API
`0.141.5+1.21.11`, Loom 1.13.6, and Java 21. It introduced narrow named roots
for authlib record access, named command permission checks, `Identifier.of`,
the 2D HUD matrix stack, and `MinecraftClient#getApiServices().sessionService()`.
Do not recreate or fold these back into runtime version checks.

Forge and NeoForge are useful API-era comparisons, not Fabric code templates.
Forge 1.21.11 is a separately wrapped Gradle 9.5 build island; it is integrated
through manifest-driven build, CI, runtime, and release adapters rather than
through the incompatible root Gradle build.

## Required target inventory

One compile target now exists at each known Minecraft/API seam. Widen a
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

Known seam layout:

- Fabric 1.21.11 compiles the shared login-query registration, payload-codec,
  lifecycle, world-path, and disconnect code and has passed the four core
  account-flow scenarios.
- `identifier-factories` isolates `Identifier.of`.
- `profile-record` isolates authlib 7 `GameProfile` accessors.
- `hud-matrix-2d` isolates the JOML 2D draw stack.
- `permission-checks` isolates the 1.21.11 named admin/owner checks.
- `session-api-services` isolates the relocated client session service.
- Each earlier 1.21 module records its own official dependency pins and selects
  only the compatibility roots required by that API era.

## Completed implementation order

1. Completed on 2026-07-22: `fabric-1.20.2`, `fabric-1.20.4`, and
   `fabric-1.20.6` are manifest-integrated and core accepted on their exact
   compile patches. Adjacent 1.20.3 and 1.20.5 remain unproven.
2. Completed on 2026-07-22: `fabric-1.21.1`, `fabric-1.21.3`, `fabric-1.21.4`,
   and `fabric-1.21.5` reuse the bean-profile, numeric-permission, identifier
   factory, typed-payload, and 4D HUD roots.
3. Completed on 2026-07-22: `fabric-1.21.6` and `fabric-1.21.8` select the 2D
   HUD root; `fabric-1.21.10` additionally selects authlib-record and relocated
   session-service roots; `fabric-1.21.11` adds named permission checks.
4. Every exact module is integrated into `settings.gradle`,
   `release/targets.json`, CI/self-test, runtime selection, and release-JAR
   verification and is approved for the version-bound 1.2.0 release.
5. Every exact compile patch passed core runtime acceptance. Candidate adjacent
   patch ranges remain gated; the extended feature matrix remains the next
   evidence-expansion target.

Keep commits reviewable and signed: one API-era scaffold or source-sharing move
per commit, then its focused tests and documentation. Preserve archive history;
do not create permanent per-target branches.

## Immediate next-session entry point

Do not recreate or reorganize the exact-target modules. Start with the still
unproven extended runtime cases: allowed and rejected Yggdrasil endpoints,
timeouts/disconnect cancellation, same-IP grace, migration reject/timeout/
rollback, admin commands, addon status/callbacks, localized feedback/HUD, and
skin refresh. Record these as expanded evidence without retroactively claiming
that they were part of the 1.2.0 core matrix.

After the extended matrix, test candidate adjacent patches one by one using the
same production JAR. Only widen Fabric metadata after both the matching client
and server pass on that exact patch. The current exact-target evidence must not
be presented as support for 1.20.3, 1.20.5, 1.21, 1.21.2, 1.21.7, or 1.21.9.

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

The Fabric expansion is complete only when the release manifest and CI cover every
implemented module, all exact compile patches pass the full acceptance set, all
claimed adjacent patches have their own evidence, public docs match the actual
ranges, and no target relies on a copied behavioural implementation that should
live in `fabric-common`.
