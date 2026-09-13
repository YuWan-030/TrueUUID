# Spigot and Paper dual-mode authentication architecture

This is the security and module contract for the TrueUUID server plugins.
Spigot 1.20.1 and Paper 1.20.1 are implemented and are `supported` targets in
`ci/server-plugin-targets.json`. Whether a version publishes them is a separate
per-version `release` approval, described in
[`release-automation.md`](../development/release-automation.md). Version 1.2.1
remains permanently withheld and must never be reused.

Plugin targets never enter `release/targets.json`. They have their own
inventory, so a plugin approval can never block or widen a mod release. Test
steps are in
[`server-plugin-1.20.1-testing.md`](../development/server-plugin-1.20.1-testing.md)
and recorded results in
[`server-plugin-1.20.1-evidence.md`](../development/server-plugin-1.20.1-evidence.md).

## Product modes

The server plugin supports both modes. `AUTO` may select between them per
connection, but it must never weaken the authentication decision.

### Client-assisted mode

This retains the TrueUUID mod design:

1. The server sends a bounded, versioned login query.
2. A matching Forge, Fabric, or NeoForge client mod calls its local session
   service's `joinServer` method. The access token stays on the client.
3. The server independently calls the configured `hasJoined` endpoint.
4. Only the server's verified response supplies the UUID, name, and signed
   profile properties.

The wire format is loader-neutral. Each client still installs the TrueUUID
artifact matching its loader and exact Minecraft version. The Spigot or Paper
server installs only its plugin artifact.

### Vanilla-hybrid mode

This mode requires no TrueUUID client mod and works with an unmodified premium
client or a client running any protocol-compatible loader. The server runs with
`online-mode=false`, but the plugin owns the authentication decision before the
offline UUID or player data is committed:

1. Intercept `LOGIN_START` before normal offline login can complete.
2. Read the persistent identity classification for the normalized name.
3. For a stored premium identity and premium intent, send the vanilla
   Encryption Request and require the normal Mojang session handshake.
4. Repeat authoritative Mojang name classification for every offline route.
   Timeout, rate limiting, malformed data, DNS or TLS failure, or an authority
   error denies the login.
5. Apply the configured admission mode to the authoritative result and stored
   bindings. `PREMIUM_RESERVED` requires premium proof for a Mojang-existing
   name. `CONSENT_REQUIRED` may create a first local offline binding, but a
   later premium and offline collision denies pending explicit resolution.
6. After a premium client sends the vanilla Encryption Response, use the
   server's own cryptographic implementation and verify `hasJoined`. Only a
   valid response permits `PREMIUM_VERIFIED` and the premium profile.

After an exact authoritative not-found result, server configuration chooses
one explicit offline policy:

- `DENY`: premium identities only.
- `REQUIRE_TRUEUUID_CLIENT`: require the bounded TrueUUID response that
  explicitly reports a missing client session. This is a capability gate, not
  proof of ownership of the offline name. A malicious compatible client can
  imitate it.
- `ALLOW_VANILLA`: accept the exact vanilla offline UUID and name without a
  client mod. This deliberately has ordinary offline-mode impersonation
  semantics.

`ALLOW_VANILLA` is the compatibility default. No offline policy runs after a
premium proof has failed. A positive name lookup classifies the identity but is
not proof that this connection owns it. The selected admission mode determines
whether an explicitly offline first claimant may receive only an offline UUID
and status. A server that needs ownership protection for offline names must add
a separate OfflineAuth credential provider rather than treating client
capability as a credential.

### Admission and name-collision modes

Fresh plugin installations use `CONSENT_REQUIRED`:

- The first final, server-accepted identity keeps the requested base name.
- A later premium and offline collision denies before player data opens.
- The denial says that nothing changed and gives the exact protected
  administrator command for a one-use, 60-second approval.
- Approval is bound to the normalized name, the collision direction, and the
  repository generation. It never supplies premium proof or an offline
  credential.
- A colliding offline identity uses a server-derived UUID and a stable alias.
- When premium arrives after an unaliased offline binding, the offline alias is
  written only after premium session verification succeeds. A forged Login
  Start UUID hint can therefore neither rename the existing identity nor gain
  premium status.

This default is fair to the first local claimant, but it permits local
display-name squatting. An offline identity may use a Mojang-existing base name
until a collision is explicitly resolved. It never receives the Mojang UUID,
signed properties, or `PREMIUM_VERIFIED`. Servers that treat the canonical name
itself as reserved security state should use `PREMIUM_RESERVED`.

`SAFE_PARALLEL` is an explicit convenience mode. Verified premium keeps the
canonical profile, and colliding offline identities are automatically assigned
server-owned aliases. `FIRST_CLAIM` permanently denies the other identity kind
and remains explicitly unsafe. Existing configurations retain their selected
mode. Only a newly generated configuration receives `CONSENT_REQUIRED`.

`online-mode=true` is not the mixed-mode solution. It requires the vanilla
premium handshake for every connection, and an offline client cannot continue.
If a server accepts only premium accounts, native `online-mode=true` is the
simpler and preferred configuration.

### Automatic selection

An `AUTO` transport offers the TrueUUID login query first. A supported client
mod uses client-assisted mode. A vanilla client reports the query as
unsupported and continues through vanilla-hybrid mode.

Client capability is not an authentication fact. A malicious client may hide
the mod, claim to support it, forge an acknowledgement, omit a response, or
change loaders. Those actions may select a transport or cause denial, but they
must never downgrade a stored premium binding or a failed premium proof. In
`CONSENT_REQUIRED`, an explicit offline route with no local claimant may receive
only a server-derived offline identity. It is never treated as proof of the
Mojang account. A client that claims premium intent must complete the equally
authoritative vanilla or client-assisted proof.

## Non-negotiable security invariants

Treat the client as fully compromised. It controls its packets, timing, claimed
loader, query responses, endpoint strings, and disconnect behavior. These rules
apply in every adapter and mode:

- Name and profile lookup classifies a route. It never proves account
  ownership.
- `joined=true`, a client-reported UUID, skin, endpoint, or loader is never an
  authority.
- A premium identity is accepted only after the server verifies a fresh,
  connection-bound `joinServer` assertion through `hasJoined`.
- Any premium proof failure denies. HTTP 204, timeout, 429, 5xx, malformed JSON,
  name or UUID mismatch, TLS failure, client abort, and cancellation never
  become offline fallback.
- A stored premium binding remains premium-locked across outages, restarts, IP
  changes, client loader changes, and attempts to hide the TrueUUID mod.
- An authority failure is not the same as an authoritative name-not-found
  response. Unknown identities fail closed when classification is unavailable.
- An OfflineAuth-protected identity stays bound to its credential. A name-only
  or client-gated offline identity retains its stable vanilla offline UUID, but
  Mojang classification is repeated on login. If the name later becomes
  premium, login denies until an explicit recovery or administrator decision.
  Under `CONSENT_REQUIRED`, neither side takes over or changes name
  automatically.
- Nonces and vanilla verify tokens are unpredictable, single-use, scoped to one
  connection, bounded by a short deadline, and removed on every terminal path.
  Replays and responses for another connection fail closed.
- The server's own digest, key exchange, and session primitives are used. The
  plugin never recreates the signed hexadecimal server hash, the RSA exchange,
  or the token comparison.
- Network and disk work is bounded, asynchronous, and cancelled on timeout or
  disconnect. Native packet and profile changes return to the connection event
  loop.
- Custom Yggdrasil authorities remain allowlisted server configuration. The
  no-client path is Mojang-only. An authority chosen by an unmodded or untrusted
  client is never accepted.

`HybridIdentityPolicy` in `shared/protocol` encodes the minimum routing and
no-downgrade rules. Platform code may add denial reasons but must not override
those decisions.

## Persistent identity states

The case-normalized registry has these states:

- `PREMIUM_LOCKED`: the name and UUID were session-verified. Premium proof is
  mandatory for that stored UUID and status unless an explicit administrator
  recovery changes it. A separate approved offline binding may coexist only
  under a unique effective alias.
- `OFFLINE_ENROLLED`: a local offline identity with an explicit authority:
  OfflineAuth credential, TrueUUID-client capability gate, or deliberately
  impersonable vanilla name-only admission. Premium lookup never silently takes
  it over. A later collision denies pending explicit recovery or a one-use
  collision approval.
- `UNKNOWN`: no durable decision exists. The authority must classify it before
  premium proof or offline enrollment starts.

Registry writes are atomic and durable. Ambiguous or unreadable state fails
closed. The registry keeps the verified UUID, canonical name, authority,
timestamps, and minimum migration metadata, and never stores access tokens or
session secrets.

## Server compatibility policy

### One login engine per CraftBukkit mapping generation

`plugin/craftbukkit/1.20.1` holds the login engine for CraftBukkit `v1_20_R1`.
Spigot 1.20.1 and Paper 1.20.1 both run Spigot-mapped server classes with the
same login members, so both plugins run this one engine. The seam contract test
checks that against the real Spigot build 3871 runtime and Paper build 196.
Paper moved to a Mojang-mapped runtime in a later Minecraft version, which will
need its own engine module.

The Spigot and Paper JARs are separate because each is tested on its own
platform, Paper renders Adventure components, and the stores differ (Hangar
accepts only Paper). Authentication behavior is identical because both JARs
contain the same engine classes.

### Startup gates

No server build number is pinned at runtime. `TrueuuidPlugin.onEnable` checks,
in order:

1. The embedded `trueuuid-target.properties` names the same platform and
   Minecraft version as the plugin class. The file is generated from the
   manifest at build time.
2. The platform matches the JAR. The Spigot JAR refuses Paper and plain
   CraftBukkit. The Paper JAR refuses Spigot. Each message names the build to
   install instead.
3. The server reports Bukkit `1.20.1-R0.1-SNAPSHOT` and its implementation class
   is in `org.bukkit.craftbukkit.v1_20_R1`.
4. `online-mode=false`.
5. Proxy forwarding is off. `settings.bungeecord` must be `false` in
   `spigot.yml`, and on Paper `proxies.velocity.enabled` must be `false`. An
   unreadable setting refuses too. TrueUUID authenticates direct connections.
   Behind a proxy, the proxy handshake and TrueUUID would both claim the login.
6. ProtocolLib is enabled and reports a 5.x version of at least 5.1.0.
7. Every entry of `LoginSeams` resolves with its exact descriptor.

Any failure logs the reason, disables the plugin, and stops the server. A
plugin that refuses startup must never leave an offline-mode server running
without authentication.

### The seam table

`LoginSeams` lists every server and library member the bridge touches: owner,
member name, exact JVM descriptor, access kind, and purpose. `LoginBridge`
resolves method handles only from this table, and resolution reports every
missing member at once. `serverRuntimeContractTest` checks every obfuscated
entry against the real mapped JAR of each CI server, including static or
instance access. A server build that changes one member therefore fails in CI
and refuses at startup by name. It never fails during a login.

### ProtocolLib

ProtocolLib is used for two things only: intercepting the three login packets,
and finding the native connection behind its temporary login player.
ProtocolLib 5.1 and 5.2 return a `NettyChannelMinimalInjector` wrapper.
ProtocolLib 5.3 and later return the `NettyChannelInjector` itself. Both paths
end at its private `networkManager` field. The shape is probed once at startup,
and any other shape refuses. Packets are sent and connections are closed
through the native `NetworkManager` and Netty channel, never through
ProtocolLib's injector API, which changed between those versions.

`ci/server-plugin-targets.json` records the tested minimum, the compile version,
the version installed on test servers, and the SHA-256 of every verified
release. A unit test loads each verified ProtocolLib JAR in isolation and
resolves its connection shape.

## Modules

```text
shared/protocol                   wire, identity policy, safe HTTP
shared/server-core                admission policy, repository, aliases, permissions
plugin/bukkit-common              Bukkit settings, status API, LuckPerms contexts,
                                  join feedback, message styles
plugin/craftbukkit/1.20.1         login engine, seam table, ProtocolLib connection,
                                  startup gates, commands
plugin/spigot/1.20.1              Spigot adapter: legacy colours, spigot.yml proxy check
plugin/paper/1.20.1               Paper adapter: Adventure gradient, Velocity check
plugin/server-plugin-target.gradle shared target build: JAR name, manifest,
                                  plugin.yml, embedded target metadata
```

Shared modules operate on plain Java values. Bukkit types, packets, profiles,
schedulers, paths, text components, ProtocolLib, and server internals stay in
plugin modules. A platform adapter only supplies its platform identity, its
message style, and its proxy-forwarding check. Authentication never depends on
the adapter.

`AdmissionMode`, `ServerConfiguration`, `UnifiedAdmissionPolicy`, deterministic
alias allocation, strict repository state, permission constants, and bounded
collision approvals live in `shared/server-core`. An adapter consumes those
types instead of recreating boolean fallback logic.

### Presentation and machine-read lines

Player chat, the action bar, command output, and the startup banner go through
`MessageStyle`. Both platforms render the bold blue `TrueUUID` gradient from
`BrandText` in `shared/presentation`, which the mods also use. Paper renders it
through Adventure and Spigot through hexadecimal colour codes. Every content
argument is literal text, so a player name or configuration value can never
inject formatting or tags.

Audit lines stay plain and use the plugin logger. Tests and operators read
them:

```text
Secure login bridge enabled: mode=..., offline=..., admission=..., platform=..., server=..., minecraft=..., ProtocolLib=...
TrueUUID login_complete outcome=... transport=... player=... uuid=...
Denied TrueUUID login for <name>: <reason> authority=... premium_failure=... offline_failure=... client_message=...
```

Login denials are plain native disconnect messages, because the login protocol
carries no client locale.

### Cross-loader parity rule

"Same feature" means the adapters consume the same validated configuration
snapshot and shared decision vector and publish the same outcome and reason
key. It does not mean that similarly named booleans happen to produce a
comparable happy path. Forge, Fabric, NeoForge, Bukkit, and future proxy
adapters must agree on:

- admission and offline-client modes
- authoritative lookup and proof-failure handling
- collision consent, alias allocation, and persistence
- final status and identity API values
- private feedback selection and alias-change notification
- command permission constants and LuckPerms contexts

The plugins consume `shared/server-core`. The released loader adapters still
contain legacy fallback booleans and verified-name registries, so they are not
yet behaviorally equivalent to the collision model. Compilation, shared
translation assets, or compatible wire bytes are not loader parity. Port one
exact loader seam at a time and keep this mismatch as an acceptance gate until
the installed artifacts pass the same golden and runtime cases.

## Acceptance gates

### Gate used for the first plugin release

On 2026-09-13 the maintainer set the gate for supporting Spigot and Paper
1.20.1:

- automated: shared and plugin unit tests, the seam contract against each
  pinned server, the Fabric 1.20.1 wire comparison, structural JAR
  verification, and the installed-server boot and clean-stop smoke
- local, with the installed production-shaped JAR on each server: an unmodified
  vanilla premium client, a Fabric 1.20.1 TrueUUID premium client, and a Fabric
  1.20.1 TrueUUID offline client, under `AUTO`, `ALLOW_VANILLA`, and
  `CONSENT_REQUIRED`

Both plugins passed. The evidence document lists exactly what ran.

### Full gate, still open

These cases remain untested on the current plugins and are not claimed:

- Forge and NeoForge 1.20.1 client-assisted logins
- a modded client hiding TrueUUID, and forged `joined=true`, UUID, skin, custom
  endpoint, or loader claims
- an offline client attempting a premium-locked or currently premium name
- HTTP 204, 429, 5xx, timeout, malformed response, DNS or TLS failure, and a
  disconnect during each asynchronous phase
- the `DENY` and `REQUIRE_TRUEUUID_CLIENT` offline modes and the
  `SAFE_PARALLEL`, `PREMIUM_RESERVED`, and collision-approval flows with real
  clients
- OfflineAuth enrollment, login, and wrong-password denial
- replayed query, encryption response, nonce, and trusted assertion
- restart persistence, a corrupt registry, and transactional migration rollback
- server builds other than the pinned ones, and ProtocolLib 5.1 to 5.3 on a
  running server
- LuckPerms contexts, and a deployment health gate that refuses service when
  the plugin or ProtocolLib JAR itself is absent

Compilation, unit tests, ProtocolLib loading, and a dedicated-server boot are
necessary but do not prove any login case. Support for another case requires
installed production JARs and retained logs and artifact hashes for the exact
server and client combination.

## Primary references

- Paper plugin messaging: <https://docs.papermc.io/paper/dev/plugin-messaging/>
- Paper 1.20.1 pre-login profile API:
  <https://jd.papermc.io/paper/1.20.1/org/bukkit/event/player/AsyncPlayerPreLoginEvent.html>
- Spigot pre-login API:
  <https://hub.spigotmc.org/javadocs/spigot/org/bukkit/event/player/AsyncPlayerPreLoginEvent.html>
- ProtocolLib login packet declarations:
  <https://github.com/dmulloy2/ProtocolLib/blob/master/src/main/java/com/comphenix/protocol/PacketType.java>
- A no-client hybrid handshake implementation reference:
  <https://github.com/TuxCoding/FastLogin>
- Velocity modern forwarding and backend security:
  <https://docs.papermc.io/velocity/player-information-forwarding/> and
  <https://docs.papermc.io/velocity/security/>
