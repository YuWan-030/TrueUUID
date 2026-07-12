package cn.alini.trueuuid.server;

import com.google.gson.*;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.Reader;
import java.io.Writer;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

public class NameRegistry implements AutoCloseable {
    private static final int MAX_ENTRIES = 100_000;
    private static final long MAX_REGISTRY_BYTES = 64L * 1024L * 1024L;
    public static class Entry {
        public UUID premiumUuid;
        public long firstVerifiedAt;
        public long lastVerifiedAt;
        public String lastSuccessIp;
        public AuthState.AuthSource authSource;
        public String authDisplayName;
    }

    private final Path file;
    private final Map<String, Entry> map = new HashMap<>();
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final java.util.concurrent.ThreadPoolExecutor writer = new java.util.concurrent.ThreadPoolExecutor(
            1, 1, 0, java.util.concurrent.TimeUnit.MILLISECONDS,
            new java.util.concurrent.ArrayBlockingQueue<>(1), r -> {
        Thread t = new Thread(r, "TrueUUID-RegistryWriter");
        t.setDaemon(true);
        return t;
    }, new java.util.concurrent.ThreadPoolExecutor.AbortPolicy());
    private final java.util.concurrent.atomic.AtomicBoolean writeScheduled = new java.util.concurrent.atomic.AtomicBoolean();
    private final java.util.concurrent.atomic.AtomicBoolean dirty = new java.util.concurrent.atomic.AtomicBoolean();
    private volatile boolean closed;

    public NameRegistry() {
        this.file = FMLPaths.CONFIGDIR.get().resolve("trueuuid-registry.json");
        load();
    }

    public synchronized Optional<UUID> getPremiumUuid(String name) {
        Entry e = map.get(name.toLowerCase(Locale.ROOT));
        return e == null ? Optional.empty() : Optional.ofNullable(e.premiumUuid);
    }

    public synchronized boolean isKnownPremiumName(String name) {
        return map.containsKey(name.toLowerCase(Locale.ROOT));
    }

    public synchronized AuthState.AuthSource getAuthSource(String name) {
        Entry e = map.get(name.toLowerCase(Locale.ROOT));
        return e == null || e.authSource == null ? AuthState.AuthSource.MOJANG : e.authSource;
    }

    public synchronized String getAuthDisplayName(String name) {
        Entry e = map.get(name.toLowerCase(Locale.ROOT));
        if (e == null || e.authDisplayName == null || e.authDisplayName.isBlank()) {
            return getAuthSource(name) == AuthState.AuthSource.YGGDRASIL ? "Yggdrasil skin site" : "Mojang";
        }
        return e.authDisplayName;
    }

    public synchronized void recordSuccess(String name, UUID premiumUuid, String ip) {
        recordSuccess(name, premiumUuid, ip, AuthState.AuthSource.MOJANG, "Mojang");
    }

    public synchronized void recordSuccess(String name, UUID premiumUuid, String ip, AuthState.AuthSource source, String displayName) {
        String k = name.toLowerCase(Locale.ROOT);
        Entry e = map.get(k);
        if (e == null) {
            if (map.size() >= MAX_ENTRIES) throw new IllegalStateException("TrueUUID registry entry limit reached");
            e = new Entry();
        }
        e.premiumUuid = premiumUuid;
        e.authSource = source != null ? source : AuthState.AuthSource.MOJANG;
        e.authDisplayName = displayName == null || displayName.isBlank() ? e.authSource.name() : displayName;
        long now = Instant.now().toEpochMilli();
        if (e.firstVerifiedAt == 0) e.firstVerifiedAt = now;
        e.lastVerifiedAt = now;
        e.lastSuccessIp = ip;
        map.put(k, e);
        saveAsync();
    }

    private void load() {
        try {
            if (Files.exists(file)) {
                if (Files.size(file) > MAX_REGISTRY_BYTES) throw new IOException("TrueUUID registry is too large");
                try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                    JsonObject o = JsonParser.parseReader(r).getAsJsonObject();
                    for (String k : o.keySet()) {
                        JsonObject e = o.getAsJsonObject(k);
                        Entry en = new Entry();
                        en.premiumUuid = UUID.fromString(e.get("premiumUuid").getAsString());
                        en.firstVerifiedAt = e.get("firstVerifiedAt").getAsLong();
                        en.lastVerifiedAt = e.get("lastVerifiedAt").getAsLong();
                        if (e.has("lastSuccessIp")) en.lastSuccessIp = e.get("lastSuccessIp").getAsString();
                        if (e.has("authSource")) {
                            try {
                                en.authSource = AuthState.AuthSource.valueOf(e.get("authSource").getAsString());
                            } catch (IllegalArgumentException ignored) {
                                en.authSource = AuthState.AuthSource.MOJANG;
                            }
                        }
                        if (e.has("authDisplayName")) en.authDisplayName = e.get("authDisplayName").getAsString();
                        if (map.size() >= MAX_ENTRIES) throw new IOException("TrueUUID registry exceeds " + MAX_ENTRIES + " entries");
                        map.put(k, en);
                    }
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void saveAsync() {
        if (closed) return;
        dirty.set(true);
        if (writeScheduled.compareAndSet(false, true)) writer.execute(this::drainWrites);
    }

    private void drainWrites() {
        try {
            while (dirty.getAndSet(false)) save();
        } finally {
            writeScheduled.set(false);
            if (dirty.get() && !closed && writeScheduled.compareAndSet(false, true)) writer.execute(this::drainWrites);
        }
    }

    private void save() {
        try {
            Files.createDirectories(file.getParent());
            JsonObject o = snapshotJson();
            Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
            try (Writer w = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                gson.toJson(o, w);
            }
            try {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private synchronized JsonObject snapshotJson() {
        JsonObject o = new JsonObject();
        for (Map.Entry<String, Entry> me : map.entrySet()) {
                JsonObject e = new JsonObject();
                e.addProperty("premiumUuid", me.getValue().premiumUuid.toString());
                e.addProperty("firstVerifiedAt", me.getValue().firstVerifiedAt);
                e.addProperty("lastVerifiedAt", me.getValue().lastVerifiedAt);
                if (me.getValue().lastSuccessIp != null)
                    e.addProperty("lastSuccessIp", me.getValue().lastSuccessIp);
                if (me.getValue().authSource != null)
                    e.addProperty("authSource", me.getValue().authSource.name());
                if (me.getValue().authDisplayName != null)
                    e.addProperty("authDisplayName", me.getValue().authDisplayName);
                o.add(me.getKey(), e);
        }
        return o;
    }

    @Override public void close() {
        closed = true;
        dirty.set(true);
        if (writeScheduled.compareAndSet(false, true)) writer.execute(this::drainWrites);
        writer.shutdown();
        try {
            if (!writer.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) writer.shutdownNow();
        } catch (InterruptedException ex) {
            writer.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
