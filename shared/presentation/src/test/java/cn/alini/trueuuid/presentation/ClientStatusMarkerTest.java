package cn.alini.trueuuid.presentation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ClientStatusMarkerTest {
    @Test void onlyServerPublishableStatusesRoundTrip() {
        assertEquals(ConfirmedAccountStatus.PREMIUM,
                ClientStatusMarker.decode(ClientStatusMarker.encode(ConfirmedAccountStatus.PREMIUM)));
        assertEquals(ConfirmedAccountStatus.OFFLINE,
                ClientStatusMarker.decode(ClientStatusMarker.encode(ConfirmedAccountStatus.OFFLINE)));
        assertThrows(IllegalArgumentException.class,
                () -> ClientStatusMarker.encode(ConfirmedAccountStatus.SINGLEPLAYER));
        assertThrows(IllegalArgumentException.class,
                () -> ClientStatusMarker.encode(ConfirmedAccountStatus.LAN_PREMIUM));
    }

    @Test void ordinaryActionBarAndMalformedMarkersAreIgnored() {
        assertNull(ClientStatusMarker.decode("hello"));
        assertNull(ClientStatusMarker.decode("trueuuid:server-confirmed-status/not-a-number"));
        assertNull(ClientStatusMarker.decode(null));
    }
}
