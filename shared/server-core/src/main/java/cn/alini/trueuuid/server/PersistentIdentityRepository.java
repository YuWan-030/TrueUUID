package cn.alini.trueuuid.server;

import cn.alini.trueuuid.protocol.HybridIdentityPolicy;
import cn.alini.trueuuid.protocol.PersistentHybridIdentityStore;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/** Strict schema-2 identity repository with independent premium/offline bindings and unique indexes. */
public final class PersistentIdentityRepository {
    public static final int SCHEMA_VERSION = 2;
    public static final int MAX_BASE_NAMES = 100_000;
    public static final long MAX_FILE_BYTES = 32L * 1024L * 1024L;
    private static final Set<String> ROOT_FIELDS = Set.of("schemaVersion", "records");
    private static final Set<String> RECORD_FIELDS = Set.of("baseName", "blocked", "premium", "offline");
    private static final Set<String> BINDING_FIELDS = Set.of(
            "uuid", "effectiveName", "authority", "firstAcceptedAt", "lastAcceptedAt");
    private static final Set<String> LEGACY_REGISTRY_FIELDS = Set.of(
            "premiumUuid", "firstVerifiedAt", "lastVerifiedAt", "authSource", "authDisplayName");

    public record Binding(UUID uuid, String effectiveName, AuthenticatedIdentity.Authority authority,
                          long firstAcceptedAt, long lastAcceptedAt) {
        public Binding {
            Objects.requireNonNull(uuid, "uuid");
            effectiveName = MinecraftNames.requireValidEffective(effectiveName);
            Objects.requireNonNull(authority, "authority");
            if (firstAcceptedAt < 0 || lastAcceptedAt < firstAcceptedAt) {
                throw new IllegalArgumentException("invalid acceptance timestamps");
            }
        }
    }

    public record Record(String baseName, Binding premium, Binding offline, boolean blocked) {
        public Record {
            baseName = MinecraftNames.requireValid(baseName);
            if (premium == null && offline == null && !blocked) {
                throw new IllegalArgumentException("empty identity record");
            }
            if (premium != null && premium.authority() == AuthenticatedIdentity.Authority.OFFLINE) {
                throw new IllegalArgumentException("premium binding has offline authority");
            }
            if (offline != null && offline.authority() != AuthenticatedIdentity.Authority.OFFLINE) {
                throw new IllegalArgumentException("offline binding has premium authority");
            }
        }

        public UnifiedAdmissionPolicy.StoredBindings policyBindings() {
            return new UnifiedAdmissionPolicy.StoredBindings(
                    premium != null,
                    offline != null,
                    offline != null && !baseName.equalsIgnoreCase(offline.effectiveName()),
                    blocked);
        }
    }

    private final Path file;
    private final Clock clock;
    private final Map<String, Record> records;
    private final AtomicLong generation = new AtomicLong();

    public PersistentIdentityRepository(Path file) throws IOException {
        this(file, Clock.systemUTC());
    }

    PersistentIdentityRepository(Path file, Clock clock) throws IOException {
        this.file = Objects.requireNonNull(file, "file").toAbsolutePath().normalize();
        this.clock = Objects.requireNonNull(clock, "clock");
        this.records = load(this.file);
        validateIndexes(this.records);
    }

    public synchronized Optional<Record> findByBaseName(String baseName) {
        return Optional.ofNullable(records.get(MinecraftNames.normalize(baseName)));
    }

    public synchronized Optional<AuthenticatedIdentity> identityOf(UUID uuid) {
        Objects.requireNonNull(uuid, "uuid");
        for (Record record : records.values()) {
            if (record.premium() != null && record.premium().uuid().equals(uuid)) {
                return Optional.of(toIdentity(record, record.premium(), AuthenticatedIdentity.Kind.PREMIUM));
            }
            if (record.offline() != null && record.offline().uuid().equals(uuid)) {
                return Optional.of(toIdentity(record, record.offline(), AuthenticatedIdentity.Kind.OFFLINE));
            }
        }
        return Optional.empty();
    }

    public synchronized boolean effectiveNameUnavailable(String normalizedEffectiveName) {
        String normalized = MinecraftNames.normalizeEffective(normalizedEffectiveName);
        return records.values().stream().anyMatch(record ->
                (record.premium() != null
                        && MinecraftNames.normalizeEffective(record.premium().effectiveName()).equals(normalized))
                        || (record.offline() != null
                        && MinecraftNames.normalizeEffective(record.offline().effectiveName()).equals(normalized)));
    }

    public synchronized Map<String, Record> snapshot() {
        return Map.copyOf(records);
    }

    public long generation() {
        return generation.get();
    }

    public synchronized AuthenticatedIdentity recordPremium(
            UUID uuid, String requestedName, String canonicalName, AuthenticatedIdentity.Authority authority
    ) throws IOException {
        if (authority == AuthenticatedIdentity.Authority.OFFLINE) {
            throw new IllegalArgumentException("premium binding requires verified authority");
        }
        String key = MinecraftNames.normalize(requestedName);
        Record existing = records.get(key);
        Binding previous = existing == null ? null : existing.premium();
        if (previous != null && !previous.uuid().equals(uuid)) {
            throw new IOException("premium UUID conflict for " + key);
        }
        long now = clock.millis();
        Binding binding = new Binding(uuid, canonicalName, authority,
                previous == null ? now : previous.firstAcceptedAt(), now);
        Record updated = new Record(requestedName, binding,
                existing == null ? null : existing.offline(), existing != null && existing.blocked());
        persistRecord(key, updated);
        return toIdentity(updated, binding, AuthenticatedIdentity.Kind.PREMIUM);
    }

    public synchronized AuthenticatedIdentity recordOffline(
            UUID uuid, String requestedName, String effectiveName
    ) throws IOException {
        String key = MinecraftNames.normalize(requestedName);
        Record existing = records.get(key);
        Binding previous = existing == null ? null : existing.offline();
        if (previous != null && !previous.uuid().equals(uuid)) {
            throw new IOException("offline UUID conflict for " + key);
        }
        if (previous != null) effectiveName = previous.effectiveName();
        long now = clock.millis();
        Binding binding = new Binding(uuid, effectiveName, AuthenticatedIdentity.Authority.OFFLINE,
                previous == null ? now : previous.firstAcceptedAt(), now);
        Record updated = new Record(requestedName, existing == null ? null : existing.premium(),
                binding, existing != null && existing.blocked());
        persistRecord(key, updated);
        return toIdentity(updated, binding, AuthenticatedIdentity.Kind.OFFLINE);
    }

    public synchronized void block(String baseName, boolean blocked) throws IOException {
        String key = MinecraftNames.normalize(baseName);
        Record existing = records.get(key);
        if (existing == null && !blocked) return;
        Record updated = existing == null
                ? new Record(baseName, null, null, true)
                : new Record(existing.baseName(), existing.premium(), existing.offline(), blocked);
        persistRecord(key, updated);
    }

    /** Atomically changes only an offline effective name; live player profiles are never rewritten. */
    public synchronized AuthenticatedIdentity setOfflineAlias(UUID offlineUuid, String alias) throws IOException {
        Objects.requireNonNull(offlineUuid, "offlineUuid");
        String validatedAlias = MinecraftNames.requireValidEffective(alias);
        for (Map.Entry<String, Record> item : records.entrySet()) {
            Record existing = item.getValue();
            Binding previous = existing.offline();
            if (previous == null || !previous.uuid().equals(offlineUuid)) continue;
            if (existing.premium() != null
                    && existing.premium().effectiveName().equalsIgnoreCase(validatedAlias)) {
                throw new IOException("offline alias may not occupy a premium canonical name");
            }
            Binding updatedBinding = new Binding(previous.uuid(), validatedAlias, previous.authority(),
                    previous.firstAcceptedAt(), clock.millis());
            Record updated = new Record(existing.baseName(), existing.premium(), updatedBinding, existing.blocked());
            persistRecord(item.getKey(), updated);
            return toIdentity(updated, updatedBinding, AuthenticatedIdentity.Kind.OFFLINE);
        }
        throw new IOException("offline identity does not exist");
    }

    /** Removes only TrueUUID's binding. It deliberately does not touch any loader player-data file. */
    public synchronized boolean release(UUID uuid, long expectedGeneration) throws IOException {
        Objects.requireNonNull(uuid, "uuid");
        if (generation.get() != expectedGeneration) return false;
        for (Map.Entry<String, Record> item : records.entrySet()) {
            Record current = item.getValue();
            Binding premium = current.premium();
            Binding offline = current.offline();
            boolean matched = false;
            if (premium != null && premium.uuid().equals(uuid)) {
                premium = null;
                matched = true;
            }
            if (offline != null && offline.uuid().equals(uuid)) {
                offline = null;
                matched = true;
            }
            if (!matched) continue;
            Map<String, Record> candidate = new LinkedHashMap<>(records);
            if (premium == null && offline == null && !current.blocked()) {
                candidate.remove(item.getKey());
            } else {
                candidate.put(item.getKey(), new Record(current.baseName(), premium, offline, current.blocked()));
            }
            validateIndexes(candidate);
            writeAtomically(candidate);
            records.clear();
            records.putAll(candidate);
            generation.incrementAndGet();
            return true;
        }
        return false;
    }

    /** Imports schema-1 state once. The legacy file is read only and remains untouched. */
    public synchronized int importLegacyIfEmpty(Path legacyFile) throws IOException {
        Objects.requireNonNull(legacyFile, "legacyFile");
        if (!records.isEmpty() || !Files.exists(legacyFile, LinkOption.NOFOLLOW_LINKS)) return 0;
        PersistentHybridIdentityStore legacy = new PersistentHybridIdentityStore(legacyFile);
        Map<String, Record> candidate = new LinkedHashMap<>();
        for (PersistentHybridIdentityStore.Entry entry : legacy.snapshot().values()) {
            AuthenticatedIdentity.Authority authority = switch (entry.authority()) {
                case MOJANG -> AuthenticatedIdentity.Authority.MOJANG;
                case ALLOWLISTED_YGGDRASIL -> AuthenticatedIdentity.Authority.ALLOWLISTED_YGGDRASIL;
                case OFFLINE_AUTH, OFFLINE_NAME_ONLY, TRUEUUID_CLIENT_GATE -> AuthenticatedIdentity.Authority.OFFLINE;
            };
            Binding binding = new Binding(entry.uuid(), entry.canonicalName(), authority,
                    entry.firstVerifiedAt(), entry.lastVerifiedAt());
            Record record = entry.identity() == HybridIdentityPolicy.StoredIdentity.PREMIUM_LOCKED
                    ? new Record(entry.canonicalName(), binding, null, false)
                    : new Record(entry.canonicalName(), null, binding, false);
            String key = MinecraftNames.normalize(entry.canonicalName());
            if (candidate.putIfAbsent(key, record) != null) {
                throw new IOException("ambiguous legacy identity for " + key);
            }
        }
        validateIndexes(candidate);
        writeAtomically(candidate);
        records.putAll(candidate);
        return candidate.size();
    }

    /** Imports the old loader registry once, preserving that source file byte-for-byte. */
    public synchronized int importVerifiedRegistryIfEmpty(Path registryFile) throws IOException {
        Objects.requireNonNull(registryFile, "registryFile");
        if (!records.isEmpty() || !Files.exists(registryFile, LinkOption.NOFOLLOW_LINKS)) return 0;
        if (Files.isSymbolicLink(registryFile)
                || !Files.isRegularFile(registryFile, LinkOption.NOFOLLOW_LINKS)
                || Files.size(registryFile) == 0 || Files.size(registryFile) > MAX_FILE_BYTES) {
            throw new IOException("legacy verified-name registry is not a bounded regular file");
        }
        Map<String, Record> candidate = new LinkedHashMap<>();
        try (Reader input = Files.newBufferedReader(registryFile, StandardCharsets.UTF_8);
             JsonReader json = new JsonReader(input)) {
            json.setLenient(false);
            json.beginObject();
            while (json.hasNext()) {
                String name = MinecraftNames.requireValid(json.nextName());
                String key = MinecraftNames.normalize(name);
                if (candidate.containsKey(key)) throw new IOException("duplicate legacy premium name");
                LegacyPremium premium = readLegacyPremium(json);
                Binding binding = new Binding(premium.uuid(), name, premium.authority(),
                        premium.firstVerifiedAt(), premium.lastVerifiedAt());
                candidate.put(key, new Record(name, binding, null, false));
                if (candidate.size() > MAX_BASE_NAMES) throw new IOException("legacy registry limit exceeded");
            }
            json.endObject();
            if (json.peek() != JsonToken.END_DOCUMENT) throw new IOException("trailing legacy registry data");
        } catch (IOException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw new IOException("invalid legacy verified-name registry", failure);
        }
        validateIndexes(candidate);
        writeAtomically(candidate);
        records.putAll(candidate);
        return candidate.size();
    }

    private LegacyPremium readLegacyPremium(JsonReader json) throws IOException {
        long now = clock.millis();
        if (json.peek() == JsonToken.STRING) {
            return new LegacyPremium(UUID.fromString(json.nextString()),
                    AuthenticatedIdentity.Authority.MOJANG, now, now);
        }
        json.beginObject();
        Set<String> seen = new HashSet<>();
        String uuid = null;
        String authSource = "MOJANG";
        Long first = null;
        Long last = null;
        while (json.hasNext()) {
            String field = json.nextName();
            if (!LEGACY_REGISTRY_FIELDS.contains(field) || !seen.add(field)) {
                throw new IOException("unknown or duplicate legacy registry field: " + field);
            }
            switch (field) {
                case "premiumUuid" -> uuid = json.nextString();
                case "firstVerifiedAt" -> first = json.nextLong();
                case "lastVerifiedAt" -> last = json.nextLong();
                case "authSource" -> authSource = json.nextString();
                case "authDisplayName" -> json.skipValue();
                default -> throw new IOException("unexpected legacy registry field");
            }
        }
        json.endObject();
        if (uuid == null) throw new IOException("legacy premium UUID is missing");
        long firstAt = first == null ? now : first;
        long lastAt = last == null ? Math.max(firstAt, now) : last;
        AuthenticatedIdentity.Authority authority = "MOJANG".equalsIgnoreCase(authSource)
                ? AuthenticatedIdentity.Authority.MOJANG
                : AuthenticatedIdentity.Authority.ALLOWLISTED_YGGDRASIL;
        return new LegacyPremium(UUID.fromString(uuid), authority, firstAt, lastAt);
    }

    private void persistRecord(String key, Record updated) throws IOException {
        if (!records.containsKey(key) && records.size() >= MAX_BASE_NAMES) {
            throw new IOException("identity repository entry limit reached");
        }
        Map<String, Record> candidate = new LinkedHashMap<>(records);
        candidate.put(key, updated);
        validateIndexes(candidate);
        writeAtomically(candidate);
        records.clear();
        records.putAll(candidate);
        generation.incrementAndGet();
    }

    private static AuthenticatedIdentity toIdentity(
            Record record, Binding binding, AuthenticatedIdentity.Kind kind
    ) {
        return new AuthenticatedIdentity(binding.uuid(), kind, record.baseName(), binding.effectiveName(),
                binding.authority(), !record.baseName().equalsIgnoreCase(binding.effectiveName()));
    }

    private static void validateIndexes(Map<String, Record> candidate) throws IOException {
        Set<UUID> uuids = new HashSet<>();
        Set<String> names = new HashSet<>();
        for (Map.Entry<String, Record> item : candidate.entrySet()) {
            Record record = item.getValue();
            if (!item.getKey().equals(MinecraftNames.normalize(record.baseName()))) {
                throw new IOException("non-canonical identity repository key");
            }
            for (Binding binding : new Binding[]{record.premium(), record.offline()}) {
                if (binding == null) continue;
                if (!uuids.add(binding.uuid())) throw new IOException("duplicate identity UUID");
                if (!names.add(MinecraftNames.normalizeEffective(binding.effectiveName()))) {
                    throw new IOException("duplicate effective identity name");
                }
            }
        }
    }

    private static Map<String, Record> load(Path file) throws IOException {
        Map<String, Record> loaded = new LinkedHashMap<>();
        if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) return loaded;
        if (Files.isSymbolicLink(file) || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("identity repository must be a regular non-symlink file");
        }
        long size = Files.size(file);
        if (size == 0 || size > MAX_FILE_BYTES) throw new IOException("identity repository is empty or too large");
        try (Reader input = Files.newBufferedReader(file, StandardCharsets.UTF_8);
             JsonReader json = new JsonReader(input)) {
            json.setLenient(false);
            json.beginObject();
            Set<String> seenRoot = new HashSet<>();
            Integer schema = null;
            boolean sawRecords = false;
            while (json.hasNext()) {
                String field = json.nextName();
                if (!ROOT_FIELDS.contains(field) || !seenRoot.add(field)) {
                    throw new IOException("unknown or duplicate repository root field: " + field);
                }
                if (field.equals("schemaVersion")) {
                    schema = json.nextInt();
                } else {
                    sawRecords = true;
                    json.beginArray();
                    while (json.hasNext()) {
                        Record record = readRecord(json);
                        String key = MinecraftNames.normalize(record.baseName());
                        if (loaded.putIfAbsent(key, record) != null) throw new IOException("duplicate base name");
                        if (loaded.size() > MAX_BASE_NAMES) {
                            throw new IOException("identity repository limit exceeded");
                        }
                    }
                    json.endArray();
                }
            }
            json.endObject();
            if (json.peek() != JsonToken.END_DOCUMENT) throw new IOException("trailing repository data");
            if (schema == null || schema != SCHEMA_VERSION || !sawRecords) {
                throw new IOException("unsupported or incomplete identity repository schema");
            }
        } catch (IOException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw new IOException("invalid identity repository", failure);
        }
        return loaded;
    }

    private static Record readRecord(JsonReader json) throws IOException {
        json.beginObject();
        Set<String> seen = new HashSet<>();
        String baseName = null;
        Boolean blocked = null;
        Binding premium = null;
        Binding offline = null;
        boolean sawPremium = false;
        boolean sawOffline = false;
        while (json.hasNext()) {
            String field = json.nextName();
            if (!RECORD_FIELDS.contains(field) || !seen.add(field)) {
                throw new IOException("unknown or duplicate identity record field: " + field);
            }
            switch (field) {
                case "baseName" -> baseName = json.nextString();
                case "blocked" -> blocked = json.nextBoolean();
                case "premium" -> {
                    sawPremium = true;
                    premium = readNullableBinding(json);
                }
                case "offline" -> {
                    sawOffline = true;
                    offline = readNullableBinding(json);
                }
                default -> throw new IOException("unexpected identity record field");
            }
        }
        json.endObject();
        if (seen.size() != RECORD_FIELDS.size() || baseName == null || blocked == null
                || !sawPremium || !sawOffline) {
            throw new IOException("identity record has missing fields");
        }
        return new Record(baseName, premium, offline, blocked);
    }

    private static Binding readNullableBinding(JsonReader json) throws IOException {
        if (json.peek() == JsonToken.NULL) {
            json.nextNull();
            return null;
        }
        json.beginObject();
        Set<String> seen = new HashSet<>();
        String uuid = null;
        String effectiveName = null;
        String authority = null;
        Long first = null;
        Long last = null;
        while (json.hasNext()) {
            String field = json.nextName();
            if (!BINDING_FIELDS.contains(field) || !seen.add(field)) {
                throw new IOException("unknown or duplicate identity binding field: " + field);
            }
            switch (field) {
                case "uuid" -> uuid = json.nextString();
                case "effectiveName" -> effectiveName = json.nextString();
                case "authority" -> authority = json.nextString();
                case "firstAcceptedAt" -> first = json.nextLong();
                case "lastAcceptedAt" -> last = json.nextLong();
                default -> throw new IOException("unexpected identity binding field");
            }
        }
        json.endObject();
        if (seen.size() != BINDING_FIELDS.size() || uuid == null || effectiveName == null
                || authority == null || first == null || last == null) {
            throw new IOException("identity binding has missing fields");
        }
        return new Binding(UUID.fromString(uuid), effectiveName,
                AuthenticatedIdentity.Authority.valueOf(authority), first, last);
    }

    private void writeAtomically(Map<String, Record> snapshot) throws IOException {
        Path directory = Objects.requireNonNull(file.getParent(), "repository parent");
        Files.createDirectories(directory);
        if (Files.isSymbolicLink(directory)) throw new IOException("repository directory must not be a symlink");
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", SCHEMA_VERSION);
        JsonArray serialized = new JsonArray();
        new TreeMap<>(snapshot).forEach((ignored, record) -> {
            JsonObject item = new JsonObject();
            item.addProperty("baseName", record.baseName());
            item.addProperty("blocked", record.blocked());
            item.add("premium", serializeBinding(record.premium()));
            item.add("offline", serializeBinding(record.offline()));
            serialized.add(item);
        });
        root.add("records", serialized);
        byte[] bytes = new GsonBuilder().serializeNulls().create()
                .toJson(root).getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_FILE_BYTES) throw new IOException("identity repository is too large");
        Path temporary = directory.resolve(file.getFileName() + ".tmp-" + UUID.randomUUID());
        try {
            try (FileChannel output = FileChannel.open(temporary,
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                ByteBuffer data = ByteBuffer.wrap(bytes);
                while (data.hasRemaining()) output.write(data);
                output.force(true);
            }
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                throw new IOException("atomic repository replacement is not supported", unsupported);
            }
            try (FileChannel channel = FileChannel.open(directory, StandardOpenOption.READ)) {
                channel.force(true);
            } catch (UnsupportedOperationException ignored) {
                // File contents were already forced.
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static JsonElement serializeBinding(Binding binding) {
        if (binding == null) return com.google.gson.JsonNull.INSTANCE;
        JsonObject object = new JsonObject();
        object.addProperty("uuid", binding.uuid().toString());
        object.addProperty("effectiveName", binding.effectiveName());
        object.addProperty("authority", binding.authority().name());
        object.addProperty("firstAcceptedAt", binding.firstAcceptedAt());
        object.addProperty("lastAcceptedAt", binding.lastAcceptedAt());
        return object;
    }

    private record LegacyPremium(UUID uuid, AuthenticatedIdentity.Authority authority,
                                 long firstVerifiedAt, long lastVerifiedAt) {
    }

}
