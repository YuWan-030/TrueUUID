package cn.alini.trueuuid.server;

import cn.alini.trueuuid.protocol.AuthMessages;
import cn.alini.trueuuid.protocol.AuthWireCodec;
import cn.alini.trueuuid.protocol.SessionVerifier;
import cn.alini.trueuuid.protocol.VerifiedProfile;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LoginAttemptTest {
    @Test
    void queryUsesSharedGoldenCodecAndBoundedState() {
        LoginAttempt attempt = new LoginAttempt();
        assertEquals("nonce", AuthWireCodec.decodeQuery(attempt.begin(7, "nonce", 10)).nonce());
        assertEquals(LoginAttempt.Result.TIMEOUT, attempt.timeout(40, 30));
    }

    @Test
    void malformedAnswerIsRejectedBeforeVerifierRuns() {
        LoginAttempt attempt = new LoginAttempt();
        attempt.begin(7, "nonce", 10);
        assertThrows(IllegalArgumentException.class,
                () -> attempt.answer(7, new byte[] {1}, "name", "203.0.113.7", unusedVerifier()));
    }

    @Test
    void lifecycleVerifiesOnlyTheExpectedTransaction() {
        LoginAttempt attempt = new LoginAttempt();
        attempt.begin(7, "nonce", 10);
        byte[] answer = AuthWireCodec.encodeAnswer(new AuthMessages.Answer(true, "", false, false));
        assertEquals(LoginAttempt.Result.IGNORE, attempt.answer(8, answer, "name", "203.0.113.7", unusedVerifier()).join().result());
        assertEquals(LoginAttempt.Result.VERIFIED, attempt.answer(7, answer, "name", "203.0.113.7", unusedVerifier()).join().result());
    }

    private static SessionVerifier unusedVerifier() {
        return request -> CompletableFuture.completedFuture(Optional.of(
                new VerifiedProfile(java.util.UUID.randomUUID(), "name", java.util.List.of())));
    }
}
