# TrueUUID

[English](README.md) | 简体中文

这是一个适用于 Forge 1.20.x 的服务端/客户端同装模组。在离线模式服务器的“登录阶段”安全校验玩家是否为正版账号，且不会把玩家的访问令牌发送到服务器。

TrueUUID 让离线服也能：
- 让客户端本地执行 Mojang 的 “joinServer”。
- 服务器用一次性 nonce 调用 Mojang Session Server 验证。
- 验证成功：将玩家的 UUID 替换为正版 UUID，修正名字大小写，注入皮肤属性。
- 验证失败或超时：可配置策略（默认超时踢出；失败按策略决定兜底）。
- 玩家进服时通过标题提示“正版模式/离线模式”，离线兜底时发送聊天说明。

注意：客户端与服务端都必须安装本模组；服务器需设置为离线模式（online-mode=false）。

## 特性亮点

- 隐私友好：访问令牌只在客户端本地使用，不发送给服务器。
- 身份一致性：即使在离线服，验证成功的玩家也使用正版 UUID 与皮肤。
- 体验清晰：标题提示模式，离线兜底时额外聊天说明。
- 数据安全策略：减少/避免“正版与离线两套 UUID 存档”的分叉风险。

## 新增能力与策略

- 名字注册表：持久记录“已成功通过正版校验”的名字及其正版 UUID。
- 策略：knownPremiumDenyOffline
    - 一旦某名字被验证为正版，之后该名字鉴权失败时不再允许离线兜底（从源头杜绝数据分叉）。
- 策略：allowOfflineForUnknownOnly
    - 仅允许“从未验证为正版”的新名字走离线兜底。
- 近期同 IP 容错（可选）
    - 若同名同 IP 在短时间 TTL 内曾验证成功，这次失败可临时按正版处理，用于缓解瞬时网络问题。公共/共享网络请谨慎启用，并缩短 TTL。
- 管理员命令：/trueuuid link <name>
    - 将该名字对应“离线 UUID”的玩家数据迁移/合并到“正版 UUID”。支持 dry-run 预览与备份。
- 可靠显示踢出原因（Forge 1.20.x）
    - 登录阶段显式发送登录/游戏断开包，确保客户端不再只显示“连接中断”，而是展示自定义中文原因。

## 工作流程

1. 服务器为离线模式。在登录阶段（HELLO），服务器发送自定义登录查询（transactionId + 标识 `trueuuid:auth` + 随机 nonce）。
2. 客户端拦截该查询，读取 nonce，在本地调用 `MinecraftSessionService.joinServer(profile, token, nonce)`（令牌不出本地）。
3. 客户端仅回传一个布尔确认（不含敏感数据）。
4. 服务器调用 Mojang `/hasJoined?username={name}&serverId={nonce}[&ip={ip}]` 进行验证。
5. 验证成功：
    - 替换待登录的 `GameProfile` 为正版 UUID 与 Mojang 返回的规范名字。
    - 注入皮肤 `textures` 属性（含签名）。
    - 进服后强制刷新玩家信息以更新皮肤。
    - 显示绿色标题“正版模式”（副标题可配置短语）。
6. 验证失败：
    - 默认：
        - 超时：按配置踢出。
        - 失败：由下述策略决定（见“配置”）。离线兜底会显示红色标题“离线模式”和聊天说明。

## 环境需求

- Minecraft：1.20.x
- Forge：47.x（如 47.4.0+）
- Java：17
- 客户端与服务端都需安装本模组
- 服务器需设为 `online-mode=false`

## 配置项

首次运行会生成：
- `config/trueuuid-common.toml`

主要键与默认值：

- auth.timeoutMs = 10000
    - 登录阶段等待客户端回包的时间（毫秒）。
- auth.allowOfflineOnTimeout = false
    - false：超时踢出（默认）
    - true：超时时允许离线兜底
- auth.allowOfflineOnFailure = true
    - 旧式总开关；更推荐使用下面更细粒度的新策略。
- auth.timeoutKickMessage = "登录超时，未完成账号校验"
    - 超时时展示的踢出原因。
- auth.offlineFallbackMessage = "注意：你当前以离线模式进入服务器；如果你是正版账号..."
    - 离线兜底进入时发送的聊天说明。
- auth.offlineShortSubtitle = "鉴权失败：离线模式"
    - 离线模式时标题的短副标题。
- auth.onlineShortSubtitle = "已通过正版校验"
    - 正版模式时标题的短副标题。
- auth.knownPremiumDenyOffline = true
    - 已验证过正版的名字，之后鉴权失败不允许离线兜底（防止分叉）。
- auth.allowOfflineForUnknownOnly = true
    - 仅“从未验证为正版”的名字可离线兜底。
- auth.recentIpGrace.enabled = true
    - 启用“近期同 IP 成功”容错。
- auth.recentIpGrace.ttlSeconds = 300
    - 同 IP 容错 TTL（建议 60–600）。

说明：
- 同 IP 容错重在可用性，并非强安全；TTL 不宜过长，公共网络慎用。
- 旧开关 `allowOfflineOnFailure` 仍有效，但建议优先使用新策略。

## 管理命令

- /trueuuid link <name>
    - 将该名字对应的“离线 UUID”数据迁移到“正版 UUID”（从注册表中读取正版 UUID）。
    - 子命令示例：
        - /trueuuid link dryrun <name>  — 预览操作，不写盘
        - /trueuuid link run <name>     — 执行迁移（示例实现中默认会先备份）
    - 行为：
        - 若正版数据文件不存在，则把离线文件“移动”为正版。
        - 若两者都存在，默认保留正版（合并背包/末影箱/统计可按需实现，已预留 TODO）。

涉及文件（按 UUID）：
- world/playerdata/<uuid>.dat
- world/advancements/<uuid>.json
- world/stats/<uuid>.json

备份目录：
- world/backups/trueuuid/<时间戳>/<name>/

## 故障排查

- 客户端只显示“连接中断”而非自定义原因：
    - 在 Forge 47.4.x 的登录/配置握手阶段可能存在并行。服务端已在登录阶段显式发送“登录断开包 + 游戏断开包”后再断开，通常可正确展示原因。若仍无效，请用“纯净客户端”（无 UI 改造模组）测试。

- 皮肤未及时刷新：
    - 服务端会在玩家进服后一帧广播 PlayerInfo 刷新。若客户端有改造皮肤的模组，请确保不拦截原版刷新。

## 兼容与注意

- 代理（Bungee/Velocity）：若能获取真实 IP，会将其带入 `/hasJoined`；即使没有 IP 参数也能正常验证（IP 对 Mojang 接口是可选）。
- 数据一致性：结合“已知正版名禁止离线 + 仅未知名可离线”，可有效避免“同名不同 UUID”与存档分叉。
- 若客户端未安装本模组：将无法回传预期负载，具体行为由配置/策略决定（可能踢出或仅允许未知新名字离线）。

## 构建

- 克隆仓库
- 执行：
    - Windows：`gradlew.bat build`
    - macOS/Linux：`./gradlew build`
- 成品在：`build/libs/trueuuid-<version>.jar`

## 隐私

- 访问令牌仅在客户端本地使用，不会发送至服务器。
- 服务器仅收到布尔确认，随后自行请求 Mojang Session Server 完成校验。

## 许可

- GNU LGPL 3.0（详见 `gradle.properties` / 仓库 License）

## 致谢

- Mojang authlib 与 session API
- Sponge Mixin
- ForgeGradle

---
维护者: [@YuWan-030](https://github.com/YuWan-030)