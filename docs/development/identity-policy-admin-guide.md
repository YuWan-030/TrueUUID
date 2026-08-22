# Identity policy administrator guide

This guide describes the unsupported Spigot 1.20.1 candidate. It is written for
administrators who do not want to understand the login protocol. The secure
architecture contract remains
[`server-plugin-bridge.md`](../architecture/server-plugin-bridge.md).

## Recommended fresh configuration

```yaml
authentication:
  transport: AUTO
  timeout-ms: 30000

admission:
  mode: CONSENT_REQUIRED
  offline-client: ALLOW_VANILLA
  first-claim-risk-accepted: false

aliases:
  prefix: "-"

feedback:
  private-chat: true
  vanilla-action-bar: true
  title: false
  modded-overlay: true

permissions:
  provider: AUTO
```

Premium players can join with or without the TrueUUID client mod. Offline
players can also join. The first accepted premium or offline identity keeps the
requested base name. If the other identity kind later uses that name, login
stops before player data opens and no name or UUID changes silently.

| Mode | Collision behavior | Use when |
|---|---|---|
| `CONSENT_REQUIRED` | Deny, explain, and wait for one-use admin approval | Recommended mixed server |
| `SAFE_PARALLEL` | Automatically alias the offline identity | Convenience matters more than confirmation |
| `PREMIUM_RESERVED` | Mojang-existing names are premium-only | Canonical names must be reserved |
| `FIRST_CLAIM` | First identity kind permanently excludes the other | Explicitly unsafe legacy policy only |

`offline-client` is independent:

- `ALLOW_VANILLA`: modded and unmodified offline clients may join. This does
  not prove ownership of an offline name.
- `REQUIRE_TRUEUUID_CLIENT`: an offline client must answer the bounded TrueUUID
  query. This proves compatible software, not ownership.
- `DENY`: only verified premium identities may join. With transport `AUTO`, a
  premium player still does not need the mod.

## Resolving a name collision

The denied player receives a kick message containing one of these protected
commands:

```text
/trueuuid identity collision allow FixGOD offline
/trueuuid identity collision allow FixGOD premium
```

Run the command only after speaking to the affected player or checking state:

```text
/trueuuid identity inspect FixGOD
/trueuuid policy explain FixGOD
```

The approval permits one matching reconnect within 60 seconds. It is bound to
the normalized name, collision direction, and current repository generation. A
concurrent repository update invalidates it.

For an incoming offline player, the server derives the offline UUID from the
requested base name and applies a stable server-owned alias. For an incoming
premium player, the existing offline binding is moved to its future alias only
after premium session proof succeeds. A forged UUID hint, failed proof, timeout,
or disconnect cannot rename the stored offline identity.

When an alias is used, the joining player receives a private message containing
both requested and effective names. A connected offline player whose future
alias was approved is disconnected with a reconnect explanation; the server
never rewrites a live profile after player data opens. UUID and player-data
ownership are not changed by alias allocation.

## Alias prefix

The default alias is short: `FixGOD` becomes `-FixGOD`. A deterministic five-
character Base32 suffix is added only if that readable alias is already used.
Existing aliases do not change merely because configuration changes.

Supported punctuation prefixes are `.`, `+`, and `-`. The default `-` avoids
Floodgate's usual `.` namespace. Comma is deliberately unsupported because
many permission, selector, tab-list, and moderation integrations assume the
conservative punctuation set above. Client-requested and Mojang-returned names
remain strict Java names; only a server-owned effective alias may use one
leading namespace marker.

## Commands and LuckPerms

`/trueuuid status` is available to a player for their own live session. All
administrative branches require a named permission; console is allowed.

```text
trueuuid.command.status.other
trueuuid.command.health
trueuuid.command.policy
trueuuid.command.identity.inspect
trueuuid.command.identity.alias
trueuuid.command.identity.collision
trueuuid.command.identity.block
trueuuid.command.identity.release
trueuuid.command.reload
trueuuid.notify
```

Bukkit permission checks work with LuckPerms without group changes. If
LuckPerms 5.5 is installed on the backend, the candidate registers:

```text
trueuuid-status=premium|offline
trueuuid-aliased=true|false
trueuuid-authority=mojang|yggdrasil|offline
```

TrueUUID never assigns groups automatically. Administrators may use these
contexts in LuckPerms rules, while chat/tab plugins format their own metadata.
TrueUUID's private message, action bar, and modded badge remain independent.

`permissions.provider` is literal:

- `AUTO` registers the contexts when LuckPerms is present and otherwise keeps
  ordinary Bukkit permission checks. This is the recommended default.
- `LUCKPERMS` requires LuckPerms on this backend. Missing LuckPerms makes
  startup or reload fail closed instead of silently changing semantics.
- `PLATFORM` uses Bukkit permissions only and does not register contexts.

A failed reload keeps the previous validated configuration snapshot active.
LuckPerms must be installed on the backend where these checks occur; installing
it only on a proxy does not provide backend contexts.

## Security expectations

- Name lookup classifies a route; it does not authenticate the connection.
- Only server-verified proof may publish `PREMIUM_VERIFIED` or install Mojang
  UUID/profile properties.
- Offline UUIDs are server-derived; the Login Start UUID hint is never proof.
- Authority outages and every failed premium proof deny. They never downgrade.
- `ALLOW_VANILLA` permits ordinary offline-name impersonation. Add a separate
  offline credential provider if ownership of offline identities matters.
- `CONSENT_REQUIRED` permits local display-name squatting by the first offline
  claimant. Use `PREMIUM_RESERVED` if that is unacceptable.

## Loader parity status

The shared server core defines the target admission/configuration contract, but
the current Forge, Fabric, and NeoForge server adapters still use their legacy
fallback booleans and verified-name stores. Their client badge and wire remain
compatible; the new collision repository, approval commands, aliases, identity
API, and LuckPerms contexts are not yet behaviorally ported. Do not copy Spigot
YAML keys into a loader config and assume they work.

Each loader becomes equivalent only after its exact installed artifact consumes
the shared core and passes the same collision, malicious-client, restart,
permission, and feedback cases. Until then the target matrix and handoff must
state this gap plainly.
