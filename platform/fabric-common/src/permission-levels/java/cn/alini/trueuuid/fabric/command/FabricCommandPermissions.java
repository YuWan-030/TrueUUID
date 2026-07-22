package cn.alini.trueuuid.fabric.command;

import net.minecraft.server.command.ServerCommandSource;

import java.util.function.Predicate;

/** Numeric command permission checks used through the 1.20 API era. */
final class FabricCommandPermissions {
    static Predicate<ServerCommandSource> require(int level) {
        return source -> source.hasPermissionLevel(level);
    }

    private FabricCommandPermissions() {}
}
