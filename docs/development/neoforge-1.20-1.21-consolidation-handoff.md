# Handoff: NeoForge version consolidation (1.20.1–1.20.6, 1.21–1.21.11)

**This is a handoff document, not an implementation log.** It describes work
for a future session to plan and execute — nothing in this doc has been built
yet. Read [`version-consolidation-roadmap.md`](../architecture/version-consolidation-roadmap.md)
first for the shared protocol-version evidence and rules.

**Sequencing dependency: run this session after
[`forge-1.20-1.21-consolidation-handoff.md`](forge-1.20-1.21-consolidation-handoff.md)'s
Phase B has landed.** That session's first new-territory module,
`forge-1.20.2`, will have already answered whether the configuration-phase
era (1.20.2+) needs a new shared source root or can reuse an existing one.
This session will very likely hit the same question under `net.neoforged`
packages instead of `net.minecraftforge` — read that session's findings
before re-deriving them from scratch.

## Objective and boundaries

Extend NeoForge coverage from today's 5 modules (`neoforge-1.21.1`,
`neoforge-1.21.3`, `neoforge-1.21.4`, `neoforge-1.21.5`, `neoforge-1.21.8`) to
12 modules covering 1.20.1–1.20.6 and 1.21–1.21.11, mirroring the Forge
session's module count and phase structure — NeoForge and Forge 1.21.x are
already "in step" feature-wise per `target-matrix.md`, and there's no reason
for this consolidation to break that symmetry.

Start on `main` using `feature/neoforge-version-consolidation`. Every new
module starts **Planned** and `release: false`.

Read before touching code:

```bash
git status --short --branch
git log --oneline -12
sed -n '1,300p' docs/architecture/version-consolidation-roadmap.md
sed -n '1,300p' docs/architecture/target-matrix.md
git log --oneline --all -- platform/forge-1.20.2   # Session 1's findings, once it exists
```

## The architectural difference from Forge: NeoForge has a privileged module, not a shared root

`forge-common` is a true shared **source root** — no module is privileged,
every `forge-1.21.x` module is a peer that pulls the same source in via
`srcDir`. NeoForge's sharing model is different, and this session must not
assume it works the same way:

- `platform/neoforge-common/` holds **zero Java** — only `metadata.gradle`
  and `neoforge.mods.toml`.
- The real Java lives in `platform/neoforge-1.21.1/src/main` — that module is
  **privileged**. Editing it silently changes every later NeoForge module.
- Later modules (`neoforge-1.21.3`, `.4`, `.5`, `.8`) each set
  `adapterMain = "${rootDir}/platform/neoforge-1.21.1/src/main"` and compile
  that source against their own NeoForge/Minecraft version. `neoforge-1.21.8`
  additionally `exclude`s a `modernGuiSources` list from that shared source
  and supplies its own GUI file locally (`ClientAccountStatus.java`), because
  Minecraft 1.21.6+ switched `PoseStack` → `Matrix3x2fStack` for the badge
  draw — the same underlying API change Forge's `TrueuuidHudScale.java`
  handles, just via file-exclusion instead of an `-common` root.

## Target list for this session

| Module | Action | Covers | Protocol |
|---|---|---|---|
| `neoforge-1.20.1` | **new, best-effort, do last** | 1.20.1 | 763 |
| `neoforge-1.20.2` | **new** | 1.20.2 | 764 (solo) |
| `neoforge-1.20.4` | **new** | 1.20.3 + 1.20.4 | 765 |
| `neoforge-1.20.6` | **new** | 1.20.5 + 1.20.6 | 766 |
| `neoforge-1.21.1` | **widen** | 1.21 + 1.21.1 | 767 |
| `neoforge-1.21.3` | **widen** | 1.21.2 + 1.21.3 | 768 |
| `neoforge-1.21.4` | unchanged | 1.21.4 | 769 (solo) |
| `neoforge-1.21.5` | unchanged | 1.21.5 | 770 (solo) |
| `neoforge-1.21.6` | **new** | 1.21.6 | 771 (solo) |
| `neoforge-1.21.8` | **widen** | 1.21.7 + 1.21.8 | 772 |
| `neoforge-1.21.10` | **new** | 1.21.9 + 1.21.10 | 773 |
| `neoforge-1.21.11` | **new** | 1.21.11 | 774 (solo) |

The `neoforge-1.21.8` widen has the same correction Forge's session applies:
the eventual target is `[1.21.7,1.21.9)`, not `[1.21.6,1.21.9)` — 1.21.6 does
not share a protocol version with 1.21.7/1.21.8 (see the roadmap doc).
`neoforge-1.21.6` gets its own module.

### On `neoforge-1.20.1` specifically

This target exists only because the user explicitly approved a best-effort
attempt despite
[NeoForged's own docs](https://docs.neoforged.net/docs/gettingstarted/versioning/)
recommending Forge, not NeoForge, on 1.20.1 — NeoForge only became fully
independent from Forge starting at 1.20.2. Build this **last**, after every
other target in this list is settled, keep it `release: false` regardless of
build/test outcome, and only invest further effort if it genuinely proves
solid rather than fighting the toolchain. If NeoForge tooling itself makes
1.20.1 impractical (e.g. no published NeoForge artifact actually targets it,
as opposed to just being discouraged), document that finding in
`target-matrix.md` and stop — this was flagged as the lowest-priority, most
speculative target in the whole 3-session plan.

## Phased implementation order

### Phase A — cheap corrections, no new modules

Same shape as Forge's Phase A: fix the `neoforge-1.21.8` mods.toml comment
(if one exists analogous to Forge's — check
`platform/neoforge-1.21.8/build.gradle`'s `neoforgeMetadata` block and any
adjacent comments) to target `[1.21.7,1.21.9)`, and leave tracked-but-not-yet-live
widen notes for `neoforge-1.21.1` (→ also 1.21) and `neoforge-1.21.3` (→ also
1.21.2), pending login runs on those newly-claimed patches.

### Phase B — `neoforge-1.20.2`: decide the shared-root question

Read Session 1's `forge-1.20.2` findings first (git log / the Forge handoff
doc's own notes once it's been executed). The concrete question:

1. Try pointing a new `neoforge-1.20.2` module's `adapterMain` at
   `platform/neoforge-1.21.1/src/main`, the same way `neoforge-1.21.8` does,
   and attempt a build against real NeoForge 1.20.2 versions (look these up
   fresh at `maven.neoforged.net` — do not carry forward any version number
   from this doc as authoritative). NeoForge's versioning scheme is
   `<MC-minor>.<MC-patch>.<build>`, e.g. `21.8.9` for 1.21.8, so expect
   something in the `20.2.x` line.
2. If it compiles clean or with a small file-exclusion seam (matching the
   `modernGuiSources` pattern): 1.20.2 reuses the existing privileged module.
3. If real divergence shows up: `neoforge-1.20.2` becomes its **own**
   privileged module for the 1.20.x sub-line (mirroring how `forge-1.20.1` is
   architecturally separate from the `forge-common` family), and
   `neoforge-1.20.4`/`neoforge-1.20.6` point their `adapterMain` at it
   instead of at `neoforge-1.21.1`. Document whichever outcome in
   `target-matrix.md`'s "Modern NeoForge code sharing" section.
4. Either way: compile, pass `shared/protocol` fixtures and focused unit
   tests, then run the full acceptance matrix before calling it past
   `Planned`.

### Phase C — `neoforge-1.20.4`, `neoforge-1.20.6`

Same recipe as whatever Phase B settled on.

### Phase D — `neoforge-1.21.6`

Lowest-risk module: another target recompiling the existing, proven
`neoforge-1.21.1` privileged source, same recipe as the existing
`neoforge-1.21.4`/`neoforge-1.21.5` modules.

### Phase E — `neoforge-1.21.10`, `neoforge-1.21.11`

Same recipe. Check for a new GUI/API seam the way `modernGuiSources` exists
for 1.21.6+ today — do not assume the 1.21.5+ break is the last one in the
line.

### Phase F — `neoforge-1.20.1` (best-effort, last)

See "On `neoforge-1.20.1` specifically" above.

## Non-negotiable gates

Identical to the Forge session's: compile with declared JDK, pass shared
fixtures and focused unit tests, keep network/disk work off the server
thread, cancel login-owned work on timeout/disconnect, and complete a real
two-sided login matrix (Mojang success, allowed Yggdrasil, denial, timeout,
disconnect, migration rollback) before any module or widened range leaves
`Planned`. Do not add a module to the root aggregate build, CI workflows, or
`release/targets.json` until its standalone build and tests pass
independently.

## What this session does not do

- Does not touch Forge or Fabric modules.
- Does not port the 1.20.1 (Forge) feature backlog (migration, admin
  commands) to any new NeoForge module — every target here stays scoped to
  the existing NeoForge login-verification core, matching Forge 1.21.x's
  current feature scope. That's tracked separately in `target-matrix.md`.
- Does not touch the `26.x` Minecraft version line (see the roadmap doc's
  note on 1.21.11 being the final legacy-scheme release) — NeoForge already
  has a `26.1` line live upstream, but bringing TrueUUID onto it is a
  separate future session with its own versioning-era decisions, not part of
  this consolidation.
