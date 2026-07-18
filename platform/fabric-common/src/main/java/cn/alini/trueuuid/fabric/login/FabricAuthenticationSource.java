package cn.alini.trueuuid.fabric.login;

/**
 * The server-owned outcome of a Fabric login.  This deliberately carries no
 * Minecraft or networking types, so both the pending store and its contract
 * tests remain independent of a connection or player object.
 */
public enum FabricAuthenticationSource {
    VERIFIED("session-verified premium login", "trueuuid.chat.premium", "trueuuid.title.premium",
            "trueuuid.subtitle.premium", ClientStatus.PREMIUM),
    GRACE("recent same-IP grace login", "trueuuid.chat.premium", "trueuuid.title.premium",
            "trueuuid.subtitle.premium", ClientStatus.PREMIUM),
    OFFLINE_FALLBACK("offline fallback login", "trueuuid.chat.offline_fallback", "trueuuid.title.offline",
            "trueuuid.subtitle.offline", ClientStatus.OFFLINE),
    NATIVE_ONLINE_MODE("native online-mode premium login", "trueuuid.chat.online_mode", "trueuuid.title.premium",
            "trueuuid.subtitle.online_mode", ClientStatus.PREMIUM);

    private final String auditLabel;
    private final String chatKey;
    private final String titleKey;
    private final String subtitleKey;
    private final ClientStatus clientStatus;

    FabricAuthenticationSource(String auditLabel, String chatKey, String titleKey, String subtitleKey,
                               ClientStatus clientStatus) {
        this.auditLabel = auditLabel;
        this.chatKey = chatKey;
        this.titleKey = titleKey;
        this.subtitleKey = subtitleKey;
        this.clientStatus = clientStatus;
    }

    public String auditLabel() { return auditLabel; }
    public String chatKey() { return chatKey; }
    public String titleKey() { return titleKey; }
    public String subtitleKey() { return subtitleKey; }
    public ClientStatus clientStatus() { return clientStatus; }

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
