package cn.alini.trueuuid.mixin.client;

import cn.alini.trueuuid.net.ForgeAuthPayload;
import cn.alini.trueuuid.net.ForgeNetIds;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.login.ClientboundCustomQueryPacket;
import net.minecraft.network.protocol.login.custom.CustomQueryPayload;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Query decoder shared by the pre-record 1.21.6+ Forge mappings. */
@Mixin(ClientboundCustomQueryPacket.class)
abstract class ForgeClientQueryDecodeMixin {
    // Forge's distributed client jar retains Mojang's official private method
    // name. Do not remap this to the userdev SRG name recorded in the refmap.
    @Inject(method = "readPayload", at = @At("HEAD"), cancellable = true, remap = false)
    private static void trueuuid$read(Identifier id, FriendlyByteBuf data,
                                      CallbackInfoReturnable<CustomQueryPayload> callback) {
        if (ForgeNetIds.AUTH.equals(id)) callback.setReturnValue(new ForgeAuthPayload(data));
    }
}
