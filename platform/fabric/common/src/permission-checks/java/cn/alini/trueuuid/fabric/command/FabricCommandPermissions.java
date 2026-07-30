package cn.alini.trueuuid.fabric.command;

import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;

import java.util.function.Predicate;

/** Named permission checks introduced by Minecraft 1.21.11. */
public final class FabricCommandPermissions {
    static Predicate<ServerCommandSource> require(int level) {
        return switch (level) {
            case 2 -> CommandManager.requirePermissionLevel(CommandManager.GAMEMASTERS_CHECK)::test;
            case 3 -> CommandManager.requirePermissionLevel(CommandManager.ADMINS_CHECK)::test;
            case 4 -> CommandManager.requirePermissionLevel(CommandManager.OWNERS_CHECK)::test;
            default -> throw new IllegalArgumentException("unsupported command permission level: " + level);
        };
    }

    public static boolean isOperator(net.minecraft.server.network.ServerPlayerEntity player) {
        return require(2).test(player.getCommandSource());
    }

    private FabricCommandPermissions() {}
}
