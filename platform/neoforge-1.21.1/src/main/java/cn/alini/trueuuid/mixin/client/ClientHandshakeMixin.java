package cn.alini.trueuuid.mixin.client;

import cn.alini.trueuuid.net.AuthAnswerPayload;
import cn.alini.trueuuid.net.AuthPayload;
import cn.alini.trueuuid.client.ClientAccountStatus;
import cn.alini.trueuuid.client.ClientYggdrasilEndpoint;
import cn.alini.trueuuid.net.NetIds;
import cn.alini.trueuuid.protocol.AuthMessages;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.multiplayer.ClientHandshakePacketListenerImpl;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.login.ClientboundCustomQueryPacket;
import net.minecraft.network.protocol.login.ServerboundCustomQueryAnswerPacket;
import net.minecraft.network.protocol.login.custom.CustomQueryPayload;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@Mixin(ClientHandshakePacketListenerImpl.class)
abstract class ClientHandshakeMixin {
    @Shadow private Connection connection;
    @Shadow private Consumer<Component> updateStatus;

    @Inject(method = "handleCustomQuery", at = @At("HEAD"), cancellable = true)
    private void trueuuid$answer(ClientboundCustomQueryPacket packet, CallbackInfo callback) {
        CustomQueryPayload payload = packet.payload();
        if (!NetIds.AUTH.equals(payload.id()) || !(payload instanceof AuthPayload query)) return;

        Minecraft minecraft = Minecraft.getInstance();
        User user = minecraft.getUser();
        String token = user.getAccessToken();
        Connection loginConnection = connection;
        int transactionId = packet.transactionId();
        if (token == null || token.isBlank() || "0".equals(token)) {
            ClientAccountStatus.markOffline();
            trueuuid$send(loginConnection, transactionId, false, "", true);
            callback.cancel();
            return;
        }

        // Resolve the skin-site endpoint before joinServer: the join assertion
        // is one-shot, and an authlib-injector session must be verified by the
        // server against the same Yggdrasil service (subject to its allowlist).
        String hasJoinedUrl = ClientYggdrasilEndpoint.resolveHasJoinedUrl();
        updateStatus.accept(Component.translatable("connect.authorizing"));
        CompletableFuture.supplyAsync(() -> {
                    try {
                        // The access token stays on the client. The server only
                        // receives a later hasJoined assertion through its safe verifier.
                        minecraft.getMinecraftSessionService().joinServer(user.getProfileId(), token, query.message().nonce());
                        return true;
                    } catch (Throwable ignored) {
                        return false;
                    }
                })
                .orTimeout(25, TimeUnit.SECONDS)
                .exceptionally(error -> false)
                .thenAccept(joined -> {
                    if (joined) ClientAccountStatus.markPremium();
                    else ClientAccountStatus.markOffline();
                    trueuuid$send(loginConnection, transactionId, joined, hasJoinedUrl, false);
                });
        callback.cancel();
    }

    private static void trueuuid$send(Connection connection, int transactionId, boolean joined, String hasJoinedUrl, boolean missingToken) {
        connection.send(new ServerboundCustomQueryAnswerPacket(transactionId,
                new AuthAnswerPayload(new AuthMessages.Answer(joined, hasJoinedUrl, false, missingToken))));
    }
}
