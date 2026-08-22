# Platform adapters

Each directory here is an independently buildable loader/version adapter. An
adapter owns Minecraft packets, loader lifecycle, mixins, configuration, world
paths and localized UI integration. It may depend on `shared/protocol`; shared
code must never depend back on an adapter.

Physical directories are grouped by loader:

```text
platform/
├── fabric/{common,<minecraft-version>}
├── forge/{common,<minecraft-version>}
├── neoforge/{common,<minecraft-version>}
└── common/{assets,forgelike}
```

Gradle coordinates and manifest IDs deliberately remain flat and stable, such
as `:platform:fabric-1.21.10` and `fabric-1.21.10`. Root `settings.gradle`
maps those logical identities to `platform/fabric/1.21.10`.

The current release manifest contains 52 exact client/server targets: 16 Forge,
18 Fabric, and 18 NeoForge. Future ports are added only when they compile and
pass real login integration tests. Each target has its own module, for example
`forge-1.21.1` or `fabric-1.20.6`; it is not a permanent Git branch. See
[`../docs/architecture/target-matrix.md`](../docs/architecture/target-matrix.md)
and [`../docs/development/adding-adapter.md`](../docs/development/adding-adapter.md)
before adding one.

Fabric and NeoForge carry exact modules for every Minecraft patch from 1.20.1
through 1.21.11. Forge carries every patch in that line for which Forge
published a loader; Forge 1.20.5 and 1.21.2 do not exist upstream. The targets
share behavioural implementations with thin session, record, permission,
identifier, networking, and HUD source seams.
A Fabric port still owns its loader-specific client/server networking and
lifecycle code.
Forge 1.21.11 uses its own Gradle 9.5 wrapper because ForgeGradle 7 cannot join
the root Gradle 8.14 build; manifest-driven scripts and workflows invoke that
wrapper explicitly.
Spigot/Paper plugins need an independently designed early-login server adapter.
The planned dual-mode design supports either a matching TrueUUID client mod or
a server-driven vanilla encryption/session proof with no client mod; a normal
Bukkit plugin message is too late for either identity decision. These plugin
targets are not implemented or supported yet. See the
[server plugin bridge boundary](../docs/architecture/server-plugin-bridge.md)
before creating one. Empty directories are not used as support claims.
