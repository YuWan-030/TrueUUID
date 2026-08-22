package cn.alini.trueuuid.protocol;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import java.io.IOException;
import java.io.Reader;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/** Strict durable ownership state for hybrid premium/offline identities. */
public final class PersistentHybridIdentityStore {
    public static final int SCHEMA_VERSION = 1;
    public static final int MAX_ENTRIES = 100_000;
    public static final long MAX_FILE_BYTES = 32L * 1024L * 1024L;

    public enum Authority {
        MOJANG,
        ALLOWLISTED_YGGDRASIL,
        OFFLINE_AUTH,
        OFFLINE_NAME_ONLY,
        TRUEUUID_CLIENT_GATE;

        boolean isOffline() {
            return this == OFFLINE_AUTH || this == OFFLINE_NAME_ONLY || this == TRUEUUID_CLIENT_GATE;
        }
    }

    public record Entry(
            HybridIdentityPolicy.StoredIdentity identity,
            UUID uuid,
            String canonicalName,
            Authority authority,
            long firstVerifiedAt,
            long lastVerifiedAt
    ) {
        public Entry {
            Objects.requireNonNull(identity, "identity");
            if (identity == HybridIdentityPolicy.StoredIdentity.UNKNOWN) {
                throw new IllegalArgumentException("UNKNOWN identities are not persisted");
            }
            Objects.requireNonNull(uuid, "uuid");
            canonicalName = requireMinecraftName(canonicalName);
            Objects.requireNonNull(authority, "authority");
            if (identity == HybridIdentityPolicy.StoredIdentity.OFFLINE_ENROLLED
                    && !authority.isOffline()) {
                throw new IllegalArgumentException("offline identity requires an offline authority");
            }
            if (identity == HybridIdentityPolicy.StoredIdentity.PREMIUM_LOCKED
                    && authority.isOffline()) {
                throw new IllegalArgumentException("premium identity cannot use an offline authority");
            }
            if (firstVerifiedAt < 0 || lastVerifiedAt < firstVerifiedAt) {
                throw new IllegalArgumentException("invalid verification timestamps");
            }
        }
    }

    private static final Set<String> ROOT_FIELDS = Set.of("schemaVersion", "entries");
    private static final Set<String> ENTRY_FIELDS = Set.of(
            "identity", "uuid", "canonicalName", "authority", "firstVerifiedAt", "lastVerifiedAt");

    private final Path file;
    private final Clock clock;
    private final Map<String, Entry> entries;

    public PersistentHybridIdentityStore(Path file) throws IOException {
        this(file, Clock.systemUTC());
    }

    PersistentHybridIdentityStore(Path file, Clock clock) throws IOException {
        this.file = Objects.requireNonNull(file, "file").toAbsolutePath().normalize();
        this.clock = Objects.requireNonNull(clock, "clock");
        this.entries = load(this.file);
    }

    public synchronized HybridIdentityPolicy.StoredIdentity classification(String name) {
        return find(name).map(Entry::identity).orElse(HybridIdentityPolicy.StoredIdentity.UNKNOWN);
    }

    public synchronized Optional<Entry> find(String name) {
        if (name == null) return Optional.empty();
        return Optional.ofNullable(entries.get(normalizeName(name)));
    }

    public synchronized Map<String, Entry> snapshot() {
        return Map.copyOf(entries);
    }

    /** Persists a premium lock before returning it to the login acceptance path. */
    public synchronized Entry recordPremium(VerifiedProfile profile, Authority authority) throws IOException {
        Objects.requireNonNull(profile, "profile");
        if (authority.isOffline()) {
            throw new IllegalArgumentException("premium identity cannot use an offline authority");
        }
        return persist(HybridIdentityPolicy.StoredIdentity.PREMIUM_LOCKED,
                profile.uuid(), profile.name(), Objects.requireNonNull(authority, "authority"));
    }

    /** Persists an OfflineAuth enrollment before returning it to the login acceptance path. */
    public synchronized Entry recordOffline(UUID uuid, String canonicalName) throws IOException {
        return recordOffline(uuid, canonicalName, Authority.OFFLINE_AUTH);
    }

    public synchronized Entry recordOffline(UUID uuid, String canonicalName, Authority authority) throws IOException {
        if (authority == null || !authority.isOffline()) {
            throw new IllegalArgumentException("offline identity requires an offline authority");
        }
        return persist(HybridIdentityPolicy.StoredIdentity.OFFLINE_ENROLLED,
                Objects.requireNonNull(uuid, "uuid"), canonicalName, authority);
    }

    private Entry persist(
            HybridIdentityPolicy.StoredIdentity identity,
            UUID uuid,
            String canonicalName,
            Authority authority
    ) throws IOException {
        canonicalName = requireMinecraftName(canonicalName);
        String key = normalizeName(canonicalName);
        Entry existing = entries.get(key);
        if (existing == null && entries.size() >= MAX_ENTRIES) {
            throw new IOException("hybrid identity store entry limit reached");
        }
        if (existing != null && (existing.identity() != identity || !existing.uuid().equals(uuid))) {
            throw new IOException("identity ownership conflict for " + key);
        }

        long now = clock.millis();
        Entry updated = new Entry(identity, uuid, canonicalName, authority,
                existing == null ? now : existing.firstVerifiedAt(), now);
        Map<String, Entry> candidate = new LinkedHashMap<>(entries);
        candidate.put(key, updated);
        writeAtomically(candidate);
        entries.clear();
        entries.putAll(candidate);
        return updated;
    }

    private static Map<String, Entry> load(Path file) throws IOException {
        Map<String, Entry> loaded = new LinkedHashMap<>();
        if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) return loaded;
        if (Files.isSymbolicLink(file) || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("hybrid identity store must be a regular non-symlink file");
        }
        long size = Files.size(file);
        if (size == 0 || size > MAX_FILE_BYTES) {
            throw new IOException("hybrid identity store is empty or too large");
        }

        try (Reader input = Files.newBufferedReader(file, StandardCharsets.UTF_8);
             JsonReader json = new JsonReader(input)) {
            json.setLenient(false);
            json.beginObject();
            Set<String> seenRoot = new java.util.HashSet<>();
            Integer schema = null;
            boolean sawEntries = false;
            while (json.hasNext()) {
                String field = json.nextName();
                if (!ROOT_FIELDS.contains(field) || !seenRoot.add(field)) {
                    throw new IOException("unknown or duplicate store field: " + field);
                }
                if (field.equals("schemaVersion")) {
                    schema = json.nextInt();
                } else {
                    sawEntries = true;
                    json.beginArray();
                    while (json.hasNext()) {
                        ParsedEntry parsed = readEntry(json);
                        String key = normalizeName(parsed.entry.canonicalName());
                        if (!key.equals(parsed.key) || loaded.putIfAbsent(key, parsed.entry) != null) {
                            throw new IOException("duplicate or non-canonical identity key: " + parsed.key);
                        }
                        if (loaded.size() > MAX_ENTRIES) {
                            throw new IOException("hybrid identity store entry limit exceeded");
                        }
                    }
                    json.endArray();
                }
            }
            json.endObject();
            if (json.peek() != JsonToken.END_DOCUMENT) throw new IOException("trailing store data");
            if (schema == null || schema != SCHEMA_VERSION || !sawEntries) {
                throw new IOException("unsupported or incomplete hybrid identity store schema");
            }
        } catch (IllegalArgumentException | IllegalStateException failure) {
            throw new IOException("invalid hybrid identity store", failure);
        }
        return loaded;
    }

    private static ParsedEntry readEntry(JsonReader json) throws IOException {
        json.beginObject();
        Map<String, Object> values = new LinkedHashMap<>();
        while (json.hasNext()) {
            String field = json.nextName();
            if (!ENTRY_FIELDS.contains(field) && !field.equals("key")) {
                throw new IOException("unknown identity field: " + field);
            }
            if (values.containsKey(field)) throw new IOException("duplicate identity field: " + field);
            Object value = switch (field) {
                case "firstVerifiedAt", "lastVerifiedAt" -> json.nextLong();
                default -> json.nextString();
            };
            values.put(field, value);
        }
        json.endObject();
        if (values.size() != ENTRY_FIELDS.size() + 1 || !values.keySet().containsAll(ENTRY_FIELDS)
                || !values.containsKey("key")) {
            throw new IOException("identity entry has missing fields");
        }
        String key = boundedString(values.get("key"), 16, "key").toLowerCase(Locale.ROOT);
        Entry entry = new Entry(
                HybridIdentityPolicy.StoredIdentity.valueOf(boundedString(values.get("identity"), 32, "identity")),
                UUID.fromString(boundedString(values.get("uuid"), 36, "uuid")),
                boundedString(values.get("canonicalName"), 16, "canonicalName"),
                Authority.valueOf(boundedString(values.get("authority"), 32, "authority")),
                (Long) values.get("firstVerifiedAt"),
                (Long) values.get("lastVerifiedAt"));
        return new ParsedEntry(key, entry);
    }

    private void writeAtomically(Map<String, Entry> snapshot) throws IOException {
        Path directory = Objects.requireNonNull(file.getParent(), "store parent");
        Files.createDirectories(directory);
        if (Files.isSymbolicLink(directory)) throw new IOException("store directory must not be a symlink");

        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", SCHEMA_VERSION);
        JsonArray serializedEntries = new JsonArray();
        new TreeMap<>(snapshot).forEach((key, entry) -> {
            JsonObject serialized = new JsonObject();
            serialized.addProperty("key", key);
            serialized.addProperty("identity", entry.identity().name());
            serialized.addProperty("uuid", entry.uuid().toString());
            serialized.addProperty("canonicalName", entry.canonicalName());
            serialized.addProperty("authority", entry.authority().name());
            serialized.addProperty("firstVerifiedAt", entry.firstVerifiedAt());
            serialized.addProperty("lastVerifiedAt", entry.lastVerifiedAt());
            serializedEntries.add(serialized);
        });
        root.add("entries", serializedEntries);
        byte[] bytes = new Gson().toJson(root).getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_FILE_BYTES) throw new IOException("hybrid identity store is too large");

        Path temporary = directory.resolve(file.getFileName() + ".tmp-" + UUID.randomUUID());
        try {
            try (FileChannel output = FileChannel.open(temporary,
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                ByteBuffer data = ByteBuffer.wrap(bytes);
                while (data.hasRemaining()) output.write(data);
                output.force(true);
            }
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                throw new IOException("atomic store replacement is not supported", unsupported);
            }
            forceDirectory(directory);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static void forceDirectory(Path directory) throws IOException {
        try (FileChannel channel = FileChannel.open(directory, StandardOpenOption.READ)) {
            channel.force(true);
        } catch (UnsupportedOperationException ignored) {
            // Some providers cannot open directories; the file itself was forced.
        }
    }

    private static String normalizeName(String value) {
        return requireMinecraftName(value).toLowerCase(Locale.ROOT);
    }

    private static String requireMinecraftName(String value) {
        Objects.requireNonNull(value, "name");
        if (value.isBlank() || value.length() > 16 || !value.matches("[A-Za-z0-9_]+")) {
            throw new IllegalArgumentException("invalid Minecraft name");
        }
        return value;
    }

    private static String boundedString(Object value, int maximum, String field) throws IOException {
        if (!(value instanceof String string) || string.isBlank() || string.length() > maximum) {
            throw new IOException(field + " is missing, blank, or too long");
        }
        return string;
    }

    private record ParsedEntry(String key, Entry entry) {
    }
}
