# TrueUUID

[English](README.md) | 简体中文

TrueUUID 是一个用于离线模式 Minecraft 服务器的登录阶段账号校验 Mod。

它可以让离线模式服务器验证 Mojang 正版账号，以及受支持的 Yggdrasil/authlib-injector 皮肤站账号，同时不会把玩家的 access token 发送给服务器。

客户端和服务端都必须安装本 Mod。服务端必须设置：

```properties
online-mode=false
```

## 功能

- 隐私安全的账号校验：玩家 access token 只在客户端本地使用。
- 在离线模式服务器上支持正版/Yggdrasil UUID。
- 验证成功后修正玩家名大小写。
- 登录阶段注入带签名的皮肤 textures 属性。
- 玩家进服后刷新玩家信息，帮助皮肤正确更新。
- 清晰提示玩家当前是正版、皮肤站还是离线兜底状态。
- 通过 Minecraft 语言文件显示界面文案，不同客户端会按自己的语言设置看到对应文字。
- 支持离线玩家数据迁移到正版/皮肤站 UUID，迁移前需要确认并自动备份。
- 防止已验证过的玩家名再次被同名离线账号冒用。

## 为什么需要它

离线模式服务器默认无法信任玩家 UUID。TrueUUID 可以在保持服务器离线模式的同时，提高玩家身份一致性。

验证成功的玩家可以继续使用 Mojang 或 Yggdrasil 返回的正式 UUID 和皮肤数据，而服务器不会接触玩家的 access token。

它适合整合包、局域网式服务器、私人离线模式社区，以及希望改善身份一致性但不想直接开启 Mojang 在线模式的服务器。

## 工作流程

1. 服务端运行在离线模式。
2. 玩家登录时，服务端发送带 nonce 的自定义登录查询。
3. 安装了 Mod 的客户端收到查询后，在本地使用玩家 profile、token 和 nonce 调用 `joinServer`。token 不会离开客户端。
4. 客户端返回认证结果和选择的认证来源。
5. 服务端通过 Mojang Session Server 或受支持的 Yggdrasil `hasJoined` 接口验证 nonce。
6. 如果验证成功：
   - 将待登录 profile 替换为验证后的 UUID。
   - 修正玩家名大小写。
   - 注入带签名的皮肤 textures 属性。
   - 记录认证来源。
   - 玩家进入后刷新玩家列表和皮肤信息。
7. 如果验证失败或超时：
   - 行为由配置决定。
   - 已验证过的名字可以禁止离线兜底。
   - 未知名字可以按配置允许离线兜底。

## 离线数据迁移

TrueUUID 1.0.9 增加了更安全的离线数据迁移流程，用于玩家原本使用离线账号游玩，后来改用同名正版账号或皮肤站账号的情况。

当验证登录时检测到同名离线 UUID 数据，客户端会显示确认继承窗口。只有玩家确认后才会继承离线数据。

迁移前，TrueUUID 会同时备份旧的离线数据，以及目标正版/皮肤站 UUID 已有的数据。

目前支持迁移的数据包括：

- 原版 `playerdata`
- 原版 `playerdata_old`
- 进度 `advancements`
- 统计 `stats`
- Cosmetic Armor 的 `.cosarmor` 数据
- Open Parties and Claims
- FTB Chunks
- FTB Essentials
- FTB Teams
- FTB Quests
- FTB Ranks
- CustomNPCs 玩家数据

## 环境要求

Forge 版本：

- Minecraft: 1.20.1
- Forge: 47.x
- Java: 17

NeoForge 版本：

- Minecraft: 1.21.1
- NeoForge: 21.1.x
- Java: 21

客户端和服务端必须都安装 TrueUUID。

## 安装

服务端：

1. 在 `server.properties` 中设置 `online-mode=false`。
2. 将对应版本的 TrueUUID jar 放入服务端 `mods` 文件夹。

客户端：

1. 将对应版本的 TrueUUID jar 放入客户端 `mods` 文件夹。

如果客户端没有安装本 Mod，服务端将收不到预期的登录查询响应。根据配置不同，该玩家可能会被踢出，也可能会进入离线兜底流程。

## 配置

首次运行后会生成配置文件：

```text
config/trueuuid-common.toml
```

常用配置：

```toml
auth.timeoutMs = 30000
```

登录阶段等待客户端认证响应的时间，单位为毫秒。

```toml
auth.allowOfflineOnTimeout = false
```

`false`：认证超时后踢出。

`true`：认证超时后允许离线兜底。

```toml
auth.allowOfflineOnFailure = true
```

`true`：普通认证失败时允许进入离线兜底流程。

`false`：认证失败时直接断开连接。

```toml
auth.knownPremiumDenyOffline = true
```

如果某个名字已经成功验证为正版或 Yggdrasil 账号，后续不允许该名字再通过离线兜底进入。

```toml
auth.allowOfflineForUnknownOnly = true
```

只允许从未验证过的名字使用离线兜底。

```toml
auth.recentIpGrace.enabled = true
auth.recentIpGrace.ttlSeconds = 10
```

允许已验证玩家退出后，在短时间内使用同名同 IP 重连时复用上一次验证 UUID。该机制不会用于明确拒绝认证或明确离线登录的客户端。

```toml
auth.showJoinFeedback = true
```

是否在玩家进服后显示正版、皮肤站、离线兜底、单人模式等状态提示。设为 `false` 后不再发送这些 Title/聊天提示，但不影响鉴权和皮肤刷新。

默认进服提示和断开连接文案会以 Minecraft 翻译 key 发送，并由玩家客户端的语言文件（`zh_cn` / `en_us`）渲染。如果服务器之前已经生成过带中英双语自定义文字的配置，请把这些 message 值改回 `trueuuid.*` key，或删除配置后重新生成，才能按客户端语言显示。

```text
/trueuuid cleanupuuid <name>
```

仅管理员可用，权限等级 4。该指令会备份并清理指定玩家名对应的重复离线 UUID 数据，不会修改正版/皮肤站 UUID 数据。

```text
/trueuuid migrateuuid <name>
```

仅管理员可用，权限等级 4。该指令表示管理员同意玩家继承同名离线 UUID 数据，会先备份再迁移到正版/皮肤站 UUID。

```toml
auth.yggdrasil.apiRootWhitelist = []
```

Yggdrasil/authlib-injector `hasJoined` URL 白名单。空列表表示信任客户端上报的接口地址。可以添加 `"littleskin.cn"` 等条目来限制允许的皮肤站来源。

NeoForge 1.21.1 额外提供：

```toml
auth.mojangReverseProxy = "https://sessionserver.mojang.com"
```

Mojang Session Server 地址。如有需要，可以改为反代地址。

## 兼容性说明

- 代理服：Mojang `hasJoined` 的 IP 参数是可选项。即使代理隐藏了真实客户端 IP，通常仍可完成验证。
- 皮肤：TrueUUID 会在登录阶段注入带签名的皮肤属性，并在玩家进服后刷新玩家信息。如果客户端仍显示旧皮肤，可以尝试重新进服或清理皮肤缓存。
- 离线兜底：离线兜底行为可配置。推荐配置下，已经验证过的名字不能再被离线客户端复用。
- 注册表：TrueUUID 会把已验证过的名字记录在 `trueuuid-registry.json`。如果清空该文件，服务端会忘记之前的正版/Yggdrasil 绑定记录。

## 构建

Windows：

```powershell
.\gradlew.bat build
```

macOS/Linux：

```bash
./gradlew build
```

构建产物会输出到 `build/libs/`。

## 隐私

玩家 access token 永远不会发送给服务器。

客户端只在本地使用 token 调用 `joinServer`。服务端只接收认证结果，并自行通过 Mojang Session Server 或受支持的 Yggdrasil 接口验证 nonce。

## 许可证

GNU LGPL 3.0

## 致谢

- Mojang authlib and session API
- Sponge Mixin
- ForgeGradle
- NeoForge / ModDevGradle

---

维护者：[@YuWan-030](https://github.com/YuWan-030)
