package cn.alini.trueuuid.fabric.mixin.server;

import cn.alini.trueuuid.fabric.login.FabricLoginStateAccess;
import cn.alini.trueuuid.fabric.login.FabricLoginTransaction;
import com.mojang.authlib.GameProfile;
import net.minecraft.network.ClientConnection;
import net.minecraft.server.network.ServerLoginNetworkHandler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

/** Holds TrueUUID state on its owning 1.20-era login connection. */
@Mixin(ServerLoginNetworkHandler.class)
public abstract class ServerLoginNetworkHandlerMixin implements FabricLoginStateAccess {
    @Shadow private GameProfile profile;
    @Shadow @Final ClientConnection connection;
    @Unique private final FabricLoginTransaction trueuuid$loginTransaction = new FabricLoginTransaction();

    @Override
    public FabricLoginTransaction trueuuid$getLoginTransaction() {
        return trueuuid$loginTransaction;
    }

    @Override
    public GameProfile trueuuid$getProfile() {
        return profile;
    }

    @Override
    public void trueuuid$setProfile(GameProfile profile) {
        this.profile = profile;
    }

    @Override
    public ClientConnection trueuuid$getConnection() {
        return connection;
    }
}
