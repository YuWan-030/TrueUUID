package cn.alini.trueuuid.mixin.server;

import cn.alini.trueuuid.net.ForgeAuthAnswerPayload;
import cn.alini.trueuuid.net.ForgeQueryTracker;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.login.ServerboundCustomQueryAnswerPacket;
import net.minecraft.network.protocol.login.custom.CustomQueryAnswerPayload;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerboundCustomQueryAnswerPacket.class)
abstract class ForgeServerAnswerDecodeMixin {
    @Inject(method = "readPayload", at = @At("HEAD"), cancellable = true)
    private static void trueuuid$read(int transactionId, FriendlyByteBuf data,
                                      CallbackInfoReturnable<CustomQueryAnswerPayload> callback) {
        if (ForgeQueryTracker.claim(transactionId)) callback.setReturnValue(new ForgeAuthAnswerPayload(data));
    }
}
