package cn.alini.trueuuid.mixin.client;

import cn.alini.trueuuid.net.ForgeAuthAnswerPayload;
import cn.alini.trueuuid.net.ForgeAuthPayload;
import cn.alini.trueuuid.net.ForgeNetIds;
import cn.alini.trueuuid.protocol.AuthMessages;
import cn.alini.trueuuid.client.ClientAccountStatus;
import cn.alini.trueuuid.client.ClientAuthDiagnostics;
import cn.alini.trueuuid.client.ClientYggdrasilEndpoint;
import cn.alini.trueuuid.Trueuuid;
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

    // See ForgeClientQueryDecodeMixin: production Forge uses this official
    // name while the userdev refmap contains its SRG alias.
    @Inject(method = "handleCustomQuery", at = @At("HEAD"), cancellable = true, remap = false)
    private void trueuuid$join(ClientboundCustomQueryPacket packet, CallbackInfo callback) {
        CustomQueryPayload payload = packet.payload();
        if (!ForgeNetIds.AUTH.equals(payload.id()) || !(payload instanceof ForgeAuthPayload query)) return;
        Minecraft minecraft = Minecraft.getInstance();
        User user = minecraft.getUser();
        String accessToken = user.getAccessToken();
        if (accessToken == null || accessToken.isBlank() || "0".equals(accessToken)) {
            Trueuuid.debug("TrueUUID client session token is absent or is a development placeholder");
            ClientAccountStatus.markOffline();
            trueuuid$reply(connection, packet.transactionId(), false, "", true);
            callback.cancel();
            return;
        }

        // Resolve the skin-site endpoint before joinServer: the join assertion
        // is one-shot, and an authlib-injector session must be verified by the
        // server against the same Yggdrasil service (subject to its allowlist).
        String hasJoinedUrl = ClientYggdrasilEndpoint.resolveHasJoinedUrl();
        updateStatus.accept(Component.translatable("connect.authorizing"));
        Connection loginConnection = connection;
        CompletableFuture.supplyAsync(() -> {
                    try {
                        minecraft.getMinecraftSessionService().joinServer(user.getProfileId(), accessToken, query.message().nonce());
                        Trueuuid.debug("TrueUUID joinServer completed successfully");
                        return true;
                    } catch (Throwable failure) {
                        Trueuuid.debug("TrueUUID joinServer failed: {}", ClientAuthDiagnostics.failureCategory(failure));
                        return false;
                    }
                })
                .orTimeout(25, TimeUnit.SECONDS)
                .exceptionally(error -> false)
                .thenAccept(joined -> {
                    if (joined) ClientAccountStatus.markPremium();
                    else ClientAccountStatus.markOffline();
                    trueuuid$reply(loginConnection, packet.transactionId(), joined, hasJoinedUrl, false);
                });
        callback.cancel();
    }

    private static void trueuuid$reply(Connection connection, int transactionId, boolean joined, String hasJoinedUrl, boolean missingToken) {
        connection.send(new ServerboundCustomQueryAnswerPacket(transactionId,
                new ForgeAuthAnswerPayload(new AuthMessages.Answer(joined, hasJoinedUrl, false, missingToken))));
    }
}
