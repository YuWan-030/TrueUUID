package cn.alini.trueuuid.fabric.login;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.session.Session;

/** Minecraft 1.21.11 exposes the session service through ApiServices. */
final class FabricSessionJoiner {
    static void join(MinecraftClient client, Session session, String serverId) throws Exception {
        client.getApiServices().sessionService()
                .joinServer(session.getUuidOrNull(), session.getAccessToken(), serverId);
    }

    private FabricSessionJoiner() {}
}
