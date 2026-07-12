package cn.alini.trueuuid.protocol;

public final class AuthPolicy {
    public enum Decision { PREMIUM_GRACE, OFFLINE, DENY }

    public record Input(boolean knownVerifiedName, boolean explicitOfflineClient,
                        boolean localProxy, boolean graceAvailable,
                        boolean denyOfflineForKnown, boolean allowOfflineOnFailure,
                        boolean allowOfflineForUnknownOnly) {}

    public static Decision decide(Input input) {
        if (input.knownVerifiedName() && !input.explicitOfflineClient()
                && (input.localProxy() || input.graceAvailable())) {
            return Decision.PREMIUM_GRACE;
        }
        if (input.knownVerifiedName() && input.denyOfflineForKnown()) return Decision.DENY;
        if (!input.allowOfflineOnFailure()) return Decision.DENY;
        if (!input.allowOfflineForUnknownOnly() || !input.knownVerifiedName()) return Decision.OFFLINE;
        return Decision.DENY;
    }

    private AuthPolicy() {}
}
