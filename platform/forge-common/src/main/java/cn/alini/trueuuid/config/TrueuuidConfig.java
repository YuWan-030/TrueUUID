package cn.alini.trueuuid.config;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;

import java.util.List;

/** Forge-side configuration surface for the shared endpoint policy. */
public final class TrueuuidConfig {
    private static final ForgeConfigSpec SPEC;
    private static final ForgeConfigSpec.LongValue TIMEOUT_MS;
    private static final ForgeConfigSpec.BooleanValue ALLOW_OFFLINE_ON_TIMEOUT;
    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> YGGDRASIL_HOSTS;
    private static final ForgeConfigSpec.BooleanValue SHOW_JOIN_FEEDBACK;
    private static final ForgeConfigSpec.BooleanValue SHOW_JOIN_TITLE;
    private static final ForgeConfigSpec.BooleanValue SHOW_ACCOUNT_OVERLAY;
    private static final ForgeConfigSpec.ConfigValue<String> OVERLAY_CORNER;
    private static final ForgeConfigSpec.IntValue OVERLAY_OFFSET_X;
    private static final ForgeConfigSpec.IntValue OVERLAY_OFFSET_Y;
    private static final ForgeConfigSpec.DoubleValue OVERLAY_SCALE;
    private static final ForgeConfigSpec.BooleanValue ALLOW_OFFLINE_ON_FAILURE;
    private static final ForgeConfigSpec.BooleanValue KNOWN_PREMIUM_DENY_OFFLINE;
    private static final ForgeConfigSpec.BooleanValue ALLOW_OFFLINE_FOR_UNKNOWN_ONLY;
    private static final ForgeConfigSpec.BooleanValue DEBUG;
    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.push("auth");
        TIMEOUT_MS = builder.comment("Server-side wait for the client's TrueUUID answer and session verification, in milliseconds.")
                .defineInRange("timeoutMs", 30_000L, 1_000L, 600_000L);
        ALLOW_OFFLINE_ON_TIMEOUT = builder.comment("false: kick when authentication times out. true: apply the offline fallback policy on timeout instead.")
                .define("allowOfflineOnTimeout", false);
        YGGDRASIL_HOSTS = builder.comment("Allowed Yggdrasil hasJoined hosts. Empty rejects every client-provided endpoint.")
                .defineListAllowEmpty(List.of("yggdrasil", "apiRootWhitelist"), List::of, value -> value instanceof String);
        SHOW_JOIN_FEEDBACK = builder.comment("Show the localized chat message after a TrueUUID login.")
                .define("showJoinFeedback", true);
        SHOW_JOIN_TITLE = builder.comment("Also show a full-screen title/subtitle on join. Off by default: the persistent account-status overlay already reports this less intrusively.")
                .define("showJoinTitle", false);
        SHOW_ACCOUNT_OVERLAY = builder.comment("Show the client account-status overlay after a TrueUUID login handshake.")
                .define("showAccountOverlay", true);
        OVERLAY_CORNER = builder.comment(
                        "Screen corner for the account-status badge: top_left, top_right, bottom_left, bottom_right.",
                        "Default bottom_right: vanilla keeps status effects and advancement toasts in the top right,",
                        "chat in the bottom left, and mods commonly take the top left.")
                .defineInList("overlayCorner", "bottom_right", List.of("top_left", "top_right", "bottom_left", "bottom_right"));
        OVERLAY_OFFSET_X = builder.comment("Extra horizontal pixels for the badge, to dodge another mod's HUD. Positive moves right.")
                .defineInRange("overlayOffsetX", 0, -4096, 4096);
        OVERLAY_OFFSET_Y = builder.comment("Extra vertical pixels for the badge, to dodge another mod's HUD. Positive moves down.")
                .defineInRange("overlayOffsetY", 0, -4096, 4096);
        OVERLAY_SCALE = builder.comment(
                        "Size of the account-status badge, scaling the padlock and label together.",
                        "Whole numbers (1.0, 2.0) keep Minecraft's bitmap font perfectly crisp; values in between are slightly soft.")
                .defineInRange("overlayScale", 1.0D, 0.5D, 4.0D);
        ALLOW_OFFLINE_ON_FAILURE = builder.comment("Allow an offline fallback when the client has no valid premium session or session verification fails.")
                .define("allowOfflineOnFailure", true);
        KNOWN_PREMIUM_DENY_OFFLINE = builder.comment("Deny offline fallback for a name that has previously completed a verified premium login.")
                .define("knownPremiumDenyOffline", true);
        ALLOW_OFFLINE_FOR_UNKNOWN_ONLY = builder.comment("Restrict offline fallback to names with no prior verified premium login.")
                .define("allowOfflineForUnknownOnly", true);
        DEBUG = builder.comment("Enable debug logging for the login handshake. Never logs tokens, nonces, endpoints, or raw authlib responses.")
                .define("debug", false);
        builder.pop();
        SPEC = builder.build();
    }
    public static void register() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, SPEC);
    }
    public static long timeoutMs() { return TIMEOUT_MS.get(); }
    public static boolean allowOfflineOnTimeout() { return ALLOW_OFFLINE_ON_TIMEOUT.get(); }
    @SuppressWarnings("unchecked") public static List<String> yggdrasilHosts() { return (List<String>) YGGDRASIL_HOSTS.get(); }
    public static boolean showJoinFeedback() { return SHOW_JOIN_FEEDBACK.get(); }
    public static boolean showJoinTitle() { return SHOW_JOIN_TITLE.get(); }
    public static boolean showAccountOverlay() { return SHOW_ACCOUNT_OVERLAY.get(); }
    public static String overlayCorner() { return OVERLAY_CORNER.get(); }
    public static int overlayOffsetX() { return OVERLAY_OFFSET_X.get(); }
    public static int overlayOffsetY() { return OVERLAY_OFFSET_Y.get(); }
    public static float overlayScale() { return OVERLAY_SCALE.get().floatValue(); }
    public static boolean allowOfflineOnFailure() { return ALLOW_OFFLINE_ON_FAILURE.get(); }
    public static boolean knownPremiumDenyOffline() { return KNOWN_PREMIUM_DENY_OFFLINE.get(); }
    public static boolean allowOfflineForUnknownOnly() { return ALLOW_OFFLINE_FOR_UNKNOWN_ONLY.get(); }
    public static boolean debug() { return DEBUG.get(); }
    private TrueuuidConfig() {}
}
