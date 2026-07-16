# Target and release matrix

TrueUUID is a single repository with independent loader/Minecraft adapters.
An adapter is the unit of support and release; a Git branch is not.

## Current targets

| Target | Loader | Java | State | Notes |
|---|---|---:|---|---|
| Minecraft 1.20.1 | Forge 47.4.10 | 17 | Active | Implemented in `platform/forge-1.20.1` (independent pre-configuration-phase island). Local Mojang login/UUID/name/skin run passed on 2026-07-12 with JDK 17. Yggdrasil, denial/timeout/grace, and migration rollback runs remain pending. |
| Minecraft 1.20.1 | Fabric Loader 0.19.3 / Fabric API 0.92.9+1.20.1 | 17 target / 21 build launcher | Planned | `platform/fabric-1.20.1`; Yarn `1.20.1+build.10`, Loom `1.13.6`, Gradle 8.14. Mojang login transport and bounded verification compile and test. It has no runtime or support claim until its focused two-sided matrix passes. |
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

Forge and NeoForge targets verify premium logins, but the 1.21 line is a
login-verification core: it does not yet carry the surrounding features 1.20.1
grew. NeoForge is in step with the Forge 1.21 line — both trail 1.20.1 by the
same gap. Fabric 1.20.1 is a new adapter whose first secure vertical slice is
Mojang login verification; its goal is the Forge 1.20.1 column, not the
reduced 1.21 feature set.

Column coverage: **Forge 1.21.x** = 1.21.1 / 1.21.3 / 1.21.4 / 1.21.5 / 1.21.8 (five
targets sharing `platform/forge-common`). **NeoForge 1.21.x** = the same five
versions, sharing the `neoforge-1.21.1` adapter source. A cell applies to every
target in its column.

`fallback` and `policy` are separate rows on purpose: allowing an unverified
client to keep its offline UUID is not the same as deciding *whether* it may.

| Feature | Forge 1.20.1 | Fabric 1.20.1 | Forge 1.21.x | NeoForge 1.21.x |
|---|---|---|---|---|
| Premium (Mojang) verification | yes | yes — runtime pending | yes | yes |
| Offline fallback (join allowed) | yes | yes — runtime pending | yes | yes |
| Offline **policy** (deny known-premium names) | yes | yes | yes | yes |
| Verified-name registry (persisted) | yes | yes | yes | yes |
| Join feedback (chat, opt-in title) | yes | **no** | yes | yes |
| Account-status badge | yes | yes — position/scale hardcoded | yes | yes |
| Config file | yes | yes — JSON, offline policy only | yes | yes |
| Addon API (`AccountStatus`, callbacks) | yes | **no** | yes | yes |
| Localized strings from `common-assets` | own copy | yes | yes | yes |
| Yggdrasil / skin-site accounts | yes | **no** — refuses the login | yes — runtime pending | yes — runtime pending |
| Offline to premium data migration | yes | **no** | **no** | **no** |
| Admin commands (`cleanupuuid`, `migrateuuid`) | yes | **no** | **no** | **no** |
| Skin refresh after join | yes | yes — others only | yes — others only | yes — others only |
| Recent-IP reconnect grace | yes | yes | yes | yes |
| Configurable `timeoutMs` / `allowOfflineOnTimeout` | yes | yes | yes | yes |
| `debug` logging toggle | yes | yes | yes | yes |

Traps behind that table:

- **Fabric's offline fallback is policy-gated since 2026-07-15.** Every adapter
  now consults `OfflineFallbackPolicy` plus a persistent verified-name registry
  before releasing an unverified profile. Fabric's policy options live in
  `config/trueuuid.json` (same names and defaults as the Forge/NeoForge TOML);
  its registry shares the `trueuuid-registry.json` file shape with the other
  loaders.
- **Fabric strings come from `platform/common-assets` since 2026-07-15.** Its
  disconnect reasons are `trueuuid.disconnect.*` translation keys and the
  duplicate two-key `lang/` copy is gone. Its custom-endpoint refusal uses the
  shared `trueuuid.disconnect.custom_endpoint_unsupported` key.
- **Fabric fails closed on skin-site logins, the 1.21 line fails open.** Fabric
  refuses the login with the `custom_endpoint_unsupported` disconnect; the
  1.21 Forge/NeoForge clients silently answer with an empty endpoint and fall
  back to Mojang. Fabric's behaviour is the honest one.

- **`auth.yggdrasil.apiRootWhitelist` is live on the 1.21 line since
  2026-07-15.** The 1.21 clients resolve the authlib-injector endpoint the same
  way 1.20.1 does — the `-javaagent:` argument first, then
  `YggdrasilMinecraftSessionService.CHECK_URL` reflection
  (`cn.alini.trueuuid.client.ClientYggdrasilEndpoint`, duplicated verbatim for
  NeoForge) — and send it as `customEndpoint`. The server-side allowlist, DNS
  pinning, and no-redirect checks are unchanged; an endpoint outside the
  allowlist still fails verification. No skin-site login run has been recorded
  on any 1.21 target yet.
- **The addon API is unified since 2026-07-15.** 1.20.1 now carries the
  `AccountStatus` + `getStatus` + `registerLoginCallback` surface
  (`server/AccountStatusTracker`, published at join before feedback). One
  residual asymmetry: 1.20.1's pre-existing `getPremiumUuid(name)` returns a
  nullable `UUID`, while the 1.21 line returns `Optional<UUID>`; the same
  method name cannot carry both signatures, so changing it would break
  released 1.20.1 addons.

User-facing strings live once in `platform/common-assets` for the 1.21 line and
NeoForge. `forge-1.20.1` keeps its own copy: it has ~34 extra keys and three
shared keys whose wording and meaning differ, so it needs a deliberate merge.

### Per-target validation tracker

Feature source and runtime evidence are separate. "Build/tests" means the
declared toolchain can produce the adapter and its focused tests pass; it is
not a login or release claim.

| Target | Source state | Build/tests | Recorded runtime | Main gap before Active |
|---|---|---|---|---|
| Forge 1.20.1 | Full reference feature set | passed | One Mojang login | Yggdrasil, denial, timeout/grace, migration rollback |
| Fabric 1.20.1 | Mojang verification, policy-gated offline fallback with persisted registry, JSON config, shared strings, and default Forge/NeoForge-matching HUD | passed (2026-07-15) | Server bootstrap reached localhost bind; no login run | Two-sided Mojang matrix, then the remaining Forge 1.20.1 features |
| Forge 1.21.1 | Login-verification core | passed | One premium login | Full matrix and Forge 1.20.1 feature backlog |
| Forge 1.21.3 / 1.21.4 / 1.21.5 / 1.21.8 | Same core via `forge-common` plus target seams | passed | none | Per-target login matrix and the shared 1.21 feature backlog |
| NeoForge 1.21.1 | Login-verification core | passed | none | Full login matrix and Forge 1.20.1 feature backlog |
| NeoForge 1.21.3 / 1.21.4 / 1.21.5 | Recompile the 1.21.1 core | passed | none | Per-target login matrix and the shared 1.21 feature backlog |
| NeoForge 1.21.8 | Recompile the 1.21.1 core plus GUI seam | passed | Server boot only | Client/login matrix and the shared 1.21 feature backlog |

## Recorded runtime evidence

| Date | Target | Loader/JDK | Artifact | Result |
|---|---|---|---|---|
| 2026-07-12 | Forge 1.20.1 | Forge 47.4.10 / Java 17.0.12 | `trueuuid-1.1.0-forge1.20.1.jar` | Matching Prism client and offline-mode development server: Mojang `joinServer` and server `hasJoined` passed; verified UUID/name/skin and Mojang join feedback observed. |
| 2026-07-15 | Forge 1.21.1 | Forge 52.1.0 / Java 21.0.11 | `trueuuid-1.1.0-forge1.21.1.jar` | Matching Prism premium client and offline-mode development server: TrueUUID challenge, client `joinServer`, server session verification, premium UUID replacement, and localized join feedback passed. Offline fallback and the remaining acceptance scenarios are still pending runtime validation. |
| 2026-07-15 | Fabric 1.20.1 | Fabric Loader 0.19.3 / Java 21 launcher | Gradle `runServer` | The local development server loaded TrueUUID and reached the offline-mode bind on `127.0.0.1:25565`; the bounded smoke stopped during world generation. No Fabric client or login test ran. |

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
| 2026-07-15 | Fabric 1.20.1 | Fabric Loader 0.19.3 / Fabric API 0.92.9+1.20.1 / Java 21 launcher (Java 17 target) | `platform/fabric-1.20.1/build/libs/trueuuid-1.1.0-fabric1.20.1.jar` | `:platform:fabric-1.20.1:test :platform:fabric-1.20.1:remapJar` passed. The first slice has login-phase packet bounds, local client `joinServer`, bounded Mojang `hasJoined`, and profile replacement. No client/server runtime run. |

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
  fabric-1.20.1/    # independent Fabric login-phase adapter
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
