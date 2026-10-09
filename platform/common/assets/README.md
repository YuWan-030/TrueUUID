# Shared translations

This directory is a resource root, not a Gradle module.
Fabric and the modern Forge/NeoForge builds include
`src/main/resources` through their shared Gradle wiring.

Forge 1.20.1 still has local language files. Keep matching message keys and
wording synchronized with them when changing shared translations.

Translations have no loader dependency. Login, migration, commands, and
presentation code refer to these keys from their own adapters.
An extra feature in Forge 1.20.1 does not imply that the other loaders lack
migration or commands. Check the target matrix and source before making
a parity claim.
