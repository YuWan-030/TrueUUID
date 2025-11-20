package cn.alini.trueuuid.server;

import com.google.gson.*;
import net.neoforged.fml.loading.FMLPaths;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

public class NameRegistry {
    public static class Entry {
        public UUID premiumUuid;
        public long firstVerifiedAt;
        public long lastVerifiedAt;
        public String lastSuccessIp;
    }

    private final Path file;
    private final Map<String, Entry> map = new HashMap<>();
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

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

    public synchronized void recordSuccess(String name, UUID premiumUuid, String ip) {
        String k = name.toLowerCase(Locale.ROOT);
        Entry e = map.getOrDefault(k, new Entry());
        e.premiumUuid = premiumUuid;
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
                try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                    JsonObject o = JsonParser.parseReader(r).getAsJsonObject();
                    for (String k : o.keySet()) {
                        JsonObject e = o.getAsJsonObject(k);
                        Entry en = new Entry();
                        en.premiumUuid = UUID.fromString(e.get("premiumUuid").getAsString());
                        en.firstVerifiedAt = e.get("firstVerifiedAt").getAsLong();
                        en.lastVerifiedAt = e.get("lastVerifiedAt").getAsLong();
                        if (e.has("lastSuccessIp")) en.lastSuccessIp = e.get("lastSuccessIp").getAsString();
                        map.put(k, en);
                    }
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void saveAsync() {
        new Thread(this::save, "TrueUUID-RegistrySave").start();
    }

    private synchronized void save() {
        try {
            Files.createDirectories(file.getParent());
            JsonObject o = new JsonObject();
            for (Map.Entry<String, Entry> me : map.entrySet()) {
                JsonObject e = new JsonObject();
                e.addProperty("premiumUuid", me.getValue().premiumUuid.toString());
                e.addProperty("firstVerifiedAt", me.getValue().firstVerifiedAt);
                e.addProperty("lastVerifiedAt", me.getValue().lastVerifiedAt);
                if (me.getValue().lastSuccessIp != null)
                    e.addProperty("lastSuccessIp", me.getValue().lastSuccessIp);
                o.add(me.getKey(), e);
            }
            try (Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                gson.toJson(o, w);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}