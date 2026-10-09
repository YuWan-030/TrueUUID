# Shared Fabric sources

This is a source root, not a Gradle module.
Each Fabric target applies `target-matrix.gradle` and recompiles the selected
sources against its own Minecraft, Yarn, Fabric API, and Java versions.
It produces a separate remapped JAR.

## Ownership

`src/main/java` contains the entrypoint, JSON config, server login transaction,
profile conversion, status API, commands, and loader lifecycle.
`src/legacy-1.20/java` contains the common client login registration and
Minecraft networking hooks used by the current targets.
The name identifies where that seam started, not a maximum supported version.

Authentication values, client session classification, fallback policy,
verified-name storage, endpoint checks, and migration logic come from
`shared/protocol`. Badge state, artwork, layout, and notification routing
come from `shared/presentation`.
Translations are in `platform/common/assets`.

The login path accepts offline requests only from the explicit no-session
answer. Failed proof and timeout must not select offline fallback.
Reconnect grace is retained as compatibility code/config, not an active
login decision. See
[client session selection](../../../docs/architecture/client-session.md).

## API eras

Targets select narrow roots for APIs that changed:

- `session-profile`, `session-uuid`, or `session-api-services` for authlib's
  join call and the relocated session service;
- `play-buffers` or `play-payloads` for raw buffers versus typed play payloads;
- `profile-bean` or `profile-record` for authlib profiles;
- `permission-levels` or `permission-checks` for command permissions;
- `identifier-constructors` or `identifier-factories` for identifiers;
- `hud-matrix-4d` or `hud-matrix-2d` for the GUI stack.

`target-matrix.gradle` defines the default roots and target overrides.
Read it with the exact module's `build.gradle` before moving a file.
Keep a file shared only across targets against which it compiles unchanged.
Run `python3 scripts/ci/validate-source-sharing.py` after changing ownership.
