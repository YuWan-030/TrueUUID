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

    private static volatile boolean allowOfflineOnFailure = true;
    private static volatile boolean knownPremiumDenyOffline = true;
    private static volatile boolean allowOfflineForUnknownOnly = true;

    public static synchronized void load() {
        Path file = FabricLoader.getInstance().getConfigDir().resolve("trueuuid.json");
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

    private static boolean readBoolean(JsonObject section, String key, boolean fallback) {
        return section.has(key) && section.get(key).isJsonPrimitive() && section.get(key).getAsJsonPrimitive().isBoolean()
                ? section.get(key).getAsBoolean() : fallback;
    }

    private static void writeDefaults(Path file) throws Exception {
        JsonObject auth = new JsonObject();
        auth.addProperty("allowOfflineOnFailure", allowOfflineOnFailure);
        auth.addProperty("knownPremiumDenyOffline", knownPremiumDenyOffline);
        auth.addProperty("allowOfflineForUnknownOnly", allowOfflineForUnknownOnly);
        JsonObject root = new JsonObject();
        root.add("auth", auth);
        Files.createDirectories(file.getParent());
        try (Writer output = Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            GSON.toJson(root, output);
        }
    }

    private FabricConfig() {}
}
