package cn.alini.trueuuid.mixin.server;

import cn.alini.trueuuid.net.ForgeAuthAnswerPayload;
import cn.alini.trueuuid.net.ForgeAuthPayload;
import cn.alini.trueuuid.net.ForgeQueryTracker;
import cn.alini.trueuuid.protocol.AuthWireCodec;
import cn.alini.trueuuid.protocol.VerifiedProfile;
import cn.alini.trueuuid.server.ForgeAdapterRuntime;
import cn.alini.trueuuid.server.ForgeLoginFlow;
import cn.alini.trueuuid.Trueuuid;
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

@Mixin(ServerLoginPacketListenerImpl.class)
abstract class ForgeServerLoginMixin {
    @Unique private static final SecureRandom TRUEUUID$TRANSACTIONS = new SecureRandom();
    @Shadow private GameProfile authenticatedProfile;
    @Shadow final MinecraftServer server = null;
    @Shadow final Connection connection = null;
    @Shadow public abstract void disconnect(Component reason);
    @Invoker("verifyLoginAndFinishConnectionSetup") abstract void trueuuid$completeNativeLogin(GameProfile profile);

    @Unique private final ForgeLoginFlow trueuuid$flow = new ForgeLoginFlow();
    @Unique private int trueuuid$transaction;

    @Inject(method = "handleHello", at = @At("TAIL"))
    private void trueuuid$requestAssertion(ServerboundHelloPacket packet, CallbackInfo callback) {
        if (server.usesAuthentication() || authenticatedProfile == null) return;
        do {
            trueuuid$transaction = TRUEUUID$TRANSACTIONS.nextInt(1, Integer.MAX_VALUE);
        } while (!ForgeQueryTracker.register(trueuuid$transaction));
        byte[] wire = trueuuid$flow.start(trueuuid$transaction, UUID.randomUUID().toString().replace("-", ""), System.currentTimeMillis());
        Trueuuid.LOGGER.info("TrueUUID authentication challenge sent: player={}", authenticatedProfile.getName());
        connection.send(new ClientboundCustomQueryPacket(trueuuid$transaction,
                new ForgeAuthPayload(AuthWireCodec.decodeQuery(wire))));
    }

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void trueuuid$holdLogin(CallbackInfo callback) {
        if (!trueuuid$flow.active()) return;
        callback.cancel();
        if (trueuuid$flow.timedOut(System.currentTimeMillis(), 30_000L)) {
            disconnect(Component.translatable("trueuuid.disconnect.timeout"));
            trueuuid$closeFlow();
        }
    }

    @Inject(method = "handleCustomQueryPacket", at = @At("HEAD"), cancellable = true)
    private void trueuuid$checkAssertion(ServerboundCustomQueryAnswerPacket packet, CallbackInfo callback) {
        if (packet.transactionId() != trueuuid$transaction || !(packet.payload() instanceof ForgeAuthAnswerPayload answer)
                || authenticatedProfile == null) return;
        callback.cancel();
        if (!answer.message().joined()) {
            String playerName = authenticatedProfile.getName();
            if (!ForgeAdapterRuntime.canUseOfflineFallback(playerName)) {
                Trueuuid.LOGGER.warn("TrueUUID offline fallback denied for previously verified name: player={}", playerName);
                disconnect(Component.translatable("trueuuid.disconnect.bound_premium"));
            } else {
                ForgeAdapterRuntime.recordOfflineFallback(authenticatedProfile);
                trueuuid$completeNativeLogin(authenticatedProfile);
            }
            trueuuid$closeFlow();
            return;
        }
        String ip = connection.getRemoteAddress() instanceof InetSocketAddress address && address.getAddress() != null
                ? address.getAddress().getHostAddress() : "";
        trueuuid$flow.accept(packet.transactionId(), AuthWireCodec.encodeAnswer(answer.message()), authenticatedProfile.getName(), ip,
                        ForgeAdapterRuntime.verifier())
                .thenAccept(profile -> server.execute(() -> {
                    try {
                        if (!connection.isConnected()) return;
                        if (profile.isEmpty()) {
                            Trueuuid.LOGGER.warn("TrueUUID premium verification denied: player={}", authenticatedProfile.getName());
                            disconnect(Component.translatable("trueuuid.disconnect.auth_denied"));
                            return;
                        }
                        authenticatedProfile = trueuuid$toNativeProfile(profile.get());
                        ForgeAdapterRuntime.recordVerifiedProfile(profile.get());
                        trueuuid$completeNativeLogin(authenticatedProfile);
                    } finally {
                        trueuuid$closeFlow();
                    }
                }));
    }

    @Inject(method = "onDisconnect", at = @At("HEAD"))
    private void trueuuid$cancel(net.minecraft.network.DisconnectionDetails details, CallbackInfo callback) {
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
        ForgeQueryTracker.discard(trueuuid$transaction);
        trueuuid$transaction = 0;
        trueuuid$flow.close();
    }
}
