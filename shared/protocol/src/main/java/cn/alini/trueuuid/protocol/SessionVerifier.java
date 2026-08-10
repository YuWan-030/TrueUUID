package cn.alini.trueuuid.protocol;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/** Verifies a client join assertion without exposing a loader connection. */
public interface SessionVerifier {
    /**
     * The client IP is server-observed metadata for local request limits and
     * policy decisions. It must not be sent to a remote hasJoined endpoint by
     * default: proxies and split IPv4/IPv6 routing can make it differ from the
     * address observed when the client called joinServer.
     */
    record Request(String username, String serverId, String clientIp, String clientEndpoint) {}

    CompletableFuture<Optional<VerifiedProfile>> verify(Request request);
}
