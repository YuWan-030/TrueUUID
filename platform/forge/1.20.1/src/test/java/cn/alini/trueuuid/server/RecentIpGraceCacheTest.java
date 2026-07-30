package cn.alini.trueuuid.server;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RecentIpGraceCacheTest {
    @Test void activatedEntryExpiresAndIsRemoved() {
        MutableClock clock = new MutableClock();
        RecentIpGraceCache cache = new RecentIpGraceCache(4, clock);
        cache.record("Alice", "203.0.113.1", UUID.randomUUID());
        cache.activateAfterLogout("Alice", "203.0.113.1");
        clock.advance(Duration.ofSeconds(11));
        cache.cleanup(10);
        assertTrue(cache.tryGrace("Alice", "203.0.113.1", 10).isEmpty());
    }

    @Test void capacityEvictsOldestEntry() {
        MutableClock clock = new MutableClock();
        RecentIpGraceCache cache = new RecentIpGraceCache(1, clock);
        cache.record("Alice", "203.0.113.1", UUID.randomUUID());
        cache.record("Bob", "203.0.113.2", UUID.randomUUID());
        cache.activateAfterLogout("Alice", "203.0.113.1");
        assertTrue(cache.tryGrace("Alice", "203.0.113.1", 10).isEmpty());
    }

    private static final class MutableClock extends Clock {
        private Instant now = Instant.ofEpochMilli(1);
        void advance(Duration duration) { now = now.plus(duration); }
        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
