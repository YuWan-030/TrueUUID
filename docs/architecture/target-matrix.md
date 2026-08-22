# Target and release matrix

TrueUUID is one multi-target repository. A target is an exact loader and
Minecraft adapter declared in `release/targets.json`; a branch, directory, or
wide metadata range is not a support claim.

## Current verdict

Version 1.3.0 is the current development line and must not be published yet.
`release/targets.json` sets `release_ready` and every target-level `release`
approval to `false`. Historical results remain useful regression evidence, but
the current source and 1.3.0 artifacts require fresh acceptance. Spigot/Paper
dual-mode work must pass its own gates before support is declared. Version
1.2.1 remains permanently withheld; never create or reuse a `v1.2.1` tag.

The worktree declares 52 exact mod targets: 16 Forge, 18 Fabric, and 18 NeoForge.
Fabric and NeoForge now carry one exact module for every Minecraft patch from
1.20.1 through 1.21.11. Forge carries one for every patch in that line for
which Forge published a loader. Forge never published a 1.20.5 or 1.21.2
loader, so those two Forge targets do not exist upstream and are not omissions.

`release/targets.json` is the single target inventory. Root `settings.gradle`
derives its included modules from it, the aggregate root `build` task derives
its dependencies from it, and `validate-targets.sh` fails when the manifest and
the `platform/` modules disagree. Adding a module without a manifest entry, or
the reverse, is now a validation error rather than a silent gap.
Physical modules are grouped as `platform/<loader>/<minecraft-version>`, while
their stable manifest IDs and Gradle coordinates remain `<loader>-<version>`
and `:platform:<loader>-<version>`.

Mod presentation metadata has one source: `mod_id`, `mod_name`, `mod_license`,
`mod_authors`, and `mod_description` in `gradle.properties`. Every loader
expands those into its own metadata file, and `verify-release-jar.sh` rejects
any production JAR whose embedded name, license, author list, or description
disagrees with `gradle.properties`. No adapter carries a hardcoded author,
description, or display string.

## Evidence level for 1.2.1

On 2026-07-28 the 16 targets added in 1.2.1 completed the four-case
installed-JAR matrix on their exact compile patches. The summary is
`build/runtime-acceptance/20260728T074332Z/summary.tsv`: 64 accepted
target/scenario pairs, of which 24 are fresh `PASS` results and 40 are
`REUSED_PASS` rows carried from `20260728T070242Z` through `--resume-from`.
No failed or incomplete result was reused.

That run found a real defect. `neoforge-1.20.3` had been wired to the NeoForge
20.4 login-answer seam, but NeoForge 20.2 and 20.3 patch
`ServerboundCustomQueryAnswerPacket` to carry their own `SimpleQueryPayload`,
which 20.4 dropped for the vanilla payload. Both halves were wrong: the answer
factory sent an unwrapped payload, and `ServerboundCustomQueryAnswerMixin` was
registered even though NeoForge itself owns that decode on a wrapper loader.
Each half failed only at the wire, during login, on an otherwise green build.
The 20.2 seam now lives in the shared `login-20.2-20.3` source root that both
targets select, and `verify-login-wire.gradle` fails the build of any NeoForge
target whose answer factory or Mixin registration disagrees with the
`SimpleQueryPayload` presence in its own loader jar.

The Forge and NeoForge authentication gate also moved in 1.2.1, from
`handleHello` TAIL to `startClientVerification` HEAD, on every 1.20.2 and later
target. Vanilla assigns `state = VERIFYING` inside `startClientVerification` and
returns, so a hook on the TAIL of `handleHello` leaves a window in which the
server tick can observe `VERIFYING` and run
`verifyLoginAndFinishConnectionSetup` before the TrueUUID flow becomes active —
a cross-thread race that completes a native offline login without the
authentication gate. Injecting at the HEAD of `startClientVerification`
installs the gate before the state is ever visible to the tick thread.

That relocation invalidated the 1.2.0 evidence for the 22 affected targets, so
on 2026-07-28 all 11 Forge and 11 NeoForge targets from 1.20.2 through 1.21.11
repeated the same four-case matrix against the relocated gate. The summary is
`build/runtime-acceptance/20260728T133142Z/summary.tsv`: 88 target/scenario
pairs, all fresh `PASS`, nothing reused.

Fabric is not affected by the relocation: it gates login through the Fabric API
`ServerLoginConnectionEvents.QUERY_START` synchronizer, which holds login open
by contract rather than by hook placement. The 12 Fabric targets carried over
from 1.2.0 therefore keep their 2026-07-22 evidence; no change in 1.2.1 touched
the login path they run.

Minecraft 1.20.1 has no `startClientVerification`, so its Forge-like adapters
use a narrower seam. `handleHello` initializes `gameProfile` and then publishes
the Forge `NEGOTIATING` state. The TrueUUID hook now runs immediately after
either of the two possible `gameProfile` writes and before that state
publication; a strict Mixin `require = 2` makes a changed or missing seam fail
at startup instead of silently reopening the race.

That 1.20.1 relocation invalidated the two anchors' earlier evidence. On
2026-07-29, `forge-1.20.1` and `neoforge-1.20.1` repeated all four scenarios
against rebuilt exact-patch artifacts. The summary is
`build/runtime-acceptance/20260730T043305Z/summary.tsv`: eight fresh `PASS`
rows, nothing reused.

Every declared target had four-case installed-JAR evidence for the exact login
path captured by those historical artifacts: 38 accepted on 2026-07-28, two on
2026-07-29, and 12 on 2026-07-22.

The current dirty worktree subsequently hardened all shared loader login seams:
only the exact bounded no-session response may request offline admission;
timeout, malformed/deceptive response, failed premium verification, and cached
same-IP grace no longer downgrade authentication. That source change
invalidates the older installed-JAR runtime evidence for the current artifacts.
All affected Forge, Fabric, and NeoForge targets require fresh exact-artifact
runtime acceptance before any new release claim. Version 1.3.0 remains vetoed.

The following remain implemented or unit-tested only, exactly as in 1.2.0:
extended Yggdrasil/skin-site login, timeout and disconnect cancellation,
migration rejection/timeout/rollback, admin commands, addon
callbacks, HUD and pause-screen presentation, and skin refresh. No visual
assertion has confirmed the three-second fade, the Singleplayer lock artwork,
configured corners/scales/offsets, or pause-menu placement on every GUI era.
Recent-IP grace configuration is retained only for upgrade compatibility; the
current secure login seams do not use it after silence or proof failure.

## Declared targets

Every listed target is built and structurally checked by CI. The aggregate
`scripts/ci/build-all-targets.sh` command builds the 51 root modules and then
the standalone Forge 1.21.11 Gradle 9.5 target.

Runtime state values:

- **Core accepted (1.2.0)** — all four installed-JAR scenarios passed for that
  exact target on 2026-07-22: verified Mojang premium join, policy-approved
  offline fallback, confirmed offline-to-premium migration, and denial of
  offline reuse of a previously verified name.
- **Core accepted (1.2.1)** — the same four scenarios passed for that exact
  target on 2026-07-28 or 2026-07-29, after the applicable login-gate
  relocation and, for NeoForge 1.20.3, the login-wire era fix.
No target carries stale evidence: a row keeps its 1.2.0 date only when 1.2.1
changed nothing in the login path it runs.

The `Historical 1.2.1 approval` column records the target-level approval that
existed for the withheld native-mod candidate. Current 1.3.0 manifest approvals
are all false. Spigot and Paper do not appear in this table because the Spigot
implementation remains an unsupported candidate and Paper is not implemented.

| Target ID | Loader version | Java | Runtime state | Historical 1.2.1 approval |
|---|---:|---:|---|---:|
| `forge-1.20.1` | Forge 47.4.10 | 17 | Core accepted (1.2.1) | true |
| `forge-1.20.2` | Forge 48.1.0 | 17 | Core accepted (1.2.1) | true |
| `forge-1.20.3` | Forge 49.0.2 | 17 | Core accepted (1.2.1) | true |
| `forge-1.20.4` | Forge 49.2.8 | 17 | Core accepted (1.2.1) | true |
| `forge-1.20.6` | Forge 50.2.9 | 21 | Core accepted (1.2.1) | true |
| `forge-1.21` | Forge 51.0.33 | 21 | Core accepted (1.2.1) | true |
| `forge-1.21.1` | Forge 52.1.0 | 21 | Core accepted (1.2.1) | true |
| `forge-1.21.3` | Forge 53.1.0 | 21 | Core accepted (1.2.1) | true |
| `forge-1.21.4` | Forge 54.1.14 | 21 | Core accepted (1.2.1) | true |
| `forge-1.21.5` | Forge 55.1.10 | 21 | Core accepted (1.2.1) | true |
| `forge-1.21.6` | Forge 56.0.9 | 21 | Core accepted (1.2.1) | true |
| `forge-1.21.7` | Forge 57.0.3 | 21 | Core accepted (1.2.1) | true |
| `forge-1.21.8` | Forge 58.1.0 | 21 | Core accepted (1.2.1) | true |
| `forge-1.21.9` | Forge 59.0.5 | 21 | Core accepted (1.2.1) | true |
| `forge-1.21.10` | Forge 60.1.11 | 21 | Core accepted (1.2.1) | true |
| `forge-1.21.11` | Forge 61.1.9 | 21 | Core accepted (1.2.1) | true |
| `fabric-1.20.1` | Fabric Loader 0.19.3 | 17 | Core accepted (1.2.0) | true |
| `fabric-1.20.2` | Fabric Loader 0.19.3 | 17 | Core accepted (1.2.0) | true |
| `fabric-1.20.3` | Fabric Loader 0.19.3 | 17 | Core accepted (1.2.1) | true |
| `fabric-1.20.4` | Fabric Loader 0.19.3 | 17 | Core accepted (1.2.0) | true |
| `fabric-1.20.5` | Fabric Loader 0.19.3 | 21 | Core accepted (1.2.1) | true |
| `fabric-1.20.6` | Fabric Loader 0.19.3 | 21 | Core accepted (1.2.0) | true |
| `fabric-1.21` | Fabric Loader 0.19.3 | 21 | Core accepted (1.2.1) | true |
| `fabric-1.21.1` | Fabric Loader 0.19.3 | 21 | Core accepted (1.2.0) | true |
| `fabric-1.21.2` | Fabric Loader 0.19.3 | 21 | Core accepted (1.2.1) | true |
| `fabric-1.21.3` | Fabric Loader 0.19.3 | 21 | Core accepted (1.2.0) | true |
| `fabric-1.21.4` | Fabric Loader 0.19.3 | 21 | Core accepted (1.2.0) | true |
| `fabric-1.21.5` | Fabric Loader 0.19.3 | 21 | Core accepted (1.2.0) | true |
| `fabric-1.21.6` | Fabric Loader 0.19.3 | 21 | Core accepted (1.2.0) | true |
| `fabric-1.21.7` | Fabric Loader 0.19.3 | 21 | Core accepted (1.2.1) | true |
| `fabric-1.21.8` | Fabric Loader 0.19.3 | 21 | Core accepted (1.2.0) | true |
| `fabric-1.21.9` | Fabric Loader 0.19.3 | 21 | Core accepted (1.2.1) | true |
| `fabric-1.21.10` | Fabric Loader 0.19.3 | 21 | Core accepted (1.2.0) | true |
| `fabric-1.21.11` | Fabric Loader 0.19.3 | 21 | Core accepted (1.2.0) | true |
| `neoforge-1.20.1` | NeoForge 47.1.106 | 17 | Core accepted (1.2.1) | true |
| `neoforge-1.20.2` | NeoForge 20.2.93 | 17 | Core accepted (1.2.1) | true |
| `neoforge-1.20.3` | NeoForge 20.3.8-beta | 17 | Core accepted (1.2.1) | true |
| `neoforge-1.20.4` | NeoForge 20.4.251 | 17 | Core accepted (1.2.1) | true |
| `neoforge-1.20.5` | NeoForge 20.5.21-beta | 21 | Core accepted (1.2.1) | true |
| `neoforge-1.20.6` | NeoForge 20.6.139 | 21 | Core accepted (1.2.1) | true |
| `neoforge-1.21` | NeoForge 21.0.167 | 21 | Core accepted (1.2.1) | true |
| `neoforge-1.21.1` | NeoForge 21.1.213 | 21 | Core accepted (1.2.1) | true |
| `neoforge-1.21.2` | NeoForge 21.2.1-beta | 21 | Core accepted (1.2.1) | true |
| `neoforge-1.21.3` | NeoForge 21.3.56 | 21 | Core accepted (1.2.1) | true |
| `neoforge-1.21.4` | NeoForge 21.4.121 | 21 | Core accepted (1.2.1) | true |
| `neoforge-1.21.5` | NeoForge 21.5.74 | 21 | Core accepted (1.2.1) | true |
| `neoforge-1.21.6` | NeoForge 21.6.20-beta | 21 | Core accepted (1.2.1) | true |
| `neoforge-1.21.7` | NeoForge 21.7.25-beta | 21 | Core accepted (1.2.1) | true |
| `neoforge-1.21.8` | NeoForge 21.8.9 | 21 | Core accepted (1.2.1) | true |
| `neoforge-1.21.9` | NeoForge 21.9.16-beta | 21 | Core accepted (1.2.1) | true |
| `neoforge-1.21.10` | NeoForge 21.10.64 | 21 | Core accepted (1.2.1) | true |
| `neoforge-1.21.11` | NeoForge 21.11.44 | 21 | Core accepted (1.2.1) | true |

Forge 1.21.11 requires ForgeGradle 7 and Gradle 9.5, so it remains a standalone
build island with its own wrapper. The manifest marks it `standalone`, and CI,
the runtime harness, and the release pipeline invoke that wrapper explicitly.

Patch versions absent from this table are not implicitly covered. A declared
Minecraft range may be widened only after each claimed patch passes its own
runtime checks.

## Feature parity

The adapters share the same security and behavioural spine wherever their
loader APIs permit it. “Implemented” is a source/build claim; the “core
accepted” rows describe all 52 exact targets with the evidence date recorded
above.

| Feature | Forge targets | Fabric targets | NeoForge targets | Evidence level |
|---|---|---|---|---|
| Mojang premium verification | yes | yes | yes | core runtime accepted on every exact target |
| Offline fallback policy | yes | yes | yes | core runtime accepted on every exact target |
| Persisted known-name denial | yes | yes | yes | core runtime accepted on every exact target |
| Confirmed data migration | yes | yes | yes | core runtime accepted on every exact target |
| Login gate installed before vanilla can finish login | yes | by API contract | yes | core runtime accepted on every exact target |
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
presentation values, and notification routing live in `shared/presentation`.
Minecraft profiles, packets, commands, world paths, loader lifecycle, and
server-thread scheduling stay in platform adapters. Forge targets recompile
`platform/forge/common` with narrow SRG/official, event-bus, GUI, record, and
identifier-era seams. NeoForge targets recompile the canonical
`platform/neoforge/common` core with equally narrow named era roots; its
NeoGradle 7 eras (1.20.2, 1.20.3, 1.20.5) and its ModDevGradle eras share those
roots and differ only in Gradle plugin and metadata filename. Minecraft seams
shared by both live in `platform/common/forgelike`. Fabric targets recompile
`platform/fabric/common` against their pinned Yarn/Fabric APIs, with small
source roots for session joining, typed payloads, authlib records, permissions,
identifiers, and HUD matrix transitions.

`scripts/ci/validate-source-sharing.py` rejects exact Java source copies,
including tests, and version-module source donors before target or release
validation can pass.

## Historical 1.2.0 evidence

The table below is retained as historical, pre-1.2.1 Fabric evidence; it is not
the release approval for the current worktree. The 2026-07-22 acceptance
snapshots for every loader live under
`build/runtime-acceptance/20260722T114302Z/<target>/artifact/` (with the exact
Forge 1.21.6 focused artifact under `20260722T114109Z`). That run recorded 140
fresh `PASS` results and four `REUSED_PASS` results for Forge 1.21.6 from the
immediately preceding focused run; no failed or incomplete result was reused.
The harness removes each snapshot source from the module's normal `build/libs`
path. Normal builds compile the release hook implementation, and release-JAR
verification rejects acceptance environment names or packaged scripts.

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

These hashes predate the 1.2.1 version bump and the login-gate relocation, so
they no longer match the current artifacts. They document what was tested, not
what ships.

## Targets outside the manifest

| Target | State | Required before support/release |
|---|---|---|
| Forge 1.12.2 | Deferred legacy target | Isolated JDK 8 build plus frozen protocol compatibility and runtime evidence |

Forge publishes no loader for 1.20.5 or 1.21.2. Other omitted patches and
candidate widened ranges remain unsupported until exact-patch runtime evidence
exists. See [version consolidation](version-consolidation-roadmap.md).

## Release gate

The historical 1.2.1 approval covered every exact target in this table and no
omitted patch. After the 1.3.0 version change, every current manifest `release`
flag was reset to `false` and the approval is bound to
`release_version: 1.3.0`.

All 52 targets carry four-case installed-JAR evidence for the login path they
ship: `build/runtime-acceptance/20260728T074332Z` for the 16 targets added in
1.2.1, `20260728T133142Z` for the 22 whose login gate relocated, and
`20260722T114302Z` for the 14 Fabric and 1.20.1 targets 1.2.1 did not change.
Approval still does not convert the unit-tested rows in the feature table above
into runtime-tested claims.

For future versions, bind target approvals to that repository version, run the
declared-JDK build, shared fixtures, focused tests, release-JAR structural
verification, the real client/server core matrix, and obtain explicit
maintainer approval. Continue expanding runtime evidence for allowed
Yggdrasil, denial/timeout/disconnect/grace, negative migration, commands, addon
callbacks, HUD presentation, and skin refresh rather than silently upgrading
their evidence level.

Publishing mechanics are documented in
[release automation](../development/release-automation.md).
