package cn.alini.trueuuid.protocol;

import java.util.concurrent.CompletionStage;

/** Detailed premium verifier used by server adapters that must not collapse failures. */
@FunctionalInterface
public interface PremiumSessionVerifier {
    CompletionStage<PremiumVerificationResult> verifyPremium(SessionVerifier.Request request);
}
