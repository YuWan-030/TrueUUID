# neoforge-common — canonical NeoForge adapter sources

This directory owns the NeoForge implementation. Every NeoForge target recompiles
`src/main/java` against its pinned Minecraft/NeoForge APIs and adds only the named
API-era roots it needs. A target directory may contain a Java file only when that
file genuinely compiles for that target alone.

The current era roots separate the legacy overlay/config/login APIs, the 1.21.6+
2D HUD stack and overlay registration, the 1.21.9+ authlib record API, and the
1.21.11 identifier API. `platform/forgelike-common` contains the small Minecraft
seams that compile unchanged for both modern Forge and NeoForge.

`src/main/resources/META-INF/neoforge.mods.toml` is the only NeoForge mod-list
template. Individual target builds supply compatibility ranges through
`ext.neoforgeMetadata`; `metadata.gradle` expands canonical project metadata.

Run `python3 scripts/ci/validate-source-sharing.py` after adding an era seam. It
rejects exact Java source copies, including tests, and any return to
version-module source donors.
