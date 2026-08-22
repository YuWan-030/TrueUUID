package cn.alini.trueuuid.server;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** One-use actor/target/generation-bound confirmations for identity release. */
public final class ReleaseConfirmationService {
    public static final Duration LIFETIME = Duration.ofSeconds(60);
    private final SecureRandom random;
    private final Clock clock;
    private final Map<String, Pending> byActor = new HashMap<>();

    public ReleaseConfirmationService() {
        this(new SecureRandom(), Clock.systemUTC());
    }

    ReleaseConfirmationService(SecureRandom random, Clock clock) {
        this.random = Objects.requireNonNull(random, "random");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public synchronized String issue(String actorId, UUID target, long generation) {
        String actor = requireActor(actorId);
        Objects.requireNonNull(target, "target");
        if (generation < 0) throw new IllegalArgumentException("generation must not be negative");
        byte[] bytes = new byte[18];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        byActor.put(actor, new Pending(target, generation,
                Math.addExact(clock.millis(), LIFETIME.toMillis()), token));
        return token;
    }

    public synchronized boolean consume(String actorId, UUID target, long generation, String token) {
        String actor = requireActor(actorId);
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(token, "token");
        Pending pending = byActor.remove(actor);
        return pending != null && clock.millis() <= pending.deadlineEpochMilli()
                && pending.target().equals(target) && pending.generation() == generation
                && constantTimeEquals(pending.token(), token);
    }

    private static boolean constantTimeEquals(String expected, String actual) {
        byte[] left = expected.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        byte[] right = actual.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        return java.security.MessageDigest.isEqual(left, right);
    }

    private static String requireActor(String actorId) {
        Objects.requireNonNull(actorId, "actorId");
        if (actorId.isBlank() || actorId.length() > 128) throw new IllegalArgumentException("invalid actor id");
        return actorId;
    }

    private record Pending(UUID target, long generation, long deadlineEpochMilli, String token) {
    }
}
