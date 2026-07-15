# forge-common — shared source for modern Forge adapters

This directory is **not a Gradle module**. It is a shared source root consumed by
every modern Forge target (the 1.20.2+ payload-based login protocol: `forge-1.21.1`,
`forge-1.21.3`, `forge-1.21.4`, `forge-1.21.5`, `forge-1.21.8`, …). Each of those
modules adds this tree via:

```gradle
def forgeCommon = "${rootDir}/platform/forge-common/src/main"
sourceSets.main.java.srcDir "${forgeCommon}/java"
sourceSets.main.resources.srcDir "${forgeCommon}/resources"
```

so the shared code is **recompiled per target against that target's Forge version
and Minecraft mappings**. There is no shared bytecode — only shared source. This is
what lets one fix (e.g. to the config surface or the offline-fallback policy) land in
every Forge version at once, while each version still gets a correct, remapped jar.

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
- `resources/` — `trueuuid.mixins.json`, `pack.mcmeta`, `assets/trueuuid/lang/*`

## What stays in each per-version module (version-divergent)

Anything that binds to a Minecraft or Forge API that changes between patches.
Keep these in the module's own `src/`:

- `mixin/client/ForgeClientHandshakeMixin`, `mixin/client/ForgeClientQueryDecodeMixin`,
  `mixin/server/ForgeServerLoginMixin`, `mixin/server/ForgeServerAnswerDecodeMixin`
  — the login mixins (the refmap is generated per build)
- `net/ForgeAuthPayload.java`, `net/ForgeAuthAnswerPayload.java` — the
  `CustomQueryPayload` / `CustomQueryAnswerPayload` implementations
- `TrueuuidForgeEvents.java` — game-event seam; differs only by the
  `@SubscribeEvent` import (`eventbus.api` on Forge ≤ 55 vs
  `eventbus.api.listener` on Forge 56+)
- `TrueuuidClientOverlay.java` — HUD-layer seam; present only on Forge 54+
  (see the HUD table below)
- `META-INF/mods.toml` — version and loader ranges

## Rule

A file belongs here only if it compiles unchanged against **every** modern Forge
target that includes it. If it needs even one version-specific tweak, it goes back
into the per-version modules (duplicated) instead. `forge-1.20.1` is a separate
pre-configuration-phase protocol era and does **not** use this root.

## The HUD badge: two paths, one per pipeline

Forge's HUD extension API changed with the 1.21.5+ render pipeline, so the badge
registration is a per-version seam while the drawing stays shared here in
`client/ClientAccountStatus`:

| Forge | HUD hook available | Path used |
|---|---|---|
| 52 (1.21.1), 53 (1.21.3) | none | shared `mixin/client/ForgeClientGuiMixin` injects `Gui.render` |
| 54 (1.21.4), 55 (1.21.5) | `AddGuiOverlayLayersEvent` (EventBus 6) + `ForgeLayeredDraw.add(id, LayeredDraw.Layer)` | per-module `TrueuuidClientOverlay` |
| 58 (1.21.8) | `AddGuiOverlayLayersEvent` (EventBus 7) + `ForgeLayeredDraw.add(id, ForgeLayer)` | per-module `TrueuuidClientOverlay` |

`TrueuuidMixinPlugin` disables `ForgeClientGuiMixin` whenever `ForgeLayeredDraw`
is present, so exactly one path is active per version. This matters: on the
1.21.5+ pipeline a `Gui.render` inject still *fires*, but its draws are dropped —
which is why the badge silently never appeared on 1.21.8.

Two traps to remember:

- Do **not** trust Forge's `*-sources.jar` for API availability; it lists classes
  (`RegisterGuiOverlaysEvent`, `RenderGuiEvent`, `IGuiOverlay`) that are absent
  from the actual compile classpath. `javap` the
  `forge-<mc>-<ver>_mapped_official_<mc>.jar` instead.
- Only the Forge-preserved *login* methods may use `remap = false`. An ordinary
  vanilla method such as `Gui.render` is obfuscated at runtime and must be
  remapped through the refmap, or the injection silently never matches.

The icon is drawn with `GuiGraphics.fill` primitives rather than a blit texture,
because the `blit` signatures differ across 1.21.1 through 1.21.8. Adding a real texture
would move the icon draw into the per-version seam.
