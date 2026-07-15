# common-assets — one source of truth for user-facing strings

Not a Gradle module: a shared **resource** root. Every adapter that speaks the
modern login flow adds it, so a message only ever exists once:

```gradle
sourceSets.main.resources.srcDir "${rootDir}/platform/common-assets/src/main/resources"
```

Language files are pure data with no loader or Minecraft-version coupling, so
unlike `platform/forge-common` this root is safe for **both** Forge and NeoForge.
Adding a string here keeps chat, titles, disconnects and the account badge worded
identically on every target.

`platform/forge-1.20.1` still carries its own copy: it implements extra features
(migration, admin commands, skin-site sources) and three of its shared keys are
worded differently. Reconcile those before pointing it here.
