# Kickoff prompt: sync features across versions and loaders

Paste the block below to start the parity work. It is deliberately scoped to one
feature at a time — porting several at once across three loaders produces a diff
nobody can review or bisect.

---

```text
Close the TrueUUID feature-parity gap between forge-1.20.1 (the complete,
runtime-proven reference) and the newer adapters.

READ FIRST, in this order. Do not trust any summary over the code:
  - docs/development/next-agent-handoff.md   (state, build env, and 8 traps)
  - docs/architecture/target-matrix.md       ("Feature parity" table)
  - platform/forge-common/README.md          (seam map)

BUILD ENV (non-negotiable): the default `java` is 25 and Gradle cannot run on it.
  export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
  ./gradlew build --continue     # 13 modules must stay green

KNOW WHERE CODE LANDS — the three roots are NOT the same shape:
  - platform/forge-common      -> source root; one edit hits all 5 Forge 1.21.x.
  - platform/neoforge-1.21.1   -> PRIVILEGED source; 1.21.3/4/5/8 srcDir into it,
                                  so one edit silently hits all 5 NeoForge targets.
                                  (platform/neoforge-common is metadata only.)
  - platform/common-assets     -> shared lang for both loaders. Any new
                                  user-facing string goes here, never inline.
  - platform/fabric-1.20.1     -> standalone. Do NOT create fabric-common: there
                                  is only one Fabric module, so it would be pure
                                  indirection. Create it when a 2nd one lands.
  - platform/forge-1.20.1      -> independent island. Read it as the reference;
                                  only change it when the task says so.

WORK ONE ITEM AT A TIME, in this order. After each: build all 13 modules, then
commit with a short imperative message (no co-author trailer).

  1. Fabric offline policy (SECURITY, do this first).
     Fabric completes its offline fallback unconditionally, so a name already
     proven premium can be taken by an unverified client. Port
     OfflineFallbackPolicy + a persisted verified-name registry, and gate the
     fallback on it, matching ForgeAdapterRuntime.canUseOfflineFallback. Fabric
     has no config class at all, so the policy options need a home first.

  2. Fabric strings.
     Point fabric-1.20.1 at platform/common-assets, delete its duplicate two-key
     lang copy, and convert its Text.literal("TrueUUID ...") disconnects to
     trueuuid.disconnect.* keys. Its text agrees with the shared file today only
     by luck. No wording decision needed - it is a strict subset with no conflicts.

  3. Skin-site / Yggdrasil on the 1.21 line.
     auth.yggdrasil.apiRootWhitelist is INERT there and FAILS OPEN: the clients
     always answer with an empty customEndpoint, so it silently falls back to
     Mojang while the config implies otherwise. The wire protocol already carries
     customEndpoint; only the client half is missing. Port the resolution from
     forge-1.20.1's ClientHandshakeMixin (-javaagent:authlib-injector argument +
     YggdrasilMinecraftSessionService.CHECK_URL reflection) into the 1.21 client
     mixins. Keep the server-side allowlist, DNS pinning and no-redirect checks
     exactly as they are. If it cannot be finished, make it fail CLOSED like
     Fabric rather than leave it failing open.

  4. AccountStatus API onto forge-1.20.1.
     1.20.1 still exposes only isPremium(name)/getPremiumUuid(name). Port
     api/AccountStatus + the getStatus/registerLoginCallback surface from
     forge-common so every target has one API.

  5. Remaining 1.20.1 features, one commit each, forge-common first (5 targets at
     once), then neoforge-1.21.1, then Fabric:
     configurable timeoutMs/allowOfflineOnTimeout (currently hardcoded 30s
     server / 25s client), debug toggle, recent-IP grace, skin refresh after
     join, admin commands, then offline->premium migration (largest; it needs the
     confirm screen and transactional rollback - treat as its own project).

DO NOT:
  - unify forge-1.20.1's lang onto common-assets. It has ~34 extra keys and three
    shared keys whose MEANING differs (its auth_denied conflates offline-denied
    with verify-failure; the 1.21 line splits those into auth_denied and
    bound_premium). That needs a human wording decision - surface it, don't guess.
  - use remap = false on ordinary vanilla methods (only Forge-preserved login
    methods), or trust Forge's *-sources.jar for API availability - javap the
    forge-<mc>-<ver>_mapped_official_<mc>.jar.
  - replace the badge's fill-drawn pixel art with a blit texture: blit and pose()
    diverge three ways across 1.21.1/1.21.4/1.21.8.
  - weaken the security boundary: the access token never leaves the client, and
    the server keeps bounded hasJoined verification with the endpoint allowlist,
    DNS pinning, TLS hostname verification, no redirects, and response limits.
  - claim any target is supported because it builds. Only a real two-sided login
    run (premium needs a real launcher; a dev runClient uses a dummy token and
    exercises the offline path only) moves a target off Planned in
    release/targets.json and target-matrix.md.

REPORT: what landed, what each target gained, what is still missing, and any
decision you need from me. Update the "Feature parity" table in
docs/architecture/target-matrix.md as things land.
```

---

## Why this order

1 and 2 are Fabric correctness and cost little. 3 is the biggest user-visible
gap and is actively misleading today, because the config advertises support that
silently does not exist. 4 is small and removes an awkward asymmetry where the
only runtime-proven target has the oldest API. 5 is the long tail, ordered
cheapest-first, with migration last because it is a project in itself.
