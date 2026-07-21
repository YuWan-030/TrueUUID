package cn.alini.trueuuid.net;

import java.util.Objects;
import net.minecraft.resources.Identifier;

public final class ForgeNetIds {
    // tryParse is the only Identifier factory present across the whole
    // modern-Forge range (javap-verified on the mapped 1.20.2, 1.21.1 and
    // 1.21.8 jars, 2026-07-16): the two-argument constructor is public on
    // 1.20.x but private on 1.21.x, and fromNamespaceAndPath only exists on
    // 1.21.x. The input is a constant, so the null branch is unreachable.
    public static final Identifier AUTH =
            Objects.requireNonNull(Identifier.tryParse("trueuuid:auth"));
    private ForgeNetIds() {}
}
