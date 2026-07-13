package cn.alini.trueuuid.protocol;

import java.util.Objects;

/**
 * Loader-independent login state transitions. Adapters perform the resulting
 * packet, scheduling, profile and disconnect effects on their native APIs.
 */
public final class LoginStateMachine {
    public enum Phase { IDLE, AWAITING_AUTH, VERIFYING, AWAITING_MIGRATION, MIGRATING }
    public enum AnswerResult { IGNORE, VERIFY, MIGRATE, DENY }
    public enum TimeoutResult { NONE, AUTH, MIGRATION }

    private Phase phase = Phase.IDLE;
    private int transactionId;
    private long startedAtMillis;

    public AuthMessages.Query beginAuthentication(int transactionId, String nonce, long nowMillis) {
        requireIdle();
        start(transactionId, nowMillis, Phase.AWAITING_AUTH);
        return new AuthMessages.Query(nonce, false, "", "");
    }

    public AuthMessages.Query beginMigration(int transactionId, MigrationTransaction.Offer offer, long nowMillis) {
        require(Phase.VERIFYING, "migration can only follow verification");
        Objects.requireNonNull(offer, "offer");
        start(transactionId, nowMillis, Phase.AWAITING_MIGRATION);
        return new AuthMessages.Query("trueuuid:migration-confirm", true,
                offer.offlineUuid().toString(), offer.summary());
    }

    public AnswerResult acceptAnswer(int transactionId, AuthMessages.Answer answer) {
        Objects.requireNonNull(answer, "answer");
        if (this.transactionId != transactionId) return AnswerResult.IGNORE;
        if (phase == Phase.AWAITING_AUTH) {
            if (!answer.joined()) return AnswerResult.DENY;
            phase = Phase.VERIFYING;
            return AnswerResult.VERIFY;
        }
        if (phase == Phase.AWAITING_MIGRATION) {
            if (!answer.joined() || !answer.migrationConfirmed()) return AnswerResult.DENY;
            phase = Phase.MIGRATING;
            return AnswerResult.MIGRATE;
        }
        return AnswerResult.IGNORE;
    }

    public TimeoutResult timeoutAt(long nowMillis, long authTimeoutMillis, long migrationTimeoutMillis) {
        if (phase == Phase.AWAITING_AUTH || phase == Phase.VERIFYING) {
            return elapsed(nowMillis, authTimeoutMillis) ? TimeoutResult.AUTH : TimeoutResult.NONE;
        }
        if (phase == Phase.AWAITING_MIGRATION || phase == Phase.MIGRATING) {
            return elapsed(nowMillis, migrationTimeoutMillis) ? TimeoutResult.MIGRATION : TimeoutResult.NONE;
        }
        return TimeoutResult.NONE;
    }

    public Phase phase() { return phase; }
    public int transactionId() { return transactionId; }
    public boolean isActive() { return phase != Phase.IDLE; }
    public void reset() { phase = Phase.IDLE; transactionId = 0; startedAtMillis = 0; }

    private void start(int transactionId, long nowMillis, Phase next) {
        if (transactionId == 0 || nowMillis < 0) throw new IllegalArgumentException("invalid login state input");
        this.transactionId = transactionId;
        this.startedAtMillis = nowMillis;
        this.phase = next;
    }

    private boolean elapsed(long nowMillis, long timeoutMillis) {
        return timeoutMillis > 0 && nowMillis - startedAtMillis >= timeoutMillis;
    }

    private void requireIdle() { require(Phase.IDLE, "login is already active"); }
    private void require(Phase expected, String message) {
        if (phase != expected) throw new IllegalStateException(message);
    }
}
