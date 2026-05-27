package cn.alini.trueuuid.command;

import cn.alini.trueuuid.Trueuuid;
import cn.alini.trueuuid.config.TrueuuidConfig;
import cn.alini.trueuuid.server.PlayerDataMigration;
import cn.alini.trueuuid.server.TrueuuidRuntime;
import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

@EventBusSubscriber(modid = Trueuuid.MODID)
public class TrueuuidCommands {

    @SubscribeEvent
    public static void onRegister(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("trueuuid")
                .requires(src -> src.hasPermission(3))
                .then(Commands.literal("config")
                        .requires(src -> src.hasPermission(3))
                        .then(Commands.literal("nomojang")
                                .then(Commands.literal("status")
                                        .executes(ctx -> cmdNomojangStatus(ctx.getSource())))
                                .then(Commands.literal("on")
                                        .executes(ctx -> cmdNomojangSet(ctx.getSource(), true)))
                                .then(Commands.literal("off")
                                        .executes(ctx -> cmdNomojangSet(ctx.getSource(), false)))
                                .then(Commands.literal("toggle")
                                        .executes(ctx -> cmdNomojangToggle(ctx.getSource())))))
                .then(Commands.literal("mojang")
                        .then(Commands.literal("status")
                                .executes(ctx -> mojangStatus(ctx.getSource()))))
                .then(Commands.literal("cleanupuuid")
                        .requires(src -> src.hasPermission(4))
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(ctx -> cleanupUuid(ctx.getSource(), StringArgumentType.getString(ctx, "name")))))
                .then(Commands.literal("migrateuuid")
                        .requires(src -> src.hasPermission(4))
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(ctx -> migrateUuid(ctx.getSource(), StringArgumentType.getString(ctx, "name")))))
                .then(Commands.literal("reload")
                        .executes(ctx -> cmdConfigReload(ctx.getSource()))));
    }

    private static int cmdConfigReload(CommandSourceStack src) {
        try {
            Path cfgPath = FMLPaths.CONFIGDIR.get().resolve("trueuuid-common.toml");
            CommentedFileConfig cfg = CommentedFileConfig.builder(cfgPath)
                    .sync()
                    .autosave()
                    .build();
            cfg.load();

            java.util.function.BiFunction<String, String, Object> getVal = (authKey, altKey) -> {
                if (cfg.contains(authKey)) return cfg.get(authKey);
                if (altKey != null && cfg.contains(altKey)) return cfg.get(altKey);
                return null;
            };

            Object v;
            v = getVal.apply("auth.nomojang.enabled", "nomojang.enabled");
            if (v instanceof Boolean value) TrueuuidConfig.COMMON.nomojangEnabled.set(value);

            v = getVal.apply("auth.debug", "debug");
            if (v instanceof Boolean value) TrueuuidConfig.COMMON.debug.set(value);

            v = getVal.apply("auth.recentIpGrace.enabled", "recentIpGrace.enabled");
            if (v instanceof Boolean value) TrueuuidConfig.COMMON.recentIpGraceEnabled.set(value);

            v = getVal.apply("auth.knownPremiumDenyOffline", "knownPremiumDenyOffline");
            if (v instanceof Boolean value) TrueuuidConfig.COMMON.knownPremiumDenyOffline.set(value);

            v = getVal.apply("auth.allowOfflineForUnknownOnly", "allowOfflineForUnknownOnly");
            if (v instanceof Boolean value) TrueuuidConfig.COMMON.allowOfflineForUnknownOnly.set(value);

            v = getVal.apply("auth.allowOfflineOnTimeout", "allowOfflineOnTimeout");
            if (v instanceof Boolean value) TrueuuidConfig.COMMON.allowOfflineOnTimeout.set(value);

            v = getVal.apply("auth.allowOfflineOnFailure", "allowOfflineOnFailure");
            if (v instanceof Boolean value) TrueuuidConfig.COMMON.allowOfflineOnFailure.set(value);

            v = getVal.apply("auth.timeoutMs", "timeoutMs");
            if (v instanceof Number value) TrueuuidConfig.COMMON.timeoutMs.set(value.longValue());

            v = getVal.apply("auth.recentIpGrace.ttlSeconds", "recentIpGrace.ttlSeconds");
            if (v instanceof Number value) TrueuuidConfig.COMMON.recentIpGraceTtlSeconds.set(value.intValue());

            v = getVal.apply("auth.timeoutKickMessage", "timeoutKickMessage");
            if (v != null) TrueuuidConfig.COMMON.timeoutKickMessage.set(String.valueOf(v));

            v = getVal.apply("auth.offlineFallbackMessage", "offlineFallbackMessage");
            if (v != null) TrueuuidConfig.COMMON.offlineFallbackMessage.set(String.valueOf(v));

            v = getVal.apply("auth.offlineShortSubtitle", "offlineShortSubtitle");
            if (v != null) TrueuuidConfig.COMMON.offlineShortSubtitle.set(String.valueOf(v));

            v = getVal.apply("auth.onlineShortSubtitle", "onlineShortSubtitle");
            if (v != null) TrueuuidConfig.COMMON.onlineShortSubtitle.set(String.valueOf(v));

            v = getVal.apply("auth.mojangReverseProxy", "mojangReverseProxy");
            if (v != null) TrueuuidConfig.COMMON.mojangReverseProxy.set(String.valueOf(v));

            src.sendSuccess(() -> Component.literal("[TrueUUID] 配置已从磁盘重载").withStyle(ChatFormatting.GREEN), false);
            return 1;
        } catch (Exception ex) {
            src.sendFailure(Component.literal("[TrueUUID] 重载配置失败: " + ex.getMessage()).withStyle(ChatFormatting.RED));
            return 0;
        }
    }

    private static int cmdNomojangStatus(CommandSourceStack src) {
        boolean enabled = TrueuuidConfig.nomojangEnabled();
        src.sendSuccess(() -> Component.literal("[TrueUUID] NoMojang: " + (enabled ? "已启用" : "已禁用"))
                .withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.RED), false);
        return 1;
    }

    private static int cmdNomojangSet(CommandSourceStack src, boolean value) {
        try {
            TrueuuidConfig.COMMON.nomojangEnabled.set(value);
            src.sendSuccess(() -> Component.literal("[TrueUUID] NoMojang 已" + (value ? "启用" : "禁用"))
                    .withStyle(value ? ChatFormatting.GREEN : ChatFormatting.RED), false);
            return 1;
        } catch (Throwable t) {
            src.sendFailure(Component.literal("[TrueUUID] 无法设置 NoMojang: " + t.getMessage()).withStyle(ChatFormatting.RED));
            return 0;
        }
    }

    private static int cmdNomojangToggle(CommandSourceStack src) {
        return cmdNomojangSet(src, !TrueuuidConfig.nomojangEnabled());
    }

    private static int mojangStatus(CommandSourceStack src) {
        try {
            String testUrl = TrueuuidConfig.COMMON.mojangReverseProxy.get() + "/session/minecraft/hasJoined?username=Mojang&serverId=test";
            java.net.URL url = new java.net.URL(testUrl);
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);
            conn.connect();

            int responseCode = conn.getResponseCode();
            if (responseCode == 200 || responseCode == 204 || responseCode == 403) {
                src.sendSuccess(() -> Component.literal("[TrueUUID] Mojang 会话服务器可访问，响应码: " + responseCode)
                        .withStyle(ChatFormatting.GREEN), false);
            } else {
                src.sendFailure(Component.literal("[TrueUUID] Mojang 会话服务器响应异常，响应码: " + responseCode)
                        .withStyle(ChatFormatting.RED));
            }
            return 1;
        } catch (Exception e) {
            src.sendFailure(Component.literal("[TrueUUID] 无法连接到 Mojang 会话服务器: " + e.getMessage())
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
    }

    private static int cleanupUuid(CommandSourceStack src, String name) {
        try {
            PlayerDataMigration.CleanupResult result = PlayerDataMigration.cleanupOfflineData(src.getServer(), name);
            if (result == null) {
                src.sendFailure(Component.literal("[TrueUUID] 未找到 " + name + " 的重复离线 UUID 数据。"));
                return 0;
            }

            src.sendSuccess(() -> Component.literal(
                    "[TrueUUID] 已清理 " + name + " 的重复离线 UUID 数据。"
                            + "\n离线 UUID: " + result.offlineUuid()
                            + "\n发现数据: " + result.summary()
                            + "\n移动文件数: " + result.cleanedFiles()
                            + (result.cleanedGlobalRefs() ? "\n已清理全局 UUID 引用。" : "")
                            + "\n备份目录: " + result.backupDir()), false);
            return 1;
        } catch (Exception ex) {
            src.sendFailure(Component.literal("[TrueUUID] 清理重复 UUID 失败: " + ex.getMessage()));
            ex.printStackTrace();
            return 0;
        }
    }

    private static int migrateUuid(CommandSourceStack src, String name) {
        try {
            Optional<UUID> verifiedUuid = TrueuuidRuntime.NAME_REGISTRY.getPremiumUuid(name);
            if (verifiedUuid.isEmpty()) {
                src.sendFailure(Component.literal("[TrueUUID] 未找到 " + name + " 的正版/皮肤站 UUID 绑定记录。请先让玩家成功完成一次验证登录。"));
                return 0;
            }

            PlayerDataMigration.OfflineData offlineData = PlayerDataMigration.findOfflineData(src.getServer(), name);
            if (offlineData == null) {
                src.sendFailure(Component.literal("[TrueUUID] 未找到 " + name + " 的同名离线 UUID 数据。"));
                return 0;
            }

            PlayerDataMigration.migrateOfflineToVerified(src.getServer(), name, verifiedUuid.get());
            src.sendSuccess(() -> Component.literal(
                    "[TrueUUID] 已将 " + name + " 的离线 UUID 数据继承到正版/皮肤站 UUID。"
                            + "\n离线 UUID: " + offlineData.offlineUuid()
                            + "\n目标 UUID: " + verifiedUuid.get()
                            + "\n继承数据: " + offlineData.summary()
                            + "\n已自动备份，备份目录位于存档 trueuuid-backups/offline-upgrades 下。"), false);
            return 1;
        } catch (Exception ex) {
            src.sendFailure(Component.literal("[TrueUUID] 继承离线 UUID 数据失败: " + ex.getMessage()));
            ex.printStackTrace();
            return 0;
        }
    }
}
