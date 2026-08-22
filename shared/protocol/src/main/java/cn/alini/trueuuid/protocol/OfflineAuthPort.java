package cn.alini.trueuuid.protocol;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/** Server-neutral boundary for a future Bukkit OfflineAuth adapter. */
@FunctionalInterface
public interface OfflineAuthPort {
    enum Operation {
        AUTHENTICATE_ENROLLED,
        ENROLL_NEW
    }

    enum Failure {
        INVALID_CREDENTIAL,
        ALREADY_ENROLLED,
        UNAVAILABLE,
        CANCELLED,
        TIMEOUT,
        INTERNAL_ERROR
    }

    record Request(String connectionId, long generation, String requestedName, Operation operation) {
        public Request {
            connectionId = requireBounded(connectionId, 256, "connectionId");
            requestedName = requireMinecraftName(requestedName);
            Objects.requireNonNull(operation, "operation");
        }
    }

    sealed interface Result permits Accepted, Denied {
    }

    record Accepted(UUID uuid, String canonicalName) implements Result {
        public Accepted {
            Objects.requireNonNull(uuid, "uuid");
            canonicalName = requireEffectiveName(canonicalName);
        }
    }

    record Denied(Failure reason) implements Result {
        public Denied {
            Objects.requireNonNull(reason, "reason");
        }
    }

    CompletionStage<Result> authenticate(Request request);

    private static String requireMinecraftName(String value) {
        String name = requireBounded(value, 16, "name");
        if (name.isBlank() || !name.matches("[A-Za-z0-9_]+")) {
            throw new IllegalArgumentException("name is not a valid Minecraft name");
        }
        return name;
    }

    private static String requireEffectiveName(String value) {
        String name = requireBounded(value, 16, "name");
        if (name.isBlank() || !(name.matches("[A-Za-z0-9_]+")
                || name.matches("[.+-][A-Za-z0-9_]+"))) {
            throw new IllegalArgumentException("name is not a valid effective Minecraft name");
        }
        return name;
    }

    private static String requireBounded(String value, int maximum, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank() || value.length() > maximum) {
            throw new IllegalArgumentException(field + " is blank or too long");
        }
        return value;
    }
}
