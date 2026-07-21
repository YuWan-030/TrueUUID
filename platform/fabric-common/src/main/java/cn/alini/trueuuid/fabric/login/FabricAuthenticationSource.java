package cn.alini.trueuuid.fabric.login;

import cn.alini.trueuuid.api.AccountStatus;

/**
 * The server-owned outcome of a Fabric login.  This deliberately carries no
 * Minecraft or networking types (only the plain-Java {@link AccountStatus}
 * value), so both the pending store and its contract tests remain independent
 * of a connection or player object.
 */
public enum FabricAuthenticationSource {
    VERIFIED("session-verified premium login", "trueuuid.chat.premium", "trueuuid.title.premium",
            "trueuuid.subtitle.premium", ClientStatus.PREMIUM, AccountStatus.PREMIUM_VERIFIED),
    GRACE("recent same-IP grace login", "trueuuid.chat.premium", "trueuuid.title.premium",
            "trueuuid.subtitle.premium", ClientStatus.PREMIUM, AccountStatus.PREMIUM_VERIFIED),
    OFFLINE_FALLBACK("offline fallback login", "trueuuid.chat.offline_fallback", "trueuuid.title.offline",
            "trueuuid.subtitle.offline", ClientStatus.OFFLINE, AccountStatus.OFFLINE_FALLBACK),
    NATIVE_ONLINE_MODE("native online-mode premium login", "trueuuid.chat.online_mode", "trueuuid.title.premium",
            "trueuuid.subtitle.online_mode", ClientStatus.PREMIUM, AccountStatus.ONLINE_MODE);

    private final String auditLabel;
    private final String chatKey;
    private final String titleKey;
    private final String subtitleKey;
    private final ClientStatus clientStatus;
    private final AccountStatus publicStatus;

    FabricAuthenticationSource(String auditLabel, String chatKey, String titleKey, String subtitleKey,
                               ClientStatus clientStatus, AccountStatus publicStatus) {
        this.auditLabel = auditLabel;
        this.chatKey = chatKey;
        this.titleKey = titleKey;
        this.subtitleKey = subtitleKey;
        this.clientStatus = clientStatus;
        this.publicStatus = publicStatus;
    }

    public String auditLabel() { return auditLabel; }
    public String chatKey() { return chatKey; }
    public String titleKey() { return titleKey; }
    public String subtitleKey() { return subtitleKey; }
    public ClientStatus clientStatus() { return clientStatus; }

    /** The public addon-API status an addon sees for this login outcome. */
    public AccountStatus publicStatus() { return publicStatus; }

    /** The only two statuses a server may publish to the Fabric badge. */
    public enum ClientStatus {
        PREMIUM(1),
        OFFLINE(2);

        private final int wireId;

        ClientStatus(int wireId) {
            this.wireId = wireId;
        }

        public int wireId() { return wireId; }

        public static ClientStatus fromWireId(int wireId) {
            return switch (wireId) {
                case 1 -> PREMIUM;
                case 2 -> OFFLINE;
                default -> null;
            };
        }
    }
}
