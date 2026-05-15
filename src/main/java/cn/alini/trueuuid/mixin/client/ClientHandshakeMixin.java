package cn.alini.trueuuid.mixin.client;

import cn.alini.trueuuid.net.AuthAnswerPayload;
import cn.alini.trueuuid.net.AuthPayload;
import cn.alini.trueuuid.net.NetIds;
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
public abstract class ClientHandshakeMixin {
    @Shadow private Connection connection;
    @Shadow private Consumer<Component> updateStatus;

    @Inject(method = "handleCustomQuery", at = @At("HEAD"), cancellable = true)
    private void trueuuid$onCustomQuery(ClientboundCustomQueryPacket packet, CallbackInfo ci) {
        CustomQueryPayload payload = packet.payload();
        if (!NetIds.AUTH.equals(payload.id())) return;
        if(!(payload instanceof AuthPayload(String serverId))) return;

        Minecraft mc = Minecraft.getInstance();
        User user = mc.getUser();
        var profile = user.getProfileId();
        String token = user.getAccessToken();
        Connection loginConnection = this.connection;
        int transactionId = packet.transactionId();

        // dev/离线启动常见的占位 token 不可能通过 Mojang 校验，立即回失败，避免登录线程等到服务器超时。
        if (trueuuid$isMissingSessionToken(token)) {
            trueuuid$sendAuthAck(loginConnection, transactionId, false);
            ci.cancel();
            return;
        }

        // 复用原版正版登录文案，中文客户端会显示“正在登录中...”。
        this.updateStatus.accept(Component.translatable("connect.authorizing"));

        // Mojang joinServer 可能因网络卡住；放到后台线程，保留原版登录等待界面，同时在服务端 30 秒超时前回包。
        CompletableFuture.supplyAsync(() -> {
                    try {
                        // 令牌只在本地使用
                        mc.getMinecraftSessionService().joinServer(profile, token, serverId);
                        return true;
                    } catch (Throwable t) {
                        return false;
                    }
                })
                .orTimeout(30, TimeUnit.SECONDS)
                .exceptionally(t -> false)
                .thenAccept(ok -> trueuuid$sendAuthAck(loginConnection, transactionId, ok));

        ci.cancel();
    }

    private static boolean trueuuid$isMissingSessionToken(String token) {
        // 这些值通常来自开发环境或离线启动器，继续请求 Mojang 只会制造无意义等待。
        return token == null || token.isBlank() || "0".equals(token);
    }

    private static void trueuuid$sendAuthAck(Connection connection, int transactionId, boolean ok) {
        connection.send(new ServerboundCustomQueryAnswerPacket(transactionId, new AuthAnswerPayload(ok)));
    }
}
