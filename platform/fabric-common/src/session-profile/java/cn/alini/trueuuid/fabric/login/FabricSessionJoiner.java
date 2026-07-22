package cn.alini.trueuuid.fabric.login;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.Session;

/** Minecraft 1.20.1 passes a complete profile to authlib's joinServer call. */
final class FabricSessionJoiner {
    static void join(MinecraftClient client, Session session, String serverId) throws Exception {
        client.getSessionService().joinServer(session.getProfile(), session.getAccessToken(), serverId);
    }

    private FabricSessionJoiner() {}
}
