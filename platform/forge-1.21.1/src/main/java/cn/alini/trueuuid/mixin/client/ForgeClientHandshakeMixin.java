package cn.alini.trueuuid.mixin.client;

import cn.alini.trueuuid.net.ForgeAuthAnswerPayload;
import cn.alini.trueuuid.net.ForgeAuthPayload;
import cn.alini.trueuuid.net.ForgeNetIds;
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
abstract class ForgeClientHandshakeMixin {
    @Shadow private Connection connection;
    @Shadow private Consumer<Component> updateStatus;

    @Inject(method = "handleCustomQuery", at = @At("HEAD"), cancellable = true)
    private void trueuuid$join(ClientboundCustomQueryPacket packet, CallbackInfo callback) {
        CustomQueryPayload payload = packet.payload();
        if (!ForgeNetIds.AUTH.equals(payload.id()) || !(payload instanceof ForgeAuthPayload query)) return;
        Minecraft minecraft = Minecraft.getInstance();
        User user = minecraft.getUser();
        String accessToken = user.getAccessToken();
        if (accessToken == null || accessToken.isBlank() || "0".equals(accessToken)) {
            trueuuid$reply(connection, packet.transactionId(), false, true);
            callback.cancel();
            return;
        }

        updateStatus.accept(Component.translatable("connect.authorizing"));
        Connection loginConnection = connection;
        CompletableFuture.supplyAsync(() -> {
                    try {
                        minecraft.getMinecraftSessionService().joinServer(user.getProfileId(), accessToken, query.message().nonce());
                        return true;
                    } catch (Throwable ignored) {
                        return false;
                    }
                })
                .orTimeout(25, TimeUnit.SECONDS)
                .exceptionally(error -> false)
                .thenAccept(joined -> trueuuid$reply(loginConnection, packet.transactionId(), joined, false));
        callback.cancel();
    }

    private static void trueuuid$reply(Connection connection, int transactionId, boolean joined, boolean missingToken) {
        connection.send(new ServerboundCustomQueryAnswerPacket(transactionId,
                new ForgeAuthAnswerPayload(new AuthMessages.Answer(joined, "", false, missingToken))));
    }
}
