# NeoForge shared metadata

`src/main/resources/META-INF/neoforge.mods.toml` is the only NeoForge mod-list
template. It owns the displayed version, description, authors, URL, logo and
mixin declaration for every NeoForge target.

Each `platform/neoforge-<minecraft-version>/build.gradle` supplies only its
NeoForge and Minecraft compatibility ranges through `ext.neoforgeMetadata`,
then applies `metadata.gradle`. `processResources` expands the template from
the canonical root `gradle.properties` values, so never add a per-target TOML.

The banner remains the existing shared `branding/trueuuid-banner.png` symlink
from the 1.21.1 adapter resources; later targets explicitly package it while
they reuse that adapter's resources.
