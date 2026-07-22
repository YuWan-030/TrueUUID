<h1 align="center">
  <img src="https://raw.githubusercontent.com/YuWan-030/TrueUUID/main/TrueUUID-banner.svg" width="300" alt="TrueUUID">
</h1>

English | [简体中文](README_zh-CN.md)

TrueUUID adds account verification to modded Minecraft servers that run in
offline mode. A premium player's access token stays on their own computer; the
server receives only the login proof needed to verify their UUID and skin.

The same TrueUUID JAR must be installed on the client and server. Always choose
the JAR made for your exact Minecraft version and loader.

> [!IMPORTANT]
> Version 1.2.0 publishes only the 36 exact targets approved in the
> [target matrix](docs/architecture/target-matrix.md). Omitted Minecraft
> patches are not implicitly supported.

## Quick start

1. Download or build the JAR for your exact loader and Minecraft version.
2. Put that same JAR in the client's and server's `mods` folders.
3. Set the server to offline mode:

   ```properties
   online-mode=false
   ```

4. Start the server once to generate the TrueUUID configuration.
5. Join with a matching modded client. The HUD shows green for a verified
   premium login and red when the server accepts offline fallback.

Do not expose a development server created by `run-dev-target.sh` to the
internet. It deliberately binds to localhost and uses offline mode for testing.

## What TrueUUID does

- Verifies Mojang sessions without sending the player's access token to the
  server.
- Uses the verified UUID, username casing, and signed skin properties during
  login.
- Can allow offline players while blocking offline reuse of names that were
  previously verified.
- Stores known verified names in `trueuuid-registry.json`.
- Shows localized login feedback and a small premium/offline status badge.
- Supports bounded login timeouts, response limits, HTTPS validation, public
  address checks, and redirect refusal.
- Supports configured Yggdrasil/authlib-injector services on adapters that list
  that feature in the target matrix.

All 36 currently declared adapters include offline-to-verified data migration,
admin commands, and the addon API. Every exact target passed the premium,
offline fallback, confirmed migration, and known-name denial installed-JAR
matrix. Consult the target matrix instead of assuming every implemented feature
or an omitted adjacent Minecraft patch was exercised.

## How login verification works

1. The offline-mode server sends the client a one-time login challenge.
2. The client uses its local session to answer the challenge through Mojang's
   `joinServer` call. Its access token never leaves the client.
3. The server checks the result with Mojang's `hasJoined` endpoint.
4. A successful login receives the verified UUID and signed skin properties.
5. A failed or timed-out check is either rejected or handled by the configured
   offline-fallback policy.

For an allowed Yggdrasil provider, the same flow uses its HTTPS session
endpoint. Custom endpoints are disabled until their host is explicitly
allowlisted.

## Installation

### Server

1. Install the loader and Minecraft version matching the TrueUUID filename.
2. Copy the JAR into the server's `mods` directory.
3. Set `online-mode=false` in `server.properties`.
4. Keep the server behind a firewall while configuring and testing it.

### Prism Launcher

1. Open the instance matching the JAR's Minecraft version and loader.
2. Select **Edit** → **Mods** → **Add file**.
3. Select the built TrueUUID JAR from the target's `build/libs` directory.
4. Install the same JAR on the server.

Do not use a Forge JAR in NeoForge, or a JAR built for another Minecraft patch.
For Fabric, use the remapped JAR in `build/libs`, never the development JAR in
`build/devlibs`.

## Configuration

Forge and NeoForge generate:

```text
config/trueuuid-common.toml
```

Fabric generates:

```text
config/trueuuid.json
```

Forge and NeoForge use the TOML keys below. Fabric stores the matching login
policy settings in the JSON `auth` object; the target matrix lists any
adapter-specific gaps.

The main policy options are:

```toml
auth.timeoutMs = 30000
auth.allowOfflineOnTimeout = false
auth.allowOfflineOnFailure = true
auth.knownPremiumDenyOffline = true
auth.allowOfflineForUnknownOnly = true
auth.recentIpGrace.enabled = true
auth.recentIpGrace.ttlSeconds = 10
auth.showJoinFeedback = true
auth.showJoinTitle = false
auth.showAccountOverlay = true
```

The recommended defaults allow an unknown offline name but stop that name from
falling back to offline mode after it has completed a verified login.

All three loaders can position the account badge. Forge and NeoForge use TOML;
Fabric uses the matching keys inside the JSON `auth` object:

```toml
auth.overlayCorner = "bottom_right"
auth.overlayOffsetX = 0
auth.overlayOffsetY = 0
auth.overlayScale = 1.25
```

For Yggdrasil/authlib-injector providers, add only hosts you trust:

```toml
auth.yggdrasil.apiRootWhitelist = ["littleskin.cn"]
```

An empty allowlist keeps Mojang as the only accepted session service.

## Migration and admin commands

TrueUUID can back up and move same-name offline player data to a verified UUID.
Migration requires confirmation and preserves both
the old offline data and any existing destination data before changing files.

Available admin commands:

```text
/trueuuid cleanupuuid <name>
/trueuuid migrateuuid <name>
```

Both commands require permission level 4. The target matrix identifies which
adapters currently include migration.

## Addon API

The server-side API exposes `cn.alini.trueuuid.api.TrueuuidApi`. Other mods can
query whether a player joined as premium or through offline fallback:

```java
AccountStatus status = TrueuuidApi.getStatus(serverPlayer);
if (status.isOffline()) {
    // Apply your server's offline-player policy.
}
```

The API also provides a login callback and access to the verified-name
registry. Check the target matrix before depending on it.

## Build one JAR for Prism Launcher

Gradle must be launched with Java 21 for every target. Java 17 targets are
still compiled and run with their declared Java 17 toolchain automatically.

On Linux, from the repository checkout:

```bash
cd ~/TrueUUID
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
export PATH="$JAVA_HOME/bin:$PATH"

TARGET=neoforge-1.21.11
./scripts/ci/build-target.sh "$TARGET"
```

The Prism-ready JAR is then:

```text
platform/neoforge-1.21.11/build/libs/trueuuid-1.2.0-neoforge-1.21.11.jar
```

Change `TARGET` to any ID in `release/targets.json`, for example:

```text
forge-1.20.1
forge-1.21.6
forge-1.21.8
fabric-1.20.1
fabric-1.20.2
fabric-1.20.4
fabric-1.20.6
fabric-1.21.1
fabric-1.21.3
fabric-1.21.4
fabric-1.21.5
fabric-1.21.6
fabric-1.21.8
fabric-1.21.10
fabric-1.21.11
neoforge-1.20.4
neoforge-1.21.11
```

List every available target with:

```bash
jq -r '.targets[].id' release/targets.json
```

On Windows PowerShell, with Java 21 selected:

```powershell
.\gradlew.bat :platform:neoforge-1.21.11:build --no-daemon
# Forge 1.21.11 is the standalone Gradle 9.5 target:
.\platform\forge-1.21.11\gradlew.bat -p platform/forge-1.21.11 build --no-daemon
```

To build every target instead:

```bash
./scripts/ci/build-all-targets.sh
```

Each finished production JAR is written to:

```text
platform/<loader>-<minecraft-version>/build/libs/
```

For local client/server development runs, see
[local runtime testing](docs/development/local-runtime-testing.md).

## Privacy and security

- The player's access token stays on the client.
- The server receives a nonce result, UUID, username, and signed profile
  properties—not the token.
- Custom authentication endpoints must use HTTPS and pass the configured host,
  DNS/IP, timeout, response-size, and redirect checks.
- Offline fallback is a server policy. Keep known-name protection enabled on
  shared servers.

## License

TrueUUID is licensed under the GNU LGPL 3.0.

Maintained by [@YuWan-030](https://github.com/YuWan-030),
[@wish131400](https://github.com/wish131400), and
[@F1xGOD](https://github.com/F1xGOD).
