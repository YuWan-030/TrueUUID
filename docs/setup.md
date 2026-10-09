# Installation and configuration

Choose a production JAR for your exact loader and Minecraft version.
The [target matrix](architecture/target-matrix.md) lists the declared targets
and their test evidence.

## Mod servers and clients

1. Install the matching Forge, Fabric, or NeoForge version.
2. Put the same TrueUUID JAR in the client's and server's `mods` folders.
3. Set `online-mode=false` in `server.properties`.
4. Start the server once to generate the TrueUUID config.

In Prism Launcher, open the matching instance and select **Edit → Mods → Add
file**. For Fabric, choose the remapped JAR in `build/libs`, rather than the
development JAR in `build/devlibs`.

Development servers created by `scripts/run-dev-target.sh` bind to localhost
and use offline mode. Keep them local.

## Spigot and Paper 1.20.1

Put the matching TrueUUID plugin and ProtocolLib 5.1.0 or newer in the server's
`plugins` directory, then set `online-mode=false`.
The [plugin administrator guide](development/identity-policy-admin-guide.md)
covers admission modes, name collisions, aliases, and LuckPerms integration.
Those settings belong to the plugins. Loader mods use the config below.

Both plugins share the CraftBukkit 1.20.1 login engine. Recorded runtime tests
used ProtocolLib 5.4.0. Check the
[plugin evidence](development/server-plugin-1.20.1-evidence.md) before relying
on a different server or ProtocolLib build.

## Mod configuration

Forge and NeoForge generate `config/trueuuid-common.toml`.
Fabric generates `config/trueuuid.json`, with these options inside `auth`.

The main TOML defaults are:

```toml
[auth]
timeoutMs = 30000
allowOfflineOnFailure = true
knownPremiumDenyOffline = true
allowOfflineForUnknownOnly = true
showJoinFeedback = true
showJoinTitle = false
showAccountOverlay = true
overlayCorner = "bottom_right"
overlayOffsetX = 0
overlayOffsetY = 0
overlayScale = 1.0
```

Despite its older name, `allowOfflineOnFailure` applies only to an explicit
no-session response. It allows an offline launcher to request offline
admission. PCL2's UUID placeholder is recognized by the issue #34 fix.
A failed premium proof, timeout, malformed answer, or authentication-service
failure denies login. A client without TrueUUID cannot join a native mod
server.

For a previously verified name, offline fallback is refused while either
`knownPremiumDenyOffline` or `allowOfflineForUnknownOnly` is true.
These controls protect previously verified names. They do not prove ownership
of an unknown offline name.

`allowOfflineOnTimeout`, `recentIpGrace.enabled`, and
`recentIpGrace.ttlSeconds` remain for config compatibility. The 1.3.0 login
paths ignore them.

### Yggdrasil providers

Allow only authentication hosts you trust. For example, in TOML:

```toml
[auth.yggdrasil]
apiRootWhitelist = ["littleskin.cn"]
```

Fabric stores the equivalent list at `auth.yggdrasilHosts`, for example:

```json
{
  "auth": {
    "yggdrasilHosts": ["littleskin.cn"]
  }
}
```

Add that field to your existing `auth` object.
An empty allowlist accepts only Mojang. A custom endpoint still has to pass
HTTPS hostname verification, public-address checks, timeouts, response limits,
and the no-redirect policy. A valid provider login uses that provider's
verified profile. The local token check does not reject other opaque
UUID-shaped tokens.

### Account badge

`overlayCorner`, offsets, and scale control the client badge.
The shared model also shows singleplayer and verified LAN states.
These are presentation settings and do not change authentication policy.
The [target matrix](architecture/target-matrix.md) records the limits of visual
testing across Minecraft GUI eras.

## Migration and commands

A verified same-name login can offer to move offline player data to the
verified UUID. The player must confirm. The migration backs up the offline
files and any existing destination data before changing them.

Loader mod administrator commands are:

```text
/trueuuid cleanupuuid <name>
/trueuuid migrateuuid <name>
```

Both require permission level 4, or the equivalent named check on Minecraft
1.21.11. Plugin identity commands and collision approvals are described in
the separate administrator guide.

## Addon API

Loader mods expose `cn.alini.trueuuid.api.TrueuuidApi`.
Use the returned status explicitly:

```java
AccountStatus status = TrueuuidApi.getStatus(serverPlayer);
if (status.isOffline()) {
    // Apply the server's offline-player policy.
}
```

Do not use `!status.isPremium()` to grant offline access. An unknown status is
not an accepted offline login. The API also exposes login callbacks and
verified-name queries. Platform-specific player types stay in the adapters.
Plugin identity APIs have a separate contract in the administrator guide.

## Build a target

Launch Gradle with JDK 21, including for Java 17 targets. Toolchains select
the compiler and test runtime.

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
export PATH="$JAVA_HOME/bin:$PATH"
./scripts/ci/build-target.sh neoforge-1.21.11
jq -r '.targets[].id' release/targets.json
```

Production mod JARs are written to
`platform/<loader>/<minecraft-version>/build/libs/`.
To build every mod target, run `./scripts/ci/build-all-targets.sh`.
Forge 1.21.11 uses its standalone Gradle 9.5 wrapper. The scripts select it
automatically.

On Windows PowerShell with Java 21 selected:

```powershell
.\gradlew.bat :platform:neoforge-1.21.11:build --no-daemon
.\platform\forge\1.21.11\gradlew.bat -p platform/forge/1.21.11 build --no-daemon
```

[Local runtime testing](development/local-runtime-testing.md) explains
development launches and installed-JAR acceptance.
