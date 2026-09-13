# Spigot and Paper 1.20.1 evidence

Latest acceptance: 2026-09-13.

Status: `spigot-1.20.1` and `paper-1.20.1` are **supported** in
`ci/server-plugin-targets.json` and approved for 1.3.0. Both passed the gate
agreed for the first plugin release. The broader gates at the end of this
document remain open, and nothing here claims them.

After this acceptance, a presentation-only change replaced the message styles
with the shared bold gradient wordmark from `BrandText`. The login engine did
not change. The maintainer decided on 2026-09-13 that the automated checks
below, repeated on the final JARs, plus Verify and Full Self-Test in the Release
workflow, cover that change. The hashes in the artifact table are the JARs that
ran the logins, not the final release JARs.

The security contract is
[`server-plugin-bridge.md`](../architecture/server-plugin-bridge.md). The test
steps are in [`server-plugin-1.20.1-testing.md`](server-plugin-1.20.1-testing.md).

## Agreed gate

On 2026-09-13 the maintainer set the gate for the first Spigot and Paper
release: local installed-JAR logins on each server with an unmodified vanilla
premium client and a Fabric 1.20.1 TrueUUID client, premium and offline, plus
the automated CI operations. No other runtime suites were required. The server
configuration for the logins was `AUTO`, `ALLOW_VANILLA`, `CONSENT_REQUIRED`.

## Inputs

Runtime and tools:

- Server Java: Oracle JDK 17.0.12. Gradle launcher: OpenJDK 21.
- Spigot: BuildTools build 200 (SHA-256
  `b61fa90158f594ee95bea1a27399eb64d439b4c8ae9345bd4476a02ce49b06ff`) with
  `--rev 1.20.1`, producing implementation `3871-Spigot-d2eba2c-3f9263b` from
  BuildData `221903b51701960ac778d8641b31cebcf411caa8`, Bukkit
  `69c7ce23f295a5bf1b1b7128bc1daece4ead768e`, CraftBukkit
  `3f9263ba3a726846a9466e12da95d73229af4ad9`, and Spigot
  `d2eba2c820b52b742eb542c6d2c4d76e3d743570`. Bootstrap JAR
  `556e50368e9da8a209615be4db7924e60e67e9c410b131851aef8db834d34a64`, mapped
  runtime JAR `9b671d28dddbe7061159aa37464a98317fe8bd845871f242089e44d6b1e529d2`.
  BuildTools output is not byte-reproducible, so these differ from earlier
  builds of the same revisions.
- Paper: build 196 from the PaperMC downloads service, paperclip SHA-256
  `234a9b32098100c6fc116664d64e36ccdb58b5b649af0f80bcccb08b0255eaea`,
  implementation `git-Paper-196`, patched runtime JAR
  `cfd4617af161ec938a752a6a24eb411aec599848e60b865b295e0923ef3a056e`.
- ProtocolLib 5.4.0 on both test servers, SHA-256
  `ee2e7ab9b5386f2d103081c4d108e61b1035df2ca692b53d6e2409fb1f5caccf`.
  ProtocolLib 5.1.0, SHA-256
  `562c3ef79391e25f71b23359adb6becae7bcee36b0dfe2621b2c679013116769`, is the
  compile version and the tested minimum.

Clients:

- Fabric: `trueuuid-1.3.0-fabric-1.20.1.jar`
  `b5b4f86c16daa6719120bb783fedade6e40c30e027377d46b7c17f935d61b31f`, Fabric
  API `0.92.9+1.20.1`
  `4056979ccaa1012354dfff14f39fdb085aefb3f1e0224ff45c3af41e4ffed5fd`, Fabric
  Loader 0.19.3. The CI `build` operation rebuilt this Fabric JAR from source
  and produced the identical hash.
- Vanilla: unmodified Minecraft 1.20.1 through PortableMC.
- Accounts: premium `FixGOD` with canonical UUID
  `d64da409-52f6-4ce8-a082-73b9a5d303bd`. Offline name `TUOff471896`, for
  which the Mojang profile endpoint returned 404 on 2026-09-13.

## Plugin artifacts

The logins used the candidate JARs built from the same sources:

| Target | Tested candidate JAR | Supported JAR |
|---|---|---|
| `spigot-1.20.1` | `0bff2fd7b0deb76d05e266e9d18691c6cdb276ccdba646e41815e18818f630ef` | `fe24a47cccca42417c8fa3bdb793db542f4b339a17dd5c981c2f13b4588d9f3d` |
| `paper-1.20.1` | `bf79381d2f6bb0a3eafa42f895ea4d2ee8e516a8ce15e70382bd768eef897972` | `aa5d578d757e3fa2635c38f91d3b717d2c9eedcda4bb6b3af5fef06cffcc5953` |

Promotion to `supported` changes only `META-INF/MANIFEST.MF`, `plugin.yml`, and
`trueuuid-target.properties`, which carry the support status. Extracting both
JARs of each target and comparing them showed every class file and
`config.yml` byte-identical. Release JARs are rebuilt by CI, so their hashes
are recorded by the Release workflow.

## Automated checks

- Plugin unit tests: 47 tests across `plugin:bukkit-common`,
  `plugin:craftbukkit:1.20.1`, and `plugin:paper:1.20.1`, including the seam
  table completeness test, the resolver test for every access kind, startup
  gate tests, target metadata tests, message style tests, and the ProtocolLib
  shape test. That test loaded ProtocolLib 5.1.0 in isolation and resolved the
  `minimal-injector` shape, and loaded 5.4.0 and resolved `channel-injector`.
- `serverRuntimeContractTest`: every obfuscated `LoginSeams` entry, with its
  exact descriptor and static or instance access, is present in the mapped
  Spigot runtime JAR and in the patched Paper runtime JAR.
- CI `build` operation for both targets: shared protocol and server-core tests,
  plugin tests, the seam contract against that target's server, the plugin JAR,
  and `crossLoaderCompatibilityTest` against the rebuilt Fabric 1.20.1 JAR all
  passed.
- CI `verify` operation: both candidate JARs and both supported JARs passed the
  structural, manifest, `plugin.yml`, and target-metadata checks.
- CI `smoke` operation: each installed JAR booted with ProtocolLib 5.4.0 under
  `AUTO`, `DENY`, `CONSENT_REQUIRED`, printed the ready line, stopped after one
  TERM signal with the world saved, and released its port and world lock. Both
  `latest.log` files contained the banner as plain text and no ANSI escape
  codes. Paper read `proxies.velocity.enabled` from its configuration and
  passed the proxy gate.

## Real login matrix

Every login was followed by a normal join. Each client received the private
chat message shown, which was confirmed in the client log.

### Paper 1.20.1

| Time | Client | Server audit line | Client chat |
|---|---|---|---|
| 01:47:54 | Fabric premium | `login_complete outcome=PREMIUM_VERIFIED transport=CLIENT_ASSISTED player=FixGOD uuid=d64da409-52f6-4ce8-a082-73b9a5d303bd` | `TrueUUID » Premium account verified.` |
| 01:48:38 | Fabric offline | `login_complete outcome=OFFLINE_FALLBACK transport=OFFLINE_VANILLA player=TUOff471896 uuid=3b7f6898-bab9-301c-8d1f-5270c9bf706b` | `TrueUUID » Offline fallback accepted. This account was not premium-verified.` |
| 01:49:55 | Vanilla premium | `login_complete outcome=PREMIUM_VERIFIED transport=VANILLA_HYBRID player=FixGOD uuid=d64da409-52f6-4ce8-a082-73b9a5d303bd` | `TrueUUID » Premium account verified.` |

The ready line was `Secure login bridge enabled: mode=AUTO,
offline=ALLOW_VANILLA, admission=CONSENT_REQUIRED, platform=Paper,
server=git-Paper-196, minecraft=1.20.1, ProtocolLib=5.4.0`. The server stopped
cleanly at 01:50:41. The token-free log SHA-256 is
`4d81fb6b69d63f42b9b33874ec1d4d7a1564796a4d6e40acbfeef77909a30669`.

### Spigot 1.20.1

| Time | Client | Server audit line | Client chat |
|---|---|---|---|
| 01:51:48 | Fabric premium | `login_complete outcome=PREMIUM_VERIFIED transport=CLIENT_ASSISTED player=FixGOD uuid=d64da409-52f6-4ce8-a082-73b9a5d303bd` | `TrueUUID » Premium account verified.` |
| 01:52:11 | Fabric offline | `login_complete outcome=OFFLINE_FALLBACK transport=OFFLINE_VANILLA player=TUOff471896 uuid=3b7f6898-bab9-301c-8d1f-5270c9bf706b` | `TrueUUID » Offline fallback accepted. This account was not premium-verified.` |
| 01:52:33 | Vanilla premium | `login_complete outcome=PREMIUM_VERIFIED transport=VANILLA_HYBRID player=FixGOD uuid=d64da409-52f6-4ce8-a082-73b9a5d303bd` | `TrueUUID » Premium account verified.` |

The ready line was `Secure login bridge enabled: mode=AUTO,
offline=ALLOW_VANILLA, admission=CONSENT_REQUIRED, platform=Spigot,
server=3871-Spigot-d2eba2c-3f9263b, minecraft=1.20.1, ProtocolLib=5.4.0`. The
server saved all dimensions and stopped cleanly at 01:53:13. The token-free log
SHA-256 is `de6633804274a7c8dfc5e3f5068b8a083a5555cd31dd2691daf0faa6d6d27c29`.

Neither log contains access-token or refresh-token material.

## Not tested

The following cases were not run against the current plugins:

- Forge and NeoForge 1.20.1 client-assisted logins.
- The `DENY` and `REQUIRE_TRUEUUID_CLIENT` offline modes with real clients. The
  smoke boots under `DENY` but connects no player.
- `SAFE_PARALLEL`, `PREMIUM_RESERVED`, and the collision approval flow with
  real clients.
- Malicious clients: hidden or forged TrueUUID answers, replay, a wrong verify
  token, and disconnects during each asynchronous phase.
- Authority failures such as HTTP 204, 429, 5xx, timeout, and malformed or
  oversized responses.
- Restart persistence, a corrupt identity repository, and migration rollback.
- Server builds other than Spigot 3871 and Paper 196, and ProtocolLib 5.1, 5.2,
  or 5.3 on a running server. The 5.1.0 member shape was checked only by the
  isolated class-loading test.
- LuckPerms contexts, the OfflineAuth provider, and a deployment health gate.
- The styled console banner in an interactive terminal. CI and these local
  runs captured console output without a terminal, where no colour is emitted.

## Earlier exact-build findings

Before the shared engine existed, an exact Spigot 3871 bridge with ProtocolLib
5.1.0 was probed between 2026-08-14 and 2026-08-22. Those runs showed that
ProtocolLib can hold `LOGIN_START` on the Netty thread before the native
listener commits a profile, that the `trueuuid:auth` query and the native
Encryption Request both complete before resumption, that a null query answer
selects native proof while a malformed or deceptive answer denies, that a
corrupt identity store refuses startup and stops the server, and that native
login denials reach raw protocol clients as readable disconnect packets. They
also included the first real Fabric and vanilla premium logins. Those results
apply to the superseded exact bridge, not to the current engine. The full
inventory of hashes and logs is preserved in Git history:

```bash
git show 63b758f:docs/development/spigot-1.20.1-phase0-evidence.md
```
