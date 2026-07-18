package cn.alini.trueuuid.mixin.client;

import cn.alini.trueuuid.net.ForgeAuthPayload;
import cn.alini.trueuuid.net.ForgeNetIds;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.login.ClientboundCustomQueryPacket;
import net.minecraft.network.protocol.login.custom.CustomQueryPayload;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientboundCustomQueryPacket.class)
abstract class ForgeClientQueryDecodeMixin {
    // Unlike the 1.21.x line, production Forge 48-50 still runs SRG names, so this
    // target MUST be remapped through the refmap; the 1.21.x modules'
    // remap = false would silently never match here.
    @Inject(method = "readPayload", at = @At("HEAD"), cancellable = true)
    private static void trueuuid$read(ResourceLocation id, FriendlyByteBuf data,
                                      CallbackInfoReturnable<CustomQueryPayload> callback) {
        if (ForgeNetIds.AUTH.equals(id)) callback.setReturnValue(new ForgeAuthPayload(data));
    }
}
