package cn.alini.trueuuid.protocol;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/** Verifies a client join assertion without exposing a loader connection. */
public interface SessionVerifier {
    record Request(String username, String serverId, String clientIp, String clientEndpoint) {}

    CompletableFuture<Optional<VerifiedProfile>> verify(Request request);
}
