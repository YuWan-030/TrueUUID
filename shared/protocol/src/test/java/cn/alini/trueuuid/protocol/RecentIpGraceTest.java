package cn.alini.trueuuid.protocol;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecentIpGraceTest {
    private static final Duration TTL = Duration.ofSeconds(10);

    private static GraceCache.Entry entry(UUID uuid) {
        return new GraceCache.Entry(uuid, AuthSource.MOJANG, "Mojang");
    }

    @Test void consumesOnceWithinTtlAfterActivation() {
        MutableClock clock = new MutableClock();
        RecentIpGrace cache = new RecentIpGrace(4, clock);
        UUID uuid = UUID.randomUUID();
        cache.record("Alice", "203.0.113.1", entry(uuid));
        cache.activateAfterLogout("Alice", "203.0.113.1");
        clock.advance(Duration.ofSeconds(5));
        assertEquals(uuid, cache.consume("Alice", "203.0.113.1", TTL).orElseThrow().uuid());
        assertTrue(cache.consume("Alice", "203.0.113.1", TTL).isEmpty());
    }

    @Test void neverGrantsWithoutActivation() {
        MutableClock clock = new MutableClock();
        RecentIpGrace cache = new RecentIpGrace(4, clock);
        cache.record("Alice", "203.0.113.1", entry(UUID.randomUUID()));
        assertTrue(cache.consume("Alice", "203.0.113.1", TTL).isEmpty());
    }

    @Test void deniesDifferentIpAndExpiredWindow() {
        MutableClock clock = new MutableClock();
        RecentIpGrace cache = new RecentIpGrace(4, clock);
        cache.record("Alice", "203.0.113.1", entry(UUID.randomUUID()));
        cache.activateAfterLogout("Alice", "203.0.113.1");
        assertTrue(cache.consume("Alice", "198.51.100.9", TTL).isEmpty());
        clock.advance(Duration.ofSeconds(11));
        assertTrue(cache.consume("Alice", "203.0.113.1", TTL).isEmpty());
    }

    @Test void capacityEvictsOldestEntry() {
        MutableClock clock = new MutableClock();
        RecentIpGrace cache = new RecentIpGrace(1, clock);
        cache.record("Alice", "203.0.113.1", entry(UUID.randomUUID()));
        cache.record("Bob", "203.0.113.2", entry(UUID.randomUUID()));
        cache.activateAfterLogout("Alice", "203.0.113.1");
        assertTrue(cache.consume("Alice", "203.0.113.1", TTL).isEmpty());
    }

    private static final class MutableClock extends Clock {
        private Instant now = Instant.ofEpochMilli(1);
        void advance(Duration duration) { now = now.plus(duration); }
        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
