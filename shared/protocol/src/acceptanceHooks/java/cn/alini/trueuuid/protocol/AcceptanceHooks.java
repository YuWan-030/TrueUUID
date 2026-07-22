package cn.alini.trueuuid.protocol;

/** Compiled only for the local installed-JAR acceptance matrix. */
public final class AcceptanceHooks {
    public static boolean loggingEnabled() {
        return enabled("TRUEUUID_ACCEPTANCE_LOG");
    }

    public static boolean autoConfirmMigration() {
        return enabled("TRUEUUID_TEST_AUTO_CONFIRM_MIGRATION");
    }

    private static boolean enabled(String name) {
        String value = System.getenv(name);
        return "1".equals(value) || "true".equalsIgnoreCase(value);
    }

    private AcceptanceHooks() {}
}
