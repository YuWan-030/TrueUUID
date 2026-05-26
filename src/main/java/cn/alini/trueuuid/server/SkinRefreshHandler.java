package cn.alini.trueuuid.server;

import cn.alini.trueuuid.Trueuuid;
import cn.alini.trueuuid.config.TrueuuidConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 登录后刷新外观，并在“离线放行”时提示玩家；同时显示屏幕标题提示当前模式。
 */
@EventBusSubscriber(modid = Trueuuid.MODID)
public class SkinRefreshHandler {
    private static final int LOCAL_SELF_REFRESH_DELAY_TICKS = 20;
    private static final Map<UUID, Integer> PENDING_LOCAL_SELF_REFRESH = new ConcurrentHashMap<>();
    private static final int SUBTITLE_MAX_CHARS = 64; // 保护：过长截断，避免越界

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        var server = sp.getServer();
        if (server == null) return;

        // 1) 登录后一帧刷新外观（强制客户端重拉皮肤）
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

        // 2) 判断是否离线放行，并发送聊天提示 + 屏幕标题（副标题使用短文案）
        if (isIntegratedLocalPlayer(server, sp)) {
            PENDING_LOCAL_SELF_REFRESH.put(sp.getUUID(), LOCAL_SELF_REFRESH_DELAY_TICKS);
        }

        var netConn = sp.connection.getConnection(); // ServerGamePacketListenerImpl.connection
        var fallbackOpt = AuthState.consume(netConn);
        var successOpt = AuthState.consumeAuthSuccess(netConn, sp.getUUID(), sp.getGameProfile().getName());

        if (fallbackOpt.isPresent()) {
            // 聊天提示：长文案
            String longMsg = TrueuuidConfig.offlineFallbackMessage();
            if (longMsg == null || longMsg.isEmpty()) {
                longMsg = "注意：你当前以离线模式进入服务器；如果你是正版账号，可能是网络原因导致无法成功鉴权，请重新登陆重试。";
            }
            sp.sendSystemMessage(Component.literal(longMsg).withStyle(ChatFormatting.RED));

            // Title：红色“离线模式”，副标题短文案（黄色）
            var title = Component.literal("离线模式").withStyle(ChatFormatting.RED);
            String shortSubtitle = TrueuuidConfig.offlineShortSubtitle();
            var subtitle = Component.literal(clamp(shortSubtitle, SUBTITLE_MAX_CHARS)).withStyle(ChatFormatting.YELLOW);

            sendTitleNextTick(server, sp, title, subtitle, "OFFLINE");
        } else if (successOpt.isPresent() && successOpt.get().source() == AuthState.AuthSource.YGGDRASIL) {
            AuthState.AuthSuccess success = successOpt.get();
            var title = Component.literal("皮肤站登录").withStyle(ChatFormatting.AQUA);
            String sourceName = success.displayName();
            var subtitle = Component.literal(clamp("已通过 " + sourceName + " 校验", SUBTITLE_MAX_CHARS)).withStyle(ChatFormatting.GREEN);

            sendTitleNextTick(server, sp, title, subtitle, "YGGDRASIL:" + sourceName);
        } else if (successOpt.isPresent()) {
            // 正版模式：绿色标题，副标题短文案（灰色）
            var title = Component.literal("正版模式").withStyle(ChatFormatting.GREEN);
            String shortSubtitle = TrueuuidConfig.onlineShortSubtitle();
            var subtitle = Component.literal(clamp(shortSubtitle, SUBTITLE_MAX_CHARS)).withStyle(ChatFormatting.GRAY);

            String mode = "MOJANG:" + successOpt.get().displayName();
            sendTitleNextTick(server, sp, title, subtitle, mode);
        } else if (!server.isDedicatedServer()) {
            var title = Component.literal("单人模式").withStyle(ChatFormatting.GOLD);
            var subtitle = Component.literal("未进行服务器鉴权").withStyle(ChatFormatting.GRAY);

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
    public static void onServerTick(ServerTickEvent.Post event) {
        if (PENDING_LOCAL_SELF_REFRESH.isEmpty()) return;
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
        if (sp.connection.getConnection().getRemoteAddress() instanceof InetSocketAddress isa) {
            return isa.getAddress().getHostAddress();
        }
        return null;
    }

    private static boolean isIntegratedLocalPlayer(net.minecraft.server.MinecraftServer server, ServerPlayer sp) {
        if (server == null || server.isDedicatedServer() || sp == null) return false;
        return !(sp.connection.getConnection().getRemoteAddress() instanceof InetSocketAddress);
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

    private static String clamp(String s, int max) {
        if (s == null) return "";
        if (s.length() <= max) return s;
        return s.substring(0, Math.max(0, max - 1)) + "…";
    }
}
