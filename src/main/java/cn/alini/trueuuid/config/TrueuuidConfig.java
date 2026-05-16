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

    // 新增：策略相关
    public static boolean knownPremiumDenyOffline() { return COMMON.knownPremiumDenyOffline.get(); }
    public static boolean allowOfflineForUnknownOnly() { return COMMON.allowOfflineForUnknownOnly.get(); }
    public static boolean recentIpGraceEnabled() { return COMMON.recentIpGraceEnabled.get(); }
    public static int recentIpGraceTtlSeconds() { return COMMON.recentIpGraceTtlSeconds.get(); }
    public static boolean debug() { return COMMON.debug.get(); }
    // 新增 nomojang 开关访问器
    public static boolean nomojangEnabled() { return COMMON.nomojangEnabled.get(); }
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

        // 新增 nomojang 配置
        public final ModConfigSpec.BooleanValue nomojangEnabled;
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
            allowOfflineOnTimeout = b.comment("false:超时踢出(默认)true:超时放行为离线").define("allowOfflineOnTimeout", false);
            allowOfflineOnFailure = b.comment("false:失败时踢出true:任何鉴权失败放行为离线(默认)").define("allowOfflineOnFailure", true);

            timeoutKickMessage = b.define("timeoutKickMessage", "登录超时，未完成账号校验");
            offlineFallbackMessage = b.define(
                    "offlineFallbackMessage",
                    "注意：你当前以离线模式进入服务器；如果你是正版账号，可能是网络原因导致无法成功鉴权，请重新登陆重试。继续游玩，若后续鉴权成功可能会丢失玩家数据。"
            );

            // 默认短、不占屏
            offlineShortSubtitle = b.define("offlineShortSubtitle", "鉴权失败：离线模式");
            onlineShortSubtitle  = b.define("onlineShortSubtitle",  "已通过正版校验");

            // 策略项
            knownPremiumDenyOffline   = b.comment("一旦该名字成功验证过正版，后续鉴权失败时禁止以离线身份进入。")
                    .define("knownPremiumDenyOffline", true);
            allowOfflineForUnknownOnly = b.comment("仅对从未验证为正版的新名字允许离线兜底。")
                    .define("allowOfflineForUnknownOnly", true);
            recentIpGraceEnabled      = b.comment("启用“退出后同 IP 短时重连”容错，只在玩家退出后的 TTL 秒内临时沿用上次认证来源。")
                    .define("recentIpGrace.enabled", true);
            recentIpGraceTtlSeconds   = b.comment("退出游戏后允许同 IP 容错重连的秒数。默认 10 秒，避免长期误导为皮肤站/正版登录。")
                    .defineInRange("recentIpGrace.ttlSeconds", 10, 1, 60);
            debug = b.comment("启用调试日志输出").define("debug", false);
            // 新增：跳过 Mojang 会话认证（开启后不再通过 sessionserver 验证）
            nomojangEnabled = b.comment("开启后关闭对 Mojang 会话服务的在线校验逻辑；同 IP 且近期有正版成功的名称按正版 UUID 处理，其余直接按离线进入处理。")
                    .define("nomojang.enabled", false);
            mojangReverseProxy = b.comment("mojang的反代地址,专门为那些不想给服务器开代理的人使用,默认为mojang地址").define("mojangReverseProxy", "https://sessionserver.mojang.com");
            apiRootWhitelist = b.comment(
                            "authlib-injector/Yggdrasil 皮肤站 hasJoined URL 白名单。",
                            "默认空列表表示信任客户端上报的皮肤站端点；填写 littleskin.cn 等关键字后，只允许匹配项通过。"
                    )
                    .defineListAllowEmpty(List.of("yggdrasil.apiRootWhitelist"), List::of, o -> o instanceof String);
            b.pop();
        }
    }

    private TrueuuidConfig() {}

}
