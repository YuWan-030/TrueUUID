# Platform adapters

Each directory here is an independently buildable loader/version adapter. An
adapter owns Minecraft packets, loader lifecycle, mixins, configuration, world
paths and localized UI integration. It may depend on `shared/protocol`; shared
code must never depend back on an adapter.

The current release manifest contains 36 exact client/server targets across Forge,
Fabric, and NeoForge. Future ports are added only when they compile and pass
real login integration tests. Each target has its own module, for example
`forge-1.21.1` or `fabric-1.20.6`; it is not a permanent Git branch. See
[`../docs/architecture/target-matrix.md`](../docs/architecture/target-matrix.md)
and [`../docs/development/adding-adapter.md`](../docs/development/adding-adapter.md)
before adding one.

Fabric 1.20.1, 1.20.2, 1.20.4, 1.20.6, 1.21.1, 1.21.3, 1.21.4, 1.21.5,
1.21.6, 1.21.8, 1.21.10, and 1.21.11 are implemented and core accepted.
They share one behavioural implementation with thin session, record,
permission, identifier, networking, and HUD source seams.
A Fabric port still owns its loader-specific client/server networking and
lifecycle code.
Forge 1.21.11 uses its own Gradle 9.5 wrapper because ForgeGradle 7 cannot join
the root Gradle 8.14 build; manifest-driven scripts and workflows invoke that
wrapper explicitly.
Spigot/Paper plugins need an independently designed server adapter plus a
compatible client mod or proxy protocol because TrueUUID authenticates during a
bilateral login phase. Empty directories are not used as support claims.
