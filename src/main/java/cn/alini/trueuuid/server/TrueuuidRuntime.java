package cn.alini.trueuuid.server;

public final class TrueuuidRuntime {
    private static volatile boolean INIT = false;
    public static NameRegistry NAME_REGISTRY;
    public static RecentIpGraceCache IP_GRACE;

    public static void init() {
        if (INIT) return;
        synchronized (TrueuuidRuntime.class) {
            if (INIT) return;
            NAME_REGISTRY = new NameRegistry();
            IP_GRACE = new RecentIpGraceCache();
            INIT = true;
        }
    }

    private TrueuuidRuntime() {}

}