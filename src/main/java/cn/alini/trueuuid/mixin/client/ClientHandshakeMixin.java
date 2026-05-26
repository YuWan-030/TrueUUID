package cn.alini.trueuuid.mixin.client;

import cn.alini.trueuuid.net.AuthAnswerPayload;
import cn.alini.trueuuid.net.AuthPayload;
import cn.alini.trueuuid.net.NetIds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.multiplayer.ClientHandshakePacketListenerImpl;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.login.ClientboundCustomQueryPacket;
import net.minecraft.network.protocol.login.ServerboundCustomQueryAnswerPacket;
import net.minecraft.network.protocol.login.custom.CustomQueryPayload;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@Mixin(ClientHandshakePacketListenerImpl.class)
public abstract class ClientHandshakeMixin {
    @Unique private static final HttpClient TRUEUUID$HTTP = HttpClient.newHttpClient();

    @Shadow private Connection connection;
    @Shadow private Consumer<Component> updateStatus;

    @Inject(method = "handleCustomQuery", at = @At("HEAD"), cancellable = true)
    private void trueuuid$onCustomQuery(ClientboundCustomQueryPacket packet, CallbackInfo ci) {
        CustomQueryPayload payload = packet.payload();
        if (!NetIds.AUTH.equals(payload.id())) return;
        if (!(payload instanceof AuthPayload authPayload)) return;
        String serverId = authPayload.serverId();

        Minecraft mc = Minecraft.getInstance();
        User user = mc.getUser();
        var profile = user.getProfileId();
        String token = user.getAccessToken();
        Connection loginConnection = this.connection;
        int transactionId = packet.transactionId();

        // dev/离线启动常见的占位 token 不可能通过 Mojang 校验，立即回失败，避免登录线程等到服务器超时。
        if (trueuuid$isMissingSessionToken(token)) {
            trueuuid$sendAuthAck(loginConnection, transactionId, false, "", false, true);
            ci.cancel();
            return;
        }

        // 在调用 joinServer 之前先读取 hasJoined URL，authlib-injector 会影响该端点。
        String hasJoinedUrl = trueuuid$resolveHasJoinedUrl();

        // 复用原版正版登录文案，中文客户端会显示“正在登录中...”。
        this.updateStatus.accept(Component.translatable("connect.authorizing"));

        // Mojang joinServer 可能因网络卡住；放到后台线程，保留原版登录等待界面，同时在服务端 30 秒超时前回包。
        CompletableFuture.supplyAsync(() -> {
                    try {
                        // 令牌只在本地使用
                        mc.getMinecraftSessionService().joinServer(profile, token, serverId);
                        return true;
                    } catch (Throwable t) {
                        if (hasJoinedUrl.isEmpty()) {
                            return trueuuid$joinMojangDirect(profile, token, serverId);
                        }
                        return false;
                    }
                })
                .orTimeout(24, TimeUnit.SECONDS)
                .exceptionally(t -> false)
                .thenAccept(ok -> {
                    if (!ok || !authPayload.offlineUpgradeAvailable()) {
                        trueuuid$sendAuthAck(loginConnection, transactionId, ok, hasJoinedUrl, false, false);
                        return;
                    }
                    trueuuid$confirmOfflinePlayerDataUpgrade(mc, authPayload, hasJoinedUrl, loginConnection, transactionId);
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
        connection.send(new ServerboundCustomQueryAnswerPacket(transactionId, new AuthAnswerPayload(ok, hasJoinedUrl, migrationConfirmed, missingSessionToken)));
    }

    @Unique
    private static void trueuuid$confirmOfflinePlayerDataUpgrade(Minecraft mc, AuthPayload payload, String hasJoinedUrl, Connection connection, int transactionId) {
        mc.execute(() -> {
            String mode = hasJoinedUrl == null || hasJoinedUrl.isBlank() ? "正版验证" : "皮肤站登录";
            Component title = Component.literal("确认迁移离线玩家数据");
            Component message = Component.literal(
                    "检测到同名离线玩家数据。\n\n"
                            + "当前登录方式：" + mode + "\n"
                            + "离线 UUID：" + payload.offlineUuid() + "\n"
                            + "可迁移数据：" + payload.offlineDataSummary() + "\n\n"
                            + "继续进入将先备份离线玩家数据，再迁移到当前账号。\n"
                            + "迁移后该名称将绑定当前账号，后续不再允许离线身份进入。"
            );
            mc.setScreen(new ConfirmScreen(
                    confirmed -> {
                        trueuuid$sendAuthAck(connection, transactionId, true, hasJoinedUrl, confirmed, false);
                        mc.setScreen(null);
                    },
                    title,
                    message,
                    Component.literal("确认迁移数据并进入"),
                    Component.literal("取消进入")
            ));
        });
    }

    @Unique
    private static void trueuuid$confirmOfflineUpgrade(Minecraft mc, AuthPayload payload, String hasJoinedUrl, Connection connection, int transactionId) {
        mc.execute(() -> {
            String mode = hasJoinedUrl == null || hasJoinedUrl.isBlank() ? "正版验证" : "皮肤站登录";
            Component title = Component.literal("确认迁移离线存档");
            Component message = Component.literal(
                    "检测到你曾以离线身份游玩过该名称。\n\n"
                            + "当前登录方式：" + mode + "\n"
                            + "离线 UUID：" + payload.offlineUuid() + "\n"
                            + "可迁移数据：" + payload.offlineDataSummary() + "\n\n"
                            + "继续进入将先备份离线存档，再迁移到当前账号。\n"
                            + "迁移后该名称将绑定当前账号，后续不再允许离线身份进入。"
            );
            mc.setScreen(new ConfirmScreen(
                    confirmed -> {
                        trueuuid$sendAuthAck(connection, transactionId, true, hasJoinedUrl, confirmed, false);
                        mc.setScreen(null);
                    },
                    title,
                    message,
                    Component.literal("确认迁移并进入"),
                    Component.literal("取消进入")
            ));
        });
    }

    @Unique
    private static boolean trueuuid$joinMojangDirect(java.util.UUID profile, String token, String serverId) {
        try {
            String body = "{"
                    + "\"accessToken\":\"" + trueuuid$jsonEscape(token) + "\","
                    + "\"selectedProfile\":\"" + profile.toString().replace("-", "") + "\","
                    + "\"serverId\":\"" + trueuuid$jsonEscape(serverId) + "\""
                    + "}";
            HttpRequest req = HttpRequest.newBuilder(URI.create(trueuuid$mojangUrl("sessionserver", "/session/minecraft/join")))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> resp = TRUEUUID$HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            return resp.statusCode() == 204;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * 解析客户端实际使用的 hasJoined 端点。
     * authlib-injector 通常通过 -javaagent:authlib-injector.jar=<API root> 注入，优先读取该参数，
     * 避免把客户端本机 127.0.0.1 代理地址发给服务端。
     */
    @Unique
    private static String trueuuid$resolveHasJoinedUrl() {
        String agentUrl = trueuuid$resolveHasJoinedUrlFromJavaAgent();
        if (!agentUrl.isEmpty()) return agentUrl;

        try {
            Class<?> clazz = Class.forName("com.mojang.authlib.yggdrasil.YggdrasilMinecraftSessionService");

            String url = trueuuid$readStaticUrlField(clazz, "CHECK_URL");
            if (url != null) return trueuuid$sanitizeUrl(url);

            for (Field f : clazz.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(f.getModifiers()) && URL.class.isAssignableFrom(f.getType())) {
                    f.setAccessible(true);
                    URL fieldVal = (URL) f.get(null);
                    if (fieldVal != null && fieldVal.toString().contains("hasJoined")) {
                        return trueuuid$sanitizeUrl(fieldVal.toString());
                    }
                }
            }

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
            // 反射失败时使用 Mojang 默认端点。
        }
        return "";
    }

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
        if (!root.endsWith("/")) {
            root = root + "/";
        }
        return trueuuid$sanitizeUrl(root + "sessionserver/session/minecraft/hasJoined");
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

    @Unique
    private static String trueuuid$sanitizeUrl(String rawUrl) {
        if (rawUrl == null || rawUrl.isEmpty()) return "";

        String url = rawUrl;
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

        if (url.contains("sessionserver.mojang.com")) {
            return "";
        }

        int qIdx = url.indexOf('?');
        if (qIdx >= 0) {
            url = url.substring(0, qIdx);
        }

        return url;
    }

    @Unique
    private static String trueuuid$mojangUrl(String service, String path) {
        return "https://" + service + "." + "mo" + "jang" + ".com" + path;
    }

    @Unique
    private static String trueuuid$jsonEscape(String value) {
        if (value == null) return "";
        StringBuilder out = new StringBuilder(value.length() + 8);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.toString();
    }
}
