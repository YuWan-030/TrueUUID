package cn.alini.trueuuid.mixin.client;

import cn.alini.trueuuid.config.TrueuuidConfig;
import cn.alini.trueuuid.net.AuthAnswerPayload;
import cn.alini.trueuuid.net.AuthPayload;
import cn.alini.trueuuid.net.NetIds;
import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.multiplayer.ClientHandshakePacketListenerImpl;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.login.ClientboundCustomQueryPacket;
import net.minecraft.network.protocol.login.ServerboundCustomQueryAnswerPacket;
import net.minecraft.network.protocol.login.custom.CustomQueryAnswerPayload;
import net.minecraft.network.protocol.login.custom.CustomQueryPayload;
import net.minecraft.network.protocol.login.custom.DiscardedQueryAnswerPayload;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientHandshakePacketListenerImpl.class)
public abstract class ClientHandshakeMixin {
    @Shadow private Connection connection;

    @Inject(method = "handleCustomQuery", at = @At("HEAD"), cancellable = true)
    private void trueuuid$onCustomQuery(ClientboundCustomQueryPacket packet, CallbackInfo ci) {
        CustomQueryPayload payload = packet.payload();
        if (!NetIds.AUTH.equals(payload.id())) return;
        if(!(payload instanceof AuthPayload(String serverId))) return;

        boolean ok;
        try {
            Minecraft mc = Minecraft.getInstance();
            User user = mc.getUser();
            var profile = user.getProfileId();
            String token = user.getAccessToken();

            // 令牌只在本地使用
            mc.getMinecraftSessionService().joinServer(profile, token, serverId);
            ok = true;
        } catch (Throwable t) {
            ok = false;
        }
        AuthAnswerPayload answer = new AuthAnswerPayload(ok);
        this.connection.send(new ServerboundCustomQueryAnswerPacket(packet.transactionId(), answer));
        ci.cancel();
    }
}