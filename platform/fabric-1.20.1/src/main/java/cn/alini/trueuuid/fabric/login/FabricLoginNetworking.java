package cn.alini.trueuuid.fabric.login;

import cn.alini.trueuuid.fabric.TrueuuidFabric;
import cn.alini.trueuuid.fabric.client.FabricClientStatus;
import cn.alini.trueuuid.fabric.config.FabricConfig;
import cn.alini.trueuuid.protocol.AuthMessages;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientLoginNetworking;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerLoginConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerLoginNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerRemoveS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Fabric login-phase registration and native packet conversion only. */
public final class FabricLoginNetworking {
    public static final Identifier AUTH_CHANNEL = new Identifier(TrueuuidFabric.MOD_ID, "auth");
    public static final Identifier STATUS_CHANNEL = new Identifier(TrueuuidFabric.MOD_ID, "account_status");
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
        // Re-broadcast the joining player's info one tick later so other clients
        // re-fetch the skin of the replaced (verified) profile, matching 1.20.1.
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.player;
            FabricAuthenticationSource source = FabricAdapterRuntime.consumePendingLogin(player.getUuid());
            if (source == null && server.isOnlineMode()) source = FabricAuthenticationSource.NATIVE_ONLINE_MODE;
            if (source != null) publishJoinResult(player, source);
            server.execute(() -> {
                PlayerRemoveS2CPacket remove = new PlayerRemoveS2CPacket(List.of(player.getUuid()));
                PlayerListS2CPacket update = PlayerListS2CPacket.entryFromPlayer(List.of(player));
                for (ServerPlayerEntity other : server.getPlayerManager().getPlayerList()) {
                    if (other.getUuid().equals(player.getUuid())) continue;
                    other.networkHandler.sendPacket(remove);
                    other.networkHandler.sendPacket(update);
                }
            });
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                FabricAdapterRuntime.activateGraceAfterLogout(handler.player.getGameProfile().getName(), handler.player.getIp()));
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
                                .thenApply(joined -> FabricLoginPayloads.answer(
                                        new AuthMessages.Answer(joined, "", false, !joined)));
                    } catch (IllegalArgumentException malformed) {
                        TrueuuidFabric.LOGGER.warn("Rejected malformed TrueUUID Fabric login query: {}",
                                malformed.getMessage());
                        return CompletableFuture.completedFuture(null);
                    }
                })) {
            throw new IllegalStateException("TrueUUID Fabric client login channel was already registered");
        }
        if (!ClientPlayNetworking.registerGlobalReceiver(STATUS_CHANNEL,
                (client, handler, buffer, responseSender) -> {
                    FabricAuthenticationSource.ClientStatus status = FabricStatusPayloads.read(buffer);
                    if (status == null) {
                        TrueuuidFabric.LOGGER.warn("Rejected malformed TrueUUID Fabric account-status payload");
                        return;
                    }
                    client.execute(() -> FabricClientStatus.setServerStatus(status));
                })) {
            throw new IllegalStateException("TrueUUID Fabric status channel was already registered");
        }
        // Do not carry a prior server's badge into a new world. The subsequent
        // status packet can only be sent from that new server's join result.
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> FabricClientStatus.clear());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> FabricClientStatus.clear());
    }

    private static boolean joinedMojangSession(String serverId) {
        MinecraftClient client = MinecraftClient.getInstance();
        var session = client.getSession();
        if (session.getUuidOrNull() == null || session.getAccessToken() == null
                || session.getAccessToken().isBlank() || "0".equals(session.getAccessToken())) {
            TrueuuidFabric.debug("TrueUUID client session token is absent or is a development placeholder");
            return false;
        }
        try {
            // The access token never crosses the loader packet boundary.
            client.getSessionService().joinServer(session.getProfile(), session.getAccessToken(), serverId);
            TrueuuidFabric.debug("TrueUUID joinServer completed successfully");
            return true;
        } catch (Throwable failure) {
            TrueuuidFabric.debug("TrueUUID joinServer failed: {}", failureCategory(failure));
            return false;
        }
    }

    /** Runs only from the consumed server result after vanilla has created the player. */
    private static void publishJoinResult(ServerPlayerEntity player, FabricAuthenticationSource source) {
        TrueuuidFabric.LOGGER.info("TrueUUID {}: player={}, uuid={}", source.auditLabel(),
                player.getGameProfile().getName(), player.getUuid());

        if (FabricConfig.showJoinFeedback()) {
            player.sendMessage(Text.translatable(source.chatKey()).formatted(chatColor(source)), false);
        }
        if (FabricConfig.showJoinTitle()) {
            player.networkHandler.sendPacket(new TitleFadeS2CPacket(10, 60, 20));
            player.networkHandler.sendPacket(new TitleS2CPacket(Text.translatable(source.titleKey()).formatted(titleColor(source))));
            player.networkHandler.sendPacket(new SubtitleS2CPacket(Text.translatable(source.subtitleKey()).formatted(Formatting.GRAY)));
        }
        if (ServerPlayNetworking.canSend(player, STATUS_CHANNEL)) {
            var payload = PacketByteBufs.create();
            FabricStatusPayloads.write(payload, source.clientStatus());
            ServerPlayNetworking.send(player, STATUS_CHANNEL, payload);
        }
    }

    private static Formatting chatColor(FabricAuthenticationSource source) {
        return source.clientStatus() == FabricAuthenticationSource.ClientStatus.PREMIUM ? Formatting.GREEN : Formatting.RED;
    }

    private static Formatting titleColor(FabricAuthenticationSource source) {
        return chatColor(source);
    }

    /** Fixed, token-free failure categories, matching the other adapters' client diagnostics. */
    private static String failureCategory(Throwable failure) {
        String message = failure.getMessage();
        String normalized = message == null ? "" : message.toLowerCase(java.util.Locale.ROOT);
        if (normalized.contains("invalid token") || normalized.contains("invalid session")
                || normalized.contains("unauthorized") || normalized.contains("forbidden")) {
            return "account session rejected (refresh the launcher account)";
        }
        if (normalized.contains("timeout") || normalized.contains("connect")
                || normalized.contains("unavailable") || normalized.contains("service")) {
            return "authentication service unavailable";
        }
        return "authentication request rejected (" + failure.getClass().getSimpleName() + ")";
    }

    private static FabricLoginTransaction transaction(Object handler) {
        return ((FabricLoginStateAccess) handler).trueuuid$getLoginTransaction();
    }

    private FabricLoginNetworking() {}
}
