package cn.alini.trueuuid.presentation;

/** Server-confirmed account state plus explicit client-local integrated-world modes. */
public enum ConfirmedAccountStatus {
    PREMIUM(1, "trueuuid.overlay.premium", "trueuuid.pause.premium", 0x259B4A),
    OFFLINE(2, "trueuuid.overlay.offline", "trueuuid.pause.offline", 0xBD2E2E),
    SINGLEPLAYER(3, "trueuuid.overlay.singleplayer", "trueuuid.pause.singleplayer", 0xE9B114),
    /** Local integrated server published to LAN; never accepted from a network marker. */
    LAN_PREMIUM(4, "trueuuid.overlay.lan_premium", "trueuuid.pause.lan_premium", 0x259B4A);

    private final int wireId;
    private final String overlayTranslationKey;
    private final String pauseTranslationKey;
    private final int rgb;

    ConfirmedAccountStatus(int wireId, String overlayTranslationKey, String pauseTranslationKey, int rgb) {
        this.wireId = wireId;
        this.overlayTranslationKey = overlayTranslationKey;
        this.pauseTranslationKey = pauseTranslationKey;
        this.rgb = rgb;
    }

    public int wireId() { return wireId; }
    public String overlayTranslationKey() { return overlayTranslationKey; }
    public String pauseTranslationKey() { return pauseTranslationKey; }
    public int rgb() { return rgb; }
    public boolean isPremium() { return this == PREMIUM || this == LAN_PREMIUM; }

    public static ConfirmedAccountStatus fromWireId(int wireId) {
        return switch (wireId) {
            case 1 -> PREMIUM;
            case 2 -> OFFLINE;
            default -> null;
        };
    }
}
