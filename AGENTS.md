# TrueUUID contributor guide

## Repository model

This is one multi-target repository. Target support is represented by Gradle
modules under `platform/`, not by permanently divergent Git branches.

- `main` is the integration trunk and the source of truth.
- Use short-lived `feature/<scope>` branches for changes and merge them into
  `main`.
- Create `maintenance/<loader>-<minecraft-version>` only when a released,
  older target needs a backport. Use `git cherry-pick -x` for that backport.
- Historical branches under `archive/` are read-only references. Do not merge
  an old target branch wholesale into `main` and do not rewrite/delete archive
  history.
- Releases are immutable tags such as `forge-1.20.1-v1.1.0`, not a branch for
  every loader/version pair.

See `docs/architecture/target-matrix.md` for the current support status and
`docs/development/adding-adapter.md` before beginning a new target.

## Module boundaries

- `shared/protocol` must remain plain Java: no Minecraft, loader, authlib,
  Netty, or Mixin dependencies.
- Future shared authentication and server modules must expose plain Java
  values and interfaces. Minecraft profiles, packets, text, paths, lifecycle
  callbacks, and server-thread scheduling belong in platform adapters.
- Every `platform/<loader>-<minecraft-version>` module owns exactly one
  loader/Minecraft boundary and produces one target-specific artifact.
- A Java 8 legacy target must be isolated. It may share the frozen binary
  protocol fixtures, but must not lower modern shared modules from their
  declared Java level.

## Security and lifecycle rules

- Treat client-supplied endpoint data and login payloads as untrusted. Preserve
  bounded decoding, endpoint allowlisting, public-address checks, TLS hostname
  verification, response limits, and no-redirect behavior.
- Never put network or disk I/O on the server thread. Login-owned work must be
  bounded and cancelled when the connection disconnects or times out.
- Keep the login path explicit and testable: authentication, offline fallback,
  migration confirmation, migration rollback, and disconnects must not drift
  between adapters.

## Validation

For the active Forge 1.20.1 target, run the focused test suite before handing
off changes:

```bash
./gradlew :shared:protocol:test :platform:forge-1.20.1:test --offline --no-daemon
git diff --check
```

Gradle 8.8 must itself start on a compatible JDK; its Java toolchain setting
does not fix an unsupported launcher JDK. The project targets Java 17. On this
workspace, where the default `java` may be newer, use:

```bash
JAVA_HOME=/usr/lib/jvm/jdk-17.0.12-oracle-x64 \
PATH=/usr/lib/jvm/jdk-17.0.12-oracle-x64/bin:$PATH \
./gradlew :shared:protocol:test :platform:forge-1.20.1:test --offline --no-daemon
```

Do not claim support for a new target until its declared JDK build, shared
protocol fixtures, focused tests, and real modded client/server login matrix
have passed.
