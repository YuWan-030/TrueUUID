# Adding a loader or Minecraft-version adapter

This guide applies to every new TrueUUID target. It prevents a directory or
branch from being mistaken for supported software.

## 1. Define one target

Record the exact Minecraft version, loader version, Java toolchain, mappings,
artifact name, and supported client/server roles in
`docs/architecture/target-matrix.md`. A target such as Forge 1.21.1 is not the
same as NeoForge 1.21.1, and a Minecraft version range is not proof of binary
compatibility.

Start from `main` on `feature/<loader>-<minecraft-version>-adapter`. Do not
fork a permanent target branch and do not copy an old branch wholesale.

The preserved NeoForge branch may provide API/lifecycle evidence, but it is not
a security-safe implementation template.

## 2. Add a self-contained platform module

Create `platform/<loader>-<minecraft-version>` only when it has a working
Gradle build. Its responsibilities are limited to:

- loader entrypoint, configuration, commands, lifecycle hooks, and resources;
- Minecraft packet codecs, payload registration, Mixins, text conversion, and
  profile conversion;
- world-path discovery and server/client-thread handoff;
- packaging a single correctly named artifact.

The adapter may depend on shared modules. Shared modules must never depend on
the adapter.

## 3. Preserve the protocol boundary

Decode the native login packet into a bounded, Java-only protocol message;
pass it to shared authentication logic; then apply the returned platform
effects. Do not expose `GameProfile`, `Component`, `Connection`,
`FriendlyByteBuf`, loader events, or Mixin callbacks to shared code.

Update golden binary fixtures whenever the wire protocol changes. A protocol
major-version change must be deliberate and retain a clear compatibility or
rejection path for older clients.

## 4. Preserve authentication safety

The adapter must retain these properties:

- only the client uses its access token for `joinServer`;
- server session verification is asynchronous and bounded;
- client-reported Yggdrasil endpoints are HTTPS/443, allowlisted, DNS-resolved
  to public addresses, pinned for the request, size-limited, and never
  redirected;
- login work is cancelled on timeout or disconnect;
- migration is confirmed, backed up, transactional, and rolls back on failure.

Paper/Spigot are not ordinary adapters: they cannot replace the login UUID
before player-data loading. Treat them as a separate, signed assertion bridge
design, normally with a compatible client mod and proxy, before claiming
support.

## 5. Validate before changing status

Before marking a target active, run:

1. Its declared-JDK Gradle build.
2. Shared protocol fixture and unit tests.
3. Target-specific codec and lifecycle tests.
4. A real modded client/server matrix: Mojang success, allowed Yggdrasil
   success, denial, timeout, disconnect, offline fallback policy, and migration
   rollback.

Add the target to the aggregate build and compile/test CI matrix after its
self-contained build and focused tests pass. Add its declared JDK, build task,
JAR checks, and client/server bootstrap markers to the full self-test path.
Enable it in
`release/targets.json` only after every gate above passes. Then update the
target matrix and release it with a signed repository version tag.
