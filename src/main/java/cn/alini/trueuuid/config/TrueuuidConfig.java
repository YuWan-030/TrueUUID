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
            allowOfflineOnTimeout = b.comment("false: kick on timeout. true: allow offline fallback on timeout. / false: 超时踢出。true: 超时允许离线兜底。").define("allowOfflineOnTimeout", false);
            allowOfflineOnFailure = b.comment("false: kick on authentication failure. true: allow offline fallback on authentication failure. / false: 鉴权失败时踢出。true: 鉴权失败时允许离线兜底。").define("allowOfflineOnFailure", true);

            timeoutKickMessage = b.comment("Kick message on authentication timeout. Use a trueuuid.* translation key for client-side localization, or enter plain text to force a custom server message. / 鉴权超时踢出消息。使用 trueuuid.* 翻译键可按客户端语言显示，也可填写纯文本强制使用服务器消息。")
                    .define("timeoutKickMessage", "trueuuid.disconnect.timeout");
            offlineFallbackMessage = b.comment("Offline fallback chat message. Keep the default trueuuid.* key to use each player's game language. / 离线兜底聊天提示。保留默认 trueuuid.* 翻译键可按每个玩家的游戏语言显示。")
                    .define(
                    "offlineFallbackMessage",
                    "trueuuid.chat.offline_fallback"
            );

            // Default subtitles are short and localized by the client language.
            offlineShortSubtitle = b.comment("Offline fallback subtitle. Keep the default trueuuid.* key to use each player's game language. / 离线兜底副标题。保留默认 trueuuid.* 翻译键可按每个玩家的游戏语言显示。")
                    .define("offlineShortSubtitle", "trueuuid.subtitle.offline");
            onlineShortSubtitle  = b.comment("Premium login subtitle. Keep the default trueuuid.* key to use each player's game language. / 正版登录副标题。保留默认 trueuuid.* 翻译键可按每个玩家的游戏语言显示。")
                    .define("onlineShortSubtitle",  "trueuuid.subtitle.online");
            showJoinFeedback = b.comment("Show join feedback after a player joins. When disabled, no premium/skin-site/offline/single-player title or offline fallback chat message is sent; authentication and skin refresh are unchanged. / 玩家进服后显示登录状态提示。关闭后不再发送正版、皮肤站、离线、单人模式标题，也不发送离线兜底聊天提示；不影响鉴权和皮肤刷新。")
                    .define("showJoinFeedback", true);

            // 策略项 (Strategy items)
            knownPremiumDenyOffline   = b.comment("Once a name has been verified as premium/skin-site, deny later offline fallback for that name. / 一旦该名字成功验证过正版或皮肤站，后续鉴权失败时禁止以离线身份进入。")
                    .define("knownPremiumDenyOffline", true);
            allowOfflineForUnknownOnly = b.comment("Only allow offline fallback for names that have never been verified as premium/skin-site. / 仅对从未验证为正版或皮肤站的新名字允许离线兜底。")
                    .define("allowOfflineForUnknownOnly", true);
            recentIpGraceEnabled      = b.comment("Enable short same-IP reconnect grace after logout, reusing the last verified identity only within the TTL window. / 启用退出后同 IP 短时重连容错，只在 TTL 窗口内临时沿用上次认证身份。")
                    .define("recentIpGrace.enabled", true);
            recentIpGraceTtlSeconds   = b.comment("Same-IP grace seconds after logout. Default is 10 seconds to avoid long-lived misleading premium/skin-site identity. / 退出游戏后允许同 IP 容错重连的秒数。默认 10 秒，避免长期误导为正版或皮肤站登录。")
                    .defineInRange("recentIpGrace.ttlSeconds", 10, 1, 60);
            debug = b.comment("Enable debug logging. / 启用调试日志输出。").define("debug", false);

            apiRootWhitelist = b.comment(
                    "Whitelist for authlib-injector skin-site domains. / authlib-injector 皮肤站域名白名单。",
                    "Empty by default: trust any skin-site URL reported by the client. / 留空表示信任客户端上报的任何皮肤站 URL。",
                    "When configured, only skin-site hosts matching a whitelist entry are accepted. / 配置后，只有主机名匹配白名单条目的皮肤站才会被接受。",
                    "Example: [\"littleskin.cn\", \"skin.example.com\"] / 示例: [\"littleskin.cn\", \"skin.example.com\"]"
            ).defineListAllowEmpty(List.of("yggdrasil", "apiRootWhitelist"), List::of, o -> o instanceof String);

            b.pop();
        }
    }

    private TrueuuidConfig() {}

}
