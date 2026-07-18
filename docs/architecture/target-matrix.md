# Target and release matrix

TrueUUID is a single repository with independent loader/Minecraft adapters.
An adapter is the unit of support and release; a Git branch is not.

## Current targets

| Target | Loader | Java | State | Notes |
|---|---|---:|---|---|
| Minecraft 1.20.1 | Forge 47.4.10 | 17 | Active | Implemented in `platform/forge-1.20.1` (independent pre-configuration-phase island). Local Mojang login/UUID/name/skin run passed on 2026-07-12 with JDK 17. Yggdrasil, denial/timeout/grace, and migration rollback runs remain pending. ⚠ 2026-07-16: a fresh `:platform:forge-1.20.1:build` jar carries official-named bytecode and no refmap (`reobfJar` runs but remaps nothing), which cannot apply mixins on Forge 47's SRG-named production runtime — re-validate the 2026-07-12 evidence against a production-shaped artifact before any release claim. Found while proving `forge-1.20.2`'s SRG-era pipeline; see that row. |
| Minecraft 1.20.1 | Fabric Loader 0.19.3 / Fabric API 0.92.9+1.20.1 | 17 target / 21 build launcher | Planned | `platform/fabric-1.20.1`; shares `platform/fabric-common`. Yarn `1.20.1+build.10`, Loom `1.13.6`, Gradle 8.14. Mojang login transport and bounded verification compile and test. It has no runtime or support claim until its focused two-sided matrix passes. |
| Minecraft 1.20.2 | Forge 48.1.0 | 17 | Planned | `platform/forge-1.20.2`; shares `platform/forge-common` (the 1.20.2 configuration-phase pivot joined the existing root — no separate `forge-1.20x-common` was needed; sole exclusion: `ForgeClientGuiMixin`). Build + mixin refmap + focused tests pass (2026-07-16). No login run yet. Protocol 764 is shared with no other patch, so its range stays single-patch permanently. Production Forge 48 runs SRG names: its client mixins drop the 1.21.x `remap = false` and the shipped jar is the reobfuscated one. Not in the root aggregate build, CI, or `release/targets.json` yet. |
| Minecraft 1.21.1 | Forge 52.1.0 | 21 | Planned | `platform/forge-1.21.1`; shares `platform/forge-common`. A local premium client/server login passed, but the full acceptance matrix remains incomplete. |
| Minecraft 1.21.3 | Forge 53.1.0 | 21 | Planned | `platform/forge-1.21.3`; shares `platform/forge-common`. Build + mixin refmap + tests pass (2026-07-15). No login run yet. |
| Minecraft 1.21.4 | Forge 54.1.14 | 21 | Planned | `platform/forge-1.21.4`; shares `platform/forge-common`. Build + mixin refmap + tests pass (2026-07-15). No login run yet. |
| Minecraft 1.21.5 | Forge 55.1.10 | 21 | Planned | `platform/forge-1.21.5`; shares `platform/forge-common`. Build + mixin refmap + tests pass (2026-07-15). No login run yet. |
| Minecraft 1.21.8 | Forge 58.1.0 | 21 | Planned | `platform/forge-1.21.8`; shares `platform/forge-common`. Uses the EventBus 7 seam. Build + mixin refmap + tests pass (2026-07-15). No login run yet. Candidate to cover 1.21.7 (protocol 772) once that login run passes; 1.21.6 is protocol 771 and needs its own module. |
| Minecraft 1.20.1 | NeoForge 47.1.106 (legacy `net.neoforged:forge` coordinate) | 17 | Planned (best-effort) | `platform/neoforge-1.20.1`; the `forge-1.20.1` island's source recompiled unchanged against the NeoForge 1.20.1 artifact under ForgeGradle 6 — that era keeps `net.minecraftforge` packages, the `forge` mod id, FML 47, and SRG production names, so there is no NeoForge-flavored source to write. Uniquely among NeoForge targets it carries the full 1.20.1 feature set (it is the island's code). Build + the island's tests pass and the jar is SRG-reobfuscated with its refmap (2026-07-17) — notably better-formed than the island's own current artifact, confirming the ⚠ on `forge-1.20.1` is module-local wiring. Explicitly approved best-effort against upstream guidance (NeoForged recommend Forge on 1.20.1): stays `release: false` regardless of outcome, lowest priority for runtime validation. Not in the root aggregate build, CI, or `release/targets.json`. |
| Minecraft 1.20.2 | NeoForge 20.2.93 | 17 | Planned | `platform/neoforge-1.20.2`; recompiles the `neoforge-1.21.1` privileged source (the configuration-phase pivot reuses it — no separate 1.20.x privileged module was needed). Three era seams are module-local copies (`TrueuuidClientOverlay`, `ServerLoginMixin`, `TrueuuidConfig`); `NetIds` was made range-stable in the shared source. Built with NeoGradle 7 because NeoForge 20.2 predates ModDevGradle's moddev-bundle metadata (20.4+ have it). Era metadata: `META-INF/mods.toml`, javafml `[1,)`, `mandatory=true`. Build + focused tests pass (2026-07-16). No login run yet. Protocol 764 pairs with no other patch, so the range stays single-patch permanently. Not in the root aggregate build, CI, or `release/targets.json`. |
| Minecraft 1.20.4 | NeoForge 20.4.251 | 17 | Planned | `platform/neoforge-1.20.4`; recompiles the `neoforge-1.21.1` privileged source with the same three era seams as `neoforge-1.20.2` (diagnostic-verified identical failure set), but on the standard ModDevGradle toolchain. Era metadata: `META-INF/mods.toml`, javafml `[1,)`. Build + focused tests pass (2026-07-16). No login run yet. Declares `[1.20.4,1.20.5)`; widening to cover 1.20.3 (protocol 765) waits on a 1.20.3 login run, which would ride a beta loader (NeoForge shipped only 20.3 betas). Not in the root aggregate build, CI, or `release/targets.json`. |
| Minecraft 1.20.6 | NeoForge 20.6.139 | 21 | Planned | `platform/neoforge-1.20.6`; recompiles the `neoforge-1.21.1` privileged source with a single era seam (`ServerLoginMixin`: `onDisconnect` takes `Component` before 1.21) — 20.6 already has `RegisterGuiLayersEvent`, the modern `EventBusSubscriber` form, and `ModContainer.registerConfig`. Uses `neoforge.mods.toml` with javafml `[3,)` via the parameterized shared template. Build + focused tests pass (2026-07-16). No login run yet. Declares `[1.20.6,1.20.7)`; widening to cover 1.20.5 (protocol 766) waits on a 1.20.5 login run, which would ride a beta loader (NeoForge shipped only 20.5 betas). Not in the root aggregate build, CI, or `release/targets.json`. |
| Minecraft 1.21.1 | NeoForge 21.1.213 | 21 | Planned | Implemented in `platform/neoforge-1.21.1`; the preserved `archive/neoforge-1.21.1-pre-monorepo` branch was API/lifecycle evidence only. Its JDK 21 build, shared fixtures, and codec/lifecycle tests pass, but no modded client/server matrix has run. It is the hardened source baseline for the later NeoForge 1.21.x modules. |
| Minecraft 1.21.3 | NeoForge 21.3.56 | 21 | Planned | `platform/neoforge-1.21.3`; recompiles the hardened NeoForge 1.21 adapter source against 1.21.3. Build and focused tests pass (2026-07-15). No login run yet. |
| Minecraft 1.21.4 | NeoForge 21.4.121 | 21 | Planned | `platform/neoforge-1.21.4`; recompiles the hardened NeoForge 1.21 adapter source against 1.21.4. Build and focused tests pass (2026-07-15). No login run yet. |
| Minecraft 1.21.5 | NeoForge 21.5.74 | 21 | Planned | `platform/neoforge-1.21.5`; recompiles the hardened NeoForge 1.21 adapter source against 1.21.5. Build and focused tests pass (2026-07-15). No login run yet. |
| Minecraft 1.21.8 | NeoForge 21.8.9 | 21 | Planned | `platform/neoforge-1.21.8`; recompiles the hardened NeoForge 1.21 adapter source against 1.21.8. Build, focused tests, and a local server boot pass (2026-07-15); the GUI seam uses the new 2D matrix stack. No login run yet. |
| Minecraft 1.21.6 | NeoForge 21.6.20-beta | 21 | Planned | `platform/neoforge-1.21.6`; recompiles the `neoforge-1.21.1` privileged source with the same `modernGuiSources` seam as `neoforge-1.21.8` (1.21.6 is where the Matrix3x2fStack pipeline begins). NeoForge never shipped a stable 21.6 — this builds on the beta-only line's last build. javafml `[3,)` per 21.6's own manifest. Build + focused tests pass (2026-07-17). No login run yet. Protocol 771 pairs with no other patch: single-patch range permanently. Not in the root aggregate build, CI, or `release/targets.json`. |
| Minecraft 1.21.10 | NeoForge 21.10.64 | 21 | Planned | `platform/neoforge-1.21.10`; recompiles the privileged source with `modernGuiSources` plus the new `recordEraSources` seam — Minecraft 1.21.9+ made authlib's `GameProfile` a record (`id()`/`name()`/`properties()`), moved the client session service to `Minecraft.services()`, and removed `ServerPlayer.getServer()`; exactly `AdapterRuntime`, `ServerLoginMixin`, and `ClientHandshakeMixin` carry those call sites. Build + focused tests pass (2026-07-17). No login run yet. Declares `[1.21.10,1.21.11)`; widening to cover 1.21.9 (protocol 773) waits on a 1.21.9 login run (beta-only 21.9 loader line). Not in the root aggregate build, CI, or `release/targets.json`. |
| Minecraft 1.21.11 | NeoForge 21.11.44 | 21 | Planned | `platform/neoforge-1.21.11`; recompiles the privileged source with `modernGuiSources` + `recordEraSources` + a third seam unique to this patch: 1.21.11's official mappings rename `ResourceLocation` to `Identifier` (verified in the 21.11 compile artifacts; 1.21.10 still has `ResourceLocation`), touching `NetIds`, `AuthPayload`, `ClientboundCustomQueryMixin`, and `TrueuuidClientOverlay`. Build + focused tests pass (2026-07-17). No login run yet. Protocol 774 pairs with no other patch and this is the final legacy-scheme release: single-patch range permanently. Not in the root aggregate build, CI, or `release/targets.json`. |
| Minecraft 1.12.2 | Forge 14.23.5.2860 | 8 | Deferred legacy | Requires an isolated JDK 8 build and protocol-compatibility fixtures. |

An empty folder, version range in metadata, or successful compilation alone is
not a support claim. Every supported target needs a real two-sided login run.
The six modern Forge modules build and pass unit tests, but all remain Planned
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
target in its column. `forge-1.20.2` (added 2026-07-16) also compiles
`platform/forge-common` and matches the Forge 1.21.x column exactly — same
login-verification core, same feature gaps.

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
| Admin commands (`cleanupuuid`, `migrateuuid`) | yes | **no** — coupled to migration | **no** — coupled to migration | **no** — coupled to migration |
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

- **The admin commands are fronts for the migration machinery.** 1.20.1's
  `cleanupuuid` and `migrateuuid` both call `MigrationCoordinator` /
  `PlayerDataMigration`, so they cannot be ported ahead of the
  offline→premium migration project; only `trueuuid mojang status` and
  `trueuuid reload` are independent of it.

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
| Fabric 1.20.1 | Mojang verification, policy-gated offline fallback with persisted registry, JSON config, shared strings, and default Forge/NeoForge-matching HUD; server audit lines added 2026-07-17 | passed (2026-07-17) | Partial two-sided run (2026-07-17): premium client joined with green badge, but the run predates the audit lines so server-side verification is unproven; rerun pending | Rerun capturing `session-verified premium login`, then the full Mojang matrix, then migration/admin commands, join feedback, and the addon API |
| Forge 1.21.1 | Login-verification core | passed | One premium login | Full matrix and Forge 1.20.1 feature backlog |
| Forge 1.21.3 / 1.21.4 / 1.21.5 / 1.21.8 | Same core via `forge-common` plus target seams | passed | none | Per-target login matrix and the shared 1.21 feature backlog |
| Forge 1.20.2 | Same core via `forge-common` plus target seams (pre-1.21 overlay API, SRG-era reobf and refmap) | passed (2026-07-16) | none | Per-target login matrix and the shared 1.21 feature backlog |
| NeoForge 1.21.1 | Login-verification core | passed | none | Full login matrix and Forge 1.20.1 feature backlog |
| NeoForge 1.21.3 / 1.21.4 / 1.21.5 | Recompile the 1.21.1 core | passed | none | Per-target login matrix and the shared 1.21 feature backlog |
| NeoForge 1.21.8 | Recompile the 1.21.1 core plus GUI seam | passed | Server boot only | Client/login matrix and the shared 1.21 feature backlog |
| NeoForge 1.20.2 | Recompile the 1.21.1 core plus three era seams (overlay API, onDisconnect signature, config registration) | passed (2026-07-16) | none | Per-target login matrix and the shared 1.21 feature backlog |
| NeoForge 1.20.4 | Recompile the 1.21.1 core plus the same three era seams (ModDevGradle toolchain) | passed (2026-07-16) | none | Per-target login matrix and the shared 1.21 feature backlog |
| NeoForge 1.20.6 | Recompile the 1.21.1 core plus one era seam (onDisconnect signature) | passed (2026-07-16) | none | Per-target login matrix and the shared 1.21 feature backlog |
| NeoForge 1.21.6 | Recompile the 1.21.1 core plus the 1.21.6+ GUI seam | passed (2026-07-17) | none | Per-target login matrix and the shared 1.21 feature backlog |
| NeoForge 1.21.10 | Recompile the 1.21.1 core plus GUI + record-era seams | passed (2026-07-17) | none | Per-target login matrix and the shared 1.21 feature backlog |
| NeoForge 1.21.11 | Recompile the 1.21.1 core plus GUI + record-era + Identifier-rename seams | passed (2026-07-17) | none | Per-target login matrix and the shared 1.21 feature backlog |
| NeoForge 1.20.1 | The `forge-1.20.1` island recompiled against NeoForge 47.1 (full feature set) | passed (2026-07-17) | none | Best-effort: two-sided login matrix on a NeoForge 47.1 install; lowest priority |

## Recorded runtime evidence

| Date | Target | Loader/JDK | Artifact | Result |
|---|---|---|---|---|
| 2026-07-12 | Forge 1.20.1 | Forge 47.4.10 / Java 17.0.12 | `trueuuid-1.1.0-forge1.20.1.jar` | Matching Prism client and offline-mode development server: Mojang `joinServer` and server `hasJoined` passed; verified UUID/name/skin and Mojang join feedback observed. |
| 2026-07-15 | Forge 1.21.1 | Forge 52.1.0 / Java 21.0.11 | `trueuuid-1.1.0-forge1.21.1.jar` | Matching Prism premium client and offline-mode development server: TrueUUID challenge, client `joinServer`, server session verification, premium UUID replacement, and localized join feedback passed. Offline fallback and the remaining acceptance scenarios are still pending runtime validation. |
| 2026-07-15 | Fabric 1.20.1 | Fabric Loader 0.19.3 / Java 21 launcher | Gradle `runServer` | The local development server loaded TrueUUID and reached the offline-mode bind on `127.0.0.1:25565`; the bounded smoke stopped during world generation. No Fabric client or login test ran. |
| 2026-07-15 | Fabric 1.20.1 | Fabric Loader 0.19.3 / Java 21 launcher | Gradle `runServer` | After the offline policy, registry, shared strings, timeout, grace, and skin-refresh ports: a fresh boot generated `config/trueuuid.json` with the new options and reached `Done (4.303s)`. No login run. |
| 2026-07-15 | NeoForge 1.21.8 | NeoForge 21.8.9 / Java 21 | Gradle `runServer` | After the parity ports: a fresh boot regenerated `trueuuid-common.toml` with the new `timeoutMs`, `allowOfflineOnTimeout`, `debug`, and `[auth.recentIpGrace]` options (validator clean) and reached `Done (1.360s)`. No login run. |
| 2026-07-17 | Fabric 1.20.1 | Fabric Loader 0.19.3 / Java 21 launcher | Gradle `runServer` + premium client | First real two-sided run: the dev server reached `Done (8.481s)`, a premium client connected, joined with the TrueUUID handshake (client HUD showed the green premium badge), and disconnected cleanly. **Recorded as partial**: at the time, a fully successful premium verification logged nothing server-side — `FabricLoginTransaction` had no challenge-sent/session-verified/fallback-accepted audit lines (all INFO logging was debug-gated or absent), so this run cannot prove the server's `hasJoined` leg. The audit lines were added the same day; a rerun must capture `TrueUUID session-verified premium login` before Phase 0 counts as closed. |

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
| 2026-07-16 | Forge 1.20.2 | Forge 48.1.0 / Java 17 toolchain (JDK 21 launcher) | `platform/forge-1.20.2/build/libs/trueuuid-1.1.0-forge1.20.2.jar` | `:platform:forge-1.20.2:build` passed: `forge-common` recompiled against 1.20.2 mappings with the single `ForgeClientGuiMixin` exclusion; unit tests and `shared/protocol` fixtures passed. The jar is the reobfuscated (SRG) artifact — verified to carry the refmap with client-mixin entries and SRG-renamed shadow members, because production Forge 48 runs SRG names (confirmed against the `1.20.2-48.1.0` universal jar). All five 1.21.x modules were rebuilt and re-tested the same day against the shared-root change (`ForgeNetIds` now uses `ResourceLocation.tryParse`, the only factory present across 1.20.2–1.21.8). No login run. |
| 2026-07-16 | NeoForge 1.20.4 | NeoForge 20.4.251 / Java 17 toolchain (JDK 21 launcher) / ModDevGradle 2.0.118 | `platform/neoforge-1.20.4/build/libs/trueuuid-1.1.0-neoforge1.20.4.jar` | `:platform:neoforge-1.20.4:build` passed with the same three era-seam exclusions as 1.20.2 (diagnostic-verified identical failure set); era-correct `META-INF/mods.toml` (javafml `[1,)`), JAVA_17 mixin config. No login run. |
| 2026-07-16 | NeoForge 1.20.6 | NeoForge 20.6.139 / Java 21 / ModDevGradle 2.0.118 | `platform/neoforge-1.20.6/build/libs/trueuuid-1.1.0-neoforge1.20.6.jar` | `:platform:neoforge-1.20.6:build` passed with a single era-seam exclusion (`ServerLoginMixin`); the shared `TrueuuidClientOverlay` compiles unchanged there now that it uses `ResourceLocation.tryParse`. `neoforge.mods.toml` with javafml `[3,)` (matching 20.6's own manifest). All other NeoForge modules rebuilt clean against the shared-source change. No login run. |
| 2026-07-17 | NeoForge 1.20.1 | NeoForge 47.1.106 (`net.neoforged:forge`) / Java 17 toolchain / ForgeGradle 6 | `platform/neoforge-1.20.1/build/libs/trueuuid-1.1.0-neoforge1.20.1.jar` | `:platform:neoforge-1.20.1:build` passed: the forge-1.20.1 island source and tests recompiled unchanged against NeoForge 1.20.1 (FG6 accepted the legacy-coordinate userdev artifact directly). Jar verified SRG-reobfuscated with refmap. No login run. |
| 2026-07-17 | NeoForge 1.21.6 | NeoForge 21.6.20-beta / Java 21 / ModDevGradle 2.0.118 | `platform/neoforge-1.21.6/build/libs/trueuuid-1.1.0-neoforge1.21.6.jar` | `:platform:neoforge-1.21.6:build` passed with the `modernGuiSources` seam only — the 1.21.8 recipe transplants cleanly to the beta-only 21.6 line. No login run. |
| 2026-07-17 | NeoForge 1.21.10 | NeoForge 21.10.64 / Java 21 / ModDevGradle 2.0.118 | `platform/neoforge-1.21.10/build/libs/trueuuid-1.1.0-neoforge1.21.10.jar` | `:platform:neoforge-1.21.10:build` passed after adding `recordEraSources`: authlib 7 (`GameProfile` record), `Minecraft.services().sessionService()`, and `player.level().getServer()` verified against the 21.10 compile artifacts. No login run. |
| 2026-07-17 | NeoForge 1.21.11 | NeoForge 21.11.44 / Java 21 / ModDevGradle 2.0.118 | `platform/neoforge-1.21.11/build/libs/trueuuid-1.1.0-neoforge1.21.11.jar` | `:platform:neoforge-1.21.11:build` passed after additionally adding `identifierEraSources` for the `ResourceLocation` → `Identifier` official-mappings rename (verified: `net/minecraft/resources/Identifier.class` in the 21.11 artifacts, absent in 21.10's). No login run. |
| 2026-07-16 | NeoForge 1.20.2 | NeoForge 20.2.93 / Java 17 toolchain (JDK 21 launcher) / NeoGradle 7.0.192 | `platform/neoforge-1.20.2/build/libs/trueuuid-1.1.0-neoforge1.20.2.jar` | `:platform:neoforge-1.20.2:build` passed: the `neoforge-1.21.1` privileged source recompiled against 1.20.2 with three era-seam file exclusions and the shared `NetIds` moved to `ResourceLocation.tryParse`; focused tests passed; the jar carries era-correct `META-INF/mods.toml` (javafml `[1,)`), JAVA_17 mixin config, and Java 17 bytecode. All five 1.21.x NeoForge modules rebuilt and re-tested the same day against the shared-source change. No login run. |

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
  fabric-common/    # shared SOURCE root for the Fabric line (not a module)
  forge-1.20.1/     # independent pre-configuration-phase island
  fabric-1.20.1/    # Fabric login-phase adapter; consumes fabric-common
  forge-1.20.2/     # first configuration-phase Forge target; recompiles forge-common
                    # (SRG-era: ships the reobfuscated jar with refmap)
  forge-1.21.1/     # \
  forge-1.21.3/     #  } each recompiles forge-common against its own Forge/mappings
  forge-1.21.4/     #  } and adds only its version-divergent shims
  forge-1.21.5/     # /
  forge-1.21.8/     # /  (EventBus 7)
  neoforge-1.20.1/ # best-effort: the forge-1.20.1 island recompiled against
                   # NeoForge 47.1 (legacy coordinate, FG6, full feature set)
  neoforge-1.20.2/ # first configuration-phase NeoForge target; recompiles the
                   # 1.21.1 privileged source with era seams (NeoGradle 7)
  neoforge-1.20.4/ # same three era seams as 1.20.2, standard ModDevGradle
  neoforge-1.20.6/ # single era seam (login mixin); neoforge.mods.toml era
  neoforge-1.21.1/ # shared modern-NeoForge source + 1.21.1 metadata
  neoforge-1.21.3/ # per-version metadata/build, recompiles the shared source
  neoforge-1.21.4/
  neoforge-1.21.5/
  neoforge-1.21.6/  # beta-only NeoForge line; 1.21.6+ GUI seam
  neoforge-1.21.8/
  neoforge-1.21.10/ # + record-era seams (GameProfile record, services())
  neoforge-1.21.11/ # + Identifier rename (final legacy-scheme release)
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
compiles unchanged against *every* module that includes it. Since 2026-07-16 the
family also spans the configuration-phase pivot: `forge-1.20.2` recompiles this
same root (no separate `forge-1.20x-common` was needed). Its extra seams beyond
the list below: `ForgeClientGuiMixin` is 1.21.x-only (`DeltaTracker`) and is
excluded from the 1.20.2 compile; `ForgeNetIds` uses `ResourceLocation.tryParse`,
the only factory present across the whole range; and production Forge 48 still
runs SRG names, so the 1.20.2 client mixins must not use the 1.21.x line's
`remap = false` and its shipped jar is the reobfuscated one. Forge 49/50
(1.20.3–1.20.6) production naming must be re-checked when those modules are
built. Empirically, across Forge 52 (1.21.1) → 58 (1.21.8) the only divergence
is:

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

## Fabric code sharing

The Fabric line uses the same source-sharing model:
`platform/fabric-common/src` (a **source** root, not a Gradle module) holds the
version-independent adapter code — entrypoint, JSON config, offline policy,
verified-name registry, reconnect grace wiring, session check, payload bounds,
and the login transaction — plus the loader-agnostic tests. Each Fabric module
adds it via `srcDir` and recompiles it against its own Minecraft/Yarn versions.
Version-divergent seams stay per module: `FabricLoginNetworking` (the
`Identifier` constructor and client session API drift), the client HUD classes
(`HudRenderCallback`'s signature changed at 1.21), the login mixin, and the
`fabric.mod.json`/mixins metadata. The rule matches forge-common's: a file may
live in `fabric-common` only if it compiles unchanged against every Fabric
target that includes it.

## Modern NeoForge code sharing

The NeoForge 1.21.x adapters use the same source-sharing model: the hardened
adapter implementation remains in `platform/neoforge-1.21.1/src/main`, and every
later NeoForge module compiles that source against its own exact NeoForge and
Minecraft versions. Since 2026-07-16 that includes `neoforge-1.20.2`, the
configuration-phase pivot: pointing it at the unmodified privileged source
failed on exactly four files, so no separate 1.20.x privileged module exists.
`NetIds` now uses `ResourceLocation.tryParse` (the only factory present across
1.20.2–1.21.x; same finding as the Forge line) and the other three are
module-local copies via the `modernGuiSources`-style compile-source exclusion:
`TrueuuidClientOverlay` (RegisterGuiLayersEvent is 1.21+; 20.2 uses
RegisterGuiOverlaysEvent), `mixin/server/ServerLoginMixin` (`onDisconnect`
takes `Component` before 1.21), and `config/TrueuuidConfig` (FML 1.x registers
configs on `ModLoadingContext`, not `ModContainer`). Toolchain era boundary:
NeoForge 20.2 predates the moddev-bundle Gradle metadata ModDevGradle 2
requires (20.4+ publish it), so `neoforge-1.20.2` alone builds with NeoGradle 7
userdev; it also predates the `neoforge.mods.toml` rename, so it owns its
`META-INF/mods.toml` (javafml `[1,)`, `mandatory=true`) instead of applying
`neoforge-common/metadata.gradle`.

Two newer era seams sit at the top of the range (2026-07-17, both found by
diagnostic compiles and verified against the published compile artifacts).
These are vanilla/authlib changes, so the Forge 1.21.9–1.21.11 work will hit
them too:

- **1.21.9+ record era** (`recordEraSources` in `neoforge-1.21.10` and
  `neoforge-1.21.11`): authlib's `GameProfile` became a record
  (`id()`/`name()`/`properties()`), the client session service moved to
  `Minecraft.services().sessionService()`, and `ServerPlayer.getServer()` is
  gone (use `player.level().getServer()`; `ServerPlayer.level()` returns
  `ServerLevel` covariantly). Affects exactly `AdapterRuntime`,
  `ServerLoginMixin`, and `ClientHandshakeMixin`.
- **1.21.11 Identifier rename** (`identifierEraSources` in
  `neoforge-1.21.11` only): the official mappings rename `ResourceLocation`
  to `Identifier` in the final legacy-scheme release (1.21.10 still has
  `ResourceLocation`). Affects exactly `NetIds`, `AuthPayload`,
  `ClientboundCustomQueryMixin`, and `TrueuuidClientOverlay`.

Each later module owns its `neoforge.mods.toml`, Gradle run
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
covering 1.21.7), but only after a two-sided login run passes on each
covered patch. Modules default to their exact build patch until then.

#### Tracked pending widens (declared ranges unchanged)

Recorded 2026-07-16 from the protocol evidence in
[`version-consolidation-roadmap.md`](version-consolidation-roadmap.md). Each
row is a widen candidate only: the declared range stays at the build patch
until a two-sided login run passes on the newly-claimed patch. Forge modules
carry the note in `mods.toml`; NeoForge modules carry it next to their
`neoforgeMetadata` block.

##### Forge

| Module | Declared range today | Eventual target | Newly-claimed patch | Gate status |
|---|---|---|---|---|
| `forge-1.21.1` | `[1.21.1,1.21.2)` | `[1.21,1.21.2)`, loaderVersion `[51,)` | 1.21 (protocol 767) | Declared range unchanged pending a login run on 1.21 |
| `forge-1.21.3` | `[1.21.3,1.21.4)` | `[1.21.2,1.21.4)` | 1.21.2 (protocol 768) | Unreachable: Forge never shipped a 1.21.2 loader (promotions checked 2026-07-16), so the login-run gate cannot be met |
| `forge-1.21.8` | `[1.21.8,1.21.9)` | `[1.21.7,1.21.9)`, loaderVersion `[57,)` | 1.21.7 (protocol 772) | Declared range unchanged pending a login run on 1.21.7. Corrects the earlier `[1.21.6,1.21.9)` note: 1.21.6 is protocol 771 and gets its own module |

##### NeoForge

| Module | Declared range today | Eventual target | Newly-claimed patch | Gate status |
|---|---|---|---|---|
| `neoforge-1.21.1` | `[1.21.1,1.21.2)` | `[1.21,1.21.2)` | 1.21 (protocol 767) | Declared range unchanged pending a login run on 1.21 (NeoForge 21.0 line exists, latest 21.0.167) |
| `neoforge-1.21.3` | `[1.21.3,1.21.4)` | `[1.21.2,1.21.4)` | 1.21.2 (protocol 768) | Declared range unchanged pending a login run on 1.21.2; NeoForge shipped only two early 21.2 betas, so that run rides a beta loader |
| `neoforge-1.21.8` | `[1.21.8,1.21.9)` | `[1.21.7,1.21.9)` | 1.21.7 (protocol 772) | Declared range unchanged pending a login run on 1.21.7 (NeoForge 21.7 is a beta-only line). 1.21.6 is protocol 771 and gets its own module |

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
