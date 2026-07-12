package cn.alini.trueuuid.protocol;

import java.util.Objects;

public final class AuthMessages {
    public static final int MAX_NONCE_CHARS = 64;
    public static final int MAX_ENDPOINT_CHARS = 2048;
    public static final int MAX_SUMMARY_CHARS = 1024;

    public record Query(String nonce, boolean migrationAvailable, String offlineUuid, String summary) {
        public Query {
            nonce = bounded(nonce, MAX_NONCE_CHARS, "nonce");
            offlineUuid = bounded(Objects.requireNonNullElse(offlineUuid, ""), 64, "offlineUuid");
            summary = bounded(Objects.requireNonNullElse(summary, ""), MAX_SUMMARY_CHARS, "summary");
            if (!migrationAvailable && (!offlineUuid.isEmpty() || !summary.isEmpty())) {
                throw new IllegalArgumentException("migration details without an offer");
            }
        }
    }

    public record Answer(boolean joined, String customEndpoint, boolean migrationConfirmed,
                         boolean missingSessionToken) {
        public Answer {
            customEndpoint = bounded(Objects.requireNonNullElse(customEndpoint, ""),
                    MAX_ENDPOINT_CHARS, "customEndpoint");
        }
    }

    private static String bounded(String value, int max, String field) {
        Objects.requireNonNull(value, field);
        if (value.length() > max) throw new IllegalArgumentException(field + " is too long");
        return value;
    }

    private AuthMessages() {}
}
