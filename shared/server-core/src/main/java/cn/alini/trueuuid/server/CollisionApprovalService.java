package cn.alini.trueuuid.server;

import java.time.Clock;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * One-use, short-lived administrator approvals for an identity collision.
 *
 * <p>An approval is bound to the normalized base name, exact resolution and
 * repository generation. It grants no authentication authority: premium
 * proof and offline UUID derivation still occur normally after consumption.</p>
 */
public final class CollisionApprovalService {
    public static final Duration LIFETIME = Duration.ofSeconds(60);

    private final Clock clock;
    private final Map<String, Pending> approvals = new HashMap<>();

    public CollisionApprovalService() {
        this(Clock.systemUTC());
    }

    CollisionApprovalService(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public synchronized Approval issue(
            String baseName,
            UnifiedAdmissionPolicy.CollisionResolution resolution,
            long repositoryGeneration
    ) {
        String normalized = MinecraftNames.normalize(baseName);
        Objects.requireNonNull(resolution, "resolution");
        if (repositoryGeneration < 0) {
            throw new IllegalArgumentException("repository generation must not be negative");
        }
        long deadline = Math.addExact(clock.millis(), LIFETIME.toMillis());
        Pending pending = new Pending(resolution, repositoryGeneration, deadline);
        approvals.put(normalized, pending);
        return new Approval(normalized, resolution, repositoryGeneration, deadline);
    }

    public synchronized boolean consume(
            String baseName,
            UnifiedAdmissionPolicy.CollisionResolution resolution,
            long repositoryGeneration
    ) {
        String normalized = MinecraftNames.normalize(baseName);
        Objects.requireNonNull(resolution, "resolution");
        Pending pending = approvals.remove(normalized);
        return pending != null
                && clock.millis() <= pending.deadlineEpochMilli()
                && pending.resolution() == resolution
                && pending.repositoryGeneration() == repositoryGeneration;
    }

    public synchronized Optional<Approval> pending(String baseName) {
        String normalized = MinecraftNames.normalize(baseName);
        Pending pending = approvals.get(normalized);
        if (pending == null) return Optional.empty();
        if (clock.millis() > pending.deadlineEpochMilli()) {
            approvals.remove(normalized);
            return Optional.empty();
        }
        return Optional.of(new Approval(normalized, pending.resolution(),
                pending.repositoryGeneration(), pending.deadlineEpochMilli()));
    }

    public record Approval(
            String normalizedBaseName,
            UnifiedAdmissionPolicy.CollisionResolution resolution,
            long repositoryGeneration,
            long deadlineEpochMilli
    ) {
        public Approval {
            normalizedBaseName = MinecraftNames.normalize(normalizedBaseName);
            Objects.requireNonNull(resolution, "resolution");
            if (repositoryGeneration < 0 || deadlineEpochMilli < 0) {
                throw new IllegalArgumentException("invalid collision approval bounds");
            }
        }
    }

    private record Pending(
            UnifiedAdmissionPolicy.CollisionResolution resolution,
            long repositoryGeneration,
            long deadlineEpochMilli
    ) {
    }
}
