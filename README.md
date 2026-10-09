<h1 align="center">
  <img src="https://raw.githubusercontent.com/YuWan-030/TrueUUID/main/TrueUUID-banner.svg" width="300" alt="TrueUUID">
</h1>

English | [简体中文](README_zh-CN.md)

TrueUUID verifies accounts during login on offline-mode Minecraft servers.
Premium players keep their access token on their client, which sends it only
to their authentication service. The server checks the login proof and uses
the verified UUID, name, and signed skin properties.

Forge, Fabric, and NeoForge need TrueUUID on both client and server.
Choose the JAR for your exact loader and Minecraft version. Spigot and Paper
1.20.1 use a server plugin with ProtocolLib 5.1.0 or newer and also accept
unmodified premium clients.

The repository has 52 exact mod targets from Minecraft 1.20.1 through 1.21.11,
plus the two server plugins. See the
[target matrix](docs/architecture/target-matrix.md) for versions and test evidence.

## Install

1. Download the JAR for your loader and exact Minecraft version.
2. Put it in both `mods` folders. For Spigot/Paper, put the plugin and
   ProtocolLib in the server's `plugins` folder.
3. Set `online-mode=false` in `server.properties`.
4. Start the server to generate the config, then join.

The account badge shows verified or offline status. Offline players can join
when server policy allows it. Keep known-name protection enabled to prevent
offline reuse of a previously verified name.

See the [setup guide](docs/setup.md) for configuration, Prism Launcher,
Yggdrasil providers, and confirmed player-data migration. Plugin administrators
can use the [identity policy guide](docs/development/identity-policy-admin-guide.md).

## Build

Launch Gradle with JDK 21. Each target uses its declared Java toolchain.

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
export PATH="$JAVA_HOME/bin:$PATH"
./scripts/ci/build-target.sh forge-1.20.1
```

The JAR is in `platform/forge/1.20.1/build/libs/`. Choose another target ID
from `release/targets.json`. Use the remapped Fabric JAR in `build/libs`.

## Development

- [Documentation index](docs/README.md)
- [Module boundaries and new adapters](docs/development/adding-adapter.md)
- [Local client/server testing](docs/development/local-runtime-testing.md)
- [Release process](docs/development/release-automation.md)

## License and credits

Code: [LGPL-3.0](LICENSE). Lock icons drawn by F1xGOD:
[noncommercial use with credit](branding/lock-icons.txt).

Maintained by [@YuWan-030](https://github.com/YuWan-030),
[@wish131400](https://github.com/wish131400), and
[@F1xGOD](https://github.com/F1xGOD).
