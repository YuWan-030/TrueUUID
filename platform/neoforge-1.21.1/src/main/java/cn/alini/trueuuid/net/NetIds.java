package cn.alini.trueuuid.net;

import net.minecraft.resources.ResourceLocation;

public final class NetIds {
    // tryParse is the only ResourceLocation factory present across the whole
    // NeoForge range this source compiles on (1.20.2 through 1.21.x):
    // fromNamespaceAndPath only exists from 1.21, and the two-argument
    // constructor is private there. Same finding and fix as the Forge line's
    // ForgeNetIds (2026-07-16). The input is a constant, so the null branch
    // is unreachable.
    public static final ResourceLocation AUTH =
            java.util.Objects.requireNonNull(ResourceLocation.tryParse("trueuuid:auth"));
    private NetIds() {}
}
