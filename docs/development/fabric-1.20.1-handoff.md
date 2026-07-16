# Handoff: start the Fabric 1.20.1 adapter

## Objective and boundaries

Create `platform/fabric-1.20.1`: a client-and-server Fabric adapter for
Minecraft 1.20.1 targeting Java 17 bytecode. Its current Loom baseline needs a
Java 21 Gradle launcher. This is a new loader boundary, not a Forge port
with imports renamed. Start on `main` using
`feature/fabric-1.20.1-adapter`.

The target starts **Planned** and `release: false`. Do not add it to the
aggregate build, CI matrices, runtime-smoke script, or release manifest until
its standalone Gradle build and focused tests work. Do not mark it supported or
release-ready until the full two-sided runtime matrix below is recorded in
[`target-matrix.md`](../architecture/target-matrix.md).

Read these before changing code:

```bash
git status --short --branch
git log --oneline -12
sed -n '1,240p' docs/development/adding-adapter.md
sed -n '1,240p' docs/architecture/target-matrix.md
sed -n '1,220p' docs/development/release-automation.md
```

## Source map

Use `platform/forge-1.20.1` as the feature and security reference, not as a
drop-in implementation. Its important responsibilities are separated already:

| Concern | Forge 1.20.1 reference |
|---|---|
| Login packet interception | `mixin/client/ClientHandshakeMixin.java`, `mixin/server/ServerLoginMixin.java` |
| Client-only token work | `client/ClientAuthExecutor.java` |
| Login policy and async controller | `server/ServerLoginController.java`, `server/AuthDecider.java`, `server/SessionCheck.java` |
| Verified-name and recent-IP policy | `server/NameRegistry.java`, `server/RecentIpGraceCache.java` |
| Migration and rollback | `server/MigrationCoordinator.java`, `server/PlayerDataMigration.java`, `server/MigrationLockRegistry.java` |
| Commands, join feedback, and skin refresh | `command/TrueuuidCommands.java`, `server/RuntimeLifecycleHandler.java`, `server/SkinRefreshHandler.java` |
| Bounded loader-free protocol/security | `shared/protocol/src/main/java/cn/alini/trueuuid/protocol/` |

`shared/protocol` must remain plain Java. Never place Fabric APIs, Minecraft
profiles, packets, text objects, filesystem paths, authlib, Netty, Mixins, or
server scheduling in it. A new reusable abstraction is appropriate only if it
is equally loader-free and has focused tests.

## Fabric-specific design requirements

Fabric play networking is too late for identity replacement: the adapter must
prove its custom exchange runs during the **login** phase. First inspect the
current Fabric Loader/API and Yarn mapping APIs for 1.20.1. If Fabric's public
login networking cannot replace the required vanilla behavior, use narrow
client/server Mixins around the mapped login packet handling, with explicit
codec bounds and tests.

The native adapter owns all of these conversions:

1. Decode the login query and bound its bytes before passing an
   `AuthMessages` value into shared code.
2. On the client, call `joinServer` with the local account token only. The
   token must never enter a packet, log, config, callback, or server object.
3. On the server, run `hasJoined` verification off the server thread; schedule
   only the completed profile/disconnect effect back to the server thread.
4. Cancel login-owned work on timeout and disconnect. Avoid static transaction
   registries that can collide or leak across connections.
5. Preserve the endpoint policy: HTTPS, port 443, explicit allowlist, public
   DNS addresses only, per-request DNS pinning, TLS hostname verification,
   response limits, and no redirects.
6. Use Fabric lifecycle events only for Fabric lifecycle work: config loading,
   commands, player join/quit tracking, feedback, skin refresh, and migration
   filesystem discovery. Keep blocking I/O off event/server threads.

Do not regress to the incomplete 1.21 feature set. The Fabric goal is parity
with Forge 1.20.1: Mojang and allowed Yggdrasil verification, configured
offline fallback and known-name policy, migration confirmation/rollback,
commands, skin refresh, recent-IP grace, configurable timeouts, and debug
control. Port these as small Fabric adapters around existing logic; do not
create a monolithic Fabric entrypoint.

## Implementation order

1. Pin exact Fabric Loader, Fabric API, Loom, Yarn mappings, and Java 17 in a
   self-contained `platform/fabric-1.20.1/build.gradle`. Record every exact
   version in `target-matrix.md` as Planned.
2. Add Fabric server/client entrypoints, metadata, mixin configuration, and a
   minimal production JAR. Verify the JAR has both entrypoints and no test
   classes before adding authentication.
3. Implement the bounded login query/answer codecs and transaction ownership.
   Add malformed/truncated/oversized-payload tests before touching the remote
   verifier.
4. Wire client `joinServer` and asynchronous server verification. Validate
   genuine premium success and disconnect cleanup before adding fallback.
5. Adapt the policy, registry, migration, command, join-feedback, and skin
   seams one at a time, preserving their existing tests or adding Fabric
   lifecycle tests.
6. Keep its exact target entry in `settings.gradle` so focused Fabric tasks are
   addressable, but do not make the root lifecycle build depend on it. Add it
   to `release/targets.json` with `release: false`, `verify.yml`,
   `self-test.yml`, `runtime-smoke.sh`, and `verify-release-jar.sh` only after
   the module builds/tests independently and those scripts have Fabric checks.

## Required validation before support status changes

Use the Java 21 launcher explicitly on this workspace; the Fabric adapter still
compiles with `--release 17`:

```bash
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 \
PATH=/usr/lib/jvm/java-21-openjdk-amd64/bin:$PATH \
./gradlew :shared:protocol:test :platform:fabric-1.20.1:test --no-daemon
```

Use `:platform:fabric-1.20.1:remapJar` when producing the JAR for a Fabric
instance; Loom's plain `jar` task writes only the development artifact.

Then run a real matching Fabric client/server matrix and record exact logs,
artifact, loader, and JDK evidence:

- Mojang success: UUID, name casing, skin properties, and client feedback;
- allowed Yggdrasil success and rejected/malformed/private endpoint rejection;
- missing token, denied verification, timeout, and disconnect during work;
- allowed/denied offline fallback and known-name/recent-IP behavior;
- migration confirmation, successful migration, and rollback after forced
  failure;
- server and headless client bootstrap plus `git diff --check`.

No build or smoke result authorizes publishing. The current release workflow
publishes only a reviewed target with `release: true`, after its full matrix,
to GitHub Releases, Modrinth, and CurseForge.
