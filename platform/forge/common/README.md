# Shared Forge sources

This is a source root, not a Gradle module.
Forge 1.20.2 and later targets recompile the selected sources against their
pinned Minecraft and Forge APIs. Forge 1.20.1 owns a separate adapter.

`modern-target.gradle` and `legacy-matrix.gradle` compose the source roots
for root-build targets. Forge 1.21.11 has its own build file and Gradle 9.5
wrapper. Check the exact target's Gradle file before moving a seam.

## Ownership

`src/main/java` contains the entrypoint, config, commands, addon API,
lifecycle/profile conversion, and the server boundary around shared login
logic. Shared source is included only where its APIs compile.

`shared/protocol` owns authentication policy, client session classification,
bounded storage, verification, and migration.
`shared/presentation` owns badge state, pixel artwork, layout, and login
notification routing. `ClientAccountStatus` binds that model to Minecraft
drawing. The artwork is rendered with fill primitives.
Translations are in `platform/common/assets`.

The client handshake is selected from `legacy-matrix`, `login-matrix`, or
`record-matrix`. Packet decoding, profile records, permissions, identifiers,
event buses, and GUI calls have their own API-era roots.
Minecraft seams shared with NeoForge live in `platform/common/forgelike`.

## Login and HUD seams

For Minecraft 1.20.2 and later, the server installs the authentication gate
at `startClientVerification` HEAD, before vanilla can publish its verifying
state. Forge 1.20.1 gates immediately after the profile writes in
`handleHello`. A tail hook can race native login completion.

HUD registration changes between Forge eras. `legacy-overlay`,
`layered-draw`, `modern-matrix`, and `record-matrix` hold the relevant
drawing/registration seams. `TrueuuidMixinPlugin` enables
`ForgeClientGuiMixin` only when `ForgeLayeredDraw` is absent, so a target
uses one HUD path.

Forge EventBus 7 layer registration differs from the earlier mod-bus API.
Check the overlay source and compiled loader JAR. Source JARs alone can list
APIs that are absent from the compile classpath.

Forge 48/49 production Mixins use SRG mappings. Forge 50 and later keep
official names for the login and GUI targets. The Gradle wiring checks the
refmap mode.

Add a narrow shared root when several targets use the same API.
Keep single-target seams in their module. Run
`python3 scripts/ci/validate-source-sharing.py` to detect exact Java copies.
The [target matrix](../../../docs/architecture/target-matrix.md) records
runtime evidence separately from source sharing.
