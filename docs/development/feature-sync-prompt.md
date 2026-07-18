# Next-agent prompt: modular cross-loader feature parity

Copy the block below into a fresh Codex session. It is grounded in the live
tree at signed `main` commit `5cb9493` (2026-07-18). Re-check that state before
editing because this handoff is guidance, not evidence.

---

```text
Continue TrueUUID from current origin/main. The goal is feature parity across
the existing Fabric, Forge, and NeoForge families without cloning implementations
into every Minecraft-version module.

Create a short-lived feature/shared-parity-foundation branch. Do not use or
modify archive/* branches. Do not add more target modules, flip release flags,
publish artifacts, or claim runtime support in this phase.

READ FIRST, completely:
  - AGENTS.md
  - docs/architecture/target-matrix.md
  - docs/development/adding-adapter.md
  - docs/architecture/version-consolidation-roadmap.md
  - platform/forge-common/README.md
  - docs/development/forge-1.20x-runtime-handoff.md
  - shared/protocol/src/main/java/cn/alini/trueuuid/protocol/*
  - platform/forge-1.20.1/src/main/java/cn/alini/trueuuid/server/MigrationCoordinator.java
  - platform/forge-1.20.1/src/main/java/cn/alini/trueuuid/server/PlayerDataMigration.java
  - platform/forge-common/src/main/java/cn/alini/trueuuid/server/ForgeAdapterRuntime.java
  - platform/neoforge-1.21.1/src/main/java/cn/alini/trueuuid/server/AdapterRuntime.java
  - platform/fabric-common/src/main/java/cn/alini/trueuuid/fabric/login/FabricAdapterRuntime.java
  - platform/fabric-1.20.1/src/main/java/cn/alini/trueuuid/fabric/login/FabricLoginNetworking.java

VERIFY THE LIVE BASELINE:
  git status --short --branch
  git log --show-signature --oneline -5
  jq -r '.targets[] | [.id, .release] | @tsv' release/targets.json

At this handoff, origin/main is expected to contain signed commits c0c5f69 and
5cb9493. There are 23 declared targets and every release flag is false. The root
build and all 23 structural JAR checks passed, but that is build evidence only.
Do not repeat stale claims from older docs or prompts.

CURRENT FEATURE STATE — VERIFY IN CODE:
  - Fabric 1.20.1 already has the bounded server-owned pending-login result,
    play-join consumption, localized audit/chat/title, and server-authoritative
    HUD payload. Its premium/offline runtime rerun is still pending.
  - Modern Forge targets share platform/forge-common plus narrow API-era source
    roots. Forge 1.20.1 is a separate legacy protocol/reference island.
  - NeoForge 1.20.2-1.21.11 recompile the 1.21.1 implementation plus narrow
    version seams. NeoForge 1.20.1 recompiles the Forge 1.20.1 island.
  - Fabric lacks the public AccountStatus/callback API and Yggdrasil support.
  - Fabric, modern Forge, and modern NeoForge lack offline-to-verified migration
    and the cleanupuuid/migrateuuid commands.
  - Modern Forge/NeoForge Yggdrasil code exists but has no recorded real
    skin-site login acceptance.
  - AccountStatus, ClientAuthDiagnostics, ClientYggdrasilEndpoint, and
    OfflineFallbackPolicy contain byte-identical Forge/NeoForge copies.
    Do not preserve those copies merely because their packages currently match.
  - shared/protocol already contains generic policy, bounded-store, registry,
    session-verification, login-state, and migration-planning code. Some of that
    is server-core behavior rather than wire protocol; do not add another copy.

ARCHITECTURE RULES:
  1. One behavioral implementation per loader family/API era. A target module
     should contain version metadata and only unavoidable Minecraft/loader seams.
  2. Shared modules expose plain Java values and interfaces only. Minecraft
     profiles, players, packets, text, paths discovered from MinecraftServer,
     lifecycle callbacks, commands, and server-thread scheduling stay in adapters.
  3. Do not build one giant PlatformAdapter interface or add loader/version
     conditionals to shared code. Extract small contracts around stable semantics.
  4. Do not lower modern shared Java levels for a legacy target. Keep any future
     Java 8 target isolated.
  5. Preserve every security invariant: bounded decoding/state, endpoint
     allowlisting, public-address rejection, DNS pinning, TLS hostname checks,
     no redirects, response/time limits, cancellation, and client-only tokens.

FIRST REVIEWABLE PHASE — SHARED PARITY FOUNDATION:

Audit the shared/protocol contents and the exact Forge/NeoForge/Fabric copies.
Create truthful plain-Java boundaries instead of turning protocol into a catch-all:
  - shared/protocol: wire messages, codecs, and protocol versioning;
  - shared/core, if warranted: cross-side values and pure helpers such as the
    endpoint discovery/normalization and fixed diagnostic classification;
  - shared/server-core: auth/endpoint policy, safe verification, bounded runtime
    stores, verified-name logic, login state, and migration planning/execution.

Do not create an empty or one-class module merely to match those names. Move
existing classes rather than wrapping or duplicating them. Dependencies must
point inward (server-core may depend on core/protocol; protocol must not depend
on server-core). If a class genuinely belongs in protocol, document why and
leave it there.

Make every existing platform consumer compile against that boundary. Remove
only copies whose semantics are genuinely identical. Keep thin loader-facing
facades where public binary/package compatibility requires them. Add contract
tests once in the shared module and small adapter-mapping tests per loader
family; do not clone the same test into every version module.

As the first parity slice, add Fabric's addon API using the same public concepts
as Forge/NeoForge:
  - AccountStatus and UUID/name queries;
  - status publication from the already-consumed server result;
  - callbacks on the server thread before later join logic needs the status;
  - cleanup on disconnect/shutdown;
  - no client claim can manufacture Premium state.

Keep Minecraft's ServerPlayerEntity overload and Fabric lifecycle hookup in the
Fabric adapter. Put only loader-neutral status values/store semantics in shared
code. Preserve the released Forge 1.20.1 nullable getPremiumUuid signature;
modern APIs use Optional and must not silently break addon compatibility.

Stop and hand off after this foundation plus Fabric API is independently
reviewable, tested, and signed. Do not combine migration and Yggdrasil into the
same commit series.

NEXT PARITY SLICES, IN THIS ORDER:

1. Fabric Yggdrasil/authlib-injector support:
   - reuse the shared endpoint policy and safe session verifier;
   - extract the identical loader-neutral Forge/NeoForge endpoint discovery
     helpers instead of making a third copy;
   - keep authlib/Minecraft client reflection and Fabric networking in a small
     Fabric seam;
   - fail closed until the full allowlisted endpoint path is complete;
   - add rejected-host/private-address/redirect/oversize/timeout tests and a
     real allowed skin-site client/server run.

2. Migration and admin parity:
   - use Forge 1.20.1 behavior as the reference, but extract the filesystem
     transaction engine from PlayerDataMigration into plain server-core;
   - reuse the existing MigrationPlanner instead of inventing another planner;
   - model candidate paths, backup/journal operations, UUID text rewrites,
     commit, and rollback without Minecraft types;
   - keep world-path discovery, mod-specific path mapping, command registration,
     player disconnect/reconnect, confirmation packets/UI, and server-thread
     handoff in each loader family adapter;
   - port one vertical migration contract to modern Forge, NeoForge, and Fabric,
     then add cleanupuuid/migrateuuid as thin fronts;
   - test collision preflight, symlink refusal, size bounds, partial failure,
     rollback restoration, confirmation timeout/cancel, and successful migration.

3. Reconcile remaining parity deliberately:
   - skin refresh and any config/API differences recorded in target-matrix.md;
   - common-assets wording versus Forge 1.20.1's extra/different keys;
   - runtime acceptance for behavior already implemented.

VALIDATION FOR EVERY SHARED CHANGE:
  - run shared contract tests;
  - run focused tests for at least one consumer of every affected loader family
    and every affected API era;
  - run the root build and structural release-JAR verification before handoff;
  - use each target's declared JDK; Gradle's toolchain cannot repair an
    incompatible launcher JDK;
  - run git diff --check;
  - record exact commands and distinguish build, server boot, and real login.

RUNTIME PRIORITY WHILE FEATURE WORK CONTINUES:
  - Forge 1.20.4 and 1.20.6: execute the exact release-JAR client/server matrix
    from docs/development/forge-1.20x-runtime-handoff.md.
  - Fabric 1.20.1: rerun premium and offline acceptance against the current
    server-owned result path, capturing audit, localized chat, and HUD.
  - NeoForge 1.21.11: finish denial, malformed payload, timeout, fallback/grace,
    Yggdrasil, and migration/parity gates after those features exist.

Do not mark a whole source family runtime-proven from one anchor. Shared tests
can prove shared semantics; each shipped target/range still needs its exact JAR
boot/login acceptance before release:true.

COMMITS AND HANDOFF:
  - one architectural move or parity slice per signed commit;
  - no Co-Authored-By trailer;
  - preserve user changes and archive history;
  - update target-matrix.md only with evidence actually obtained;
  - leave all release flags false and create no tag/release without approval;
  - report changed boundaries, removed duplicates, tests, runtime evidence,
    remaining gaps, and the exact next session.
```

---

The intended first deliverable is a clean shared server-core boundary plus the
missing Fabric addon API. Yggdrasil and migration follow as separate vertical
slices; version expansion resumes only after the copied behavior is consistent
and runtime-testable.
