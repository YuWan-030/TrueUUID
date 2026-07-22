# Target and release matrix

TrueUUID is one multi-target repository. A target is an exact loader and
Minecraft adapter declared in `release/targets.json`; a branch, directory, or
wide metadata range is not a support claim.

## Current verdict

The 2026-07-22 HUD/join-audit consolidation worktree changes account-status
transport, rendering, pause-screen hooks, notification routing, shared login
utilities, and shared JAR contents. Its shared and adapter tests pass, all 35
root-project targets plus the standalone Forge 1.21.11 build island complete,
and all 36 production JARs pass structural, metadata, shared-class, Mixin, and
filename verification. The source-sharing validator also finds no exact Java
copies or version-module donor trees across the 165 platform Java sources.

The final installed-JAR matrix for the consolidated multiplayer login runtime
accepted all 144 target/scenario pairs. It recorded 140 fresh `PASS` results in
`build/runtime-acceptance/20260722T114302Z/summary.tsv` and four
`REUSED_PASS` results for Forge 1.21.6 from the immediately preceding focused
run in `20260722T114109Z`; the resumed rows point to that exact artifact and
evidence directory. No failed or incomplete result was reused.

The subsequent private-singleplayer/Premium-(LAN) presentation fix changes no
wire protocol, authentication decision, migration, or dedicated-server path.
Its shared transition/policy tests, representative old/new loader probes, full
36-target aggregate build, and all 36 release-JAR checks pass. The installed
matrix was not repeated after this local integrated-world-only change.

No visual assertion has yet confirmed the three-second fade, user-supplied
Singleplayer lock artwork, configured corners/scales/offsets, or pause-menu
placement on every GUI era. Operator-only delivery and non-operator exclusion
are covered by plain routing tests and full-target compilation but still
require installed-server runtime observation.

On 2026-07-22, all 36 targets in `release/targets.json` completed the same
installed-JAR client/server core acceptance set against the consolidated
shared-runtime worktree:

- verified Mojang premium join;
- policy-approved offline fallback;
- confirmed offline-to-premium data migration; and
- denial of offline reuse of a previously verified premium name.

The harness rebuilt and snapshotted each target artifact, created a unique
fresh world, booted one matching server per target, and reused that server only
for the four dependency-ordered scenarios. The final summary is
`build/runtime-acceptance/20260722T114302Z/summary.tsv`; each target directory
contains its tested JAR, SHA-256, server log, and per-scenario client evidence.
Across the current manifest there are 144 accepted target/scenario pairs.

This proves the four core paths for the exact targets below. It does **not**
prove every loader on every Minecraft patch from 1.20.1 through 1.21.11:

- Fabric has accepted exact compile targets for 1.20.1, 1.20.2, 1.20.4,
  1.20.6, 1.21.1, 1.21.3, 1.21.4, 1.21.5, 1.21.6, 1.21.8, 1.21.10, and
  1.21.11. This does not implicitly cover 1.20.3, 1.20.5, 1.21, 1.21.2,
  1.21.7, or 1.21.9.
- Forge has accepted exact targets for the same published patch set through
  1.21.11. The 1.21.11 adapter remains a standalone Gradle 9.5 build island,
  but the manifest, CI, runtime harness, and release pipeline invoke its own
  wrapper explicitly.
- Patch versions absent from the table are not implicitly covered. A range may
  be widened only after each claimed patch passes its own runtime checks.
- Yggdrasil/skin-site login, timeout/disconnect, recent-IP grace, migration
  rejection/timeout/rollback, admin commands, addon callbacks, and skin refresh
  are implemented or unit-tested as described below, but were not exercised by
  this four-scenario run.

For version 1.2.0, the maintainer explicitly approved all 36 exact targets for
publication after the core runtime matrix, aggregate builds, unit tests, and
release-JAR checks passed. Every `release` flag is therefore `true` and the
approval is bound to `release_version: 1.2.0`. The extended paths above remain
documented evidence limitations; approval does not turn them into runtime-tested
claims or extend support to omitted Minecraft patches.

## Declared targets

“Core accepted” means all four scenarios above passed for that exact target.
Every listed target is built and structurally checked by CI. The aggregate
`scripts/ci/build-all-targets.sh` command builds the 35 root modules and then
the standalone Forge 1.21.11 Gradle 9.5 target.

| Target ID | Loader version | Java | Runtime state | Release |
|---|---:|---:|---|---:|
| `forge-1.20.1` | Forge 47.4.10 | 17 | Core accepted | true |
| `forge-1.20.2` | Forge 48.1.0 | 17 | Core accepted | true |
| `forge-1.20.4` | Forge 49.2.8 | 17 | Core accepted | true |
| `forge-1.20.6` | Forge 50.2.9 | 21 | Core accepted | true |
| `forge-1.21.1` | Forge 52.1.0 | 21 | Core accepted | true |
| `forge-1.21.3` | Forge 53.1.0 | 21 | Core accepted | true |
| `forge-1.21.4` | Forge 54.1.14 | 21 | Core accepted | true |
| `forge-1.21.5` | Forge 55.1.10 | 21 | Core accepted | true |
| `forge-1.21.6` | Forge 56.0.9 | 21 | Core accepted | true |
| `forge-1.21.8` | Forge 58.1.0 | 21 | Core accepted | true |
| `forge-1.21.10` | Forge 60.1.11 | 21 | Core accepted | true |
| `forge-1.21.11` | Forge 61.1.9 | 21 | Core accepted | true |
| `fabric-1.20.1` | Fabric Loader 0.19.3 | 17 | Core accepted | true |
| `fabric-1.20.2` | Fabric Loader 0.19.3 | 17 | Core accepted | true |
| `fabric-1.20.4` | Fabric Loader 0.19.3 | 17 | Core accepted | true |
| `fabric-1.20.6` | Fabric Loader 0.19.3 | 21 | Core accepted | true |
| `fabric-1.21.1` | Fabric Loader 0.19.3 | 21 | Core accepted | true |
| `fabric-1.21.3` | Fabric Loader 0.19.3 | 21 | Core accepted | true |
| `fabric-1.21.4` | Fabric Loader 0.19.3 | 21 | Core accepted | true |
| `fabric-1.21.5` | Fabric Loader 0.19.3 | 21 | Core accepted | true |
| `fabric-1.21.6` | Fabric Loader 0.19.3 | 21 | Core accepted | true |
| `fabric-1.21.8` | Fabric Loader 0.19.3 | 21 | Core accepted | true |
| `fabric-1.21.10` | Fabric Loader 0.19.3 | 21 | Core accepted | true |
| `fabric-1.21.11` | Fabric Loader 0.19.3 | 21 | Core accepted | true |
| `neoforge-1.20.1` | NeoForge 47.1.106 | 17 | Core accepted | true |
| `neoforge-1.20.2` | NeoForge 20.2.93 | 17 | Core accepted | true |
| `neoforge-1.20.4` | NeoForge 20.4.251 | 17 | Core accepted | true |
| `neoforge-1.20.6` | NeoForge 20.6.139 | 21 | Core accepted | true |
| `neoforge-1.21.1` | NeoForge 21.1.213 | 21 | Core accepted | true |
| `neoforge-1.21.3` | NeoForge 21.3.56 | 21 | Core accepted | true |
| `neoforge-1.21.4` | NeoForge 21.4.121 | 21 | Core accepted | true |
| `neoforge-1.21.5` | NeoForge 21.5.74 | 21 | Core accepted | true |
| `neoforge-1.21.6` | NeoForge 21.6.20-beta | 21 | Core accepted | true |
| `neoforge-1.21.8` | NeoForge 21.8.9 | 21 | Core accepted | true |
| `neoforge-1.21.10` | NeoForge 21.10.64 | 21 | Core accepted | true |
| `neoforge-1.21.11` | NeoForge 21.11.44 | 21 | Core accepted | true |

## Feature parity

The adapters share the same security and behavioural spine wherever their
loader APIs permit it. “Implemented” is a source/build claim; only rows marked
“core runtime accepted” were exercised across all 36 declared targets in the
latest evidence set.

| Feature | Forge targets | Fabric targets | NeoForge targets | Evidence level |
|---|---|---|---|---|
| Mojang premium verification | yes | yes | yes | core runtime accepted |
| Offline fallback policy | yes | yes | yes | core runtime accepted |
| Persisted known-name denial | yes | yes | yes | core runtime accepted |
| Confirmed data migration | yes | yes | yes | core runtime accepted |
| Localized join feedback and HUD | yes | yes | yes | join observed; visual/API details not asserted on every target |
| Three-second fading badge and pause-menu lock badge | implemented | implemented | implemented | shared fake-clock tests and full-target build; visual runtime pending |
| Singleplayer and Premium (LAN) chat/HUD/pause transitions | implemented | implemented | implemented | shared transition/policy tests and full-target build; visual runtime pending |
| Structured login audit and operator-only notification | implemented | implemented | implemented | routing unit tests and full-target build; operator runtime pending |
| Addon account-status API/callbacks | yes | yes | yes | build and unit tests |
| Allowlisted Yggdrasil/skin-site verification | yes | yes | yes | build and unit tests; runtime pending |
| Migration rejection/timeout/rollback | yes | yes | yes | shared/adapter unit tests; runtime pending |
| Admin migration/cleanup commands | yes | yes | yes | build-tested; runtime pending |
| Timeout/disconnect cancellation | yes | yes | yes | unit-tested; runtime pending |
| Recent-IP reconnect grace | yes | yes | yes | unit-tested; runtime pending |
| Skin refresh after join | yes | yes | yes | build-tested; runtime pending |

The login protocol, bounded pending-result storage, fallback policy, migration
locks, persistent verified-name store, `hasJoined` response parser, endpoint
discovery, safe diagnostics, and filesystem migration engine remain plain Java
under `shared/protocol`; loader-neutral status timing, artwork, layout,
presentation values, and notification routing live in
`shared/presentation`. Minecraft profiles, packets, commands, world paths,
loader lifecycle, and server-thread scheduling stay in platform adapters. Forge
targets recompile `platform/forge-common` with narrow SRG/official, event-bus,
GUI, record, and identifier-era seams. NeoForge targets recompile the canonical
`platform/neoforge-common` core with equally narrow named era roots. Minecraft
seams shared by both live in `platform/forgelike-common`. Fabric targets
recompile `platform/fabric-common` against their pinned Yarn/Fabric APIs, with
small source roots for session joining, typed payloads, authlib records,
permissions, identifiers, and HUD matrix transitions.

`scripts/ci/validate-source-sharing.py` rejects exact Java source copies,
including tests, and version-module source donors before target or release
validation can pass.

The table below is retained as historical, pre-consolidation Fabric evidence;
it is not the release approval for the current worktree. Current hashes and
acceptance snapshots for every loader live under
`build/runtime-acceptance/20260722T114302Z/<target>/artifact/` (with the exact
Forge 1.21.6 focused artifact under `20260722T114109Z`). The harness removes
each snapshot source from the module's normal `build/libs` path. Normal builds
compile the release hook implementation, and release-JAR verification rejects
acceptance environment names or packaged scripts.

| Target | Release JAR SHA-256 | Acceptance snapshot SHA-256 | Core evidence |
|---|---|---|---|
| `fabric-1.20.1` | `05b6229163339c9982b5ddc97073eb6b00e38c4db34458f8b73aa59b840b13fd` | `457dcb4a96a121523d3ac0ff65da77deefd0a7688ec0f6c6baeaa48a08e1b8db` | `20260722T042021Z` |
| `fabric-1.20.2` | `acf0a69cc3e7ab43e8c4cf95062238aa5ede329a089543a3d22db45201bb00a7` | `5b5400e9cb0a4a857bf8c9ef8e42bce97d75fa6ec2dc4c30e89dd21447647b9f` | `20260722T042021Z` |
| `fabric-1.20.4` | `b0abed4f05bb0fb66504d34ae930a534034cb2b16ef62854fc20be300aa56c0f` | `9807910275578225848a91e13af030ac5d4dcb6c37043d2a3f51dcf7eceb87f6` | `20260722T042021Z` |
| `fabric-1.20.6` | `23fc04dc45b37fcd3a4209050bca6e713d06de4c90747b6efa5aa66cd7deeeb2` | `d1beaa9c4b53799c0f2cb74d69eac51a93d829fad7b79fc4e9231a514cb26433` | `20260722T042021Z` |
| `fabric-1.21.1` | `ffe5285ee0e6686f0fc057a49fac90334ddaf18c64c0caa9197b41ce81aad7a3` | `7eb9000f59fd453a6d3716053e4a6e604a4c0ba41f81949f88244246fb01ff12` | `20260722T043906Z` |
| `fabric-1.21.3` | `1a994f7be7633b7116616e68dd1eddf4ffcfeaa26e8e4b2eac1ff081b4979d93` | `8b9e71321e37598c7f23523e196ef871cb9646a4da8c4a30179ca0bb37368436` | `20260722T051512Z` |
| `fabric-1.21.4` | `3048d0dbcc706cc849b0596822f81af84240979c983e7965a9250f88cc6bc317` | `99449045d81e8451129469fac1826be75c08316fa4aef18302881cfbef6a88a7` | `20260722T051512Z` |
| `fabric-1.21.5` | `c8b6e8d8e70bb5a6e83e0ca7f5293f4d07356d338469b2e36f93d9bfa84c74d2` | `dad38437c01ee8c14b4083a35653f6a5619531227c5dd1d831bfb0a9ed08c04c` | `20260722T051512Z` |
| `fabric-1.21.6` | `a943b520de00a79ac5ffbee24ab14a5ced843323efb84f216ef764bcc5f5e0d5` | `884a267f0b9663771043059c6c8667761766bdae9f2d834a382c284eeeddb3e9` | `20260722T051512Z` |
| `fabric-1.21.8` | `41700042fef9bcb8c1ba5b16c97cfd7507fd44daaa47954cef797483c4cf361f` | `a3ce2ce50c453af55cf8bd9aa1e28daa5845461ab2f898ed1155d6328daaa0f5` | `20260722T051512Z` |
| `fabric-1.21.10` | `c21086988860d6e93d70155e2b209b01774f93eb2e2b4b933832e08e4ee642fb` | `98104ecf0c42ae6190a9d558c695ddaade9c4e266f0b18cf4cb2ed19f0eceae2` | `20260722T051512Z` |
| `fabric-1.21.11` | `9c42bf4110ae925f368195b96ef4036001691059d31a43bdd41678aadeafd69d` | `28df1be7233f366402bd49b2ffccc7c607ae2b6d980d6c05237a261f6bd78feb` | `20260722T051512Z` |

For historical comparison, the earlier Forge 1.21.11 standalone build-island
production JAR was
`4050bac906bc260837f85ee3b9579a41482eedf81abd80179e15559cf634284d` and
its acceptance snapshot is
`e2650bf2c3d4a8b7eac2d8dbc0b5afe208054d151e60f8885f3bc863a06b4870`;
all four scenarios passed in `20260722T062636Z`.

## Targets outside the manifest

| Target | State | Required before support/release |
|---|---|---|
| Forge 1.12.2 | Deferred legacy target | Isolated JDK 8 build plus frozen protocol compatibility and runtime evidence |

Forge publishes no loader for 1.20.5 or 1.21.2. Other omitted patches and
candidate widened ranges remain unsupported until exact-patch runtime evidence
exists. See [version consolidation](version-consolidation-roadmap.md).

## Release gate

The 1.2.0 approval covers every exact target in this table and no omitted patch.
For future versions, bind target approvals to that repository version, run the
declared-JDK build, shared fixtures, focused tests, release-JAR structural
verification, the real client/server core matrix, and obtain explicit
maintainer approval. Continue expanding runtime evidence for allowed
Yggdrasil, denial/timeout/disconnect/grace, negative migration, commands, addon
callbacks, HUD presentation, and skin refresh rather than silently upgrading
their evidence level.

Publishing mechanics are documented in
[release automation](../development/release-automation.md).
