package cn.alini.trueuuid.mixin.client;

import cn.alini.trueuuid.client.ClientAccountStatus;
import cn.alini.trueuuid.presentation.ClientStatusMarker;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Method;

@Mixin(ClientPacketListener.class)
abstract class ClientStatusMixin {
    @Inject(method = "handleLogin", at = @At("HEAD"))
    private void trueuuid$clearOldStatus(CallbackInfo callback) {
        ClientAccountStatus.clear();
    }

    @Inject(method = "setActionBarText", at = @At("HEAD"), cancellable = true)
    private void trueuuid$receiveStatus(ClientboundSetActionBarTextPacket packet, CallbackInfo callback) {
        Component component = trueuuid$component(packet);
        var status = component == null ? null : ClientStatusMarker.decode(component.getString());
        if (status == null) return;
        ClientAccountStatus.setServerStatus(status.wireId());
        callback.cancel();
    }

    private static Component trueuuid$component(Object packet) {
        for (String name : new String[] {"text", "getText", "content"}) {
            try {
                Method method = packet.getClass().getMethod(name);
                Object value = method.invoke(packet);
                if (value instanceof Component component) return component;
            } catch (ReflectiveOperationException ignored) {
            }
        }
        return null;
    }
}
