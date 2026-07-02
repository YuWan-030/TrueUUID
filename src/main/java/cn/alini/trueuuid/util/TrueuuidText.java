package cn.alini.trueuuid.util;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

public final class TrueuuidText {
    private static final String TRANSLATION_PREFIX = "trueuuid.";

    public static MutableComponent configComponent(String configured, String fallbackKey) {
        if (configured == null || configured.isBlank()) {
            return Component.translatable(fallbackKey);
        }
        String trimmed = configured.trim();
        if (trimmed.startsWith(TRANSLATION_PREFIX) && !trimmed.contains("\n")) {
            return Component.translatable(trimmed);
        }
        return Component.literal(configured);
    }

    private TrueuuidText() {
    }
}
