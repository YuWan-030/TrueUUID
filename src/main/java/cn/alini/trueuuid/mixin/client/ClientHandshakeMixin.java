package cn.alini.trueuuid.mixin.client;

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
        if (!NetIds.AUTH.equals(packet.payload().id())) return;
        String serverId = null;

        try {
            CustomQueryPayload payload = packet.payload();

            // 创建临时 buffer 来读取数据
            FriendlyByteBuf tempBuf = new FriendlyByteBuf(Unpooled.buffer());
            payload.write(tempBuf); // 写入
            serverId = tempBuf.readUtf(); // 读取
            tempBuf.release(); // 释放

        } catch (Exception e) {
            if (cn.alini.trueuuid.config.TrueuuidConfig.debug()) {
                System.out.println("[TrueUUID] 读取 serverId 失败: " + e);
            }
            ci.cancel();
            return;
        }

        if (serverId.isEmpty()) {
            ci.cancel();
            return;
        }

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

        // 创建响应 payload
        final boolean finalOk = ok;
        CustomQueryAnswerPayload answerPayload = new CustomQueryAnswerPayload() {
            @Override
            public void write(FriendlyByteBuf buf) {
                buf.writeBoolean(finalOk);
            }
        };

        this.connection.send(new ServerboundCustomQueryAnswerPacket(packet.transactionId(), answerPayload));
        ci.cancel();
    }
}