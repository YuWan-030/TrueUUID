package cn.alini.trueuuid.protocol;

import java.util.UUID;

/** Local session hints for selecting the client proof path, never server authorization. */
public final class ClientSession {
    /**
     * PCL2 uses the profile UUID as its offline token, even with account type
     * "msa". Match that exact placeholder, not arbitrary UUID-shaped tokens:
     * Yggdrasil providers can issue opaque tokens in that format too.
     */
    public static boolean isMissing(UUID profileId, String accessToken) {
        if (profileId == null || accessToken == null || accessToken.isBlank() || "0".equals(accessToken)) {
            return true;
        }
        String uuid = profileId.toString();
        return uuid.equalsIgnoreCase(accessToken)
                || uuid.replace("-", "").equalsIgnoreCase(accessToken);
    }

    /** Failed proof must not become an explicit offline request. */
    public static AuthMessages.Answer proofAnswer(boolean joined, String endpoint) {
        return new AuthMessages.Answer(joined, endpoint, false, false);
    }

    public static AuthMessages.Answer offlineAnswer() {
        return new AuthMessages.Answer(false, "", false, true);
    }

    private ClientSession() {}
}
