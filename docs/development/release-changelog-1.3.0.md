## English

TrueUUID 1.3.0 is currently an unreleased development line. This changelog is
not a support claim and must be finalized only after every release gate passes.

### Development changes

- Added an unsupported, exact-version Spigot 1.20.1 candidate with
  client-assisted and unmodified-client premium authentication paths.
- Added plain-Java hybrid identity policy, bounded login coordination,
  server-owned collision handling, stable aliases, and fail-closed persistence.
- Hardened native loader login paths so silence, malformed responses, and
  failed premium proof cannot downgrade to offline admission.
- Added clearer private login feedback, administrative policy commands, and
  optional LuckPerms integration for the Spigot candidate.

### Supported downloads

- None are approved for 1.3.0 yet. The native-mod targets require fresh
  exact-artifact acceptance, and Spigot remains outside the release manifest.

## 中文

TrueUUID 1.3.0 目前仍是未发布的开发版本。本更新日志不代表支持声明；只有
全部发布关卡通过后才能定稿。

### 开发中更新

- 新增尚未受支持的 Spigot 1.20.1 精确版本候选插件，包含客户端辅助鉴权和
  无需 Mod 的原版正版鉴权路径。
- 新增纯 Java 的混合身份策略、有限登录协调、服务端名称冲突处理、稳定别名
  与失败即拒绝的持久化逻辑。
- 加固原生加载器登录路径，静默、畸形响应或正版证明失败均不得降级为离线
  放行。
- 为 Spigot 候选插件增加更清晰的私密登录提示、管理策略命令和可选的
  LuckPerms 集成。

### 支持的下载

- 1.3.0 目前没有已批准下载。原生 Mod 目标需要重新完成精确产物验收，
  Spigot 仍不在发布清单中。
