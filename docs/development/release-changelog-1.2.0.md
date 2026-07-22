## English

TrueUUID 1.2.0 is a major cross-loader update since 1.1.2. It expands the
previous Forge 1.20.1 and NeoForge 1.21.1 release to 36 exact builds across
Forge, Fabric, and NeoForge.

### Highlights

- Added Fabric support and expanded Forge and NeoForge coverage across Minecraft 1.20.1, 1.20.2, 1.20.4, 1.20.6, 1.21.1, 1.21.3, 1.21.4, 1.21.5, 1.21.6, 1.21.8, 1.21.10, and 1.21.11. Each JAR targets one exact loader and Minecraft version.
- Added a configurable account-status HUD showing verified premium or offline fallback state, with English and Simplified Chinese login feedback.
- Added the public, loader-neutral `TrueuuidApi` for addons to query account state, register login callbacks, and inspect protected verified names.
- Hardened Mojang/Yggdrasil verification, offline fallback, verified-name protection, migration handling, timeouts, and short same-IP reconnect grace.
- Fixed the profile-property handling that could leave Fabric 1.21.10 and 1.21.11 stuck on the negotiating screen, and repaired Forge 1.20.1 production Mixin packaging.
- All 36 release targets passed build and packaging checks plus premium login, offline fallback, confirmed migration, and known-name denial acceptance tests. GitHub, Modrinth, and CurseForge publish the same tested JARs and this same changelog.

## 中文

TrueUUID 1.2.0 是自 1.1.2 以来的一次重要跨加载器更新。支持范围从原有的
Forge 1.20.1 和 NeoForge 1.21.1 扩展到 Forge、Fabric 与 NeoForge 的 36 个精确构建。

### 主要更新

- 新增 Fabric 支持，并将 Forge 与 NeoForge 扩展到 Minecraft 1.20.1、1.20.2、1.20.4、1.20.6、1.21.1、1.21.3、1.21.4、1.21.5、1.21.6、1.21.8、1.21.10 和 1.21.11。每个 JAR 仅对应一个加载器和 Minecraft 版本。
- 新增可配置的账号状态 HUD，用于显示正版验证或离线回退状态，并提供英文和简体中文登录提示。
- 新增跨加载器公开 `TrueuuidApi`，供附加模组查询账号状态、注册登录回调并检查受保护的已验证名称。
- 加强 Mojang/Yggdrasil 验证、离线回退、已验证名称保护、迁移处理、超时控制和短时间同 IP 重连宽限。
- 修复可能导致 Fabric 1.21.10 和 1.21.11 卡在“正在协商”界面的 profile 属性处理问题，并修复 Forge 1.20.1 生产 JAR 的 Mixin 打包。
- 全部 36 个发布目标均通过构建和打包检查，以及正版登录、离线回退、确认迁移和已验证名称拒绝验收。GitHub、Modrinth 与 CurseForge 将发布同一批已测试 JAR 和同一份更新日志。
