package cn.alini.trueuuid.protocol;

import java.util.Optional;
import java.util.UUID;

/** Persistent verified-name binding, implemented by each platform's storage adapter. */
public interface VerifiedNameRegistry {
    record Entry(UUID uuid, AuthSource source, String displayName) {}

    Optional<Entry> find(String name);
    void record(String name, Entry entry, String clientIp);
}
