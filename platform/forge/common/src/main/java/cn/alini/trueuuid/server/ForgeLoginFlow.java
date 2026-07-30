package cn.alini.trueuuid.server;

import cn.alini.trueuuid.protocol.AuthMessages;
import cn.alini.trueuuid.protocol.AuthWireCodec;
import cn.alini.trueuuid.protocol.LoginStateMachine;
import cn.alini.trueuuid.protocol.MigrationTransaction;
import cn.alini.trueuuid.protocol.SessionVerifier;
import cn.alini.trueuuid.protocol.VerifiedProfile;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/** Native Forge listener state with all verification work delegated off-thread. */
public final class ForgeLoginFlow {
    private final LoginStateMachine state = new LoginStateMachine();
    private String nonce;
    private CompletableFuture<Optional<VerifiedProfile>> verification;

    public synchronized byte[] start(int transactionId, String nonce, long now) {
        this.nonce = nonce;
        return AuthWireCodec.encodeQuery(state.beginAuthentication(transactionId, nonce, now));
    }

    public synchronized CompletableFuture<Optional<VerifiedProfile>> accept(int transactionId, byte[] answerWire,
                                                                             String name, String clientIp,
                                                                             SessionVerifier verifier) {
        AuthMessages.Answer answer = AuthWireCodec.decodeAnswer(answerWire);
        return switch (state.acceptAnswer(transactionId, answer)) {
            case VERIFY -> {
                verification = verifier.verify(new SessionVerifier.Request(name, nonce, clientIp, answer.customEndpoint()));
                yield verification.exceptionally(error -> Optional.empty());
            }
            case IGNORE, DENY, MIGRATE -> CompletableFuture.completedFuture(Optional.empty());
        };
    }

    public synchronized byte[] migrationQuery(int transactionId, MigrationTransaction.Offer offer, long now) {
        return AuthWireCodec.encodeQuery(state.beginMigration(transactionId, offer, now));
    }

    public synchronized boolean acceptMigration(int transactionId, byte[] answerWire) {
        AuthMessages.Answer answer = AuthWireCodec.decodeAnswer(answerWire);
        return state.acceptAnswer(transactionId, answer) == LoginStateMachine.AnswerResult.MIGRATE;
    }

    public synchronized boolean timedOut(long now, long timeoutMillis) {
        return state.timeoutAt(now, timeoutMillis, timeoutMillis) != LoginStateMachine.TimeoutResult.NONE;
    }

    public synchronized boolean active() { return state.isActive(); }
    public synchronized LoginStateMachine.Phase phase() { return state.phase(); }

    public synchronized void close() {
        if (verification != null) verification.cancel(true);
        verification = null;
        nonce = null;
        state.reset();
    }
}
