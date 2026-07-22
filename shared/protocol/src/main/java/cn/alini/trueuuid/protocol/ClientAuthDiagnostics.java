package cn.alini.trueuuid.protocol;

import java.util.Locale;

/** Token-free categories for client authentication failures. */
public final class ClientAuthDiagnostics {
    public static String failureCategory(Throwable failure) {
        if (failure == null) return "authentication request rejected (unknown failure)";
        String message = failure.getMessage();
        String normalized = message == null ? "" : message.toLowerCase(Locale.ROOT);
        if (normalized.contains("invalid token") || normalized.contains("invalid session")
                || normalized.contains("unauthorized") || normalized.contains("forbidden")) {
            return "account session rejected (refresh the launcher account)";
        }
        if (normalized.contains("timeout") || normalized.contains("connect")
                || normalized.contains("unavailable") || normalized.contains("service")) {
            return "authentication service unavailable";
        }
        return "authentication request rejected (" + failure.getClass().getSimpleName() + ")";
    }

    private ClientAuthDiagnostics() {}
}
