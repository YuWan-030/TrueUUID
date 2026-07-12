package cn.alini.trueuuid.command;

import cn.alini.trueuuid.Trueuuid;
import cn.alini.trueuuid.config.TrueuuidConfig;
import cn.alini.trueuuid.server.PlayerDataMigration;
import cn.alini.trueuuid.server.SessionCheck;
import cn.alini.trueuuid.server.TrueuuidRuntime;
import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = Trueuuid.MODID)
public class TrueuuidCommands {

    @SubscribeEvent
    public static void onRegister(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("trueuuid")
                .requires(src -> src.hasPermission(3))
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
        TrueuuidRuntime.MIGRATIONS.io(() -> { reloadConfig(); return null; })
                .whenComplete((ignored, failure) -> src.getServer().execute(() -> {
                    if (failure == null) {
                        src.sendSuccess(() -> Component.translatable("trueuuid.command.reload.success")
                                .withStyle(ChatFormatting.GREEN), false);
                    } else {
                        src.sendFailure(Component.translatable("trueuuid.command.reload.failure", Component.translatable("trueuuid.error.internal"))
                                .withStyle(ChatFormatting.RED));
                    }
                }));
        return 1;
    }

    private static void reloadConfig() throws Exception {
            Path cfgPath = FMLPaths.CONFIGDIR.get().resolve("trueuuid-common.toml");
            try (CommentedFileConfig cfg = CommentedFileConfig.builder(cfgPath)
                    .sync()
                    .autosave()
                    .build()) {
            cfg.load();

            java.util.function.BiFunction<String, String, Object> getVal = (authKey, altKey) -> {
                if (cfg.contains(authKey)) return cfg.get(authKey);
                if (altKey != null && cfg.contains(altKey)) return cfg.get(altKey);
                return null;
            };

            Object v;
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

            v = getVal.apply("auth.showJoinFeedback", "showJoinFeedback");
            if (v instanceof Boolean value) TrueuuidConfig.COMMON.showJoinFeedback.set(value);
        }
    }

    private static int mojangStatus(CommandSourceStack src) {
        SessionCheck.probeMojangAsync().whenComplete((responseCode, failure) -> src.getServer().execute(() -> {
            if (failure != null) {
                src.sendFailure(Component.translatable("trueuuid.command.mojang.connect_failed", Component.translatable("trueuuid.error.internal"))
                        .withStyle(ChatFormatting.RED));
            } else if (responseCode == 200 || responseCode == 204 || responseCode == 403) {
                src.sendSuccess(() -> Component.translatable("trueuuid.command.mojang.reachable", responseCode)
                        .withStyle(ChatFormatting.GREEN), false);
            } else {
                src.sendFailure(Component.translatable("trueuuid.command.mojang.unexpected", responseCode)
                        .withStyle(ChatFormatting.RED));
            }
        }));
        return 1;
    }

    private static int cleanupUuid(CommandSourceStack src, String name) {
        TrueuuidRuntime.MIGRATIONS.cleanup(src.getServer(), name).whenComplete((result, failure) -> src.getServer().execute(() -> {
            if (failure != null) {
                src.sendFailure(Component.translatable("trueuuid.command.cleanup.failure", Component.translatable("trueuuid.error.internal")));
                return;
            }
            if (result == null) {
                src.sendFailure(Component.translatable("trueuuid.command.cleanup.not_found", name));
                return;
            }

            Component message = Component.translatable(
                    "trueuuid.command.cleanup.success",
                    name,
                    result.offlineUuid(),
                    result.summary(),
                    result.cleanedFiles(),
                    result.backupDir()
            );
            if (result.cleanedGlobalRefs()) {
                message = message.copy().append(Component.translatable("trueuuid.command.cleanup.global_refs"));
            }
            final Component finalMessage = message;
            src.sendSuccess(() -> finalMessage, false);
        }));
        return 1;
    }

    private static int migrateUuid(CommandSourceStack src, String name) {
        Optional<UUID> verifiedUuid = TrueuuidRuntime.NAME_REGISTRY.getPremiumUuid(name);
        if (verifiedUuid.isEmpty()) {
            src.sendFailure(Component.translatable("trueuuid.command.migrate.no_verified", name));
            return 0;
        }
        TrueuuidRuntime.MIGRATIONS.find(src.getServer(), name).thenCompose(offlineData -> {
            if (offlineData == null) return java.util.concurrent.CompletableFuture.completedFuture(null);
            return TrueuuidRuntime.MIGRATIONS.migrate(src.getServer(), name, verifiedUuid.get()).thenApply(ignored -> offlineData);
        }).whenComplete((offlineData, failure) -> src.getServer().execute(() -> {
            if (failure != null) {
                src.sendFailure(Component.translatable("trueuuid.command.migrate.failure", Component.translatable("trueuuid.error.internal")));
            } else if (offlineData == null) {
                src.sendFailure(Component.translatable("trueuuid.command.migrate.no_offline", name));
            } else {
                src.sendSuccess(() -> Component.translatable(
                    "trueuuid.command.migrate.success",
                    name,
                    offlineData.offlineUuid(),
                    verifiedUuid.get(),
                    offlineData.summary()
                ), false);
            }
        }));
        return 1;
    }

}
