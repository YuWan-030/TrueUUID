package cn.alini.trueuuid.server;

import cn.alini.trueuuid.protocol.BoundedRequestCoordinator;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class AuthRequestCoordinatorTest {
    @Test void deduplicatesOnlyTheSameNonceAndEndpoint() throws Exception {
        try (BoundedRequestCoordinator coordinator = new BoundedRequestCoordinator()) {
            CountDownLatch release = new CountDownLatch(1);
            AtomicInteger calls = new AtomicInteger();
            var first = coordinator.submit("Alice", "203.0.113.1", "nonce-a\0endpoint", () -> {
                calls.incrementAndGet();
                assertTrue(release.await(2, TimeUnit.SECONDS));
                return "a";
            });
            var duplicate = coordinator.submit("Alice", "203.0.113.1", "nonce-a\0endpoint", () -> "wrong");
            var distinct = coordinator.submit("Alice", "203.0.113.1", "nonce-b\0endpoint", () -> {
                calls.incrementAndGet();
                return "b";
            });
            assertSame(first, duplicate);
            release.countDown();
            assertEquals("a", first.get(2, TimeUnit.SECONDS));
            assertEquals("b", distinct.get(2, TimeUnit.SECONDS));
            assertEquals(2, calls.get());
        }
    }

    @Test void closeCancelsAndCleansMultipleInFlightRequests() throws Exception {
        BoundedRequestCoordinator coordinator = new BoundedRequestCoordinator();
        CountDownLatch started = new CountDownLatch(2);
        var first = coordinator.submit("Alice", "203.0.113.1", "a", () -> {
            started.countDown(); Thread.sleep(5_000); return "a";
        });
        var second = coordinator.submit("Alice", "203.0.113.1", "b", () -> {
            started.countDown(); Thread.sleep(5_000); return "b";
        });
        assertTrue(started.await(2, TimeUnit.SECONDS));
        assertDoesNotThrow(coordinator::close);
        assertTrue(first.isCancelled());
        assertTrue(second.isCancelled());
    }
}
