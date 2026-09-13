package cn.alini.trueuuid.fabric;

import cn.alini.trueuuid.fabric.config.FabricConfig;
import cn.alini.trueuuid.fabric.command.TrueuuidFabricCommands;
import cn.alini.trueuuid.fabric.login.FabricLoginNetworking;
import cn.alini.trueuuid.presentation.BrandText;
import cn.alini.trueuuid.presentation.TerminalBanner;
import cn.alini.trueuuid.protocol.AcceptanceHooks;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Common Fabric entrypoint, shared from {@code platform/fabric/common}.
 * {@code FabricLoginNetworking} is the per-version seam it wires up, like
 * forge-common's {@code TrueuuidForgeEvents}.
 */
public final class TrueuuidFabric implements ModInitializer {
    public static final String MOD_ID = "trueuuid";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    /** Logs at INFO only while the auth.debug config toggle is on, matching 1.20.1. */
    public static void debug(String message, Object... args) {
        if (cn.alini.trueuuid.fabric.config.FabricConfig.debug()) LOGGER.info(message, args);
    }

    public static void acceptance(String message, Object... args) {
        if (AcceptanceHooks.loggingEnabled()) {
            LOGGER.info("TRUEUUID_ACCEPTANCE " + message, args);
        }
    }

    /** Prefixes a player-facing message with the shared bold gradient wordmark. */
    public static MutableText branded(Text message) {
        MutableText line = Text.empty();
        for (BrandText.Glyph glyph : BrandText.glyphs()) {
            line.append(Text.literal(glyph.text())
                    .setStyle(Style.EMPTY.withColor(TextColor.fromRgb(glyph.rgb())).withBold(true)));
        }
        return line.append(Text.literal(" » ").formatted(Formatting.DARK_GRAY)).append(message);
    }

    @Override
    public void onInitialize() {
        FabricConfig.load();
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                TrueuuidFabricCommands.register(dispatcher));
        FabricLoginNetworking.registerServerHooks();
        LOGGER.info("TrueUUID Fabric adapter loaded");
        TerminalBanner.print("Fabric adapter loaded");
    }
}
