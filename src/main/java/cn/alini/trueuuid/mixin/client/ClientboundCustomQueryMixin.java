package cn.alini.trueuuid.mixin.client;

import cn.alini.trueuuid.net.AuthPayload;
import cn.alini.trueuuid.net.NetIds;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.login.ClientboundCustomQueryPacket;
import net.minecraft.network.protocol.login.custom.CustomQueryPayload;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientboundCustomQueryPacket.class)
public abstract class ClientboundCustomQueryMixin {
    @Inject(method = "readPayload", at = @At("HEAD"), cancellable = true)
    private static void trueuuid$decodeAuth(ResourceLocation id,
                                            FriendlyByteBuf buf,
                                            CallbackInfoReturnable<CustomQueryPayload> cir) {
        if (NetIds.AUTH.equals(id)) {
            cir.setReturnValue(new AuthPayload(buf));
        }
    }
}