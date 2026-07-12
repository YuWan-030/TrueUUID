package cn.alini.trueuuid.protocol;

import java.time.Clock;
import java.time.Duration;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class ExpiringBoundedStore<K, V> implements AutoCloseable {
    private record Entry<V>(V value, long expiresAt) {}

    private final int capacity;
    private final long ttlMillis;
    private final Clock clock;
    private final LinkedHashMap<K, Entry<V>> entries = new LinkedHashMap<>(16, .75f, true);
    private boolean closed;

    public ExpiringBoundedStore(int capacity, Duration ttl) {
        this(capacity, ttl, Clock.systemUTC());
    }

    public ExpiringBoundedStore(int capacity, Duration ttl, Clock clock) {
        if (capacity < 1) throw new IllegalArgumentException("capacity must be positive");
        if (ttl == null || ttl.isNegative() || ttl.isZero()) throw new IllegalArgumentException("ttl must be positive");
        this.capacity = capacity;
        this.ttlMillis = ttl.toMillis();
        this.clock = clock;
    }

    public synchronized void put(K key, V value) {
        ensureOpen();
        cleanup();
        entries.put(key, new Entry<>(value, clock.millis() + ttlMillis));
        while (entries.size() > capacity) {
            Iterator<K> it = entries.keySet().iterator();
            it.next();
            it.remove();
        }
    }

    public synchronized Optional<V> get(K key) {
        ensureOpen();
        Entry<V> entry = entries.get(key);
        if (entry == null) return Optional.empty();
        if (entry.expiresAt() <= clock.millis()) {
            entries.remove(key);
            return Optional.empty();
        }
        return Optional.of(entry.value());
    }

    public synchronized Optional<V> remove(K key) {
        ensureOpen();
        Entry<V> entry = entries.remove(key);
        if (entry == null || entry.expiresAt() <= clock.millis()) return Optional.empty();
        return Optional.of(entry.value());
    }

    public synchronized void cleanup() {
        ensureOpen();
        long now = clock.millis();
        entries.entrySet().removeIf(e -> e.getValue().expiresAt() <= now);
    }

    public synchronized int size() {
        ensureOpen();
        cleanup();
        return entries.size();
    }

    @Override public synchronized void close() {
        entries.clear();
        closed = true;
    }

    private void ensureOpen() {
        if (closed) throw new IllegalStateException("store is closed");
    }
}
