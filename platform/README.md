# Platform adapters

Each directory here is an independently buildable loader/version adapter. An
adapter owns Minecraft packets, loader lifecycle, mixins, configuration, world
paths and localized UI integration. It may depend on `shared/protocol`; shared
code must never depend back on an adapter.

Current support:

- `forge-1.20.1`: Forge 47.4.8, Java 17, client and server.

Future ports are added only when they compile and pass real login integration
tests. A Fabric port needs separate Fabric client/server networking and lifecycle
code. Spigot/Paper plugins need an independently designed server adapter plus a
compatible client mod or proxy protocol because TrueUUID authenticates during a
bilateral login phase. Empty directories are not used as support claims.
