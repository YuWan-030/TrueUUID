package cn.alini.trueuuid.protocol;

import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLHandshakeException;
import java.io.IOException;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.ArrayDeque;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class SafeSessionVerifierTest {
    @Test
    void omitsServerObservedClientIpFromHasJoinedRequest() throws Exception {
        var request = new SessionVerifier.Request(
                "PremiumUser", "0123456789abcdef", "203.0.113.42", "");
        URI target = SafeSessionVerifier.withQuery(
                SafeSessionVerifier.MOJANG_HAS_JOINED, request);

        assertEquals("username=PremiumUser&serverId=0123456789abcdef", target.getRawQuery());
        assertFalse(target.getRawQuery().contains("ip="));
    }

    @Test
    void encodesHasJoinedValuesExactlyOnce() throws Exception {
        var request = new SessionVerifier.Request(
                "Premium User", "nonce+value", "2001:db8::1", "https://auth.example.com/hasJoined");
        URI target = SafeSessionVerifier.withQuery(
                URI.create("https://auth.example.com/sessionserver/session/minecraft/hasJoined"),
                request);

        assertEquals("username=Premium+User&serverId=nonce%2Bvalue", target.getRawQuery());
    }

    @Test void detailedVerifierKeepsAuthorityFailuresDistinct() throws Exception {
        assertFailure(new SafeSessionHttpClient.Response(204, ""), HybridIdentityPolicy.PremiumProof.HTTP_204);
        assertFailure(new SafeSessionHttpClient.Response(429, ""), HybridIdentityPolicy.PremiumProof.RATE_LIMITED);
        assertFailure(new SafeSessionHttpClient.Response(503, ""), HybridIdentityPolicy.PremiumProof.SERVER_ERROR);
        assertFailure(new SafeSessionHttpClient.Response(404, ""), HybridIdentityPolicy.PremiumProof.NOT_JOINED);
        assertFailure(new SocketTimeoutException("timeout"), HybridIdentityPolicy.PremiumProof.TIMEOUT);
        assertFailure(new UnknownHostException("missing.invalid"), HybridIdentityPolicy.PremiumProof.DNS_FAILURE);
        assertFailure(new SSLHandshakeException("certificate"), HybridIdentityPolicy.PremiumProof.TLS_FAILURE);
        assertFailure(new IOException("redirects are forbidden"), HybridIdentityPolicy.PremiumProof.REDIRECTED);
        assertFailure(new IOException("response is too large"), HybridIdentityPolicy.PremiumProof.OVERSIZED_RESPONSE);
        assertFailure(new IOException("connection reset"), HybridIdentityPolicy.PremiumProof.AUTHORITY_UNAVAILABLE);
    }

    @Test void detailedVerifierRejectsMalformedAndNameMismatchedSuccess() throws Exception {
        try (BoundedRequestCoordinator requests = new BoundedRequestCoordinator()) {
            SafeSessionVerifier malformed = verifier(requests,
                    new FakeTransport(new SafeSessionHttpClient.Response(200, "{}")),
                    response -> { throw new IllegalArgumentException("bad json"); });
            assertEquals(HybridIdentityPolicy.PremiumProof.MALFORMED_RESPONSE,
                    ((PremiumVerificationResult.Failed) malformed.verifyPremium(request()).toCompletableFuture().get()).reason());
        }

        try (BoundedRequestCoordinator requests = new BoundedRequestCoordinator()) {
            SafeSessionVerifier mismatch = verifier(requests,
                    new FakeTransport(new SafeSessionHttpClient.Response(200, "{}")),
                    response -> java.util.Optional.of(new VerifiedProfile(UUID.randomUUID(), "Mallory", List.of())));
            assertEquals(HybridIdentityPolicy.PremiumProof.NAME_MISMATCH,
                    ((PremiumVerificationResult.Failed) mismatch.verifyPremium(request()).toCompletableFuture().get()).reason());
        }
    }

    @Test void detailedVerifiedProfileStillFeedsTheCompatibleOptionalApi() throws Exception {
        UUID uuid = UUID.randomUUID();
        VerifiedProfile profile = new VerifiedProfile(uuid, "PremiumUser", List.of());
        try (BoundedRequestCoordinator requests = new BoundedRequestCoordinator()) {
            SafeSessionVerifier verifier = verifier(requests,
                    new FakeTransport(new SafeSessionHttpClient.Response(200, "{}")),
                    response -> java.util.Optional.of(profile));
            assertEquals(profile, verifier.verify(request()).get().orElseThrow());
        }
    }

    private static void assertFailure(Object responseOrFailure, HybridIdentityPolicy.PremiumProof expected) throws Exception {
        try (BoundedRequestCoordinator requests = new BoundedRequestCoordinator()) {
            FakeTransport transport = new FakeTransport(responseOrFailure);
            SafeSessionVerifier verifier = verifier(requests, transport, HasJoinedProfileParser::parse);
            PremiumVerificationResult result = verifier.verifyPremium(request()).toCompletableFuture().get();
            assertEquals(expected, ((PremiumVerificationResult.Failed) result).reason());
            if (expected == HybridIdentityPolicy.PremiumProof.HTTP_204) assertEquals(4, transport.calls);
        }
    }

    private static SafeSessionVerifier verifier(BoundedRequestCoordinator requests, SessionHttpTransport transport,
                                                SafeSessionVerifier.ResponseParser parser) {
        return new SafeSessionVerifier(requests, () -> new EndpointPolicy(List.of()), parser, transport);
    }

    private static SessionVerifier.Request request() {
        return new SessionVerifier.Request("PremiumUser", "nonce", "203.0.113.42", "");
    }

    private static final class FakeTransport implements SessionHttpTransport {
        private final ArrayDeque<Object> responses = new ArrayDeque<>();
        private int calls;

        private FakeTransport(Object response) {
            for (int index = 0; index < 4; index++) responses.add(response);
        }

        @Override public SafeSessionHttpClient.Response getTrusted(URI uri) throws IOException {
            calls++;
            Object next = responses.removeFirst();
            if (next instanceof IOException failure) throw failure;
            return (SafeSessionHttpClient.Response) next;
        }

        @Override public SafeSessionHttpClient.Response get(URI uri, List<InetAddress> approvedAddresses) throws IOException {
            return getTrusted(uri);
        }
    }
}
