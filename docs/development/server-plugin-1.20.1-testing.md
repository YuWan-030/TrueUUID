# Spigot and Paper 1.20.1 test guide

This guide runs the TrueUUID server plugins locally with real clients. The
runner binds loopback by default and keeps server runtimes, worlds, and logs in
ignored `build/server-plugins/<target>/` storage. Server JARs are built or
downloaded on your machine and are never committed or redistributed.

The recorded results are in
[`server-plugin-1.20.1-evidence.md`](server-plugin-1.20.1-evidence.md). The
security contract is
[`server-plugin-bridge.md`](../architecture/server-plugin-bridge.md).

## Targets and pinned test inputs

| Target | Test server | ProtocolLib | Plugin JAR |
|---|---|---|---|
| `spigot-1.20.1` | Spigot 1.20.1 from BuildTools 200 (build 3871) | 5.4.0 | `trueuuid-<version>-spigot-1.20.1.jar` |
| `paper-1.20.1` | Paper 1.20.1 build 196 | 5.4.0 | `trueuuid-<version>-paper-1.20.1.jar` |

The pins make tests reproducible. They are not what the plugin requires. The
plugin accepts any Spigot or Paper 1.20.1 build whose login seams all resolve,
and any ProtocolLib 5.x release from 5.1.0. Install the Spigot JAR on Spigot and
the Paper JAR on Paper. Each JAR refuses the other platform and names the build
to install instead.

## Start a server

From the repository root:

```bash
./scripts/run-server-plugin.sh paper-1.20.1 server AUTO
./scripts/run-server-plugin.sh spigot-1.20.1 server AUTO
```

The first Spigot run builds the server with BuildTools and takes several
minutes. The first Paper run downloads and patches the pinned Paper build.
Later runs reuse both. Wait for these two lines before starting a client:

```text
Secure login bridge enabled: mode=AUTO, offline=ALLOW_VANILLA, admission=CONSENT_REQUIRED, platform=Paper, server=git-Paper-196, minecraft=1.20.1, ProtocolLib=5.4.0
Done (...)! For help, type "help"
```

Type `stop` in the console for a clean shutdown. Ctrl+C, SIGTERM, and SIGHUP
become the same console `stop`, so the server thread always saves the world. Do
not launch the server JAR directly. Only one server may use a test directory.
The runner checks the world lock and the port and names the process that holds
them instead of starting a second server.

`./scripts/run-server-plugin.sh <target> paths` prints the staged server
directory and every JAR path. `./scripts/run-server-plugin.sh --help` lists the
environment overrides, including another port, another verified ProtocolLib
version, and a custom server directory.

### Startup refusals

TrueUUID is the authentication boundary of an offline-mode server. When it
cannot run safely, it logs the reason, disables itself, and stops the whole
server, so the server never stays online unauthenticated. It refuses to start
when any of these is true:

- The JAR is for the other platform, or the server is not Minecraft 1.20.1.
- `online-mode` is `true`.
- `settings.bungeecord` is enabled in `spigot.yml`, or on Paper
  `proxies.velocity.enabled` is enabled. Proxy forwarding and TrueUUID would
  both claim the same login.
- ProtocolLib is missing, disabled, older than 5.1.0, or not a 5.x release.
- A required login seam is missing or changed on this server build. The log
  lists every missing member with its exact descriptor.
- The identity repository is unreadable or corrupt.

## Transport, offline-client, and collision modes

The second argument after the target controls how **premium** users prove
ownership:

- `AUTO`: premium users with the mod use TrueUUID, and premium users without it
  use native Mojang login. This is the recommended choice.
- `CLIENT_ASSISTED`: premium users must have the matching TrueUUID client mod.
- `VANILLA_HYBRID`: every premium user uses native Mojang login.

The third argument controls which **offline clients** may join:

| Command suffix | Premium with mod | Premium without mod | Offline with mod | Offline without mod |
|---|---:|---:|---:|---:|
| `AUTO ALLOW_VANILLA` | join | join | join | join |
| `AUTO REQUIRE_TRUEUUID_CLIENT` | join | join | join | reject |
| `AUTO DENY` | join | join | reject | reject |

`AUTO DENY` means premium accounts only. It does not mean premium users need the
mod. In every row, a Mojang-existing or premium-locked name must pass premium
verification before it can receive the canonical Mojang profile.

The fourth argument controls premium and offline name collisions. Fresh
installations use `CONSENT_REQUIRED`. The first accepted identity keeps the base
name, and a later collision is denied until an administrator approves one
transition, for example:

```text
/trueuuid identity collision allow FixGOD offline
```

The approval is one-use, expires after 60 seconds, and is bound to the base
name, the collision direction, and the repository generation. It proves nothing
about identity. A premium login still needs server-side session proof. The
alternatives are `SAFE_PARALLEL` (automatic stable offline aliases),
`PREMIUM_RESERVED` (Mojang-existing names are premium-only), and the unsafe
`FIRST_CLAIM`. See the
[identity policy administrator guide](identity-policy-admin-guide.md).

`ALLOW_VANILLA` deliberately allows anyone to use an offline identity, and
`REQUIRE_TRUEUUID_CLIENT` is a client capability check, not ownership
authentication. Keep a test server private, or use `DENY`, until a separate
offline authentication addon is installed.

## Test with clients

### Fabric 1.20.1 premium

Sign in once if necessary, then start the client in a second terminal while the
server runs:

```bash
./scripts/test-premium-client.sh login
./scripts/test-premium-client.sh --server 127.0.0.1:25565 fabric-1.20.1
```

`TRUEUUID_MOD_JAR` and `TRUEUUID_FABRIC_API_JAR` select exact prebuilt JARs
instead of rebuilding. A successful login writes a token-free audit line, and
the player sees a private `TrueUUID » Premium account verified.` chat message:

```text
TrueUUID login_complete outcome=PREMIUM_VERIFIED transport=CLIENT_ASSISTED player=... uuid=...
```

### Fabric 1.20.1 offline

Use a random name for which
`https://api.mojang.com/users/profiles/minecraft/<name>` returns 404. Under
`CONSENT_REQUIRED`, never use your premium name as the offline test name,
because that is a collision that needs administrator approval.

```bash
./scripts/test-premium-client.sh --server 127.0.0.1:25565 \
  --offline-name TUOff471896 fabric-1.20.1
```

```text
TrueUUID login_complete outcome=OFFLINE_FALLBACK transport=OFFLINE_VANILLA player=... uuid=...
```

`ALLOW_VANILLA` does not query the client, so the transport is `OFFLINE_VANILLA`
even with the mod installed. Under `REQUIRE_TRUEUUID_CLIENT` it is
`OFFLINE_TRUEUUID_CLIENT`.

### Unmodified premium client

Start Minecraft 1.20.1 without TrueUUID and connect to `127.0.0.1:25565`:

```text
TrueUUID login_complete outcome=PREMIUM_VERIFIED transport=VANILLA_HYBRID player=... uuid=...
```

This path uses the server's native encryption request, verify token, RSA,
session digest, and authlib lookup. The player sees the private chat message
and, a moment later, a short action-bar notice. A Fabric client that registers
the TrueUUID status channel gets its normal badge instead of the action bar.

When switching clients, the previous client's `lost connection` line can arrive
late. Count a failure only after the new client connects.

## Messages and styling

Authentication failures are native login disconnect messages, sent before the
player joins, in plain English because the login protocol carries no client
locale. Examples:

- `Offline accounts are disabled on this server. Sign in with a premium Minecraft account.`
- `Minecraft account lookup is unavailable. Login was denied to protect premium usernames. Try again later.`
- `Name collision: no identity was changed. Ask an administrator to run /trueuuid identity collision allow ...`

Chat, action-bar, and command output use a styled `TrueUUID »` prefix. Paper
renders it with a blue gradient through Adventure, and Spigot uses legacy colour
codes. Player names and configuration values are always literal text. The
startup banner is decorative and uses the same styles on the console. Log files
stay plain, and the machine-read lines (`Secure login bridge enabled`,
`login_complete`, `Denied TrueUUID login for`) never carry colour.

## CI operations

Verify and Full Self-Test run the same operations for every entry in
`ci/server-plugin-targets.json`:

```bash
./scripts/ci/run-server-plugin-target.sh paper-1.20.1 build
./scripts/ci/run-server-plugin-target.sh paper-1.20.1 verify build/server-plugin-ci/paper-1.20.1/verified-plugin
./scripts/ci/run-server-plugin-target.sh paper-1.20.1 smoke build/server-plugin-ci/paper-1.20.1/logs
```

`build` prepares the pinned server, runs the shared and plugin tests, checks
every login seam against the real mapped server JAR, and compares the plugin's
wire bytes with the Fabric 1.20.1 JAR. `verify` inspects the JAR structure and
metadata. `smoke` installs the JAR, waits for the ready line, stops the server
with one signal, and checks world saving, port release, lock release, and that
`latest.log` contains no ANSI escape codes.

## Evidence and troubleshooting

The manual server lives in `build/server-plugins/<target>/manual-server/`. Its
staged hashes are in `server-artifacts.sha256`. Show the relevant lines without
exposing credentials:

```bash
rg 'TrueUUID login_complete|Denied TrueUUID|refused startup' \
  build/server-plugins/paper-1.20.1/manual-server/logs/latest.log
```

`/trueuuid status` is self-service for players. The protected health, policy,
identity, collision, alias, block, release, validation, and reload commands use
the `trueuuid.command.*` permissions. `/trueuuid health` also prints the server
build, the ProtocolLib version, and whether the JAR is a supported build.
