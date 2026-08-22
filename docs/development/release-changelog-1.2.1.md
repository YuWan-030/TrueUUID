> **WITHHELD — DO NOT PUBLISH:** 1.2.1 is retained only as an internal
> regression baseline. The next public release must use a new version after the
> Spigot/Paper security and acceptance gates pass.

## English

TrueUUID 1.2.1 completes exact-patch coverage across the Minecraft 1.20.1–1.21.11 release line.

### Highlights

- Added exact Fabric and NeoForge builds for 1.20.3, 1.20.5, 1.21, 1.21.2, 1.21.7, and 1.21.9.
- Added exact Forge builds for 1.20.3, 1.21, 1.21.7, and 1.21.9. Forge itself never published a loader for Minecraft 1.20.5 or 1.21.2.
- Fixed a Forge and NeoForge login race across the supported Minecraft line: 1.20.2 and later install the authentication gate at `startClientVerification`, while 1.20.1 installs it immediately after profile initialization and before Forge publishes negotiation state. Fabric was never affected because it holds login open through the Fabric API synchronizer.
- Grouped physical modules under `platform/fabric`, `platform/forge`, and `platform/neoforge`, with loader-common and cross-loader roots beside them, while preserving stable target IDs and Gradle task names.
- Kept authentication, migration, addon API, HUD/pause status, join feedback, and audit behavior in shared loader/era cores; the new version modules contain only dependency and API-era boundaries.
- Centralized author and presentation metadata for F1xGOD, Wish, and YuWan.
- Hardened release publishing so GitHub, Modrinth, and CurseForge receive the same exact-version JARs and changelog, with post-upload Modrinth validation and complete CurseForge version/loader preflight.

## 中文

TrueUUID 1.2.1 完成了 Minecraft 1.20.1–1.21.11 正式版本线的精确补丁覆盖。

### 主要更新

- 新增 Fabric 与 NeoForge 的 1.20.3、1.20.5、1.21、1.21.2、1.21.7 和 1.21.9 精确构建。
- 新增 Forge 的 1.20.3、1.21、1.21.7 和 1.21.9 精确构建。Forge 上游从未发布 Minecraft 1.20.5 或 1.21.2 的加载器。
- 修复 Forge 与 NeoForge 整个支持版本线的登录竞态：1.20.2 及以上版本在 `startClientVerification` 安装鉴权关卡，1.20.1 则在初始化玩家档案后、Forge 发布协商状态前安装。Fabric 通过 Fabric API 同步器保持登录挂起，从未受此影响。
- 将物理模块整理到 `platform/fabric`、`platform/forge` 与 `platform/neoforge` 下，并将加载器共享和跨加载器共享目录归位，同时保持目标 ID 与 Gradle 任务名不变。
- 鉴权、迁移、附加模组 API、HUD/暂停菜单状态、进服提示和审计逻辑继续由共享加载器/时代核心维护；新增版本模块只保留依赖与 API 时代边界。
- 统一维护 F1xGOD、Wish 与 YuWan 的作者及展示元数据。
- 加强发布流程：GitHub、Modrinth 与 CurseForge 使用同一批精确版本 JAR 和更新日志，并增加 Modrinth 上传后校验及 CurseForge 完整版本/加载器预检。
