# Kickoff prompt: loader parity before full 1.20.1-1.21.11 coverage

Copy the block below into a fresh Codex session. It deliberately finishes the
existing adapters before adding more Minecraft-version modules. Do not turn
this into one unreviewable cross-loader rewrite.

---

```text
TrueUUID needs one consistent, server-authoritative feature contract across
Fabric, Forge, and NeoForge before its version matrix expands further.

Start fresh from current origin/main. Create a short-lived
feature/fabric-status-parity branch for the first reviewable phase. Do not use,
merge, rewrite, or delete archive/* branches. Do not add new Minecraft versions
in this phase and do not flip any release flags.

READ FIRST, completely:
  - AGENTS.md
  - docs/architecture/target-matrix.md
  - docs/development/adding-adapter.md
  - docs/architecture/version-consolidation-roadmap.md
  - docs/development/feature-sync-prompt.md
  - platform/forge-common/README.md
  - platform/fabric-common/src/main/java/cn/alini/trueuuid/fabric/login/FabricLoginTransaction.java
  - platform/fabric-1.20.1/src/main/java/cn/alini/trueuuid/fabric/login/FabricLoginNetworking.java
  - platform/fabric-common/src/main/java/cn/alini/trueuuid/fabric/config/FabricConfig.java
  - platform/forge-common/src/main/java/cn/alini/trueuuid/server/ForgeAdapterRuntime.java
  - platform/neoforge-1.21.1/src/main/java/cn/alini/trueuuid/server/AdapterRuntime.java

Re-check live git state and current CI before trusting any handoff text:
  git status --short --branch
  git log --oneline -12
  jq -r '.targets[] | [.id, .release] | @tsv' release/targets.json

BUILD ENV:
  export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
  export PATH="$JAVA_HOME/bin:$PATH"

Known live state at handoff time (2026-07-18; verify it):
  - All 21 declared targets build locally, but all remain release:false.
  - NeoForge 1.21.11 completed one real Prism premium login with verified UUID,
    signed skin, localized premium chat, premium HUD, and clean disconnect.
  - Fabric 1.20.1 can verify Mojang and shows a client HUD, but sends no join
    chat because it has no post-login authentication-source consumer.
  - Fabric currently marks its HUD from client joinServer success before the
    server hasJoined result. Forge/NeoForge also predict their HUD in the
    client handshake, although their chat, audit, API state, and callbacks are
    based on the server's final post-login source.
  - Build success is not release approval. The target matrix is authoritative.

PHASE 1 — fix the Fabric result path first:

1. Introduce a bounded, expiring pending-login source in Fabric, keyed by the
   final player UUID, matching ForgeAdapterRuntime's security properties:
   maximum size, TTL, prune-on-read/write, cleanup on shutdown, and no static
   connection retention.

2. Record VERIFIED, OFFLINE_FALLBACK, and grace outcomes during login. Consume
   exactly once from ServerPlayConnectionEvents.JOIN after vanilla has created
   the player. Never derive the final source from the client's claim alone.

3. From that consumed server result, consistently perform:
   - the existing stable English audit log vocabulary;
   - localized chat using platform/common-assets keys;
   - optional title/subtitle;
   - live AccountStatus publication and callbacks once the Fabric addon API is
     added;
   - a server-confirmed client status update for the HUD.

4. Add Fabric config keys with the same names/defaults as Forge/NeoForge:
   showJoinFeedback=true, showJoinTitle=false, showAccountOverlay=true, plus
   overlayCorner/offsetX/offsetY/scale. Preserve existing JSON files: missing
   keys receive defaults; malformed or oversized files keep secure defaults.

5. Replace the current client-predicted HUD status with a server-authoritative
   play-phase payload. Define the loader-neutral status value in shared plain
   Java if useful, but keep Minecraft/Fabric networking in the adapter. Clear
   status on disconnect/world change. Do not let a client joinServer success
   display Premium when the server accepted offline fallback or denied login.

6. Apply the same server-authoritative HUD rule to Forge and NeoForge in small,
   separate commits after Fabric is proven. Their existing post-login source is
   already authoritative; add only the status transport and client update.

TESTS REQUIRED FOR PHASE 1:
  - pending state is bounded, expires, consumes once, and clears on shutdown;
  - verified/offline/grace outcomes map to the correct public status and
    translation keys;
  - showJoinFeedback=false suppresses chat without suppressing API/HUD state;
  - malformed client data cannot manufacture Premium status;
  - config compatibility/default tests;
  - focused builds for every source consumer, then the root build;
  - real Fabric 1.20.1 premium and offline runs with server audit + client chat
    + HUD evidence.

PHASE 2 — close existing feature parity before adding version modules:

Use Forge 1.20.1 as the feature reference, but preserve the shared security
boundary. Port in reviewable units:
  - Fabric AccountStatus API and callbacks;
  - Fabric allowlisted Yggdrasil/authlib-injector support (fail closed until
    complete; never trust a client-reported endpoint directly);
  - offline-to-verified migration and its admin commands to modern Forge,
    NeoForge, and Fabric, including confirmation, backups, and rollback;
  - any remaining config, feedback, skin-refresh, or API mismatch recorded in
    target-matrix.md.

Do not claim parity from matching method names. Add contract tests and real
runtime evidence. Preserve endpoint allowlisting, public-address rejection,
DNS pinning, TLS hostname verification, no redirects, response limits,
timeouts, cancellation, and the rule that access tokens never leave clients.

PHASE 3 — only after existing adapters pass the common contract, expand version
coverage in separate branches/sessions:

  Fabric: Minecraft 1.20.1 through 1.21.11.
  Forge:  Minecraft 1.20.1 through 1.21.11.
  NeoForge: Minecraft 1.20.2 through 1.21.11. Keep the existing 1.20.1
            best-effort module release-disabled; it is outside the requested
            public range and upstream recommends Forge for that patch.

The exact Minecraft patches are:
  1.20.1, 1.20.2, 1.20.3, 1.20.4, 1.20.5, 1.20.6,
  1.21, 1.21.1, 1.21.2, 1.21.3, 1.21.4, 1.21.5,
  1.21.6, 1.21.7, 1.21.8, 1.21.9, 1.21.10, 1.21.11.

Do not automatically create one module/JAR per patch. First verify current
official loader availability and APIs. Use the existing protocol clusters only
as candidates for one artifact range:
  - 1.20.3 + 1.20.4
  - 1.20.5 + 1.20.6
  - 1.21 + 1.21.1
  - 1.21.2 + 1.21.3
  - 1.21.7 + 1.21.8
  - 1.21.9 + 1.21.10

A range may be widened only after the exact production JAR passes a real
two-sided run on every claimed patch. Loader/API compatibility and matching
wire protocol are both required. Single-protocol patches remain independent:
1.20.1, 1.20.2, 1.21.4, 1.21.5, 1.21.6, and 1.21.11.

Each new target/range must be added together to settings.gradle, the root build,
release/targets.json (release:false), verify/self-test matrices, local dev-run
registration, structural JAR verification, and target-matrix.md. Fabric release
artifacts must be remapped build/libs JARs, never devlibs. Record exact loader,
JDK, mappings, artifact path/hash, and runtime evidence.

RELEASE GATE:
  Keep every target release:false until its declared-JDK build, shared fixtures,
  target tests, structural JAR checks, and full client/server acceptance matrix
  pass: Mojang success, allowed Yggdrasil success where supported, rejected
  endpoint, denial/missing token, malformed payload, timeout, disconnect,
  offline fallback, known-premium denial, grace, skin/UUID correctness, and
  migration confirmation/rollback where the feature exists.

COMMITS AND HANDOFF:
  - One behavior or target family per signed commit; no Co-Author trailer.
  - Preserve user changes and archive history.
  - Do not flip release flags or create tags/releases without explicit approval.
  - Update target-matrix.md as evidence lands.
  - Stop after the first reviewable phase and report changed files, tests,
    runtime evidence, remaining parity gaps, and the next branch/session.
```

---

The intended first deliverable is Fabric's authoritative feedback/status path,
not eighteen new Fabric modules. Version expansion comes after the behavior
being copied is trustworthy and consistent.
