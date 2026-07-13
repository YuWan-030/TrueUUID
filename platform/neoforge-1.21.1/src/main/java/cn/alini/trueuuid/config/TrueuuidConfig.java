package cn.alini.trueuuid.config;

import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

/** Loader config intentionally exposes host names only; EndpointPolicy validates the URL and DNS. */
public final class TrueuuidConfig {
    private static final ModConfigSpec SPEC;
    private static final ModConfigSpec.ConfigValue<List<? extends String>> YGGDRASIL_HOSTS;
    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("auth");
        YGGDRASIL_HOSTS = builder.comment("Allowed Yggdrasil hasJoined hosts. Empty rejects every client-provided endpoint.")
                .defineListAllowEmpty(List.of("yggdrasil", "apiRootWhitelist"), List::of, value -> value instanceof String);
        builder.pop();
        SPEC = builder.build();
    }
    public static void register() { ModLoadingContext.get().getActiveContainer().registerConfig(ModConfig.Type.COMMON, SPEC); }
    @SuppressWarnings("unchecked") public static List<String> yggdrasilHosts() { return (List<String>) YGGDRASIL_HOSTS.get(); }
    private TrueuuidConfig() {}
}
