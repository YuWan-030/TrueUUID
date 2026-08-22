package cn.alini.trueuuid.protocol;

/**
 * Plain-text login notices that must remain readable before the client has
 * loaded TrueUUID's language resources.
 */
public final class ClientModRequirement {
    public static final String MISSING_CLIENT_MESSAGE =
            "This server requires TrueUUID on the client. Install or enable it, then reconnect.";

    private ClientModRequirement() {}
}
