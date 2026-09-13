## English

TrueUUID 1.3.0 is a security update for every Forge, Fabric, and NeoForge
build. It also delivers the exact-patch coverage prepared for 1.2.1, which was
never published. This release contains 52 exact builds for Minecraft 1.20.1
through 1.21.11. Every build passed the installed-JAR login matrix: premium
login, offline fallback, confirmed migration, and known-name denial.

### Security fixes

- A timeout, a silent or malformed client response, or a failed session check no longer falls back to offline admission. Only a TrueUUID client that explicitly reports it has no local premium session reaches the offline-fallback policy.
- Removed the short same-IP reconnect grace. A reconnecting player must verify again.
- Fixed a Forge and NeoForge login race that could let a native offline login finish before the TrueUUID check started. Minecraft 1.20.2 and later install the gate at `startClientVerification`, and 1.20.1 installs it right after the login profile is set, before Forge publishes negotiation state. Fabric was not affected.
- Fixed premium verification failing with HTTP 204 behind proxies, NAT, or split IPv4/IPv6 routing. TrueUUID no longer sends the server-observed client IP to the `hasJoined` endpoint.
- Fixed NeoForge 1.20.3 login answers, which used the wrong NeoForge payload wrapper and failed during login.

### New builds

- Fabric and NeoForge: 1.20.3, 1.20.5, 1.21, 1.21.2, 1.21.7, and 1.21.9.
- Forge: 1.20.3, 1.21, 1.21.7, and 1.21.9. Forge never published a loader for Minecraft 1.20.5 or 1.21.2.

### Other changes

- A player without TrueUUID on the client is disconnected with a plain message asking them to install it.
- The offline-fallback chat notice is shorter and states that the account was not premium-verified.

### Upgrade notes for server owners

- If you relied on `allowOfflineOnFailure = true` to admit players while Mojang or the network was unavailable, those logins are now denied. Players on an offline launcher with TrueUUID installed are still handled by the offline-fallback policy.
- `allowOfflineOnTimeout` and the `recentIpGrace` keys remain in existing config files for compatibility. They are ignored.

### Supported downloads

- Forge: 1.20.1, 1.20.2, 1.20.3, 1.20.4, 1.20.6, 1.21, 1.21.1, and 1.21.3 through 1.21.11.
- Fabric: every Minecraft release from 1.20.1 through 1.21.11.
- NeoForge: every Minecraft release from 1.20.1 through 1.21.11.
- The Spigot and Paper 1.20.1 server plugins are not part of this release.

## 中文

TrueUUID 1.3.0 是面向所有 Forge、Fabric 与 NeoForge 构建的安全更新，同时
包含原本为 1.2.1 准备、但从未发布的精确补丁版本覆盖。本次发布共 52 个精确
构建，覆盖 Minecraft 1.20.1 至 1.21.11。每个构建都通过了生产 JAR 真实登录验收：
正版登录、离线兜底、确认迁移和已验证名称拒绝。

### 安全修复

- 超时、客户端无响应或响应畸形、会话校验失败时，不再降级为离线放行。只有明确报告本地没有正版会话的 TrueUUID 客户端，才会进入离线兜底策略。
- 移除短时间同 IP 重连宽限。玩家重连时必须重新验证。
- 修复 Forge 与 NeoForge 的登录竞态：原版离线登录可能在 TrueUUID 校验开始前完成。Minecraft 1.20.2 及以上版本在 `startClientVerification` 安装鉴权关卡，1.20.1 则在设置登录档案后、Forge 发布协商状态前安装。Fabric 不受影响。
- 修复在代理、NAT 或 IPv4/IPv6 分流环境下正版验证返回 HTTP 204 而失败的问题。TrueUUID 不再向 `hasJoined` 接口发送服务端看到的客户端 IP。
- 修复 NeoForge 1.20.3 使用错误的 NeoForge 载荷包装、导致登录应答失败的问题。

### 新增构建

- Fabric 与 NeoForge：1.20.3、1.20.5、1.21、1.21.2、1.21.7 和 1.21.9。
- Forge：1.20.3、1.21、1.21.7 和 1.21.9。Forge 上游从未发布 Minecraft 1.20.5 或 1.21.2 的加载器。

### 其他更新

- 客户端未安装 TrueUUID 的玩家会被断开连接，并看到提示其安装本 Mod 的纯文本信息。
- 离线兜底的聊天提示更简短，并明确说明该账号未通过正版验证。

### 服务器升级须知

- 如果此前依赖 `allowOfflineOnFailure = true` 在 Mojang 或网络不可用时放行玩家，这类登录现在会被拒绝。安装了 TrueUUID 的离线启动器玩家仍按离线兜底策略处理。
- `allowOfflineOnTimeout` 与 `recentIpGrace` 相关配置为兼容旧配置文件而保留，但会被忽略。

### 支持的下载

- Forge：1.20.1、1.20.2、1.20.3、1.20.4、1.20.6、1.21、1.21.1，以及 1.21.3 至 1.21.11。
- Fabric：1.20.1 至 1.21.11 的每个 Minecraft 正式版本。
- NeoForge：1.20.1 至 1.21.11 的每个 Minecraft 正式版本。
- Spigot 与 Paper 1.20.1 服务端插件不包含在本次发布中。
