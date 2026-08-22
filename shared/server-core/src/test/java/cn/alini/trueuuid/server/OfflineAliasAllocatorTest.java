package cn.alini.trueuuid.server;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class OfflineAliasAllocatorTest {
    private final OfflineAliasAllocator allocator = new OfflineAliasAllocator();
    private final UUID uuid = UUID.fromString("12345678-1234-5678-9234-567812345678");

    @Test void aliasIsRecognizableBoundedValidAndDeterministic() {
        String first = allocator.allocate("PremiumName123", uuid, "-", ignored -> false);
        String second = allocator.allocate("PremiumName123", uuid, "-", ignored -> false);
        assertEquals(first, second);
        assertEquals("-PremiumName123", first);
        assertTrue(first.matches("[.+-]?[A-Za-z0-9_]{1,15}"));
        assertEquals(15, first.length());
    }

    @Test void shortRequestedNameDoesNotReceiveAnUnnecessaryHash() {
        assertEquals("-FixGOD", allocator.allocate("FixGOD", uuid, "-", ignored -> false));
    }

    @Test void collisionRetriesDeterministicallyAndStopsAtBound() {
        Set<String> seen = new HashSet<>();
        String allocated = allocator.allocate("Name", uuid, "-", candidate -> {
            seen.add(candidate);
            return seen.size() < 3;
        });
        assertEquals(3, seen.size());
        assertTrue(allocated.startsWith("-Name"));
        assertNotEquals("-Name", allocated);
        assertThrows(IllegalStateException.class,
                () -> allocator.allocate("Name", uuid, "-", ignored -> true));
    }
}
