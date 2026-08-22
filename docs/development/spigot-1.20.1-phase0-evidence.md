# Spigot 1.20.1 Phase 0 evidence

Initial runtime evidence: 2026-08-14
Latest build audit: 2026-08-22

Status: **unsupported candidate**. This evidence proves the exact early-login
seams, fail-closed candidate behavior, and the first real premium login through
both client-assisted Fabric and unmodified vanilla transport. It does not
satisfy the complete cross-loader, OfflineAuth, or malicious-client release
gates. Spigot therefore remains absent from `release/targets.json` and
unsupported in the target matrix.

## Pinned inputs

- ProtocolLib 5.1.0, upstream commit
  `26b0601f74a929194d613d58dfbc85512c219cae`, release JAR SHA-256
  `562c3ef79391e25f71b23359adb6becae7bcee36b0dfe2621b2c679013116769`.
- BuildTools build 200, JAR SHA-256
  `b61fa90158f594ee95bea1a27399eb64d439b4c8ae9345bd4476a02ce49b06ff`.
- Spigot 1.20.1 build 3871,
  `3871-Spigot-d2eba2c-3f9263b`, built from BuildData
  `221903b51701960ac778d8641b31cebcf411caa8`, Bukkit
  `69c7ce23f295a5bf1b1b7128bc1daece4ead768e`, CraftBukkit
  `3f9263ba3a726846a9466e12da95d73229af4ad9`, and Spigot
  `d2eba2c820b52b742eb542c6d2c4d76e3d743570`.
- Java 17 runtime: `/usr/lib/jvm/jdk-17.0.12-oracle-x64`.
- Gradle launcher: `/usr/lib/jvm/java-21-openjdk-amd64`.

The disposable BuildTools and runtime area was
`/tmp/trueuuid-phase0.HS1sME`. It is outside the repository and none of the
Spigot artifacts are committed or redistributed.

## Generated artifact hashes

- Current 1.3.0 unsupported Spigot candidate:
  `5b49c808c477ef0d05a7451494f1d47861e0152b9ac80fa9cc1b55148585a28e`.
  Its manifest and `plugin.yml` both report 1.3.0, exact descriptor and
  ProtocolLib checks passed, and its support marker remains
  `UNSUPPORTED-CANDIDATE`. It booted on exact Spigot 3871 with ProtocolLib
  5.1.0, enabled the secure bridge, and completed a typed `stop` with exit 0,
  all dimensions saved, port released, and world lock released. The token-free
  log SHA-256 is
  `55094db04d30288d7a08076a3e18a78516a960a19c0e7a720fc2d2e93704aa9d`.
  No player login or real premium login was completed with this exact hash.
- Matching 1.3.0 Fabric 1.20.1 JAR:
  `b5b4f86c16daa6719120bb783fedade6e40c30e027377d46b7c17f935d61b31f`.
- Matching rebuilt 1.3.0 Forge 1.20.1 JAR:
  `0afd76264bd933e65412f3f3c4e300e196b1f7df02f31473617a08607463044a`.
- Matching rebuilt 1.3.0 NeoForge 1.20.1 JAR:
  `45f725d4a8d6e94363dbdb5f6f67f368580102d5e19fb18ac3a83379ad53cefb`.
- Current BuildTools output/bootstrap and inner Spigot runtime remain
  `30cadfa3a1fe12477115fdc40577bdc254c0ffda789c2fc078bc6eeb79dcbafe`
  and
  `ae62de07363e988d8ba2875cf0cc22ed08f72bd761a00a87b885eee77ee71004`.

The hashes below are retained with their original runtime evidence and must not
be treated as evidence for the newer 1.3.0 artifacts.

- BuildTools output/bootstrap `spigot-1.20.1.jar`:
  `fbc4ab7e46d43da4d09eec9dc56d713f187ac824bfb580872832c352cdd01186`.
- Inner Spigot runtime `spigot-1.20.1-R0.1-SNAPSHOT.jar`:
  `6f9f0e1c4da77efbe434927ce4363d992007a428c3f83b6baabb9c9185866e20`.
- BuildTools work `minecraft_server.1.20.1.jar`:
  `3af73a9dc5a102e38147946360dd27d4d70bae7055bf91cf2151cd5d121b79e0`.
- BuildTools work `server-1.20.1.jar`:
  `400d269a89c99b5db9acd1260cfefffd8d2657b7878dfd5609980d15a3c035bb`.
- BuildTools work `mapped.8788cf22.jar`:
  `0be68f7fa0fec127a26728898b1a7635b6d74fd34e3538f911428d025b4dab93`.
- Bundled authlib 4.0.43:
  `697043d19e0b84b04f011dbec1bec3d80c04d8c468e7e4c6b221c0183bc1c0ef`.
- Phase 0 probe JAR:
  `abc0b097ac40e89ec2456cbbc59a68e98f4d50696107de74f7da93bff0f87156`.
- Premium-login-tested production-shaped candidate JAR:
  `fd13cba6dde1804156c7deb438947be61e2c8fc711fa01fbdbdd3772c52f3948`.
- Superseded candidate with private feedback, configurable offline admission, and
  native client-visible login denials:
  `98dac52a5d1575021a680d4e9d766bc1879ab077113023c090034b468d4f8e35`.
  This exact JAR has passed focused tests and an exact-server denial-packet
  probe. Premium and graphical-client acceptance must still be repeated for
  this exact hash.
- Superseded consent-policy and cross-loader no-downgrade candidate JAR:
  `c67c7a1b945a129f4703829aeb5bf44c55a23cca8cb5e345ef8ad2812ecf01ae`.
  Matching rebuilt Fabric 1.20.1 JAR:
  `c9ebed13995223ac20ebfdeee85d266b2a1f1993e42fb84e9cbf5e91c9f2b128`.
  Rebuilt Forge 1.20.1 JAR:
  `6e3264da3bf054d4c6bae0256c0dd16801eedb71911610c9c0e887e24571d851`;
  rebuilt NeoForge 1.20.1 JAR:
  `a99ec2924b049863ab50bf1048205626e3918b1f89a02abf18eb97f460d2802a`.
  Their packaged English/Chinese feedback now uses the same offline and
  collision wording, but these two hashes have build/resource-inspection
  evidence only and no fresh installed-server login evidence.
  BuildTools output/bootstrap:
  `30cadfa3a1fe12477115fdc40577bdc254c0ffda789c2fc078bc6eeb79dcbafe`;
  inner Spigot runtime:
  `ae62de07363e988d8ba2875cf0cc22ed08f72bd761a00a87b885eee77ee71004`.
  Its final consent runtime log SHA-256 is
  `8b446ea39b392f05e75ad7de5715d8b6e51d8052aee57bbdeb835ad2f9cac293`.
  No real premium login has been completed with this exact hash.
- Matching rebuilt Fabric 1.20.1 client JAR:
  `a017f102067224550e1284eaa47ef7fe42e9a7b764cc199290cd9abfe77cd503`.
- Token-free real-login server log:
  `54661583ec5c8aa42a95626cf9746854c8d7f1bdf656aef7db8edc953da969f5`.
  Spigot later rotated that exact content into
  `2026-08-14-1.log.gz` (compressed-file SHA-256
  `d9cc8ff9f380ddbfd1f742a46d15fa4e2220706e9096aaa8be62323ec2edc537`).
- Superseded post-feedback exact-server boot/clean-stop log:
  `91ee49f49c31049f69ed27dc8f2eb7d762fd3fd49780504e1170cdd1e91db7be`.
- Superseded post-feedback real-test log:
  `b9c8b0a24ade509080ab1fb71f488ccea794071f3400b292125cec6b57769f23`.
- Superseded offline-policy exact-server log:
  `da3a7f96627d316d538e36c58d38fb582a95d826bdd2a8f1fa3371737e5f80d5`.
- Superseded candidate offline-login/clean-stop log:
  `ad7cb699da9c0f4476bf8abfa4f665e9bb85c5a4af7481b9decbac66b0b064ed`.
- Superseded candidate Ctrl+C-supervised clean-stop log:
  `2026-08-14-1.log.gz` (compressed SHA-256
  `5e4ee25c538abbc00c36bf07cd83a2d486ea3ed86cafc14f31ef0a06a1ddea22`,
  decompressed-content SHA-256
  `d5c890e76fb3345be5461e39699cdb3da2f89c3321f43c0702ae5beb6732c372`).
- Native `AUTO DENY` disconnect log: compressed SHA-256
  `50035f54544223c089a1231a02ebd547a27f64a47155c944937183b9d42bb39b`,
  decompressed-content SHA-256
  `d71287b14da21bea679fac79fd99d3800df8bd195b9f2eeab786913258c06676`.
- Native `AUTO REQUIRE_TRUEUUID_CLIENT` disconnect/clean-stop log:
  `f3727c2addf2d69e085213623d92a5bd74500c2632c87ad1e3f6e446a65ecff6`.
- Superseded-JAR native `AUTO DENY` disconnect/clean-stop log:
  `9328963473104b9cf332aa53df9d74fac4c9773d21ef4c06f2d5082ca0e0f3a8`.

## Proven seams and runtime cases

The disposable Phase 0 probe and then the installed candidate JAR were run on
the exact Spigot runtime with ProtocolLib 5.1.0:

- ProtocolLib cancelled `LOGIN_START` on a Netty login thread while the native
  listener was still in `HELLO` and its profile was null. The probe remained
  non-blocking and no offline UUID or pre-login event had been committed.
- A bounded `trueuuid:auth` login query was sent and answered before the native
  login handler resumed. A fake-authority probe installed a fixed verified
  UUID and reached both pre-login events before player-data use. This is seam
  evidence only and is not authentication acceptance evidence.
- A null query response in `AUTO` selected the native path. The listener moved
  to `KEY`, sent its native Encryption Request, and accepted a correctly
  encrypted RSA/AES verify-token response unchanged. Spigot/authlib performed
  the session lookup and rejected the intentionally unjoined `PhaseVanilla`
  account. The client received the encrypted native disconnect packet.
- The production candidate repeated the null-response native path and authlib
  denial. A syntactically valid client-assisted `joined=true` answer was
  checked server-side and denied with `PREMIUM_PROOF_FAILED` after the session
  authority supplied no joined profile; it did not become offline fallback.
- A malformed non-null answer was denied with `PREMIUM_PROOF_FAILED`; `AUTO`
  did not downgrade it to native or offline authentication.
- A deliberately corrupt `hybrid-identities.json` made the candidate refuse
  startup, disable itself, and request whole-server shutdown. The exact server
  reached its startup completion boundary and immediately stopped; it did not
  remain available in unauthenticated offline mode.
- Repeating a disconnected name produced a fresh transaction and native proof,
  demonstrating connection cleanup rather than a permanently stuck name
  reservation. Coordinator unit tests cover the remaining late-callback,
  replay, timeout, cancellation, disconnect, and terminal-race behavior.
- The exact adapter now calls authlib 4.0.43's
  `GameProfileRepository.findProfilesByNames` through descriptor-checked
  MethodHandles on a bounded login-owned worker. Only the exact
  `ProfileNotFoundException` result can classify a name as absent. A returned
  profile selects premium proof, while timeout, rate limiting, malformed data,
  callback mismatch, cancellation, or any other failure denies.
- The candidate and Fabric 1.20.1 client now share the same plain-Java
  one-byte final-status codec. After the Fabric client registers
  `trueuuid:account_status` in play state, Spigot sends only the final
  server-owned premium/offline outcome; an unknown value cannot become Premium.
- At 19:05:50, the installed candidate accepted a real Fabric 1.20.1 premium
  client as `CLIENT_ASSISTED`, publishing `PREMIUM_VERIFIED` for `FixGOD` with
  canonical UUID `d64da409-52f6-4ce8-a082-73b9a5d303bd`, followed by a normal
  player join.
- At 19:06:30, the same server and installed candidate accepted an unmodified
  premium 1.20.1 client as `VANILLA_HYBRID`, publishing `PREMIUM_VERIFIED` for
  the same canonical name and UUID, followed by a normal player join. The
  server then stopped cleanly at 19:07:04.

These are real premium-login and real Fabric-client results for the exact
`fd13...` JAR, not for the newer post-feedback JAR. No Forge or NeoForge client
has yet been used against the candidate. No access token is present in the
retained server log.

The superseded `9c577b...` JAR subsequently loaded on exact Spigot build 3871,
enabled the exact login bridge, reached `Done`, and stopped cleanly. At
19:25:29 it also completed a real unmodified premium login through
`VANILLA_HYBRID`, publishing the same canonical FixGOD UUID. ProtocolLib did
not print an update notice because the disposable runner wrote
`global.auto updater.notify: false`; ProtocolLib itself remained pinned to
5.1.0 with automatic download disabled.

The current candidate adds three explicit offline policies without weakening
premium names: `ALLOW_VANILLA`, `REQUIRE_TRUEUUID_CLIENT`, and `DENY`.
`ALLOW_VANILLA` deliberately has classic offline-mode name semantics after the
authoritative absence result. `REQUIRE_TRUEUUID_CLIENT` requires the existing
bounded missing-session TrueUUID answer, but is a capability gate rather than
proof of offline-name ownership. A separate authentication addon is still
required when an operator wants passwords or another ownership factor.

The exact `1ac842...` JAR booted with the default `ALLOW_VANILLA` mode.
A raw unmodified protocol-763 client named `FinalOff901` received Login Success,
and the server published `OFFLINE_FALLBACK` with transport `OFFLINE_VANILLA`
and the stable offline UUID `d833f0d3-f217-3f01-b1cc-6c19922f9c71` before the
normal join. A second raw unmodified client named `Notch`, which had no prior
local record, was classified as `PREMIUM_EXISTS`, received Spigot's native
Encryption Request, and never received offline fallback. These are direct
packet/server-runtime probes, not evidence from a graphical vanilla client.

An earlier build with the same offline-routing implementation also exercised
`REQUIRE_TRUEUUID_CLIENT`: a vanilla null response for the absent name
`RawNoMod567` was denied, while the exact bounded missing-session response for
`RawMod568` reached Login Success and published `OFFLINE_FALLBACK` with
transport `OFFLINE_TRUEUUID_CLIENT`. The final current JAR still needs the real
Fabric offline-client run.

A later `REQUIRE_TRUEUUID_CLIENT` run classified the requested name `1233` as
`PREMIUM_EXISTS`, so it correctly selected premium proof rather than either
offline policy. The official Mojang profile endpoint independently returned
HTTP 200 with canonical name `1233` and UUID
`d2a0ad16-4882-4c47-b79e-1f241c6c3f28`; it is not a suitable offline test
name. That run was interrupted
with SIGINT; its JVM then remained in Spigot's native chunk-distance/light-engine
shutdown loop and retained the valid world lock. A captured thread dump showed
no TrueUUID authentication worker causing the hang. The runner now detects the
live lock/port and refuses a duplicate server before staging or startup; it
never deletes a live `session.lock`.

The current `9f75e1...` candidate then ran in isolated storage on port 25566.
The official profile endpoint returned not-found for `TUOff8472Qz`; a raw
unmodified protocol-763 client received Login Success, and the server published
`OFFLINE_FALLBACK`/`OFFLINE_VANILLA` with stable UUID
`1219f93e-0c5e-3716-8d36-9e2f3dba87ec` before the normal join. A console
`stop` saved every dimension and exited in four seconds. A separate boot proved
that the new runner converts Ctrl+C into the same console stop path and also
saved every dimension in four seconds. These remain protocol/runtime probes,
not graphical-client acceptance evidence.

The `171c4f...` candidate replaced ProtocolLib's temporary-player
`disconnect(String)` at the held `LOGIN_START` seam because ProtocolLib 5.1.0
only closed that pre-login channel and did not serialize the supplied reason.
The exact adapter now invokes Spigot 3871's descriptor-checked native
`LoginListener.disconnect(String)` on the connection event loop. A raw
protocol-763 client against `AUTO DENY` decoded Login Disconnect packet `0`
with JSON text `Offline accounts are disabled on this server. Sign in with a
premium Minecraft account.` A second raw client answered the
`trueuuid:auth` query with the vanilla null response under
`AUTO REQUIRE_TRUEUUID_CLIENT` and decoded packet `0` with JSON text explaining
that the matching TrueUUID client mod is required. Both logs recorded the same
message at native disconnect and a typed, token-free operator audit; both
servers then stopped normally with every dimension saved. These prove actual
wire delivery, not graphical rendering or premium-login acceptance.

The final `98dac5...` candidate additionally routes the capacity-limit and
pre-attempt internal-error branches through the same native seam, leaving a
channel close only as the last fail-closed fallback when the exact listener is
unavailable. It repeated the `AUTO DENY` packet-0 decode and clean shutdown on
the exact server; the client-visible JSON text was unchanged.

The immediately preceding `a0c3a3...` candidate introduced the consent-based
fresh default.
On exact Spigot 3871, a raw unmodified protocol-763 client named
`TUFinal8472` received Login Success and the server published
`OFFLINE_FALLBACK`/`OFFLINE_VANILLA` with server-derived UUID
`671d274d-3c68-3013-a4fa-ae21d1a29af8`. A first offline `FixGOD` login was
accepted with UUID `76bcf6ba-e081-3976-aa3a-38db4da0a066`. A subsequent
connection carrying the matching Mojang UUID hint was denied before encryption
with the exact protected command
`/trueuuid identity collision allow FixGOD premium`; neither stored identity
changed. After console approval, the reconnect received the native Encryption
Request. Disconnecting without a valid Encryption Response left the stored
offline identity unaliased and did not create a premium binding. This is a
forged/incomplete-proof safety probe, not a real premium login.

The same exact runtime was stopped once by a single Ctrl+C and once by typed
`stop`. In both cases the wrapper stayed attached through all dimension saves,
printed `Server exited cleanly`, returned status 0, and released TCP port 25579
and the world lock. Candidate boot, these raw-protocol probes, and server
shutdown are not graphical-client acceptance evidence.

The current `c67c7a...` candidate adds literal permission-provider behavior:
`AUTO` uses LuckPerms contexts only when present, `PLATFORM` uses Bukkit
permissions only, and `LUCKPERMS` requires the backend plugin. On exact Spigot,
it repeated the protected `FixGOD` collision denial. A live reload to
`LUCKPERMS` without LuckPerms failed validation while the previous `AUTO`
snapshot remained active; restoring `AUTO` reloaded successfully. Typed
`stop` saved all dimensions, returned status 0, and released the port and lock.

## Candidate build contract

`scripts/ci/build-spigot-candidate.sh` reconstructs the exact non-redistributed
runtime with BuildTools, checks all repository revisions and pinned hashes,
runs the shared and candidate tests, runs the descriptor compatibility test
against the inner runtime JAR, and records artifact hashes under ignored build
storage. The workflow deliberately calls this an unsupported candidate.

The final focused validation used JDK 21 to launch Gradle while retaining the
Java 17 target toolchains:

```text
:shared:protocol:test :platform:forge-1.20.1:test
:plugin:bukkit-common:test :plugin:spigot:1.20.1:test
:platform:fabric-1.20.1:remapJar
:plugin:spigot:1.20.1:crossLoaderCompatibilityTest
:plugin:spigot:1.20.1:exactRuntimeCompatibilityTest
:plugin:spigot:1.20.1:jar
```

It completed successfully. A separate representative-era matrix also passed
Fabric 1.21.11, Forge 1.20.2 and 1.21.10, and NeoForge 1.20.2 and 1.21.10 after
the no-downgrade changes. All three development
validators, shell syntax checks, and `git diff --check` passed. The normal
release validator still refused publication with exit 65 because
`release_ready=false`.

## Remaining acceptance gates

- Repeat real Fabric and vanilla premium login with the current offline-policy
  JAR. Earlier production-shaped candidates passed both paths, but that is not
  acceptance evidence for the current hash.
- Complete real graphical vanilla-offline and Fabric-offline joins under the
  selected policies. Raw protocol probes are not a substitute.
- Complete Forge and NeoForge 1.20.1 client-assisted logins with the installed
  candidate JAR.
- Port `shared/server-core` collision storage, approvals, aliases, commands,
  identity API, feedback selection, and LuckPerms contexts into the Forge,
  Fabric, and NeoForge server adapters, then run the same installed-artifact
  contract/runtime matrix. Current no-downgrade proof behavior and client
  assets are shared, but the server-side feature set is not yet equivalent.
- Run the complete malicious-client, disconnect, packet-order, response-size,
  authority-failure, restart, and name-collision matrix.
- Add the separate Bukkit OfflineAuth provider and validate only its final,
  server-authoritative `OFFLINE_FALLBACK` result.
- Add an operator/deployment health gate that refuses service when the hard
  ProtocolLib dependency or the TrueUUID JAR itself is absent; a plugin cannot
  stop a server when its own JAR is missing or Spigot declines to load it.

Until all gates pass, this candidate must not be placed in the release target
manifest or described as supported.
