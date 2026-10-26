<h1 align="center">
  <img src="https://raw.githubusercontent.com/YuWan-030/TrueUUID/main/TrueUUID-banner.svg" width="300" alt="TrueUUID">
</h1>

[English](README.md) | 简体中文

TrueUUID 在使用 `online-mode=false` 的 Minecraft 服务器上验证账号。
通过验证的玩家保留账号的 UUID、用户名和皮肤。
客户端只将 access token 发送给玩家的认证服务，不会发送给 Minecraft 服务端。

Forge、Fabric 和 NeoForge 需要客户端与服务端都安装 TrueUUID。
请选择与加载器和 Minecraft 版本完全匹配的 JAR。
Spigot 和 Paper 1.20.1 使用服务端插件，需要 ProtocolLib 5.1.0 或更新版本，
也支持未安装 TrueUUID 的正版客户端。

仓库包含 Minecraft 1.20.1 至 1.21.11 的 52 个精确 Mod 目标及两个服务端插件。
版本与测试记录请看[目标矩阵](docs/architecture/target-matrix.md)。

## 安装

1. 下载与加载器和 Minecraft 版本匹配的 JAR。
2. 将它放入客户端和服务端的 `mods` 文件夹。
   Spigot/Paper 服务端则将插件和 ProtocolLib 放入 `plugins` 文件夹。
3. 在 `server.properties` 中设置 `online-mode=false`。
4. 启动服务端生成配置，然后进入服务器。

徽标显示每位玩家的验证状态。服主决定是否允许没有登录会话的玩家加入。
已验证过的用户名默认受到保护。

配置、Prism Launcher、皮肤站和需确认的玩家数据迁移见[安装与配置指南](docs/setup.md)。
插件管理见[身份策略指南](docs/development/identity-policy-admin-guide.md)。

## 构建

使用 JDK 21 启动 Gradle。各目标仍使用自己声明的 Java 工具链。

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
export PATH="$JAVA_HOME/bin:$PATH"
./scripts/ci/build-target.sh forge-1.20.1
```

JAR 位于 `platform/forge/1.20.1/build/libs/`。
其他目标 ID 见 `release/targets.json`。
Fabric 请使用 `build/libs` 中的 remapped JAR。

## 开发

- [文档索引](docs/README.md)
- [模块边界与新增适配器](docs/development/adding-adapter.md)
- [本地客户端与服务端测试](docs/development/local-runtime-testing.md)
- [发布流程](docs/development/release-automation.md)

## 许可与致谢

代码采用 [LGPL-3.0](LICENSE)。锁图标由 F1xGOD 亲手绘制，
[署名后可用于非商业用途](branding/lock-icons.txt)，请勿商用。

维护者：[@YuWan-030](https://github.com/YuWan-030)、
[@wish131400](https://github.com/wish131400) 和
[@F1xGOD](https://github.com/F1xGOD)。
