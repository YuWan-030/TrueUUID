package cn.alini.trueuuid.protocol;

public final class AuthPolicy {
    public enum Decision { PREMIUM_GRACE, OFFLINE, DENY }

    public record Input(boolean knownVerifiedName, boolean explicitOfflineClient,
                        boolean localProxy, boolean graceAvailable,
                        boolean denyOfflineForKnown, boolean allowOfflineOnFailure,
                        boolean allowOfflineForUnknownOnly) {}

    public static Decision decide(Input input) {
        // Failure, silence, or a deceptive response must not downgrade into
        // offline admission or a cached premium identity. Only the exact
        // bounded no-session response may select the offline policy.
        if (!input.explicitOfflineClient()) return Decision.DENY;
        if (input.knownVerifiedName() && input.denyOfflineForKnown()) return Decision.DENY;
        if (!input.allowOfflineOnFailure()) return Decision.DENY;
        if (!input.allowOfflineForUnknownOnly() || !input.knownVerifiedName()) return Decision.OFFLINE;
        return Decision.DENY;
    }

    private AuthPolicy() {}
}
