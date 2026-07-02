package cn.alini.trueuuid.config;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;

import java.util.List;

public final class TrueuuidConfig {
    public static final ForgeConfigSpec COMMON_SPEC;
    public static final Common COMMON;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        COMMON = new Common(b);
        COMMON_SPEC = b.build();
    }

    public static void register() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, COMMON_SPEC);
    }

    public static long timeoutMs() { return COMMON.timeoutMs.get(); }
    public static boolean allowOfflineOnTimeout() { return COMMON.allowOfflineOnTimeout.get(); }

    // 旧开关：保留兼容，但新策略将更细化 (Old switch: Keep for compatibility, but new strategy will be more granular)
    public static boolean allowOfflineOnFailure() { return COMMON.allowOfflineOnFailure.get(); }

    public static String timeoutKickMessage() { return COMMON.timeoutKickMessage.get(); }
    public static String offlineFallbackMessage() { return COMMON.offlineFallbackMessage.get(); }

    // 新增：短副标题（用于屏幕 Title 区域） (Added: Short subtitle (for screen Title area))
    public static String offlineShortSubtitle() { return COMMON.offlineShortSubtitle.get(); }
    public static String onlineShortSubtitle() { return COMMON.onlineShortSubtitle.get(); }
    public static boolean showJoinFeedback() { return COMMON.showJoinFeedback.get(); }

    // 新增：策略相关 (Added: Strategy related)
    public static boolean knownPremiumDenyOffline() { return COMMON.knownPremiumDenyOffline.get(); }
    public static boolean allowOfflineForUnknownOnly() { return COMMON.allowOfflineForUnknownOnly.get(); }
    public static boolean recentIpGraceEnabled() { return COMMON.recentIpGraceEnabled.get(); }
    public static int recentIpGraceTtlSeconds() { return COMMON.recentIpGraceTtlSeconds.get(); }
    public static boolean debug() { return COMMON.debug.get(); }

    // authlib-injector / Yggdrasil 皮肤站支持
    @SuppressWarnings("unchecked")
    public static List<String> apiRootWhitelist() { return (List<String>) COMMON.apiRootWhitelist.get(); }

    public static final class Common {
        public final ForgeConfigSpec.LongValue timeoutMs;
        public final ForgeConfigSpec.BooleanValue allowOfflineOnTimeout;
        public final ForgeConfigSpec.BooleanValue allowOfflineOnFailure;
        public final ForgeConfigSpec.ConfigValue<String> timeoutKickMessage;
        public final ForgeConfigSpec.ConfigValue<String> offlineFallbackMessage;

        // 新增 (Added)
        public final ForgeConfigSpec.ConfigValue<String> offlineShortSubtitle;
        public final ForgeConfigSpec.ConfigValue<String> onlineShortSubtitle;
        public final ForgeConfigSpec.BooleanValue showJoinFeedback;

        // authlib-injector / Yggdrasil 皮肤站白名单
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> apiRootWhitelist;

        // 新增：策略相关 (Added: Strategy related)
        public final ForgeConfigSpec.BooleanValue knownPremiumDenyOffline;
        public final ForgeConfigSpec.BooleanValue allowOfflineForUnknownOnly;
        public final ForgeConfigSpec.BooleanValue recentIpGraceEnabled;
        public final ForgeConfigSpec.IntValue recentIpGraceTtlSeconds;
        public final ForgeConfigSpec.BooleanValue debug;

        Common(ForgeConfigSpec.Builder b) {
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

            // 默认短、不占屏 (Default short, does not occupy screen)
            offlineShortSubtitle = b.define("offlineShortSubtitle", "trueuuid.subtitle.offline");
            onlineShortSubtitle  = b.define("onlineShortSubtitle",  "trueuuid.subtitle.online");
            showJoinFeedback = b.comment("是否在玩家进服后显示登录状态提示。/ Show join feedback after a player joins. 关闭后不再发送正版/皮肤站/离线/单人模式 Title，也不发送离线兜底聊天提示；不影响皮肤刷新和鉴权逻辑。/ When disabled, no premium/skin-site/offline/single-player Title or offline fallback chat message is sent; authentication and skin refresh are unchanged.")
                    .define("showJoinFeedback", true);

            // 策略项 (Strategy items)
            knownPremiumDenyOffline   = b.comment("一旦该名字成功验证过正版/皮肤站，后续鉴权失败时禁止以离线身份进入。/ Once a name has been verified as premium/skin-site, deny later offline fallback for that name.")
                    .define("knownPremiumDenyOffline", true);
            allowOfflineForUnknownOnly = b.comment("仅对从未验证为正版/皮肤站的新名字允许离线兜底。/ Only allow offline fallback for names that have never been verified as premium/skin-site.")
                    .define("allowOfflineForUnknownOnly", true);
            recentIpGraceEnabled      = b.comment("启用“退出后同 IP 短时重连”容错，只在玩家退出后的 TTL 秒内临时沿用上次认证来源。/ Enable short same-IP reconnect grace after logout, reusing the last verified identity only within the TTL window.")
                    .define("recentIpGrace.enabled", true);
            recentIpGraceTtlSeconds   = b.comment("退出游戏后允许同 IP 容错重连的秒数。默认 10 秒，避免长期误导为皮肤站/正版登录。/ Same-IP grace seconds after logout. Default is 10 seconds to avoid long-lived misleading premium/skin-site identity.")
                    .defineInRange("recentIpGrace.ttlSeconds", 10, 1, 60);
            debug = b.comment("启用调试日志输出。/ Enable debug logging.").define("debug", false);

            apiRootWhitelist = b.comment(
                    "authlib-injector 皮肤站域名白名单。/ Whitelist for authlib-injector skin-site domains.",
                    "留空(默认)表示信任客户端上报的任何皮肤站 URL。/ Empty by default: trust any skin-site URL reported by the client.",
                    "配置后，只有 URL 中包含白名单条目的皮肤站才会被接受。/ When configured, only URLs containing a whitelist entry are accepted.",
                    "例如 / Example: [\"littleskin.cn\", \"skin.example.com\"]"
            ).defineListAllowEmpty(List.of("yggdrasil.apiRootWhitelist"), List::of, o -> o instanceof String);

            b.pop();
        }
    }

    private TrueuuidConfig() {}

}
