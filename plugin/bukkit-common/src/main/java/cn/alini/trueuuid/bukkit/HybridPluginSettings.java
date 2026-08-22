package cn.alini.trueuuid.bukkit;

import cn.alini.trueuuid.protocol.HybridLoginCoordinator;
import cn.alini.trueuuid.protocol.OfflineAdmissionMode;
import cn.alini.trueuuid.server.AdmissionMode;
import cn.alini.trueuuid.server.ServerConfiguration;
import org.bukkit.configuration.file.FileConfiguration;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Bukkit parser for the loader-neutral validated configuration snapshot. */
public record HybridPluginSettings(ServerConfiguration core) {
    public HybridPluginSettings {
        Objects.requireNonNull(core, "core");
    }

    public static HybridPluginSettings load(FileConfiguration configuration) {
        return load(configuration, true);
    }

    /** Existing files without admission.mode retain the previous strict behavior. */
    public static HybridPluginSettings load(FileConfiguration configuration, boolean freshInstallation) {
        Objects.requireNonNull(configuration, "configuration");
        HybridLoginCoordinator.Mode transport = parseEnum(HybridLoginCoordinator.Mode.class,
                string(configuration, "authentication.transport", "authentication.mode", "AUTO"),
                "authentication.transport must be CLIENT_ASSISTED, VANILLA_HYBRID, or AUTO");
        OfflineAdmissionMode offline = parseEnum(OfflineAdmissionMode.class,
                string(configuration, "admission.offline-client", "offline.mode", "ALLOW_VANILLA"),
                "admission.offline-client must be DENY, REQUIRE_TRUEUUID_CLIENT, or ALLOW_VANILLA");

        AdmissionMode admissionMode;
        if (configuration.contains("admission.mode", true) || freshInstallation) {
            admissionMode = parseEnum(AdmissionMode.class,
                    configuration.getString("admission.mode", "CONSENT_REQUIRED"),
                    "admission.mode must be CONSENT_REQUIRED, SAFE_PARALLEL, PREMIUM_RESERVED, or FIRST_CLAIM");
        } else {
            admissionMode = ServerConfiguration.deriveLegacyAdmissionMode(
                    configuration.getBoolean("security.known-premium-deny-offline", true));
        }

        ServerConfiguration snapshot = new ServerConfiguration(
                new ServerConfiguration.Authentication(transport,
                        Duration.ofMillis(configuration.getLong("authentication.timeout-ms", 30_000L)),
                        configuration.getInt("authentication.maximum-pending-logins", 64),
                        configuration.getStringList("authentication.custom-endpoint-allowlist")),
                new ServerConfiguration.Admission(admissionMode, offline,
                        configuration.getBoolean("admission.first-claim-risk-accepted", false)),
                new ServerConfiguration.Aliases(configuration.getString("aliases.prefix", "-")),
                new ServerConfiguration.Feedback(
                        bool(configuration, "feedback.private-chat", "feedback.player-chat", true),
                        configuration.getBoolean("feedback.vanilla-action-bar", true),
                        configuration.getBoolean("feedback.title", false),
                        configuration.getBoolean("feedback.modded-overlay", true),
                        configuration.getInt("feedback.vanilla-action-bar-delay-ticks", 20)),
                new ServerConfiguration.Permissions(parseEnum(ServerConfiguration.Provider.class,
                        configuration.getString("permissions.provider", "AUTO"),
                        "permissions.provider must be AUTO, LUCKPERMS, or PLATFORM")));
        return new HybridPluginSettings(snapshot);
    }

    public HybridLoginCoordinator.Mode mode() { return core.authentication().transport(); }
    public Duration loginTimeout() { return core.authentication().timeout(); }
    public int maximumPendingLogins() { return core.authentication().maximumPendingLogins(); }
    public List<String> customEndpointAllowlist() { return core.authentication().customEndpointAllowlist(); }
    public OfflineAdmissionMode offlineAdmissionMode() { return core.admission().offlineClient(); }
    public AdmissionMode admissionMode() { return core.admission().mode(); }
    public String aliasPrefix() { return core.aliases().prefix(); }
    public boolean showPlayerChat() { return core.feedback().privateChat(); }
    public boolean showVanillaActionBar() { return core.feedback().vanillaActionBar(); }
    public int vanillaActionBarDelayTicks() { return core.feedback().vanillaActionBarDelayTicks(); }

    private static String string(FileConfiguration configuration, String modern, String legacy, String fallback) {
        return configuration.contains(modern, true)
                ? configuration.getString(modern, fallback)
                : configuration.getString(legacy, fallback);
    }

    private static boolean bool(FileConfiguration configuration, String modern, String legacy, boolean fallback) {
        return configuration.contains(modern, true)
                ? configuration.getBoolean(modern, fallback)
                : configuration.getBoolean(legacy, fallback);
    }

    private static <T extends Enum<T>> T parseEnum(Class<T> type, String value, String message) {
        try {
            return Enum.valueOf(type, Objects.requireNonNull(value, "configuration value")
                    .trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException(message, invalid);
        }
    }
}
