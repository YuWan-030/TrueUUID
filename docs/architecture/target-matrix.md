# Target and release matrix

TrueUUID is a single repository with independent loader/Minecraft adapters.
An adapter is the unit of support and release; a Git branch is not.

## Current targets

| Target | Loader | Java | State | Notes |
|---|---|---:|---|---|
| Minecraft 1.20.1 | Forge 47.4.10 | 17 | Active | Implemented in `platform/forge-1.20.1` (independent pre-configuration-phase island). Local Mojang login/UUID/name/skin run passed on 2026-07-12 with JDK 17. Yggdrasil, denial/timeout/grace, and migration rollback runs remain pending. |
| Minecraft 1.21.1 | Forge 52.1.0 | 21 | Planned | `platform/forge-1.21.1`; shares `platform/forge-common`. A local premium client/server login passed, but the full acceptance matrix remains incomplete. |
| Minecraft 1.21.3 | Forge 53.1.0 | 21 | Planned | `platform/forge-1.21.3`; shares `platform/forge-common`. Build + mixin refmap + tests pass (2026-07-15). No login run yet. |
| Minecraft 1.21.4 | Forge 54.1.14 | 21 | Planned | `platform/forge-1.21.4`; shares `platform/forge-common`. Build + mixin refmap + tests pass (2026-07-15). No login run yet. |
| Minecraft 1.21.5 | Forge 55.1.10 | 21 | Planned | `platform/forge-1.21.5`; shares `platform/forge-common`. Build + mixin refmap + tests pass (2026-07-15). No login run yet. |
| Minecraft 1.21.8 | Forge 58.1.0 | 21 | Planned | `platform/forge-1.21.8`; shares `platform/forge-common`. Uses the EventBus 7 seam. Build + mixin refmap + tests pass (2026-07-15). No login run yet. Candidate to cover 1.21.6/1.21.7 once those login runs pass. |
| Minecraft 1.21.1 | NeoForge 21.1.213 | 21 | Planned | Implemented in `platform/neoforge-1.21.1`; the preserved `archive/neoforge-1.21.1-pre-monorepo` branch was API/lifecycle evidence only. Its JDK 21 build, shared fixtures, and codec/lifecycle tests pass, but no modded client/server matrix has run. It is the hardened source baseline for the later NeoForge 1.21.x modules. |
| Minecraft 1.21.3 | NeoForge 21.3.56 | 21 | Planned | `platform/neoforge-1.21.3`; recompiles the hardened NeoForge 1.21 adapter source against 1.21.3. Build and focused tests pass (2026-07-15). No login run yet. |
| Minecraft 1.21.4 | NeoForge 21.4.121 | 21 | Planned | `platform/neoforge-1.21.4`; recompiles the hardened NeoForge 1.21 adapter source against 1.21.4. Build and focused tests pass (2026-07-15). No login run yet. |
| Minecraft 1.21.5 | NeoForge 21.5.74 | 21 | Planned | `platform/neoforge-1.21.5`; recompiles the hardened NeoForge 1.21 adapter source against 1.21.5. Build and focused tests pass (2026-07-15). No login run yet. |
| Minecraft 1.21.8 | NeoForge 21.8.9 | 21 | Planned | `platform/neoforge-1.21.8`; recompiles the hardened NeoForge 1.21 adapter source against 1.21.8. Build, focused tests, and a local server boot pass (2026-07-15); the GUI seam uses the new 2D matrix stack. No login run yet. |
| Minecraft 1.12.2 | Forge 14.23.5.2860 | 8 | Deferred legacy | Requires an isolated JDK 8 build and protocol-compatibility fixtures. |

An empty folder, version range in metadata, or successful compilation alone is
not a support claim. Every supported target needs a real two-sided login run.
The five modern Forge modules build and pass unit tests, but all remain Planned
until each has its own login run.

## Feature parity

Every target verifies premium logins, but the 1.21 line is a login-verification
core: it does not yet carry the surrounding features 1.20.1 grew. NeoForge is in
step with the Forge 1.21 line — both trail 1.20.1 by the same gap.

| Feature | Forge 1.20.1 | Forge 1.21.x | NeoForge 1.21.1 |
|---|---|---|---|
| Premium (Mojang) verification | yes | yes | yes |
| Offline fallback + policy | yes | yes | yes |
| Verified-name registry | yes | yes | yes |
| Join feedback (chat, opt-in title) | yes | yes | yes |
| Account-status badge | yes | yes | yes |
| Addon API (`AccountStatus`, callbacks) | **no** — name lookups only | yes | yes |
| Yggdrasil / skin-site accounts | yes | **no** | **no** |
| Offline to premium data migration | yes | **no** | **no** |
| Admin commands (`cleanupuuid`, `migrateuuid`) | yes | **no** | **no** |
| Skin refresh after join | yes | **no** | **no** |
| Recent-IP reconnect grace | yes | **no** | **no** |
| Configurable `timeoutMs` / `allowOfflineOnTimeout` | yes | **no** — 30s fixed | **no** — 30s fixed |
| `debug` logging toggle | yes | **no** | **no** |

Two traps behind that table:

- **`auth.yggdrasil.apiRootWhitelist` is inert on the 1.21 line.** The option
  exists and the server-side verifier honours it, but the 1.21 clients never
  resolve an authlib-injector endpoint — they always answer with an empty one, so
  verification always falls back to Mojang. 1.20.1's client reads the
  `-javaagent:` argument and `YggdrasilMinecraftSessionService.CHECK_URL`; that
  code has no 1.21 counterpart. Skin-site users are silently unsupported there.
- **The addon API is inverted.** 1.20.1 predates it and still exposes only
  `isPremium(name)` / `getPremiumUuid(name)`; the 1.21 line has the newer
  `AccountStatus` + `registerLoginCallback` surface. Porting it back to 1.20.1 is
  the smaller half of closing the gap.

User-facing strings live once in `platform/common-assets` for the 1.21 line and
NeoForge. `forge-1.20.1` keeps its own copy: it has ~34 extra keys and three
shared keys whose wording and meaning differ, so it needs a deliberate merge.

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
| 2026-07-15 | Forge 1.21.3 | Forge 53.1.0 / OpenJDK 21.0.11 | `platform/forge-1.21.3/build/libs/trueuuid-1.1.0-forge1.21.3.jar` | `:platform:forge-1.21.3:build` passed (shared `platform/forge-common` recompiled against 1.21.3 mappings; mixin refmap generated; unit tests passed). No login run. |
| 2026-07-15 | Forge 1.21.4 | Forge 54.1.14 / OpenJDK 21.0.11 | `platform/forge-1.21.4/build/libs/trueuuid-1.1.0-forge1.21.4.jar` | `:platform:forge-1.21.4:build` passed (shared source recompiled against 1.21.4 mappings; refmap generated; unit tests passed). No login run. |
| 2026-07-15 | Forge 1.21.5 | Forge 55.1.10 / OpenJDK 21.0.11 | `platform/forge-1.21.5/build/libs/trueuuid-1.1.0-forge1.21.5.jar` | `:platform:forge-1.21.5:build` passed (shared source recompiled against 1.21.5 mappings; refmap generated; unit tests passed). No login run. |
| 2026-07-15 | Forge 1.21.8 | Forge 58.1.0 / OpenJDK 21.0.11 | `platform/forge-1.21.8/build/libs/trueuuid-1.1.0-forge1.21.8.jar` | `:platform:forge-1.21.8:build` passed against the EventBus 7 API (shared source + the `.listener` `@SubscribeEvent` seam; refmap generated; unit tests passed). No login run. |
| 2026-07-15 | NeoForge 1.21.3 | NeoForge 21.3.56 / OpenJDK 21.0.11 | `platform/neoforge-1.21.3/build/libs/trueuuid-1.1.0-neoforge1.21.3.jar` | `:platform:neoforge-1.21.3:build` passed; it recompiles the hardened NeoForge source and runs the adapter lifecycle tests. No login run. |
| 2026-07-15 | NeoForge 1.21.4 | NeoForge 21.4.121 / OpenJDK 21.0.11 | `platform/neoforge-1.21.4/build/libs/trueuuid-1.1.0-neoforge1.21.4.jar` | `:platform:neoforge-1.21.4:build` passed; it recompiles the hardened NeoForge source and runs the adapter lifecycle tests. No login run. |
| 2026-07-15 | NeoForge 1.21.5 | NeoForge 21.5.74 / OpenJDK 21.0.11 | `platform/neoforge-1.21.5/build/libs/trueuuid-1.1.0-neoforge1.21.5.jar` | `:platform:neoforge-1.21.5:build` passed; it recompiles the hardened NeoForge source and runs the adapter lifecycle tests. No login run. |
| 2026-07-15 | NeoForge 1.21.8 | NeoForge 21.8.9 / OpenJDK 21.0.11 | `platform/neoforge-1.21.8/build/libs/trueuuid-1.1.0-neoforge1.21.8.jar` | Build and adapter lifecycle tests passed with a target-local GUI seam for the 1.21.6+ matrix stack. After the config-validator fix, a local `runServer` boot reached `Done` on 127.0.0.1:25565. No client boot or login run. |

These build artifacts are validation outputs, not release artifacts or runtime
support claims. Every row remains Planned until the complete acceptance matrix
in `docs/development/adding-adapter.md` passes, including migration rollback.

## Target axes

Minecraft version and loader are separate axes. For example, Forge 1.20.1 and
Fabric 1.20.1 may share Java-only protocol/authentication code, while their
packet, lifecycle, and Mixin adapters remain independent. Forge 1.21.1 and
NeoForge 1.21.1 are likewise separate targets.

Use one module per declared target:

```text
platform/
  forge-common/     # shared SOURCE root for the modern Forge line (not a module)
  forge-1.20.1/     # independent pre-configuration-phase island
  forge-1.21.1/     # \
  forge-1.21.3/     #  } each recompiles forge-common against its own Forge/mappings
  forge-1.21.4/     #  } and adds only its version-divergent shims
  forge-1.21.5/     # /
  forge-1.21.8/     # /  (EventBus 7)
  neoforge-1.21.1/ # shared modern-NeoForge source + 1.21.1 metadata
  neoforge-1.21.3/ # per-version metadata/build, recompiles the shared source
  neoforge-1.21.4/
  neoforge-1.21.5/
  neoforge-1.21.8/
```

Directories are added only when they contain a compiling adapter. Shared code
does not make an untested loader or Minecraft version supported.

## Modern Forge code sharing

The 1.20.2+ payload-based login protocol is one family. Rather than duplicate the
adapter across every 1.21.x patch, the version-independent code lives once in
`platform/forge-common/src/main` (a **source** root, not a Gradle module) and each
per-version module compiles it against its own Forge version and Minecraft
mappings via:

```gradle
def forgeCommon = "${rootDir}/platform/forge-common/src/main"
sourceSets.main.java.srcDir "${forgeCommon}/java"
sourceSets.main.resources.srcDir "${forgeCommon}/resources"
```

There is no shared bytecode — only shared source — so every module still gets a
correctly remapped jar and refmap. A file may live in `forge-common` only if it
compiles unchanged against *every* module that includes it. Empirically, across
Forge 52 (1.21.1) → 58 (1.21.8) the only divergence is:

- the login/GUI mixins + `CustomQueryPayload` records + `ClientAccountStatus`
  (copied per module; they compile unchanged because the login classes are stable
  by name across the range), and
- a one-file `TrueuuidForgeEvents` seam whose only version-specific line is the
  `@SubscribeEvent` import — `net.minecraftforge.eventbus.api.SubscribeEvent`
  (EventBus 6, Forge ≤ 55) vs `net.minecraftforge.eventbus.api.listener.SubscribeEvent`
  (EventBus 7, Forge 56+).

`forge-1.20.1` predates the configuration phase (different login classes, `@Mod`
event API, and `ResourceLocation` constructor) and deliberately does **not** use
this root.

## Modern NeoForge code sharing

The NeoForge 1.21.x adapters use the same source-sharing model: the hardened
adapter implementation remains in `platform/neoforge-1.21.1/src/main`, and every
later NeoForge module compiles that source against its own exact NeoForge and
Minecraft versions. Each later module owns its `neoforge.mods.toml`, Gradle run
configuration, artifact name, and version range. This is source sharing only;
no compiled classes or refmaps are reused between Minecraft versions. If a
NeoForge API diverges, the affected source must move to that target module
before it can be claimed to compile or work there. `neoforge-1.21.8` currently
does this for the badge draw: Minecraft 1.21.6+ switched from `PoseStack` to
`Matrix3x2fStack`. The common GUI registration uses NeoForge's current default
mod-bus subscription, which also compiles on the earlier 1.21 targets.

### Sharing a jar across patches

Source is shared; a **jar** is per Forge build (each patch has its own Forge
version and remapped refmap). A single module may still declare a wider
`mods.toml` Minecraft range to cover adjacent patches (e.g. `forge-1.21.8`
covering 1.21.6/1.21.7), but only after a two-sided login run passes on each
covered patch. Modules default to their exact build patch until then.

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

Lightweight continuous integration runs on pushes and pull requests. The
on-demand and release-triggered Full Self-Test builds and boots every
implemented target. Publishing is stricter:
[`release/targets.json`](../../release/targets.json) lists the exact target
artifacts eligible for a signed version tag. A compiling Planned target must
remain `"release": false`. The currently recorded acceptance evidence is
incomplete, so all targets remain disabled until a maintainer reviews an
approval change. See
[`release-automation.md`](../development/release-automation.md) for owner setup
and publishing details.

## Compatibility eras

Modern targets may share Java 17/21 core modules. Legacy Java 8 targets must
consume a deliberately small compatibility codec or independently implement
the frozen wire contract. Golden binary fixtures are the cross-era contract;
Minecraft, loader, mapping, authlib, and Gradle code are never shared across
that boundary.
