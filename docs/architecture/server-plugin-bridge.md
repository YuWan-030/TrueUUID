# Server plugin bridge boundary

This document records the architecture gate for future Paper, Spigot, and
proxy integrations. It is a design contract, not a support claim. No plugin
target should be added to the release manifest until a real early-login path
and the acceptance matrix below exist.

## Client requirement

The client side still needs a compatible TrueUUID mod because authentication
starts during login, before the player joins the world. Fabric is the practical
first client for plugin development, but the protocol must remain loader
neutral so the existing Forge and NeoForge clients can use the same bridge.

A normal Paper or Spigot plugin message is too late to safely replace the
authenticated UUID: Bukkit player creation and player-data selection have
already happened. A server plugin therefore needs one of these proven
early-login paths:

1. a supported Paper login API that exposes the required data before player
   creation and player-data loading; or
2. a proxy such as Velocity that terminates the mod login exchange, verifies
   it, and forwards a signed, short-lived identity assertion to the backend.

Do not use post-join UUID rewriting, reflection into unstable server internals,
or client-reported identity as an authority.

## Trust and protocol boundary

The server remains authoritative:

- the access token stays on the client and is used only for `joinServer`;
- the bridge verifies the session with `hasJoined`, or verifies a trusted
  proxy assertion, before accepting a premium identity;
- the bridge selects premium login, configured offline fallback, denial, or
  migration before player data is opened;
- assertions bind the verified UUID, name, backend, nonce, issue time, expiry,
  and connection context;
- signatures use explicitly configured keys with rotation support;
- nonces are single-use, expiries are short, decoding is bounded, and replay,
  malformed, expired, or unverifiable assertions fail closed;
- verification and disk work are bounded, asynchronous, and cancelled when the
  connection closes or times out.

The existing endpoint allowlist, public-address checks, TLS hostname
verification, response-size limits, and no-redirect behavior remain mandatory.

## Intended module boundaries

Keep `shared/protocol` plain Java and loader neutral. Create
`shared/server-core` only when there is real, tested code to extract: pending
login state, authentication outcomes, cancellation, offline policy, and
migration orchestration expressed through plain Java values and interfaces.

Future adapters may then be introduced as working modules:

```text
plugin/paper/<minecraft-version>
plugin/spigot/<minecraft-version>
proxy/velocity/<proxy-version>
```

Minecraft profiles, packets, text, paths, lifecycle callbacks, scheduler
handoff, and proxy APIs stay in their adapters. Do not add empty directories or
a catch-all shared module as placeholders.

## Implementation gate

Before implementation begins, prove all of the following:

1. the selected Paper API or proxy hook runs before UUID-dependent player data
   is selected;
2. the client exchange has one versioned, bounded wire format with golden
   fixtures shared by Fabric, Forge, and NeoForge;
3. the backend can consume only authenticated assertions and cannot be reached
   directly in a way that bypasses the proxy trust boundary;
4. timeout, disconnect, cancellation, replay, key rotation, and offline
   fallback behavior are explicit and testable;
5. identity migration can be confirmed, backed up, committed, and rolled back
   before the player becomes active.

## Acceptance gate

Support requires more than compilation or server boot. Each declared
client/bridge/backend combination must pass:

- premium Mojang success;
- allowed Yggdrasil success;
- rejected and malformed credentials;
- expired, replayed, and incorrectly signed assertions;
- timeout and disconnect cancellation;
- configured offline fallback and fail-closed behavior;
- migration success and rollback;
- direct-backend bypass rejection when a proxy is required;
- exactly one joining-player result message, with only optional operator
  notification.

Only after those scenarios have fresh runtime evidence should the target enter
`release/targets.json` or be described as supported.
