# Forge 1.12.2 compatibility plan

This target is planned, not implemented or supported.

- Build in an isolated Gradle/ForgeGradle project with JDK 8 and Forge
  14.23.5.2860. Do not lower the Java 17 shared module or the modern adapters.
- Reimplement the login packet and lifecycle adapter against 1.12.2 MCP
  mappings. Do not share Minecraft, Forge, authlib, Netty, or mixin classes.
- Exchange Java-8-compatible protocol fixtures with `shared/protocol`; copy a
  deliberately small compatibility facade only after the wire format is frozen.
- Validate a real modded 1.12.2 client and server for success, denial, timeout,
  disconnect, custom Yggdrasil policy, and transactional migration rollback.
- Publish only from a future `forge/1.12.2` release branch after all gates pass.
