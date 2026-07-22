package cn.alini.trueuuid.fabric.login;

import cn.alini.trueuuid.protocol.BoundedRequestCoordinator;
import cn.alini.trueuuid.protocol.EndpointPolicy;
import cn.alini.trueuuid.protocol.SafeSessionHttpClient;
import cn.alini.trueuuid.protocol.SafeSessionVerifier;
import cn.alini.trueuuid.protocol.SessionVerifier;
import cn.alini.trueuuid.protocol.VerifiedProfile;
import cn.alini.trueuuid.fabric.config.FabricConfig;
import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Bounded hasJoined verifier for Fabric. */
final class FabricSessionCheck {
    private static final Gson GSON = new Gson();
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
        return new SafeSessionVerifier(requests(), () -> new EndpointPolicy(FabricConfig.yggdrasilHosts()), FabricSessionCheck::parse);
    }

    private static synchronized BoundedRequestCoordinator requests() {
        if (requests == null) requests = new BoundedRequestCoordinator();
        return requests;
    }

    private static Optional<VerifiedProfile> parse(SafeSessionHttpClient.Response response) {
        if (response.status() != 200) return Optional.empty();
        HasJoinedJson dto = GSON.fromJson(response.body(), HasJoinedJson.class);
        if (dto == null || dto.id == null || dto.name == null) return Optional.empty();
        UUID uuid = parseUuid(dto.id);
        List<VerifiedProfile.Property> properties = dto.properties == null ? List.of() : dto.properties.stream()
                .filter(property -> property != null && property.name != null && property.value != null)
                .map(property -> new VerifiedProfile.Property(property.name, property.value, property.signature))
                .toList();
        return Optional.of(new VerifiedProfile(uuid, dto.name, properties));
    }

    private static UUID parseUuid(String compact) {
        if (!compact.matches("[0-9a-fA-F]{32}")) throw new IllegalArgumentException("invalid profile UUID");
        return UUID.fromString(compact.replaceFirst("(.{8})(.{4})(.{4})(.{4})(.{12})", "$1-$2-$3-$4-$5"));
    }

    private static final class HasJoinedJson {
        String id;
        String name;
        List<PropertyJson> properties;
    }

    private static final class PropertyJson {
        String name;
        String value;
        @SerializedName("signature") String signature;
    }

    private FabricSessionCheck() {}
}
