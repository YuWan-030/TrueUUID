# Version consolidation roadmap

This document records when one compiled adapter may claim adjacent Minecraft
patches. The live target inventory and evidence are in
[`target-matrix.md`](target-matrix.md).

## Current state

The release manifest contains 36 exact compile targets: 12 Forge, 12 Fabric,
and 12 NeoForge. All 36 passed the four-case core runtime matrix on 2026-07-22.
Metadata remains conservative and does not implicitly claim omitted patches.

The exact Fabric compile-patch expansion is complete. Adjacent candidate patches
still require their own matching client/server evidence before any metadata
range is widened. Follow the canonical [Fabric handoff](../development/fabric-1.20.1-1.21.11-handoff.md).
Forge 1.21.11 remains a separate Gradle 9.5 build island, but its own wrapper is
integrated into the manifest, CI, release workflow, and runtime harness.

## Candidate protocol clusters

Matching protocol versions identify candidates for one compiled JAR; they do
not establish loader/API or runtime compatibility.

| Cluster | Candidate Minecraft patches |
|---|---|
| 763 | 1.20.1 |
| 764 | 1.20.2 |
| 765 | 1.20.3, 1.20.4 |
| 766 | 1.20.5, 1.20.6 |
| 767 | 1.21, 1.21.1 |
| 768 | 1.21.2, 1.21.3 |
| 769 | 1.21.4 |
| 770 | 1.21.5 |
| 771 | 1.21.6 |
| 772 | 1.21.7, 1.21.8 |
| 773 | 1.21.9, 1.21.10 |
| 774 | 1.21.11 |

Forge has no published loader for Minecraft 1.20.5 or 1.21.2. Loader-specific
availability must be checked before attempting a candidate range.

## Widening rule

A target may widen its Minecraft version range only after all of the following:

1. the same production JAR passes structural verification;
2. a matching modded client and server boot on every exact patch claimed;
3. the full applicable login/feature matrix passes on every patch;
4. the target matrix records the loader build, artifact hash, and result; and
5. a maintainer explicitly approves the range and release state.

Compilation, protocol equality, or success on the module's compile patch does
not substitute for an exact-patch run. If an API seam prevents safe widening,
add a thin module/source-set seam instead of introducing version conditionals
into shared behaviour.

Minecraft 1.21.11 is the last legacy `1.x.y` patch considered by this roadmap.
Later year-based versions are a new compatibility era and require a separate
design and target plan.
