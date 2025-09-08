# TrueUUID

English | [简体中文](README_zh-CN.md)

A Forge 1.20.x mod that securely verifies premium accounts on an offline-mode server during the login phase, without sending the player's access token to the server.

TrueUUID lets an offline-mode server:
- Ask the client to perform Mojang "joinServer" locally.
- Verify with Mojang Session Server from the server using a nonce.
- If verification passes: replace the player's UUID with the official premium UUID, fix username casing, and inject skin properties.
- If verification fails or times out: configurable behavior (kick on timeout by default; controlled fallbacks for failures).
- Inform the player on join with a Title whether they are in "Online/Premium Mode" or "Offline Mode" and send an explanatory chat message for offline fallback.

Note: Client and server must both install this mod. The server must run in offline mode.

## Highlights

- Privacy-preserving: the player's access token never leaves the client. The client calls `joinServer(profile, token, nonce)` locally.
- Better identity integrity: even in offline mode, verified players keep their official UUID and skins.
- Clear UX: Title messages for premium vs offline mode, plus a chat hint when falling back to offline.
- Data safety policies to prevent data split between online/offline UUIDs.

## New features and policies

- Name Registry: Persistently records names that have successfully verified as premium (name -> premium UUID).
- Policy: knownPremiumDenyOffline
    - Once a name has verified as premium, future auth failures will deny offline fallback for that name (to prevent data divergence).
- Policy: allowOfflineForUnknownOnly
    - Only allow offline fallback for names that have never verified as premium.
- Recent IP Grace (optional)
    - If the same name and IP had a successful verification within a short TTL (e.g., 5 minutes), a temporary premium session can be granted if verification fails. Useful to handle transient network hiccups. Use with caution on shared IP environments.
- Admin command: /trueuuid link <name>
    - Migrate/merge an offline UUID’s data to the premium UUID. Supports dry-run and backups.
- Reliable disconnect reason on 1.20.x Forge
    - During login, the server now explicitly sends the login/game disconnect packet so the client shows the detailed reason, not just “Disconnected”.

## How it works

1. Server is in offline mode. During login (HELLO), the server sends a custom login query with a nonce (transactionId + identifier `trueuuid:auth` + data).
2. The modded client intercepts this query, reads the nonce, and locally calls `MinecraftSessionService.joinServer(profile, token, nonce)` (token never sent to the server).
3. The client replies with a boolean ack (no sensitive data).
4. The server calls Mojang Session Server `/hasJoined?username={name}&serverId={nonce}[&ip={ip}]`.
5. If success:
    - Replace the pending login `GameProfile` with premium UUID and the name returned from Mojang (ensuring correct casing).
    - Inject skin properties (textures) with signature into the `GameProfile` property map.
    - After join, force-refresh player info so skins update.
    - Show a green Title “Premium Mode” with a short subtitle (configurable).
6. If failure:
    - By default:
        - Timeout: kick with configurable message.
        - Failure: behavior governed by policies below (see Configuration). Offline fallback shows a red Title “Offline Mode” with a short subtitle and chat explanation.

## Requirements

- Minecraft: 1.20.x
- Forge: 47.x (e.g., 47.4.0+)
- Java: 17
- Client and server must both install this mod.
- Server property: `online-mode=false`

## Configuration

Generated at first run:
- `config/trueuuid-common.toml`

Keys and defaults:

- auth.timeoutMs = 10000
    - Login-phase wait time (ms) for the client's reply.
- auth.allowOfflineOnTimeout = false
    - false: kick on timeout (default)
    - true: allow offline fallback if timeout occurs
- auth.allowOfflineOnFailure = true
    - Legacy broad fallback switch; fine-grained new policies below take precedence in most flows.
- auth.timeoutKickMessage = "登录超时，未完成账号校验"
    - Kick reason shown on timeout.
- auth.offlineFallbackMessage = "注意：你当前以离线模式进入服务器；如果你是正版账号，可能是网络原因导致无法成功鉴权，请重新登陆重试。继续游玩，若后续鉴权成功可能会丢失玩家数据。"
    - Chat message shown to the player if they were allowed in via offline fallback.
- auth.offlineShortSubtitle = "鉴权失败：离线模式"
    - Short subtitle for Title when in offline mode.
- auth.onlineShortSubtitle = "已通过正版校验"
    - Short subtitle for Title when in premium mode.
- auth.knownPremiumDenyOffline = true
    - If a name has ever verified as premium, deny offline fallback on later failures (prevents data splitting).
- auth.allowOfflineForUnknownOnly = true
    - Only names that have never verified as premium may fall back to offline.
- auth.recentIpGrace.enabled = true
    - Enable recent-IP grace window.
- auth.recentIpGrace.ttlSeconds = 300
    - TTL for recent-IP grace (suggested 60–600).

Notes:
- Recent IP Grace is a usability feature, not strong security. Do not use a large TTL. Avoid enabling on shared networks if you have strict identity requirements.
- The legacy `allowOfflineOnFailure` is still respected, but the new policies are recommended.

## Commands

- /trueuuid link <name>
    - Migrate data from the offline UUID (derived from name) to the premium UUID recorded in the registry.
    - Subcommands (examples):
        - /trueuuid link dryrun <name>  — show planned moves/merges, no writes.
        - /trueuuid link run <name>     — perform migration (backs up by default in the example implementation).
    - Behavior:
        - If premium data files do not exist, offline files are moved to premium.
        - If both exist, premium wins by default; merging of inventories/ender/stats can be implemented as needed (placeholders provided).

Affected files (per UUID):
- world/playerdata/<uuid>.dat
- world/advancements/<uuid>.json
- world/stats/<uuid>.json

Backups:
- Stored under world/backups/trueuuid/<timestamp>/<name>/

## Troubleshooting

- Client shows “Disconnected” without the custom reason:
    - On Forge 47.4.x, login/config stages can race. The server side now explicitly sends both login and game disconnect packets before closing to ensure the client UI displays the reason. If you still see plain “Disconnected”, test with a clean client (no UI/GUI overhaul mods).

- Skins not updating immediately:
    - The server broadcasts a PlayerInfo refresh on join. If a client-side mod overrides skin handling, ensure it does not block vanilla updates.

## Compatibility and notes

- Proxies (Bungee/Velocity): The server optionally includes the client IP in the Mojang `/hasJoined` call when available. If behind a proxy that hides the real IP, verification still works (the IP parameter is optional).
- Data integrity: With the new policies, once a name is proven premium, offline fallback is denied to prevent “dual identity” and data split.
- If the client is unmodded: it will not respond to the custom login query. Depending on config/policies, the server may kick or fall back to offline only for unknown names.

## Building

- Clone the repo.
- Run:
    - Windows: `gradlew.bat build`
    - macOS/Linux: `./gradlew build`
- The built mod is at: `build/libs/trueuuid-<version>.jar`

## Privacy

- The player's access token is never sent to your server. The token is used locally on the client to call `joinServer`.
- The server only receives a boolean ack and then itself contacts Mojang's session server to verify the nonce.

## License

- GNU LGPL 3.0 (see `gradle.properties` / project license)

## Credits

- Mojang authlib and session API.
- Sponge Mixin.
- ForgeGradle.

---
Maintained by: [@YuWan-030](https://github.com/YuWan-030)