package cn.alini.trueuuid.config;


import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

public final class TrueuuidConfig {
    public static final ModConfigSpec COMMON_SPEC;
    public static final Common COMMON;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        COMMON = new Common(b);
        COMMON_SPEC = b.build();
    }

    public static void register() {
        ModLoadingContext.get().getActiveContainer().registerConfig(ModConfig.Type.COMMON, COMMON_SPEC);
    }

    public static long timeoutMs() { return COMMON.timeoutMs.get(); }
    public static boolean allowOfflineOnTimeout() { return COMMON.allowOfflineOnTimeout.get(); }

    // 旧开关：保留兼容，但新策略将更细化
    public static boolean allowOfflineOnFailure() { return COMMON.allowOfflineOnFailure.get(); }

    public static String timeoutKickMessage() { return COMMON.timeoutKickMessage.get(); }
    public static String offlineFallbackMessage() { return COMMON.offlineFallbackMessage.get(); }

    // 新增：短副标题（用于屏幕 Title 区域）
    public static String offlineShortSubtitle() { return COMMON.offlineShortSubtitle.get(); }
    public static String onlineShortSubtitle() { return COMMON.onlineShortSubtitle.get(); }
    public static boolean showJoinFeedback() { return COMMON.showJoinFeedback.get(); }

    // 新增：策略相关
    public static boolean knownPremiumDenyOffline() { return COMMON.knownPremiumDenyOffline.get(); }
    public static boolean allowOfflineForUnknownOnly() { return COMMON.allowOfflineForUnknownOnly.get(); }
    public static boolean recentIpGraceEnabled() { return COMMON.recentIpGraceEnabled.get(); }
    public static int recentIpGraceTtlSeconds() { return COMMON.recentIpGraceTtlSeconds.get(); }
    public static boolean debug() { return COMMON.debug.get(); }
    public static String mojangReverseProxy() { return COMMON.mojangReverseProxy.get(); }
    @SuppressWarnings("unchecked")
    public static List<String> apiRootWhitelist() { return (List<String>) COMMON.apiRootWhitelist.get(); }

    public static final class Common {
        public final ModConfigSpec.LongValue timeoutMs;
        public final ModConfigSpec.BooleanValue allowOfflineOnTimeout;
        public final ModConfigSpec.BooleanValue allowOfflineOnFailure;
        public final ModConfigSpec.ConfigValue<String> timeoutKickMessage;
        public final ModConfigSpec.ConfigValue<String> offlineFallbackMessage;

        // 新增
        public final ModConfigSpec.ConfigValue<String> offlineShortSubtitle;
        public final ModConfigSpec.ConfigValue<String> onlineShortSubtitle;
        public final ModConfigSpec.BooleanValue showJoinFeedback;

        public final ModConfigSpec.ConfigValue<String> mojangReverseProxy;
        public final ModConfigSpec.ConfigValue<List<? extends String>> apiRootWhitelist;

        // 新增：策略相关
        public final ModConfigSpec.BooleanValue knownPremiumDenyOffline;
        public final ModConfigSpec.BooleanValue allowOfflineForUnknownOnly;
        public final ModConfigSpec.BooleanValue recentIpGraceEnabled;
        public final ModConfigSpec.IntValue recentIpGraceTtlSeconds;
        public final ModConfigSpec.BooleanValue debug;

        Common(ModConfigSpec.Builder b) {
            b.push("auth");

            timeoutMs = b.defineInRange("timeoutMs", 30_000L, 1_000L, 600_000L);
            allowOfflineOnTimeout = b.comment("false: 超时踢出 / Kick on timeout. true: 超时放行为离线 / Allow offline fallback on timeout.").define("allowOfflineOnTimeout", false);
            allowOfflineOnFailure = b.comment("false: 失败时踢出 / Kick on failure. true: 鉴权失败放行为离线 / Allow offline fallback on authentication failure.").define("allowOfflineOnFailure", true);

            timeoutKickMessage = b.comment("Kick message on authentication timeout. Use a trueuuid.* translation key for client-side localization, or enter plain text to force a custom server message.")
                    .define("timeoutKickMessage", "trueuuid.disconnect.timeout");
            offlineFallbackMessage = b.define(
                    "offlineFallbackMessage",
                    "trueuuid.chat.offline_fallback"
            );

            // 默认短、不占屏
            offlineShortSubtitle = b.define("offlineShortSubtitle", "trueuuid.subtitle.offline");
            onlineShortSubtitle  = b.define("onlineShortSubtitle",  "trueuuid.subtitle.online");
            showJoinFeedback = b.comment("是否在玩家进服后显示登录状态提示。/ Show join feedback after a player joins. 关闭后不再发送正版/皮肤站/离线/单人模式 Title，也不发送离线兜底聊天提示；不影响皮肤刷新和鉴权逻辑。/ When disabled, no premium/skin-site/offline/single-player Title or offline fallback chat message is sent; authentication and skin refresh are unchanged.")
                    .define("showJoinFeedback", true);

            // 策略项
            knownPremiumDenyOffline   = b.comment("一旦该名字成功验证过正版/皮肤站，后续鉴权失败时禁止以离线身份进入。/ Once a name has been verified as premium/skin-site, deny later offline fallback for that name.")
                    .define("knownPremiumDenyOffline", true);
            allowOfflineForUnknownOnly = b.comment("仅对从未验证为正版/皮肤站的新名字允许离线兜底。/ Only allow offline fallback for names that have never been verified as premium/skin-site.")
                    .define("allowOfflineForUnknownOnly", true);
            recentIpGraceEnabled      = b.comment("启用“退出后同 IP 短时重连”容错，只在玩家退出后的 TTL 秒内临时沿用上次认证来源。/ Enable short same-IP reconnect grace after logout, reusing the last verified identity only within the TTL window.")
                    .define("recentIpGrace.enabled", true);
            recentIpGraceTtlSeconds   = b.comment("退出游戏后允许同 IP 容错重连的秒数。默认 10 秒，避免长期误导为皮肤站/正版登录。/ Same-IP grace seconds after logout. Default is 10 seconds to avoid long-lived misleading premium/skin-site identity.")
                    .defineInRange("recentIpGrace.ttlSeconds", 10, 1, 60);
            debug = b.comment("启用调试日志输出。/ Enable debug logging.").define("debug", false);
            mojangReverseProxy = b.comment("Mojang 会话服务器地址，可改为反代地址。/ Mojang Session Server endpoint. You may change this to a reverse proxy if needed.").define("mojangReverseProxy", "https://sessionserver.mojang.com");
            apiRootWhitelist = b.comment(
                            "Whitelist for authlib-injector/Yggdrasil skin-site domains. / authlib-injector/Yggdrasil 皮肤站域名白名单。",
                            "Empty by default: trust any skin-site URL reported by the client. / 留空表示信任客户端上报的任何皮肤站 URL。",
                            "When configured, only skin-site hosts matching a whitelist entry are accepted. / 配置后，只有主机名匹配白名单条目的皮肤站才会被接受。",
                            "Example: [\"littleskin.cn\", \"skin.example.com\"] / 示例: [\"littleskin.cn\", \"skin.example.com\"]"
                    )
                    .defineListAllowEmpty(List.of("yggdrasil", "apiRootWhitelist"), List::of, o -> o instanceof String);
            b.pop();
        }
    }

    private TrueuuidConfig() {}

}
