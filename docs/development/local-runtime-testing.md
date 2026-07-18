# Local runtime testing

Every module declared in `release/targets.json` can be launched from two
terminals without remembering its Gradle module path. The launcher validates
that manifest first, so its target inventory cannot silently drift behind the
build, smoke-test, and release inventories:

```bash
scripts/run-dev-target.sh forge-1.20.1 server
scripts/run-dev-target.sh forge-1.20.1 client
```

All invocations need a Java 21 Gradle launcher because Fabric Loom is
configured during every Gradle invocation. Java 17 targets still run the game
with their module toolchain. Select the launcher with
`TRUEUUID_JAVA_HOME=/path/to/jdk` when necessary.

## Fabric 1.20.1

Fabric has a Java 17 bytecode target, but Loom 1.13.6 requires a Java 21
Gradle launcher. Run client and offline-mode local server separately:

```bash
TRUEUUID_JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 \
  scripts/run-dev-target.sh fabric-1.20.1 server
TRUEUUID_JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 \
  scripts/run-dev-target.sh fabric-1.20.1 client
```

The script prepares `platform/fabric-1.20.1/run/server` with `eula=true`,
`online-mode=false`, and a localhost bind. Fabric has policy-gated offline
fallback and a verified-name registry, but no migration, addon API, admin
commands, or Yggdrasil runtime claim.

For concurrent local runs, the launcher caps Gradle at 1G, the Fabric server
at 1536M, and the Fabric client at 3G. Override one cap only when needed:

```bash
TRUEUUID_CLIENT_XMX=4G TRUEUUID_JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 \
  scripts/run-dev-target.sh fabric-1.20.1 client
```

For a client running on this same machine, set `server-ip=127.0.0.1` in the
target's `run/server.properties`. This also avoids a wildcard IPv6 bind failure
on hosts without IPv6. For a LAN test, use the server's actual IPv4 address
instead; do not expose a development server to the public internet.

Forge 1.21.1 is a test candidate, not a released target. Its local premium
login path has passed once, but the full acceptance matrix is incomplete.
Launch it with Java 21 in two terminals:

```bash
TRUEUUID_JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 \
  scripts/run-dev-target.sh forge-1.21.1 server
TRUEUUID_JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 \
  scripts/run-dev-target.sh forge-1.21.1 client
```

Use the Gradle development runs for the first 1.21.1 acceptance pass. A normal
distribution artifact must not be called ready until the real login matrix has
passed.

Forge's `online-mode=true` performs Mojang authentication itself and therefore
bypasses TrueUUID's custom login query. To test the TrueUUID premium-session
path locally, the development launcher enforces `online-mode=false` and
`server-ip=127.0.0.1` before every server run. The adapter must then log a
`session-verified premium login` before allowing the player to join. Never
expose an offline-mode development server publicly.

`auth.showJoinFeedback` defaults to `true` in `config/trueuuid-common.toml`.
It sends the player a localized chat message and title after verification or
offline fallback. `auth.showAccountOverlay` defaults to `true` and shows a
small top-left account badge after a TrueUUID handshake. Green means premium
verified; red means the server accepted its configured offline fallback. The
server audit line remains English for administrators and log tooling;
player-facing text follows the player's client language.

For Forge 1.21.1, an offline launcher/account receives an immediate offline
fallback decision rather than waiting for a premium timeout. The default
policy allows only names that have not previously completed a verified premium
login. Successful premium names are recorded in
`config/trueuuid-registry.json`; keep
`auth.knownPremiumDenyOffline=true` and
`auth.allowOfflineForUnknownOnly=true` on public/private shared servers.
Do not advertise the Yggdrasil and player-data migration behaviours from the
1.20.1 adapter for 1.21.1 yet.

Run the server and client commands in separate terminals. The client must use
the same freshly built protocol-v1 source tree as the server; it cannot
interoperate with an older TrueUUID jar.

The launcher accepts every target in the validated manifest, including
NeoForge 1.20.x, 1.21.6, 1.21.10, and 1.21.11. This is a test convenience, not
a support or release claim; record each real login result separately in the
target matrix before changing its release flag.
