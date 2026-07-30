package cn.alini.trueuuid.fabric.login;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.session.Session;

/** Minecraft 1.20.2+ passes the authenticated UUID directly to joinServer. */
final class FabricSessionJoiner {
    static void join(MinecraftClient client, Session session, String serverId) throws Exception {
        client.getSessionService().joinServer(session.getUuidOrNull(), session.getAccessToken(), serverId);
    }

    private FabricSessionJoiner() {}
}
