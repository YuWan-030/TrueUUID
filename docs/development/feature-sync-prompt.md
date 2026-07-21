# Next-agent prompt: modular cross-loader feature parity

Copy the block below into a fresh session. It is grounded in the live tree at
branch `feature/shared-parity-foundation`, HEAD `e7deb0c` (2026-07-18), **8
commits ahead of `main`, not pushed or merged**. Re-check that state before
editing — this handoff is guidance, not evidence.

---

```text
Continue TrueUUID on branch feature/shared-parity-foundation (or rebase it onto
current main first). The goal is feature parity AND full 1.20.1-1.21.11 version
coverage across Fabric, Forge, and NeoForge, without cloning implementations
into every module.

Do not use or modify archive/* branches. Do not flip release flags, publish
artifacts, tag, or claim runtime support. One architectural move or parity slice
per signed commit; no Co-Authored-By trailer.

READ FIRST, completely:
  - AGENTS.md
  - docs/architecture/target-matrix.md   (authoritative support truth)
  - docs/development/adding-adapter.md
  - docs/architecture/version-consolidation-roadmap.md
  - docs/development/full-legacy-loader-coverage-handoff.md
  - platform/forge-common/README.md
  - shared/protocol/src/main/java/cn/alini/trueuuid/protocol/*   (esp. MigrationExecutor, MigrationPlanner, MigrationTransaction, AuthMessages, AuthWireCodec, LoginStateMachine, AccountStatus, AccountStatusStore)
  - platform/forge-1.20.1/src/main/java/cn/alini/trueuuid/server/{PlayerDataMigration,MigrationCoordinator,ServerLoginController}.java  (the reference migration + admin implementation)
  - platform/fabric-common/src/main/java/cn/alini/trueuuid/{api/TrueuuidApi,fabric/login/FabricAccountStatusTracker,fabric/login/FabricLoginTransaction}.java

VERIFY THE LIVE BASELINE:
  git log --oneline main..HEAD          # expect the 8 commits below at HEAD e7deb0c
  ./scripts/release/validate-targets.sh # 24 in-tree targets; forge-1.21.11 island excluded
  jq -r '.targets[] | [.id, (.release|tostring)] | @tsv' release/targets.json  # all release:false

DONE THIS SESSION (do not redo; verify in git):
  f3ec75b  Shared AccountStatus + generic AccountStatusStore<P> in shared:protocol
           (3 byte-identical copies removed; unit-tested store).
  5fbb1db  Fabric addon API: cn.alini.trueuuid.api.TrueuuidApi + FabricAccountStatusTracker
           + FabricAuthenticationSource.publicStatus(); published at play-join before
           feedback, cleared on disconnect/stop. No client can manufacture PREMIUM.
  25b8d7f  Forge/NeoForge/forge-1.20.1 runtimes now use the shared AccountStatusStore.
  15148ef  target-matrix.md: Fabric addon API recorded.
  3e23ff4  Dev tooling: scripts/test-premium-client.sh (portablemc real-premium client
           for any target) and scripts/clean-dev.sh.
  e6e5a48  Forge 1.21.10 in-tree target (record-era seams; forge-common byte-identical).
  7ba7054  Migration transaction ENGINE extracted to shared:protocol as MigrationExecutor
           (preflight/symlink/size guards, backup+journal, atomic apply, rollback, cleanup;
           9-case test). forge-1.20.1 PlayerDataMigration is now a thin path-map onto it.
  e7deb0c  Forge 1.21.11 as a standalone ForgeGradle-7 / Gradle-9.5 build island
           (platform/forge-1.21.11, own wrapper). Completes Forge coverage.

CURRENT COVERAGE (1.20.1-1.21.11):
  - Forge:    COMPLETE (11 in-tree + the 1.21.11 island). Only 1.20.5 and 1.21.2
              are impossible (upstream Forge ships no loader).
  - NeoForge: COMPLETE (1.20.1-1.21.11).
  - Fabric:   1.20.1 ONLY. Needs the full 1.20.2-1.21.11 fan-out.

CURRENT FEATURE STATE (verify in code):
  - Addon API (AccountStatus/callbacks): present on all three loaders now.
  - Migration ENGINE: shared (MigrationExecutor). WIRED only into forge-1.20.1.
  - Migration + admin commands (cleanupuuid/migrateuuid): still ONLY forge-1.20.1.
    The modern Forge line has NO command class at all yet.
  - The migration-confirmation PROTOCOL is already shared and compiled into every
    loader (AuthMessages.Query.migrationAvailable/offlineUuid/summary,
    Answer.migrationConfirmed, AuthWireCodec, LoginStateMachine.beginMigration +
    migration timeout). The modern line simply never acts on it.
  - Yggdrasil: Fabric fails closed (missing); modern Forge/NeoForge have code with
    no recorded skin-site login.
  - Nearly every target has NO real login run. That is the release gate, not the build.

ARCHITECTURE RULES:
  1. One behavioral implementation per loader family/API era. A module holds
     version metadata + only unavoidable Minecraft/loader seams.
  2. Shared modules expose plain Java values/interfaces only (MigrationExecutor
     takes Path, not MinecraftServer). Minecraft profiles/players/packets/paths/
     commands/server-thread scheduling stay in adapters.
  3. Extract small contracts around stable semantics; no giant PlatformAdapter,
     no loader/version conditionals in shared code.
  4. Do not lower shared Java levels for a legacy target.
  5. Preserve every security invariant: token stays on the client; server owns
     the final result; bounded decode/state, endpoint allowlist, public-address
     rejection, DNS pinning, TLS hostname checks, no redirects, response/time
     limits, cancellation, known-name offline protection, transactional rollback.

REMAINING WORK, IN THIS ORDER (each its own reviewable, signed commit/series):

1. Migration + admin -> modern Forge (forge-common). The engine exists; add:
   - a path-map like forge-1.20.1's (LevelResource paths are the same) delegating
     to MigrationExecutor;
   - MigrationCoordinator (single-thread IO executor implementing MigrationTransaction)
     and MigrationLockRegistry (already ExpiringBoundedStore-based) — copy from
     forge-1.20.1, they are loader-family-generic;
   - a NEW Brigadier command class (cleanupuuid/migrateuuid, and mojang status/reload)
     registered via RegisterCommandsEvent through the EventBus 6/7 @SubscribeEvent
     seam (see forge-common .../TrueuuidForgeEvents in legacy-matrix vs modern-matrix);
   - login-flow wiring that sets migrationAvailable / consumes migrationConfirmed
     (the delicate part; the protocol already carries the fields).
   Tests: collision preflight, symlink refusal, size bounds, partial-failure rollback,
   confirmation timeout/cancel, successful migration (engine tests already cover the
   filesystem core; add adapter-mapping + command tests).

2. Migration + admin -> NeoForge (neoforge-1.21.1 source), same shape.

3. Migration + admin -> Fabric (fabric-common): Yarn WorldSavePath path-map, Fabric
   command registration, wire into FabricLoginTransaction (already carries the
   AuthMessages fields).

4. Fabric Yggdrasil/authlib-injector: reuse the shared EndpointPolicy + SafeSessionVerifier;
   fail closed until the full allowlisted path is complete; add rejected-host/private-
   address/redirect/oversize/timeout tests + a real allowed skin-site run.

5. Fabric version fan-out 1.20.2-1.21.11 (each recompiles fabric-common against its
   Yarn/Loom/API; HudRenderCallback + Identifier-constructor seams per era). Only after
   Fabric 1.20.1 has a recorded premium AND offline run.

6. forge-1.21.11 island release-pipeline integration (NOT done): a manifest entry with
   an external-build marker, a CI job that uses the island's own gradlew, and the
   structural verifier against its jar. validate-targets.sh already skips islands
   (they own a settings.gradle); CI/self-test/verify matrices are manifest-driven and
   currently only cover the 24 in-tree targets.

7. Runtime acceptance: per-target premium + offline login via scripts/test-premium-client.sh
   (portablemc; token stored 600 outside the repo; Direct Connect 127.0.0.1). Record each
   real result in target-matrix.md. Do not mark a whole family proven from one anchor.

KEY LEARNINGS (this session):
  - Forge 1.21.11 / Forge 61 need ForgeGradle [7.0.3,8) on Gradle 9.5; FG 6.0.54 on
    Gradle 8.14 dies on Forge 61's userdev (duplicate entry mcp/client/Start.class).
    Hence the standalone island model (own wrapper, srcDir the shared source).
  - Minecraft 1.21+ uses official mappings => NO Mixin refmap is needed. Omit the
    SpongePowered mixin annotation processor AND MixinGradle (Gradle-9-incompatible);
    Mixin resolves targets by official name at load. This is how the island's mixins
    build. See platform/forge-1.21.11/build.gradle.
  - The forge-1.21.11 identifier-era rename is net.minecraft.resources.ResourceLocation
    -> net.minecraft.resources.Identifier in exactly ForgeNetIds, ForgeAuthPayload,
    ForgeClientQueryDecodeMixin, TrueuuidClientOverlay (matches NeoForge 1.21.11).

VALIDATION FOR EVERY CHANGE:
  export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
  ./gradlew :shared:protocol:test <one consumer per affected family/era>:build
  ./gradlew build                 # 24 in-tree targets (island builds separately)
  ./scripts/release/validate-targets.sh
  (cd platform/forge-1.21.11 && ./gradlew build)   # only when touching the island/shared source
  git diff --check
  Confirm platform/forge-common stays byte-identical when adding version targets.
  Distinguish build vs server boot vs real login in every report.

COMMITS AND HANDOFF:
  - one move/slice per signed commit; no Co-Authored-By trailer;
  - preserve user changes and archive history;
  - update target-matrix.md only with evidence actually obtained;
  - leave all release flags false; create no tag/release;
  - report changed boundaries, removed duplicates, tests, runtime evidence,
    remaining gaps, and the exact next session.
```

---

The remaining spine is: **migration + admin across the modern Forge, NeoForge,
and Fabric adapters** (the engine is already shared), then **Fabric Yggdrasil**,
then the **Fabric version fan-out**, plus the **island's release-pipeline
integration** and per-target **runtime logins**. Coverage is done for Forge and
NeoForge; Fabric coverage and cross-loader migration are the biggest remaining
pieces.
