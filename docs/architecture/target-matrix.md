# Target and release matrix

TrueUUID is one multi-target repository. A target is an exact loader and
Minecraft adapter declared in `release/targets.json`; a branch, directory, or
wide metadata range is not a support claim.

## Current verdict

On 2026-07-22, all 24 targets in `release/targets.json` completed the same
installed-JAR client/server core acceptance set:

- verified Mojang premium join;
- policy-approved offline fallback;
- confirmed offline-to-premium data migration; and
- denial of offline reuse of a previously verified premium name.

The harness rebuilt and snapshotted each target artifact, created a unique
fresh world, booted one matching server per target, and reused that server only
for the four dependency-ordered scenarios. The consolidated result contains
96 passing target/scenario pairs. It is stored locally under the ignored
`build/runtime-acceptance/20260722T024059Z/summary.tsv`; `REUSED_PASS` entries
point to the earlier run containing the original logs and artifact snapshot.

This proves the four core paths for the exact targets below. It does **not**
prove every loader on every Minecraft patch from 1.20.1 through 1.21.11:

- Fabric currently has only the accepted 1.20.1 target.
- Forge 1.21.11 builds as a standalone Gradle 9.5 island, but is not in the
  root manifest, release pipeline, or four-scenario runtime matrix.
- Patch versions absent from the table are not implicitly covered. A range may
  be widened only after each claimed patch passes its own runtime checks.
- Yggdrasil/skin-site login, timeout/disconnect, recent-IP grace, migration
  rejection/timeout/rollback, admin commands, addon callbacks, and skin refresh
  are implemented or unit-tested as described below, but were not exercised by
  this four-scenario run.

For those reasons all `release` flags remain `false`. The runtime result is a
support milestone, not permission to publish untested feature paths.

## Declared targets

“Core accepted” means all four scenarios above passed for that exact target.
Every listed target is built and structurally checked by the root project.

| Target ID | Loader version | Java | Runtime state | Release |
|---|---:|---:|---|---:|
| `forge-1.20.1` | Forge 47.4.10 | 17 | Core accepted | false |
| `forge-1.20.2` | Forge 48.1.0 | 17 | Core accepted | false |
| `forge-1.20.4` | Forge 49.2.8 | 17 | Core accepted | false |
| `forge-1.20.6` | Forge 50.2.9 | 21 | Core accepted | false |
| `forge-1.21.1` | Forge 52.1.0 | 21 | Core accepted | false |
| `forge-1.21.3` | Forge 53.1.0 | 21 | Core accepted | false |
| `forge-1.21.4` | Forge 54.1.14 | 21 | Core accepted | false |
| `forge-1.21.5` | Forge 55.1.10 | 21 | Core accepted | false |
| `forge-1.21.6` | Forge 56.0.9 | 21 | Core accepted | false |
| `forge-1.21.8` | Forge 58.1.0 | 21 | Core accepted | false |
| `forge-1.21.10` | Forge 60.1.11 | 21 | Core accepted | false |
| `fabric-1.20.1` | Fabric Loader 0.19.3 | 17 | Core accepted | false |
| `neoforge-1.20.1` | NeoForge 47.1.106 | 17 | Core accepted | false |
| `neoforge-1.20.2` | NeoForge 20.2.93 | 17 | Core accepted | false |
| `neoforge-1.20.4` | NeoForge 20.4.251 | 17 | Core accepted | false |
| `neoforge-1.20.6` | NeoForge 20.6.139 | 21 | Core accepted | false |
| `neoforge-1.21.1` | NeoForge 21.1.213 | 21 | Core accepted | false |
| `neoforge-1.21.3` | NeoForge 21.3.56 | 21 | Core accepted | false |
| `neoforge-1.21.4` | NeoForge 21.4.121 | 21 | Core accepted | false |
| `neoforge-1.21.5` | NeoForge 21.5.74 | 21 | Core accepted | false |
| `neoforge-1.21.6` | NeoForge 21.6.20-beta | 21 | Core accepted | false |
| `neoforge-1.21.8` | NeoForge 21.8.9 | 21 | Core accepted | false |
| `neoforge-1.21.10` | NeoForge 21.10.64 | 21 | Core accepted | false |
| `neoforge-1.21.11` | NeoForge 21.11.44 | 21 | Core accepted | false |

## Feature parity

The adapters share the same security and behavioural spine wherever their
loader APIs permit it. “Implemented” is a source/build claim; only rows marked
“core runtime accepted” were exercised across all 24 targets in the latest
matrix.

| Feature | Forge targets | Fabric 1.20.1 | NeoForge targets | Evidence level |
|---|---|---|---|---|
| Mojang premium verification | yes | yes | yes | core runtime accepted |
| Offline fallback policy | yes | yes | yes | core runtime accepted |
| Persisted known-name denial | yes | yes | yes | core runtime accepted |
| Confirmed data migration | yes | yes | yes | core runtime accepted |
| Localized join feedback and HUD | yes | yes | yes | join observed; visual/API details not asserted on every target |
| Addon account-status API/callbacks | yes | yes | yes | build and unit tests |
| Allowlisted Yggdrasil/skin-site verification | yes | yes | yes | build and unit tests; runtime pending |
| Migration rejection/timeout/rollback | yes | yes | yes | shared/adapter unit tests; runtime pending |
| Admin migration/cleanup commands | yes | yes | yes | build-tested; runtime pending |
| Timeout/disconnect cancellation | yes | yes | yes | unit-tested; runtime pending |
| Recent-IP reconnect grace | yes | yes | yes | unit-tested; runtime pending |
| Skin refresh after join | yes | yes | yes | build-tested; runtime pending |

The login protocol and filesystem migration engine remain plain Java under
`shared/protocol`. Minecraft profiles, packets, commands, world paths, loader
lifecycle, and server-thread scheduling stay in the platform adapters. Forge
targets recompile `platform/forge-common` with narrow SRG/official,
event-bus, GUI, record, and identifier-era seams. NeoForge targets recompile
the 1.21 adapter baseline with equally narrow version seams. Fabric 1.20.1
recompiles `platform/fabric-common` against Yarn/Fabric APIs.

## Targets outside the manifest

| Target | State | Required before support/release |
|---|---|---|
| Forge 1.21.11 | Standalone ForgeGradle 7 / Gradle 9.5 build island; build-tested only | Add an explicit external-build manifest/CI contract, structural JAR verification, and the full runtime matrix |
| Fabric 1.20.2-1.21.11 | Not implemented | Follow the canonical [Fabric expansion handoff](../development/fabric-1.20.1-1.21.11-handoff.md) |
| Forge 1.12.2 | Deferred legacy target | Isolated JDK 8 build plus frozen protocol compatibility and runtime evidence |

Forge publishes no loader for 1.20.5 or 1.21.2. Other omitted patches and
candidate widened ranges remain unsupported until exact-patch runtime evidence
exists. See [version consolidation](version-consolidation-roadmap.md).

## Release gate

Before changing a target to `release: true`, record all applicable real
client/server scenarios: Mojang, allowed Yggdrasil, denial, timeout,
disconnect, offline fallback, grace, migration accept/reject/timeout/rollback,
admin commands, addon status/callbacks, and skin refresh. Then run the target's
declared-JDK build, shared fixtures, focused tests, release-JAR structural
verification, and obtain explicit maintainer approval.

Publishing mechanics are documented in
[release automation](../development/release-automation.md).
