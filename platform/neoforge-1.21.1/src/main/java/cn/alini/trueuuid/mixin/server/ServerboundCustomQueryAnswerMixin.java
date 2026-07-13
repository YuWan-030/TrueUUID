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
abstract class ServerboundCustomQueryAnswerMixin {
    @Inject(method = "readPayload", at = @At("HEAD"), cancellable = true)
    private static void trueuuid$decode(int transactionId, FriendlyByteBuf buffer,
                                        CallbackInfoReturnable<CustomQueryAnswerPayload> callback) {
        if (AuthQueryTracker.consume(transactionId)) callback.setReturnValue(new AuthAnswerPayload(buffer));
    }
}
