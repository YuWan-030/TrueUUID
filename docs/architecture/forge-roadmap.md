# TrueUUID Forge architecture roadmap

Status: verified against `origin/1.20` (`99cc1f5`) and `origin/1.21`
(`abb9454`) on 2026-07-12. The latter is a NeoForge port, not a Forge build;
its detached `./gradlew build` passed under JDK 21 during this review.

## Protocol today

TrueUUID is a bilateral login-phase protocol; the matching mod is required on
both client and server. On an offline-mode server, the 1.20.1 adapter injects
after `ServerLoginPacketListenerImpl.handleHello`, creates a nonce and sends a
`ClientboundCustomQueryPacket` on `trueuuid:auth`. The client intercepts
`ClientHandshakePacketListenerImpl.handleCustomQuery`, calls authlib
`joinServer` locally (the access token never crosses the connection), and
returns `ok`, an optional Yggdrasil `hasJoined` endpoint, migration consent, and
the missing-token flag in `ServerboundCustomQueryPacket`. The server pauses
login from `tick`, verifies `hasJoined` asynchronously, replaces the pending
`GameProfile` with the verified UUID/name/textures, records the result, and
allows Forge login negotiation to resume. If verified offline-UUID data exists,
a second login query asks for migration consent before acceptance.

The Java-only wire contract is therefore: channel id; nonce query; bounded UTF-8
answer fields and flags; auth decision; verified identity result; optional
migration offer/answer. Minecraft packet objects, buffers, listeners, mixins,
text components, server scheduling, configuration and lifecycle events are not
part of that contract.

## Version-specific touchpoints

| Area | Forge 1.20.1 adapter | NeoForge 1.21.1 evidence / modern Forge port concern |
|---|---|---|
| Loader/build | ForgeGradle 6, `net.minecraftforge:forge:1.20.1-47.4.8`, `mods.toml`, Java 17 | Existing other branch uses ModDevGradle, NeoForge 21.1.213, `neoforge.mods.toml`, Java 21; it cannot be reused as a Forge build |
| Entrypoint/config | Forge `@Mod`, `ModLoadingContext`, `ModConfig`, `ModConfigSpec`, `FMLPaths` | Package/event APIs change to `net.neoforged.*`; a Forge 52 adapter must use Forge APIs rather than renaming the NeoForge artifact |
| Login listener | `gameProfile`, `handleHello`, `tick`, `handleCustomQueryPacket`, `disconnect` on `ServerLoginPacketListenerImpl` | 1.21 branch shadows `authenticatedProfile` and invokes `startClientVerification` / `verifyLoginAndFinishConnectionSetup`; mappings and state transitions must be checked against Forge 52 |
| Login packets | `FriendlyByteBuf` carried directly by `ClientboundCustomQueryPacket` / `ServerboundCustomQueryPacket`; `getIdentifier`, `getData`, `getTransactionId` | 1.21 uses `CustomQueryPayload`, `CustomQueryAnswerPayload`, packet `payload()`, and decoder mixins; Forge 52 decoding/registration must be proven in a two-sided login run |
| Resource ids | 1.20.1 `ResourceLocation` construction | 1.21 uses `ResourceLocation.fromNamespaceAndPath` |
| Client auth | mixin into `ClientHandshakePacketListenerImpl.handleCustomQuery`; authlib reflection around `YggdrasilMinecraftSessionService` | authlib fields/signatures and login payload APIs differ; the NeoForge branch also contains a direct Mojang join fallback which must not be copied without review |
| Profile/textures | authlib `GameProfile`, `Property`, pending profile replacement | Field names and connection-accept calls differ across mappings |
| Events/UI | Forge command, player login/logout and server tick events; 1.20.1 player-info/title packet accessors | Event packages, tick event shape, connection accessor and packet constructors differ on NeoForge 1.21.1 |
| Disk paths | `MinecraftServer.getWorldPath(LevelResource...)` plus Forge `FMLPaths` | Path discovery must stay in the platform adapter; migration plans operate on supplied `Path` values only |

## Compatibility matrix

| Target | Loader | Java | Status |
|---|---|---:|---|
| Minecraft 1.20.1 / Forge 47.4.8 | Forge | 17 | Current supported target; foundation hardened and built here |
| Minecraft 1.21.1 / Forge 52.1.14 | Forge | 21 | Planned modern Forge target. [Forge lists 52.1.14 for 1.21.1](https://files.minecraftforge.net/net/minecraftforge/forge/index_1.21.1.html), but TrueUUID support begins only after its exact payload/login APIs compile and a client/server login run passes |
| Minecraft 1.21.1 / NeoForge 21.1.213 | NeoForge | 21 | Existing independent `origin/1.21` release line; useful API evidence, not a Forge variant and not modified by this integration branch |
| Minecraft 1.12.2 / Forge 14.23.5.2860 | Forge | 8 | Deferred legacy target; no implementation in this task |

The legacy port needs an isolated Gradle/ForgeGradle build using JDK 8, old MCP
mappings, old login packet/mixin targets, and a small Java-8-compatible protocol
facade. It must not lower the shared modern module's language level or dependency
constraints. Cross-build protocol fixtures, rather than shared Minecraft code,
will establish compatibility.

## Module boundary

`shared/protocol` owns Java-only immutable messages, size/format validation,
endpoint policy contracts, auth-policy decisions, bounded/expiring state
utilities, migration operation/planning contracts, and unit tests. It imports no
Minecraft, Forge, NeoForge, authlib, Netty or loader classes.

`platform/forge-1.20.1` owns packet/buffer codecs, mixins, authlib profile
conversion, server-thread handoff, lifecycle events, config adapters, world path
discovery, commands and localized `Component.translatable` presentation. Its
login mixin must stay a thin hook delegating to focused authentication,
connection-state and migration services.

`platform/forge-modern` is deliberately absent until Forge 52.1.14 APIs are
confirmed in source/mappings and both a build and a real two-sided login run
succeed. A directory name is not a support claim.

`legacy/forge-1.12.2` contains documentation only until the isolated JDK 8 build
and protocol fixture strategy are approved.

Fabric requires Fabric Loader networking/lifecycle adapters and mixins. Spigot
and Paper are server-only plugin platforms and cannot satisfy the present
client-and-server login protocol without a separately designed client mod,
proxy/plugin channel and threat model. These are future independent ports, not
Gradle source-set switches.

## Release branches

All shared and Forge adapter work lands on `forge/integration`. Versioned release
branches are cut from validated integration points: `forge/1.20.1`, then
`forge/1.21.1` only after its build and login acceptance checks pass. Existing
`1.20` and `1.21` release branches are never rewritten in place; the NeoForge
line remains independent. Legacy work will use `forge/1.12.2` only after the
isolated build exists. Fixes flow through integration first and are backported
as reviewable commits, never by force-pushing release history.

## Acceptance gates for a new adapter

The adapter must compile with its declared JDK, pass shared protocol fixtures
and focused unit tests, reject untrusted/private endpoint resolution, keep all
network and disk work off the server thread, cancel login-owned work on timeout
or disconnect, and complete a real modded-client/modded-server login matrix
(Mojang success, allowed Yggdrasil success, denial, timeout, disconnect, and
migration rollback). Until all gates pass, the target remains planned.
