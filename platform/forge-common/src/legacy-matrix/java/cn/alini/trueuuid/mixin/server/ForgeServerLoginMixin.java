package cn.alini.trueuuid.mixin.server;

import cn.alini.trueuuid.config.TrueuuidConfig;
import cn.alini.trueuuid.net.ForgeAuthAnswerPayload;
import cn.alini.trueuuid.net.ForgeAuthPayload;
import cn.alini.trueuuid.net.ForgeQueryTracker;
import cn.alini.trueuuid.protocol.AuthWireCodec;
import cn.alini.trueuuid.protocol.LoginStateMachine;
import cn.alini.trueuuid.protocol.MigrationTransaction;
import cn.alini.trueuuid.protocol.VerifiedProfile;
import cn.alini.trueuuid.server.ForgeAdapterRuntime;
import cn.alini.trueuuid.server.ForgeLoginFlow;
import cn.alini.trueuuid.server.PlayerDataMigration;
import cn.alini.trueuuid.Trueuuid;
import cn.alini.trueuuid.mixin.ForgeRuntimeNames;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.login.ClientboundCustomQueryPacket;
import net.minecraft.network.protocol.login.ServerboundCustomQueryAnswerPacket;
import net.minecraft.network.protocol.login.ServerboundHelloPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.net.InetSocketAddress;
import java.util.UUID;
import java.security.SecureRandom;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Mixin(value = ServerLoginPacketListenerImpl.class, remap = ForgeRuntimeNames.REMAP_MEMBERS)
abstract class ForgeServerLoginMixin {
    @Unique private static final SecureRandom TRUEUUID$TRANSACTIONS = new SecureRandom();
    @Shadow private GameProfile authenticatedProfile;
    @Shadow MinecraftServer server;
    @Shadow Connection connection;
    @Shadow public abstract void disconnect(Component reason);
    @Invoker("verifyLoginAndFinishConnectionSetup") abstract void trueuuid$completeNativeLogin(GameProfile profile);

    @Unique private final ForgeLoginFlow trueuuid$flow = new ForgeLoginFlow();
    @Unique private int trueuuid$transaction;
    @Unique private VerifiedProfile trueuuid$pendingVerifiedProfile;
    @Unique private PlayerDataMigration.OfflineData trueuuid$pendingOfflineData;
    @Unique private String trueuuid$pendingIp;
    @Unique private String trueuuid$pendingEndpoint;

    @Inject(method = "handleHello", at = @At("TAIL"))
    private void trueuuid$requestAssertion(ServerboundHelloPacket packet, CallbackInfo callback) {
        if (server.usesAuthentication() || authenticatedProfile == null) return;
        if (ForgeAdapterRuntime.isMigrationPending(authenticatedProfile.getName())) {
            disconnect(Component.translatable("trueuuid.disconnect.migration_pending"));
            return;
        }
        trueuuid$transaction = trueuuid$newTransaction();
        byte[] wire = trueuuid$flow.start(trueuuid$transaction, UUID.randomUUID().toString().replace("-", ""), System.currentTimeMillis());
        Trueuuid.LOGGER.info("TrueUUID authentication challenge sent: player={}", authenticatedProfile.getName());
        Trueuuid.acceptance("phase=auth_query_sent player={}", authenticatedProfile.getName());
        connection.send(new ClientboundCustomQueryPacket(trueuuid$transaction,
                new ForgeAuthPayload(AuthWireCodec.decodeQuery(wire))));
    }

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void trueuuid$holdLogin(CallbackInfo callback) {
        if (!trueuuid$flow.active()) return;
        callback.cancel();
        boolean migration = trueuuid$flow.phase() == LoginStateMachine.Phase.AWAITING_MIGRATION
                || trueuuid$flow.phase() == LoginStateMachine.Phase.MIGRATING;
        long timeoutMs = migration ? Math.max(TrueuuidConfig.timeoutMs(), 180_000L) : TrueuuidConfig.timeoutMs();
        if (!trueuuid$flow.timedOut(System.currentTimeMillis(), timeoutMs)) return;
        String playerName = authenticatedProfile == null ? null : authenticatedProfile.getName();
        if (migration) {
            Trueuuid.acceptance("result=migration_timeout player={}", playerName == null ? "<unknown>" : playerName);
            disconnect(Component.translatable("trueuuid.disconnect.migration_confirm_timeout"));
        } else if (TrueuuidConfig.allowOfflineOnTimeout() && authenticatedProfile != null) {
            // The offline policy still applies: a timeout must not hand a
            // previously verified name to a client that never answered.
            if (ForgeAdapterRuntime.canUseOfflineFallback(playerName)) {
                Trueuuid.LOGGER.warn("TrueUUID authentication timed out; offline fallback accepted: player={}", playerName);
                ForgeAdapterRuntime.recordOfflineFallback(authenticatedProfile);
                trueuuid$completeNativeLogin(authenticatedProfile);
            } else {
                Trueuuid.LOGGER.warn("TrueUUID offline fallback denied for previously verified name: player={}", playerName);
                disconnect(Component.translatable("trueuuid.disconnect.bound_premium"));
            }
        } else {
            disconnect(Component.translatable("trueuuid.disconnect.timeout"));
        }
        trueuuid$closeFlow();
    }

    @Inject(method = "handleCustomQueryPacket", at = @At("HEAD"), cancellable = true)
    private void trueuuid$checkAssertion(ServerboundCustomQueryAnswerPacket packet, CallbackInfo callback) {
        if (packet.transactionId() != trueuuid$transaction || !(packet.payload() instanceof ForgeAuthAnswerPayload answer)
                || authenticatedProfile == null) return;
        callback.cancel();
        Trueuuid.debug("TrueUUID received authentication response: player={}", authenticatedProfile.getName());
        Trueuuid.acceptance("phase=auth_answer_received player={} migrationPhase={}",
                authenticatedProfile.getName(), trueuuid$flow.phase() == LoginStateMachine.Phase.AWAITING_MIGRATION);
        String ip = connection.getRemoteAddress() instanceof InetSocketAddress address && address.getAddress() != null
                ? address.getAddress().getHostAddress() : "";
        if (trueuuid$flow.phase() == LoginStateMachine.Phase.AWAITING_MIGRATION) {
            trueuuid$handleMigrationAnswer(packet, answer, ip);
            return;
        }
        if (!answer.message().joined()) {
            String playerName = authenticatedProfile.getName();
            if (trueuuid$acceptGraceLogin(playerName, ip)) {
                // fall through: grace accepted the reconnect as premium
            } else if (!ForgeAdapterRuntime.canUseOfflineFallback(playerName)) {
                Trueuuid.LOGGER.warn("TrueUUID offline fallback denied for previously verified name: player={}", playerName);
                Trueuuid.acceptance("result=known_deny player={}", playerName);
                disconnect(Component.translatable("trueuuid.disconnect.bound_premium"));
            } else {
                Trueuuid.acceptance("result=offline_fallback player={}", playerName);
                ForgeAdapterRuntime.recordOfflineFallback(authenticatedProfile);
                trueuuid$completeNativeLogin(authenticatedProfile);
            }
            trueuuid$closeFlow();
            return;
        }
        trueuuid$flow.accept(packet.transactionId(), AuthWireCodec.encodeAnswer(answer.message()), authenticatedProfile.getName(), ip,
                        ForgeAdapterRuntime.verifier())
                .thenCompose(profile -> {
                    if (profile.isEmpty()) return CompletableFuture.completedFuture(new VerifiedLookup(profile, null));
                    return ForgeAdapterRuntime.migrations().find(server, profile.get().name())
                            .thenApply(data -> new VerifiedLookup(profile, data));
                })
                .whenComplete((lookup, failure) -> server.execute(() -> {
                    try {
                        if (!connection.isConnected()) return;
                        if (failure != null) {
                            Trueuuid.LOGGER.warn("TrueUUID premium verification failed during migration lookup: player={}",
                                    authenticatedProfile.getName());
                            disconnect(Component.translatable("trueuuid.disconnect.auth_denied"));
                            return;
                        }
                        if (lookup.profile().isEmpty()) {
                            if (trueuuid$acceptGraceLogin(authenticatedProfile.getName(), ip)) return;
                            Trueuuid.LOGGER.warn("TrueUUID premium verification denied: player={}", authenticatedProfile.getName());
                            disconnect(Component.translatable("trueuuid.disconnect.auth_denied"));
                            return;
                        }
                        VerifiedProfile verified = lookup.profile().get();
                        if (lookup.offlineData() != null && !lookup.offlineData().offlineUuid().equals(verified.uuid())) {
                            Trueuuid.acceptance("phase=migration_needed player={} offlineUuid={} verifiedUuid={}",
                                    verified.name(), lookup.offlineData().offlineUuid(), verified.uuid());
                            trueuuid$requestMigrationConfirmation(verified, lookup.offlineData(), ip, answer.message().customEndpoint());
                            return;
                        }
                        authenticatedProfile = trueuuid$toNativeProfile(verified);
                        ForgeAdapterRuntime.recordVerifiedProfile(verified, ip);
                        Trueuuid.acceptance("result=premium_ready player={} uuid={}", verified.name(), verified.uuid());
                        trueuuid$completeNativeLogin(authenticatedProfile);
                    } finally {
                        if (trueuuid$flow.phase() != LoginStateMachine.Phase.AWAITING_MIGRATION) trueuuid$closeFlow();
                    }
                }));
    }

    /** One same-name, same-IP reconnect inside the grace window keeps the verified identity. */
    @Unique private boolean trueuuid$acceptGraceLogin(String playerName, String ip) {
        java.util.Optional<UUID> grace = ForgeAdapterRuntime.tryGraceLogin(playerName, ip);
        if (grace.isEmpty()) return false;
        Trueuuid.LOGGER.info("TrueUUID recent same-IP grace login: player={}, uuid={}", playerName, grace.get());
        authenticatedProfile = new GameProfile(grace.get(), playerName);
        ForgeAdapterRuntime.recordGraceLogin(authenticatedProfile);
        trueuuid$completeNativeLogin(authenticatedProfile);
        return true;
    }

    // 1.20.2-1.20.6 pass the raw disconnect Component; DisconnectionDetails
    // only exists from 1.21.
    @Inject(method = "onDisconnect", at = @At("HEAD"))
    private void trueuuid$cancel(Component reason, CallbackInfo callback) {
        trueuuid$closeFlow();
    }

    @Unique private GameProfile trueuuid$toNativeProfile(VerifiedProfile verified) {
        GameProfile profile = new GameProfile(verified.uuid(), verified.name());
        for (VerifiedProfile.Property property : verified.properties()) {
            profile.getProperties().put(property.name(), property.signature() == null
                    ? new Property(property.name(), property.value())
                    : new Property(property.name(), property.value(), property.signature()));
        }
        return profile;
    }

    @Unique private void trueuuid$closeFlow() {
        if (trueuuid$pendingVerifiedProfile != null) {
            ForgeAdapterRuntime.clearMigrationPending(trueuuid$pendingVerifiedProfile.name());
        }
        ForgeQueryTracker.discard(trueuuid$transaction);
        trueuuid$transaction = 0;
        trueuuid$pendingVerifiedProfile = null;
        trueuuid$pendingOfflineData = null;
        trueuuid$pendingIp = null;
        trueuuid$pendingEndpoint = null;
        trueuuid$flow.close();
    }

    @Unique private int trueuuid$newTransaction() {
        int transaction;
        do {
            transaction = TRUEUUID$TRANSACTIONS.nextInt(1, Integer.MAX_VALUE);
        } while (!ForgeQueryTracker.register(transaction));
        return transaction;
    }

    @Unique private void trueuuid$requestMigrationConfirmation(VerifiedProfile verified,
                                                               PlayerDataMigration.OfflineData offlineData,
                                                               String ip, String endpoint) {
        ForgeQueryTracker.discard(trueuuid$transaction);
        trueuuid$transaction = trueuuid$newTransaction();
        trueuuid$pendingVerifiedProfile = verified;
        trueuuid$pendingOfflineData = offlineData;
        trueuuid$pendingIp = ip;
        trueuuid$pendingEndpoint = endpoint;
        byte[] wire = trueuuid$flow.migrationQuery(trueuuid$transaction,
                new MigrationTransaction.Offer(offlineData.offlineUuid(), offlineData.summary()),
                System.currentTimeMillis());
        Trueuuid.acceptance("phase=migration_query_sent player={} offlineUuid={} verifiedUuid={}",
                verified.name(), offlineData.offlineUuid(), verified.uuid());
        connection.send(new ClientboundCustomQueryPacket(trueuuid$transaction,
                new ForgeAuthPayload(AuthWireCodec.decodeQuery(wire))));
    }

    @Unique private void trueuuid$handleMigrationAnswer(ServerboundCustomQueryAnswerPacket packet,
                                                        ForgeAuthAnswerPayload answer, String ip) {
        if (!trueuuid$flow.acceptMigration(packet.transactionId(), AuthWireCodec.encodeAnswer(answer.message()))
                || trueuuid$pendingVerifiedProfile == null || trueuuid$pendingOfflineData == null) {
            Trueuuid.acceptance("result=migration_rejected player={}",
                    authenticatedProfile == null ? "<unknown>" : authenticatedProfile.getName());
            trueuuid$sendDuplicateUuidDisconnect();
            trueuuid$closeFlow();
            return;
        }
        VerifiedProfile verified = trueuuid$pendingVerifiedProfile;
        PlayerDataMigration.OfflineData offlineData = trueuuid$pendingOfflineData;
        String migrationName = verified.name();
        Trueuuid.acceptance("phase=migration_answer_accepted player={} uuid={}", migrationName, verified.uuid());
        ForgeAdapterRuntime.markMigrationPending(migrationName);
        ForgeAdapterRuntime.migrations().migrate(server, migrationName, verified.uuid())
                .whenComplete((ignored, failure) -> server.execute(() -> {
                    try {
                        if (!connection.isConnected()) return;
                        if (failure != null) {
                            Trueuuid.acceptance("result=migration_failed player={}", migrationName);
                            disconnect(Component.translatable("trueuuid.disconnect.migration_failed",
                                    migrationName, offlineData.offlineUuid(), verified.uuid(),
                                    Component.translatable("trueuuid.error.internal")));
                            return;
                        }
                        authenticatedProfile = trueuuid$toNativeProfile(verified);
                        ForgeAdapterRuntime.recordVerifiedProfile(verified, trueuuid$pendingIp == null ? ip : trueuuid$pendingIp);
                        Trueuuid.acceptance("result=migration_complete player={} uuid={}", migrationName, verified.uuid());
                        trueuuid$completeNativeLogin(authenticatedProfile);
                    } finally {
                        ForgeAdapterRuntime.clearMigrationPending(migrationName);
                        trueuuid$closeFlow();
                    }
                }));
    }

    @Unique private void trueuuid$sendDuplicateUuidDisconnect() {
        VerifiedProfile verified = trueuuid$pendingVerifiedProfile;
        PlayerDataMigration.OfflineData data = trueuuid$pendingOfflineData;
        if (verified == null || data == null) {
            disconnect(Component.translatable("trueuuid.disconnect.auth_denied"));
            return;
        }
        Component sourceName = trueuuid$pendingEndpoint == null || trueuuid$pendingEndpoint.isBlank()
                ? Component.translatable("trueuuid.auth_source.premium")
                : Component.translatable("trueuuid.auth_source.skin_site.with_name", trueuuid$pendingEndpoint);
        disconnect(Component.translatable("trueuuid.disconnect.duplicate_uuid",
                sourceName, data.offlineUuid(), verified.uuid(), verified.name()));
    }

    @Unique private record VerifiedLookup(Optional<VerifiedProfile> profile, PlayerDataMigration.OfflineData offlineData) {}
}
