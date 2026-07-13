package cn.alini.trueuuid.protocol;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/** Shared safe hasJoined verifier; response parsing is supplied by the Java-only adapter. */
public final class SafeSessionVerifier implements SessionVerifier {
    public static final URI MOJANG_HAS_JOINED = URI.create("https://sessionserver.mojang.com/session/minecraft/hasJoined");
    private static final int MAX_HAS_JOINED_ATTEMPTS = 4;

    @FunctionalInterface public interface ResponseParser {
        Optional<VerifiedProfile> parse(SafeSessionHttpClient.Response response) throws Exception;
    }

    private final BoundedRequestCoordinator requests;
    private final Supplier<EndpointPolicy> endpointPolicy;
    private final ResponseParser parser;
    private final SafeSessionHttpClient http;

    public SafeSessionVerifier(BoundedRequestCoordinator requests, Supplier<EndpointPolicy> endpointPolicy,
                               ResponseParser parser) {
        this(requests, endpointPolicy, parser, new SafeSessionHttpClient());
    }

    SafeSessionVerifier(BoundedRequestCoordinator requests, Supplier<EndpointPolicy> endpointPolicy,
                        ResponseParser parser, SafeSessionHttpClient http) {
        this.requests = Objects.requireNonNull(requests, "requests");
        this.endpointPolicy = Objects.requireNonNull(endpointPolicy, "endpointPolicy");
        this.parser = Objects.requireNonNull(parser, "parser");
        this.http = Objects.requireNonNull(http, "http");
    }

    @Override public CompletableFuture<Optional<VerifiedProfile>> verify(Request request) {
        Objects.requireNonNull(request, "request");
        String username = Objects.requireNonNullElse(request.username(), "");
        String endpoint = Objects.requireNonNullElse(request.clientEndpoint(), "");
        return requests.submit(username, request.clientIp(), request.serverId() + "\u0000" + endpoint, () -> {
            try {
                String query = "username=" + encode(username) + "&serverId=" + encode(request.serverId())
                        + (request.clientIp() == null || request.clientIp().isBlank() ? "" : "&ip=" + encode(request.clientIp()));
                URI target;
                java.util.List<java.net.InetAddress> approvedAddresses;
                if (endpoint.isBlank()) {
                    target = withQuery(MOJANG_HAS_JOINED, query);
                    approvedAddresses = null;
                } else {
                    EndpointPolicy.ApprovedEndpoint approved = endpointPolicy.get().approveClientEndpoint(endpoint);
                    target = withQuery(approved.uri(), query);
                    approvedAddresses = approved.addresses();
                }
                // A successful join assertion may take a short time to reach
                // the hasJoined read path. Retry only 204 (not verified yet),
                // on the bounded auth worker and never on the server thread.
                for (int attempt = 0; attempt < MAX_HAS_JOINED_ATTEMPTS; attempt++) {
                    SafeSessionHttpClient.Response response = approvedAddresses == null
                            ? http.getTrusted(target) : http.get(target, approvedAddresses);
                    Optional<VerifiedProfile> verified = parser.parse(response);
                    if (verified.isPresent() || response.status() != 204 || attempt + 1 == MAX_HAS_JOINED_ATTEMPTS) {
                        return verified;
                    }
                    Thread.sleep(150L * (attempt + 1));
                }
                return Optional.empty();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return Optional.empty();
            } catch (Exception ignored) {
                return Optional.empty();
            }
        });
    }

    private static URI withQuery(URI base, String query) throws Exception {
        return new URI("https", null, base.getHost(), base.getPort(), base.getPath(), query, null);
    }

    private static String encode(String value) { return URLEncoder.encode(Objects.requireNonNullElse(value, ""), StandardCharsets.UTF_8); }
}
