package cn.alini.trueuuid.protocol;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Bounded, loader-neutral store for names that may not silently fall back to
 * offline UUIDs. Loaders provide only the path to their configuration folder.
 */
public final class PersistentVerifiedNameStore implements AutoCloseable {
    static final int MAX_ENTRIES = 100_000;
    static final long MAX_FILE_BYTES = 64L * 1024L * 1024L;

    private final Path file;
    private final Map<String, UUID> entries = new LinkedHashMap<>();
    private final ExecutorService writer = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "TrueUUID-RegistryWriter");
        thread.setDaemon(false);
        return thread;
    });
    private final Gson gson = new Gson();
    private boolean closed;
    private boolean writeQueued;
    private boolean dirty;

    public PersistentVerifiedNameStore(Path file) {
        this.file = Objects.requireNonNull(file, "file");
        load();
    }

    public synchronized boolean contains(String name) {
        return name != null && entries.containsKey(name.toLowerCase(Locale.ROOT));
    }

    public synchronized Optional<UUID> premiumUuid(String name) {
        return name == null ? Optional.empty()
                : Optional.ofNullable(entries.get(name.toLowerCase(Locale.ROOT)));
    }

    public synchronized void record(String name, UUID uuid) {
        if (closed || name == null || name.isBlank() || uuid == null) return;
        String key = name.toLowerCase(Locale.ROOT);
        if (!entries.containsKey(key) && entries.size() >= MAX_ENTRIES) return;
        entries.put(key, uuid);
        dirty = true;
        if (!writeQueued) {
            writeQueued = true;
            writer.execute(this::drainWrites);
        }
    }

    private void load() {
        try {
            if (!Files.exists(file) || Files.size(file) > MAX_FILE_BYTES) return;
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                JsonObject saved = JsonParser.parseReader(reader).getAsJsonObject();
                for (Map.Entry<String, JsonElement> entry : saved.entrySet()) {
                    if (entries.size() >= MAX_ENTRIES || entry.getKey() == null || entry.getValue() == null) break;
                    JsonElement value = entry.getValue();
                    String uuid = value.isJsonObject() && value.getAsJsonObject().has("premiumUuid")
                            ? value.getAsJsonObject().get("premiumUuid").getAsString()
                            : value.getAsString();
                    entries.put(entry.getKey().toLowerCase(Locale.ROOT), UUID.fromString(uuid));
                }
            }
        } catch (Exception ignored) {
            entries.clear();
        }
    }

    private void drainWrites() {
        while (true) {
            Map<String, UUID> snapshot;
            synchronized (this) {
                if (!dirty) {
                    writeQueued = false;
                    return;
                }
                dirty = false;
                snapshot = new LinkedHashMap<>(entries);
            }
            try {
                Files.createDirectories(file.getParent());
                Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
                try (Writer output = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                    gson.toJson(compatibilitySnapshot(snapshot), output);
                }
                try {
                    Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING,
                            StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException ignored) {
                    Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (Exception ignored) {
                // Keep the in-memory policy; a later verified login retries persistence.
            }
        }
    }

    private static JsonObject compatibilitySnapshot(Map<String, UUID> entries) {
        JsonObject root = new JsonObject();
        long now = System.currentTimeMillis();
        entries.forEach((name, uuid) -> {
            JsonObject entry = new JsonObject();
            entry.addProperty("premiumUuid", uuid.toString());
            entry.addProperty("firstVerifiedAt", now);
            entry.addProperty("lastVerifiedAt", now);
            entry.addProperty("authSource", "MOJANG");
            entry.addProperty("authDisplayName", "Mojang");
            root.add(name, entry);
        });
        return root;
    }

    @Override
    public void close() {
        synchronized (this) {
            if (closed) return;
            closed = true;
            if (dirty && !writeQueued) {
                writeQueued = true;
                writer.execute(this::drainWrites);
            }
        }
        writer.shutdown();
    }
}
