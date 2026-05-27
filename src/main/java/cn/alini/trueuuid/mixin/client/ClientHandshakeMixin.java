package cn.alini.trueuuid.mixin.client;

import cn.alini.trueuuid.net.NetIds;
import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.User; // official 映射 (mapping)
import net.minecraft.client.multiplayer.ClientHandshakePacketListenerImpl;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.login.ClientboundCustomQueryPacket;
import net.minecraft.network.protocol.login.ServerboundCustomQueryPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.lang.management.ManagementFactory;
import java.net.URL;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@Mixin(ClientHandshakePacketListenerImpl.class)
public abstract class ClientHandshakeMixin {
    @Shadow private Connection connection;
    @Shadow private Consumer<Component> updateStatus;

    @Inject(method = "handleCustomQuery", at = @At("HEAD"), cancellable = true)
    private void trueuuid$onCustomQuery(ClientboundCustomQueryPacket packet, CallbackInfo ci) {
        if (!NetIds.AUTH.equals(packet.getIdentifier())) return;

        FriendlyByteBuf buf = packet.getData();
        String serverId = buf.readUtf();
        boolean offlineUpgradeAvailable = false;
        String offlineUuid = "";
        String offlineDataSummary = "";
        try {
            if (buf.isReadable()) {
                offlineUpgradeAvailable = buf.readBoolean();
                if (offlineUpgradeAvailable) {
                    offlineUuid = buf.readUtf();
                    offlineDataSummary = buf.readUtf();
                }
            }
        } catch (Throwable ignored) {
            offlineUpgradeAvailable = false;
            offlineUuid = "";
            offlineDataSummary = "";
        }

        Minecraft mc = Minecraft.getInstance();
        User user = mc.getUser();
        var profile = user.getGameProfile();
        String token = user.getAccessToken();
        Connection loginConnection = this.connection;
        int transactionId = packet.getTransactionId();

        // dev/离线启动常见的占位 token 不可能通过 Mojang 校验，立即回失败，避免登录线程等到服务器超时。
        if (trueuuid$isMissingSessionToken(token)) {
            trueuuid$sendAuthAck(loginConnection, transactionId, false, "", false, true);
            ci.cancel();
            return;
        }

        // 在调用 joinServer 之前先读取 CHECK_URL，因为 joinServer 是一次性的。
        String hasJoinedUrl = trueuuid$resolveHasJoinedUrl();
        final boolean upgradeAvailable = offlineUpgradeAvailable;
        final String upgradeOfflineUuid = offlineUuid;
        final String upgradeDataSummary = offlineDataSummary;

        // 复用原版正版登录文案，中文客户端会显示"正在登录中..."。
        this.updateStatus.accept(Component.translatable("connect.authorizing"));

        // Mojang joinServer 可能因网络卡住；放到后台线程，保留原版登录等待界面，同时在服务端 30 秒超时前回包。
        CompletableFuture.supplyAsync(() -> {
                    try {
                        // 令牌只在本地使用 (Token is only used locally)
                        mc.getMinecraftSessionService().joinServer(profile, token, serverId);
                        return true;
                    } catch (Throwable t) {
                        return false;
                    }
                })
                .orTimeout(30, TimeUnit.SECONDS)
                .exceptionally(t -> false)
                .thenAccept(ok -> {
                    if (!ok || !upgradeAvailable) {
                        trueuuid$sendAuthAck(loginConnection, transactionId, ok, hasJoinedUrl, false, false);
                        return;
                    }
                    trueuuid$confirmOfflinePlayerDataUpgrade(mc, upgradeOfflineUuid, upgradeDataSummary, hasJoinedUrl, loginConnection, transactionId);
                });

        ci.cancel();
    }

    @Unique
    private static boolean trueuuid$isMissingSessionToken(String token) {
        // 这些值通常来自开发环境或离线启动器，继续请求 Mojang 只会制造无意义等待。
        return token == null || token.isBlank() || "0".equals(token);
    }

    @Unique
    private static void trueuuid$sendAuthAck(Connection connection, int transactionId, boolean ok, String hasJoinedUrl, boolean migrationConfirmed, boolean missingSessionToken) {
        // LOGIN 自定义查询必须回复 LOGIN 阶段的 ServerboundCustomQueryPacket，不能切到 PLAY 包。
        FriendlyByteBuf resp = new FriendlyByteBuf(Unpooled.buffer());
        resp.writeBoolean(ok);
        // 附带 hasJoined URL：空字符串表示使用 Mojang 默认。
        resp.writeUtf(hasJoinedUrl != null ? hasJoinedUrl : "");
        resp.writeBoolean(migrationConfirmed);
        resp.writeBoolean(missingSessionToken);
        connection.send(new ServerboundCustomQueryPacket(transactionId, resp));
    }

    @Unique
    private static void trueuuid$confirmOfflinePlayerDataUpgrade(Minecraft mc, String offlineUuid, String offlineDataSummary, String hasJoinedUrl, Connection connection, int transactionId) {
        mc.execute(() -> {
            String mode = hasJoinedUrl == null || hasJoinedUrl.isBlank() ? "正版验证" : "皮肤站登录";
            Component title = Component.literal("确认迁移离线玩家数据");
            Component message = Component.literal(
                    "检测到同名离线玩家数据。\n\n"
                            + "当前登录方式: " + mode + "\n"
                            + "离线 UUID: " + offlineUuid + "\n"
                            + "可迁移数据: " + offlineDataSummary + "\n\n"
                            + "继续进入会先备份离线玩家数据，再迁移到当前账号。迁移后该名称会绑定当前账号。"
            );
            mc.setScreen(new ConfirmScreen(
                    confirmed -> {
                        trueuuid$sendAuthAck(connection, transactionId, true, hasJoinedUrl, confirmed, false);
                        mc.setScreen(null);
                    },
                    title,
                    message,
                    Component.literal("确认迁移数据并进入"),
                    Component.literal("暂不迁移并退出")
            ));
        });
    }

    @Unique
    private static void trueuuid$confirmOfflineUpgrade(Minecraft mc, String offlineUuid, String offlineDataSummary, String hasJoinedUrl, Connection connection, int transactionId) {
        mc.execute(() -> {
            String mode = hasJoinedUrl == null || hasJoinedUrl.isBlank() ? "正版验证" : "皮肤站登录";
            Component title = Component.literal("确认迁移离线存档");
            Component message = Component.literal(
                    "检测到同名离线存档。\n\n"
                            + "当前登录方式: " + mode + "\n"
                            + "离线 UUID: " + offlineUuid + "\n"
                            + "可迁移数据: " + offlineDataSummary + "\n\n"
                            + "继续进入会先备份离线存档，再迁移到当前账号。迁移后该名称会绑定当前账号。"
            );
            mc.setScreen(new ConfirmScreen(
                    confirmed -> {
                        trueuuid$sendAuthAck(connection, transactionId, true, hasJoinedUrl, confirmed, false);
                        mc.setScreen(null);
                    },
                    title,
                    message,
                    Component.literal("确认迁移并进入"),
                    Component.literal("暂不迁移并退出")
            ));
        });
    }

    /**
     * 反射读取 YggdrasilMinecraftSessionService 中的 CHECK_URL 静态字段。
     * authlib-injector 在类加载时通过字节码修改将该字段替换为皮肤站的 URL。
     * 若未被注入（正版 Mojang），返回空字符串，服务端将使用默认 Mojang URL。
     */
    @Unique
    private static String trueuuid$resolveHasJoinedUrl() {
        String agentUrl = trueuuid$resolveHasJoinedUrlFromJavaAgent();
        if (!agentUrl.isEmpty()) return agentUrl;

        try {
            Class<?> clazz = Class.forName("com.mojang.authlib.yggdrasil.YggdrasilMinecraftSessionService");

            // 尝试读取 CHECK_URL（旧版 authlib 1.5.x，MC 1.20.x 常见）
            String url = trueuuid$readStaticUrlField(clazz, "CHECK_URL");
            if (url != null) return trueuuid$sanitizeUrl(url);

            // 某些版本可能叫 WHOIS_URL 或字段名变化，遍历所有 static URL 字段寻找 hasJoined
            for (Field f : clazz.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(f.getModifiers()) && URL.class.isAssignableFrom(f.getType())) {
                    f.setAccessible(true);
                    URL fieldVal = (URL) f.get(null);
                    if (fieldVal != null && fieldVal.toString().contains("hasJoined")) {
                        return trueuuid$sanitizeUrl(fieldVal.toString());
                    }
                }
            }

            // 也尝试 String 类型的静态字段（新版 authlib 可能用 String 而非 URL）
            for (Field f : clazz.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(f.getModifiers()) && String.class.isAssignableFrom(f.getType())) {
                    f.setAccessible(true);
                    Object val = f.get(null);
                    if (val instanceof String s && s.contains("hasJoined")) {
                        return trueuuid$sanitizeUrl(s);
                    }
                }
            }
        } catch (Throwable ignored) {
            // 反射失败（类不存在等情况），返回空字符串使用 Mojang 默认
        }
        return "";
    }

    /**
     * PCL/HMCL 等启动器会以 -javaagent:authlib-injector.jar=<API root> 启动客户端。
     * 这是比 CHECK_URL 更可靠的来源：CHECK_URL 在部分 authlib-injector 版本中可能被替换为
     * 127.0.0.1 本地代理地址，服务端无法访问该客户端本地代理。
     */
    @Unique
    private static String trueuuid$resolveHasJoinedUrlFromJavaAgent() {
        try {
            List<String> args = ManagementFactory.getRuntimeMXBean().getInputArguments();
            for (String arg : args) {
                if (!arg.startsWith("-javaagent:")) continue;
                String lower = arg.toLowerCase(java.util.Locale.ROOT);
                if (!lower.contains("authlib")) continue;

                int equals = arg.indexOf('=');
                if (equals < 0 || equals + 1 >= arg.length()) continue;

                String apiRoot = arg.substring(equals + 1).trim();
                String hasJoined = trueuuid$buildHasJoinedUrlFromApiRoot(apiRoot);
                if (!hasJoined.isEmpty()) return hasJoined;
            }
        } catch (Throwable ignored) {
            // 无法读取 JVM 参数时回退到 CHECK_URL 反射。
        }
        return "";
    }

    @Unique
    private static String trueuuid$buildHasJoinedUrlFromApiRoot(String apiRoot) {
        if (apiRoot == null || apiRoot.isBlank()) return "";
        String root = apiRoot.trim();

        // authlib-injector 的 API root 通常是 https://example.com/api/yggdrasil 或类似形式。
        // 若启动器传了末尾斜杠，直接拼 sessionserver；否则补一个斜杠。
        if (!root.endsWith("/")) {
            root = root + "/";
        }

        String hasJoined = root + "sessionserver/session/minecraft/hasJoined";
        return trueuuid$sanitizeUrl(hasJoined);
    }

    @Unique
    private static String trueuuid$readStaticUrlField(Class<?> clazz, String fieldName) {
        try {
            Field f = clazz.getDeclaredField(fieldName);
            f.setAccessible(true);
            Object val = f.get(null);
            if (val instanceof URL u) return u.toString();
            if (val instanceof String s) return s;
        } catch (Throwable ignored) {}
        return null;
    }

    /**
     * 处理 authlib-injector 本地代理格式。
     * 代理 URL 形如: http://127.0.0.1:{port}/https/{真实域名}{路径}
     * 提取真实的 https URL 返回给服务端。
     * 如果是标准 Mojang URL（sessionserver.mojang.com），返回空字符串。
     */
    @Unique
    private static String trueuuid$sanitizeUrl(String rawUrl) {
        if (rawUrl == null || rawUrl.isEmpty()) return "";

        String url = rawUrl;

        // 处理 authlib-injector 本地代理格式: http://127.0.0.1:{port}/https/{domain}{path}
        // 注意：如果解析后仍是 sessionserver.mojang.com，说明只有代理目标域名，没有皮肤站 API root；
        // 这种情况不能发给服务端，只能返回空字符串走 Mojang 默认或 JavaAgent 参数解析结果。
        if (url.startsWith("http://127.0.0.1") || url.startsWith("http://localhost")) {
            int schemeIdx = url.indexOf("/https/");
            if (schemeIdx >= 0) {
                url = "https://" + url.substring(schemeIdx + "/https/".length());
            } else {
                int httpIdx = url.indexOf("/http/");
                if (httpIdx >= 0) {
                    url = "http://" + url.substring(httpIdx + "/http/".length());
                }
            }
        }

        // 如果是 Mojang 默认 URL，返回空（让服务端使用默认值，不暴露无意义信息）
        if (url.contains("sessionserver.mojang.com")) {
            return "";
        }

        // 剥离查询参数，只保留 base URL（到 hasJoined 为止）
        int qIdx = url.indexOf('?');
        if (qIdx >= 0) {
            url = url.substring(0, qIdx);
        }

        return url;
    }

}
