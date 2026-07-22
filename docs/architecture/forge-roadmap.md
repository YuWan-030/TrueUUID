# Forge adapter architecture

This document describes the current Forge family. The authoritative support
and evidence state remains [`target-matrix.md`](target-matrix.md); directory or
metadata-range presence alone is not a support claim.

## Current Forge targets

Version 1.2.0 declares twelve exact Forge targets for Minecraft 1.20.1, 1.20.2,
1.20.4, 1.20.6, 1.21.1, 1.21.3, 1.21.4, 1.21.5, 1.21.6, 1.21.8, 1.21.10, and
1.21.11. On 2026-07-22 every target passed the installed-JAR premium,
offline-fallback, confirmed-migration, and known-name-denial scenarios. Omitted
patches and the wider Yggdrasil/timeout/grace/negative-migration/UI evidence
remain limited exactly as recorded in the target matrix.

Forge 1.21.11 requires ForgeGradle 7 and Gradle 9.5, so it is an intentional
standalone build island with its own wrapper. It is nevertheless a first-class
manifest target: build, Verify, Full Self-Test, runtime acceptance, packaging,
and publishing scripts select that wrapper explicitly. Run
`scripts/ci/build-all-targets.sh` for the complete 36-target repository build,
or `scripts/ci/build-target.sh forge-1.21.11` for this target alone.

## Shared protocol and adapter boundary

`shared/protocol` owns the plain-Java wire codec, bounded endpoint and HTTP
policy, login state, verified-profile values, known-name and grace stores, and
migration planning/execution contracts. It has no Minecraft, Forge, authlib,
Netty, Mixin, or loader imports.

`platform/forge-common` owns the stable Forge behaviour: client and server
login flows, configuration, lifecycle events, commands, HUD/status API,
profile conversion, world-path discovery, and Mixin integration. Exact target
modules recompile it against pinned Forge/Minecraft APIs and select only the
narrow source seams needed by their era. These seams cover SRG versus official
mappings, login payload shapes, event-bus revisions, GUI calls, authlib record
profiles, session-service access, and the 1.21.11 `Identifier` rename.

The login remains bilateral. An offline-mode server sends a one-time custom
login query; the client calls its local session service and returns only the
proof inputs. The server verifies `hasJoined` asynchronously, applies the
verified UUID/name/signed textures, and resumes native login on the server
thread. The player's access token never leaves the client.

## Build eras

- Forge 1.20.1 uses the legacy ForgeGradle 6/SRG path and receives additional
  refmap and reobfuscation checks.
- Forge 1.20.2 through 1.21.10 are modules in the root Gradle 8.14 build. They
  share `forge-common` and choose API-era sources without runtime version
  branching.
- Forge 1.21.11 uses its Gradle 9.5 wrapper and ForgeGradle 7. Its processed
  resources and compiled classes share one development mod root, and its run
  configurations pass the Mixin config explicitly because the split
  development classpath has no production JAR manifest.
- Forge 1.12.2 remains deferred. It would require an isolated JDK 8 build and a
  Java-8-compatible facade over frozen protocol fixtures; it must not lower the
  modern shared module's language level.

## Release and compatibility rules

All changes land through `main`; loader/version names identify modules rather
than permanent branches. Version 1.2.0 uses one signed tag, `v1.2.0`, and one
draft GitHub Release containing every approved loader/version artifact. Older
target-specific tags remain historical and are not the current release model.

Do not widen a Forge JAR's Minecraft range from protocol equality alone. Each
claimed patch needs its own loader availability check, structural production
JAR verification, matching client/server boot, applicable real login matrix,
artifact evidence, and maintainer approval. New targets follow
[`adding-adapter.md`](../development/adding-adapter.md).
