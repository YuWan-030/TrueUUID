# fabric-common — shared source for Fabric adapters

This directory is **not a Gradle module**. It is a shared source root consumed by
every Fabric target, exactly like `platform/forge-common` is for modern Forge.
Each Fabric module declares only pinned target metadata and applies
`target-matrix.gradle`, which composes the relevant source roots, for example:

```gradle
sourceSets.main.java.srcDirs "${fabricCommon}/main/java", "${fabricCommon}/legacy-1.20/java"
sourceSets.main.java.srcDir "${fabricCommon}/${target.sessionJoinEra}/java"
sourceSets.main.java.srcDir "${fabricCommon}/${target.playNetworkingEra}/java"
target.extraSourceEras.each { era ->
    sourceSets.main.java.srcDir "${fabricCommon}/${era}/java"
}
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

## Named API-era seams

Targets select small compile-time source roots instead of branching on the
Minecraft version at runtime:

- `legacy-1.20` — common login hooks, HUD, login Mixin, and resource templates;
- `session-profile` / `session-uuid` — the 1.20.1 versus 1.20.2 authlib join call;
- `play-buffers` / `play-payloads` — raw play buffers through 1.20.4 versus the
  typed custom-payload API in 1.20.6.
- `profile-bean` / `profile-record` — authlib bean getters and mutable property
  maps versus the authlib 7 immutable `GameProfile` record used by 1.21.10+;
- `permission-levels` / `permission-checks` — numeric command levels versus
  Minecraft 1.21.11's named permission checks;
- `identifier-constructors` / `identifier-factories` — public constructors
  versus `Identifier.of`;
- `hud-matrix-4d` / `hud-matrix-2d` — the legacy draw matrix stack through
  1.21.5 versus the JOML 2D stack in 1.21.6+;
- `session-api-services` — the 1.21.10+ session-service location.

Each `platform/fabric-<version>/build.gradle` owns exact Minecraft, Yarn,
Fabric Loader, Fabric API, Java, artifact, and metadata values. New eras add
similarly narrow named roots only for changes that compilation proves necessary.

## Rule

A file belongs here only if it compiles unchanged against **every** Fabric
target that includes it. When adding another target, start from this split,
select only the eras that compile for it, and add a new narrow seam only when
neither existing era matches.
