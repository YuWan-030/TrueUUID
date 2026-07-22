# Local runtime testing

Every module declared in `release/targets.json` can be launched from two
terminals without remembering its Gradle module path. The launcher validates
that manifest first, so its target inventory cannot silently drift behind the
build, smoke-test, and release inventories:

```bash
scripts/run-dev-target.sh forge-1.20.1 server
scripts/run-dev-target.sh forge-1.20.1 client
```

For a real-account acceptance pass, sign in once and then let the harness start
local offline-mode servers, launch the matching modded client, auto-connect via
portablemc, and verify the server log markers:

```bash
scripts/test-premium-client.sh login
scripts/test-runtime-matrix.sh --targets forge-1.20.1 --scenarios premium,offline
TRUEUUID_PREMIUM_NAME=YourMinecraftName \
  scripts/test-runtime-matrix.sh --targets all --scenarios premium,offline,migrate,known-deny
```

The shell command delegates process supervision to the Python 3 standard
library. Before each target boots, the runner forces the manifest-declared mod
JAR to be packaged with `-PtrueuuidAcceptanceHooks=true`, copies that exact JAR
plus its SHA-256 into the ignored result directory, and deletes the instrumented
source artifact from the module's normal `build/libs` path. Every client
scenario for that target loads the immutable snapshot. The Gradle development
server loads source-set outputs compiled with the same matrix-only property;
staging the JAR into its `mods` directory as well would load TrueUUID twice. The ephemeral
world is removed only after the server process has stopped, while pre-existing
`trueuuid-ci-world` and `world` directories are never used or deleted.
If a preferred port is occupied, the runner selects the next bindable loopback
port within its bounded scan instead of killing or disturbing the existing
owner. It fails preflight only when no candidate port is available.
The manifest also pins the exact Forge/Fabric/NeoForge build used by each
target; PortableMC must not substitute its generally recommended loader build
for the one against which the artifact was compiled.

The runner boots each target once and reuses that server for its selected
scenarios, watches server/client output as it is produced, and fails
immediately when either process exits. There are no unconditional shutdown
sleeps or blocking FIFO writes. The first Ctrl-C performs bounded cleanup; a
per-launch inherited process token lets cleanup find Gradle, game, and client
descendants even if Gradle reparents them into another process group. A second
Ctrl-C force-kills every matching process. Use `--fail-fast` for a
quick compatibility sweep, `--timeout` for a login marker deadline, and
`--startup-timeout` for slower first builds/boots that still need to download
assets. Initial PortableMC installation/download time is tracked separately by
`--client-startup-timeout` (default 15 minutes), and the shorter login timeout
starts only when the client begins connecting to the local server. Client
fatal/mixin errors still fail immediately.
When multiple scenarios share a target boot, the runner uses dependency-safe
order: migration first, then premium and offline, with known-deny last. This
keeps the migration destination absent on the fresh world and ensures the
known-deny check follows a verified-name registration. A different order in
`--scenarios` changes the selected set, not this execution order.
After an interrupted all-target run, `--targets all --start-at forge-1.20.2`
resumes at that target without repeating earlier target boots.
For non-contiguous results, `--resume-from <run-directory>` reads that run's
`summary.tsv` and reuses a target only when every currently requested scenario
has exactly one `PASS` (or previously reused pass). Failed, partial, and
interrupted targets rerun all requested scenarios together on a fresh server;
this preserves the premium-to-known-deny dependency. The new summary records
those carried results as `REUSED_PASS`, so resume runs can be chained. Repeat
`--resume-from` to combine passed targets from more than one earlier run.
Reused rows retain the original existing evidence directory; legacy chained
summaries are resolved recursively, and missing/duplicate/cyclic provenance
fails closed so that target runs again instead of carrying an unverifiable pass.
PortableMC downloads and Forge post-processing milestones are echoed to the
terminal while detailed byte progress remains in `client.log`; first-time
loader installation should no longer look like a hung client.

`test-premium-client.sh` stores PortableMC's canonical auth database under
`~/.local/share/trueuuid-testclient/shared` with private permissions. Because
PortableMC otherwise resolves its auth database from each `--work-dir`, the
launcher consolidates same-account legacy databases and links every target to
the canonical file. Refresh-token rotation is promoted back after each run, so
moving to a new Minecraft version does not require another browser approval.
Logs and runtime artifacts go under `build/runtime-acceptance/<timestamp>`. The
`migrate` and `known-deny` scenarios need `TRUEUUID_PREMIUM_NAME` because the
server discovers migration data by the verified Minecraft profile name. The
matrix harness sets `TRUEUUID_TEST_AUTO_CONFIRM_MIGRATION=1` only for the
migration client, so the client accepts that test prompt without manual clicks.
It also sets `TRUEUUID_ACCEPTANCE_LOG=1` for both server and client JVMs. Those
variables are read only by the matrix-specific compile-time implementation.
Normal builds compile an always-disabled implementation that contains neither
environment name, and release-JAR verification fails if an instrumented JAR is
presented for publication. The instrumented mod emits stable
`TRUEUUID_ACCEPTANCE ...` markers such as
`result=premium_join`, `result=offline_fallback`, `phase=migration_query_sent`,
`phase=client_migration_auto_confirm`, `result=migration_complete`, and
`result=known_deny`; these markers intentionally avoid account tokens, session
nonces, raw endpoint URLs, and profile properties. `test-premium-client.sh`
rebuilds the staged mod artifact when relevant source/config files are newer
than the jar, or unconditionally with `TRUEUUID_REBUILD_MOD=1`. The matrix uses
`TRUEUUID_MOD_JAR` internally to pin clients to its prebuilt snapshot and avoid
rebuilding the same target for every scenario. In its isolated
per-target work directory it also pre-answers Minecraft's accessibility and
multiplayer-warning onboarding options, disables the narrator/hotkey, and marks
the first server join complete so portablemc quick-play cannot wait on a manual
Continue click.

Do not pass `-PtrueuuidAcceptanceHooks=true` to a normal build, self-test, or
publish command. `test-runtime-matrix.sh` owns that property and the cleanup of
its instrumented artifact.

All invocations need a Java 21 Gradle launcher because Fabric Loom is
configured during every Gradle invocation. Java 17 targets still run the game
with their module toolchain. Select the launcher with
`TRUEUUID_JAVA_HOME=/path/to/jdk` when necessary.

## Fabric 1.20 targets

Fabric 1.20.1, 1.20.2, and 1.20.4 produce Java 17 bytecode; Fabric 1.20.6 uses
Java 21. Loom 1.13.6 requires a Java 21 Gradle launcher for all four. Run client
and offline-mode local server separately, replacing the target ID as needed:

```bash
TRUEUUID_JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 \
  scripts/run-dev-target.sh fabric-1.20.1 server
TRUEUUID_JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 \
  scripts/run-dev-target.sh fabric-1.20.1 client
```

The script prepares the selected target's `run/server` with `eula=true`,
`online-mode=false`, and a localhost bind. Fabric 1.20.1, 1.20.2, 1.20.4, and
1.20.6 passed the four core scenarios on 2026-07-22 and are approved for 1.2.0;
the target matrix separately records extended feature paths with more limited
runtime evidence. Minecraft 1.20.3 and 1.20.5 are not implicitly covered.

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

## Fabric 1.21 targets

Fabric 1.21.1, 1.21.3, 1.21.4, 1.21.5, 1.21.6, 1.21.8, 1.21.10, and 1.21.11
are manifest-integrated and approved for the version-bound 1.2.0 release. Each exact target passed its
focused build, structural release-JAR check, client/server bootstrap smoke, and
four-case installed-JAR core login matrix. Launch any exact target with the
same wrapper:

```bash
TRUEUUID_JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 \
  scripts/run-dev-target.sh fabric-1.21.11 server
TRUEUUID_JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 \
  scripts/run-dev-target.sh fabric-1.21.11 client
```

Forge 1.21.1 is one of the core-accepted, 1.2.0-approved targets.
Launch it with Java 21 in two terminals:

```bash
TRUEUUID_JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 \
  scripts/run-dev-target.sh forge-1.21.1 server
TRUEUUID_JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 \
  scripts/run-dev-target.sh forge-1.21.1 client
```

Use the Gradle development runs for manual diagnostics. Use
`test-runtime-matrix.sh` when the result must refer to the snapshotted
production JAR and fresh-world evidence.

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
Yggdrasil, timeout/grace, negative migration, command, addon-callback, and skin
refresh runtime evidence remains separate from the four core scenarios.

Run the server and client commands in separate terminals. The client must use
the same freshly built protocol-v1 source tree as the server; it cannot
interoperate with an older TrueUUID jar.

The launcher accepts every target in the validated manifest, including
NeoForge 1.20.x, 1.21.6, 1.21.10, and 1.21.11. Record each exact artifact and
scenario separately in the target matrix; a successful run never widens a
version range or changes a release flag by itself.
