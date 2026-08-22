package cn.alini.trueuuid.server;

import java.util.Locale;
import java.util.Objects;

/** Plain-Java Minecraft name validation shared by every server adapter. */
public final class MinecraftNames {
    private static final String CANONICAL_PATTERN = "[A-Za-z0-9_]+";
    private static final String EFFECTIVE_ALIAS_PATTERN = "[.+-][A-Za-z0-9_]+";

    /** A client-requested or authority-returned Java username. */
    public static String requireValid(String value) {
        Objects.requireNonNull(value, "name");
        if (value.isBlank() || value.length() > 16 || !value.matches(CANONICAL_PATTERN)) {
            throw new IllegalArgumentException("invalid Minecraft name");
        }
        return value;
    }

    /**
     * A final server-owned profile name. Punctuation is allowed only as one
     * leading namespace marker; client-requested and premium names stay strict.
     */
    public static String requireValidEffective(String value) {
        Objects.requireNonNull(value, "effective name");
        if (value.isBlank() || value.length() > 16
                || !(value.matches(CANONICAL_PATTERN) || value.matches(EFFECTIVE_ALIAS_PATTERN))) {
            throw new IllegalArgumentException("invalid effective Minecraft name");
        }
        return value;
    }

    public static String normalize(String value) {
        return requireValid(value).toLowerCase(Locale.ROOT);
    }

    public static String normalizeEffective(String value) {
        return requireValidEffective(value).toLowerCase(Locale.ROOT);
    }

    public static boolean isCanonical(String value) {
        return value != null && !value.isBlank() && value.length() <= 16
                && value.matches(CANONICAL_PATTERN);
    }

    private MinecraftNames() {
    }
}
