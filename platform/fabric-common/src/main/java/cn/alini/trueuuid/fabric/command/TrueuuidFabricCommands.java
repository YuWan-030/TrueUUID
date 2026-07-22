package cn.alini.trueuuid.fabric.command;

import cn.alini.trueuuid.fabric.config.FabricConfig;
import cn.alini.trueuuid.fabric.login.FabricAdapterRuntime;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Optional;
import java.util.UUID;

public final class TrueuuidFabricCommands {
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("trueuuid")
                .requires(source -> source.hasPermissionLevel(3))
                .then(CommandManager.literal("mojang")
                        .then(CommandManager.literal("status")
                                .executes(context -> mojangStatus(context.getSource()))))
                .then(CommandManager.literal("cleanupuuid")
                        .requires(source -> source.hasPermissionLevel(4))
                        .then(CommandManager.argument("name", StringArgumentType.word())
                                .executes(context -> cleanupUuid(context.getSource(),
                                        StringArgumentType.getString(context, "name")))))
                .then(CommandManager.literal("migrateuuid")
                        .requires(source -> source.hasPermissionLevel(4))
                        .then(CommandManager.argument("name", StringArgumentType.word())
                                .executes(context -> migrateUuid(context.getSource(),
                                        StringArgumentType.getString(context, "name")))))
                .then(CommandManager.literal("reload")
                        .executes(context -> reload(context.getSource()))));
    }

    private static int reload(ServerCommandSource source) {
        FabricAdapterRuntime.migrations().io(() -> {
            FabricConfig.load();
            return null;
        }).whenComplete((ignored, failure) -> source.getServer().execute(() -> {
            if (failure == null) {
                source.sendFeedback(() -> Text.translatable("trueuuid.command.reload.success")
                        .formatted(Formatting.GREEN), false);
            } else {
                source.sendError(Text.translatable("trueuuid.command.reload.failure",
                        Text.translatable("trueuuid.error.internal")).formatted(Formatting.RED));
            }
        }));
        return 1;
    }

    private static int mojangStatus(ServerCommandSource source) {
        FabricAdapterRuntime.probeMojangAsync().whenComplete((responseCode, failure) -> source.getServer().execute(() -> {
            if (failure != null) {
                source.sendError(Text.translatable("trueuuid.command.mojang.connect_failed",
                        Text.translatable("trueuuid.error.internal")).formatted(Formatting.RED));
            } else if (responseCode == 200 || responseCode == 204 || responseCode == 403) {
                source.sendFeedback(() -> Text.translatable("trueuuid.command.mojang.reachable", responseCode)
                        .formatted(Formatting.GREEN), false);
            } else {
                source.sendError(Text.translatable("trueuuid.command.mojang.unexpected", responseCode)
                        .formatted(Formatting.RED));
            }
        }));
        return 1;
    }

    private static int cleanupUuid(ServerCommandSource source, String name) {
        FabricAdapterRuntime.migrations().cleanup(source.getServer(), name)
                .whenComplete((result, failure) -> source.getServer().execute(() -> {
                    if (failure != null) {
                        source.sendError(Text.translatable("trueuuid.command.cleanup.failure",
                                Text.translatable("trueuuid.error.internal")));
                        return;
                    }
                    if (result == null) {
                        source.sendError(Text.translatable("trueuuid.command.cleanup.not_found", name));
                        return;
                    }
                    Text message = Text.translatable("trueuuid.command.cleanup.success",
                            name, result.offlineUuid(), result.summary(), result.cleanedFiles(), result.backupDir());
                    if (result.cleanedGlobalRefs()) {
                        message = message.copy().append(Text.translatable("trueuuid.command.cleanup.global_refs"));
                    }
                    Text finalMessage = message;
                    source.sendFeedback(() -> finalMessage, false);
                }));
        return 1;
    }

    private static int migrateUuid(ServerCommandSource source, String name) {
        Optional<UUID> verifiedUuid = FabricAdapterRuntime.premiumUuidOf(name);
        if (verifiedUuid.isEmpty()) {
            source.sendError(Text.translatable("trueuuid.command.migrate.no_verified", name));
            return 0;
        }
        FabricAdapterRuntime.migrations().find(source.getServer(), name).thenCompose(offlineData -> {
            if (offlineData == null) return java.util.concurrent.CompletableFuture.completedFuture(null);
            return FabricAdapterRuntime.migrations().migrate(source.getServer(), name, verifiedUuid.get())
                    .thenApply(ignored -> offlineData);
        }).whenComplete((offlineData, failure) -> source.getServer().execute(() -> {
            if (failure != null) {
                source.sendError(Text.translatable("trueuuid.command.migrate.failure",
                        Text.translatable("trueuuid.error.internal")));
            } else if (offlineData == null) {
                source.sendError(Text.translatable("trueuuid.command.migrate.no_offline", name));
            } else {
                source.sendFeedback(() -> Text.translatable("trueuuid.command.migrate.success",
                        name, offlineData.offlineUuid(), verifiedUuid.get(), offlineData.summary()), false);
            }
        }));
        return 1;
    }

    private TrueuuidFabricCommands() {}
}
