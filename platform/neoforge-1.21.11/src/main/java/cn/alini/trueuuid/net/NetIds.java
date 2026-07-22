package cn.alini.trueuuid.net;

import net.minecraft.resources.Identifier;

public final class NetIds {
    // 1.21.11-era copy: Mojang's official mappings renamed ResourceLocation
    // to Identifier in 1.21.11 (the final legacy-scheme release), so this and
    // the other Identifier-touching files are module-local here. tryParse
    // carries over unchanged; the input is a constant, so the null branch is
    // unreachable.
    public static final Identifier AUTH =
            java.util.Objects.requireNonNull(Identifier.tryParse("trueuuid:auth"));
    public static final String MIGRATION_CONFIRM_SERVER_ID = "trueuuid:migration-confirm";
    private NetIds() {}
}
