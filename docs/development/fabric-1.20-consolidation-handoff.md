# Handoff: Fabric version consolidation (1.20.1–1.20.6)

**This is a handoff document, not an implementation log.** It describes work
for a future session to plan and execute — nothing in this doc has been built
yet. Read [`version-consolidation-roadmap.md`](../architecture/version-consolidation-roadmap.md)
first for the shared protocol-version evidence and rules.

This session is independent of the Forge and NeoForge consolidation sessions
— Fabric shares nothing with either loader beyond `shared/protocol` (plain
Java) and `platform/common-assets` (loader-agnostic strings). It does not need
to wait on their outcomes, and its scope is deliberately narrower: **1.20.1
through 1.20.6 only.** The user's request did not extend Fabric into the
1.21.x line the way Forge/NeoForge were extended — do not add 1.21.x Fabric
targets under this handoff without a fresh scoping conversation.

## Objective and boundaries

Extend Fabric coverage from today's 1 module (`fabric-1.20.1`) to 4 modules
covering 1.20.1–1.20.6, using the same "cluster patches by protocol version,
don't multiply modules unnecessarily" approach as the Forge/NeoForge
sessions.

Start on `main` using `feature/fabric-version-consolidation`. Every new
module starts **Planned** and `release: false`, and — following
`fabric-1.20.1-handoff.md`'s own precedent — stays out of the root
`build.gradle` aggregate, CI matrices, and `release/targets.json` until its
standalone build and tests pass independently.

Read before touching code:

```bash
git status --short --branch
git log --oneline -12
sed -n '1,300p' docs/architecture/version-consolidation-roadmap.md
sed -n '1,300p' docs/architecture/target-matrix.md
sed -n '1,130p' docs/development/fabric-1.20.1-handoff.md
sed -n '1,60p' docs/development/next-agent-handoff.md
```

## Do this first: close `fabric-1.20.1`'s own gap before fanning out

`fabric-1.20.1` has **never had a login run recorded** — per
`target-matrix.md`'s per-target validation tracker it reached a server boot
in dev (`Done` on `127.0.0.1:25565`) but no client ever connected, Mojang or
otherwise. It's also still missing, relative to `forge-1.20.1`, the
migration/admin-command unit, join feedback (chat/title), and the addon API.

**Recommendation, not a hard requirement, but a strong one:** validate and
close as much of that gap as practical on `fabric-1.20.1` itself *before*
creating `fabric-1.20.2`/`.4`/`.6`. Those three new modules will each
recompile `fabric-common`'s source against their own Yarn mappings — if
`fabric-common` still has an unvalidated or incomplete login path, that gap
gets multiplied across 4 jars instead of fixed once. At minimum, get a real
two-sided Mojang login run recorded for `fabric-1.20.1` before treating any
new Fabric module as more than "compiles."

## Target list for this session

| Module | Action | Covers | Protocol |
|---|---|---|---|
| `fabric-1.20.1` | unchanged (but see above) | 1.20.1 | 763 (solo) |
| `fabric-1.20.2` | **new** | 1.20.2 | 764 (solo) |
| `fabric-1.20.4` | **new** | 1.20.3 + 1.20.4 | 765 |
| `fabric-1.20.6` | **new** | 1.20.5 + 1.20.6 | 766 |

Unlike the Forge/NeoForge lines, Fabric's version string in `fabric.mod.json`
is a **fixed value** today (`"minecraft": "1.20.1"`), not a range — confirm
whether Fabric Loader's metadata schema supports a genuine version-range
string (Fabric supports SemVer-style range syntax, e.g. `">=1.20.3 <1.20.5"`,
in `depends`) before assuming `fabric-1.20.4` can simply widen the same way a
Forge `mods.toml` `versionRange` does. If Fabric's metadata format makes
range declarations awkward or unreliable in practice, note that explicitly in
`target-matrix.md` rather than forcing a range string that technically
parses but hasn't been proven to gate correctly at launch time.

## Architecture: `fabric-common` is a true shared root, like `forge-common`

Per `next-agent-handoff.md`, `platform/fabric-common/src` already holds all
version-independent Fabric code — entrypoint, JSON config, offline policy,
verified-name registry, reconnect grace, session check, payload bounds, the
login transaction, and loader-agnostic tests — and every Fabric module adds
it via `srcDir`, recompiling against its own Minecraft/Yarn versions. No
module is privileged; this matches `forge-common`'s model, not
`neoforge-1.21.1`'s privileged-module model. That means the Forge session's
Phase B question ("does the shared root need to fork?") has a Fabric
equivalent, but the default expectation should be **less divergence risk**
than Forge/NeoForge's 1.20.1→1.20.2 boundary, because Fabric has no
configuration-phase-style protocol rewrite at any point in the 1.20.x line —
that rewrite is a Forge/NeoForge-specific (and vanilla-server-negotiation)
concern; Fabric's own login networking API did not undergo an equivalent
break between 1.20.1 and 1.20.6. Verify this by attempting the build, exactly
as Forge's session does — don't assume it from this paragraph alone.

Already-documented per-module (not `fabric-common`) seams to expect:

- `FabricLoginNetworking` — the `Identifier` constructor and client session
  API drift between Yarn mapping sets.
- Client HUD classes — `HudRenderCallback`'s signature is stable across
  1.20.1–1.20.6 (it changes at 1.21, which is out of scope here), but verify
  this rather than assuming it from this doc.
- The login mixin (`ServerLoginNetworkHandlerMixin` today) — mixin target
  method signatures are mapping-sensitive and must be re-verified per Yarn
  version.
- `fabric.mod.json` / `trueuuid.fabric.mixins.json` — metadata, not shared
  Java, by definition per-module.

## Phased implementation order

### Phase 0 — close the `fabric-1.20.1` gap (see above)

Not strictly blocking, but do this first if time allows.

### Phase A — `fabric-1.20.2`

1. Look up exact current Fabric Loader, Fabric API, Loom, and Yarn mapping
   versions for Minecraft 1.20.2 fresh (do not carry forward any version
   number from this doc or from `fabric-1.20.1`'s pinned versions as
   authoritative for a different Minecraft version).
2. Scaffold `platform/fabric-1.20.2` the same way `fabric-1.20.1` is
   scaffolded: own `build.gradle`, own `fabric.mod.json`, own mixins config,
   pulling `fabric-common`'s `src/main/java` and `src/test/java` via
   `srcDir`.
3. Attempt the build. Record what fails to compile, if anything, against the
   seam list above. Add or update per-module seam files following the exact
   existing pattern (e.g. a new `FabricLoginNetworking` for 1.20.2's Yarn
   mappings) rather than editing `fabric-common` to special-case a single
   version.
4. Add malformed/truncated/oversized-payload tests before touching the
   remote verifier, mirroring `fabric-1.20.1-handoff.md`'s own implementation
   order — this session is extending an existing pattern, not inventing a
   new one.
5. Compile, pass `shared/protocol` fixtures and focused unit tests, then run
   the full acceptance matrix from `fabric-1.20.1-handoff.md`'s "Required
   validation" section (Mojang success, allowed/rejected Yggdrasil,
   missing-token/denied/timeout/disconnect, offline fallback/known-name/
   recent-IP, migration confirmation/rollback, server + headless client
   bootstrap) before calling it past `Planned`.

### Phase B — `fabric-1.20.4`, `fabric-1.20.6`

Same recipe as Phase A, once per cluster. Each declares (or, per the
metadata-format caveat above, notes the limitation of declaring) coverage for
its lower-protocol sibling only after that sibling also has its own login
run.

## Non-negotiable gates

Same as `fabric-1.20.1-handoff.md`'s own "Required validation before support
status changes" section: JDK 21 Gradle launcher, `--release 17` target,
`remapJar` for any real Fabric-instance artifact, and the full real
client/server matrix recorded with exact logs, artifact, loader, and JDK
evidence before any target leaves `Planned`. No build or smoke result alone
authorizes a `release: true` flip.

## What this session does not do

- Does not extend Fabric into the 1.21.x line — out of scope per the user's
  original request; a future session would need its own scoping decision
  (Fabric's `HudRenderCallback` break at 1.21, mentioned above, is exactly
  the kind of thing that session would need to plan around).
- Does not touch Forge or NeoForge modules.
- Does not port the full 1.20.1 (Forge) feature backlog to any Fabric module
  beyond what Phase 0 recommends closing on `fabric-1.20.1` itself — the
  broader migration/admin-command/addon-API parity work stays tracked in
  `target-matrix.md`'s "Feature parity" section as its own project.
