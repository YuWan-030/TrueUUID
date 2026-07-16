package cn.alini.trueuuid.protocol;

import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Standard bounded {@link GraceCache}: a short, same-IP reconnect grace ported
 * from forge-1.20.1's RecentIpGraceCache. A verified login records an inactive
 * entry; logout activates it; one reconnect with the same name and IP inside
 * the TTL may consume it. Entries are memory-only, capacity-bounded, and an
 * entry never activated within five minutes is dropped on access.
 */
public final class RecentIpGrace implements GraceCache, AutoCloseable {
    private static final int MAX_ENTRIES = 4096;
    private static final long UNACTIVATED_TTL_MS = 300_000L;

    private static final class Record {
        Entry entry;
        long graceStartedAt;
        long recordedAt;
    }

    private final int maxEntries;
    private final Clock clock;
    private final LinkedHashMap<String, Record> map = new LinkedHashMap<>(16, .75f, true);

    public RecentIpGrace() { this(MAX_ENTRIES, Clock.systemUTC()); }

    RecentIpGrace(int maxEntries, Clock clock) {
        this.maxEntries = maxEntries;
        this.clock = clock;
    }

    @Override public synchronized void record(String name, String clientIp, Entry entry) {
        Objects.requireNonNull(entry, "entry");
        if (name == null || name.isBlank() || clientIp == null || clientIp.isEmpty()) return;
        Record record = new Record();
        record.entry = entry;
        record.graceStartedAt = 0L;
        record.recordedAt = clock.millis();
        map.put(key(name, clientIp), record);
        while (map.size() > maxEntries) map.remove(map.keySet().iterator().next());
    }

    @Override public synchronized void activateAfterLogout(String name, String clientIp) {
        if (name == null || clientIp == null || clientIp.isEmpty()) return;
        Record record = map.get(key(name, clientIp));
        if (record != null) record.graceStartedAt = clock.millis();
    }

    @Override public synchronized Optional<Entry> consume(String name, String clientIp, Duration ttl) {
        if (name == null || clientIp == null || clientIp.isEmpty()
                || ttl == null || ttl.isNegative() || ttl.isZero()) {
            return Optional.empty();
        }
        String key = key(name, clientIp);
        Record record = map.get(key);
        if (record == null) return Optional.empty();
        long now = clock.millis();
        if (record.graceStartedAt <= 0L) {
            if (now - record.recordedAt > UNACTIVATED_TTL_MS) map.remove(key);
            return Optional.empty();
        }
        map.remove(key);
        return now - record.graceStartedAt <= ttl.toMillis() ? Optional.of(record.entry) : Optional.empty();
    }

    @Override public synchronized void close() { map.clear(); }

    private static String key(String name, String ip) {
        return name.toLowerCase(Locale.ROOT) + "|" + ip;
    }
}
