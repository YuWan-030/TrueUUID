package cn.alini.trueuuid.api;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccountStatusStoreTest {

    @Test void unknownWhenNothingPublished() {
        AccountStatusStore<String> store = new AccountStatusStore<>();
        assertEquals(AccountStatus.UNKNOWN, store.statusOf(UUID.randomUUID()));
        assertEquals(AccountStatus.UNKNOWN, store.statusOf(null));
    }

    @Test void publishThenQueryThenClear() {
        AccountStatusStore<String> store = new AccountStatusStore<>();
        UUID id = UUID.randomUUID();
        store.publish("alice", id, AccountStatus.PREMIUM_VERIFIED);
        assertEquals(AccountStatus.PREMIUM_VERIFIED, store.statusOf(id));
        store.clear(id);
        assertEquals(AccountStatus.UNKNOWN, store.statusOf(id));
    }

    @Test void callbacksFireInRegistrationOrderWithPublishedStatus() {
        AccountStatusStore<String> store = new AccountStatusStore<>();
        List<String> order = new ArrayList<>();
        store.register((player, status) -> order.add("a:" + player + ":" + status));
        store.register((player, status) -> order.add("b:" + player + ":" + status));
        store.publish("bob", UUID.randomUUID(), AccountStatus.OFFLINE_FALLBACK);
        assertEquals(List.of("a:bob:OFFLINE_FALLBACK", "b:bob:OFFLINE_FALLBACK"), order);
    }

    @Test void throwingCallbackIsIsolatedAndPublicationStillHappens() {
        List<RuntimeException> caught = new ArrayList<>();
        AccountStatusStore<String> store = new AccountStatusStore<>((player, failure) -> caught.add(failure));
        List<String> ran = new ArrayList<>();
        store.register((player, status) -> { throw new IllegalStateException("boom"); });
        store.register((player, status) -> ran.add("second-ran"));
        UUID id = UUID.randomUUID();
        store.publish("carol", id, AccountStatus.ONLINE_MODE);
        assertEquals(List.of("second-ran"), ran);
        assertEquals(1, caught.size());
        assertTrue(caught.get(0) instanceof IllegalStateException);
        assertEquals(AccountStatus.ONLINE_MODE, store.statusOf(id));
    }

    @Test void clearAllDropsLiveStatusesButKeepsCallbacks() {
        List<AccountStatus> seen = new ArrayList<>();
        AccountStatusStore<String> store = new AccountStatusStore<>();
        store.register((player, status) -> seen.add(status));
        UUID first = UUID.randomUUID();
        store.publish("dave", first, AccountStatus.PREMIUM_VERIFIED);
        store.clearAll();
        assertEquals(AccountStatus.UNKNOWN, store.statusOf(first));
        UUID second = UUID.randomUUID();
        store.publish("erin", second, AccountStatus.OFFLINE_FALLBACK);
        assertEquals(AccountStatus.OFFLINE_FALLBACK, store.statusOf(second));
        assertEquals(List.of(AccountStatus.PREMIUM_VERIFIED, AccountStatus.OFFLINE_FALLBACK), seen);
    }

    @Test void nullArgumentsAreIgnored() {
        AccountStatusStore<String> store = new AccountStatusStore<>();
        UUID id = UUID.randomUUID();
        store.publish("x", null, AccountStatus.PREMIUM_VERIFIED);
        store.publish("x", id, null);
        assertEquals(AccountStatus.UNKNOWN, store.statusOf(id));
        store.register(null);
        store.clear(null);
    }
}
