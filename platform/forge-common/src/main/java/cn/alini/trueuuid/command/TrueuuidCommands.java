package cn.alini.trueuuid.command;

import cn.alini.trueuuid.config.TrueuuidConfig;
import cn.alini.trueuuid.server.ForgeAdapterRuntime;
import cn.alini.trueuuid.server.PlayerDataMigration;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.fml.loading.FMLPaths;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class TrueuuidCommands {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("trueuuid")
                .requires(source -> hasPermission(source, 3))
                .then(Commands.literal("mojang")
                        .then(Commands.literal("status")
                                .executes(context -> mojangStatus(context.getSource()))))
                .then(Commands.literal("cleanupuuid")
                        .requires(source -> hasPermission(source, 4))
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(context -> cleanupUuid(context.getSource(),
                                        StringArgumentType.getString(context, "name")))))
                .then(Commands.literal("migrateuuid")
                        .requires(source -> hasPermission(source, 4))
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(context -> migrateUuid(context.getSource(),
                                        StringArgumentType.getString(context, "name")))))
                .then(Commands.literal("reload")
                        .executes(context -> reload(context.getSource()))));
    }

    private static int reload(CommandSourceStack source) {
        ForgeAdapterRuntime.migrations().io(() -> {
            TrueuuidConfig.reloadFromDisk(FMLPaths.CONFIGDIR.get().resolve("trueuuid-common.toml"));
            return null;
        }).whenComplete((ignored, failure) -> source.getServer().execute(() -> {
            if (failure == null) {
                source.sendSuccess(() -> Component.translatable("trueuuid.command.reload.success")
                        .withStyle(ChatFormatting.GREEN), false);
            } else {
                source.sendFailure(Component.translatable("trueuuid.command.reload.failure",
                        Component.translatable("trueuuid.error.internal")).withStyle(ChatFormatting.RED));
            }
        }));
        return 1;
    }

    private static int mojangStatus(CommandSourceStack source) {
        ForgeAdapterRuntime.probeMojangAsync().whenComplete((responseCode, failure) -> source.getServer().execute(() -> {
            if (failure != null) {
                source.sendFailure(Component.translatable("trueuuid.command.mojang.connect_failed",
                        Component.translatable("trueuuid.error.internal")).withStyle(ChatFormatting.RED));
            } else if (responseCode == 200 || responseCode == 204 || responseCode == 403) {
                source.sendSuccess(() -> Component.translatable("trueuuid.command.mojang.reachable", responseCode)
                        .withStyle(ChatFormatting.GREEN), false);
            } else {
                source.sendFailure(Component.translatable("trueuuid.command.mojang.unexpected", responseCode)
                        .withStyle(ChatFormatting.RED));
            }
        }));
        return 1;
    }

    private static int cleanupUuid(CommandSourceStack source, String name) {
        ForgeAdapterRuntime.migrations().cleanup(source.getServer(), name)
                .whenComplete((result, failure) -> source.getServer().execute(() -> {
                    if (failure != null) {
                        source.sendFailure(Component.translatable("trueuuid.command.cleanup.failure",
                                Component.translatable("trueuuid.error.internal")));
                        return;
                    }
                    if (result == null) {
                        source.sendFailure(Component.translatable("trueuuid.command.cleanup.not_found", name));
                        return;
                    }
                    Component message = Component.translatable("trueuuid.command.cleanup.success",
                            name, result.offlineUuid(), result.summary(), result.cleanedFiles(), result.backupDir());
                    if (result.cleanedGlobalRefs()) {
                        message = message.copy().append(Component.translatable("trueuuid.command.cleanup.global_refs"));
                    }
                    Component finalMessage = message;
                    source.sendSuccess(() -> finalMessage, false);
                }));
        return 1;
    }

    private static int migrateUuid(CommandSourceStack source, String name) {
        Optional<UUID> verifiedUuid = ForgeAdapterRuntime.premiumUuidOf(name);
        if (verifiedUuid.isEmpty()) {
            source.sendFailure(Component.translatable("trueuuid.command.migrate.no_verified", name));
            return 0;
        }
        ForgeAdapterRuntime.migrations().find(source.getServer(), name).thenCompose(offlineData -> {
            if (offlineData == null) return java.util.concurrent.CompletableFuture.completedFuture(null);
            return ForgeAdapterRuntime.migrations().migrate(source.getServer(), name, verifiedUuid.get())
                    .thenApply(ignored -> offlineData);
        }).whenComplete((offlineData, failure) -> source.getServer().execute(() -> {
            if (failure != null) {
                source.sendFailure(Component.translatable("trueuuid.command.migrate.failure",
                        Component.translatable("trueuuid.error.internal")));
            } else if (offlineData == null) {
                source.sendFailure(Component.translatable("trueuuid.command.migrate.no_offline", name));
            } else {
                source.sendSuccess(() -> Component.translatable("trueuuid.command.migrate.success",
                        name, offlineData.offlineUuid(), verifiedUuid.get(), offlineData.summary()), false);
            }
        }));
        return 1;
    }

    private static boolean hasPermission(CommandSourceStack source, int level) {
        for (String method : List.of("hasPermission", "hasPermissions")) {
            try {
                Object result = CommandSourceStack.class.getMethod(method, int.class).invoke(source, level);
                return result instanceof Boolean allowed && allowed;
            } catch (NoSuchMethodException ignored) {
                // Try the other mapped era.
            } catch (ReflectiveOperationException ignored) {
                return false;
            }
        }
        return false;
    }

    private TrueuuidCommands() {}
}
