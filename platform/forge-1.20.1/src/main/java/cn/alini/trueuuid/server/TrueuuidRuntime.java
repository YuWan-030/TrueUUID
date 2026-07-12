package cn.alini.trueuuid.server;

public final class TrueuuidRuntime {
    private static volatile boolean INIT = false;
    public static NameRegistry NAME_REGISTRY;
    public static RecentIpGraceCache IP_GRACE;
    public static AuthState AUTH_STATE;
    public static AuthRequestCoordinator AUTH_REQUESTS;
    public static MigrationCoordinator MIGRATIONS;
    public static MigrationLockRegistry MIGRATION_LOCKS;

    public static void init() {
        if (INIT) return;
        synchronized (TrueuuidRuntime.class) {
            if (INIT) return;
            NAME_REGISTRY = new NameRegistry();
            IP_GRACE = new RecentIpGraceCache();
            AUTH_STATE = new AuthState();
            AUTH_REQUESTS = new AuthRequestCoordinator();
            MIGRATIONS = new MigrationCoordinator();
            MIGRATION_LOCKS = new MigrationLockRegistry();
            INIT = true;
        }
    }

    public static synchronized void shutdown() {
        if (!INIT) return;
        AUTH_REQUESTS.close();
        MIGRATIONS.close();
        MIGRATION_LOCKS.close();
        AUTH_STATE.close();
        IP_GRACE.close();
        NAME_REGISTRY.close();
        INIT = false;
    }

    private TrueuuidRuntime() {}

}
