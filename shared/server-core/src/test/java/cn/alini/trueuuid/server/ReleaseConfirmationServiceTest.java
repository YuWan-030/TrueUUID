package cn.alini.trueuuid.server;

import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ReleaseConfirmationServiceTest {
    @Test void confirmationIsActorTargetGenerationBoundAndOneUse() {
        ReleaseConfirmationService service = new ReleaseConfirmationService(
                new SecureRandom(new byte[]{1, 2, 3}),
                Clock.fixed(Instant.ofEpochMilli(1_000), ZoneOffset.UTC));
        UUID target = UUID.randomUUID();
        String token = service.issue("console", target, 7);
        assertFalse(service.consume("other", target, 7, token));

        token = service.issue("console", target, 7);
        assertFalse(service.consume("console", target, 8, token));

        token = service.issue("console", target, 7);
        assertTrue(service.consume("console", target, 7, token));
        assertFalse(service.consume("console", target, 7, token));
    }
}
