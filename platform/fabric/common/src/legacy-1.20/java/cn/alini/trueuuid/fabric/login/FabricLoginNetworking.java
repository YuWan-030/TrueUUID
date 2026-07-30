package cn.alini.trueuuid.fabric.login;

import cn.alini.trueuuid.fabric.TrueuuidFabric;
import cn.alini.trueuuid.fabric.config.FabricConfig;
import cn.alini.trueuuid.fabric.command.FabricCommandPermissions;
import cn.alini.trueuuid.protocol.AuthMessages;
import cn.alini.trueuuid.presentation.LoginNotificationRouter;
import cn.alini.trueuuid.presentation.IntegratedWorldPolicy;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerLoginConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerLoginNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerRemoveS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.List;

/** Fabric 1.20-era login registration and native packet conversion only. */
public final class FabricLoginNetworking {
    public static final Identifier AUTH_CHANNEL = FabricIdentifiers.create(TrueuuidFabric.MOD_ID, "auth");
    public static final Identifier STATUS_CHANNEL = FabricIdentifiers.create(TrueuuidFabric.MOD_ID, "account_status");
    public static final String MIGRATION_CONFIRM_SERVER_ID = "trueuuid:migration-confirm";
    private static boolean serverRegistered;

    public static synchronized void registerServerHooks() {
        if (serverRegistered) return;
        serverRegistered = true;
        FabricServerStatusNetworking.registerPayload();

        ServerLoginConnectionEvents.QUERY_START.register((handler, server, sender, synchronizer) -> {
            // Vanilla already owns authentication for an online-mode server.
            if (!server.isOnlineMode()) {
                transaction(handler).begin(handler, server,
                        query -> sender.sendPacket(AUTH_CHANNEL, FabricLoginPayloads.query(query)), synchronizer);
            }
        });
        ServerLoginConnectionEvents.DISCONNECT.register((handler, server) -> transaction(handler).cancel());
        // Re-broadcast the joining player's info one tick later so other clients
        // re-fetch the skin of the replaced (verified) profile, matching 1.20.1.
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.player;
            FabricAuthenticationSource source = FabricAdapterRuntime.consumePendingLogin(player.getUuid());
            if (source == null && server.isOnlineMode()) source = FabricAuthenticationSource.NATIVE_ONLINE_MODE;
            if (source != null) publishJoinResult(server, player, source);
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
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            FabricAccountStatusTracker.clear(handler.player.getUuid());
            FabricAdapterRuntime.activateGraceAfterLogout(
                    FabricGameProfiles.name(handler.player.getGameProfile()), handler.player.getIp());
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            FabricSessionCheck.close();
            FabricAccountStatusTracker.clearAll();
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

    /** Runs only from the consumed server result after vanilla has created the player. */
    private static void publishJoinResult(net.minecraft.server.MinecraftServer server, ServerPlayerEntity player,
                                          FabricAuthenticationSource source) {
        boolean privateSingleplayer = IntegratedWorldPolicy.isPrivateSingleplayer(
                server.isSingleplayer(), server.isRemote());
        // Publish status for the addon API and notify callbacks first, so any
        // conditional join logic (separate spawn, permissions) sees the status
        // regardless of the join-feedback config below, matching ForgeAdapterRuntime.
        FabricAccountStatusTracker.publish(player, source.publicStatus());

        TrueuuidFabric.LOGGER.info("TrueUUID login_complete outcome={} player={} uuid={} auth_source={}",
                source.presentation().outcome(), FabricGameProfiles.name(player.getGameProfile()), player.getUuid(),
                source.presentation().authenticationSource());
        TrueuuidFabric.acceptance("result={} player={} uuid={}",
                source == FabricAuthenticationSource.OFFLINE_FALLBACK ? "offline_fallback" : "premium_join",
                FabricGameProfiles.name(player.getGameProfile()), player.getUuid());

        for (var delivery : LoginNotificationRouter.route(player, server.getPlayerManager().getPlayerList(),
                FabricCommandPermissions::isOperator,
                FabricConfig.showJoinFeedback() && !privateSingleplayer,
                FabricConfig.showOperatorNotifications())) {
            if (delivery.kind() == LoginNotificationRouter.Kind.JOIN_RESULT) {
                delivery.recipient().sendMessage(Text.translatable(source.chatKey()).formatted(chatColor(source)), false);
            } else {
                delivery.recipient().sendMessage(Text.translatable("trueuuid.operator.login",
                        FabricGameProfiles.name(player.getGameProfile()), player.getUuid(),
                        source.presentation().outcome(), source.presentation().authenticationSource())
                        .formatted(Formatting.GRAY), false);
            }
        }
        if (!privateSingleplayer && FabricConfig.showJoinTitle()) {
            player.networkHandler.sendPacket(new TitleFadeS2CPacket(10, 60, 20));
            player.networkHandler.sendPacket(new TitleS2CPacket(Text.translatable(source.titleKey()).formatted(titleColor(source))));
            player.networkHandler.sendPacket(new SubtitleS2CPacket(Text.translatable(source.subtitleKey()).formatted(Formatting.GRAY)));
        }
        if (!privateSingleplayer) FabricServerStatusNetworking.send(player, source.clientStatus());
    }

    private static Formatting chatColor(FabricAuthenticationSource source) {
        return source.clientStatus() == FabricAuthenticationSource.ClientStatus.PREMIUM ? Formatting.GREEN : Formatting.RED;
    }

    private static Formatting titleColor(FabricAuthenticationSource source) {
        return chatColor(source);
    }

    private static FabricLoginTransaction transaction(Object handler) {
        return ((FabricLoginStateAccess) handler).trueuuid$getLoginTransaction();
    }

    private FabricLoginNetworking() {}
}
