# Handoff: full legacy-range Forge and Fabric coverage

This document replaces neither the feature-parity work nor the existing
per-loader consolidation handoffs. It records the newly requested end state:
cover every **loader-supported** Minecraft patch from 1.20.1 through 1.21.11
with Forge and Fabric, without falsely claiming a loader where its upstream
does not ship one.

## Current feasibility verdict

- **Fabric:** feasible in principle for every requested patch. The official
  Fabric metadata listed every patch from 1.20.1 through 1.21.11 on
  2026-07-18, including a stable loader entry for 1.21.11. Each target still
  needs its exact Fabric API, Yarn, Loom, remapped JAR, and two-sided runtime
  validation.
- **Forge:** not feasible as literal coverage of every requested patch. The
  official Forge promotions metadata had no Forge build for **1.20.5** or
  **1.21.2** on 2026-07-18. Do not claim those as Forge-supported or hide that
  absence behind a wider version range. Forge does have at least a latest
  build for every other requested patch; a "latest"-only line is a candidate
  for investigation, not a stability claim.
- **NeoForge:** remains its own scope. The existing best-effort 1.20.1 target
  stays release-disabled; do not turn this Forge/Fabric handoff into a
  cross-loader rewrite.

Therefore the truthful public goal is:

```text
Fabric: every requested patch, after its acceptance matrix passes.
Forge: every requested patch for which Forge publishes a loader, after its
       acceptance matrix passes; explicitly document 1.20.5 and 1.21.2 as
       upstream-unavailable unless Forge later publishes them.
```

## Do not skip the active contract work

The source change in `4cb5640` gives Fabric a server-authoritative login
result, but it has no real premium or offline runtime evidence yet. Before
fanning that source into new Fabric modules, record both runs with server
audit, localized chat, and the server-confirmed HUD. Then complete the
remaining feature-parity units in `docs/development/feature-sync-prompt.md`:

1. Fabric addon `AccountStatus` API and callbacks.
2. Fabric's allowlisted custom Yggdrasil support, failing closed until all
   endpoint checks are present.
3. Migration/admin-command parity across modern Forge, NeoForge, and Fabric.
4. Forge and NeoForge server-authoritative HUD transport, as separate commits,
   only after Fabric's runtime path is proven.

No target becomes Active or `release: true` from a build or a matching method
name. The target matrix remains authoritative.

## Exact target inventory after parity is proven

Use protocol clusters only to minimize candidate artifacts; a range is widened
only after the exact production JAR passes the complete matrix on every patch
it claims.

| Loader | Exact modules/range candidates | Upstream constraint |
|---|---|---|
| Fabric | 1.20.1, 1.20.2, 1.20.3–1.20.4, 1.20.5–1.20.6, 1.21–1.21.1, 1.21.2–1.21.3, 1.21.4, 1.21.5, 1.21.6, 1.21.7–1.21.8, 1.21.9–1.21.10, 1.21.11 | Verify exact Fabric API/Yarn/Loom and each metadata range live. |
| Forge | 1.20.1, 1.20.2, 1.20.3–1.20.4, 1.20.6, 1.21–1.21.1, 1.21.3, 1.21.4, 1.21.5, 1.21.6, 1.21.7–1.21.8, 1.21.9–1.21.10, 1.21.11 | Do **not** claim 1.20.5 or 1.21.2: no official Forge build was listed in the 2026-07-18 metadata check. |

The Forge range candidates differ deliberately from the generic protocol table:
a protocol match cannot manufacture an upstream Forge loader for a patch.

## First implementation session after the parity/runtime gate

Start a short-lived `feature/forge-legacy-coverage` branch from refreshed
`origin/main`. The first reviewable unit should be **one target family only**:

1. Re-query official Forge promotions and exact published artifacts. Record
   loader version, Java level, mappings, and the absence/presence result in
   `target-matrix.md` before scaffolding.
2. Start with `forge-1.21.6`, an independent protocol patch in the established
   modern `forge-common` family. Keep it `Planned` and `release:false`.
3. Add its module, root/settings registration, release inventory entry,
   verify/self-test selection, local dev-run registration, structural JAR
   verification, and target-matrix row together.
4. Run the declared-JDK focused build and shared protocol fixtures. Only then
   attempt its real modded client/server matrix. Do not widen a neighboring
   range in the same commit.
5. Commit signed, merge directly to `main` only after that reviewable unit is
   validated. Do not use or rewrite `archive/*` branches.

Fabric expansion starts separately after the Fabric runtime proof, with
`fabric-1.20.2` as its first module. Keep version-specific networking, client
HUD, mixin, and metadata seams in each module; `platform/fabric-common` stays
source-only and must not grow conditional per-version code.

## Required evidence per target

For each new module or widened range, record in `target-matrix.md`:

- exact loader/API/mappings/JDK and artifact hash/path;
- remapped `build/libs` Fabric JAR, never a development JAR;
- focused tests, root build, and structural JAR checks;
- Mojang success, allowed custom endpoint where supported, rejected endpoint,
  denied/missing token, malformed payload, timeout, disconnect, offline
  fallback, known-premium denial, reconnect grace, UUID/skin correctness, and
  migration confirmation/rollback where migration exists.

Keep every `release/targets.json` entry `release:false` until every applicable
gate passes and a maintainer explicitly authorizes release approval.
