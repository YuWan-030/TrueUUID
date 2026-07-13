# TrueUUID Forge architecture roadmap

Status: verified against `origin/1.20` (`99cc1f5`) and `origin/1.21`
(`abb9454`) on 2026-07-12. The latter is a NeoForge port, not a Forge build;
its detached `./gradlew build` passed under JDK 21 during this review.

## Protocol today

TrueUUID is a bilateral login-phase protocol; the matching mod is required on
both client and server. On an offline-mode server, the 1.20.1 adapter marks a
login as eligible after `ServerLoginPacketListenerImpl.handleHello`, then waits
for Forge's own `NEGOTIATING` handshake to reach `READY_TO_ACCEPT` before it
creates a nonce and sends a `ClientboundCustomQueryPacket` on `trueuuid:auth`.
This ordering prevents a TrueUUID reply from being interpreted as an FML
indexed handshake reply. The client intercepts
`ClientHandshakePacketListenerImpl.handleCustomQuery`, calls authlib
`joinServer` locally (the access token never crosses the connection), and
returns `ok`, an optional Yggdrasil `hasJoined` endpoint, migration consent, and
the missing-token flag in `ServerboundCustomQueryPacket`. The server pauses
login from `tick`, verifies `hasJoined` asynchronously, replaces the pending
`GameProfile` with the verified UUID/name/textures, records the result, and
allows the final Forge acceptance path to resume. If verified offline-UUID data
exists, a second login query asks for migration consent before acceptance.

The Java-only wire contract is version 1: a fixed header (`0x54555549`), a
protocol-version byte, a message-kind byte, and bounded UTF-8 query/answer
fields. Golden query and answer fixtures live with the Java-only codec. Native
adapters must reject malformed, trailing, or unsupported-version payloads;
matching client and server artifacts are required for this protocol revision.
Minecraft packet objects, buffers, listeners, mixins, text components, server
scheduling, configuration and lifecycle events are not part of that contract.

## Version-specific touchpoints

| Area | Forge 1.20.1 adapter | NeoForge 1.21.1 evidence / modern Forge port concern |
|---|---|---|
| Loader/build | ForgeGradle 6, `net.minecraftforge:forge:1.20.1-47.4.10`, `mods.toml`, Java 17 | Existing other branch uses ModDevGradle, NeoForge 21.1.213, `neoforge.mods.toml`, Java 21; it cannot be reused as a Forge build |
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
| Minecraft 1.20.1 / Forge 47.4.10 | Forge | 17 | Current supported target; foundation hardened and built here |
| Minecraft 1.21.1 / Forge 52.1.14 | Forge | 21 | Planned modern Forge target. [Forge lists 52.1.14 for 1.21.1](https://files.minecraftforge.net/net/minecraftforge/forge/index_1.21.1.html), but TrueUUID support begins only after its exact payload/login APIs compile and a client/server login run passes |
| Minecraft 1.21.1 / NeoForge 21.1.213 | NeoForge | 21 | Existing independent `origin/1.21` release line; useful API evidence, not a Forge variant and not modified by this integration branch |
| Minecraft 1.12.2 / Forge 14.23.5.2860 | Forge | 8 | Deferred legacy target; no implementation in this task |

The legacy port needs an isolated Gradle/ForgeGradle build using JDK 8, old MCP
mappings, old login packet/mixin targets, and a small Java-8-compatible protocol
facade. It must not lower the shared modern module's language level or dependency
constraints. Cross-build protocol fixtures, rather than shared Minecraft code,
will establish compatibility.

## Module boundary

`shared/protocol` owns Java-only immutable messages, versioned binary codecs
and golden fixtures, login-state transitions, verified-profile values, session
verifier/registry/grace/migration interfaces, endpoint policy, a bounded
request coordinator, DNS-pinned/no-redirect/response-bounded HTTPS transport,
auth-policy decisions, migration operation/planning contracts, and unit tests.
It imports no Minecraft, Forge, NeoForge, authlib, Netty or loader classes.

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

## Integration, historical branches, and releases

All shared and platform work lands on `main`. New work uses short-lived
`feature/<scope>` branches; loader/version names describe modules, not permanent
development branches. Existing `1.20` and `1.21` lines are preserved as
historical references and are never rewritten in place. The latter is a
NeoForge line, not a Forge variant.

Release tags identify the artifact target, for example
`forge-1.20.1-v1.1.0`. Create a `maintenance/<loader>-<minecraft-version>`
branch only when a released older target needs a backport. Fixes land on `main`
first and are then cherry-picked with `-x`; release history is never
force-pushed. See `target-matrix.md` and `../development/adding-adapter.md` for
the repository-wide rules.

## Acceptance gates for a new adapter

The adapter must compile with its declared JDK, pass shared protocol fixtures
and focused unit tests, reject untrusted/private endpoint resolution, keep all
network and disk work off the server thread, cancel login-owned work on timeout
or disconnect, and complete a real modded-client/modded-server login matrix
(Mojang success, allowed Yggdrasil success, denial, timeout, disconnect, and
migration rollback). Until all gates pass, the target remains planned.
