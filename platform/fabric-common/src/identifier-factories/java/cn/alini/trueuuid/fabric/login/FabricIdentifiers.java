package cn.alini.trueuuid.fabric.login;

import net.minecraft.util.Identifier;

/** Identifier factory used after the two-argument constructor became private. */
final class FabricIdentifiers {
    static Identifier create(String namespace, String path) {
        return Identifier.of(namespace, path);
    }

    private FabricIdentifiers() {}
}
