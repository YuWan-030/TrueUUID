# TrueUUID

<h1 align="center">
  <img src="https://raw.githubusercontent.com/YuWan-030/TrueUUID/main/TrueUUID-banner.svg" width="300" alt="TrueUUID">
</h1>

English | [简体中文](README_zh-CN.md)

TrueUUID is a Minecraft authentication mod for offline-mode servers. It securely verifies premium accounts during login while keeping each player's access token on their own client.

It also supports configured Yggdrasil/authlib-injector skin-site accounts. Forge 1.20.1 is runtime-proven today; Forge and NeoForge 1.21.1 adapters are in progress, with more loaders and Minecraft versions planned.

Both the client and the server must install this mod. The server must run with:

```properties
online-mode=false
```

## Features

- Privacy-preserving authentication: the player's access token is only used locally on the client.
- Premium/Yggdrasil UUID support on offline-mode servers.
- Correct username casing after successful verification.
- Signed skin texture injection during login.
- Player info refresh after joining, helping skins update correctly.
- Clear join feedback for premium, skin-site, and offline fallback states.
- Localized UI text through Minecraft language files, so each client sees messages in its selected language.
- Offline-to-verified player data migration with confirmation and backups.
- Protection against known verified players rejoining with the same name in offline mode.

## Why

Offline-mode servers normally cannot trust player UUIDs. TrueUUID improves identity integrity while keeping the server in offline mode.

Verified players can keep their official Mojang or Yggdrasil UUID and skin data, while the server never sees their access token.

This is useful for modpacks, LAN-style servers, private offline-mode communities, and servers that want better identity consistency without enabling Mojang online-mode directly.

## How It Works

1. The server runs in offline mode.
2. During login, the server sends a custom login query with a nonce.
3. The modded client receives the query and locally calls `joinServer` with the player's profile, token, and nonce. The token never leaves the client.
4. The client replies with the authentication result and the selected authentication source.
5. The server verifies the nonce through Mojang Session Server or a supported Yggdrasil `hasJoined` endpoint.
6. If verification succeeds:
   - The pending login profile is replaced with the verified UUID.
   - Username casing is corrected.
   - Signed skin texture properties are injected.
   - The authentication source is recorded.
   - Player info is refreshed after joining.
7. If verification fails or times out:
   - Behavior is controlled by the config.
   - Known verified names can be prevented from falling back to offline mode.
   - Unknown names may still be allowed to use offline fallback if configured.

## Offline Data Migration

TrueUUID 1.0.9 adds a safer migration flow for players who used to play offline and later switch to a premium or skin-site account with the same name.

When a verified login detects matching offline UUID data, the player will see a confirmation screen. Migration only happens after confirmation.

Before migration, TrueUUID backs up both the old offline data and any existing target verified UUID data.

Supported migration targets include:

- Vanilla `playerdata`
- Vanilla `playerdata_old`
- Advancements
- Stats
- Cosmetic Armor `.cosarmor` data
- Open Parties and Claims
- FTB Chunks
- FTB Essentials
- FTB Teams
- FTB Quests
- FTB Ranks
- CustomNPCs playerdata

## Requirements

Runtime-proven target:

- Minecraft: 1.20.1
- Forge: 47.4.10 (recommended)
- Java: 17

Planned targets with passing declared-JDK builds and focused tests, but no
completed real client/server acceptance matrix yet:

- Minecraft 1.21.1, Forge 52.1.14, Java 21
- Minecraft 1.21.1, NeoForge 21.1.213, Java 21

Fabric and further loader/version adapters are planned. See
[`docs/architecture/target-matrix.md`](docs/architecture/target-matrix.md) for
the exact support status.

Client and server must both install TrueUUID.

## Installation

Server:

1. Set `online-mode=false` in `server.properties`.
2. Place the matching TrueUUID jar in the server's `mods` folder.

Client:

1. Place the matching TrueUUID jar in the client's `mods` folder.

If the client does not have this mod installed, the server will not receive the expected login query response. Depending on configuration, the player may be kicked or allowed to fall back to offline mode.

## Configuration

After the first run, the config file is generated at:

```text
config/trueuuid-common.toml
```

Important options:

```toml
auth.timeoutMs = 30000
```

Login-phase wait time in milliseconds.

```toml
auth.allowOfflineOnTimeout = false
```

`false`: kick on timeout.

`true`: allow offline fallback on timeout.

```toml
auth.allowOfflineOnFailure = true
```

`true`: allow offline fallback for normal verification failures.

`false`: disconnect on verification failure.

```toml
auth.knownPremiumDenyOffline = true
```

If a name has already been verified as premium or Yggdrasil, deny later offline fallback for that name.

```toml
auth.allowOfflineForUnknownOnly = true
```

Only allow offline fallback for names that have never been verified before.

```toml
auth.recentIpGrace.enabled = true
auth.recentIpGrace.ttlSeconds = 10
```

Allows a short same-IP reconnect grace period after a verified player disconnects. This grace is not used when the client explicitly rejects authentication or logs in as offline.

```toml
auth.showJoinFeedback = true
```

Show join feedback Title/chat messages for premium, skin-site, offline fallback, and single-player states. Set to `false` to silence those messages without changing authentication or skin refresh behavior.

Default feedback and disconnect messages are sent as Minecraft translation keys and rendered by the player's client language files (`en_us` / `zh_cn`). If you previously generated a config with custom bilingual strings, change those message values back to `trueuuid.*` keys or regenerate the config to use client-side localization.

```text
/trueuuid cleanupuuid <name>
```

Admin-only command, permission level 4. It backs up and removes the duplicate offline UUID data for a player name without touching the verified UUID data.

```text
/trueuuid migrateuuid <name>
```

Admin-only command, permission level 4. It approves inheriting the same-name offline UUID data by migrating it to the verified Mojang/Yggdrasil UUID with backups.

```toml
auth.yggdrasil.apiRootWhitelist = []
```

Allowlist for Yggdrasil/authlib-injector `hasJoined` hosts. An empty list rejects all client-reported endpoints and keeps Mojang as the safe default. Add an exact host such as `"littleskin.cn"`, or an explicit wildcard such as `"*.example.com"`. Custom endpoints must pass HTTPS/443, path, DNS/IP, response-size, timeout, and no-redirect checks.

## Compatibility Notes

- Proxies: Mojang's `hasJoined` IP parameter is optional. Verification can still work when the real client IP is hidden by a proxy.
- Skins: TrueUUID injects signed skin properties during login and refreshes player info after joining. If a client still shows stale skins, rejoining or clearing the skin cache may help.
- Offline fallback: Offline fallback is configurable. In the recommended setup, previously verified names cannot be reused by offline clients.
- Registry: TrueUUID stores known verified names in `trueuuid-registry.json`. If this file is cleared, the server forgets previous premium/Yggdrasil bindings.

## Building

Windows:

```powershell
.\gradlew.bat build
```

macOS/Linux:

```bash
./gradlew build
```

Each platform build writes its target-specific artifact to its own
`platform/<loader>-<minecraft-version>/build/libs/` directory. Forge 1.20.1
is the runtime-proven release; see
[`docs/architecture/target-matrix.md`](docs/architecture/target-matrix.md) for
the exact target status and release model.

## Privacy

The player's access token is never sent to the server.

The client uses the token locally for `joinServer`. The server only receives the authentication result and verifies the nonce through Mojang Session Server or a supported Yggdrasil endpoint.

## License

GNU LGPL 3.0

## Credits

- Mojang authlib and session API
- Sponge Mixin
- ForgeGradle
- NeoForge / ModDevGradle

---

Maintained by [@YuWan-030](https://github.com/YuWan-030).
