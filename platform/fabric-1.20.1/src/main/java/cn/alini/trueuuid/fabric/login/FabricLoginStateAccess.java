package cn.alini.trueuuid.fabric.login;

import com.mojang.authlib.GameProfile;

/** Implemented by the narrow ServerLoginNetworkHandler mixin. */
public interface FabricLoginStateAccess {
    FabricLoginTransaction trueuuid$getLoginTransaction();

    GameProfile trueuuid$getProfile();

    void trueuuid$setProfile(GameProfile profile);
}
