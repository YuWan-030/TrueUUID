// java
package cn.alini.trueuuid.server;

import cn.alini.trueuuid.config.TrueuuidConfig;
import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * 服务端调用 hasJoined 校验正版并获取最终 UUID 与皮肤属性
 */
public final class SessionCheck {
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final Gson GSON = new Gson();
    private static final String MOJANG_HAS_JOINED = mojangUrl("sessionserver", "/session/minecraft/hasJoined");
    private static final String MOJANG_PROFILE_BY_NAME = mojangUrl("api", "/users/profiles/minecraft/");
    private static final String MOJANG_PROFILE_BY_UUID = mojangUrl("sessionserver", "/session/minecraft/profile/");

    public record Property(String name, String value, String signature) {}

    public record HasJoinedResult(UUID uuid, String name, List<Property> properties) {}

    private static class HasJoinedJson {
        String id; // 无连字符的 UUID
        String name;
        List<Prop> properties;
    }
    private static class ProfileJson {
        String id;
        String name;
    }
    private static class Prop {
        String name;
        String value;
        @SerializedName("signature")
        String sig;
    }

    /**
     * 异步版本：不阻塞调用线程，返回 CompletableFuture\<Optional\<HasJoinedResult\>\>
     */
    public static CompletableFuture<Optional<HasJoinedResult>> hasJoinedAsync(String username, String serverId, String ip) {
        return hasJoinedAsync(username, serverId, ip, "");
    }

    public static CompletableFuture<Optional<HasJoinedResult>> hasJoinedAsync(String username, String serverId, String ip, String hasJoinedBaseUrl) {
        String baseUrl = hasJoinedBaseUrl == null || hasJoinedBaseUrl.isBlank()
                ? trueuuid$mojangHasJoinedUrl()
                : hasJoinedBaseUrl.trim();
        String url = baseUrl
                + "?username=" + URLEncoder.encode(username, StandardCharsets.UTF_8)
                + "&serverId=" + URLEncoder.encode(serverId, StandardCharsets.UTF_8);

        if (cn.alini.trueuuid.config.TrueuuidConfig.debug()) {
            System.out.println("[TrueUUID][DEBUG] 请求会话校验接口: " + url + (hasJoinedBaseUrl == null || hasJoinedBaseUrl.isBlank() ? " (Mojang)" : " (自定义)"));
        }

        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).GET().build();

        return HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                .thenApply(resp -> {
                    if (cn.alini.trueuuid.config.TrueuuidConfig.debug()) {
                        System.out.println("[TrueUUID][DEBUG] 响应状态码: " + resp.statusCode());
                        System.out.println("[TrueUUID][DEBUG] 响应内容: " + resp.body());
                    }

                    if (resp.statusCode() != 200) {
                        if (cn.alini.trueuuid.config.TrueuuidConfig.debug()) {
                            System.out.println("[TrueUUID][DEBUG] 校验失败，状态码非200，返回空");
                        }
                        return Optional.<HasJoinedResult>empty();
                    }

                    HasJoinedJson dto = GSON.fromJson(resp.body(), HasJoinedJson.class);
                    if (dto == null || dto.id == null) {
                        if (cn.alini.trueuuid.config.TrueuuidConfig.debug()) {
                            System.out.println("[TrueUUID][DEBUG] 解析JSON失败或未获取到UUID，返回空");
                        }
                        return Optional.<HasJoinedResult>empty();
                    }

                    UUID uuid = parseUuid(dto.id);

                    if (cn.alini.trueuuid.config.TrueuuidConfig.debug()) {
                        System.out.println("[TrueUUID][DEBUG] 校验成功，UUID: " + uuid + "，玩家名: " + dto.name);
                    }

                    List<Property> props = dto.properties == null ? List.of() :
                            dto.properties.stream()
                                    .map(p -> new Property(p.name, p.value, p.sig))
                                    .toList();

                    return Optional.of(new HasJoinedResult(uuid, dto.name, props));
                })
                .exceptionally(ex -> {
                    if (cn.alini.trueuuid.config.TrueuuidConfig.debug()) {
                        System.out.println("[TrueUUID][DEBUG] 与会话服务通信或解析时发生异常: " + ex);
                    }
                    return Optional.empty();
                });
    }

    public static CompletableFuture<Optional<HasJoinedResult>> lookupMojangProfileAsync(String username) {
        String nameUrl = MOJANG_PROFILE_BY_NAME + URLEncoder.encode(username, StandardCharsets.UTF_8);
        HttpRequest nameReq = HttpRequest.newBuilder(URI.create(nameUrl)).GET().build();

        return HTTP.sendAsync(nameReq, HttpResponse.BodyHandlers.ofString())
                .thenCompose(nameResp -> {
                    if (nameResp.statusCode() != 200) {
                        return CompletableFuture.completedFuture(Optional.<HasJoinedResult>empty());
                    }

                    ProfileJson profile = GSON.fromJson(nameResp.body(), ProfileJson.class);
                    if (profile == null || profile.id == null || profile.name == null) {
                        return CompletableFuture.completedFuture(Optional.<HasJoinedResult>empty());
                    }

                    String textureUrl = MOJANG_PROFILE_BY_UUID + profile.id + "?unsigned=false";
                    HttpRequest textureReq = HttpRequest.newBuilder(URI.create(textureUrl)).GET().build();
                    return HTTP.sendAsync(textureReq, HttpResponse.BodyHandlers.ofString())
                            .thenApply(textureResp -> {
                                if (textureResp.statusCode() != 200) {
                                    return Optional.<HasJoinedResult>empty();
                                }

                                HasJoinedJson dto = GSON.fromJson(textureResp.body(), HasJoinedJson.class);
                                if (dto == null || dto.id == null || dto.name == null) {
                                    return Optional.<HasJoinedResult>empty();
                                }

                                UUID uuid = parseUuid(dto.id);
                                List<Property> props = dto.properties == null ? List.of() :
                                        dto.properties.stream()
                                                .map(p -> new Property(p.name, p.value, p.sig))
                                                .toList();
                                return Optional.of(new HasJoinedResult(uuid, dto.name, props));
                            });
                })
                .exceptionally(ex -> Optional.empty());
    }

    private static String trueuuid$mojangHasJoinedUrl() {
        String reverseProxy = TrueuuidConfig.mojangReverseProxy();
        if (reverseProxy == null || reverseProxy.isBlank() || "https://sessionserver.mojang.com".equals(reverseProxy)) {
            return MOJANG_HAS_JOINED;
        }
        String root = reverseProxy.endsWith("/") ? reverseProxy.substring(0, reverseProxy.length() - 1) : reverseProxy;
        return root + "/session/minecraft/hasJoined";
    }

    // 保留同步方法（若需要）或移除
    public static Optional<HasJoinedResult> hasJoined(String username, String serverId, String ip) throws Exception {
        // 保留原同步实现（或内部调用 hasJoinedAsync().get()，视需要）
        throw new UnsupportedOperationException("同步 hasJoined 已不推荐使用，请使用 hasJoinedAsync");
    }

    private SessionCheck() {}

    private static String mojangUrl(String service, String path) {
        return "https://" + service + "." + "mo" + "jang" + ".com" + path;
    }

    private static UUID parseUuid(String id) {
        return UUID.fromString(id.replaceFirst(
                "(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{12})",
                "$1-$2-$3-$4-$5"));
    }
}
