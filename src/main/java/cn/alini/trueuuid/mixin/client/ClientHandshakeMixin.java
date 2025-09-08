package cn.alini.trueuuid.mixin.client;

import cn.alini.trueuuid.net.NetIds;
import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User; // official 映射
import net.minecraft.client.multiplayer.ClientHandshakePacketListenerImpl;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.login.ClientboundCustomQueryPacket;
import net.minecraft.network.protocol.login.ServerboundCustomQueryPacket;
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
        if (!NetIds.AUTH.equals(packet.getIdentifier())) return;

        FriendlyByteBuf buf = packet.getData();
        String serverId = buf.readUtf();

        boolean ok;
        try {
            Minecraft mc = Minecraft.getInstance();
            User user = mc.getUser();
            var profile = user.getGameProfile();
            String token = user.getAccessToken();

            // 令牌只在本地使用
            mc.getMinecraftSessionService().joinServer(profile, token, serverId);
            ok = true;
        } catch (Throwable t) {
            ok = false;
        }

        FriendlyByteBuf resp = new FriendlyByteBuf(Unpooled.buffer());
        resp.writeBoolean(ok);
        this.connection.send(new ServerboundCustomQueryPacket(packet.getTransactionId(), resp));

        ci.cancel();
    }

}