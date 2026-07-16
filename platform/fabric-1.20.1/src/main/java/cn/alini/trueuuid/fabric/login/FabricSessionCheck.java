package cn.alini.trueuuid.fabric.login;

import cn.alini.trueuuid.protocol.BoundedRequestCoordinator;
import cn.alini.trueuuid.protocol.EndpointPolicy;
import cn.alini.trueuuid.protocol.SafeSessionHttpClient;
import cn.alini.trueuuid.protocol.SafeSessionVerifier;
import cn.alini.trueuuid.protocol.SessionVerifier;
import cn.alini.trueuuid.protocol.VerifiedProfile;
import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Mojang-only, bounded hasJoined verifier for the initial Fabric adapter. */
final class FabricSessionCheck {
    private static final Gson GSON = new Gson();
    private static BoundedRequestCoordinator requests;

    static CompletableFuture<VerifiedProfile> hasJoinedAsync(String username, String serverId) {
        return verifier().verify(new SessionVerifier.Request(username, serverId, "", ""))
                .thenApply(result -> result.orElse(null));
    }

    static synchronized void close() {
        if (requests != null) {
            requests.close();
            requests = null;
        }
    }

    private static synchronized SessionVerifier verifier() {
        if (requests == null) requests = new BoundedRequestCoordinator();
        return new SafeSessionVerifier(requests, () -> new EndpointPolicy(List.of()), FabricSessionCheck::parse);
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
