package cn.alini.trueuuid.fabric.login;

import java.time.Duration;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * Bounded, expiring login outcomes waiting for vanilla to create a player.
 * Keys and values are plain values only; it never retains a connection,
 * network handler, or player instance.
 */
final class FabricPendingLoginStore {
    static final int MAX_ENTRIES = 4096;
    static final long TTL_MILLIS = Duration.ofMinutes(5).toMillis();

    private final int maximumEntries;
    private final long ttlMillis;
    private final LongSupplier clock;
    private final Map<UUID, Entry> entries = new LinkedHashMap<>();

    FabricPendingLoginStore() {
        this(MAX_ENTRIES, TTL_MILLIS, System::currentTimeMillis);
    }

    FabricPendingLoginStore(int maximumEntries, long ttlMillis, LongSupplier clock) {
        if (maximumEntries < 1 || ttlMillis < 1) throw new IllegalArgumentException("invalid pending-login bounds");
        this.maximumEntries = maximumEntries;
        this.ttlMillis = ttlMillis;
        this.clock = clock;
    }

    synchronized void record(UUID playerId, FabricAuthenticationSource source) {
        if (playerId == null || source == null) return;
        prune(clock.getAsLong());
        while (entries.size() >= maximumEntries && !entries.containsKey(playerId)) {
            Iterator<UUID> iterator = entries.keySet().iterator();
            if (!iterator.hasNext()) break;
            iterator.next();
            iterator.remove();
        }
        entries.put(playerId, new Entry(source, clock.getAsLong()));
    }

    synchronized FabricAuthenticationSource consume(UUID playerId) {
        prune(clock.getAsLong());
        Entry entry = entries.remove(playerId);
        return entry == null ? null : entry.source;
    }

    synchronized void clear() {
        entries.clear();
    }

    synchronized int size() {
        prune(clock.getAsLong());
        return entries.size();
    }

    private void prune(long now) {
        entries.entrySet().removeIf(entry -> now - entry.getValue().createdAt >= ttlMillis);
    }

    private record Entry(FabricAuthenticationSource source, long createdAt) {}
}
