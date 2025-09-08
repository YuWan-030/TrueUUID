package cn.alini.trueuuid.server;

import java.time.Instant;
import java.util.*;

public class RecentIpGraceCache {
    private static class Rec {
        UUID premiumUuid;
        long lastSuccessAt;
    }

    private final Map<String, Rec> map = new HashMap<>();

    private static String key(String name, String ip) {
        return name.toLowerCase(Locale.ROOT) + "|" + ip;
    }

    public synchronized void record(String name, String ip, UUID premiumUuid) {
        if (ip == null || ip.isEmpty()) return;
        Rec r = new Rec();
        r.premiumUuid = premiumUuid;
        r.lastSuccessAt = Instant.now().toEpochMilli();
        map.put(key(name, ip), r);
    }

    public synchronized Optional<UUID> tryGrace(String name, String ip, int ttlSeconds) {
        if (ip == null || ip.isEmpty()) return Optional.empty();
        Rec r = map.get(key(name, ip));
        if (r == null) return Optional.empty();
        long now = Instant.now().toEpochMilli();
        if (now - r.lastSuccessAt <= ttlSeconds * 1000L) {
            return Optional.of(r.premiumUuid);
        }
        return Optional.empty();
    }

    public synchronized void cleanup(int ttlSeconds) {
        long now = Instant.now().toEpochMilli();
        map.entrySet().removeIf(e -> now - e.getValue().lastSuccessAt > ttlSeconds * 1000L);
    }

}