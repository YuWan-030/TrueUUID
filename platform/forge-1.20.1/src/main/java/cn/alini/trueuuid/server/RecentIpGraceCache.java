package cn.alini.trueuuid.server;

import cn.alini.trueuuid.protocol.AuthSource;
import cn.alini.trueuuid.protocol.GraceCache;

import java.time.Duration;
import java.time.Instant;
import java.time.Clock;
import java.util.*;

public class RecentIpGraceCache implements AutoCloseable, GraceCache {
    private static final int MAX_ENTRIES = 4096;
    private static final long UNACTIVATED_TTL_MS = 300_000L;
    private final int maxEntries;
    private final Clock clock;
    public record GraceResult(UUID premiumUuid, AuthState.AuthSource source, String displayName) {}

    private static class Rec {
        UUID premiumUuid;
        AuthState.AuthSource source;
        String displayName;
        long graceStartedAt;
        long recordedAt;
    }

    private final LinkedHashMap<String, Rec> map = new LinkedHashMap<>(16, .75f, true);

    public RecentIpGraceCache() { this(MAX_ENTRIES, Clock.systemUTC()); }
    RecentIpGraceCache(int maxEntries, Clock clock) {
        this.maxEntries = maxEntries;
        this.clock = clock;
    }

    private static String key(String name, String ip) {
        return name.toLowerCase(Locale.ROOT) + "|" + ip;
    }

    public synchronized void record(String name, String ip, UUID premiumUuid) {
        record(name, ip, premiumUuid, AuthState.AuthSource.MOJANG, "Mojang");
    }

    public synchronized void record(String name, String ip, UUID premiumUuid, AuthState.AuthSource source, String displayName) {
        if (ip == null || ip.isEmpty()) return;
        Rec r = new Rec();
        r.premiumUuid = premiumUuid;
        r.source = source != null ? source : AuthState.AuthSource.MOJANG;
        r.displayName = displayName == null || displayName.isBlank() ? r.source.name() : displayName;
        r.graceStartedAt = 0L;
        r.recordedAt = clock.millis();
        map.put(key(name, ip), r);
        while (map.size() > maxEntries) map.remove(map.keySet().iterator().next());
    }

    @Override public synchronized void record(String name, String ip, GraceCache.Entry entry) {
        Objects.requireNonNull(entry, "entry");
        record(name, ip, entry.uuid(), AuthState.AuthSource.valueOf(entry.source().name()), entry.displayName());
    }

    public synchronized void activateAfterLogout(String name, String ip) {
        if (ip == null || ip.isEmpty()) return;
        Rec r = map.get(key(name, ip));
        if (r != null) {
            r.graceStartedAt = clock.millis();
        }
    }

    public synchronized Optional<UUID> tryGrace(String name, String ip, int ttlSeconds) {
        if (ip == null || ip.isEmpty()) return Optional.empty();
        Rec r = map.get(key(name, ip));
        if (r == null) return Optional.empty();
        if (r.graceStartedAt <= 0L) return Optional.empty();
        long now = clock.millis();
        if (now - r.graceStartedAt <= ttlSeconds * 1000L) {
            UUID premiumUuid = r.premiumUuid;
            map.remove(key(name, ip));
            return Optional.of(premiumUuid);
        }
        map.remove(key(name, ip));
        return Optional.empty();
    }

    public synchronized Optional<GraceResult> tryGraceResult(String name, String ip, int ttlSeconds) {
        if (ip == null || ip.isEmpty()) return Optional.empty();
        Rec r = map.get(key(name, ip));
        if (r == null) return Optional.empty();
        if (r.graceStartedAt <= 0L) return Optional.empty();
        long now = clock.millis();
        if (now - r.graceStartedAt <= ttlSeconds * 1000L) {
            AuthState.AuthSource source = r.source != null ? r.source : AuthState.AuthSource.MOJANG;
            String displayName = r.displayName == null || r.displayName.isBlank() ? source.name() : r.displayName;
            map.remove(key(name, ip));
            return Optional.of(new GraceResult(r.premiumUuid, source, displayName));
        }
        map.remove(key(name, ip));
        return Optional.empty();
    }

    @Override public synchronized Optional<GraceCache.Entry> consume(String name, String ip, Duration ttl) {
        if (ttl == null || ttl.isNegative() || ttl.isZero()) return Optional.empty();
        long seconds = Math.max(1L, ttl.toSeconds());
        Optional<GraceResult> result = tryGraceResult(name, ip, (int) Math.min(Integer.MAX_VALUE, seconds));
        return result.map(value -> new GraceCache.Entry(value.premiumUuid(),
                AuthSource.valueOf(value.source().name()), value.displayName()));
    }

    public synchronized void cleanup(int ttlSeconds) {
        long now = clock.millis();
        map.entrySet().removeIf(e -> e.getValue().graceStartedAt > 0L
                ? now - e.getValue().graceStartedAt > ttlSeconds * 1000L
                : now - e.getValue().recordedAt > UNACTIVATED_TTL_MS);
    }

    @Override public synchronized void close() { map.clear(); }
}
