package cn.alini.trueuuid.fabric.login;

import cn.alini.trueuuid.api.AccountStatus;
import cn.alini.trueuuid.presentation.AuthenticationPresentation;

/**
 * The server-owned outcome of a Fabric login.  This deliberately carries no
 * Minecraft or networking types (only the plain-Java {@link AccountStatus}
 * value), so both the pending store and its contract tests remain independent
 * of a connection or player object.
 */
public enum FabricAuthenticationSource {
    VERIFIED(AuthenticationPresentation.MOJANG, AccountStatus.PREMIUM_VERIFIED),
    YGGDRASIL(AuthenticationPresentation.YGGDRASIL, AccountStatus.PREMIUM_VERIFIED),
    GRACE(AuthenticationPresentation.GRACE, AccountStatus.PREMIUM_VERIFIED),
    OFFLINE_FALLBACK(AuthenticationPresentation.OFFLINE_FALLBACK, AccountStatus.OFFLINE_FALLBACK),
    NATIVE_ONLINE_MODE(AuthenticationPresentation.NATIVE_ONLINE_MODE, AccountStatus.ONLINE_MODE);

    private final AuthenticationPresentation presentation;
    private final AccountStatus publicStatus;

    FabricAuthenticationSource(AuthenticationPresentation presentation, AccountStatus publicStatus) {
        this.presentation = presentation;
        this.publicStatus = publicStatus;
    }

    public String auditLabel() { return presentation.authenticationSource(); }
    public String chatKey() { return presentation.chatTranslationKey(); }
    public String titleKey() { return presentation.titleTranslationKey(); }
    public String subtitleKey() { return presentation.subtitleTranslationKey(); }
    public AuthenticationPresentation presentation() { return presentation; }
    public ClientStatus clientStatus() {
        return presentation.clientStatus().isPremium() ? ClientStatus.PREMIUM : ClientStatus.OFFLINE;
    }

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
