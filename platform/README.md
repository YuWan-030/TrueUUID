# Platform adapters

Each `platform/<loader>/<minecraft-version>` module builds one mod JAR.
It owns Minecraft packets, loader lifecycle, Mixins, configuration, world
paths, and UI integration. Shared modules never depend on an adapter.

```text
platform/
├── fabric/{common,<minecraft-version>}
├── forge/{common,<minecraft-version>}
├── neoforge/{common,<minecraft-version>}
└── common/{assets,forgelike}
```

Manifest IDs and Gradle coordinates stay flat, such as `fabric-1.21.10` and
`:platform:fabric-1.21.10`. Root `settings.gradle` maps them to physical
directories using `release/targets.json`.

The manifest lists 52 mod targets: 16 Forge, 18 Fabric, and 18 NeoForge.
Fabric and NeoForge cover each patch from 1.20.1 through 1.21.11.
Forge has no published loader for 1.20.5 or 1.21.2.
Forge 1.21.11 uses its own Gradle 9.5 wrapper, selected by the build scripts.

Common directories are source roots, not separately published adapter JARs.
Targets recompile their selected roots against pinned APIs.
`shared/protocol` owns loader-neutral login and migration logic.
`shared/presentation` owns badge state, artwork, layout, and notification
routing. Minecraft drawing and native packets stay here.

Spigot and Paper 1.20.1 are implemented under `plugin/`, with a shared
CraftBukkit login engine. Their inventory is `ci/server-plugin-targets.json`.
See the [plugin boundary](../docs/architecture/server-plugin-bridge.md).

Check the [target matrix](../docs/architecture/target-matrix.md) for evidence
and publication approvals. New targets follow the
[adapter guide](../docs/development/adding-adapter.md).
