# Fabric adapter notes

Fabric has 18 exact modules from Minecraft 1.20.1 through 1.21.11.
The modules and dependency pins are in `release/targets.json` and
`platform/fabric/<version>/build.gradle`.
The [target matrix](../architecture/target-matrix.md) records runtime
evidence and current publication approvals.

This file describes the source layout. Dated acceptance results belong in the
target matrix, so a handoff does not become a second release-status source.

## Source layout

All targets apply `platform/fabric/common/target-matrix.gradle`.
It includes the common implementation, the selected session and play
networking roots, and the target's `extraSourceEras`.
The [common source guide](../../platform/fabric/common/README.md) explains
those roots.

Keep Fabric networking and Minecraft values in the adapter.
`shared/protocol` remains plain Java.
`shared/presentation` owns loader-neutral status and artwork.
Targets recompile shared source against their pinned APIs, rather than sharing
Minecraft-dependent bytecode.

The 1.20.2 session change and 1.20.5 typed-payload change are separate seams.
Authlib records, the 2D HUD matrix, identifier factories, named permissions,
and the relocated session service also have named roots.
Do not replace compile-time composition with runtime version checks.

## Client login

The common client registration uses `ClientSession` before scheduling proof.
It recognizes empty/development tokens and PCL2's profile-UUID placeholder.
A failed premium call or timeout returns a proof failure, never a no-session
answer. The server still decides offline admission and known-name denial.
See [issue #34 and session selection](../architecture/client-session.md).

## Validation

Use a JDK 21 Gradle launcher. Minecraft 1.20.1 through 1.20.4 use Java 17
toolchains, and later declared Fabric targets use Java 21.

```bash
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 \
PATH=/usr/lib/jvm/java-21-openjdk-amd64/bin:$PATH \
./gradlew :shared:protocol:test :platform:fabric-1.20.1:build \
  --configure-on-demand --no-daemon --max-workers=1

./scripts/release/validate-targets.sh
python3 scripts/ci/validate-source-sharing.py
git diff --check
```

Use `--offline` only after confirming that the target's loader toolchain can
run with cached dependencies.

The [local runtime guide](local-runtime-testing.md) covers the installed-JAR
matrix: premium, offline fallback, confirmed migration, and known-name denial.
Changed client session logic needs fresh evidence, including PCL2 offline and
expired premium sessions. Yggdrasil, cancellation, negative migration, admin
commands, addon callbacks, HUD, and skin refresh have their own evidence gaps.

A passing build or one tested anchor does not approve all Fabric versions.
Keep metadata exact-patch until the same JAR passes on every claimed patch.
Follow the [adapter guide](adding-adapter.md) for new targets.
