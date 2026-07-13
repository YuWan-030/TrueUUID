package cn.alini.trueuuid.server;

import cn.alini.trueuuid.config.TrueuuidConfig;
import cn.alini.trueuuid.protocol.EndpointPolicy;
import cn.alini.trueuuid.protocol.SafeSessionHttpClient;
import cn.alini.trueuuid.protocol.SafeSessionVerifier;
import cn.alini.trueuuid.protocol.SessionVerifier;
import cn.alini.trueuuid.protocol.VerifiedProfile;
import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

import java.net.URI;
import java.net.URLEncoder;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class SessionCheck {
    private static final Gson GSON = new Gson();
    private static final SafeSessionHttpClient HTTP = new SafeSessionHttpClient();
    private static final URI MOJANG_HAS_JOINED = URI.create("https://sessionserver.mojang.com/session/minecraft/hasJoined");

    private static final class HasJoinedJson { String id; String name; List<Prop> properties; }
    private static final class Prop { String name; String value; @SerializedName("signature") String sig; }

    public static CompletableFuture<Optional<VerifiedProfile>> hasJoinedAsync(
            String username, String serverId, String ip, String clientEndpoint) {
        SessionVerifier verifier = new SafeSessionVerifier(TrueuuidRuntime.AUTH_REQUESTS,
                () -> new EndpointPolicy(TrueuuidConfig.apiRootWhitelist()), SessionCheck::parseHasJoined);
        return verifier.verify(new SessionVerifier.Request(username, serverId, ip, clientEndpoint));
    }

    public static CompletableFuture<Integer> probeMojangAsync() {
        return TrueuuidRuntime.AUTH_REQUESTS.submit("__probe__", "mojang", "probe", () ->
                HTTP.getTrusted(URI.create(MOJANG_HAS_JOINED + "?username=Mojang&serverId=test")).status());
    }

    private static Optional<VerifiedProfile> parseHasJoined(SafeSessionHttpClient.Response response) {
        if (TrueuuidConfig.debug()) {
            // Status and bounded body size diagnose propagation without
            // exposing the username, nonce, endpoint, or profile payload.
            cn.alini.trueuuid.Trueuuid.LOGGER.info("[TrueUUID] hasJoined response: status={}, bytes={}",
                    response.status(), response.body().length());
        }
        if (response.status() != 200) return Optional.empty();
        HasJoinedJson dto = GSON.fromJson(response.body(), HasJoinedJson.class);
        if (dto == null || dto.id == null || dto.name == null) return Optional.empty();
        UUID uuid = parseUuid(dto.id);
        List<VerifiedProfile.Property> properties = dto.properties == null ? List.of() : dto.properties.stream()
                .filter(p -> p != null && p.name != null && p.value != null)
                .map(p -> new VerifiedProfile.Property(p.name, p.value, p.sig)).toList();
        return Optional.of(new VerifiedProfile(uuid, dto.name, properties));
    }

    private static URI withQuery(URI base, String query) throws Exception {
        return new URI("https", null, base.getHost(), base.getPort(), base.getPath(), query, null);
    }

    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }

    static String publicClientIpOrEmpty(String ip) {
        if (ip == null || ip.isBlank()) return "";
        try {
            InetAddress address = InetAddress.getByName(ip);
            return EndpointPolicy.isPublic(address) ? address.getHostAddress() : "";
        } catch (Exception ignored) {
            return "";
        }
    }
    private static UUID parseUuid(String id) {
        if (!id.matches("[0-9a-fA-F]{32}")) throw new IllegalArgumentException("invalid profile UUID");
        return UUID.fromString(id.replaceFirst("(.{8})(.{4})(.{4})(.{4})(.{12})", "$1-$2-$3-$4-$5"));
    }
    private SessionCheck() {}
}
