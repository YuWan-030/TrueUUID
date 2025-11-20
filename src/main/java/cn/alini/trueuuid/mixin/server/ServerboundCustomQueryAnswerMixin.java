package cn.alini.trueuuid.mixin.server;

import cn.alini.trueuuid.net.AuthAnswerPayload;
import cn.alini.trueuuid.net.AuthQueryTracker;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.login.ServerboundCustomQueryAnswerPacket;
import net.minecraft.network.protocol.login.custom.CustomQueryAnswerPayload;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerboundCustomQueryAnswerPacket.class)
public abstract class ServerboundCustomQueryAnswerMixin {
    @Inject(method = "readPayload", at = @At("HEAD"), cancellable = true)
    private static void trueuuid$decodeAuthAnswer(
            int txId,
            FriendlyByteBuf buf,
            CallbackInfoReturnable<CustomQueryAnswerPayload> cir
    ) {
        // 只处理 trueuuid 发起的那一次查询
        if (!AuthQueryTracker.consume(txId)) {
            return;
        }

        // 1. 按原版协议先读 hasPayload
        boolean hasPayload = buf.readBoolean();
        if (!hasPayload) {
            // 客户端说没有 payload，这里你自己决定怎么处理
            // 比如直接当作失败：
            cir.setReturnValue(new AuthAnswerPayload(false));
            return;
        }

        // 2. 剩下的就是你真正的 payload 内容（一个 boolean ok）
        cir.setReturnValue(new AuthAnswerPayload(buf));
        // 对 CallbackInfoReturnable 调用 setReturnValue 会自动标记为已处理，无需再显式 ci.cancel()
    }
}