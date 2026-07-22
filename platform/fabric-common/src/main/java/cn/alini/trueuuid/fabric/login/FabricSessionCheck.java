package cn.alini.trueuuid.fabric.login;

import cn.alini.trueuuid.protocol.BoundedRequestCoordinator;
import cn.alini.trueuuid.protocol.EndpointPolicy;
import cn.alini.trueuuid.protocol.HasJoinedProfileParser;
import cn.alini.trueuuid.protocol.SafeSessionHttpClient;
import cn.alini.trueuuid.protocol.SafeSessionVerifier;
import cn.alini.trueuuid.protocol.SessionVerifier;
import cn.alini.trueuuid.protocol.VerifiedProfile;
import cn.alini.trueuuid.fabric.config.FabricConfig;

import java.net.URI;
import java.util.concurrent.CompletableFuture;

/** Bounded hasJoined verifier for Fabric. */
final class FabricSessionCheck {
    private static BoundedRequestCoordinator requests;

    static CompletableFuture<VerifiedProfile> hasJoinedAsync(String username, String serverId) {
        return hasJoinedAsync(username, serverId, "", "");
    }

    static CompletableFuture<VerifiedProfile> hasJoinedAsync(String username, String serverId,
                                                             String clientIp, String clientEndpoint) {
        return verifier().verify(new SessionVerifier.Request(username, serverId, clientIp, clientEndpoint))
                .thenApply(result -> result.orElse(null));
    }

    static CompletableFuture<Integer> probeMojangAsync() {
        SafeSessionHttpClient http = new SafeSessionHttpClient();
        return requests().submit("__probe__", "mojang", "probe", () ->
                http.getTrusted(URI.create("https://sessionserver.mojang.com/session/minecraft/hasJoined?username=Mojang&serverId=test")).status());
    }

    static synchronized void close() {
        if (requests != null) {
            requests.close();
            requests = null;
        }
    }

    private static synchronized SessionVerifier verifier() {
        return new SafeSessionVerifier(requests(), () -> new EndpointPolicy(FabricConfig.yggdrasilHosts()),
                HasJoinedProfileParser::parse);
    }

    private static synchronized BoundedRequestCoordinator requests() {
        if (requests == null) requests = new BoundedRequestCoordinator();
        return requests;
    }

    private FabricSessionCheck() {}
}
