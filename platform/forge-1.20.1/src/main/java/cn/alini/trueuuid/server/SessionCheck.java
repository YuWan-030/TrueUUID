package cn.alini.trueuuid.server;

import cn.alini.trueuuid.config.TrueuuidConfig;
import cn.alini.trueuuid.protocol.EndpointPolicy;
import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class SessionCheck {
    private static final Gson GSON = new Gson();
    private static final SafeSessionHttpClient HTTP = new SafeSessionHttpClient();
    private static final URI MOJANG_HAS_JOINED = URI.create("https://sessionserver.mojang.com/session/minecraft/hasJoined");
    private static final URI MOJANG_PROFILE_BY_NAME = URI.create("https://api.mojang.com/users/profiles/minecraft/");
    private static final URI MOJANG_PROFILE_BY_UUID = URI.create("https://sessionserver.mojang.com/session/minecraft/profile/");

    public record Property(String name, String value, String signature) {}
    public record HasJoinedResult(UUID uuid, String name, List<Property> properties) {}
    private static final class HasJoinedJson { String id; String name; List<Prop> properties; }
    private static final class ProfileJson { String id; String name; }
    private static final class Prop { String name; String value; @SerializedName("signature") String sig; }

    public static CompletableFuture<Optional<HasJoinedResult>> hasJoinedAsync(
            String username, String serverId, String ip, String clientEndpoint) {
        return TrueuuidRuntime.AUTH_REQUESTS.submit(username, ip,
                serverId + "\u0000" + Optional.ofNullable(clientEndpoint).orElse(""), () -> {
            try {
                String query = "username=" + encode(username) + "&serverId=" + encode(serverId)
                        + (ip == null || ip.isBlank() ? "" : "&ip=" + encode(ip));
                SafeSessionHttpClient.Response response;
                if (clientEndpoint == null || clientEndpoint.isBlank()) {
                    response = HTTP.getTrusted(withQuery(MOJANG_HAS_JOINED, query));
                } else {
                    EndpointPolicy.ApprovedEndpoint approved = new EndpointPolicy(TrueuuidConfig.apiRootWhitelist())
                            .approveClientEndpoint(clientEndpoint);
                    response = HTTP.get(withQuery(approved.uri(), query), approved.addresses());
                }
                return parseHasJoined(response);
            } catch (Exception ex) {
                if (TrueuuidConfig.debug()) System.out.println("[TrueUUID][DEBUG] session verification failed: " + ex.getMessage());
                return Optional.empty();
            }
        });
    }

    public static CompletableFuture<Optional<HasJoinedResult>> lookupMojangProfileAsync(String username) {
        return TrueuuidRuntime.AUTH_REQUESTS.submit(username, "mojang-profile", "profile", () -> {
            try {
                SafeSessionHttpClient.Response nameResponse = HTTP.getTrusted(
                        URI.create(MOJANG_PROFILE_BY_NAME + encode(username)));
                if (nameResponse.status() != 200) return Optional.empty();
                ProfileJson profile = GSON.fromJson(nameResponse.body(), ProfileJson.class);
                if (profile == null || profile.id == null || profile.name == null) return Optional.empty();
                SafeSessionHttpClient.Response textureResponse = HTTP.getTrusted(
                        URI.create(MOJANG_PROFILE_BY_UUID + profile.id + "?unsigned=false"));
                return parseHasJoined(textureResponse);
            } catch (Exception ex) {
                return Optional.empty();
            }
        });
    }

    public static CompletableFuture<Integer> probeMojangAsync() {
        return TrueuuidRuntime.AUTH_REQUESTS.submit("__probe__", "mojang", "probe", () ->
                HTTP.getTrusted(URI.create(MOJANG_HAS_JOINED + "?username=Mojang&serverId=test")).status());
    }

    private static Optional<HasJoinedResult> parseHasJoined(SafeSessionHttpClient.Response response) {
        if (response.status() != 200) return Optional.empty();
        HasJoinedJson dto = GSON.fromJson(response.body(), HasJoinedJson.class);
        if (dto == null || dto.id == null || dto.name == null) return Optional.empty();
        UUID uuid = parseUuid(dto.id);
        List<Property> properties = dto.properties == null ? List.of() : dto.properties.stream()
                .filter(p -> p != null && p.name != null && p.value != null)
                .map(p -> new Property(p.name, p.value, p.sig)).toList();
        return Optional.of(new HasJoinedResult(uuid, dto.name, properties));
    }

    private static URI withQuery(URI base, String query) throws Exception {
        return new URI("https", null, base.getHost(), base.getPort(), base.getPath(), query, null);
    }

    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    private static UUID parseUuid(String id) {
        if (!id.matches("[0-9a-fA-F]{32}")) throw new IllegalArgumentException("invalid profile UUID");
        return UUID.fromString(id.replaceFirst("(.{8})(.{4})(.{4})(.{4})(.{12})", "$1-$2-$3-$4-$5"));
    }
    private SessionCheck() {}
}
