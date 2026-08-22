# Spigot/Paper 1.20.1 implementation handoff

Read this file together with
[`server-plugin-bridge.md`](../architecture/server-plugin-bridge.md), which is
the authoritative security contract. This handoff defines the next executable
steps; it does not claim plugin support.

Phase 0 has now produced an unsupported exact-version candidate. Its pinned
inputs, artifact hashes, executed runtime cases, and remaining gates are in
[`spigot-1.20.1-phase0-evidence.md`](spigot-1.20.1-phase0-evidence.md).
The maintainer's first Fabric/clientless test commands are in
[`spigot-1.20.1-first-test.md`](spigot-1.20.1-first-test.md).

## Fixed decisions

- Version 1.3.0 is the current development line. `release/targets.json` carries
  the machine-readable `release_ready=false` veto and no current target is
  release-approved.
- Version 1.2.1 remains permanently withheld; never tag, draft, publish, or
  reuse it.
- Implement Spigot 1.20.1 first, then Paper 1.20.1, then add exact server
  versions one at a time.
- Both `CLIENT_ASSISTED` and `VANILLA_HYBRID` modes are required.
- `AUTO` may negotiate the transport, but a client capability claim cannot
  weaken identity policy.
- The no-client path is Mojang-only initially. Preserve the current allowlisted
  Yggdrasil path in client-assisted mode.
- OfflineAuth runs only for the server-authoritative `OFFLINE_FALLBACK`
  outcome. `UNKNOWN` and failed premium proof deny.
- Fresh Spigot installations use `CONSENT_REQUIRED`: the first accepted
  premium or offline identity keeps the base name, and a later cross-kind
  collision is denied until an administrator grants one one-use, 60-second,
  repository-generation-bound approval. A premium approval cannot change the
  offline binding until native or client-assisted premium proof succeeds.
- The shared security invariant is already applied to the loader login paths:
  silence, timeout, malformed/deceptive responses, and failed premium proof
  always deny. Full collision repository, alias, command, identity API, and
  LuckPerms-context parity is still an explicit Forge/Fabric/NeoForge gate.

## Current implementation snapshot

The Phase 0 module and `shared/server-core` now exist in the dirty worktree.
The current candidate JAR is
`5b49c808c477ef0d05a7451494f1d47861e0152b9ac80fa9cc1b55148585a28e`.
It passed the exact Spigot 3871 descriptor check, the ProtocolLib 5.1.0
checksum/version check, shared/plugin tests, and the Fabric 1.20.1 cross-loader
wire check. Its manifest and `plugin.yml` report 1.3.0. See the evidence
document for the exact runtime cases and hashes.

This is still an unsupported candidate. The current 1.3.0 hash has build,
descriptor, exact-server boot, and clean typed-stop evidence, but no player or
real premium login result. The loader adapters do not yet provide the new server-side
collision commands or repository behavior. Do not infer support or parity from
the compile matrix.

## Phase 0: prove the 1.20.1 seams

Before creating a Gradle module, build a disposable research harness or test
branch and answer these with packet traces and exact dependency versions:

1. Can the Spigot adapter intercept `LOGIN_START` before offline UUID/player
   data selection?
2. Can it suspend that connection without blocking a Netty or server thread?
3. Can it send and receive a vanilla login custom query before authentication?
4. Can it initiate the vanilla Encryption Request, delegate the response to
   Minecraft's cryptographic code, and then resume login exactly once?
5. Can it replace the authenticated profile before player data opens?
6. Does the chosen ProtocolLib build expose all five operations on Spigot
   1.20.1? If not, identify the smallest exact NMS adapter instead of adding
   reflection fallbacks.
7. Can the existing Forge, Fabric, and NeoForge 1.20.1 clients decode the same
   query when the server is Spigot rather than their loader?

Record the Spigot build, Java 17 runtime, ProtocolLib build and checksum (if
used), packet order, source links, and limitations. Stop if any required seam
can only fail open.

## Phase 1: extract only real shared behavior

Reuse `shared/protocol` directly for the first focused spike. Extract
`shared/server-core` only after at least two adapters need the same concrete
login orchestration.

The eventual plain-Java boundary should be narrow:

```text
IdentityRegistry        durable PREMIUM_LOCKED/OFFLINE_ENROLLED state
PremiumNameClassifier  bounded EXISTS/DEFINITELY_ABSENT/UNAVAILABLE result
PremiumSessionVerifier connection-bound verified profile or typed failure
HybridLoginCoordinator state transitions and platform-neutral effects
OfflineAuthPort         require credential/enrollment; never validate premium
```

Keep these platform concerns in the Spigot adapter:

```text
LoginTransport          packets, connection ownership, timeout cancellation
VanillaCryptoBridge     exact Minecraft key exchange and digest primitives
ProfileApplier          UUID/name/properties before player-data selection
BukkitStatusPublisher   AccountStatus and addon/lifecycle callbacks
SpigotPathsAndConfig    plugin directory, configuration, commands, logging
```

Do not pass Bukkit `Player`, `PlayerProfile`, ProtocolLib packet containers,
Netty channels, NMS classes, components, or schedulers into shared code.

## Phase 2: implement the Spigot 1.20.1 module

Only after Phase 0 succeeds, introduce a working module such as:

```text
plugin/bukkit-common/
plugin/spigot/1.20.1/
```

The exact Gradle coordinate and manifest schema must be added to the target
validator in the same change. Do not force `spigot` and `paper` into assumptions
that currently enumerate only Forge, Fabric, and NeoForge. Publishing scripts
must map plugin loaders and `plugin.yml` explicitly rather than treating them
as mod metadata.

If ProtocolLib is required, declare it as a hard runtime dependency with an
exact tested compatibility range. Refuse startup when the required pre-login
capability is absent. A soft dependency followed by offline login would violate
the security contract.

Use `HybridIdentityPolicy` before selecting either proof transport. Persist the
classification before completing a first login, with atomic same-directory
writes, strict bounds, canonical names, UUIDs, and fail-closed corrupt-state
handling.

## Phase 3: integrate OfflineAuth safely

The current sibling OfflineAuth implementation has a Forge 1.20.1 adapter and
plain-Java services, but no Spigot/Paper support claim. Do not link the Forge
artifact into a Bukkit server.

Choose one temporary integration while a Bukkit OfflineAuth adapter is built:

- publish a small server-neutral TrueUUID status/service contract which a
  future OfflineAuth Bukkit adapter consumes; or
- expose a documented Bukkit event/service carrying the final
  server-authoritative `AccountStatus`.

Only `OFFLINE_FALLBACK` may start registration/password authentication.
`PREMIUM_VERIFIED` bypasses it, while `UNKNOWN` and any premium failure are
denied before an unauthenticated player can interact with the world.

## Phase 4: add Paper 1.20.1

Reuse Bukkit-common and the shared state machine. Add a separate Paper module
only for actual Paper API seams, beginning with a focused proof of
`AsyncPlayerPreLoginEvent#setPlayerProfile` and its ordering relative to player
data loading.

Run the full Spigot matrix again on Paper; compatibility assumptions are not
evidence. Then run Paper-specific profile, scheduler, proxy-forwarding, and
disconnect cases. Keep the Spigot artifact working and independently tested.

## Automated CI design

Add these jobs incrementally with the first working module:

1. Plain-Java policy/state-machine tests on JDK 17.
2. Spigot 1.20.1 compile and reproducible plugin-JAR inspection.
3. Exact ProtocolLib/NMS compatibility test and startup refusal test.
4. Dedicated-server bootstrap with isolated paths, bounded memory, timeout,
   retained logs, and exact server/dependency checksums.
5. Fake-authority integration tests for 200, 204, 404, 429, 5xx, timeout,
   malformed, oversized, redirected, and TLS-invalid responses. Production
   endpoint policy must not be relaxed to make the fixture reachable.
6. Scripted malicious-client packet tests for forged acknowledgements,
   unsupported queries, replay, wrong verify token, disconnect, and reordering.
7. Installed-JAR client matrix for vanilla, Forge, Fabric, and NeoForge 1.20.1.

Spigot server acquisition must be reproducible and license-compliant. If CI
uses BuildTools, pin its provenance/checksum and `--rev 1.20.1`; do not commit
or redistribute the resulting server JAR. Paper later uses an exact pinned
Paper build and retained checksum.

Automated fixtures do not replace a real premium account acceptance run. Never
store a long-lived Minecraft access token in repository files or ordinary CI
artifacts. The maintainer-run acceptance harness should record only token-free
markers, logs, artifact hashes, and the final server result.

## Required security matrix

At minimum, preserve evidence for these cases on each plugin target:

| Client | Claimed capability | Identity | Expected result |
|---|---|---|---|
| Vanilla premium | none | unknown but Mojang-existing | verified premium |
| Vanilla offline | none | premium-locked | deny before join |
| Modified client | hides TrueUUID | premium-locked | vanilla proof or deny |
| Modified client | forged TrueUUID answer | premium-locked | server 204/invalid denial |
| Forge/Fabric/NeoForge | valid TrueUUID | premium | client-assisted verified |
| Any | any | offline-enrolled | configured offline policy; separate auth addon when ownership is required |
| Vanilla | none | unknown, authoritative not-found | allow only in `ALLOW_VANILLA` |
| TrueUUID client | missing premium session | unknown, authoritative not-found | allow in `ALLOW_VANILLA` or `REQUIRE_TRUEUUID_CLIENT` |
| Any | any | unknown, lookup unavailable | deny |
| Any | any | offline name later becomes premium | no automatic takeover |

Repeat authority failure, timeout, disconnect, replay, corrupt registry, and
restart cases for each applicable row.

## Completion definition

Spigot 1.20.1 is supported only when both modes and the malicious-client matrix
pass with the production plugin JAR. Paper 1.20.1 is supported only after its
independent matrix passes. Only then:

1. add exact plugin targets to the machine-readable manifest;
2. update release automation and distribution loader mappings;
3. set per-target release approval from fresh evidence;
4. confirm the project version and exact artifact set;
5. remove the global publication veto only after every declared target and
   feature promised by that version is ready;
6. write a new changelog and create a new signed tag. Never reuse `v1.2.1`.
