// Java
package cn.alini.trueuuid.server;

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

/**
 * 服务端调用 hasJoined 校验正版并获取最终 UUID 与皮肤属性
 */
public final class SessionCheck {
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final Gson GSON = new Gson();

    public record Property(String name, String value, String signature) {}

    public record HasJoinedResult(UUID uuid, String name, List<Property> properties) {}

    private static class HasJoinedJson {
        String id; // 无连字符的 UUID
        String name;
        List<Prop> properties;
    }
    private static class Prop {
        String name;
        String value;
        @SerializedName("signature")
        String sig;
    }

    public static Optional<HasJoinedResult> hasJoined(String username, String serverId, String ip) throws Exception {
        String url = "https://sessionserver.mojang.com/session/minecraft/hasJoined"
                + "?username=" + URLEncoder.encode(username, StandardCharsets.UTF_8)
                + "&serverId=" + URLEncoder.encode(serverId, StandardCharsets.UTF_8)
                + (ip != null && !ip.isEmpty() ? "&ip=" + URLEncoder.encode(ip, StandardCharsets.UTF_8) : "");

        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).GET().build();
        HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());

        if (resp.statusCode() != 200) return Optional.empty();

        HasJoinedJson dto = GSON.fromJson(resp.body(), HasJoinedJson.class);
        if (dto == null || dto.id == null) return Optional.empty();

        UUID uuid = UUID.fromString(dto.id.replaceFirst(
                "(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{12})",
                "$1-$2-$3-$4-$5"));

        List<Property> props = dto.properties == null ? List.of() :
                dto.properties.stream()
                        .map(p -> new Property(p.name, p.value, p.sig))
                        .toList();

        return Optional.of(new HasJoinedResult(uuid, dto.name, props));
    }

}
