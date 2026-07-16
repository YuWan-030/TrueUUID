package cn.alini.trueuuid.mixin.server;

import cn.alini.trueuuid.config.TrueuuidConfig;
import cn.alini.trueuuid.net.AuthAnswerPayload;
import cn.alini.trueuuid.net.AuthPayload;
import cn.alini.trueuuid.net.AuthQueryTracker;
import cn.alini.trueuuid.protocol.AuthWireCodec;
import cn.alini.trueuuid.protocol.VerifiedProfile;
import cn.alini.trueuuid.server.AdapterRuntime;
import cn.alini.trueuuid.server.LoginAttempt;
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
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.net.InetSocketAddress;
import java.util.UUID;
import java.security.SecureRandom;

@Mixin(ServerLoginPacketListenerImpl.class)
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

    @Inject(method = "handleHello", at = @At("TAIL"))
    private void trueuuid$begin(ServerboundHelloPacket packet, CallbackInfo callback) {
        MinecraftServer server = trueuuid$server();
        Connection connection = trueuuid$connection();
        if (server.usesAuthentication() || authenticatedProfile == null) return;
        do {
            trueuuid$transaction = TRUEUUID$TRANSACTIONS.nextInt(1, Integer.MAX_VALUE);
        } while (!AuthQueryTracker.mark(trueuuid$transaction));
        byte[] query = trueuuid$attempt.begin(trueuuid$transaction, UUID.randomUUID().toString().replace("-", ""),
                System.currentTimeMillis());
        connection.send(new ClientboundCustomQueryPacket(trueuuid$transaction,
                new AuthPayload(AuthWireCodec.decodeQuery(query))));
    }

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void trueuuid$pauseVanillaLogin(CallbackInfo callback) {
        if (!trueuuid$attempt.phase().equals(cn.alini.trueuuid.protocol.LoginStateMachine.Phase.IDLE)) {
            callback.cancel();
            if (trueuuid$attempt.timeout(System.currentTimeMillis(), TrueuuidConfig.timeoutMs()) != LoginAttempt.Result.TIMEOUT) return;
            String name = authenticatedProfile == null ? null : authenticatedProfile.getName();
            if (TrueuuidConfig.allowOfflineOnTimeout() && authenticatedProfile != null) {
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
        cn.alini.trueuuid.Trueuuid.debug("TrueUUID received authentication response: player={}", authenticatedProfile.getName());
        MinecraftServer server = trueuuid$server();
        Connection connection = trueuuid$connection();
        String name = authenticatedProfile.getName();
        String ip = connection.getRemoteAddress() instanceof InetSocketAddress address && address.getAddress() != null
                ? address.getAddress().getHostAddress() : "";
        trueuuid$attempt.answer(packet.transactionId(), AuthWireCodec.encodeAnswer(answer.message()), name, ip, AdapterRuntime.verifier())
                .thenAccept(outcome -> server.execute(() -> {
                    try {
                        if (!connection.isConnected()) return;
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
                                disconnect(Component.translatable("trueuuid.disconnect.bound_premium"));
                            } else {
                                AdapterRuntime.recordOfflineFallback(authenticatedProfile);
                                trueuuid$finishLogin(authenticatedProfile);
                            }
                            return;
                        }
                        authenticatedProfile = trueuuid$profile(outcome.profile().get());
                        AdapterRuntime.recordVerifiedProfile(outcome.profile().get(), ip);
                        trueuuid$finishLogin(authenticatedProfile);
                    } finally {
                        trueuuid$clear();
                    }
                }));
    }

    @Inject(method = "onDisconnect", at = @At("HEAD"))
    private void trueuuid$disconnect(net.minecraft.network.DisconnectionDetails details, CallbackInfo callback) {
        trueuuid$clear();
    }

    @Unique private GameProfile trueuuid$profile(VerifiedProfile profile) {
        GameProfile nativeProfile = new GameProfile(profile.uuid(), profile.name());
        for (VerifiedProfile.Property property : profile.properties()) {
            nativeProfile.getProperties().put(property.name(), property.signature() == null
                    ? new Property(property.name(), property.value())
                    : new Property(property.name(), property.value(), property.signature()));
        }
        return nativeProfile;
    }

    @Unique private void trueuuid$clear() {
        AuthQueryTracker.clear(trueuuid$transaction);
        trueuuid$transaction = 0;
        trueuuid$attempt.disconnect();
    }
}
