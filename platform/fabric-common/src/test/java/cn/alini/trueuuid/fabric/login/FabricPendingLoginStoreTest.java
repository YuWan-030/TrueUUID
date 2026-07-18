package cn.alini.trueuuid.fabric.login;

import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class FabricPendingLoginStoreTest {
    @Test
    void isBoundedAndEvictsTheOldestResult() {
        AtomicLong now = new AtomicLong(100);
        FabricPendingLoginStore store = new FabricPendingLoginStore(2, 1000, now::get);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID third = UUID.randomUUID();

        store.record(first, FabricAuthenticationSource.VERIFIED);
        store.record(second, FabricAuthenticationSource.OFFLINE_FALLBACK);
        store.record(third, FabricAuthenticationSource.GRACE);

        assertEquals(2, store.size());
        assertNull(store.consume(first));
        assertEquals(FabricAuthenticationSource.OFFLINE_FALLBACK, store.consume(second));
        assertEquals(FabricAuthenticationSource.GRACE, store.consume(third));
    }

    @Test
    void expiresOnReadAndConsumesExactlyOnce() {
        AtomicLong now = new AtomicLong(100);
        FabricPendingLoginStore store = new FabricPendingLoginStore(2, 50, now::get);
        UUID player = UUID.randomUUID();
        store.record(player, FabricAuthenticationSource.VERIFIED);

        assertEquals(FabricAuthenticationSource.VERIFIED, store.consume(player));
        assertNull(store.consume(player));

        store.record(player, FabricAuthenticationSource.OFFLINE_FALLBACK);
        now.addAndGet(50);
        assertNull(store.consume(player));
        assertEquals(0, store.size());
    }

    @Test
    void clearsAllPendingResultsForShutdown() {
        FabricPendingLoginStore store = new FabricPendingLoginStore(2, 1000, () -> 100L);
        store.record(UUID.randomUUID(), FabricAuthenticationSource.VERIFIED);
        store.record(UUID.randomUUID(), FabricAuthenticationSource.OFFLINE_FALLBACK);

        store.clear();

        assertEquals(0, store.size());
    }
}
