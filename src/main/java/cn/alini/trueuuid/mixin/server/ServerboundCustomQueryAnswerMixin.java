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
    private static void trueuuid$decodeAuthAnswer(int txId,
                                                  FriendlyByteBuf buf,
                                                  CallbackInfoReturnable<CustomQueryAnswerPayload> cir) {
        // 先检查是否是TrueUUID的事务ID，但不立即消费
        if (AuthQueryTracker.contains(txId)) {
            try {
                // 备份当前读取位置
                int readerIndex = buf.readerIndex();
                
                // 尝试读取并验证数据格式（AuthAnswerPayload: boolean + int + long = 1 + 4 + 8 = 13字节）
                if (buf.readableBytes() >= 13) {
                    buf.readBoolean(); // boolean ok
                    buf.readInt();     // int version  
                    buf.readLong();    // long timestamp
                    
                    // 如果成功读取，重置读取位置并消费txId
                    buf.readerIndex(readerIndex);
                    if (AuthQueryTracker.consume(txId)) {
                        cir.setReturnValue(new AuthAnswerPayload(buf));
                        return;
                    }
                }
                
                // 如果失败，重置读取位置
                buf.readerIndex(readerIndex);
            } catch (Exception e) {
                // 读取失败，可能不是TrueUUID包，不处理
            }
        }
    }
}