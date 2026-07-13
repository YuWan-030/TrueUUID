package cn.alini.trueuuid.protocol;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class LoginStateMachineTest {
    @Test void authThenMigrationRequiresDistinctConfirmedAnswers() {
        LoginStateMachine state = new LoginStateMachine();
        assertEquals("nonce", state.beginAuthentication(41, "nonce", 100).nonce());
        assertEquals(LoginStateMachine.AnswerResult.VERIFY,
                state.acceptAnswer(41, new AuthMessages.Answer(true, "", false, false)));
        assertEquals(LoginStateMachine.Phase.VERIFYING, state.phase());

        MigrationTransaction.Offer offer = new MigrationTransaction.Offer(UUID.randomUUID(), "playerdata");
        assertTrue(state.beginMigration(42, offer, 200).migrationAvailable());
        assertEquals(LoginStateMachine.AnswerResult.IGNORE,
                state.acceptAnswer(41, new AuthMessages.Answer(true, "", true, false)));
        assertEquals(LoginStateMachine.AnswerResult.MIGRATE,
                state.acceptAnswer(42, new AuthMessages.Answer(true, "", true, false)));
        assertEquals(LoginStateMachine.Phase.MIGRATING, state.phase());
    }

    @Test void timeoutDistinguishesTheLoginAndMigrationStages() {
        LoginStateMachine state = new LoginStateMachine();
        state.beginAuthentication(1, "nonce", 100);
        assertEquals(LoginStateMachine.TimeoutResult.NONE, state.timeoutAt(1099, 1000, 5000));
        assertEquals(LoginStateMachine.TimeoutResult.AUTH, state.timeoutAt(1100, 1000, 5000));
        assertEquals(LoginStateMachine.AnswerResult.VERIFY,
                state.acceptAnswer(1, new AuthMessages.Answer(true, "", false, false)));
        state.beginMigration(2, new MigrationTransaction.Offer(UUID.randomUUID(), "stats"), 2000);
        assertEquals(LoginStateMachine.TimeoutResult.MIGRATION, state.timeoutAt(7000, 1000, 5000));
    }

    @Test void explicitClientDenialDoesNotStartVerification() {
        LoginStateMachine state = new LoginStateMachine();
        state.beginAuthentication(1, "nonce", 1);
        assertEquals(LoginStateMachine.AnswerResult.DENY,
                state.acceptAnswer(1, new AuthMessages.Answer(false, "", false, true)));
        assertEquals(LoginStateMachine.Phase.AWAITING_AUTH, state.phase());
    }
}
