# forge-common — shared source for modern Forge adapters

This directory is **not a Gradle module**. It is a shared source root consumed by
every modern Forge target (the 1.20.2+ payload-based login protocol:
`forge-1.20.2`, `forge-1.20.4`, `forge-1.20.6`, `forge-1.21.1`,
`forge-1.21.3`, `forge-1.21.4`, `forge-1.21.5`, `forge-1.21.6`,
`forge-1.21.8`, …).
Each of those modules adds this tree via:

```gradle
def forgeCommon = "${rootDir}/platform/forge-common/src/main"
sourceSets.main.java.srcDir "${forgeCommon}/java"
sourceSets.main.resources.srcDir "${forgeCommon}/resources"
```

so the shared code is **recompiled per target against that target's Forge version
and Minecraft mappings**. There is no shared bytecode — only shared source. This is
what lets one fix (e.g. to the config surface or the offline-fallback policy) land in
every Forge version at once, while each version still gets a correct, remapped jar.

Narrow API eras use additional source-only roots beneath this directory. The
`src/login-matrix` root contains the unchanged pre-record Forge 52-58 client and
server login mixins used by Minecraft 1.21.1 through 1.21.8. Keeping this seam
shared prevents migration/security fixes from silently missing the earlier
1.21 targets. The `src/modern-matrix` root contains the remaining Forge 56/58
EventBus subscriptions, payload types, and 2D matrix HUD adapter used by
Minecraft 1.21.6 and 1.21.8. Forge 56 excludes only `TrueuuidClientOverlay`:
that event does not exist until the later Forge line, so 1.21.6 uses the shared
GUI mixin. Tests live in `src/modern-matrix-test`.

The `src/legacy-matrix` root similarly owns the unchanged Forge 48-50 login,
payload, event, HUD-scale, and lifecycle seams. `src/legacy-overlay` is the old
Forge 48/49 overlay API, while `src/layered-draw` is shared by Forge 50, 54,
and 55. Packet-envelope tests split only at Minecraft 1.20.5's StreamCodec
boundary. `legacy-matrix.gradle` holds the common ForgeGradle wiring and each
1.20.x target build supplies only exact version/toolchain metadata.

User-facing strings are **not** here: they live in `platform/common-assets`, which
both Forge and NeoForge consume, so a message is worded once for every loader. This
root is Forge-only, because its Java touches Forge APIs that NeoForge renames.

## What lives here (version-independent)

Files here may only touch APIs that are stable across the whole modern-Forge range:
the Forge mod lifecycle, `ForgeConfigSpec`, the event bus, `ServerPlayer`, and the
shared `cn.alini.trueuuid.protocol` module.

- `Trueuuid.java` — `@Mod` entrypoint
- `config/TrueuuidConfig.java` — `ForgeConfigSpec` surface
- `server/ForgeAdapterRuntime.java` — session verifier facade, join feedback, audit
- `server/ForgeVerifiedNameRegistry.java` — persistent known-premium-name store
- `server/OfflineFallbackPolicy.java` — pure offline-fallback decision
- `server/ForgeLoginFlow.java` — per-connection login state (delegates to `shared/protocol`)
- `net/ForgeQueryTracker.java` — pending transaction id set
- `net/ForgeNetIds.java` — the `trueuuid:auth` channel id
- `api/AccountStatus.java`, `api/TrueuuidApi.java` — the public addon API
- `client/ClientAccountStatus.java` — badge state + drawing (fill primitives only)
- `mixin/TrueuuidMixinPlugin.java` — gates mixins by dist and by HUD-API presence
- `mixin/client/ForgeClientGuiMixin.java` — badge draw for Forge 52/53 only
- `resources/` — `trueuuid.mixins.json`, `pack.mcmeta`

## What stays in narrow era roots or per-version modules

Anything that binds to a Minecraft or Forge API that changes between API eras.
Keep it in either a narrowly named source-only era root or the module's own
`src/` when only one target can consume it:

- `mixin/client/ForgeClientHandshakeMixin`, `mixin/client/ForgeClientQueryDecodeMixin`,
  `mixin/server/ForgeServerLoginMixin`, `mixin/server/ForgeServerAnswerDecodeMixin`
  — the login mixins. The pre-record Forge 52-58 handshake/server pair lives in
  `src/login-matrix`; the remaining packet decode/payload seams stay in their
  narrower roots or modules. `src/srg-runtime` enables member remapping for Forge
  48/49; `src/official-runtime` disables it for Forge 50's official-named
  production JAR. The build verifies the resulting refmap mode.
- `net/ForgeAuthPayload.java`, `net/ForgeAuthAnswerPayload.java` — the
  `CustomQueryPayload` / `CustomQueryAnswerPayload` implementations
- `TrueuuidForgeEvents.java` — game-event seam; differs only by the
  `@SubscribeEvent` import (`eventbus.api` on Forge ≤ 55 vs
  `eventbus.api.listener` on Forge 56+)
- `TrueuuidClientOverlay.java` — HUD seam; Forge 48/49 use the old
  `RegisterGuiOverlaysEvent` API while Forge 50/54+ use `ForgeLayeredDraw`; see
  the HUD table below
- `META-INF/mods.toml` — version and loader ranges

## Rule

A file belongs in `src/main` only if it compiles unchanged against **every** modern
Forge target that includes it. A file shared by a narrower API era belongs in a
named variant root such as `src/modern-matrix`; only a truly single-target seam is
duplicated in a module. `forge-1.20.1` is a separate pre-configuration-phase
protocol era and does **not** use this root.

Two recorded qualifications to that rule:

- `mixin/client/ForgeClientGuiMixin.java` compiles only on 1.21.x
  (`DeltaTracker` does not exist before 1.21). It stays here because the
  shared `trueuuid.mixins.json` used by the six 1.21.x modules lists it;
  the shared 1.20.x Gradle wiring excludes this one file and ships a
  parameterized mixins config without the GUI mixin entry. So
  the rule reads precisely: it compiles unchanged against every module that
  *includes* it.
- `src/main/resources` is consumed by the 1.21.x modules only. Forge
  1.20.2/1.20.4/1.20.6 consume the single parameterized resource templates in
  `src/legacy-matrix/resources`; each thin module provides its Minecraft/Forge
  ranges, pack format, and Mixin compatibility label.

## The HUD badge: two paths, one per pipeline

Forge's HUD extension API changes twice across the supported range, so badge
registration is an era seam while the drawing stays shared here in
`client/ClientAccountStatus`:

| Forge | HUD hook available | Path used |
|---|---|---|
| 48/49 (1.20.2/1.20.4) | `RegisterGuiOverlaysEvent` + `IGuiOverlay` | shared `src/legacy-overlay`; `ForgeClientGuiMixin` is excluded from the compile entirely |
| 50 (1.20.6) | `AddGuiOverlayLayersEvent` + `ForgeLayeredDraw` | shared `src/layered-draw`, also consumed by Forge 54/55 |
| 52 (1.21.1), 53 (1.21.3) | none | shared `mixin/client/ForgeClientGuiMixin` injects `Gui.render` |
| 54 (1.21.4), 55 (1.21.5) | `AddGuiOverlayLayersEvent` (EventBus 6) + `ForgeLayeredDraw.add(id, LayeredDraw.Layer)` | shared `src/layered-draw` |
| 56 (1.21.6) | no Forge HUD layer event; 2D matrix `GuiGraphics` | shared `ForgeClientGuiMixin` + `src/modern-matrix` scale seam; overlay event file excluded |
| 58 (1.21.8) | `AddGuiOverlayLayersEvent` (EventBus 7) + `ForgeLayeredDraw.add(id, ForgeLayer)` | shared `src/modern-matrix` `TrueuuidClientOverlay` |

`TrueuuidMixinPlugin` disables `ForgeClientGuiMixin` whenever `ForgeLayeredDraw`
is present, so exactly one path is active per version. This matters: on the
1.21.5+ pipeline a `Gui.render` inject still *fires*, but its draws are dropped —
which is why the badge silently never appeared on 1.21.8.

Two traps to remember:

- Do **not** trust Forge's `*-sources.jar` for API availability; it lists classes
  (`RegisterGuiOverlaysEvent`, `RenderGuiEvent`, `IGuiOverlay`) that are absent
  from the actual compile classpath. `javap` the
  `forge-<mc>-<ver>_mapped_official_<mc>.jar` instead.
- Forge 50+ production JARs keep official names for the login targets and
  `Gui.render`; those injections use `remap = false`. Forge 48/49 remain SRG
  and must retain their generated mappings.

The icon is drawn with `GuiGraphics.fill` primitives rather than a blit texture,
because the `blit` signatures differ across 1.21.1 through 1.21.8. Adding a real texture
would move the icon draw into the per-version seam.
