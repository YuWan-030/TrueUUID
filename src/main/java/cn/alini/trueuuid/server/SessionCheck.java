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

    /**
     * 异步版本：不阻塞调用线程，返回 CompletableFuture\<Optional\<HasJoinedResult\>\>
     */
    public static CompletableFuture<Optional<HasJoinedResult>> hasJoinedAsync(String username, String serverId, String ip) {
        String url = TrueuuidConfig.COMMON.mojangReverseProxy.get()+"/session/minecraft/hasJoined"
                + "?username=" + URLEncoder.encode(username, StandardCharsets.UTF_8)
                + "&serverId=" + URLEncoder.encode(serverId, StandardCharsets.UTF_8);

        if (cn.alini.trueuuid.config.TrueuuidConfig.debug()) {
            System.out.println("[TrueUUID][DEBUG] 请求 Mojang 校验接口: " + url);
        }

        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).GET().build();

        return HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                .thenApply(resp -> {
                    if (cn.alini.trueuuid.config.TrueuuidConfig.debug()) {
                        System.out.println("[TrueUUID][DEBUG] Mojang 响应状态码: " + resp.statusCode());
                        System.out.println("[TrueUUID][DEBUG] Mojang 响应内容: " + resp.body());
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

                    UUID uuid = UUID.fromString(dto.id.replaceFirst(
                            "(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{12})",
                            "$1-$2-$3-$4-$5"));

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
                        System.out.println("[TrueUUID][DEBUG] 与 Mojang 通信或解析时发生异常: " + ex);
                    }
                    return Optional.empty();
                });
    }

    // 保留同步方法（若需要）或移除
    public static Optional<HasJoinedResult> hasJoined(String username, String serverId, String ip) throws Exception {
        // 保留原同步实现（或内部调用 hasJoinedAsync().get()，视需要）
        throw new UnsupportedOperationException("同步 hasJoined 已不推荐使用，请使用 hasJoinedAsync");
    }

    private SessionCheck() {}
}
