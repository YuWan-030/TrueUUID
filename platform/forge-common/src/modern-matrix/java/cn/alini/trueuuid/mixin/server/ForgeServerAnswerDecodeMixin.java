package cn.alini.trueuuid.mixin.server;

import cn.alini.trueuuid.Trueuuid;
import cn.alini.trueuuid.net.ForgeAuthAnswerPayload;
import cn.alini.trueuuid.net.ForgeQueryTracker;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.login.ServerboundCustomQueryAnswerPacket;
import net.minecraft.network.protocol.login.custom.CustomQueryAnswerPayload;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Answer decoder shared by the pre-record 1.21.6+ Forge mappings. */
@Mixin(ServerboundCustomQueryAnswerPacket.class)
abstract class ForgeServerAnswerDecodeMixin {
    @Inject(method = "readPayload", at = @At("HEAD"), cancellable = true)
    private static void trueuuid$read(int transactionId, FriendlyByteBuf data,
                                      CallbackInfoReturnable<CustomQueryAnswerPayload> callback) {
        if (!ForgeQueryTracker.claim(transactionId)) return;

        // 1.21.1 writes query answers through FriendlyByteBuf#writeNullable:
        // consume its presence flag before decoding the TrueUUID wire payload.
        try {
            if (!data.readBoolean()) {
                throw new IllegalArgumentException("Missing TrueUUID authentication answer");
            }
            callback.setReturnValue(new ForgeAuthAnswerPayload(data));
        } catch (RuntimeException exception) {
            // Do not log the payload: it is client-controlled. The transaction id and
            // remaining bounded byte count are enough to diagnose framing failures.
            Trueuuid.LOGGER.warn("TrueUUID authentication answer decode failed: transaction={}, remainingBytes={}",
                    transactionId, data.readableBytes(), exception);
            throw exception;
        }
    }
}
