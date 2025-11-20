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

import java.util.List;

/**
 * 登录后刷新外观，并在“离线放行”时提示玩家；同时显示屏幕标题提示当前模式。
 */
@EventBusSubscriber(modid = Trueuuid.MODID)
public class SkinRefreshHandler {
    private static final int SUBTITLE_MAX_CHARS = 64; // 保护：过长截断，避免越界

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        var server = sp.getServer();
        if (server == null) return;

        // 1) 登录后一帧刷新外观（强制客户端重拉皮肤）
        server.execute(() -> {
            var list = server.getPlayerList();
            list.broadcastAll(new ClientboundPlayerInfoRemovePacket(List.of(sp.getUUID())));
            list.broadcastAll(ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(List.of(sp)));
        });

        // 2) 判断是否离线放行，并发送聊天提示 + 屏幕标题（副标题使用短文案）
        var netConn = sp.connection.getConnection(); // ServerGamePacketListenerImpl.connection
        var fallbackOpt = AuthState.consume(netConn);

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

            sp.connection.send(new ClientboundSetTitlesAnimationPacket(10, 60, 10));
            sp.connection.send(new ClientboundSetTitleTextPacket(title));
            sp.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
        } else {
            // 正版模式：绿色标题，副标题短文案（灰色）
            var title = Component.literal("正版模式").withStyle(ChatFormatting.GREEN);
            String shortSubtitle = TrueuuidConfig.onlineShortSubtitle();
            var subtitle = Component.literal(clamp(shortSubtitle, SUBTITLE_MAX_CHARS)).withStyle(ChatFormatting.GRAY);

            sp.connection.send(new ClientboundSetTitlesAnimationPacket(10, 60, 10));
            sp.connection.send(new ClientboundSetTitleTextPacket(title));
            sp.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
        }
    }

    private static String clamp(String s, int max) {
        if (s == null) return "";
        if (s.length() <= max) return s;
        return s.substring(0, Math.max(0, max - 1)) + "…";
    }
}