package cn.alini.trueuuid.client;

import java.util.Locale;

/**
 * Fixed, token-free categories for client join failures, ported from
 * forge-1.20.1. A small fixed category is enough to tell a stale launcher
 * session from a connectivity/service failure without ever logging the access
 * token, profile properties, nonce, endpoint, or raw authlib response.
 */
public final class ClientAuthDiagnostics {
    public static String failureCategory(Throwable failure) {
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
