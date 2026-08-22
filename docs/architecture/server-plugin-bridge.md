# Spigot/Paper dual-mode authentication architecture

This is the security and module contract for future Spigot and Paper targets.
It is a plan, not a support claim. Version 1.3.0 is the current development
line and is intentionally blocked from publication; the first plugin target is
Spigot 1.20.1, followed by Paper 1.20.1. Version 1.2.1 remains permanently
withheld and must never be reused.

No plugin target belongs in `release/targets.json` until it builds, packages,
boots, and passes the applicable real-login matrix.

## Product modes

The server plugin must support both modes. `AUTO` may select between them per
connection, but it must never weaken the authentication decision.

### Client-assisted mode

This retains the current TrueUUID design:

1. The server sends a bounded, versioned login query.
2. A matching Forge, Fabric, or NeoForge client mod calls its local session
   service's `joinServer` method. The access token stays on the client.
3. The server independently calls the configured `hasJoined` endpoint.
4. Only the server's verified response supplies the UUID, name, and signed
   profile properties.

The wire format must be loader-neutral. Each client still installs the
TrueUUID artifact matching its loader and exact Minecraft version; the Spigot
or Paper server installs only its plugin artifact.

### Vanilla-hybrid mode

This mode requires no TrueUUID client mod and must work with an unmodified
premium client or a client running any protocol-compatible loader. The server
runs with `online-mode=false`, but the plugin owns the authentication decision
before the offline UUID or player data is committed:

1. Intercept `LOGIN_START` before normal offline login can complete.
2. Read the persistent identity classification for the normalized name.
3. For a stored premium identity and premium intent, send the vanilla
   Encryption Request and require the normal Mojang session handshake.
4. Repeat authoritative Mojang name classification for every offline route.
   Timeout, rate limiting, malformed data, DNS/TLS failure, or an authority
   error denies the login.
5. Apply the configured admission mode to the authoritative result and stored
   bindings. `PREMIUM_RESERVED` requires premium proof for a Mojang-existing
   name. `CONSENT_REQUIRED` may create a first local offline binding, but a
   later premium/offline collision denies pending explicit resolution.
6. After a premium client sends the vanilla Encryption Response, use the
   vanilla cryptographic implementation and verify `hasJoined`. Only a valid
   response permits `PREMIUM_VERIFIED` and the premium profile.

After an exact authoritative not-found result, server configuration chooses
one explicit offline policy:

- `DENY`: premium identities only.
- `REQUIRE_TRUEUUID_CLIENT`: require the existing bounded TrueUUID response
  that explicitly reports a missing client session. This is a capability gate,
  not proof of ownership of the offline name; a malicious compatible client
  can imitate it.
- `ALLOW_VANILLA`: accept the exact vanilla offline UUID/name without a client
  mod. This deliberately has ordinary offline-mode impersonation semantics.

`ALLOW_VANILLA` is the compatibility default. No offline policy runs after a
premium proof has failed. A positive name lookup classifies the identity but is
not proof that this connection owns it; the selected admission mode determines
whether an explicitly offline first claimant may receive only an offline UUID
and status. A server that needs ownership protection for offline names must add a separate
OfflineAuth credential provider rather than treating client capability as a
credential.

### Admission and name-collision modes

Fresh plugin installations use `CONSENT_REQUIRED`:

- the first final, server-accepted identity keeps the requested base name;
- a later premium/offline collision denies before player data opens;
- the denial says that nothing changed and gives the exact protected
  administrator command for a one-use, 60-second approval;
- approval is bound to normalized name, collision direction, and repository
  generation; it never supplies premium proof or an offline credential;
- a colliding offline identity uses a server-derived UUID and stable alias;
- when premium arrives after an unaliased offline binding, the offline alias is
  written only after premium session verification succeeds. A forged Login
  Start UUID hint can therefore neither rename the existing identity nor gain
  premium status.

This default is fair to the first local claimant, but it permits local display-
name squatting: an offline identity may use a Mojang-existing base name until a
collision is explicitly resolved. It never receives the Mojang UUID, signed
properties, or `PREMIUM_VERIFIED`. Servers that treat the canonical name itself
as reserved security state should use `PREMIUM_RESERVED`.

`SAFE_PARALLEL` is an explicit convenience mode: verified premium keeps the
canonical profile and colliding offline identities are automatically assigned
server-owned aliases. `FIRST_CLAIM` permanently denies the other identity kind
and remains explicitly unsafe. Existing configurations retain their selected
mode; only a newly generated configuration receives `CONSENT_REQUIRED`.

`online-mode=true` is not the mixed-mode solution: it requires the vanilla
premium handshake for every connection and an offline client cannot continue.
If a server accepts only premium accounts, native `online-mode=true` is the
simpler and preferred configuration.

### Automatic selection

An `AUTO` transport may offer the TrueUUID login query first. A supported
client mod can use client-assisted mode; a vanilla client can report the query
as unsupported and continue through vanilla-hybrid mode.

Client capability is not an authentication fact. A malicious client may hide
the mod, claim to support it, forge an acknowledgement, omit a response, or
change loaders. Those actions may select a transport or cause denial, but they
must never downgrade a stored premium binding or a failed premium proof. In
`CONSENT_REQUIRED`, an explicit offline route with no local claimant may receive
only a server-derived offline identity; it is never treated as proof of the
Mojang account. A client that claims premium intent must complete the equally
authoritative vanilla or client-assisted proof.

## Non-negotiable security invariants

Treat the client as fully compromised. It controls its packets, timing,
claimed loader, query responses, endpoint strings, and disconnect behavior.
The following rules apply in every adapter and mode:

- Name/profile lookup classifies a route; it never proves account ownership.
- `joined=true`, a client-reported UUID, skin, endpoint, or loader is never an
  authority.
- A premium identity is accepted only after the server verifies a fresh,
  connection-bound `joinServer` assertion through `hasJoined`.
- Any premium proof failure denies. HTTP 204, timeout, 429, 5xx, malformed
  JSON, name/UUID mismatch, TLS failure, client abort, and cancellation must
  never become offline fallback.
- A stored premium binding remains premium-locked across outages, restarts, IP
  changes, client loader changes, and attempts to hide the TrueUUID mod.
- An authority failure is not the same as an authoritative name-not-found
  response. Unknown identities fail closed when classification is unavailable.
- An OfflineAuth-protected identity stays bound to its credential. A
  name-only or client-gated offline identity retains its stable vanilla offline
  UUID, but Mojang classification is repeated on login. If the name later
  becomes premium, login denies until an explicit recovery or administrator
  decision. Under `CONSENT_REQUIRED`, neither side takes over or changes name
  automatically.
- Nonces and vanilla verify tokens are unpredictable, single-use, scoped to
  one connection, bounded by a short deadline, and removed on every terminal
  path. Replays and responses for another connection fail closed.
- Use Minecraft/authlib's exact digest, key exchange, and session primitives.
  Do not recreate the signed hexadecimal server hash, RSA exchange, or token
  comparison in plugin utility code.
- Network and disk work is bounded, asynchronous, and cancelled on timeout or
  disconnect. Native packet/profile changes return to the required server or
  connection event loop.
- Custom Yggdrasil authorities remain allowlisted server configuration. The
  initial no-client Spigot implementation is Mojang-only; do not accept an
  authority chosen by an unmodded or untrusted client.

`HybridIdentityPolicy` in `shared/protocol` encodes the minimum routing and
no-downgrade rules. Platform code may add denial reasons but must not override
those decisions.

## Persistent identity states

Use a transactional, case-normalized registry with at least these states:

- `PREMIUM_LOCKED`: the name/UUID was session-verified; premium proof is
  mandatory for that stored UUID and status forever unless an explicit
  administrator recovery changes it. A separate approved offline binding may
  coexist only under a unique effective alias.
- `OFFLINE_ENROLLED`: a local offline identity with an explicit authority:
  OfflineAuth credential, TrueUUID-client capability gate, or deliberately
  impersonable vanilla name-only admission. Premium lookup never silently
  takes it over; a later collision denies pending explicit recovery or a
  one-use collision approval.
- `UNKNOWN`: no durable decision exists; the authority must classify it before
  premium proof or offline enrollment starts.

Registry writes must be atomic and durable. Ambiguous or unreadable state
fails closed. Keep verified UUID, canonical name, authority, timestamps, and
the minimum migration metadata; never store access tokens or session secrets.

## Spigot and Paper boundaries

Ordinary Bukkit plugin messaging is a play-connection API and is too late to
start either pre-login proof. The adapter needs a proven packet/login hook that
can hold the connection, send the login query or vanilla Encryption Request,
receive the answer, and resume exactly once.

Spigot 1.20.1 is first. Its public pre-login API exposes a computed UUID and
allow/deny result but no mutable login profile, so the first implementation
needs an exact, audited packet/profile adapter. ProtocolLib is a possible hard
dependency if its exact supported build exposes every required pre-login seam;
otherwise use a version-pinned NMS adapter. Do not use broad reflection or
silently continue when a packet hook is missing.

Paper 1.20.1 follows. Paper's `AsyncPlayerPreLoginEvent#setPlayerProfile` may
be a supported profile-application seam, but it does not initiate the earlier
session proof. Prove that the chosen hook executes before UUID-dependent data
selection and that changing the profile reaches the actual player-data path.
Do not infer this from API presence or server boot alone.

Paper may run the Spigot artifact, but a separate Paper target is justified
only when it uses a tested Paper-specific seam or has independently recorded
runtime evidence. Shared Bukkit code must not be copied between artifacts.

The public API audit on 2026-08-14 confirms that Paper 1.20.1 exposes
`AsyncPlayerPreLoginEvent#setPlayerProfile`, while Spigot's public event exposes
the computed UUID and allow/deny controls but no profile setter. ProtocolLib's
source declares login-state start, encryption, and custom-query packet types;
that declaration does not prove it can suspend and safely resume Spigot's
native login state. Phase 0 must prove that operational seam on the exact
server and dependency builds before it becomes an architectural dependency.

## Intended modules

Create modules only when they contain working code and tests:

```text
shared/protocol                         existing wire, policy, and safe HTTP
shared/server-core                      loader-free login effects, when extracted
plugin/bukkit-common                    Bukkit-neutral config/commands/status bridge
plugin/spigot/1.20.1                    first packet and profile adapter
plugin/paper/1.20.1                     later Paper-specific profile/lifecycle seam
proxy/velocity/<version>                optional trusted network adapter
```

The shared server core operates on immutable plain Java values and returns effects such
as `SEND_CLIENT_QUERY`, `START_VANILLA_PREMIUM_PROOF`,
`REQUIRE_OFFLINE_CREDENTIAL`, `APPLY_VERIFIED_PROFILE`, or `DENY`. Minecraft
packets, profiles, schedulers, paths, text, plugin APIs, ProtocolLib, and NMS
remain outside shared modules.

`AdmissionMode`, `ServerConfiguration`, `UnifiedAdmissionPolicy`, deterministic
alias allocation, strict repository state, permission constants, and bounded
collision approvals live in `shared/server-core`. An adapter must consume those
types instead of recreating boolean fallback logic before it can claim policy
parity. Translation components, native profiles, packet hooks, paths, lifecycle
callbacks, and LuckPerms/platform permission calls remain adapter-owned.

### Cross-loader parity rule

“Same feature” means the adapters consume the same validated configuration
snapshot and shared decision vector and publish the same outcome/reason key. It
does not mean that similarly named booleans happen to produce a comparable happy
path. Forge, Fabric, NeoForge, Bukkit, and future proxy adapters must agree on:

- admission and offline-client modes;
- authoritative lookup and proof-failure handling;
- collision consent, alias allocation, and persistence;
- final status and identity API values;
- private feedback selection and alias-change notification;
- command permission constants and LuckPerms contexts.

The current Spigot candidate consumes `shared/server-core`; the released loader
adapters still contain legacy fallback booleans and verified-name registries.
They are therefore not yet behaviorally equivalent to the new collision model.
Do not describe compilation, shared translation assets, or compatible wire
bytes as completed loader parity. Port one exact loader seam at a time and keep
this mismatch as an acceptance gate until the installed artifacts pass the same
golden and runtime cases.

Do not add empty directories or manifest targets as placeholders. Add the
Spigot module to settings, CI, and a future extended target manifest in the
same change that makes its focused build pass.

## Acceptance gates

Every server/version target must pass at least:

- vanilla premium client success with no TrueUUID mod;
- Forge, Fabric, and NeoForge client-assisted success for supported 1.20.1
  clients;
- a modded client hiding TrueUUID, which must still receive secure vanilla
  premium authentication;
- forged `joined=true`, UUID, skin, custom endpoint, and loader claims;
- offline client attempting a premium-locked or currently premium name;
- HTTP 204, 429, 5xx, timeout, malformed response, DNS/TLS failure, and
  disconnect during each async phase;
- explicit not-found versus authority-unavailable classification;
- successful OfflineAuth enrollment/login and wrong-password denial;
- offline-name-to-premium collision with no automatic takeover;
- replayed query, encryption response, nonce, and trusted assertion;
- UUID/profile application before player data opens;
- cancellation, bounded pending state, restart persistence, corrupt registry,
  and transactional migration rollback;
- exactly one terminal result and exactly one server-authoritative
  `AccountStatus` publication.

Compilation, MockBukkit tests, ProtocolLib loading, and a dedicated-server boot
are necessary but do not prove any login case. Support requires installed
production JARs and retained logs/artifact hashes for the exact server and
client combinations.

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
