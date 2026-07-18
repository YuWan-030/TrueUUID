# Version consolidation roadmap

Status: written 2026-07-16, not yet executed. This is the canonical reference
for three planned future sessions:

1. [`forge-1.20-1.21-consolidation-handoff.md`](../development/forge-1.20-1.21-consolidation-handoff.md)
2. [`neoforge-1.20-1.21-consolidation-handoff.md`](../development/neoforge-1.20-1.21-consolidation-handoff.md)
3. [`fabric-1.20-consolidation-handoff.md`](../development/fabric-1.20-consolidation-handoff.md)

Read this document first; it holds the shared evidence and rules those three
docs all point back to instead of repeating.

## The problem this solves

Today TrueUUID ships 12 Gradle modules (`target-matrix.md`), each declaring a
`versionRange` that covers exactly one Minecraft patch, even in the parts of
the tree that already share source aggressively (`forge-common`,
`neoforge-1.21.1`'s privileged-module pattern, `fabric-common`). Diffing every
per-module file across the five existing `forge-1.21.x` modules confirms the
source layer is not the problem: only **2 files** genuinely diverge across
the whole family —

- `TrueuuidForgeEvents.java` (the `SubscribeEvent` import: EventBus 6 for
  Forge ≤ 55 vs EventBus 7 for Forge 56+), and
- `client/TrueuuidHudScale.java` (`PoseStack` vs `Matrix3x2fStack`, the
  1.21.5+ GUI pipeline change)

— everything else compiles unchanged from `platform/forge-common/src/main`
via `srcDir`. So extending version coverage without "flooding" the repo is
not a source-sharing problem to re-solve; it's a question of **which patches
can safely share one compiled jar**, via a widened `versionRange`, instead of
getting their own module.

## The evidence: Minecraft protocol version numbers

`target-matrix.md` already has a policy for this ("Sharing a jar across
patches"): a module may declare a wider `versionRange` than its build patch
only after a real two-sided login run passes on every patch it claims.
Protocol-identical adjacent patches are the objective way to find *candidates*
worth attempting that proof on — never a substitute for the proof itself.

Source: [minecraft.wiki protocol version table](https://minecraft.wiki/w/Protocol_version),
cross-checked against this repo's own already-shipped version numbers (which
match, e.g. `forge-1.21.3`/`neoforge-1.21.3` already cover exactly the 768
cluster's higher member).

| MC version | Protocol | Clusters with | Forge module today | NeoForge module today |
|---|---:|---|---|---|
| 1.20.1 | 763 | *(none — pre-configuration-phase, permanent island)* | `forge-1.20.1` | — |
| 1.20.2 | 764 | *(none — first configuration-phase patch)* | — | — |
| 1.20.3 | 765 | 1.20.4 | — | — |
| 1.20.4 | 765 | 1.20.3 | — | — |
| 1.20.5 | 766 | 1.20.6 | — | — |
| 1.20.6 | 766 | 1.20.5 | — | — |
| 1.21 | 767 | 1.21.1 | *(not declared — range starts at 1.21.1)* | *(not declared)* |
| 1.21.1 | 767 | 1.21 | `forge-1.21.1` `[1.21.1,1.21.2)` | `neoforge-1.21.1` `[1.21.1,1.21.2)` |
| 1.21.2 | 768 | 1.21.3 | *(not declared)* | *(not declared)* |
| 1.21.3 | 768 | 1.21.2 | `forge-1.21.3` `[1.21.3,1.21.4)` | `neoforge-1.21.3` `[1.21.3,1.21.4)` |
| 1.21.4 | 769 | *(none)* | `forge-1.21.4` | `neoforge-1.21.4` |
| 1.21.5 | 770 | *(none)* | `forge-1.21.5` | `neoforge-1.21.5` |
| 1.21.6 | 771 | *(none)* | — | — |
| 1.21.7 | 772 | 1.21.8 | *(not declared)* | *(not declared)* |
| 1.21.8 | 772 | 1.21.7 | `forge-1.21.8` `[1.21.8,1.21.9)` | `neoforge-1.21.8` `[1.21.8,1.21.9)` |
| 1.21.9 | 773 | 1.21.10 | — | — |
| 1.21.10 | 773 | 1.21.9 | — | — |
| 1.21.11 | 774 | *(none)* | — | — |

Two important, non-obvious findings from this table:

### 1. A correction to `forge-1.21.8`'s own source

`platform/forge-1.21.8/src/main/resources/META-INF/mods.toml` currently
carries this comment:

```
# hotfixes; widen this to "[1.21.6,1.21.9)" (and loaderVersion to "[56,)") only
# after a two-sided login run passes on those patches.
```

This is wrong. **1.21.6's protocol number (771) does not match 1.21.7/1.21.8's
(772).** Only 1.21.7 is a genuine wire-level match for the 1.21.8 build;
1.21.6 needs its own module. Session 1 must fix this comment (target
`[1.21.7,1.21.9)`, not `[1.21.6,1.21.9)`) and give 1.21.6 its own module
rather than trying to fold it in.

### 2. 1.21.11 is not an arbitrary ceiling

[Minecraft Wiki](https://minecraft.wiki/w/Java_Edition_1.21.11) and
[NeoForged's own 26.1 release notes](https://neoforged.net/news/26.1release/)
confirm 1.21.11 is **the final Java Edition release using the legacy
`1.x.y` version scheme** — Minecraft moves to a year-based `26.1`/`26.2`
scheme afterward, and NeoForge already has a `26.1` line live. The requested
ceiling of 1.21.11 is therefore the complete remaining legacy range, not a
number chosen arbitrarily. The `26.x` line is **explicitly out of scope**
for the three sessions this roadmap covers — a future session should treat it
as a new, separate versioning era (much like 1.20.1 → 1.20.2 or 1.20.1 was to
the rest of this repo), not an extension of any `1.21.x` module's range.

## Target module counts after all three sessions

| Loader | Today | After | Distinct MC patches covered |
|---|---:|---:|---|
| Forge | 6 modules | 12 modules | 18 (1.20.1–1.20.6, 1.21–1.21.11) |
| NeoForge | 5 modules | 12 modules | 18 (1.20.1–1.20.6, 1.21–1.21.11) |
| Fabric | 1 module | 4 modules | 6 (1.20.1–1.20.6) |

Full per-loader module lists, phase ordering, and architectural decisions
(new shared roots vs. reusing existing ones) live in each session's own
handoff doc — this document intentionally does not repeat them, to avoid the
two ever drifting out of sync.

## Decisions already made (do not re-litigate without new information)

1. **NeoForge 1.20.1 is in scope as a best-effort module**, despite
   [NeoForged's own docs](https://docs.neoforged.net/docs/gettingstarted/versioning/)
   recommending Forge instead of NeoForge on 1.20.1 (NeoForge only became
   fully independent starting at 1.20.2). It is the lowest-priority target in
   Session 2, built last, and stays `release: false` unless it genuinely
   proves solid — this is explicitly going against upstream's own guidance,
   so keep expectations low.
2. **Already-shipped modules get widened, not just left alone**, wherever the
   protocol table above supports it: `forge-1.21.1`/`neoforge-1.21.1` should
   eventually also declare bare `1.21`; `forge-1.21.3`/`neoforge-1.21.3`
   should eventually also declare `1.21.2`. Same login-run-before-widening
   gate as every new module — this is a widen, not a shortcut.

## The one rule every session must not skip

> A `versionRange` may only claim a second Minecraft patch after a real,
> recorded two-sided (modded client + modded server) login run passes on
> **every** patch it claims. Protocol-version matching (this document) tells
> you where that attempt is *likely* to succeed. It never substitutes for
> making the attempt. A compiling jar and a matching protocol number are not
> a support claim — see `target-matrix.md`'s existing framing of "Planned"
> vs. runtime-proven targets, which applies identically here.

Each session doc's first task is to add its new target rows to
`target-matrix.md` as `Planned`, exactly as
[`fabric-1.20.1-handoff.md`](../development/fabric-1.20.1-handoff.md)
already did for Fabric before that module existed.
