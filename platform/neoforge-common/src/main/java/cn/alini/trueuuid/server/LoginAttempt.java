package cn.alini.trueuuid.server;

import cn.alini.trueuuid.protocol.AuthMessages;
import cn.alini.trueuuid.protocol.AuthWireCodec;
import cn.alini.trueuuid.protocol.LoginStateMachine;
import cn.alini.trueuuid.protocol.MigrationTransaction;
import cn.alini.trueuuid.protocol.SessionVerifier;
import cn.alini.trueuuid.protocol.VerifiedProfile;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Loader-neutral state owned by one native login listener.  The Mixin supplies
 * native packet/profile effects; this class never receives Minecraft objects.
 */
public final class LoginAttempt {
    public enum Result { IGNORE, DENY, TIMEOUT, VERIFIED }
    public record Outcome(Result result, Optional<VerifiedProfile> profile) {}

    private final LoginStateMachine state = new LoginStateMachine();
    private CompletableFuture<Optional<VerifiedProfile>> work;
    private String nonce;

    public byte[] begin(int transactionId, String nonce, long nowMillis) {
        this.nonce = nonce;
        return AuthWireCodec.encodeQuery(state.beginAuthentication(transactionId, nonce, nowMillis));
    }

    public CompletableFuture<Outcome> answer(int transactionId, byte[] wire, String username, String clientIp,
                                              SessionVerifier verifier) {
        AuthMessages.Answer answer = AuthWireCodec.decodeAnswer(wire);
        return switch (state.acceptAnswer(transactionId, answer)) {
            case IGNORE -> CompletableFuture.completedFuture(new Outcome(Result.IGNORE, Optional.empty()));
            case DENY -> CompletableFuture.completedFuture(new Outcome(Result.DENY, Optional.empty()));
            case MIGRATE -> CompletableFuture.completedFuture(new Outcome(Result.DENY, Optional.empty()));
            case VERIFY -> {
                work = verifier.verify(new SessionVerifier.Request(username, nonce, clientIp,
                        answer.customEndpoint()));
                yield work.handle((profile, error) -> error == null && profile.isPresent()
                        ? new Outcome(Result.VERIFIED, profile) : new Outcome(Result.DENY, Optional.empty()));
            }
        };
    }

    public byte[] migrationQuery(int transactionId, MigrationTransaction.Offer offer, long nowMillis) {
        return AuthWireCodec.encodeQuery(state.beginMigration(transactionId, offer, nowMillis));
    }

    public boolean acceptMigration(int transactionId, byte[] wire) {
        AuthMessages.Answer answer = AuthWireCodec.decodeAnswer(wire);
        return state.acceptAnswer(transactionId, answer) == LoginStateMachine.AnswerResult.MIGRATE;
    }

    public Result timeout(long nowMillis, long timeoutMillis) {
        return state.timeoutAt(nowMillis, timeoutMillis, timeoutMillis) == LoginStateMachine.TimeoutResult.NONE
                ? Result.IGNORE : Result.TIMEOUT;
    }

    public void disconnect() {
        if (work != null) work.cancel(true);
        work = null;
        nonce = null;
        state.reset();
    }

    public LoginStateMachine.Phase phase() {
        return state.phase();
    }
}
