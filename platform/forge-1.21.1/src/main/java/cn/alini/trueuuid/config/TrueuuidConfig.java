package cn.alini.trueuuid.config;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;

import java.util.List;

/** Forge-side configuration surface for the shared endpoint policy. */
public final class TrueuuidConfig {
    private static final ForgeConfigSpec SPEC;
    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> YGGDRASIL_HOSTS;
    private static final ForgeConfigSpec.BooleanValue SHOW_JOIN_FEEDBACK;
    private static final ForgeConfigSpec.BooleanValue SHOW_ACCOUNT_OVERLAY;
    private static final ForgeConfigSpec.BooleanValue ALLOW_OFFLINE_ON_FAILURE;
    private static final ForgeConfigSpec.BooleanValue KNOWN_PREMIUM_DENY_OFFLINE;
    private static final ForgeConfigSpec.BooleanValue ALLOW_OFFLINE_FOR_UNKNOWN_ONLY;
    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.push("auth");
        YGGDRASIL_HOSTS = builder.comment("Allowed Yggdrasil hasJoined hosts. Empty rejects every client-provided endpoint.")
                .defineListAllowEmpty(List.of("yggdrasil", "apiRootWhitelist"), List::of, value -> value instanceof String);
        SHOW_JOIN_FEEDBACK = builder.comment("Show localized chat and title feedback after a TrueUUID login.")
                .define("showJoinFeedback", true);
        SHOW_ACCOUNT_OVERLAY = builder.comment("Show the client account-status overlay after a TrueUUID login handshake.")
                .define("showAccountOverlay", true);
        ALLOW_OFFLINE_ON_FAILURE = builder.comment("Allow an offline fallback when the client has no valid premium session or session verification fails.")
                .define("allowOfflineOnFailure", true);
        KNOWN_PREMIUM_DENY_OFFLINE = builder.comment("Deny offline fallback for a name that has previously completed a verified premium login.")
                .define("knownPremiumDenyOffline", true);
        ALLOW_OFFLINE_FOR_UNKNOWN_ONLY = builder.comment("Restrict offline fallback to names with no prior verified premium login.")
                .define("allowOfflineForUnknownOnly", true);
        builder.pop();
        SPEC = builder.build();
    }
    public static void register() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, SPEC);
    }
    @SuppressWarnings("unchecked") public static List<String> yggdrasilHosts() { return (List<String>) YGGDRASIL_HOSTS.get(); }
    public static boolean showJoinFeedback() { return SHOW_JOIN_FEEDBACK.get(); }
    public static boolean showAccountOverlay() { return SHOW_ACCOUNT_OVERLAY.get(); }
    public static boolean allowOfflineOnFailure() { return ALLOW_OFFLINE_ON_FAILURE.get(); }
    public static boolean knownPremiumDenyOffline() { return KNOWN_PREMIUM_DENY_OFFLINE.get(); }
    public static boolean allowOfflineForUnknownOnly() { return ALLOW_OFFLINE_FOR_UNKNOWN_ONLY.get(); }
    private TrueuuidConfig() {}
}
