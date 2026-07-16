# fabric-common — shared source for Fabric adapters

This directory is **not a Gradle module**. It is a shared source root consumed by
every Fabric target, exactly like `platform/forge-common` is for modern Forge.
Each Fabric module adds this tree via:

```gradle
def fabricCommon = "${rootDir}/platform/fabric-common/src"
sourceSets.main.java.srcDir "${fabricCommon}/main/java"
sourceSets.test.java.srcDir "${fabricCommon}/test/java"
```

so the shared code is **recompiled per target against that target's Minecraft
and Yarn versions**. There is no shared bytecode — only shared source. One fix
here (config surface, offline policy, registry, login transaction) lands in
every Fabric version at once, while each version still gets a correct, remapped
jar and refmap.

User-facing strings are **not** here: they live in `platform/common-assets`,
which every loader consumes.

## What lives here (version-independent)

Files here may only touch APIs that are stable across every Fabric target that
includes this root: the Fabric loader/entrypoint API, the login-phase
networking *model* (buffers, transaction, state access), authlib's
`GameProfile`, and the shared `cn.alini.trueuuid.protocol` module.

- `TrueuuidFabric.java` — `ModInitializer` entrypoint; wires the per-version
  `FabricLoginNetworking` seam (like forge-common's `TrueuuidForgeEvents`)
- `config/FabricConfig.java` — JSON config surface (`config/trueuuid.json`);
  option names/defaults mirror the Forge/NeoForge TOML
- `login/FabricLoginTransaction.java` — per-connection login state machine,
  offline policy gate, grace acceptance, translation-key disconnects
- `login/FabricAdapterRuntime.java` — verified-name registry + reconnect grace
- `login/FabricVerifiedNameRegistry.java` — persistent known-premium-name store
  (same `trueuuid-registry.json` shape as the other loaders)
- `login/OfflineFallbackPolicy.java` — pure offline-fallback decision
- `login/FabricSessionCheck.java` — bounded Mojang `hasJoined` verifier
- `login/FabricLoginPayloads.java` — bounded `PacketByteBuf` boundary
- `login/FabricLoginStateAccess.java` — accessor interface the login mixin
  implements
- `login/FabricVerifiedProfiles.java` — verified profile → `GameProfile`
- `src/test/java` — loader-agnostic tests, shared into each module's test run

## What stays in each per-version module (version-divergent)

Anything binding to an API that changes between Minecraft/Fabric versions:

- `FabricLoginNetworking.java` — channel + client/server hook registration
  (`new Identifier(...)` became `Identifier.of(...)` at 1.21; the client
  session API also drifts). This is the seam `TrueuuidFabric` calls.
- `client/TrueuuidFabricClient.java`, `client/FabricClientStatus.java` — the
  HUD badge (`HudRenderCallback`'s signature changed at 1.21)
- `mixin/server/ServerLoginNetworkHandlerMixin.java` — the login mixin (the
  refmap is generated per build; shadowed member names drift between versions)
- `fabric.mod.json`, `trueuuid.fabric.mixins.json` — version and loader ranges

## Rule

A file belongs here only if it compiles unchanged against **every** Fabric
target that includes it. If it needs even one version-specific tweak, it moves
back into the per-version modules (duplicated) instead. When adding a Fabric
target older or newer than 1.20.1, start from this split, build, and demote
whatever fails to compile.
