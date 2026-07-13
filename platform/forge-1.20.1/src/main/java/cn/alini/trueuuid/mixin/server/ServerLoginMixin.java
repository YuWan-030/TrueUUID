package cn.alini.trueuuid.mixin.server;

import cn.alini.trueuuid.server.ServerLoginController;
import com.mojang.authlib.GameProfile;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.login.ServerboundCustomQueryPacket;
import net.minecraft.network.protocol.login.ServerboundHelloPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerLoginPacketListenerImpl.class)
public abstract class ServerLoginMixin {
    @Shadow private GameProfile gameProfile;
    @Shadow private MinecraftServer server;
    @Shadow private Connection connection;
    @Shadow public abstract void disconnect(Component reason);

    @Unique private ServerLoginController trueuuid$controller;

    @Unique private ServerLoginController trueuuid$controller() {
        if (trueuuid$controller == null) {
            trueuuid$controller = new ServerLoginController(new ServerLoginController.Access() {
                @Override public GameProfile profile() { return gameProfile; }
                @Override public void profile(GameProfile profile) { gameProfile = profile; }
                @Override public MinecraftServer server() { return server; }
                @Override public Connection connection() { return connection; }
                @Override public void disconnect(Component reason) { ServerLoginMixin.this.disconnect(reason); }
            });
        }
        return trueuuid$controller;
    }

    @Inject(method = "handleHello", at = @At("TAIL"))
    private void trueuuid$afterHello(ServerboundHelloPacket packet, CallbackInfo ci) {
        trueuuid$controller().afterHello(packet, ci);
    }

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void trueuuid$onTick(CallbackInfo ci) {
        trueuuid$controller().onTick(ci);
    }

    @Inject(
            method = "tick",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/network/ServerLoginPacketListenerImpl;handleAcceptedLogin()V"),
            cancellable = true
    )
    private void trueuuid$beforeAcceptedLogin(CallbackInfo ci) {
        trueuuid$controller().onReadyToAccept(ci);
    }

    @Inject(method = "handleCustomQueryPacket", at = @At("HEAD"), cancellable = true)
    private void trueuuid$onCustomQuery(ServerboundCustomQueryPacket packet, CallbackInfo ci) {
        trueuuid$controller().onLoginCustom(packet, ci);
    }

    @Inject(method = "disconnect", at = @At("HEAD"))
    private void trueuuid$onDisconnect(Component reason, CallbackInfo ci) {
        if (trueuuid$controller != null) trueuuid$controller.onDisconnect();
    }
}
