package cn.alini.trueuuid.server;

import cn.alini.trueuuid.protocol.ExpiringBoundedStore;
import net.minecraft.network.Connection;

import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * 在登录阶段记录“放行离线”的连接；玩家完全进服后消费并提示。
 * (Record "offline fallback" connections during login phase; consume and notify after player fully joins server.)
 */
public final class AuthState implements AutoCloseable {
    public enum FallbackReason { TIMEOUT, FAILURE }

    public enum AuthSource { MOJANG, YGGDRASIL }

    public record AuthSuccess(AuthSource source, String displayName) {}

    private static final int MAX_LOGIN_STATES = 4096;
    private static final Duration LOGIN_STATE_TTL = Duration.ofMinutes(5);
    private final ExpiringBoundedStore<Connection, FallbackReason> offlineFallback =
            new ExpiringBoundedStore<>(MAX_LOGIN_STATES, LOGIN_STATE_TTL);
    private final ExpiringBoundedStore<Connection, AuthSuccess> authSuccess =
            new ExpiringBoundedStore<>(MAX_LOGIN_STATES, LOGIN_STATE_TTL);
    private final ExpiringBoundedStore<String, AuthSuccess> authSuccessByProfile =
            new ExpiringBoundedStore<>(MAX_LOGIN_STATES * 2, LOGIN_STATE_TTL);

    public void markOfflineFallback(Connection conn, FallbackReason reason) {
        if (conn != null && reason != null) {
            offlineFallback.put(conn, reason);
            authSuccess.remove(conn);
        }
    }

    public void markAuthSuccess(Connection conn, AuthSource source, String displayName) {
        if (conn != null && source != null) {
            String normalizedName = displayName == null || displayName.isBlank() ? source.name() : displayName;
            authSuccess.put(conn, new AuthSuccess(source, normalizedName));
            offlineFallback.remove(conn);
        }
    }

    public void markAuthSuccess(Connection conn, UUID uuid, String name, AuthSource source, String displayName) {
        markAuthSuccess(conn, source, displayName);
        if (source == null) return;
        String normalizedName = displayName == null || displayName.isBlank() ? source.name() : displayName;
        AuthSuccess success = new AuthSuccess(source, normalizedName);
        if (uuid != null) {
            authSuccessByProfile.put("uuid:" + uuid, success);
        }
        if (name != null && !name.isBlank()) {
            authSuccessByProfile.put("name:" + name.toLowerCase(Locale.ROOT), success);
        }
    }

    public Optional<FallbackReason> consume(Connection conn) {
        if (conn == null) return Optional.empty();
        return offlineFallback.remove(conn);
    }

    public Optional<AuthSuccess> consumeAuthSuccess(Connection conn) {
        if (conn == null) return Optional.empty();
        return authSuccess.remove(conn);
    }

    public Optional<AuthSuccess> consumeAuthSuccess(Connection conn, UUID uuid, String name) {
        Optional<AuthSuccess> byConnection = consumeAuthSuccess(conn);
        if (byConnection.isPresent()) return byConnection;
        if (uuid != null) {
            Optional<AuthSuccess> success = authSuccessByProfile.remove("uuid:" + uuid);
            if (success.isPresent()) return success;
        }
        if (name != null && !name.isBlank()) {
            Optional<AuthSuccess> success = authSuccessByProfile.remove("name:" + name.toLowerCase(Locale.ROOT));
            if (success.isPresent()) return success;
        }
        return Optional.empty();
    }

    public void remove(Connection connection) {
        if (connection == null) return;
        offlineFallback.remove(connection);
        authSuccess.remove(connection);
    }

    @Override public void close() {
        offlineFallback.close();
        authSuccess.close();
        authSuccessByProfile.close();
    }
}
