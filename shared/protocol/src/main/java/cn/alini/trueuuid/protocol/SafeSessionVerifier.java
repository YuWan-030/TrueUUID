package cn.alini.trueuuid.protocol;

import java.net.URI;
import java.net.URLEncoder;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.function.Supplier;
import javax.net.ssl.SSLException;

/** Shared safe hasJoined verifier with a replaceable response parser for focused tests. */
public final class SafeSessionVerifier implements SessionVerifier, PremiumSessionVerifier {
    public static final URI MOJANG_HAS_JOINED = URI.create("https://sessionserver.mojang.com/session/minecraft/hasJoined");
    private static final int MAX_HAS_JOINED_ATTEMPTS = 4;

    @FunctionalInterface public interface ResponseParser {
        Optional<VerifiedProfile> parse(SafeSessionHttpClient.Response response) throws Exception;
    }

    private final BoundedRequestCoordinator requests;
    private final Supplier<EndpointPolicy> endpointPolicy;
    private final ResponseParser parser;
    private final SessionHttpTransport http;

    public SafeSessionVerifier(BoundedRequestCoordinator requests, Supplier<EndpointPolicy> endpointPolicy,
                               ResponseParser parser) {
        this(requests, endpointPolicy, parser, new SafeSessionHttpClient());
    }

    SafeSessionVerifier(BoundedRequestCoordinator requests, Supplier<EndpointPolicy> endpointPolicy,
                        ResponseParser parser, SessionHttpTransport http) {
        this.requests = Objects.requireNonNull(requests, "requests");
        this.endpointPolicy = Objects.requireNonNull(endpointPolicy, "endpointPolicy");
        this.parser = Objects.requireNonNull(parser, "parser");
        this.http = Objects.requireNonNull(http, "http");
    }

    @Override public CompletableFuture<Optional<VerifiedProfile>> verify(Request request) {
        CompletableFuture<PremiumVerificationResult> detailed = verifyPremium(request).toCompletableFuture();
        CancellableMappingFuture<Optional<VerifiedProfile>> compatible = new CancellableMappingFuture<>(detailed);
        detailed.whenComplete((result, failure) -> {
            if (failure != null) compatible.completeExceptionally(failure);
            else compatible.complete(result.verifiedProfile());
        });
        return compatible;
    }

    @Override public CompletableFuture<PremiumVerificationResult> verifyPremium(Request request) {
        Objects.requireNonNull(request, "request");
        String username = Objects.requireNonNullElse(request.username(), "");
        String endpoint = Objects.requireNonNullElse(request.clientEndpoint(), "");
        return requests.submit(username, request.clientIp(), request.serverId() + "\u0000" + endpoint, () -> {
            try {
                URI target;
                java.util.List<java.net.InetAddress> approvedAddresses;
                if (endpoint.isBlank()) {
                    target = withQuery(MOJANG_HAS_JOINED, request);
                    approvedAddresses = null;
                } else {
                    EndpointPolicy.ApprovedEndpoint approved = endpointPolicy.get().approveClientEndpoint(endpoint);
                    target = withQuery(approved.uri(), request);
                    approvedAddresses = approved.addresses();
                }
                // A successful join assertion may take a short time to reach
                // the hasJoined read path. Retry only 204 (not verified yet),
                // on the bounded auth worker and never on the server thread.
                for (int attempt = 0; attempt < MAX_HAS_JOINED_ATTEMPTS; attempt++) {
                    SafeSessionHttpClient.Response response = approvedAddresses == null
                            ? http.getTrusted(target) : http.get(target, approvedAddresses);
                    if (response.status() == 204 && attempt + 1 < MAX_HAS_JOINED_ATTEMPTS) {
                        Thread.sleep(150L * (attempt + 1));
                        continue;
                    }
                    if (response.status() != 200) return PremiumVerificationResult.failed(statusFailure(response.status()));
                    Optional<VerifiedProfile> verified;
                    try {
                        verified = parser.parse(response);
                    } catch (Exception malformed) {
                        return PremiumVerificationResult.failed(HybridIdentityPolicy.PremiumProof.MALFORMED_RESPONSE);
                    }
                    if (verified.isEmpty()) {
                        return PremiumVerificationResult.failed(HybridIdentityPolicy.PremiumProof.MALFORMED_RESPONSE);
                    }
                    VerifiedProfile profile = verified.orElseThrow();
                    if (!profile.name().equalsIgnoreCase(username)) {
                        return PremiumVerificationResult.failed(HybridIdentityPolicy.PremiumProof.NAME_MISMATCH);
                    }
                    return PremiumVerificationResult.verified(profile);
                }
                return PremiumVerificationResult.failed(HybridIdentityPolicy.PremiumProof.HTTP_204);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return PremiumVerificationResult.failed(HybridIdentityPolicy.PremiumProof.CANCELLED);
            } catch (Exception failure) {
                return PremiumVerificationResult.failed(exceptionFailure(failure));
            }
        });
    }

    private static HybridIdentityPolicy.PremiumProof statusFailure(int status) {
        if (status == 204) return HybridIdentityPolicy.PremiumProof.HTTP_204;
        if (status == 429) return HybridIdentityPolicy.PremiumProof.RATE_LIMITED;
        if (status >= 500 && status <= 599) return HybridIdentityPolicy.PremiumProof.SERVER_ERROR;
        return HybridIdentityPolicy.PremiumProof.NOT_JOINED;
    }

    private static HybridIdentityPolicy.PremiumProof exceptionFailure(Exception failure) {
        if (failure instanceof CancellationException) return HybridIdentityPolicy.PremiumProof.CANCELLED;
        if (failure instanceof SocketTimeoutException) return HybridIdentityPolicy.PremiumProof.TIMEOUT;
        if (failure instanceof UnknownHostException) return HybridIdentityPolicy.PremiumProof.DNS_FAILURE;
        if (failure instanceof SSLException) return HybridIdentityPolicy.PremiumProof.TLS_FAILURE;
        String message = Objects.requireNonNullElse(failure.getMessage(), "").toLowerCase(java.util.Locale.ROOT);
        if (message.contains("redirect")) return HybridIdentityPolicy.PremiumProof.REDIRECTED;
        if (message.contains("too large")) return HybridIdentityPolicy.PremiumProof.OVERSIZED_RESPONSE;
        return HybridIdentityPolicy.PremiumProof.AUTHORITY_UNAVAILABLE;
    }

    static URI withQuery(URI base, Request request) throws Exception {
        // The IP parameter is optional and deliberately omitted. The address
        // seen by this server can belong to a proxy or differ from the address
        // used by the client's joinServer request because of split routing.
        String query = "username=" + encode(request.username()) + "&serverId=" + encode(request.serverId());
        URI endpoint = new URI("https", null, base.getHost(), base.getPort(), base.getPath(), null, null);
        // query is already form-encoded. Parse the complete URI so '%' is not
        // escaped a second time by URI's component constructor.
        return new URI(endpoint.toASCIIString() + "?" + query);
    }

    private static String encode(String value) { return URLEncoder.encode(Objects.requireNonNullElse(value, ""), StandardCharsets.UTF_8); }

    private static final class CancellableMappingFuture<T> extends CompletableFuture<T> {
        private final Future<?> delegate;

        private CancellableMappingFuture(Future<?> delegate) {
            this.delegate = delegate;
        }

        @Override public boolean cancel(boolean mayInterruptIfRunning) {
            boolean cancelled = super.cancel(mayInterruptIfRunning);
            delegate.cancel(mayInterruptIfRunning);
            return cancelled;
        }
    }
}
