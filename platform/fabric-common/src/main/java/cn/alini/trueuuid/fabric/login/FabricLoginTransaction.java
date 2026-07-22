package cn.alini.trueuuid.fabric.login;

import cn.alini.trueuuid.fabric.TrueuuidFabric;
import cn.alini.trueuuid.fabric.config.FabricConfig;
import cn.alini.trueuuid.protocol.AuthMessages;
import cn.alini.trueuuid.protocol.LoginStateMachine;
import cn.alini.trueuuid.protocol.MigrationTransaction;
import cn.alini.trueuuid.protocol.VerifiedProfile;
import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.networking.v1.ServerLoginNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerLoginNetworkHandler;
import net.minecraft.text.Text;

import java.util.UUID;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * One transaction per native login handler. It has no static connection map,
 * so disconnect/timeout cleanup cannot retain a previous connection.
 *
 * <p>The first secure slice owns the Mojang decision and basic offline
 * fallback: packet bounds, local-client join assertion, bounded server
 * verification, profile replacement, or release of the native offline
 * profile. Loader-specific policy and migration features follow later.</p>
 */
public final class FabricLoginTransaction {
    private static final int TRANSACTION_ID = 1;

    private final LoginStateMachine state = new LoginStateMachine();
    private CompletableFuture<Void> completion;
    private CompletableFuture<?> verification;
    private FabricLoginQuerySender sender;
    private String nonce;
    private VerifiedProfile pendingVerifiedProfile;
    private PlayerDataMigration.OfflineData pendingOfflineData;
    private String pendingIp;
    private String pendingEndpoint;
    private boolean closed;

    public synchronized void begin(ServerLoginNetworkHandler handler, MinecraftServer server,
                                   FabricLoginQuerySender sender, ServerLoginNetworking.LoginSynchronizer synchronizer) {
        if (closed || completion != null) return;

        completion = new CompletableFuture<>();
        this.sender = sender;
        nonce = UUID.randomUUID().toString().replace("-", "");
        state.beginAuthentication(TRANSACTION_ID, nonce, System.currentTimeMillis());
        synchronizer.waitFor(completion);
        GameProfile pendingProfile = ((FabricLoginStateAccess) handler).trueuuid$getProfile();
        if (pendingProfile != null && FabricAdapterRuntime.isMigrationPending(FabricGameProfiles.name(pendingProfile))) {
            closeWithDisconnect(server, handler, "trueuuid.disconnect.migration_pending");
            return;
        }
        sender.send(new AuthMessages.Query(nonce, false, "", ""));
        TrueuuidFabric.LOGGER.info("TrueUUID authentication challenge sent: player={}",
                pendingProfile == null ? "<unknown>" : FabricGameProfiles.name(pendingProfile));
        TrueuuidFabric.acceptance("phase=auth_query_sent player={}",
                pendingProfile == null ? "<unknown>" : FabricGameProfiles.name(pendingProfile));

        scheduleTimeout(handler, server, FabricConfig.timeoutMs());
    }

    public synchronized void answer(MinecraftServer server, ServerLoginNetworkHandler handler,
                                    boolean understood, AuthMessages.Answer answer) {
        if (closed || completion == null) return;
        TrueuuidFabric.debug("TrueUUID received authentication response: understood={}", understood);
        TrueuuidFabric.acceptance("phase=auth_answer_received understood={} migrationPhase={}",
                understood, state.phase() == LoginStateMachine.Phase.AWAITING_MIGRATION);
        if (!understood || answer == null) {
            closeWithDisconnect(server, handler, "trueuuid.disconnect.auth_denied");
            return;
        }
        if (state.phase() == LoginStateMachine.Phase.AWAITING_MIGRATION) {
            acceptMigration(server, handler, answer);
            return;
        }
        LoginStateMachine.AnswerResult result = state.acceptAnswer(TRANSACTION_ID, answer);
        if (result == LoginStateMachine.AnswerResult.DENY) {
            completeOfflineFallbackOrDeny(server, handler, true);
            return;
        }
        if (result != LoginStateMachine.AnswerResult.VERIFY) {
            closeWithDisconnect(server, handler, "trueuuid.disconnect.auth_denied");
            return;
        }

        GameProfile offlineProfile = ((FabricLoginStateAccess) handler).trueuuid$getProfile();
        if (offlineProfile == null || FabricGameProfiles.name(offlineProfile) == null
                || FabricGameProfiles.name(offlineProfile).isBlank()) {
            closeWithDisconnect(server, handler, "trueuuid.disconnect.auth_denied");
            return;
        }

        String ip = clientIp(handler);
        String endpoint = answer.customEndpoint();
        verification = FabricSessionCheck.hasJoinedAsync(FabricGameProfiles.name(offlineProfile), nonce, ip, endpoint)
                .thenCompose(verified -> {
                    if (verified == null) return CompletableFuture.completedFuture(new VerifiedLookup(null, null));
                    return FabricAdapterRuntime.migrations().find(server, verified.name())
                            .thenApply(data -> new VerifiedLookup(verified, data));
                })
                .whenComplete((lookup, failure) -> server.execute(() ->
                        completeVerification(server, handler, lookup, failure, ip, endpoint)));
    }

    /**
     * Consults the persistent offline policy before releasing the native
     * offline profile, mirroring the other adapters: a name that has already
     * completed a verified premium login may not be taken by an unverified
     * client. On authentication failure (not timeout) one same-name, same-IP
     * reconnect inside the grace window keeps the verified identity instead.
     */
    private void completeOfflineFallbackOrDeny(MinecraftServer server, ServerLoginNetworkHandler handler, boolean allowGrace) {
        GameProfile offlineProfile = ((FabricLoginStateAccess) handler).trueuuid$getProfile();
        String name = offlineProfile == null ? null : FabricGameProfiles.name(offlineProfile);
        if (allowGrace && name != null) {
            var grace = FabricAdapterRuntime.tryGraceLogin(name, clientIp(handler));
            if (grace.isPresent()) {
                FabricAdapterRuntime.recordGraceLogin(grace.get());
                completeWithProfile(server, handler, new GameProfile(grace.get(), name));
                return;
            }
        }
        if (!FabricAdapterRuntime.canUseOfflineFallback(name)) {
            TrueuuidFabric.LOGGER.warn("TrueUUID offline fallback denied for previously verified name: player={}", name);
            TrueuuidFabric.acceptance("result=known_deny player={}", name);
            closeWithDisconnect(server, handler, "trueuuid.disconnect.bound_premium");
            return;
        }
        completeOfflineFallback(server, handler);
    }

    public synchronized void cancel() {
        closed = true;
        if (verification != null) verification.cancel(true);
        if (completion != null) completion.completeExceptionally(new IllegalStateException("login disconnected"));
        state.reset();
        if (pendingVerifiedProfile != null) FabricAdapterRuntime.clearMigrationPending(pendingVerifiedProfile.name());
        clearMigrationState();
    }

    private void timeout(ServerLoginNetworkHandler handler, MinecraftServer server) {
        synchronized (this) {
            if (closed || completion == null || completion.isDone()) return;
        }
        long migrationTimeout = Math.max(FabricConfig.timeoutMs(), 180_000L);
        LoginStateMachine.TimeoutResult timeout = state.timeoutAt(System.currentTimeMillis(),
                FabricConfig.timeoutMs(), migrationTimeout);
        if (timeout == LoginStateMachine.TimeoutResult.NONE) return;
        TrueuuidFabric.debug("TrueUUID login timed out after {} ms",
                timeout == LoginStateMachine.TimeoutResult.MIGRATION ? migrationTimeout : FabricConfig.timeoutMs());
        if (timeout == LoginStateMachine.TimeoutResult.MIGRATION) {
            TrueuuidFabric.acceptance("result=migration_timeout");
            closeWithDisconnect(server, handler, "trueuuid.disconnect.migration_confirm_timeout");
        } else if (FabricConfig.allowOfflineOnTimeout()) {
            // The offline policy still applies: a timeout must not hand a
            // previously verified name to a client that never answered. No
            // grace either, matching 1.20.1: grace covers failures, not silence.
            completeOfflineFallbackOrDeny(server, handler, false);
        } else {
            closeWithDisconnect(server, handler, "trueuuid.disconnect.timeout");
        }
    }

    /** The reason is a translation key from platform/common-assets, never inline text. */
    private void closeWithDisconnect(MinecraftServer server, ServerLoginNetworkHandler handler, String reasonKey) {
        server.execute(() -> {
            synchronized (FabricLoginTransaction.this) {
                if (closed) return;
                closed = true;
                if (completion != null) completion.completeExceptionally(new IllegalStateException(reasonKey));
                state.reset();
                if (pendingVerifiedProfile != null) FabricAdapterRuntime.clearMigrationPending(pendingVerifiedProfile.name());
                clearMigrationState();
            }
            handler.disconnect(Text.translatable(reasonKey));
        });
    }

    private void closeWithDisconnect(MinecraftServer server, ServerLoginNetworkHandler handler, Text reason) {
        server.execute(() -> {
            synchronized (FabricLoginTransaction.this) {
                if (closed) return;
                closed = true;
                if (completion != null) completion.completeExceptionally(new IllegalStateException(reason.getString()));
                state.reset();
                if (pendingVerifiedProfile != null) FabricAdapterRuntime.clearMigrationPending(pendingVerifiedProfile.name());
                clearMigrationState();
            }
            handler.disconnect(reason);
        });
    }

    private void completeVerification(MinecraftServer server, ServerLoginNetworkHandler handler,
                                      VerifiedLookup lookup, Throwable failure, String ip, String endpoint) {
        synchronized (this) {
            if (closed || completion == null || completion.isDone()) return;
            if (failure == null && lookup != null && lookup.profile() != null) {
                VerifiedProfile verified = lookup.profile();
                if (lookup.offlineData() != null && !lookup.offlineData().offlineUuid().equals(verified.uuid())) {
                    TrueuuidFabric.acceptance("phase=migration_needed player={} offlineUuid={} verifiedUuid={}",
                            verified.name(), lookup.offlineData().offlineUuid(), verified.uuid());
                    requestMigrationConfirmation(server, handler, verified, lookup.offlineData(), ip, endpoint);
                    return;
                }
                FabricAdapterRuntime.recordVerifiedProfile(verified, ip);
                ((FabricLoginStateAccess) handler).trueuuid$setProfile(FabricVerifiedProfiles.create(verified));
                TrueuuidFabric.acceptance("result=premium_ready player={} uuid={}", verified.name(), verified.uuid());
                state.reset();
                completion.complete(null);
                return;
            }
        }
        completeOfflineFallbackOrDeny(server, handler, true);
    }

    private void requestMigrationConfirmation(MinecraftServer server, ServerLoginNetworkHandler handler, VerifiedProfile verified,
                                              PlayerDataMigration.OfflineData offlineData, String ip, String endpoint) {
        pendingVerifiedProfile = verified;
        pendingOfflineData = offlineData;
        pendingIp = ip;
        pendingEndpoint = endpoint;
        AuthMessages.Query query = state.beginMigration(TRANSACTION_ID,
                new MigrationTransaction.Offer(offlineData.offlineUuid(), offlineData.summary()),
                System.currentTimeMillis());
        if (sender == null) {
            closeWithDisconnect(server, handler, "trueuuid.disconnect.auth_denied");
            return;
        }
        TrueuuidFabric.acceptance("phase=migration_query_sent player={} offlineUuid={} verifiedUuid={}",
                verified.name(), offlineData.offlineUuid(), verified.uuid());
        sender.send(query);
        scheduleTimeout(handler, server, Math.max(FabricConfig.timeoutMs(), 180_000L));
    }

    private void acceptMigration(MinecraftServer server, ServerLoginNetworkHandler handler, AuthMessages.Answer answer) {
        LoginStateMachine.AnswerResult result = state.acceptAnswer(TRANSACTION_ID, answer);
        if (result != LoginStateMachine.AnswerResult.MIGRATE || pendingVerifiedProfile == null || pendingOfflineData == null) {
            TrueuuidFabric.acceptance("result=migration_rejected");
            sendDuplicateUuidDisconnect(server, handler);
            return;
        }
        VerifiedProfile verified = pendingVerifiedProfile;
        PlayerDataMigration.OfflineData offlineData = pendingOfflineData;
        String migrationName = verified.name();
        TrueuuidFabric.acceptance("phase=migration_answer_accepted player={} uuid={}", migrationName, verified.uuid());
        FabricAdapterRuntime.markMigrationPending(migrationName);
        verification = FabricAdapterRuntime.migrations().migrate(server, migrationName, verified.uuid())
                .whenComplete((ignored, failure) -> server.execute(() -> {
                    synchronized (FabricLoginTransaction.this) {
                        try {
                            if (closed || completion == null || completion.isDone()) return;
                            if (failure != null) {
                                TrueuuidFabric.acceptance("result=migration_failed player={}", migrationName);
                                closeWithDisconnect(server, handler, Text.translatable("trueuuid.disconnect.migration_failed",
                                        migrationName, offlineData.offlineUuid(), verified.uuid(),
                                        Text.translatable("trueuuid.error.internal")));
                                return;
                            }
                            ((FabricLoginStateAccess) handler).trueuuid$setProfile(FabricVerifiedProfiles.create(verified));
                            FabricAdapterRuntime.recordVerifiedProfile(verified, pendingIp == null ? clientIp(handler) : pendingIp);
                            TrueuuidFabric.acceptance("result=migration_complete player={} uuid={}", migrationName, verified.uuid());
                            state.reset();
                            completion.complete(null);
                        } finally {
                            FabricAdapterRuntime.clearMigrationPending(migrationName);
                            clearMigrationState();
                        }
                    }
                }));
    }

    private void sendDuplicateUuidDisconnect(MinecraftServer server, ServerLoginNetworkHandler handler) {
        VerifiedProfile verified = pendingVerifiedProfile;
        PlayerDataMigration.OfflineData data = pendingOfflineData;
        if (verified == null || data == null) {
            closeWithDisconnect(server, handler, "trueuuid.disconnect.auth_denied");
            return;
        }
        Text sourceName = pendingEndpoint == null || pendingEndpoint.isBlank()
                ? Text.translatable("trueuuid.auth_source.premium")
                : Text.translatable("trueuuid.auth_source.skin_site.with_name", pendingEndpoint);
        closeWithDisconnect(server, handler, Text.translatable("trueuuid.disconnect.duplicate_uuid",
                sourceName, data.offlineUuid(), verified.uuid(), verified.name()));
    }

    /** Grace acceptance: keep the previously verified UUID for this reconnect. */
    private void completeWithProfile(MinecraftServer server, ServerLoginNetworkHandler handler, GameProfile profile) {
        server.execute(() -> {
            synchronized (FabricLoginTransaction.this) {
                if (closed || completion == null || completion.isDone()) return;
                ((FabricLoginStateAccess) handler).trueuuid$setProfile(profile);
                state.reset();
                completion.complete(null);
            }
        });
    }

    private static String clientIp(ServerLoginNetworkHandler handler) {
        var connection = ((FabricLoginStateAccess) handler).trueuuid$getConnection();
        return connection != null && connection.getAddress() instanceof java.net.InetSocketAddress address
                && address.getAddress() != null ? address.getAddress().getHostAddress() : "";
    }

    private void completeOfflineFallback(MinecraftServer server, ServerLoginNetworkHandler handler) {
        // Fabric's QUERY_START runs before vanilla assigns the deterministic
        // offline UUID, so the name-only profile still has a null id here.
        // Materialize exactly vanilla's OfflinePlayer UUID before releasing
        // the login; this remains distinct from a verified profile replacement.
        server.execute(() -> {
            synchronized (FabricLoginTransaction.this) {
                if (closed || completion == null || completion.isDone()) return;
                GameProfile profile = ((FabricLoginStateAccess) handler).trueuuid$getProfile();
                if (profile == null || FabricGameProfiles.name(profile) == null
                        || FabricGameProfiles.name(profile).isBlank()) {
                    closeWithDisconnect(server, handler, "trueuuid.disconnect.auth_denied");
                    return;
                }
                if (FabricGameProfiles.id(profile) == null) {
                    UUID offlineId = UUID.nameUUIDFromBytes(
                            ("OfflinePlayer:" + FabricGameProfiles.name(profile)).getBytes(StandardCharsets.UTF_8));
                    profile = new GameProfile(offlineId, FabricGameProfiles.name(profile));
                    ((FabricLoginStateAccess) handler).trueuuid$setProfile(profile);
                }
                TrueuuidFabric.acceptance("result=offline_fallback player={} uuid={}",
                        FabricGameProfiles.name(profile), FabricGameProfiles.id(profile));
                FabricAdapterRuntime.recordOfflineFallback(FabricGameProfiles.id(profile));
                state.reset();
                completion.complete(null);
            }
        });
    }

    private void scheduleTimeout(ServerLoginNetworkHandler handler, MinecraftServer server, long delayMillis) {
        CompletableFuture.runAsync(() -> timeout(handler, server),
                CompletableFuture.delayedExecutor(delayMillis, TimeUnit.MILLISECONDS));
    }

    private void clearMigrationState() {
        pendingVerifiedProfile = null;
        pendingOfflineData = null;
        pendingIp = null;
        pendingEndpoint = null;
    }

    private record VerifiedLookup(VerifiedProfile profile, PlayerDataMigration.OfflineData offlineData) {}
}
