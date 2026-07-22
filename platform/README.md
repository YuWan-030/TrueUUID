# Platform adapters

Each directory here is an independently buildable loader/version adapter. An
adapter owns Minecraft packets, loader lifecycle, mixins, configuration, world
paths and localized UI integration. It may depend on `shared/protocol`; shared
code must never depend back on an adapter.

The current root manifest contains 24 exact client/server targets across Forge,
Fabric, and NeoForge. Future ports are added only when they compile and pass
real login integration tests. Each target has its own module, for example
`forge-1.21.1` or `fabric-1.20.1`; it is not a permanent Git branch. See
[`../docs/architecture/target-matrix.md`](../docs/architecture/target-matrix.md)
and [`../docs/development/adding-adapter.md`](../docs/development/adding-adapter.md)
before adding one.

Fabric 1.20.1 is implemented; later Fabric versions need thin API-era seams
around the shared Fabric behaviour. A Fabric port still owns its loader-specific
client/server networking and lifecycle code.
Spigot/Paper plugins need an independently designed server adapter plus a
compatible client mod or proxy protocol because TrueUUID authenticates during a
bilateral login phase. Empty directories are not used as support claims.
