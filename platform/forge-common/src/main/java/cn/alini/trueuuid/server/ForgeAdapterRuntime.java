package cn.alini.trueuuid.server;

import cn.alini.trueuuid.Trueuuid;
import cn.alini.trueuuid.protocol.BoundedRequestCoordinator;
import cn.alini.trueuuid.protocol.EndpointPolicy;
import cn.alini.trueuuid.protocol.SafeSessionHttpClient;
import cn.alini.trueuuid.protocol.SafeSessionVerifier;
import cn.alini.trueuuid.protocol.SessionVerifier;
import cn.alini.trueuuid.protocol.VerifiedProfile;
import cn.alini.trueuuid.config.TrueuuidConfig;
import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import com.mojang.authlib.GameProfile;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;

import java.time.Duration;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Forge-owned runtime facade for the shared safe session verifier. */
public final class ForgeAdapterRuntime {
    private static final Gson GSON = new Gson();
    private static final int MAX_PENDING_VERIFICATIONS = 4096;
    private static final long PENDING_VERIFICATION_TTL_MILLIS = Duration.ofMinutes(5).toMillis();
    private static final Map<UUID, PendingLogin> pendingLogins = new LinkedHashMap<>();
    private static BoundedRequestCoordinator requests;
    private static SessionVerifier verifier;
    private static ForgeVerifiedNameRegistry verifiedNames;

    public static synchronized void initialize() {
        if (verifier != null) return;
        requests = new BoundedRequestCoordinator();
        verifiedNames = new ForgeVerifiedNameRegistry();
        // Fail closed for custom endpoints until this target exposes an explicit
        // Forge config allowlist; Mojang's fixed endpoint remains available.
        verifier = new SafeSessionVerifier(requests, () -> new EndpointPolicy(TrueuuidConfig.yggdrasilHosts()), ForgeAdapterRuntime::parse);
    }

    public static synchronized SessionVerifier verifier() {
        initialize();
        return verifier;
    }

    public static synchronized void shutdown() {
        if (requests != null) requests.close();
        requests = null;
        verifier = null;
        pendingLogins.clear();
        if (verifiedNames != null) verifiedNames.close();
        verifiedNames = null;
    }

    /** Records a TrueUUID session verification until the matching player joins. */
    public static synchronized void recordVerifiedProfile(VerifiedProfile profile) {
        recordPendingLogin(profile.uuid(), AuthenticationSource.TRUEUUID_SESSION);
        if (verifiedNames != null) verifiedNames.record(profile.name(), profile.uuid());
    }

    /** Records a configured offline fallback until the vanilla login completes. */
    public static synchronized void recordOfflineFallback(GameProfile profile) {
        if (profile != null && profile.getId() != null) recordPendingLogin(profile.getId(), AuthenticationSource.OFFLINE_FALLBACK);
    }

    /** Applies the offline policy before vanilla accepts the unverified profile. */
    public static synchronized boolean canUseOfflineFallback(String name) {
        boolean knownVerifiedName = verifiedNames != null && verifiedNames.contains(name);
        return OfflineFallbackPolicy.permits(knownVerifiedName, TrueuuidConfig.allowOfflineOnFailure(),
                TrueuuidConfig.knownPremiumDenyOffline(), TrueuuidConfig.allowOfflineForUnknownOnly());
    }

    private static void recordPendingLogin(UUID uuid, AuthenticationSource source) {
        prunePendingLogins(System.currentTimeMillis());
        while (pendingLogins.size() >= MAX_PENDING_VERIFICATIONS) {
            Iterator<UUID> iterator = pendingLogins.keySet().iterator();
            if (!iterator.hasNext()) break;
            iterator.next();
            iterator.remove();
        }
        pendingLogins.put(uuid, new PendingLogin(source, System.currentTimeMillis()));
    }

    /**
     * Emits audit and player feedback only after the server has completed login.
     * Registered per Forge version through the {@code TrueuuidForgeEvents} seam,
     * because the {@code @SubscribeEvent} annotation package moved in EventBus 7.
     */
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        AuthenticationSource source = consumePendingLogin(player.getUUID());
        if (source == null && player.getServer() != null && player.getServer().usesAuthentication()) {
            source = AuthenticationSource.NATIVE_ONLINE_MODE;
        }
        if (source == null) return;

        Trueuuid.LOGGER.info("TrueUUID {}: player={}, uuid={}", source.auditLabel,
                player.getGameProfile().getName(), player.getUUID());
        if (!TrueuuidConfig.showJoinFeedback()) return;

        player.sendSystemMessage(Component.translatable(source.chatKey).withStyle(source.chatColor));
        player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 60, 20));
        player.connection.send(new ClientboundSetTitleTextPacket(Component.translatable(source.titleKey).withStyle(source.titleColor)));
        player.connection.send(new ClientboundSetSubtitleTextPacket(Component.translatable(source.subtitleKey).withStyle(ChatFormatting.GRAY)));
    }

    private static synchronized AuthenticationSource consumePendingLogin(UUID uuid) {
        prunePendingLogins(System.currentTimeMillis());
        PendingLogin pending = pendingLogins.remove(uuid);
        return pending == null ? null : pending.source;
    }

    private static void prunePendingLogins(long now) {
        pendingLogins.entrySet().removeIf(entry -> now - entry.getValue().createdAt >= PENDING_VERIFICATION_TTL_MILLIS);
    }

    private static Optional<VerifiedProfile> parse(SafeSessionHttpClient.Response response) {
        if (response.status() != 200) return Optional.empty();
        HasJoined value = GSON.fromJson(response.body(), HasJoined.class);
        if (value == null || value.id == null || value.name == null || value.name.isBlank()) return Optional.empty();
        UUID id = parseUuid(value.id);
        List<VerifiedProfile.Property> properties = value.properties == null ? List.of() : value.properties.stream()
                .filter(property -> property != null && property.name != null && property.value != null)
                .map(property -> new VerifiedProfile.Property(property.name, property.value, property.signature)).toList();
        return Optional.of(new VerifiedProfile(id, value.name, properties));
    }

    private static UUID parseUuid(String value) {
        if (!value.matches("[0-9a-fA-F]{32}")) throw new IllegalArgumentException("invalid profile UUID");
        return UUID.fromString(value.replaceFirst("(.{8})(.{4})(.{4})(.{4})(.{12})", "$1-$2-$3-$4-$5"));
    }

    private static final class HasJoined { String id; String name; List<Property> properties; }
    private static final class Property { String name; String value; @SerializedName("signature") String signature; }
    private enum AuthenticationSource {
        TRUEUUID_SESSION("session-verified premium login", "trueuuid.chat.premium", "trueuuid.title.premium", "trueuuid.subtitle.premium", ChatFormatting.GREEN, ChatFormatting.GREEN),
        NATIVE_ONLINE_MODE("native online-mode premium login", "trueuuid.chat.online_mode", "trueuuid.title.premium", "trueuuid.subtitle.online_mode", ChatFormatting.GREEN, ChatFormatting.GREEN),
        OFFLINE_FALLBACK("offline fallback login", "trueuuid.chat.offline_fallback", "trueuuid.title.offline", "trueuuid.subtitle.offline", ChatFormatting.RED, ChatFormatting.RED);

        private final String auditLabel;
        private final String chatKey;
        private final String titleKey;
        private final String subtitleKey;
        private final ChatFormatting chatColor;
        private final ChatFormatting titleColor;

        AuthenticationSource(String auditLabel, String chatKey, String titleKey, String subtitleKey,
                             ChatFormatting chatColor, ChatFormatting titleColor) {
            this.auditLabel = auditLabel;
            this.chatKey = chatKey;
            this.titleKey = titleKey;
            this.subtitleKey = subtitleKey;
            this.chatColor = chatColor;
            this.titleColor = titleColor;
        }
    }
    private record PendingLogin(AuthenticationSource source, long createdAt) {}
    private ForgeAdapterRuntime() {}
}
