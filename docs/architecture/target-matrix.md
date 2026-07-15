# Target and release matrix

TrueUUID is a single repository with independent loader/Minecraft adapters.
An adapter is the unit of support and release; a Git branch is not.

## Current targets

| Target | Loader | Java | State | Notes |
|---|---|---:|---|---|
| Minecraft 1.20.1 | Forge 47.4.10 | 17 | Active | Implemented in `platform/forge-1.20.1`; local Mojang login/UUID/name/skin run passed on 2026-07-12 with JDK 17. Yggdrasil, denial/timeout/grace, and migration rollback runs remain pending. |
| Minecraft 1.21.1 | Forge 52.1.0 | 21 | Planned | Implemented independently in `platform/forge-1.21.1`; a local premium client/server login passed, but the full acceptance matrix remains incomplete. |
| Minecraft 1.21.1 | NeoForge 21.1.213 | 21 | Planned | Implemented in `platform/neoforge-1.21.1`; the preserved `archive/neoforge-1.21.1-pre-monorepo` branch was API/lifecycle evidence only. Its JDK 21 build, shared fixtures, and codec/lifecycle tests pass, but no modded client/server matrix has run. |
| Minecraft 1.12.2 | Forge 14.23.5.2860 | 8 | Deferred legacy | Requires an isolated JDK 8 build and protocol-compatibility fixtures. |

An empty folder, version range in metadata, or successful compilation alone is
not a support claim. Every supported target needs a real two-sided login run.

## Recorded runtime evidence

| Date | Target | Loader/JDK | Artifact | Result |
|---|---|---|---|---|
| 2026-07-12 | Forge 1.20.1 | Forge 47.4.10 / Java 17.0.12 | `trueuuid-1.1.0-forge1.20.1.jar` | Matching Prism client and offline-mode development server: Mojang `joinServer` and server `hasJoined` passed; verified UUID/name/skin and Mojang join feedback observed. |
| 2026-07-15 | Forge 1.21.1 | Forge 52.1.0 / Java 21.0.11 | `trueuuid-1.1.0-forge1.21.1.jar` | Matching Prism premium client and offline-mode development server: TrueUUID challenge, client `joinServer`, server session verification, premium UUID replacement, and localized join feedback passed. Offline fallback and the remaining acceptance scenarios are still pending runtime validation. |

This is one acceptance scenario, not a release-wide matrix. The remaining
scenarios in [`adding-adapter.md`](../development/adding-adapter.md) remain
required before a release claim.

## Recorded build and fixture evidence

| Date | Target | Loader/JDK | Artifact | Result |
|---|---|---|---|---|
| 2026-07-12 | NeoForge 1.21.1 | NeoForge 21.1.213 / OpenJDK 21.0.11 | `platform/neoforge-1.21.1/build/libs/trueuuid-1.1.0-neoforge1.21.1.jar` | `:shared:protocol:test :platform:neoforge-1.21.1:build` passed. The adapter codec and lifecycle tests passed; safe endpoint verification is wired, but the real login acceptance matrix is pending. |
| 2026-07-15 | Forge 1.21.1 | Forge 52.1.0 / OpenJDK 21.0.11 | `platform/forge-1.21.1/build/libs/trueuuid-1.1.0-forge1.21.1.jar` | JDK 21 build, shared fixtures, codec/lifecycle tests, and one real premium client/server login passed. The full matrix is still pending. |

These build artifacts are validation outputs, not release artifacts or runtime
support claims. Both rows remain Planned until the complete acceptance matrix
in `docs/development/adding-adapter.md` passes, including migration rollback.

## Target axes

Minecraft version and loader are separate axes. For example, Forge 1.20.1 and
Fabric 1.20.1 may share Java-only protocol/authentication code, while their
packet, lifecycle, and Mixin adapters remain independent. Forge 1.21.1 and
NeoForge 1.21.1 are likewise separate targets.

Use one module per declared target:

```text
platform/
  forge-1.20.1/
  forge-1.21.1/
  neoforge-1.21.1/
  fabric-1.20.1/
```

Directories are added only when they contain a compiling adapter. Shared code
does not make an untested loader or Minecraft version supported.

## Branches and releases

`main` contains the complete active target matrix and is the only integration
trunk. Work begins from `main` on a short-lived `feature/<scope>` branch.

Old release lines are retained as read-only `archive/*` branches. Do not
rewrite or delete archive history. The historical NeoForge line is not a Forge
variant.

Release tags identify exactly what users receive:

```text
forge-1.20.1-v1.1.0
forge-1.21.1-v1.1.0
neoforge-1.21.1-v1.1.0
```

Create a `maintenance/<loader>-<minecraft-version>` branch only for a real
backport need. Fix shared behavior on `main` first, then cherry-pick the
minimal applicable commit with `-x`.

## Automation gate

Continuous integration builds and tests every implemented target. Publishing
is stricter: [`release/targets.json`](../../release/targets.json) lists the
exact target artifacts eligible for a signed version tag. A compiling Planned
target must remain `"release": false`. The currently recorded acceptance
evidence is incomplete, so all targets remain disabled until a maintainer
reviews an approval change. See
[`release-automation.md`](../development/release-automation.md) for owner setup
and publishing details.

## Compatibility eras

Modern targets may share Java 17/21 core modules. Legacy Java 8 targets must
consume a deliberately small compatibility codec or independently implement
the frozen wire contract. Golden binary fixtures are the cross-era contract;
Minecraft, loader, mapping, authlib, and Gradle code are never shared across
that boundary.
