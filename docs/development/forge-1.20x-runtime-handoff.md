# Forge 1.20.x exact-JAR runtime handoff

Session 1 implemented the missing Forge 1.20.4 and 1.20.6 anchors. Builds,
focused tests, structural JAR checks, and Gradle development-server boots pass.
Neither target is release-approved: the exact `build/libs` JARs below still
need a matching production client/server run.

## Freeze these artifacts

| Target | Loader / Java | Exact JAR | SHA-256 |
|---|---|---|---|
| Forge 1.20.4 | Forge 49.2.8 / Java 17 | `platform/forge-1.20.4/build/libs/trueuuid-1.2.0-forge1.20.4.jar` | `053d4d2286b49aa86a992765a293223643f5a18c2a607a48f8ce80e1df9b9c7a` |
| Forge 1.20.6 | Forge 50.2.9 / Java 21 | `platform/forge-1.20.6/build/libs/trueuuid-1.2.0-forge1.20.6.jar` | `151420db0a763d550eeb860f6e8276d95c58c5768cba77ba120b763b0dcb2cff` |

Verify before installing:

```bash
sha256sum \
  platform/forge-1.20.4/build/libs/trueuuid-1.2.0-forge1.20.4.jar \
  platform/forge-1.20.6/build/libs/trueuuid-1.2.0-forge1.20.6.jar
```

Remove every older TrueUUID JAR from both the Prism instance and dedicated
server before copying one matching artifact. Do not test a client JAR against
a Gradle `runServer` and call that an exact-artifact result: the same frozen
JAR must be installed in both production `mods/` directories.

## Minimum per-target acceptance

For each target, capture the client and server `latest.log` plus the final JAR
hash and confirm:

1. Clean dedicated-server boot with no Mixin/refmap/apply error.
2. Matching Prism client boot with the account HUD visible when enabled.
3. Premium login: server `session-verified premium login`, verified UUID/name,
   skin, localized premium chat, and premium HUD.
4. Offline fallback with an unknown offline name: join succeeds, offline audit
   and chat are correct, and the HUD is offline rather than premium.
5. Clean disconnect/reconnect with no leaked transaction or stale HUD state.

Forge 1.20.6 is the highest-priority production check. Forge 50's current MDK
uses ForgeGradle 7/Gradle 9.3, while this multi-loader Gradle 8.14 repository
builds its compatible userdev bundle through ForgeGradle 6. The development
server passes, but only the installed JAR run proves the production naming
path.

## Release decision after the quick run

Passing the five checks above establishes target viability, not the entire
1.2.0 release gate. Run the complete security/behavior matrix once for this
shared legacy implementation family, then keep the per-target checks above as
artifact acceptance. Do not widen the 1.20.4 metadata to include 1.20.3 until
that exact same JAR passes on Forge 49.0.2/Minecraft 1.20.3. Forge has no
published 1.20.5 loader, so Forge 1.20.6 remains single-patch.

Keep both `release/targets.json` flags `false` until the results are recorded in
`docs/architecture/target-matrix.md` and a maintainer explicitly approves them.
