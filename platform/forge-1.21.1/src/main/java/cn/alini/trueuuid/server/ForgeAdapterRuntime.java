package cn.alini.trueuuid.server;

import cn.alini.trueuuid.protocol.BoundedRequestCoordinator;
import cn.alini.trueuuid.protocol.EndpointPolicy;
import cn.alini.trueuuid.protocol.SafeSessionHttpClient;
import cn.alini.trueuuid.protocol.SafeSessionVerifier;
import cn.alini.trueuuid.protocol.SessionVerifier;
import cn.alini.trueuuid.protocol.VerifiedProfile;
import cn.alini.trueuuid.config.TrueuuidConfig;
import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Forge-owned runtime facade for the shared safe session verifier. */
public final class ForgeAdapterRuntime {
    private static final Gson GSON = new Gson();
    private static BoundedRequestCoordinator requests;
    private static SessionVerifier verifier;

    public static synchronized void initialize() {
        if (verifier != null) return;
        requests = new BoundedRequestCoordinator();
        // Fail closed for custom endpoints until this target exposes an explicit
        // Forge config allowlist; Mojang's fixed endpoint remains available.
        verifier = new SafeSessionVerifier(requests, () -> new EndpointPolicy(TrueuuidConfig.yggdrasilHosts()), ForgeAdapterRuntime::parse);
    }

    public static synchronized SessionVerifier verifier() {
        initialize();
        return verifier;
    }

    public static synchronized void shutdown() {
        if (requests != null) requests.close();
        requests = null;
        verifier = null;
    }

    private static Optional<VerifiedProfile> parse(SafeSessionHttpClient.Response response) {
        if (response.status() != 200) return Optional.empty();
        HasJoined value = GSON.fromJson(response.body(), HasJoined.class);
        if (value == null || value.id == null || value.name == null || value.name.isBlank()) return Optional.empty();
        UUID id = parseUuid(value.id);
        List<VerifiedProfile.Property> properties = value.properties == null ? List.of() : value.properties.stream()
                .filter(property -> property != null && property.name != null && property.value != null)
                .map(property -> new VerifiedProfile.Property(property.name, property.value, property.signature)).toList();
        return Optional.of(new VerifiedProfile(id, value.name, properties));
    }

    private static UUID parseUuid(String value) {
        if (!value.matches("[0-9a-fA-F]{32}")) throw new IllegalArgumentException("invalid profile UUID");
        return UUID.fromString(value.replaceFirst("(.{8})(.{4})(.{4})(.{4})(.{12})", "$1-$2-$3-$4-$5"));
    }

    private static final class HasJoined { String id; String name; List<Property> properties; }
    private static final class Property { String name; String value; @SerializedName("signature") String signature; }
    private ForgeAdapterRuntime() {}
}
