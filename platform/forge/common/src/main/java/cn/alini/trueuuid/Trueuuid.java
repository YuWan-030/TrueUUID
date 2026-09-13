package cn.alini.trueuuid;

import cn.alini.trueuuid.protocol.AcceptanceHooks;
import cn.alini.trueuuid.presentation.BrandText;
import cn.alini.trueuuid.presentation.TerminalBanner;
import cn.alini.trueuuid.server.ForgeAdapterRuntime;
import cn.alini.trueuuid.config.TrueuuidConfig;
import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

/**
 * Shared Forge entrypoint for the modern (1.20.2+ payload-based) login protocol.
 * This source lives in {@code platform/forge/common} and is compiled by every
 * per-version Forge module against its own Forge/mappings; it does not depend on
 * NeoForge APIs. Only version-divergent shims (mixins, payloads, client render)
 * live in each module's own source tree.
 */
@Mod(Trueuuid.MODID)
public final class Trueuuid {
    public static final String MODID = "trueuuid";
    public static final Logger LOGGER = LogUtils.getLogger();

    /** Logs at INFO only while the auth.debug config toggle is on, matching 1.20.1. */
    public static void debug(String message, Object... args) {
        if (TrueuuidConfig.debug()) LOGGER.info(message, args);
    }

    public static void acceptance(String message, Object... args) {
        if (AcceptanceHooks.loggingEnabled()) {
            LOGGER.info("TRUEUUID_ACCEPTANCE " + message, args);
        }
    }

    /** Prefixes a player-facing message with the shared bold gradient wordmark. */
    public static MutableComponent branded(Component message) {
        MutableComponent line = Component.empty();
        for (BrandText.Glyph glyph : BrandText.glyphs()) {
            line.append(Component.literal(glyph.text())
                    .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(glyph.rgb())).withBold(true)));
        }
        return line.append(Component.literal(" » ").withStyle(ChatFormatting.DARK_GRAY)).append(message);
    }

    public Trueuuid() {
        TrueuuidConfig.register();
        ForgeAdapterRuntime.initialize();
        // Game-event registration is the single per-version seam: the
        // @SubscribeEvent annotation package moved between EventBus 6 (Forge 52)
        // and EventBus 7 (Forge 58). Each module supplies its own
        // TrueuuidForgeEvents with the correct import; everything else is shared.
        TrueuuidForgeEvents.register();
        LOGGER.info("TrueUUID Forge adapter loaded");
        TerminalBanner.print("Forge adapter loaded");
    }
}
