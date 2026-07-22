package cn.alini.trueuuid.presentation;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class ClientStatusStateTest {
    private final AtomicLong now = new AtomicLong();
    private final ClientStatusState state = new ClientStatusState(now::get);

    @Test void visibleAtReceiptAndBeforeExpiry() {
        state.receive(ConfirmedAccountStatus.PREMIUM);
        assertEquals(1.0F, notice().alpha());
        now.set(2_499_999_999L);
        assertEquals(ConfirmedAccountStatus.PREMIUM, notice().status());
        assertEquals(1.0F, notice().alpha());
        now.set(2_999_999_999L);
        assertTrue(state.transientNotice().isPresent());
    }

    @Test void hiddenAtAndAfterExpiry() {
        state.receive(ConfirmedAccountStatus.OFFLINE);
        now.set(ClientStatusState.VISIBLE_NANOS);
        assertTrue(state.transientNotice().isEmpty());
        now.incrementAndGet();
        assertTrue(state.transientNotice().isEmpty());
        assertEquals(ConfirmedAccountStatus.OFFLINE, state.persistentStatus().orElseThrow());
    }

    @Test void fadesDuringFinalHalfSecond() {
        state.receive(ConfirmedAccountStatus.PREMIUM);
        now.set(2_750_000_000L);
        assertEquals(0.5F, notice().alpha(), 0.0001F);
    }

    @Test void newReceiptResetsTimerAndStatus() {
        state.receive(ConfirmedAccountStatus.PREMIUM);
        now.set(2_900_000_000L);
        state.receive(ConfirmedAccountStatus.OFFLINE);
        now.set(3_000_000_000L);
        assertEquals(ConfirmedAccountStatus.OFFLINE, notice().status());
        assertEquals(1.0F, notice().alpha());
    }

    @Test void clearDropsTransientAndPersistentState() {
        state.receive(ConfirmedAccountStatus.PREMIUM);
        state.clear();
        assertTrue(state.transientNotice().isEmpty());
        assertTrue(state.persistentStatus().isEmpty());
    }

    @Test void singleplayerEntryIsOnlyReceivedOnceUntilClear() {
        assertTrue(state.receiveIfChanged(ConfirmedAccountStatus.SINGLEPLAYER));
        now.set(1_000_000_000L);
        assertFalse(state.receiveIfChanged(ConfirmedAccountStatus.SINGLEPLAYER));
        assertEquals(1.0F, notice().alpha());
        state.clear();
        assertTrue(state.receiveIfChanged(ConfirmedAccountStatus.SINGLEPLAYER));
    }

    @Test void integratedWorldTransitionsOnceFromSingleplayerToLanPremium() {
        assertEquals(ClientStatusState.LocalWorldTransition.ENTERED_SINGLEPLAYER,
                state.reconcileIntegratedWorld(true, false));
        assertEquals(ClientStatusState.LocalWorldTransition.NONE,
                state.reconcileIntegratedWorld(true, false));

        now.set(1_000_000_000L);
        assertEquals(ClientStatusState.LocalWorldTransition.OPENED_TO_LAN,
                state.reconcileIntegratedWorld(true, true));
        assertEquals(ConfirmedAccountStatus.LAN_PREMIUM, notice().status());
        assertEquals(ConfirmedAccountStatus.LAN_PREMIUM, state.persistentStatus().orElseThrow());
        assertEquals(ClientStatusState.LocalWorldTransition.NONE,
                state.reconcileIntegratedWorld(true, true));
    }

    @Test void multiplayerDoesNotOverrideServerConfirmedStatus() {
        state.receive(ConfirmedAccountStatus.OFFLINE);
        assertEquals(ClientStatusState.LocalWorldTransition.NONE,
                state.reconcileIntegratedWorld(false, false));
        assertEquals(ConfirmedAccountStatus.OFFLINE, state.persistentStatus().orElseThrow());
    }

    private ClientStatusState.TransientNotice notice() {
        return state.transientNotice().orElseThrow();
    }
}
