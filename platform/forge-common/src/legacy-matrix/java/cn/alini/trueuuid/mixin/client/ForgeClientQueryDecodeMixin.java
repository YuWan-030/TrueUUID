package cn.alini.trueuuid.mixin.client;

import cn.alini.trueuuid.net.ForgeAuthPayload;
import cn.alini.trueuuid.net.ForgeNetIds;
import cn.alini.trueuuid.mixin.ForgeRuntimeNames;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.login.ClientboundCustomQueryPacket;
import net.minecraft.network.protocol.login.custom.CustomQueryPayload;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ClientboundCustomQueryPacket.class, remap = ForgeRuntimeNames.REMAP_MEMBERS)
abstract class ForgeClientQueryDecodeMixin {
    // Forge 48/49 production uses SRG members; Forge 50 keeps official names.
    // The target-specific constant prevents an SRG refmap from breaking 50.
    @Inject(method = "readPayload", at = @At("HEAD"), cancellable = true)
    private static void trueuuid$read(ResourceLocation id, FriendlyByteBuf data,
                                      CallbackInfoReturnable<CustomQueryPayload> callback) {
        if (ForgeNetIds.AUTH.equals(id)) callback.setReturnValue(new ForgeAuthPayload(data));
    }
}
