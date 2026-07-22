package cn.alini.trueuuid.mixin.server;

import cn.alini.trueuuid.config.TrueuuidConfig;
import cn.alini.trueuuid.net.AuthAnswerPayload;
import cn.alini.trueuuid.net.AuthPayload;
import cn.alini.trueuuid.net.AuthQueryTracker;
import cn.alini.trueuuid.protocol.AuthWireCodec;
import cn.alini.trueuuid.protocol.LoginStateMachine;
import cn.alini.trueuuid.protocol.MigrationTransaction;
import cn.alini.trueuuid.protocol.VerifiedProfile;
import cn.alini.trueuuid.server.AdapterRuntime;
import cn.alini.trueuuid.server.LoginAttempt;
import cn.alini.trueuuid.server.PlayerDataMigration;
import com.google.common.collect.ImmutableMultimap;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
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
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.net.InetSocketAddress;
import java.security.SecureRandom;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Mixin(ServerLoginPacketListenerImpl.class)
// 1.21.9+ record-era copy: GameProfile is a record (name()/properties()).
// See build.gradle's recordEraSources.
/** NeoForge 21.10/21.11 record-era server login seam. */
abstract class ServerLoginMixin {
    @Unique private static final SecureRandom TRUEUUID$TRANSACTIONS = new SecureRandom();
    @Shadow private GameProfile authenticatedProfile;
    @Shadow public abstract void disconnect(Component reason);
    @Accessor("server")
    abstract MinecraftServer trueuuid$server();
    @Accessor("connection")
    abstract Connection trueuuid$connection();
    @Invoker("verifyLoginAndFinishConnectionSetup")
    abstract void trueuuid$finishLogin(GameProfile profile);

    @Unique private final LoginAttempt trueuuid$attempt = new LoginAttempt();
    @Unique private int trueuuid$transaction;
    @Unique private VerifiedProfile trueuuid$pendingVerifiedProfile;
    @Unique private PlayerDataMigration.OfflineData trueuuid$pendingOfflineData;
    @Unique private String trueuuid$pendingIp;
    @Unique private String trueuuid$pendingEndpoint;

    @Inject(method = "handleHello", at = @At("TAIL"))
    private void trueuuid$begin(ServerboundHelloPacket packet, CallbackInfo callback) {
        MinecraftServer server = trueuuid$server();
        Connection connection = trueuuid$connection();
        if (server.usesAuthentication() || authenticatedProfile == null) return;
        if (AdapterRuntime.isMigrationPending(authenticatedProfile.name())) {
            disconnect(Component.translatable("trueuuid.disconnect.migration_pending"));
            return;
        }
        trueuuid$transaction = trueuuid$newTransaction();
        byte[] query = trueuuid$attempt.begin(trueuuid$transaction, UUID.randomUUID().toString().replace("-", ""),
                System.currentTimeMillis());
        cn.alini.trueuuid.Trueuuid.acceptance("phase=auth_query_sent player={}", authenticatedProfile.name());
        connection.send(new ClientboundCustomQueryPacket(trueuuid$transaction,
                new AuthPayload(AuthWireCodec.decodeQuery(query))));
    }

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void trueuuid$pauseVanillaLogin(CallbackInfo callback) {
        if (!trueuuid$attempt.phase().equals(cn.alini.trueuuid.protocol.LoginStateMachine.Phase.IDLE)) {
            callback.cancel();
            boolean migration = trueuuid$attempt.phase() == LoginStateMachine.Phase.AWAITING_MIGRATION
                    || trueuuid$attempt.phase() == LoginStateMachine.Phase.MIGRATING;
            long timeoutMs = migration ? Math.max(TrueuuidConfig.timeoutMs(), 180_000L) : TrueuuidConfig.timeoutMs();
            if (trueuuid$attempt.timeout(System.currentTimeMillis(), timeoutMs) != LoginAttempt.Result.TIMEOUT) return;
            String name = authenticatedProfile == null ? null : authenticatedProfile.name();
            if (migration) {
                cn.alini.trueuuid.Trueuuid.acceptance("result=migration_timeout player={}", name == null ? "<unknown>" : name);
                disconnect(Component.translatable("trueuuid.disconnect.migration_confirm_timeout"));
            } else if (TrueuuidConfig.allowOfflineOnTimeout() && authenticatedProfile != null) {
                // The offline policy still applies: a timeout must not hand a
                // previously verified name to a client that never answered.
                if (AdapterRuntime.canUseOfflineFallback(name)) {
                    cn.alini.trueuuid.Trueuuid.LOGGER.warn(
                            "TrueUUID authentication timed out; offline fallback accepted: player={}", name);
                    AdapterRuntime.recordOfflineFallback(authenticatedProfile);
                    trueuuid$finishLogin(authenticatedProfile);
                } else {
                    cn.alini.trueuuid.Trueuuid.LOGGER.warn(
                            "TrueUUID offline fallback denied for previously verified name: player={}", name);
                    disconnect(Component.translatable("trueuuid.disconnect.bound_premium"));
                }
            } else {
                disconnect(Component.translatable("trueuuid.disconnect.timeout"));
            }
            trueuuid$clear();
        }
    }

    @Inject(method = "handleCustomQueryPacket", at = @At("HEAD"), cancellable = true)
    private void trueuuid$verify(ServerboundCustomQueryAnswerPacket packet, CallbackInfo callback) {
        if (packet.transactionId() != trueuuid$transaction || !(packet.payload() instanceof AuthAnswerPayload answer)
                || authenticatedProfile == null) return;
        callback.cancel();
        cn.alini.trueuuid.Trueuuid.debug("TrueUUID received authentication response: player={}", authenticatedProfile.name());
        cn.alini.trueuuid.Trueuuid.acceptance("phase=auth_answer_received player={} migrationPhase={}",
                authenticatedProfile.name(), trueuuid$attempt.phase() == LoginStateMachine.Phase.AWAITING_MIGRATION);
        MinecraftServer server = trueuuid$server();
        Connection connection = trueuuid$connection();
        String name = authenticatedProfile.name();
        String ip = connection.getRemoteAddress() instanceof InetSocketAddress address && address.getAddress() != null
                ? address.getAddress().getHostAddress() : "";
        if (trueuuid$attempt.phase() == LoginStateMachine.Phase.AWAITING_MIGRATION) {
            trueuuid$handleMigrationAnswer(packet, answer, ip);
            return;
        }
        trueuuid$attempt.answer(packet.transactionId(), AuthWireCodec.encodeAnswer(answer.message()), name, ip, AdapterRuntime.verifier())
                .thenCompose(outcome -> {
                    if (outcome.result() != LoginAttempt.Result.VERIFIED || outcome.profile().isEmpty()) {
                        return CompletableFuture.completedFuture(new VerifiedLookup(outcome, null));
                    }
                    return AdapterRuntime.migrations().find(server, outcome.profile().get().name())
                            .thenApply(data -> new VerifiedLookup(outcome, data));
                })
                .whenComplete((lookup, failure) -> server.execute(() -> {
                    try {
                        if (!connection.isConnected()) return;
                        if (failure != null) {
                            cn.alini.trueuuid.Trueuuid.LOGGER.warn(
                                    "TrueUUID premium verification failed during migration lookup: player={}", name);
                            disconnect(Component.translatable("trueuuid.disconnect.auth_denied"));
                            return;
                        }
                        LoginAttempt.Outcome outcome = lookup.outcome();
                        if (outcome.result() != LoginAttempt.Result.VERIFIED || outcome.profile().isEmpty()) {
                            // Match the Forge adapters: an unverified client may keep its
                            // offline UUID only when the configured policy allows it. One
                            // same-name, same-IP reconnect inside the grace window keeps
                            // the verified identity instead.
                            java.util.Optional<UUID> grace = AdapterRuntime.tryGraceLogin(name, ip);
                            if (grace.isPresent()) {
                                cn.alini.trueuuid.Trueuuid.LOGGER.info(
                                        "TrueUUID recent same-IP grace login: player={}, uuid={}", name, grace.get());
                                authenticatedProfile = new GameProfile(grace.get(), name);
                                AdapterRuntime.recordGraceLogin(authenticatedProfile);
                                trueuuid$finishLogin(authenticatedProfile);
                            } else if (!AdapterRuntime.canUseOfflineFallback(name)) {
                                cn.alini.trueuuid.Trueuuid.LOGGER.warn(
                                        "TrueUUID offline fallback denied for previously verified name: player={}", name);
                                cn.alini.trueuuid.Trueuuid.acceptance("result=known_deny player={}", name);
                                disconnect(Component.translatable("trueuuid.disconnect.bound_premium"));
                            } else {
                                cn.alini.trueuuid.Trueuuid.acceptance("result=offline_fallback player={}", name);
                                AdapterRuntime.recordOfflineFallback(authenticatedProfile);
                                trueuuid$finishLogin(authenticatedProfile);
                            }
                            return;
                        }
                        VerifiedProfile verified = outcome.profile().get();
                        if (lookup.offlineData() != null && !lookup.offlineData().offlineUuid().equals(verified.uuid())) {
                            cn.alini.trueuuid.Trueuuid.acceptance("phase=migration_needed player={} offlineUuid={} verifiedUuid={}",
                                    verified.name(), lookup.offlineData().offlineUuid(), verified.uuid());
                            trueuuid$requestMigrationConfirmation(verified, lookup.offlineData(), ip, answer.message().customEndpoint());
                            return;
                        }
                        authenticatedProfile = trueuuid$profile(verified);
                        AdapterRuntime.recordVerifiedProfile(verified, ip, answer.message().customEndpoint());
                        cn.alini.trueuuid.Trueuuid.acceptance("result=premium_ready player={} uuid={}", verified.name(), verified.uuid());
                        trueuuid$finishLogin(authenticatedProfile);
                    } finally {
                        if (trueuuid$attempt.phase() != LoginStateMachine.Phase.AWAITING_MIGRATION) trueuuid$clear();
                    }
                }));
    }

    @Inject(method = "onDisconnect", at = @At("HEAD"))
    private void trueuuid$disconnect(net.minecraft.network.DisconnectionDetails details, CallbackInfo callback) {
        trueuuid$clear();
    }

    @Unique private static GameProfile trueuuid$profile(VerifiedProfile profile) {
        ImmutableMultimap.Builder<String, Property> properties = ImmutableMultimap.builder();
        for (VerifiedProfile.Property property : profile.properties()) {
            properties.put(property.name(), property.signature() == null
                    ? new Property(property.name(), property.value())
                    : new Property(property.name(), property.value(), property.signature()));
        }
        return new GameProfile(profile.uuid(), profile.name(), new PropertyMap(properties.build()));
    }

    @Unique private void trueuuid$clear() {
        if (trueuuid$pendingVerifiedProfile != null) {
            AdapterRuntime.clearMigrationPending(trueuuid$pendingVerifiedProfile.name());
        }
        AuthQueryTracker.clear(trueuuid$transaction);
        trueuuid$transaction = 0;
        trueuuid$pendingVerifiedProfile = null;
        trueuuid$pendingOfflineData = null;
        trueuuid$pendingIp = null;
        trueuuid$pendingEndpoint = null;
        trueuuid$attempt.disconnect();
    }

    @Unique private int trueuuid$newTransaction() {
        int transaction;
        do {
            transaction = TRUEUUID$TRANSACTIONS.nextInt(1, Integer.MAX_VALUE);
        } while (!AuthQueryTracker.mark(transaction));
        return transaction;
    }

    @Unique private void trueuuid$requestMigrationConfirmation(VerifiedProfile verified,
                                                               PlayerDataMigration.OfflineData offlineData,
                                                               String ip, String endpoint) {
        AuthQueryTracker.clear(trueuuid$transaction);
        trueuuid$transaction = trueuuid$newTransaction();
        trueuuid$pendingVerifiedProfile = verified;
        trueuuid$pendingOfflineData = offlineData;
        trueuuid$pendingIp = ip;
        trueuuid$pendingEndpoint = endpoint;
        byte[] wire = trueuuid$attempt.migrationQuery(trueuuid$transaction,
                new MigrationTransaction.Offer(offlineData.offlineUuid(), offlineData.summary()),
                System.currentTimeMillis());
        cn.alini.trueuuid.Trueuuid.acceptance("phase=migration_query_sent player={} offlineUuid={} verifiedUuid={}",
                verified.name(), offlineData.offlineUuid(), verified.uuid());
        trueuuid$connection().send(new ClientboundCustomQueryPacket(trueuuid$transaction,
                new AuthPayload(AuthWireCodec.decodeQuery(wire))));
    }

    @Unique private void trueuuid$handleMigrationAnswer(ServerboundCustomQueryAnswerPacket packet,
                                                        AuthAnswerPayload answer, String ip) {
        if (!trueuuid$attempt.acceptMigration(packet.transactionId(), AuthWireCodec.encodeAnswer(answer.message()))
                || trueuuid$pendingVerifiedProfile == null || trueuuid$pendingOfflineData == null) {
            cn.alini.trueuuid.Trueuuid.acceptance("result=migration_rejected player={}",
                    authenticatedProfile == null ? "<unknown>" : authenticatedProfile.name());
            trueuuid$sendDuplicateUuidDisconnect();
            trueuuid$clear();
            return;
        }
        VerifiedProfile verified = trueuuid$pendingVerifiedProfile;
        PlayerDataMigration.OfflineData offlineData = trueuuid$pendingOfflineData;
        String migrationName = verified.name();
        cn.alini.trueuuid.Trueuuid.acceptance("phase=migration_answer_accepted player={} uuid={}", migrationName, verified.uuid());
        AdapterRuntime.markMigrationPending(migrationName);
        AdapterRuntime.migrations().migrate(trueuuid$server(), migrationName, verified.uuid())
                .whenComplete((ignored, failure) -> trueuuid$server().execute(() -> {
                    try {
                        if (!trueuuid$connection().isConnected()) return;
                        if (failure != null) {
                            cn.alini.trueuuid.Trueuuid.acceptance("result=migration_failed player={}", migrationName);
                            disconnect(Component.translatable("trueuuid.disconnect.migration_failed",
                                    migrationName, offlineData.offlineUuid(), verified.uuid(),
                                    Component.translatable("trueuuid.error.internal")));
                            return;
                        }
                        authenticatedProfile = trueuuid$profile(verified);
                        AdapterRuntime.recordVerifiedProfile(verified,
                                trueuuid$pendingIp == null ? ip : trueuuid$pendingIp, trueuuid$pendingEndpoint);
                        cn.alini.trueuuid.Trueuuid.acceptance("result=migration_complete player={} uuid={}", migrationName, verified.uuid());
                        trueuuid$finishLogin(authenticatedProfile);
                    } finally {
                        AdapterRuntime.clearMigrationPending(migrationName);
                        trueuuid$clear();
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

    @Unique private record VerifiedLookup(LoginAttempt.Outcome outcome, PlayerDataMigration.OfflineData offlineData) {}
}
