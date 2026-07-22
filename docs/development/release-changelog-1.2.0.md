## English

TrueUUID 1.2.0 is a major cross-loader update since 1.1.2. It expands the
previous Forge 1.20.1 and NeoForge 1.21.1 release to 36 exact builds across
Forge, Fabric, and NeoForge.

### Highlights

- Added Fabric support and expanded Forge and NeoForge coverage across Minecraft 1.20.1, 1.20.2, 1.20.4, 1.20.6, 1.21.1, 1.21.3, 1.21.4, 1.21.5, 1.21.6, 1.21.8, 1.21.10, and 1.21.11. Each JAR targets one exact loader and Minecraft version.
- Added a localized account-status badge for Premium, Offline, Singleplayer, and Premium (LAN): it appears briefly in the HUD, fades after three seconds, and remains as the same lock-and-label badge in the pause menu. Private singleplayer now emits one status message; opening the world to LAN changes the chat and badge once to Premium (LAN).
- Added one private server-confirmed join result for the joining player, structured login audit logs, and optional operator-only notifications (off by default).
- Added the public, loader-neutral `TrueuuidApi` for addons to query account state, register login callbacks, and inspect protected verified names.
- Hardened Mojang/Yggdrasil verification, offline fallback, verified-name protection, migration handling, timeouts, and short same-IP reconnect grace.
- Fixed the profile-property handling that could leave Fabric 1.21.10 and 1.21.11 stuck on the negotiating screen, and repaired Forge 1.20.1 production Mixin packaging.
- Consolidated shared status timing, artwork, presentation, notification routing, and loader runtime behavior so version modules contain only narrow API-era seams.
- All 36 release targets passed build and packaging checks plus premium login, offline fallback, confirmed migration, and known-name denial acceptance tests. GitHub, Modrinth, and CurseForge publish the same tested JARs and this exact changelog.

## 中文

TrueUUID 1.2.0 是自 1.1.2 以来的一次重要跨加载器更新。支持范围从原有的
Forge 1.20.1 和 NeoForge 1.21.1 扩展到 Forge、Fabric 与 NeoForge 的 36 个精确构建。

### 主要更新

- 新增 Fabric 支持，并将 Forge 与 NeoForge 扩展到 Minecraft 1.20.1、1.20.2、1.20.4、1.20.6、1.21.1、1.21.3、1.21.4、1.21.5、1.21.6、1.21.8、1.21.10 和 1.21.11。每个 JAR 仅对应一个加载器和 Minecraft 版本。
- 新增正版、离线、单人模式与正版（局域网）的本地化账号状态徽章：在 HUD 中短暂显示，三秒后淡出，并在暂停菜单中持续显示相同的锁图标和文字。私有单人世界只提示一次；开放至局域网后，聊天与徽章会切换一次到正版（局域网）。
- 加入玩家仅收到一次服务器确认结果；服务器记录结构化登录审计，并可选择仅向管理员发送通知（默认关闭）。
- 新增跨加载器公开 `TrueuuidApi`，供附加模组查询账号状态、注册登录回调并检查受保护的已验证名称。
- 加强 Mojang/Yggdrasil 验证、离线回退、已验证名称保护、迁移处理、超时控制和短时间同 IP 重连宽限。
- 修复可能导致 Fabric 1.21.10 和 1.21.11 卡在“正在协商”界面的 profile 属性处理问题，并修复 Forge 1.20.1 生产 JAR 的 Mixin 打包。
- 统一了状态计时、图案、显示信息、通知路由与加载器运行逻辑，各版本模块只保留必要的 API 时代适配层。
- 全部 36 个发布目标均通过构建和打包检查，以及正版登录、离线回退、确认迁移和已验证名称拒绝验收。GitHub、Modrinth 与 CurseForge 将发布同一批已测试 JAR 和这份完全一致的更新日志。
