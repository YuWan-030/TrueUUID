package cn.alini.trueuuid.server;

import cn.alini.trueuuid.protocol.AuthMessages;
import cn.alini.trueuuid.protocol.AuthWireCodec;
import cn.alini.trueuuid.protocol.SessionVerifier;
import cn.alini.trueuuid.protocol.VerifiedProfile;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ForgeLoginFlowTest {
    @Test
    void usesSharedQueryFixtureAndOnlyAcceptsTheActiveTransaction() {
        ForgeLoginFlow flow = new ForgeLoginFlow();
        assertEquals("nonce", AuthWireCodec.decodeQuery(flow.start(22, "nonce", 100)).nonce());
        byte[] answer = AuthWireCodec.encodeAnswer(new AuthMessages.Answer(true, "", false, false));
        assertTrue(flow.accept(23, answer, "Player", "203.0.113.9", verifier()).join().isEmpty());
        assertTrue(flow.accept(22, answer, "Player", "203.0.113.9", verifier()).join().isPresent());
    }

    @Test
    void timeoutAndCloseAreExplicitLifecycleEffects() {
        ForgeLoginFlow flow = new ForgeLoginFlow();
        flow.start(22, "nonce", 100);
        assertTrue(flow.timedOut(130, 30));
        flow.close();
        assertTrue(!flow.active());
    }

    private static SessionVerifier verifier() {
        return request -> CompletableFuture.completedFuture(Optional.of(new VerifiedProfile(UUID.randomUUID(), "Player", List.of())));
    }
}
