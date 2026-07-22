package cn.alini.trueuuid.presentation;

/** Internal vanilla-packet marker used where a loader has no stable play-payload API. */
public final class ClientStatusMarker {
    private static final String PREFIX = "trueuuid:server-confirmed-status/";

    public static String encode(ConfirmedAccountStatus status) {
        if (status == null || ConfirmedAccountStatus.fromWireId(status.wireId()) != status) {
            throw new IllegalArgumentException("status is client-local and cannot be published by a server");
        }
        return PREFIX + status.wireId();
    }

    public static ConfirmedAccountStatus decode(String value) {
        if (value == null || !value.startsWith(PREFIX)) return null;
        try {
            return ConfirmedAccountStatus.fromWireId(Integer.parseInt(value.substring(PREFIX.length())));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private ClientStatusMarker() {}
}
