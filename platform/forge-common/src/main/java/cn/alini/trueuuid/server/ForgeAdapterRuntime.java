package cn.alini.trueuuid.server;

import cn.alini.trueuuid.Trueuuid;
import cn.alini.trueuuid.api.AccountStatus;
import cn.alini.trueuuid.api.AccountStatusStore;
import cn.alini.trueuuid.protocol.AuthSource;
import cn.alini.trueuuid.protocol.BoundedRequestCoordinator;
import cn.alini.trueuuid.protocol.EndpointPolicy;
import cn.alini.trueuuid.protocol.ExpiringBoundedStore;
import cn.alini.trueuuid.protocol.GraceCache;
import cn.alini.trueuuid.protocol.HasJoinedProfileParser;
import cn.alini.trueuuid.protocol.MigrationLockRegistry;
import cn.alini.trueuuid.protocol.OfflineFallbackPolicy;
import cn.alini.trueuuid.protocol.PersistentVerifiedNameStore;
import cn.alini.trueuuid.protocol.RecentIpGrace;
import cn.alini.trueuuid.protocol.SafeSessionHttpClient;
import cn.alini.trueuuid.protocol.SafeSessionVerifier;
import cn.alini.trueuuid.protocol.SessionVerifier;
import cn.alini.trueuuid.protocol.VerifiedProfile;
import cn.alini.trueuuid.presentation.AuthenticationPresentation;
import cn.alini.trueuuid.presentation.ClientStatusMarker;
import cn.alini.trueuuid.presentation.IntegratedWorldPolicy;
import cn.alini.trueuuid.presentation.LoginNotificationRouter;
import cn.alini.trueuuid.config.TrueuuidConfig;
import com.mojang.authlib.GameProfile;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.fml.loading.FMLPaths;

import java.time.Duration;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

/** Forge-owned runtime facade for the shared safe session verifier. */
public final class ForgeAdapterRuntime {
    private static final ExpiringBoundedStore<UUID, AuthenticationPresentation> pendingLogins =
            new ExpiringBoundedStore<>(4096, Duration.ofMinutes(5));
    // Live per-session status plus addon callbacks for the public API, shared
    // with every loader through AccountStatusStore. Concurrent because addons
    // may query it off the server thread; entries live from login to logout.
    private static final AccountStatusStore<ServerPlayer> statusStore = new AccountStatusStore<>(
            (player, failure) -> Trueuuid.LOGGER.warn("TrueUUID login callback threw for player={}",
                    player.getUUID(), failure));
    private static BoundedRequestCoordinator requests;
    private static SessionVerifier verifier;
    private static PersistentVerifiedNameStore verifiedNames;
    private static RecentIpGrace ipGrace;
    private static MigrationCoordinator migrations;
    private static MigrationLockRegistry migrationLocks;

    public static synchronized void initialize() {
        if (verifier != null) return;
        requests = new BoundedRequestCoordinator();
        verifiedNames = new PersistentVerifiedNameStore(
                FMLPaths.CONFIGDIR.get().resolve("trueuuid-registry.json"));
        ipGrace = new RecentIpGrace();
        migrations = new MigrationCoordinator();
        migrationLocks = new MigrationLockRegistry();
        // Fail closed for custom endpoints until this target exposes an explicit
        // Forge config allowlist; Mojang's fixed endpoint remains available.
        verifier = new SafeSessionVerifier(requests,
                () -> new EndpointPolicy(TrueuuidConfig.yggdrasilHosts()), HasJoinedProfileParser::parse);
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
        recordVerifiedProfile(profile, clientIp, "");
    }

    /** Records verification while preserving whether a configured Yggdrasil endpoint authenticated it. */
    public static synchronized void recordVerifiedProfile(VerifiedProfile profile, String clientIp, String endpoint) {
        boolean yggdrasil = endpoint != null && !endpoint.isBlank();
        recordPendingLogin(profile.uuid(), yggdrasil
                ? AuthenticationPresentation.YGGDRASIL : AuthenticationPresentation.MOJANG);
        if (verifiedNames != null) verifiedNames.record(profile.name(), profile.uuid());
        if (ipGrace != null) {
            ipGrace.record(profile.name(), clientIp, new GraceCache.Entry(profile.uuid(),
                    yggdrasil ? AuthSource.YGGDRASIL : AuthSource.MOJANG,
                    yggdrasil ? "Yggdrasil" : "Mojang"));
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
        UUID id = ForgeGameProfiles.id(profile);
        if (id != null) recordPendingLogin(id, AuthenticationPresentation.GRACE);
    }

    /** Records a configured offline fallback until the vanilla login completes. */
    public static synchronized void recordOfflineFallback(GameProfile profile) {
        UUID id = ForgeGameProfiles.id(profile);
        if (id != null) recordPendingLogin(id, AuthenticationPresentation.OFFLINE_FALLBACK);
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

    private static void recordPendingLogin(UUID uuid, AuthenticationPresentation source) {
        if (uuid != null && source != null) pendingLogins.put(uuid, source);
    }

    /**
     * Emits audit and player feedback only after the server has completed login.
     * Registered per Forge version through the {@code TrueuuidForgeEvents} seam,
     * because the {@code @SubscribeEvent} annotation package moved in EventBus 7.
     */
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        refreshSkinForOthers(player);
        AuthenticationPresentation source = consumePendingLogin(player.getUUID());
        MinecraftServer server = ForgeGameProfiles.server(player);
        boolean privateSingleplayer = server != null && IntegratedWorldPolicy.isPrivateSingleplayer(
                server.isSingleplayer(), server.isPublished());
        if (source == null && server != null && server.usesAuthentication()) {
            source = AuthenticationPresentation.NATIVE_ONLINE_MODE;
        }
        if (source == null) return;

        // Publish status for the addon API and notify callbacks first, so any
        // conditional join logic (separate spawn, permissions) sees the status
        // regardless of the join-feedback config below.
        AccountStatus publicStatus = source.clientStatus().isPremium()
                ? (source == AuthenticationPresentation.NATIVE_ONLINE_MODE
                    ? AccountStatus.ONLINE_MODE : AccountStatus.PREMIUM_VERIFIED)
                : AccountStatus.OFFLINE_FALLBACK;
        statusStore.publish(player, player.getUUID(), publicStatus);

        Trueuuid.LOGGER.info("TrueUUID login_complete outcome={} player={} uuid={} auth_source={}",
                source.outcome(), ForgeGameProfiles.name(player.getGameProfile()), player.getUUID(), source.authenticationSource());
        Trueuuid.acceptance("result={} player={} uuid={}",
                source == AuthenticationPresentation.OFFLINE_FALLBACK ? "offline_fallback" : "premium_join",
                ForgeGameProfiles.name(player.getGameProfile()), player.getUUID());

        // Vanilla action-bar transport is intercepted client-side and never
        // rendered. It avoids unstable loader play-payload APIs while keeping
        // the badge strictly server-confirmed.
        if (!privateSingleplayer) {
            player.connection.send(new ClientboundSetActionBarTextPacket(Component.literal(
                    ClientStatusMarker.encode(source.clientStatus()))));
        }

        List<ServerPlayer> onlinePlayers = server == null ? List.of(player) : server.getPlayerList().getPlayers();
        for (var delivery : LoginNotificationRouter.route(player, onlinePlayers,
                ForgeAdapterRuntime::hasOperatorPermission,
                TrueuuidConfig.showJoinFeedback() && !privateSingleplayer,
                TrueuuidConfig.showOperatorNotifications())) {
            if (delivery.kind() == LoginNotificationRouter.Kind.JOIN_RESULT) {
                delivery.recipient().sendSystemMessage(Component.translatable(source.chatTranslationKey()).withStyle(
                        source.clientStatus().isPremium() ? ChatFormatting.GREEN : ChatFormatting.RED));
            } else {
                delivery.recipient().sendSystemMessage(Component.translatable("trueuuid.operator.login",
                        ForgeGameProfiles.name(player.getGameProfile()), player.getUUID(),
                        source.outcome(), source.authenticationSource()).withStyle(ChatFormatting.GRAY));
            }
        }
        // The persistent client badge reports the same state, so the full-screen
        // title stays opt-in rather than interrupting every join.
        if (!privateSingleplayer && TrueuuidConfig.showJoinTitle()) {
            player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 60, 20));
            player.connection.send(new ClientboundSetTitleTextPacket(Component.translatable(source.titleTranslationKey()).withStyle(
                    source.clientStatus().isPremium() ? ChatFormatting.GREEN : ChatFormatting.RED)));
            player.connection.send(new ClientboundSetSubtitleTextPacket(Component.translatable(source.subtitleTranslationKey()).withStyle(ChatFormatting.GRAY)));
        }
    }

    /** Drops a player's live status when they leave. Wired through the seam. */
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            statusStore.clear(player.getUUID());
            activateGraceAfterLogout(ForgeGameProfiles.name(player.getGameProfile()), player.getIpAddress());
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
        MinecraftServer server = ForgeGameProfiles.server(player);
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

    private static synchronized AuthenticationPresentation consumePendingLogin(UUID uuid) {
        return pendingLogins.remove(uuid).orElse(null);
    }

    private static boolean hasOperatorPermission(ServerPlayer player) {
        Object source = player.createCommandSourceStack();
        for (String method : List.of("hasPermission", "hasPermissions")) {
            try {
                Object result = source.getClass().getMethod(method, int.class).invoke(source, 2);
                return result instanceof Boolean allowed && allowed;
            } catch (NoSuchMethodException ignored) {
            } catch (ReflectiveOperationException ignored) {
                return false;
            }
        }
        return false;
    }
    private ForgeAdapterRuntime() {}
}
