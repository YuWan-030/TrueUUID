## English

### Platform expansion

- Added a Fabric 1.20.1 adapter with Mojang account verification, policy-gated offline fallback, a persistent verified-name registry, bounded login handling, and a client account-status indicator.
- Added Forge build coverage for Minecraft 1.20.2, 1.21.1, 1.21.3, 1.21.4, 1.21.5, 1.21.6, and 1.21.8.
- Added NeoForge build coverage for Minecraft 1.20.2, 1.20.4, 1.20.6, 1.21.1, 1.21.3, 1.21.4, 1.21.5, 1.21.6, 1.21.8, 1.21.10, and 1.21.11.
- Each downloadable JAR remains specific to its exact loader and Minecraft version. Release assets are limited to targets that completed the recorded acceptance checks.

### User-facing changes

- Added a small client-side account-status HUD showing verified premium or offline fallback state after a TrueUUID handshake.
- Added configurable HUD corner, offset, and scale options on Forge and NeoForge; Fabric uses its loader-specific default layout.
- Added client-language English and Simplified Chinese messages for login feedback and disconnect reasons.
- Added configurable verification timeouts, safer offline-fallback policy, persistent protection for previously verified names, and short same-IP reconnect grace.
- Added bounded Mojang/Yggdrasil verification with HTTPS endpoint validation, public-address checks, response limits, and redirect refusal.

### Build and release reliability

- Unified every loader module on version 1.2.0 and added build/test coverage for all 21 declared targets.
- Repaired the Forge 1.20.1 production JAR so its Mixin refmap, manifest entry, and SRG-reobfuscated references are always packaged correctly.
- Added structural release checks for SRG-era artifacts and synchronized GitHub, Modrinth, and CurseForge publishing from the same tested JARs and changelog.

## 中文

### 平台扩展

- 新增 Fabric 1.20.1 适配器，包含 Mojang 账号验证、受策略控制的离线回退、持久化已验证名称注册表、有界登录处理和客户端账号状态提示。
- 新增 Minecraft 1.20.2、1.21.1、1.21.3、1.21.4、1.21.5、1.21.6 和 1.21.8 的 Forge 构建支持。
- 新增 Minecraft 1.20.2、1.20.4、1.20.6、1.21.1、1.21.3、1.21.4、1.21.5、1.21.6、1.21.8、1.21.10 和 1.21.11 的 NeoForge 构建支持。
- 每个可下载 JAR 仅适用于其对应的加载器和 Minecraft 版本。Release 附件只包含已完成并记录验收检查的目标。

### 用户可见更新

- 新增小型客户端账号状态 HUD，在 TrueUUID 握手后显示已验证正版状态或离线回退状态。
- Forge 和 NeoForge 新增 HUD 角落、偏移和缩放配置；Fabric 使用其加载器专用的默认布局。
- 新增由客户端语言控制的英文和简体中文登录反馈及断开连接提示。
- 新增可配置验证超时、更安全的离线回退策略、对已验证名称的持久化保护，以及短时间同 IP 重连宽限。
- 新增有界 Mojang/Yggdrasil 验证，并检查 HTTPS 端点、公共地址、响应大小，同时拒绝重定向。

### 构建与发布可靠性

- 所有加载器模块统一使用 1.2.0 版本，并为全部 21 个已声明目标加入构建和测试覆盖。
- 修复 Forge 1.20.1 生产 JAR，确保始终正确打包 Mixin refmap、清单属性和 SRG 重混淆引用。
- 新增 SRG 时代产物结构检查，并让 GitHub、Modrinth 和 CurseForge 使用同一批已测试 JAR 和同一份更新日志同步发布。
