package cn.alini.trueuuid.protocol;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/** Short-lived, same-IP reconnect grace abstraction. */
public interface GraceCache {
    record Entry(UUID uuid, AuthSource source, String displayName) {}

    void record(String name, String clientIp, Entry entry);
    void activateAfterLogout(String name, String clientIp);
    Optional<Entry> consume(String name, String clientIp, Duration ttl);
}
