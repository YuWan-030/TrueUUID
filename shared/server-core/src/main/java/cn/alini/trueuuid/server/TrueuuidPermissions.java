package cn.alini.trueuuid.server;

import java.util.Set;

/** Stable permission names shared by every command adapter. */
public final class TrueuuidPermissions {
    public static final String STATUS_OTHER = "trueuuid.command.status.other";
    public static final String HEALTH = "trueuuid.command.health";
    public static final String POLICY = "trueuuid.command.policy";
    public static final String IDENTITY_INSPECT = "trueuuid.command.identity.inspect";
    public static final String IDENTITY_ALIAS = "trueuuid.command.identity.alias";
    public static final String IDENTITY_COLLISION = "trueuuid.command.identity.collision";
    public static final String IDENTITY_BLOCK = "trueuuid.command.identity.block";
    public static final String IDENTITY_RELEASE = "trueuuid.command.identity.release";
    public static final String MIGRATE = "trueuuid.command.migrate";
    public static final String CLEANUP = "trueuuid.command.cleanup";
    public static final String RELOAD = "trueuuid.command.reload";
    public static final String NOTIFY = "trueuuid.notify";

    public static final Set<String> PROTECTED = Set.of(STATUS_OTHER, HEALTH, POLICY,
            IDENTITY_INSPECT, IDENTITY_ALIAS, IDENTITY_COLLISION, IDENTITY_BLOCK, IDENTITY_RELEASE,
            MIGRATE, CLEANUP, RELOAD, NOTIFY);

    private TrueuuidPermissions() {
    }
}
