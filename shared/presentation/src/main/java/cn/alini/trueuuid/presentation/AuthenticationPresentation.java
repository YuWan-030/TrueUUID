package cn.alini.trueuuid.presentation;

/**
 * Loader-neutral wording and public presentation for a completed login.
 * Minecraft text objects and delivery remain adapter responsibilities.
 */
public enum AuthenticationPresentation {
    MOJANG("premium", "mojang", "trueuuid.chat.premium", "trueuuid.title.premium",
            "trueuuid.subtitle.premium", ConfirmedAccountStatus.PREMIUM),
    YGGDRASIL("premium", "yggdrasil", "trueuuid.chat.skin_site", "trueuuid.title.premium",
            "trueuuid.subtitle.skin_site", ConfirmedAccountStatus.PREMIUM),
    GRACE("premium", "recent_ip_grace", "trueuuid.chat.premium", "trueuuid.title.premium",
            "trueuuid.subtitle.premium", ConfirmedAccountStatus.PREMIUM),
    NATIVE_ONLINE_MODE("premium", "native_online_mode", "trueuuid.chat.online_mode", "trueuuid.title.premium",
            "trueuuid.subtitle.online_mode", ConfirmedAccountStatus.PREMIUM),
    OFFLINE_FALLBACK("offline", "offline_fallback", "trueuuid.chat.offline_fallback", "trueuuid.title.offline",
            "trueuuid.subtitle.offline", ConfirmedAccountStatus.OFFLINE);

    private final String outcome;
    private final String authenticationSource;
    private final String chatTranslationKey;
    private final String titleTranslationKey;
    private final String subtitleTranslationKey;
    private final ConfirmedAccountStatus clientStatus;

    AuthenticationPresentation(String outcome, String authenticationSource, String chatTranslationKey,
                               String titleTranslationKey, String subtitleTranslationKey,
                               ConfirmedAccountStatus clientStatus) {
        this.outcome = outcome;
        this.authenticationSource = authenticationSource;
        this.chatTranslationKey = chatTranslationKey;
        this.titleTranslationKey = titleTranslationKey;
        this.subtitleTranslationKey = subtitleTranslationKey;
        this.clientStatus = clientStatus;
    }

    public String outcome() { return outcome; }
    public String authenticationSource() { return authenticationSource; }
    public String chatTranslationKey() { return chatTranslationKey; }
    public String titleTranslationKey() { return titleTranslationKey; }
    public String subtitleTranslationKey() { return subtitleTranslationKey; }
    public ConfirmedAccountStatus clientStatus() { return clientStatus; }
}
