package cn.alini.trueuuid.config;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;

import java.util.List;

public final class TrueuuidConfig {
    public static final ForgeConfigSpec COMMON_SPEC;
    public static final Common COMMON;
    private static final List<String> OVERLAY_CORNERS = List.of(
            "top_left", "top_right", "bottom_left", "bottom_right");

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
    public static boolean showAccountOverlay() { return COMMON.showAccountOverlay.get(); }
    public static String overlayCorner() { return COMMON.overlayCorner.get(); }
    public static int overlayOffsetX() { return COMMON.overlayOffsetX.get(); }
    public static int overlayOffsetY() { return COMMON.overlayOffsetY.get(); }
    public static float overlayScale() { return COMMON.overlayScale.get().floatValue(); }

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
        public final ForgeConfigSpec.BooleanValue showAccountOverlay;
        public final ForgeConfigSpec.ConfigValue<String> overlayCorner;
        public final ForgeConfigSpec.IntValue overlayOffsetX;
        public final ForgeConfigSpec.IntValue overlayOffsetY;
        public final ForgeConfigSpec.DoubleValue overlayScale;

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

            timeoutKickMessage = b.comment("Translation key for authentication timeout. Non-trueuuid values fall back to the localized default. / 鉴权超时翻译键。非 trueuuid 值会回退到本地化默认文案。")
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
            showAccountOverlay = b.comment("Show a small client account-status overlay after a TrueUUID login handshake. / 在 TrueUUID 登录握手后显示小型客户端账号状态覆盖层。")
                    .define("showAccountOverlay", true);
            // Keep these identical to the modern Forge line (platform/forge-common)
            // so the badge behaves the same on every supported target.
            overlayCorner = b.comment("Screen corner for the account-status badge: top_left, top_right, bottom_left, bottom_right. Default bottom_right: vanilla keeps status effects and advancement toasts in the top right, chat in the bottom left, and mods commonly take the top left. / 账号状态角标所在屏幕角落。默认 bottom_right。")
                    .define("overlayCorner", "bottom_right",
                            value -> value instanceof String && OVERLAY_CORNERS.contains(value));
            overlayOffsetX = b.comment("Extra horizontal pixels for the badge, to dodge another mod's HUD. Positive moves right. / 角标水平像素偏移，正值向右。")
                    .defineInRange("overlayOffsetX", 0, -4096, 4096);
            overlayOffsetY = b.comment("Extra vertical pixels for the badge, to dodge another mod's HUD. Positive moves down. / 角标垂直像素偏移，正值向下。")
                    .defineInRange("overlayOffsetY", 0, -4096, 4096);
            overlayScale = b.comment("Size of the account-status badge, scaling the padlock and label together. Whole numbers (1.0, 2.0) keep the bitmap font crisp. / 账号状态角标缩放，整数值字体最清晰。")
                    .defineInRange("overlayScale", 1.0D, 0.5D, 4.0D);

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
                    "Empty by default: reject all client-reported URLs and use Mojang only. / 默认留空：拒绝所有客户端上报 URL，仅使用 Mojang。",
                    "Only exact hosts are accepted; use *.example.com explicitly for subdomains. / 仅接受精确主机名；如需子域名请明确填写 *.example.com。",
                    "Custom endpoints must use HTTPS port 443, an allowed hasJoined path, and public DNS addresses. / 自定义接口必须使用 HTTPS 443、允许的 hasJoined 路径和公网 DNS 地址。"
            ).defineListAllowEmpty(List.of("yggdrasil", "apiRootWhitelist"), List::of, o -> o instanceof String);

            b.pop();
        }
    }

    private TrueuuidConfig() {}

}
