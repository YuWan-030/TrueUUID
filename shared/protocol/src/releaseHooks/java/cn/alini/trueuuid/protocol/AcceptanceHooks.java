package cn.alini.trueuuid.protocol;

/** Production implementation: matrix-only hooks are absent and always disabled. */
public final class AcceptanceHooks {
    public static boolean loggingEnabled() {
        return false;
    }

    public static boolean autoConfirmMigration() {
        return false;
    }

    private AcceptanceHooks() {}
}
