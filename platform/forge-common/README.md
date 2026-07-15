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
- `mixin/TrueuuidMixinPlugin.java` — applies client/server mixins to the matching dist
- `resources/` — `trueuuid.mixins.json`, `pack.mcmeta`, `assets/trueuuid/lang/*`

## What stays in each per-version module (version-divergent)

Anything that binds to a Minecraft internal that Mojang renames or re-signatures
between patches. Keep these in the module's own `src/`:

- `mixin/**` — all login/GUI mixins (target method names + descriptors drift; the
  refmap is generated per build)
- `net/ForgeAuthPayload.java`, `net/ForgeAuthAnswerPayload.java` — the
  `CustomQueryPayload` / `CustomQueryAnswerPayload` implementations
- `client/ClientAccountStatus.java` — HUD render (`GuiGraphics.blit` / `Gui.render`
  signatures change, notably at 1.21.4/1.21.5)
- `META-INF/mods.toml` — version and loader ranges

## Rule

A file belongs here only if it compiles unchanged against **every** modern Forge
target that includes it. If it needs even one version-specific tweak, it goes back
into the per-version modules (duplicated) instead. `forge-1.20.1` is a separate
pre-configuration-phase protocol era and does **not** use this root.
