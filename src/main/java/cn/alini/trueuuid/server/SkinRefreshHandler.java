package cn.alini.trueuuid.server;

import cn.alini.trueuuid.Trueuuid;
import cn.alini.trueuuid.config.TrueuuidConfig;
import cn.alini.trueuuid.util.TrueuuidText;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 登录后刷新外观，并在“离线放行”时提示玩家；同时显示屏幕标题提示当前模式。
 * (Refresh skin after login, and notify player when "offline fallback" occurs; also display screen title to indicate current mode.)
 */
@Mod.EventBusSubscriber(modid = Trueuuid.MODID)
public class SkinRefreshHandler {
    private static final int LOCAL_SELF_REFRESH_DELAY_TICKS = 20;
    private static final Map<UUID, Integer> PENDING_LOCAL_SELF_REFRESH = new ConcurrentHashMap<>();
    private static final int SUBTITLE_MAX_CHARS = 64; // 保护：过长截断，避免越界 (Protection: Truncate if too long to avoid out of bounds)

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        var server = sp.getServer();
        if (server == null) return;

        // 1) 登录后一帧刷新外观（强制客户端重拉皮肤） (Refresh skin one frame after login (force client to re-fetch skin))
        server.execute(() -> {
            var list = server.getPlayerList();
            var removePacket = new ClientboundPlayerInfoRemovePacket(List.of(sp.getUUID()));
            var updatePacket = ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(List.of(sp));
            for (ServerPlayer player : list.getPlayers()) {
                if (player.getUUID().equals(sp.getUUID())) continue;
                player.connection.send(removePacket);
                player.connection.send(updatePacket);
            }
        });

        // 2) 判断是否离线放行，并发送聊天提示 + 屏幕标题（副标题使用短文案） (Determine if offline fallback is active, and send chat notification + screen title (subtitle uses short text))
        if (isIntegratedLocalPlayer(server, sp)) {
            PENDING_LOCAL_SELF_REFRESH.put(sp.getUUID(), LOCAL_SELF_REFRESH_DELAY_TICKS);
        }

        var netConn = sp.connection.connection; // ServerGamePacketListenerImpl.connection
        var fallbackOpt = AuthState.consume(netConn);
        var successOpt = AuthState.consumeAuthSuccess(netConn, sp.getUUID(), sp.getGameProfile().getName());

        if (!TrueuuidConfig.showJoinFeedback()) {
            if (TrueuuidConfig.debug()) {
                System.out.println("[TrueUUID] 跳过登录提示: 玩家=" + sp.getGameProfile().getName() + ", 原因=showJoinFeedback=false");
            }
            return;
        }

        if (fallbackOpt.isPresent()) {
            sp.sendSystemMessage(TrueuuidText.configComponent(
                    TrueuuidConfig.offlineFallbackMessage(),
                    "trueuuid.chat.offline_fallback"
            ).withStyle(ChatFormatting.RED));

            var title = Component.translatable("trueuuid.title.offline").withStyle(ChatFormatting.RED);
            var subtitle = TrueuuidText.configComponent(
                    TrueuuidConfig.offlineShortSubtitle(),
                    "trueuuid.subtitle.offline"
            ).withStyle(ChatFormatting.YELLOW);

            sendTitleNextTick(server, sp, title, subtitle, "OFFLINE");
        } else if (successOpt.isPresent() && successOpt.get().source() == AuthState.AuthSource.YGGDRASIL) {
            // 皮肤站模式：青绿色标题，明确区别于 Mojang 正版验证。
            AuthState.AuthSuccess success = successOpt.get();
            var title = Component.translatable("trueuuid.title.skin_site").withStyle(ChatFormatting.AQUA);
            String sourceName = success.displayName();
            var subtitle = Component.translatable("trueuuid.subtitle.skin_site", sourceName).withStyle(ChatFormatting.GREEN);

            sendTitleNextTick(server, sp, title, subtitle, "YGGDRASIL:" + sourceName);
        } else if (successOpt.isPresent()) {
            // 正版模式：绿色标题，副标题短文案（灰色） (Premium Mode: Green title, subtitle short text (Gray))
            var title = Component.translatable("trueuuid.title.premium").withStyle(ChatFormatting.GREEN);
            var subtitle = TrueuuidText.configComponent(
                    TrueuuidConfig.onlineShortSubtitle(),
                    "trueuuid.subtitle.online"
            ).withStyle(ChatFormatting.GRAY);

            String mode = "MOJANG:" + successOpt.get().displayName();
            sendTitleNextTick(server, sp, title, subtitle, mode);
        } else if (!server.isDedicatedServer()) {
            var title = Component.translatable("trueuuid.title.singleplayer").withStyle(ChatFormatting.GOLD);
            var subtitle = Component.translatable("trueuuid.subtitle.singleplayer").withStyle(ChatFormatting.GRAY);

            sendTitleNextTick(server, sp, title, subtitle, "SINGLEPLAYER");
        } else if (TrueuuidConfig.debug()) {
            System.out.println("[TrueUUID] 跳过登录标题: 玩家=" + sp.getGameProfile().getName() + ", 原因=未经过 TrueUUID 登录认证状态");
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        PENDING_LOCAL_SELF_REFRESH.remove(sp.getUUID());
        String ip = trueuuid$ipOf(sp);
        if (ip == null || ip.isBlank()) return;
        TrueuuidRuntime.IP_GRACE.activateAfterLogout(sp.getGameProfile().getName(), ip);
        if (TrueuuidConfig.debug()) {
            System.out.println("[TrueUUID] 玩家退出，开启近期同 IP 容错窗口: 玩家=" + sp.getGameProfile().getName() + ", ip=" + ip + ", ttl=" + TrueuuidConfig.recentIpGraceTtlSeconds() + "s");
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || PENDING_LOCAL_SELF_REFRESH.isEmpty()) return;
        var server = event.getServer();
        if (server == null || server.isDedicatedServer()) return;

        PENDING_LOCAL_SELF_REFRESH.replaceAll((uuid, ticks) -> ticks - 1);
        PENDING_LOCAL_SELF_REFRESH.entrySet().removeIf(entry -> {
            if (entry.getValue() > 0) return false;

            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null || player.hasDisconnected()) return true;
            if (!isIntegratedLocalPlayer(server, player)) return true;

            if (TrueuuidConfig.debug()) {
                System.out.println("[TrueUUID] Refresh integrated host skin for self: player=" + player.getGameProfile().getName());
            }
            player.connection.send(ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(List.of(player)));
            return true;
        });
    }

    private static String trueuuid$ipOf(ServerPlayer sp) {
        if (sp.connection.connection.getRemoteAddress() instanceof InetSocketAddress isa) {
            return isa.getAddress().getHostAddress();
        }
        return null;
    }

    private static boolean isIntegratedLocalPlayer(net.minecraft.server.MinecraftServer server, ServerPlayer sp) {
        if (server == null || server.isDedicatedServer() || sp == null) return false;
        return !(sp.connection.connection.getRemoteAddress() instanceof InetSocketAddress);
    }

    private static void sendTitleNextTick(net.minecraft.server.MinecraftServer server, ServerPlayer sp, Component title, Component subtitle, String mode) {
        server.execute(() -> {
            if (sp.hasDisconnected()) {
                return;
            }
            if (TrueuuidConfig.debug()) {
                System.out.println("[TrueUUID] 发送登录标题: 玩家=" + sp.getGameProfile().getName() + ", mode=" + mode);
            }
            sp.connection.send(new ClientboundSetTitlesAnimationPacket(10, 60, 10));
            sp.connection.send(new ClientboundSetTitleTextPacket(title));
            sp.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
        });
    }

}
