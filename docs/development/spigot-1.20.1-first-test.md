# First Spigot 1.20.1 client test

This is a maintainer test for the unsupported candidate. It does not change
the target matrix or release manifest. The runner binds loopback by default,
uses exact Spigot build 3871 and ProtocolLib 5.1.0, and keeps server state and
BuildTools artifacts under ignored `build/spigot-candidate/` storage.

ProtocolLib may advertise a newer release, but this candidate intentionally
requires the audited 5.1.0 JAR and its exact checksum. The runner disables only
ProtocolLib's update notification and automatic download; do not replace the
JAR until a newer version receives the same descriptor and login-state review.

## Start one server for both client types

From the repository root:

```bash
./scripts/run-spigot-candidate.sh server AUTO
```

`AUTO` is recommended for the first test:

- a Fabric 1.20.1 TrueUUID client answers `trueuuid:auth`, and the server
  performs the client-assisted session verification;
- an unmodified client returns the vanilla protocol's explicit unsupported
  response, and the server starts Spigot's native premium proof;
- malformed, deceptive non-null, missing, or timed-out TrueUUID answers deny;
  they do not select another authentication outcome.

The first run may invoke the pinned BuildTools build and take several minutes.
Later runs reuse the ignored exact server artifacts. At the console, wait for
both of these lines before starting a client:

```text
Secure login bridge enabled: mode=AUTO, offline=ALLOW_VANILLA, admission=CONSENT_REQUIRED, Spigot=3871-Spigot-d2eba2c-3f9263b, ProtocolLib=5.1.0
Done (...)! For help, type "help"
```

Use `stop` in the server console for a clean shutdown. The runner also isolates
the Java process from terminal signals and converts Ctrl+C, SIGTERM, or SIGHUP
into that same console `stop` command; do not launch `spigot.jar` directly.

Only run one server against this test directory. If another process owns the
world, return to its console and type `stop`; do not delete `session.lock` while
that process is running. The runner checks the world lock and listening port
before launching and reports the owner instead of allowing a second startup.

## Transport, offline-client, and collision modes

The second argument controls how **premium** users prove ownership:

- `AUTO`: premium users with the mod use TrueUUID; premium users without it use
  native Mojang login. This is the seamless/recommended choice.
- `CLIENT_ASSISTED`: premium users must have the matching TrueUUID client mod.
- `VANILLA_HYBRID`: every premium user uses native Mojang login; the mod is not
  required for premium authentication.

The third argument controls which **offline clients** may join. The fourth
argument independently controls premium/offline name collisions:

| Command suffix | Premium with mod | Premium without mod | Offline with mod | Offline without mod |
|---|---:|---:|---:|---:|
| `AUTO ALLOW_VANILLA` | join | join | join | join |
| `AUTO REQUIRE_TRUEUUID_CLIENT` | join | join | join | reject |
| `AUTO DENY` | join | join | reject | reject |

Thus, `AUTO DENY` means “premium accounts only”; it does not mean premium users
need the mod. In every row, a Mojang-existing or premium-locked name must pass
premium verification before it can receive the canonical Mojang name.

Fresh installations use `CONSENT_REQUIRED`. The first server-accepted identity
keeps the base name. A later premium/offline collision is denied before player
data opens, neither binding is changed, and the kick message gives the exact
administrator command needed to approve one transition. For example:

```text
/trueuuid identity collision allow FixGOD offline
```

The approval is one-use, expires after 60 seconds, and is bound to the base
name, collision direction, and current repository generation. It does not prove
identity. A premium login must still complete server-side session proof; an
offline login still receives the UUID derived by the server from the requested
base name. The offline identity receives a stable alias such as `-FixGOD` only
after the approval is consumed. If premium proof fails, no stored identity is
renamed.

```bash
./scripts/run-spigot-candidate.sh server AUTO ALLOW_VANILLA CONSENT_REQUIRED
```

`SAFE_PARALLEL` remains an explicit automatic alternative. A verified premium
identity keeps the canonical Mojang name and a colliding offline client is
assigned a stable server-generated alias. A deterministic hash suffix is added
only if the readable alias is already occupied:

```bash
./scripts/run-spigot-candidate.sh server AUTO ALLOW_VANILLA SAFE_PARALLEL
```

Use `PREMIUM_RESERVED` for strict premium-name reservation:

```bash
./scripts/run-spigot-candidate.sh server AUTO ALLOW_VANILLA PREMIUM_RESERVED
```

`FIRST_CLAIM` is intentionally unsafe and never the default. Selecting it on
the runner is the explicit risk acknowledgement: whichever identity kind is
accepted first owns the base name, so an offline user can locally squat a
premium name.

To require a matching TrueUUID client for offline accounts:

```bash
./scripts/run-spigot-candidate.sh server AUTO REQUIRE_TRUEUUID_CLIENT
```

The default admits ordinary vanilla offline clients. Name collisions pause for
explicit administrator approval instead of silently assigning an alias:

```bash
./scripts/run-spigot-candidate.sh server AUTO ALLOW_VANILLA
```

`ALLOW_VANILLA` deliberately allows anyone to use an offline identity. Under
the default `CONSENT_REQUIRED` mode, the first accepted identity may use the
base name even when the name exists at Mojang, but it never receives the Mojang
UUID, signed properties, or `PREMIUM_VERIFIED` status. If the other identity
kind later arrives, login stops for explicit resolution. This is local display-
name squatting, not premium authentication; use `PREMIUM_RESERVED` if that risk
is unacceptable.
Lookup timeout, rate limiting, malformed response, TLS/DNS failure, or any
other ambiguous result denies rather than selecting offline admission.

Use `DENY` to accept premium identities only. `REQUIRE_TRUEUUID_CLIENT` checks
for the existing bounded TrueUUID response, but it is a client-capability gate,
not proof of ownership of an offline name; pair it with a separate offline
authentication addon if ownership protection is required.

For a Fabric offline-client test, launch the existing isolated client with an
offline name while the server uses either `ALLOW_VANILLA` or
`REQUIRE_TRUEUUID_CLIENT`:

```bash
./scripts/test-premium-client.sh --server 127.0.0.1:25565 \
  --offline-name TUOff8472Qz fabric-1.20.1
```

A successful offline join publishes `OFFLINE_FALLBACK` with transport
`OFFLINE_VANILLA` or `OFFLINE_TRUEUUID_CLIENT`. It must never do so for a
premium UUID/status/properties. Under `CONSENT_REQUIRED`, a collision is not
accepted until the one-use approval is consumed; the resulting offline identity
uses a different server-derived UUID and stable alias.

### Login denial messages

Authentication failures are sent as native login disconnect messages, before
the player joins. They give the client a concrete next action without exposing
internal errors. Examples include:

- `Offline accounts are disabled on this server. Sign in with a premium Minecraft account.`
- `Offline login requires the matching TrueUUID client mod. Install it or ask the administrator to allow vanilla offline clients.`
- `This username belongs to a premium Minecraft account. Sign in with that account; offline impersonation is blocked.`
- `Minecraft account lookup is unavailable. Login was denied to protect premium usernames. Try again later.`
- `Name collision: no identity was changed. Ask an administrator to run /trueuuid identity collision allow ...`

Login protocol packets do not provide the client's locale, so these messages
are currently English. Detailed causes remain in the operator log. If an
offline client closes its own connection when it receives a native premium
Encryption Request, no server can deliver a later kick packet over that closed
connection; this is expected for a premium name used by an offline client and
does not justify downgrading the name to offline admission.

## Test with the Fabric 1.20.1 client

The existing premium-client launcher installs the freshly built Fabric mod and
Fabric API into an isolated client directory. Sign in once if necessary:

```bash
./scripts/test-premium-client.sh login
```

Then, in a second terminal while the Spigot server is running:

```bash
./scripts/test-premium-client.sh --server 127.0.0.1:25565 fabric-1.20.1
```

A successful test joins the world, sends a green private chat confirmation to
the joining player, shows the server-owned green Premium badge, and writes a
token-free, console-only audit marker like:

```text
TrueUUID login_complete outcome=PREMIUM_VERIFIED transport=CLIENT_ASSISTED player=... uuid=...
```

The client access token remains in the private PortableMC account store and is
used only by the client for authlib `joinServer`.

## Test Spigot with no TrueUUID client mod

Start a normal premium Minecraft 1.20.1 client without the TrueUUID mod and
connect to:

```text
127.0.0.1:25565
```

A successful test writes:

```text
TrueUUID login_complete outcome=PREMIUM_VERIFIED transport=VANILLA_HYBRID player=... uuid=...
```

This mode uses Spigot's native Encryption Request/Response, verify token, RSA,
session digest, encryption setup, authlib lookup, and signed profile properties.
Because vanilla has no TrueUUID rendering code, the server sends the joining
player the same private chat confirmation and then a subtle action-bar message.
It does not use a title, subtitle, icon, or operator broadcast.

The default feedback settings are:

```yaml
feedback:
  private-chat: true
  vanilla-action-bar: true
  title: false
  modded-overlay: true
  vanilla-action-bar-delay-ticks: 20
```

The delayed action bar is suppressed when the client registers the TrueUUID
status channel, so a Fabric client uses its normal badge instead of receiving a
redundant vanilla indicator. Authentication decisions never depend on this
presentation-only channel registration.

## Evidence and troubleshooting

The prepared server is in:

```text
build/spigot-candidate/manual-server/
```

Its exact staged hashes are in `candidate-artifacts.sha256`. Relevant log lines
can be shown without exposing credentials:

```bash
rg 'TrueUUID login_complete|Denied TrueUUID|Failed to verify username' \
  build/spigot-candidate/manual-server/logs/latest.log
```

Useful diagnostic modes are:

```bash
./scripts/run-spigot-candidate.sh server CLIENT_ASSISTED
./scripts/run-spigot-candidate.sh server VANILLA_HYBRID
```

The console command `/trueuuid status` is self-service for players. Protected
health, policy, identity inspection, collision approval, alias, block, release, validation, and
reload branches use the documented `trueuuid.command.*` permissions. Bukkit's
ordinary permission checks work with LuckPerms. If LuckPerms 5.5 is installed
on this backend, TrueUUID also registers `trueuuid-status`,
`trueuuid-aliased`, and `trueuuid-authority` contexts without modifying groups.

The current candidate deliberately has no Bukkit OfflineAuth provider.
`ALLOW_VANILLA` and `REQUIRE_TRUEUUID_CLIENT` therefore do not prove ownership
of an offline name. Use `DENY`, or keep the test server private until a separate
offline authentication addon is installed and tested. Do not expose this
offline-mode test server to a public or untrusted network.
