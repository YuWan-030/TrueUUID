package cn.alini.trueuuid.fabric.config;

import cn.alini.trueuuid.fabric.TrueuuidFabric;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Fabric configuration surface. Fabric ships no config library, so this is a
 * plain JSON file, but the option names, grouping, and defaults deliberately
 * mirror the Forge/NeoForge {@code trueuuid-common.toml} so one documentation
 * line covers every loader.
 */
public final class FabricConfig {
    private static final long MAX_FILE_BYTES = 1024L * 1024L;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final boolean DEFAULT_ALLOW_OFFLINE_ON_FAILURE = true;
    private static final boolean DEFAULT_KNOWN_PREMIUM_DENY_OFFLINE = true;
    private static final boolean DEFAULT_ALLOW_OFFLINE_FOR_UNKNOWN_ONLY = true;
    private static final long DEFAULT_TIMEOUT_MS = 30_000L;
    private static final boolean DEFAULT_ALLOW_OFFLINE_ON_TIMEOUT = false;
    private static final boolean DEFAULT_RECENT_IP_GRACE_ENABLED = true;
    private static final int DEFAULT_RECENT_IP_GRACE_TTL_SECONDS = 10;
    private static final boolean DEFAULT_SHOW_JOIN_FEEDBACK = true;
    private static final boolean DEFAULT_SHOW_JOIN_TITLE = false;
    private static final boolean DEFAULT_SHOW_ACCOUNT_OVERLAY = true;
    private static final String DEFAULT_OVERLAY_CORNER = "bottom_right";
    private static final int DEFAULT_OVERLAY_OFFSET_X = 0;
    private static final int DEFAULT_OVERLAY_OFFSET_Y = 0;
    private static final float DEFAULT_OVERLAY_SCALE = 1.0F;
    private static final boolean DEFAULT_DEBUG = false;

    private static volatile boolean allowOfflineOnFailure = DEFAULT_ALLOW_OFFLINE_ON_FAILURE;
    private static volatile boolean knownPremiumDenyOffline = DEFAULT_KNOWN_PREMIUM_DENY_OFFLINE;
    private static volatile boolean allowOfflineForUnknownOnly = DEFAULT_ALLOW_OFFLINE_FOR_UNKNOWN_ONLY;
    private static volatile long timeoutMs = DEFAULT_TIMEOUT_MS;
    private static volatile boolean allowOfflineOnTimeout = DEFAULT_ALLOW_OFFLINE_ON_TIMEOUT;
    private static volatile boolean recentIpGraceEnabled = DEFAULT_RECENT_IP_GRACE_ENABLED;
    private static volatile int recentIpGraceTtlSeconds = DEFAULT_RECENT_IP_GRACE_TTL_SECONDS;
    private static volatile boolean showJoinFeedback = DEFAULT_SHOW_JOIN_FEEDBACK;
    private static volatile boolean showJoinTitle = DEFAULT_SHOW_JOIN_TITLE;
    private static volatile boolean showAccountOverlay = DEFAULT_SHOW_ACCOUNT_OVERLAY;
    private static volatile String overlayCorner = DEFAULT_OVERLAY_CORNER;
    private static volatile int overlayOffsetX = DEFAULT_OVERLAY_OFFSET_X;
    private static volatile int overlayOffsetY = DEFAULT_OVERLAY_OFFSET_Y;
    private static volatile float overlayScale = DEFAULT_OVERLAY_SCALE;
    private static volatile boolean debug = DEFAULT_DEBUG;

    public static synchronized void load() {
        load(FabricLoader.getInstance().getConfigDir().resolve("trueuuid.json"));
    }

    /** Package-visible for compatibility tests without a Fabric runtime. */
    static synchronized void load(Path file) {
        resetDefaults();
        try {
            if (!Files.exists(file)) {
                writeDefaults(file);
                return;
            }
            if (Files.size(file) > MAX_FILE_BYTES) {
                TrueuuidFabric.LOGGER.warn("TrueUUID config {} is unexpectedly large; keeping the secure defaults", file);
                return;
            }
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                JsonObject auth = root.get("auth") instanceof JsonObject section ? section : new JsonObject();
                allowOfflineOnFailure = readBoolean(auth, "allowOfflineOnFailure", allowOfflineOnFailure);
                knownPremiumDenyOffline = readBoolean(auth, "knownPremiumDenyOffline", knownPremiumDenyOffline);
                allowOfflineForUnknownOnly = readBoolean(auth, "allowOfflineForUnknownOnly", allowOfflineForUnknownOnly);
                timeoutMs = readBoundedLong(auth, "timeoutMs", timeoutMs, 1_000L, 600_000L);
                allowOfflineOnTimeout = readBoolean(auth, "allowOfflineOnTimeout", allowOfflineOnTimeout);
                JsonObject grace = auth.get("recentIpGrace") instanceof JsonObject section ? section : new JsonObject();
                recentIpGraceEnabled = readBoolean(grace, "enabled", recentIpGraceEnabled);
                recentIpGraceTtlSeconds = (int) readBoundedLong(grace, "ttlSeconds", recentIpGraceTtlSeconds, 1L, 60L);
                showJoinFeedback = readBoolean(auth, "showJoinFeedback", showJoinFeedback);
                showJoinTitle = readBoolean(auth, "showJoinTitle", showJoinTitle);
                showAccountOverlay = readBoolean(auth, "showAccountOverlay", showAccountOverlay);
                overlayCorner = readOverlayCorner(auth, "overlayCorner", overlayCorner);
                overlayOffsetX = (int) readBoundedLong(auth, "overlayOffsetX", overlayOffsetX, -4096L, 4096L);
                overlayOffsetY = (int) readBoundedLong(auth, "overlayOffsetY", overlayOffsetY, -4096L, 4096L);
                overlayScale = readBoundedFloat(auth, "overlayScale", overlayScale, 0.5F, 4.0F);
                debug = readBoolean(auth, "debug", debug);
            }
        } catch (Exception failure) {
            // Never rewrite a file the user edited; the compiled defaults already
            // deny offline fallback for known premium names.
            TrueuuidFabric.LOGGER.warn("TrueUUID could not read {}; keeping the secure defaults", file, failure);
        }
    }

    /** Allow an offline fallback when the client has no valid premium session or session verification fails. */
    public static boolean allowOfflineOnFailure() { return allowOfflineOnFailure; }

    /** Deny offline fallback for a name that has previously completed a verified premium login. */
    public static boolean knownPremiumDenyOffline() { return knownPremiumDenyOffline; }

    /** Restrict offline fallback to names with no prior verified premium login. */
    public static boolean allowOfflineForUnknownOnly() { return allowOfflineForUnknownOnly; }

    /** Server-side wait for the client's TrueUUID answer and session verification, in milliseconds. */
    public static long timeoutMs() { return timeoutMs; }

    /** false: kick when authentication times out. true: apply the offline fallback policy on timeout instead. */
    public static boolean allowOfflineOnTimeout() { return allowOfflineOnTimeout; }

    /** Enable short same-IP reconnect grace after logout, reusing the last verified identity only within the TTL window. */
    public static boolean recentIpGraceEnabled() { return recentIpGraceEnabled; }

    /** Same-IP grace seconds after logout. */
    public static int recentIpGraceTtlSeconds() { return recentIpGraceTtlSeconds; }

    /** Show the localized chat result after the server has accepted the login. */
    public static boolean showJoinFeedback() { return showJoinFeedback; }

    /** Also show the server-confirmed title/subtitle; disabled by default. */
    public static boolean showJoinTitle() { return showJoinTitle; }

    /** Client preference for showing the server-confirmed account badge. */
    public static boolean showAccountOverlay() { return showAccountOverlay; }

    public static String overlayCorner() { return overlayCorner; }
    public static int overlayOffsetX() { return overlayOffsetX; }
    public static int overlayOffsetY() { return overlayOffsetY; }
    public static float overlayScale() { return overlayScale; }

    /** Enable debug logging for the login handshake. Never logs tokens, nonces, endpoints, or raw authlib responses. */
    public static boolean debug() { return debug; }

    private static boolean readBoolean(JsonObject section, String key, boolean fallback) {
        return section.has(key) && section.get(key).isJsonPrimitive() && section.get(key).getAsJsonPrimitive().isBoolean()
                ? section.get(key).getAsBoolean() : fallback;
    }

    private static long readBoundedLong(JsonObject section, String key, long fallback, long min, long max) {
        if (!section.has(key) || !section.get(key).isJsonPrimitive() || !section.get(key).getAsJsonPrimitive().isNumber()) {
            return fallback;
        }
        return Math.max(min, Math.min(max, section.get(key).getAsLong()));
    }

    private static float readBoundedFloat(JsonObject section, String key, float fallback, float min, float max) {
        if (!section.has(key) || !section.get(key).isJsonPrimitive() || !section.get(key).getAsJsonPrimitive().isNumber()) {
            return fallback;
        }
        float value = section.get(key).getAsFloat();
        return Float.isFinite(value) ? Math.max(min, Math.min(max, value)) : fallback;
    }

    private static String readOverlayCorner(JsonObject section, String key, String fallback) {
        if (!section.has(key) || !section.get(key).isJsonPrimitive() || !section.get(key).getAsJsonPrimitive().isString()) {
            return fallback;
        }
        String value = section.get(key).getAsString();
        return switch (value) {
            case "top_left", "top_right", "bottom_left", "bottom_right" -> value;
            default -> fallback;
        };
    }

    private static void writeDefaults(Path file) throws Exception {
        JsonObject auth = new JsonObject();
        auth.addProperty("allowOfflineOnFailure", allowOfflineOnFailure);
        auth.addProperty("knownPremiumDenyOffline", knownPremiumDenyOffline);
        auth.addProperty("allowOfflineForUnknownOnly", allowOfflineForUnknownOnly);
        auth.addProperty("timeoutMs", timeoutMs);
        auth.addProperty("allowOfflineOnTimeout", allowOfflineOnTimeout);
        JsonObject grace = new JsonObject();
        grace.addProperty("enabled", recentIpGraceEnabled);
        grace.addProperty("ttlSeconds", recentIpGraceTtlSeconds);
        auth.add("recentIpGrace", grace);
        auth.addProperty("showJoinFeedback", showJoinFeedback);
        auth.addProperty("showJoinTitle", showJoinTitle);
        auth.addProperty("showAccountOverlay", showAccountOverlay);
        auth.addProperty("overlayCorner", overlayCorner);
        auth.addProperty("overlayOffsetX", overlayOffsetX);
        auth.addProperty("overlayOffsetY", overlayOffsetY);
        auth.addProperty("overlayScale", overlayScale);
        auth.addProperty("debug", debug);
        JsonObject root = new JsonObject();
        root.add("auth", auth);
        Files.createDirectories(file.getParent());
        try (Writer output = Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            GSON.toJson(root, output);
        }
    }

    private static void resetDefaults() {
        allowOfflineOnFailure = DEFAULT_ALLOW_OFFLINE_ON_FAILURE;
        knownPremiumDenyOffline = DEFAULT_KNOWN_PREMIUM_DENY_OFFLINE;
        allowOfflineForUnknownOnly = DEFAULT_ALLOW_OFFLINE_FOR_UNKNOWN_ONLY;
        timeoutMs = DEFAULT_TIMEOUT_MS;
        allowOfflineOnTimeout = DEFAULT_ALLOW_OFFLINE_ON_TIMEOUT;
        recentIpGraceEnabled = DEFAULT_RECENT_IP_GRACE_ENABLED;
        recentIpGraceTtlSeconds = DEFAULT_RECENT_IP_GRACE_TTL_SECONDS;
        showJoinFeedback = DEFAULT_SHOW_JOIN_FEEDBACK;
        showJoinTitle = DEFAULT_SHOW_JOIN_TITLE;
        showAccountOverlay = DEFAULT_SHOW_ACCOUNT_OVERLAY;
        overlayCorner = DEFAULT_OVERLAY_CORNER;
        overlayOffsetX = DEFAULT_OVERLAY_OFFSET_X;
        overlayOffsetY = DEFAULT_OVERLAY_OFFSET_Y;
        overlayScale = DEFAULT_OVERLAY_SCALE;
        debug = DEFAULT_DEBUG;
    }

    private FabricConfig() {}
}
