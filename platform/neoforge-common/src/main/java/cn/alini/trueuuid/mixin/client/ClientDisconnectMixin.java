package cn.alini.trueuuid.mixin.client;

import cn.alini.trueuuid.client.ClientAccountStatus;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Clears persistent and transient presentation state as soon as a play connection closes. */
@Mixin(ClientCommonPacketListenerImpl.class)
abstract class ClientDisconnectMixin {
    @Inject(method = "onDisconnect", at = @At("HEAD"))
    private void trueuuid$clearStatus(CallbackInfo callback) {
        ClientAccountStatus.clear();
    }
}
