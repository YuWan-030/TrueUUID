package cn.alini.trueuuid.config;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;

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

    public static final class Common {
        public final ForgeConfigSpec.LongValue timeoutMs;
        public final ForgeConfigSpec.BooleanValue allowOfflineOnTimeout;
        public final ForgeConfigSpec.BooleanValue allowOfflineOnFailure;
        public final ForgeConfigSpec.ConfigValue<String> timeoutKickMessage;
        public final ForgeConfigSpec.ConfigValue<String> offlineFallbackMessage;

        // 新增
        public final ForgeConfigSpec.ConfigValue<String> offlineShortSubtitle;
        public final ForgeConfigSpec.ConfigValue<String> onlineShortSubtitle;

        // 新增 nomojang 配置
        public final ForgeConfigSpec.BooleanValue nomojangEnabled;

        // 新增：策略相关
        public final ForgeConfigSpec.BooleanValue knownPremiumDenyOffline;
        public final ForgeConfigSpec.BooleanValue allowOfflineForUnknownOnly;
        public final ForgeConfigSpec.BooleanValue recentIpGraceEnabled;
        public final ForgeConfigSpec.IntValue recentIpGraceTtlSeconds;
        public final ForgeConfigSpec.BooleanValue debug;

        Common(ForgeConfigSpec.Builder b) {
            b.push("auth");

            timeoutMs = b.defineInRange("timeoutMs", 10_000L, 1_000L, 600_000L);
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
            recentIpGraceEnabled      = b.comment("启用“近期同 IP 成功”容错，在 TTL 内失败时临时按正版处理。")
                    .define("recentIpGrace.enabled", true);
            recentIpGraceTtlSeconds   = b.comment("“近期同 IP 成功”容错的 TTL 秒数。建议 60~600。")
                    .defineInRange("recentIpGrace.ttlSeconds", 300, 30, 3600);
            debug = b.comment("启用调试日志输出").define("debug", false);
            // 新增：跳过 Mojang 会话认证（开启后不再通过 sessionserver 验证）
            nomojangEnabled = b.comment("开启后关闭对 Mojang 会话服务的在线校验逻辑；同 IP 且近期有正版成功的名称按正版 UUID 处理，其余直接按离线进入处理。")
                    .define("nomojang.enabled", false);
            b.pop();
        }
    }

    private TrueuuidConfig() {}

}