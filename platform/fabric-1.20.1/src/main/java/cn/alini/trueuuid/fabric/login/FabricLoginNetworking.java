package cn.alini.trueuuid.fabric.login;

import cn.alini.trueuuid.fabric.TrueuuidFabric;
import cn.alini.trueuuid.fabric.client.FabricClientStatus;
import cn.alini.trueuuid.protocol.AuthMessages;
import net.fabricmc.fabric.api.client.networking.v1.ClientLoginNetworking;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerLoginConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerLoginNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;

import java.util.concurrent.CompletableFuture;

/** Fabric login-phase registration and native packet conversion only. */
public final class FabricLoginNetworking {
    public static final Identifier AUTH_CHANNEL = new Identifier(TrueuuidFabric.MOD_ID, "auth");
    private static boolean serverRegistered;
    private static boolean clientRegistered;

    public static synchronized void registerServerHooks() {
        if (serverRegistered) return;
        serverRegistered = true;

        ServerLoginConnectionEvents.QUERY_START.register((handler, server, sender, synchronizer) -> {
            // Vanilla already owns authentication for an online-mode server.
            if (!server.isOnlineMode()) {
                transaction(handler).begin(handler, server, sender, synchronizer);
            }
        });
        ServerLoginConnectionEvents.DISCONNECT.register((handler, server) -> transaction(handler).cancel());
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            FabricSessionCheck.close();
            FabricAdapterRuntime.shutdown();
        });

        if (!ServerLoginNetworking.registerGlobalReceiver(AUTH_CHANNEL,
                (server, handler, understood, buffer, synchronizer, responseSender) -> {
                    AuthMessages.Answer answer = null;
                    if (understood) {
                        try {
                            answer = FabricLoginPayloads.readAnswer(buffer);
                        } catch (IllegalArgumentException malformed) {
                            TrueuuidFabric.LOGGER.warn("Rejected malformed TrueUUID Fabric login answer: {}",
                                    malformed.getMessage());
                        }
                    }
                    transaction(handler).answer(server, handler, understood, answer);
                })) {
            throw new IllegalStateException("TrueUUID Fabric login channel was already registered");
        }
    }

    public static synchronized void registerClientHooks() {
        if (clientRegistered) return;
        clientRegistered = true;
        if (!ClientLoginNetworking.registerGlobalReceiver(AUTH_CHANNEL,
                (client, handler, buffer, listenerAdder) -> {
                    try {
                        AuthMessages.Query query = FabricLoginPayloads.readQuery(buffer);
                        return CompletableFuture.supplyAsync(() -> joinedMojangSession(query.nonce()))
                                .orTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                                .exceptionally(failure -> false)
                                .thenApply(joined -> {
                                    if (joined) FabricClientStatus.markPremium();
                                    else FabricClientStatus.markOffline();
                                    return FabricLoginPayloads.answer(
                                            new AuthMessages.Answer(joined, "", false, !joined));
                                });
                    } catch (IllegalArgumentException malformed) {
                        TrueuuidFabric.LOGGER.warn("Rejected malformed TrueUUID Fabric login query: {}",
                                malformed.getMessage());
                        return CompletableFuture.completedFuture(null);
                    }
                })) {
            throw new IllegalStateException("TrueUUID Fabric client login channel was already registered");
        }
    }

    private static boolean joinedMojangSession(String serverId) {
        MinecraftClient client = MinecraftClient.getInstance();
        var session = client.getSession();
        if (session.getUuidOrNull() == null || session.getAccessToken() == null
                || session.getAccessToken().isBlank() || "0".equals(session.getAccessToken())) {
            return false;
        }
        try {
            // The access token never crosses the loader packet boundary.
            client.getSessionService().joinServer(session.getProfile(), session.getAccessToken(), serverId);
            return true;
        } catch (Throwable failure) {
            TrueuuidFabric.LOGGER.debug("TrueUUID Fabric joinServer failed: {}", failure.getClass().getSimpleName());
            return false;
        }
    }

    private static FabricLoginTransaction transaction(Object handler) {
        return ((FabricLoginStateAccess) handler).trueuuid$getLoginTransaction();
    }

    private FabricLoginNetworking() {}
}
