# Historical plan: Forge version consolidation (1.20.1–1.20.6, 1.21–1.21.11)

**Do not execute this plan literally.** It preserves the original sequencing,
but Phases B, C, and D are implemented as of 2026-07-18. Forge also publishes
no 1.20.5 or 1.21.2 loader, which invalidates two original range assumptions.
Use [`target-matrix.md`](../architecture/target-matrix.md) for live state and
[`full-legacy-loader-coverage-handoff.md`](full-legacy-loader-coverage-handoff.md)
for the corrected end state.

## Objective and boundaries

The original objective was to extend Forge coverage from the then-current 6 modules (`forge-1.20.1`, `forge-1.21.1`,
`forge-1.21.3`, `forge-1.21.4`, `forge-1.21.5`, `forge-1.21.8`) to 12 modules
covering every Minecraft patch from 1.20.1–1.20.6 and 1.21–1.21.11, while
keeping the module *count* as low as the protocol-version evidence allows —
not one module per patch.

Start on `main` using `feature/forge-version-consolidation`. Every new module
starts **Planned** and `release: false`, exactly like every existing modern
Forge target. Do not add a new module to the root `build.gradle` aggregate,
CI matrices, or `release/targets.json` until it has its own standalone build
and focused tests passing — same rule `fabric-1.20.1-handoff.md` used.

Read before touching code:

```bash
git status --short --branch
git log --oneline -12
sed -n '1,300p' docs/architecture/version-consolidation-roadmap.md
sed -n '1,300p' docs/architecture/target-matrix.md
sed -n '1,240p' docs/development/adding-adapter.md
```

## Target list for this session

| Module | Action | Covers | Protocol |
|---|---|---|---|
| `forge-1.20.1` | unchanged | 1.20.1 | 763 (pre-config-phase island — never merges forward) |
| `forge-1.20.2` | implemented | 1.20.2 | 764 (solo) |
| `forge-1.20.4` | implemented; widen pending | 1.20.4 (1.20.3 only after exact runtime) | 765 |
| `forge-1.20.6` | implemented | 1.20.6 (no Forge 1.20.5 loader exists) | 766 |
| `forge-1.21.1` | **widen** | 1.21 + 1.21.1 | 767 |
| `forge-1.21.3` | **widen** | 1.21.2 + 1.21.3 | 768 |
| `forge-1.21.4` | unchanged | 1.21.4 | 769 (solo) |
| `forge-1.21.5` | unchanged | 1.21.5 | 770 (solo) |
| `forge-1.21.6` | implemented | 1.21.6 | 771 (solo — see the correction below) |
| `forge-1.21.8` | **widen** | 1.21.7 + 1.21.8 | 772 |
| `forge-1.21.10` | **new** | 1.21.9 + 1.21.10 | 773 |
| `forge-1.21.11` | **new** | 1.21.11 | 774 (solo) |

**The `forge-1.21.8` widen is a correction, not a pure extension.** Its
`mods.toml` comment today says to eventually widen to `[1.21.6,1.21.9)`.
Real protocol data (see the roadmap doc) shows 1.21.6 doesn't share a
protocol version with 1.21.7/1.21.8 — fix the comment to target
`[1.21.7,1.21.9)` and give 1.21.6 its own module. Do this fix in Phase A
below regardless of whether the wider ranges have login-run evidence yet.

## Phased implementation order

### Phase A — cheap corrections, no new modules

1. Fix the stale comment in `platform/forge-1.21.8/src/main/resources/META-INF/mods.toml`
   (see above). Do not flip the live `versionRange` yet — leave it exactly as
   `[1.21.8,1.21.9)` until 1.21.7 has its own login run, per the existing
   "Sharing a jar across patches" policy in `target-matrix.md`. Just correct
   what the comment says the eventual target is.
2. Add tracked-but-not-yet-live widen notes (a comment + a
   `target-matrix.md` row noting "declared range unchanged pending a login
   run on the newly-claimed patch") for:
   - `forge-1.21.1` → eventually also 1.21
   - `forge-1.21.3` → eventually also 1.21.2
   - `forge-1.21.8` → eventually also 1.21.7 (the corrected target from #1)

### Phase B — the pivot: `forge-1.20.2`

This is the first Forge target inside the configuration-phase era that isn't
`forge-1.20.1`, and the module every later new module in this session depends
on architecturally. Do this before Phase C.

1. Scaffold `platform/forge-1.20.2` the same way `forge-1.21.1` is scaffolded
   (own `build.gradle`, own `gradle.properties`-equivalent version
   constants, own `mods.toml`), targeting exact Forge 1.20.2 versions you
   look up fresh at `files.minecraftforge.net/net/minecraftforge/forge/index_1.20.2.html`
   — do not carry forward any version number from this doc as authoritative;
   verify it live.
2. Point its `sourceSets.main.java.srcDir` at `platform/forge-common/src/main/java`
   exactly like `forge-1.21.1/build.gradle` does, and attempt a build.
3. **Record precisely what fails to compile, if anything.** There are two
   possible outcomes and the doc deliberately does not predict which one you
   will hit:
   - **Compiles clean, or with a small per-module seam** (the same shape as
     `TrueuuidForgeEvents.java`'s EventBus import or `TrueuuidHudScale.java`'s
     GUI API): 1.20.2 joins the existing `forge-common` family. Proceed with
     Phase C using this recipe.
   - **Real divergence** (different FML/Forge event package version,
     different configuration-phase class names, a different ForgeGradle
     major version than the 1.21.x line uses): fork a new shared root
     `platform/forge-1.20x-common` under the *exact same contract*
     `forge-common`'s README states — "a file may live here only if it
     compiles unchanged against every module that includes it." Do not
     force incompatible source into `forge-common`; that just relocates the
     duplication problem instead of solving it. Update
     `docs/architecture/target-matrix.md`'s "Target axes" section to
     document the new root the same way `forge-common` is documented there.
4. Whichever outcome: get `forge-1.20.2` to compile, pass `shared/protocol`'s
   fixtures and its own focused unit tests, then run the real acceptance
   matrix from `adding-adapter.md` (Mojang success, Yggdrasil, denial,
   timeout, disconnect, migration rollback) before calling it anything past
   `Planned`.

### Phase C — `forge-1.20.4`, `forge-1.20.6`

Completed 2026-07-18 through shared `forge-common` legacy-matrix, overlay-era,
layered-draw, and packet-codec source roots. Exact production JAR runtime
acceptance remains in `forge-1.20x-runtime-handoff.md`.

Same recipe as whatever Phase B settled on (either `forge-common` or the new
`forge-1.20x-common`). `forge-1.20.4` targets 1.20.4's mappings and declares
`versionRange` covering 1.20.3 as well only once 1.20.3 also has its own
login run — same "prove before you widen" rule as everything else. Likewise
`forge-1.20.6` for the 1.20.5/1.20.6 pair.

### Phase D — `forge-1.21.6`

Completed 2026-07-18 through the source-only `modern-matrix` root; server boot
passes and client/login acceptance remains pending.

Lowest-risk module in the whole session: another spoke of the
already-proven, already-working 1.21.x `forge-common` family, same recipe as
the existing `forge-1.21.4`/`forge-1.21.5` modules. Do this whenever it's
convenient relative to Phase B/C — it has no dependency on the 1.20.x
architecture question.

### Phase E — `forge-1.21.10`, `forge-1.21.11`

Same recipe. Explicitly check for (don't assume the absence of) another
Forge/FML API seam by this point in the version line, the way EventBus 6→7
required `TrueuuidForgeEvents.java` to diverge at Forge 56, and the way the
1.21.5+ GUI pipeline required `TrueuuidHudScale.java` and
`TrueuuidClientOverlay.java` to diverge. If one exists, it gets its own
per-module file following that exact established pattern — do not try to
paper over a real API difference with conditional logic inside a single
shared file.

## Non-negotiable gates (same as every existing target)

Per `forge-roadmap.md`'s acceptance gates and `target-matrix.md`'s existing
framing: every module in this list must compile with its declared JDK, pass
`shared/protocol` fixtures and focused unit tests, keep all network/disk work
off the server thread, cancel login-owned work on timeout/disconnect, and
complete a real modded-client/modded-server login matrix (Mojang success,
allowed Yggdrasil success, denial, timeout, disconnect, migration rollback)
before `target-matrix.md` calls it anything beyond `Planned`. A widened
`versionRange` additionally requires that same matrix on **every** patch it
now claims, not just the patch it was built against.

Do not add any module to the root `build.gradle` `build` task's `dependsOn`
list, `.github/workflows/verify.yml`, `.github/workflows/self-test.yml`, or
`release/targets.json` until its own standalone Gradle build and tests pass —
follow the exact sequencing `fabric-1.20.1-handoff.md` used for Fabric.

## What this session does not do

- Does not touch NeoForge or Fabric modules — those are separate sessions
  ([`neoforge-1.20-1.21-consolidation-handoff.md`](neoforge-1.20-1.21-consolidation-handoff.md),
  [`fabric-1.20-consolidation-handoff.md`](fabric-1.20-consolidation-handoff.md)).
  Session 2 (NeoForge) depends on this session's Phase B outcome — leave
  clear notes in `target-matrix.md` and this doc's own commit about what was
  found, since the NeoForge session will very likely hit the same
  configuration-phase class-name questions under `net.neoforged` packages.
- Does not port the 1.20.1 feature backlog (migration, admin commands) to any
  new module. Every new module here is scoped to a login-verification core
  matching the existing 1.21.x line's current feature scope, not
  `forge-1.20.1`'s full feature set — that porting work is tracked separately
  in `target-matrix.md`'s "Feature parity" section and is out of scope here.
- Does not touch the `26.x` (2026 year-based) Minecraft version line. 1.21.11
  is confirmed the final legacy-scheme release; treat `26.x` as a future
  session's new versioning era, not an extension of this range.
