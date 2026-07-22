package cn.alini.trueuuid.fabric.command;

import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;

import java.util.function.Predicate;

/** Named permission checks introduced by Minecraft 1.21.11. */
final class FabricCommandPermissions {
    static Predicate<ServerCommandSource> require(int level) {
        return switch (level) {
            case 3 -> CommandManager.requirePermissionLevel(CommandManager.ADMINS_CHECK)::test;
            case 4 -> CommandManager.requirePermissionLevel(CommandManager.OWNERS_CHECK)::test;
            default -> throw new IllegalArgumentException("unsupported command permission level: " + level);
        };
    }

    private FabricCommandPermissions() {}
}
