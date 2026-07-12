package cn.alini.trueuuid.protocol;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;

class ExpiringBoundedStoreTest {
    @Test void evictsOldestAndCleansExpiredState() {
        MutableClock clock = new MutableClock();
        var store = new ExpiringBoundedStore<String, String>(2, Duration.ofSeconds(5), clock);
        store.put("a", "1"); store.put("b", "2"); store.put("c", "3");
        assertTrue(store.get("a").isEmpty());
        assertEquals(2, store.size());
        clock.advance(Duration.ofSeconds(6));
        store.cleanup();
        assertEquals(0, store.size());
    }

    @Test void closeDisposesAllStateAndPreventsReuse() {
        var store = new ExpiringBoundedStore<String, String>(2, Duration.ofSeconds(5));
        store.put("a", "1");
        store.close();
        assertThrows(IllegalStateException.class, store::size);
    }

    private static final class MutableClock extends Clock {
        private Instant now = Instant.EPOCH;
        void advance(Duration duration) { now = now.plus(duration); }
        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
