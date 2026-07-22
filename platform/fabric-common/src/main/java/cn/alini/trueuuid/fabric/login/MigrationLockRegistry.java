package cn.alini.trueuuid.fabric.login;

import cn.alini.trueuuid.protocol.ExpiringBoundedStore;

import java.time.Duration;
import java.util.Locale;

public final class MigrationLockRegistry implements AutoCloseable {
    private final ExpiringBoundedStore<String, Boolean> locks =
            new ExpiringBoundedStore<>(1024, Duration.ofSeconds(30));

    public void mark(String name) { if (valid(name)) locks.put(key(name), Boolean.TRUE); }
    public void clear(String name) { if (valid(name)) locks.remove(key(name)); }
    public boolean contains(String name) { return valid(name) && locks.get(key(name)).isPresent(); }
    private static boolean valid(String name) { return name != null && !name.isBlank(); }
    private static String key(String name) { return name.toLowerCase(Locale.ROOT); }
    @Override public void close() { locks.close(); }
}
