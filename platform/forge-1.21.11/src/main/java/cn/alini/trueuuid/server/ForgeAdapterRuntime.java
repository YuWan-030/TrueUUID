package cn.alini.trueuuid.server;

import cn.alini.trueuuid.Trueuuid;
import cn.alini.trueuuid.api.AccountStatus;
import cn.alini.trueuuid.api.AccountStatusStore;
import cn.alini.trueuuid.protocol.AuthSource;
import cn.alini.trueuuid.protocol.BoundedRequestCoordinator;
import cn.alini.trueuuid.protocol.EndpointPolicy;
import cn.alini.trueuuid.protocol.GraceCache;
import cn.alini.trueuuid.protocol.RecentIpGrace;
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
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;

import java.net.URI;
import java.time.Duration;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

/** Forge-owned runtime facade for the shared safe session verifier. */
public final class ForgeAdapterRuntime {
    private static final Gson GSON = new Gson();
    private static final int MAX_PENDING_VERIFICATIONS = 4096;
    private static final long PENDING_VERIFICATION_TTL_MILLIS = Duration.ofMinutes(5).toMillis();
    private static final Map<UUID, PendingLogin> pendingLogins = new LinkedHashMap<>();
    // Live per-session status plus addon callbacks for the public API, shared
    // with every loader through AccountStatusStore. Concurrent because addons
    // may query it off the server thread; entries live from login to logout.
    private static final AccountStatusStore<ServerPlayer> statusStore = new AccountStatusStore<>(
            (player, failure) -> Trueuuid.LOGGER.warn("TrueUUID login callback threw for player={}",
                    player.getUUID(), failure));
    private static BoundedRequestCoordinator requests;
    private static SessionVerifier verifier;
    private static ForgeVerifiedNameRegistry verifiedNames;
    private static RecentIpGrace ipGrace;
    private static MigrationCoordinator migrations;
    private static MigrationLockRegistry migrationLocks;

    public static synchronized void initialize() {
        if (verifier != null) return;
        requests = new BoundedRequestCoordinator();
        verifiedNames = new ForgeVerifiedNameRegistry();
        ipGrace = new RecentIpGrace();
        migrations = new MigrationCoordinator();
        migrationLocks = new MigrationLockRegistry();
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
        statusStore.clearAll();
        if (verifiedNames != null) verifiedNames.close();
        verifiedNames = null;
        if (ipGrace != null) ipGrace.close();
        ipGrace = null;
        if (migrations != null) migrations.close();
        migrations = null;
        if (migrationLocks != null) migrationLocks.close();
        migrationLocks = null;
    }

    /** Records a TrueUUID session verification until the matching player joins. */
    public static synchronized void recordVerifiedProfile(VerifiedProfile profile) {
        recordVerifiedProfile(profile, "");
    }

    /** Records a session verification plus the same-IP reconnect grace seed. */
    public static synchronized void recordVerifiedProfile(VerifiedProfile profile, String clientIp) {
        recordPendingLogin(profile.uuid(), AuthenticationSource.TRUEUUID_SESSION);
        if (verifiedNames != null) verifiedNames.record(profile.name(), profile.uuid());
        if (ipGrace != null) {
            ipGrace.record(profile.name(), clientIp, new GraceCache.Entry(profile.uuid(), AuthSource.MOJANG, "Mojang"));
        }
    }

    /** Accepts a same-IP reconnect within the grace window, consuming the entry. */
    public static synchronized Optional<UUID> tryGraceLogin(String name, String clientIp) {
        if (ipGrace == null || !TrueuuidConfig.recentIpGraceEnabled()) return Optional.empty();
        return ipGrace.consume(name, clientIp, Duration.ofSeconds(TrueuuidConfig.recentIpGraceTtlSeconds()))
                .map(GraceCache.Entry::uuid);
    }

    /** Records a grace acceptance so join feedback and the API report premium. */
    public static synchronized void recordGraceLogin(GameProfile profile) {
        if (profile != null && profile.id() != null) recordPendingLogin(profile.id(), AuthenticationSource.TRUEUUID_SESSION);
    }

    /** Records a configured offline fallback until the vanilla login completes. */
    public static synchronized void recordOfflineFallback(GameProfile profile) {
        if (profile != null && profile.id() != null) recordPendingLogin(profile.id(), AuthenticationSource.OFFLINE_FALLBACK);
    }

    /** Applies the offline policy before vanilla accepts the unverified profile. */
    public static synchronized boolean canUseOfflineFallback(String name) {
        boolean knownVerifiedName = verifiedNames != null && verifiedNames.contains(name);
        return OfflineFallbackPolicy.permits(knownVerifiedName, TrueuuidConfig.allowOfflineOnFailure(),
                TrueuuidConfig.knownPremiumDenyOffline(), TrueuuidConfig.allowOfflineForUnknownOnly());
    }

    public static synchronized boolean isMigrationPending(String name) {
        initialize();
        return migrationLocks != null && migrationLocks.contains(name);
    }

    public static synchronized void markMigrationPending(String name) {
        initialize();
        if (migrationLocks != null) migrationLocks.mark(name);
    }

    public static synchronized void clearMigrationPending(String name) {
        if (migrationLocks != null) migrationLocks.clear(name);
    }

    public static MigrationCoordinator migrations() {
        initialize();
        return migrations;
    }

    public static CompletableFuture<Integer> probeMojangAsync() {
        initialize();
        SafeSessionHttpClient http = new SafeSessionHttpClient();
        return requests.submit("__probe__", "mojang", "probe", () ->
                http.getTrusted(URI.create("https://sessionserver.mojang.com/session/minecraft/hasJoined?username=Mojang&serverId=test")).status());
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
        refreshSkinForOthers(player);
        AuthenticationSource source = consumePendingLogin(player.getUUID());
        if (source == null && player.level().getServer() != null && player.level().getServer().usesAuthentication()) {
            source = AuthenticationSource.NATIVE_ONLINE_MODE;
        }
        if (source == null) return;

        // Publish status for the addon API and notify callbacks first, so any
        // conditional join logic (separate spawn, permissions) sees the status
        // regardless of the join-feedback config below.
        statusStore.publish(player, player.getUUID(), source.publicStatus);

        Trueuuid.LOGGER.info("TrueUUID {}: player={}, uuid={}", source.auditLabel,
                player.getGameProfile().name(), player.getUUID());
        Trueuuid.acceptance("result={} player={} uuid={}",
                source == AuthenticationSource.OFFLINE_FALLBACK ? "offline_fallback" : "premium_join",
                player.getGameProfile().name(), player.getUUID());

        if (TrueuuidConfig.showJoinFeedback()) {
            player.sendSystemMessage(Component.translatable(source.chatKey).withStyle(source.chatColor));
        }
        // The persistent client badge reports the same state, so the full-screen
        // title stays opt-in rather than interrupting every join.
        if (TrueuuidConfig.showJoinTitle()) {
            player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 60, 20));
            player.connection.send(new ClientboundSetTitleTextPacket(Component.translatable(source.titleKey).withStyle(source.titleColor)));
            player.connection.send(new ClientboundSetSubtitleTextPacket(Component.translatable(source.subtitleKey).withStyle(ChatFormatting.GRAY)));
        }
    }

    /** Drops a player's live status when they leave. Wired through the seam. */
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            statusStore.clear(player.getUUID());
            activateGraceAfterLogout(player.getGameProfile().name(), player.getIpAddress());
        }
    }

    private static synchronized void activateGraceAfterLogout(String name, String clientIp) {
        if (ipGrace != null && TrueuuidConfig.recentIpGraceEnabled()) {
            ipGrace.activateAfterLogout(name, clientIp);
        }
    }

    /**
     * Re-broadcasts the joining player's info one tick later so other clients
     * re-fetch the skin of the replaced (verified) profile, matching 1.20.1.
     */
    private static void refreshSkinForOthers(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        if (server == null) return;
        server.execute(() -> {
            ClientboundPlayerInfoRemovePacket remove = new ClientboundPlayerInfoRemovePacket(List.of(player.getUUID()));
            ClientboundPlayerInfoUpdatePacket update = ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(List.of(player));
            for (ServerPlayer other : server.getPlayerList().getPlayers()) {
                if (other.getUUID().equals(player.getUUID())) continue;
                other.connection.send(remove);
                other.connection.send(update);
            }
        });
    }

    // ---- Public API surface (see cn.alini.trueuuid.api.TrueuuidApi) ----

    /** Live authentication status for an online player id. */
    public static AccountStatus statusOf(UUID playerId) {
        return statusStore.statusOf(playerId);
    }

    /** True if this name has ever completed a verified premium/Yggdrasil login. */
    public static synchronized boolean isKnownPremiumName(String name) {
        return verifiedNames != null && verifiedNames.contains(name);
    }

    /** The premium UUID last bound to this name, if any. */
    public static synchronized Optional<UUID> premiumUuidOf(String name) {
        return verifiedNames == null ? Optional.empty() : verifiedNames.premiumUuid(name);
    }

    /** Registers an addon login callback (invoked on the server thread at join). */
    public static void registerLoginCallback(BiConsumer<ServerPlayer, AccountStatus> callback) {
        statusStore.register(callback);
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
        TRUEUUID_SESSION("session-verified premium login", "trueuuid.chat.premium", "trueuuid.title.premium", "trueuuid.subtitle.premium", ChatFormatting.GREEN, ChatFormatting.GREEN, AccountStatus.PREMIUM_VERIFIED),
        NATIVE_ONLINE_MODE("native online-mode premium login", "trueuuid.chat.online_mode", "trueuuid.title.premium", "trueuuid.subtitle.online_mode", ChatFormatting.GREEN, ChatFormatting.GREEN, AccountStatus.ONLINE_MODE),
        OFFLINE_FALLBACK("offline fallback login", "trueuuid.chat.offline_fallback", "trueuuid.title.offline", "trueuuid.subtitle.offline", ChatFormatting.RED, ChatFormatting.RED, AccountStatus.OFFLINE_FALLBACK);

        private final String auditLabel;
        private final String chatKey;
        private final String titleKey;
        private final String subtitleKey;
        private final ChatFormatting chatColor;
        private final ChatFormatting titleColor;
        private final AccountStatus publicStatus;

        AuthenticationSource(String auditLabel, String chatKey, String titleKey, String subtitleKey,
                             ChatFormatting chatColor, ChatFormatting titleColor, AccountStatus publicStatus) {
            this.auditLabel = auditLabel;
            this.chatKey = chatKey;
            this.titleKey = titleKey;
            this.subtitleKey = subtitleKey;
            this.chatColor = chatColor;
            this.titleColor = titleColor;
            this.publicStatus = publicStatus;
        }
    }
    private record PendingLogin(AuthenticationSource source, long createdAt) {}
    private ForgeAdapterRuntime() {}
}
