package cn.alini.trueuuid.fabric.login;

import net.minecraft.util.Identifier;

/** Identifier construction before the constructor became private. */
final class FabricIdentifiers {
    static Identifier create(String namespace, String path) {
        return new Identifier(namespace, path);
    }

    private FabricIdentifiers() {}
}
