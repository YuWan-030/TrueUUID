package cn.alini.trueuuid.server;

import net.minecraft.network.Connection;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 在登录阶段记录“放行离线”的连接；玩家完全进服后消费并提示。
 * (Record "offline fallback" connections during login phase; consume and notify after player fully joins server.)
 */
public final class AuthState {
    public enum FallbackReason { TIMEOUT, FAILURE }

    public enum AuthSource { MOJANG, YGGDRASIL }

    public record AuthSuccess(AuthSource source, String displayName) {}

    private static final ConcurrentHashMap<Connection, FallbackReason> OFFLINE_FALLBACK = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Connection, AuthSuccess> AUTH_SUCCESS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, AuthSuccess> AUTH_SUCCESS_BY_PROFILE = new ConcurrentHashMap<>();

    public static void markOfflineFallback(Connection conn, FallbackReason reason) {
        if (conn != null && reason != null) {
            OFFLINE_FALLBACK.put(conn, reason);
            AUTH_SUCCESS.remove(conn);
        }
    }

    public static void markAuthSuccess(Connection conn, AuthSource source, String displayName) {
        if (conn != null && source != null) {
            String normalizedName = displayName == null || displayName.isBlank() ? source.name() : displayName;
            AUTH_SUCCESS.put(conn, new AuthSuccess(source, normalizedName));
            OFFLINE_FALLBACK.remove(conn);
        }
    }

    public static void markAuthSuccess(Connection conn, UUID uuid, String name, AuthSource source, String displayName) {
        markAuthSuccess(conn, source, displayName);
        if (source == null) return;
        String normalizedName = displayName == null || displayName.isBlank() ? source.name() : displayName;
        AuthSuccess success = new AuthSuccess(source, normalizedName);
        if (uuid != null) {
            AUTH_SUCCESS_BY_PROFILE.put("uuid:" + uuid, success);
        }
        if (name != null && !name.isBlank()) {
            AUTH_SUCCESS_BY_PROFILE.put("name:" + name.toLowerCase(Locale.ROOT), success);
        }
    }

    public static Optional<FallbackReason> consume(Connection conn) {
        if (conn == null) return Optional.empty();
        FallbackReason r = OFFLINE_FALLBACK.remove(conn);
        return Optional.ofNullable(r);
    }

    public static Optional<AuthSuccess> consumeAuthSuccess(Connection conn) {
        if (conn == null) return Optional.empty();
        AuthSuccess success = AUTH_SUCCESS.remove(conn);
        return Optional.ofNullable(success);
    }

    public static Optional<AuthSuccess> consumeAuthSuccess(Connection conn, UUID uuid, String name) {
        Optional<AuthSuccess> byConnection = consumeAuthSuccess(conn);
        if (byConnection.isPresent()) return byConnection;
        if (uuid != null) {
            AuthSuccess success = AUTH_SUCCESS_BY_PROFILE.remove("uuid:" + uuid);
            if (success != null) return Optional.of(success);
        }
        if (name != null && !name.isBlank()) {
            AuthSuccess success = AUTH_SUCCESS_BY_PROFILE.remove("name:" + name.toLowerCase(Locale.ROOT));
            if (success != null) return Optional.of(success);
        }
        return Optional.empty();
    }

    private AuthState() {}
}
