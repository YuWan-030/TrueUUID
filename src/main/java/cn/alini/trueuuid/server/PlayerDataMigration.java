package cn.alini.trueuuid.server;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class PlayerDataMigration {
    public record OfflineData(UUID offlineUuid, String summary) {}
    public record CleanupResult(UUID offlineUuid, String summary, int cleanedFiles, boolean cleanedGlobalRefs, Path backupDir) {}

    private static final DateTimeFormatter BACKUP_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    public static UUID offlineUuid(String name) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
    }

    public static OfflineData findOfflineData(MinecraftServer server, String name) {
        UUID offlineUuid = offlineUuid(name);
        List<String> found = new ArrayList<>();
        if (Files.exists(playerData(server, offlineUuid))) found.add("playerdata");
        if (Files.exists(playerDataOld(server, offlineUuid))) found.add("playerdata_old");
        if (Files.exists(cosmeticArmor(server, offlineUuid))) found.add("Cosmetic Armor");
        if (Files.exists(advancements(server, offlineUuid))) found.add("advancements");
        if (Files.exists(stats(server, offlineUuid))) found.add("stats");
        if (Files.exists(opacPlayerClaims(server, offlineUuid)) || Files.exists(opacPlayerConfig(server, offlineUuid))) {
            found.add("Open Parties and Claims");
        }
        if (Files.exists(ftbChunksPlayer(server, offlineUuid))) found.add("FTB Chunks");
        if (Files.exists(ftbEssentialsPlayer(server, offlineUuid))) found.add("FTB Essentials");
        if (Files.exists(ftbTeamsPlayer(server, offlineUuid))) found.add("FTB Teams");
        if (Files.exists(ftbQuestsPlayer(server, offlineUuid))) found.add("FTB Quests");
        if (Files.exists(customNpcsPlayer(server, offlineUuid))) found.add("CustomNPCs");
        if (containsUuid(ftbRanksPlayers(server), offlineUuid)) found.add("FTB Ranks");
        if (found.isEmpty()) return null;
        return new OfflineData(offlineUuid, String.join(", ", found));
    }

    public static boolean needsOfflineUpgrade(MinecraftServer server, String name, UUID verifiedUuid) {
        OfflineData data = findOfflineData(server, name);
        return data != null && verifiedUuid != null && !data.offlineUuid().equals(verifiedUuid);
    }

    public static void migrateOfflineToVerified(MinecraftServer server, String name, UUID verifiedUuid) throws IOException {
        OfflineData data = findOfflineData(server, name);
        if (data == null || verifiedUuid == null || data.offlineUuid().equals(verifiedUuid)) {
            return;
        }

        List<FilePair> pairs = List.of(
                new FilePair(playerData(server, data.offlineUuid()), playerData(server, verifiedUuid), false, "vanilla"),
                new FilePair(playerDataOld(server, data.offlineUuid()), playerDataOld(server, verifiedUuid), false, "vanilla"),
                new FilePair(cosmeticArmor(server, data.offlineUuid()), cosmeticArmor(server, verifiedUuid), false, "cosarmor"),
                new FilePair(advancements(server, data.offlineUuid()), advancements(server, verifiedUuid), false, "vanilla/advancements"),
                new FilePair(stats(server, data.offlineUuid()), stats(server, verifiedUuid), false, "vanilla"),
                new FilePair(opacPlayerClaims(server, data.offlineUuid()), opacPlayerClaims(server, verifiedUuid), false, "opac"),
                new FilePair(opacPlayerConfig(server, data.offlineUuid()), opacPlayerConfig(server, verifiedUuid), true, "opac"),
                new FilePair(ftbChunksPlayer(server, data.offlineUuid()), ftbChunksPlayer(server, verifiedUuid), true, "ftbchunks"),
                new FilePair(ftbEssentialsPlayer(server, data.offlineUuid()), ftbEssentialsPlayer(server, verifiedUuid), true, "ftbessentials"),
                new FilePair(ftbTeamsPlayer(server, data.offlineUuid()), ftbTeamsPlayer(server, verifiedUuid), true, "ftbteams"),
                new FilePair(ftbQuestsPlayer(server, data.offlineUuid()), ftbQuestsPlayer(server, verifiedUuid), true, "ftbquests"),
                new FilePair(customNpcsPlayer(server, data.offlineUuid()), customNpcsPlayer(server, verifiedUuid), true, "customnpcs")
        );

        Path backupDir = backupDir(server, name, data.offlineUuid(), verifiedUuid);
        Files.createDirectories(backupDir);

        for (FilePair pair : pairs) {
            if (!Files.exists(pair.from())) continue;
            Files.createDirectories(pair.to().getParent());
            backupFile(pair.from(), backupDir.resolve("offline").resolve(pair.backupRelativeDir()).resolve(pair.from().getFileName()));
            if (Files.exists(pair.to())) {
                backupFile(pair.to(), backupDir.resolve("verified-existing").resolve(pair.backupRelativeDir()).resolve(pair.to().getFileName()));
            }
            if (pair.replaceUuidText()) {
                String text = Files.readString(pair.from(), StandardCharsets.UTF_8);
                text = replaceUuidText(text, data.offlineUuid(), verifiedUuid);
                Files.writeString(pair.to(), text, StandardCharsets.UTF_8);
            } else {
                Files.copy(pair.from(), pair.to(), StandardCopyOption.REPLACE_EXISTING);
            }
        }

        replaceGlobalTextFile(ftbRanksPlayers(server), data.offlineUuid(), verifiedUuid, backupDir.resolve("ftbranks").resolve("players.snbt"));
    }

    public static CleanupResult cleanupOfflineData(MinecraftServer server, String name) throws IOException {
        OfflineData data = findOfflineData(server, name);
        if (data == null) {
            return null;
        }

        List<FilePair> files = List.of(
                new FilePair(playerData(server, data.offlineUuid()), playerData(server, data.offlineUuid()), false, "vanilla"),
                new FilePair(playerDataOld(server, data.offlineUuid()), playerDataOld(server, data.offlineUuid()), false, "vanilla"),
                new FilePair(cosmeticArmor(server, data.offlineUuid()), cosmeticArmor(server, data.offlineUuid()), false, "cosarmor"),
                new FilePair(advancements(server, data.offlineUuid()), advancements(server, data.offlineUuid()), false, "vanilla/advancements"),
                new FilePair(stats(server, data.offlineUuid()), stats(server, data.offlineUuid()), false, "vanilla"),
                new FilePair(opacPlayerClaims(server, data.offlineUuid()), opacPlayerClaims(server, data.offlineUuid()), false, "opac"),
                new FilePair(opacPlayerConfig(server, data.offlineUuid()), opacPlayerConfig(server, data.offlineUuid()), true, "opac"),
                new FilePair(ftbChunksPlayer(server, data.offlineUuid()), ftbChunksPlayer(server, data.offlineUuid()), true, "ftbchunks"),
                new FilePair(ftbEssentialsPlayer(server, data.offlineUuid()), ftbEssentialsPlayer(server, data.offlineUuid()), true, "ftbessentials"),
                new FilePair(ftbTeamsPlayer(server, data.offlineUuid()), ftbTeamsPlayer(server, data.offlineUuid()), true, "ftbteams"),
                new FilePair(ftbQuestsPlayer(server, data.offlineUuid()), ftbQuestsPlayer(server, data.offlineUuid()), true, "ftbquests"),
                new FilePair(customNpcsPlayer(server, data.offlineUuid()), customNpcsPlayer(server, data.offlineUuid()), true, "customnpcs")
        );

        Path backupDir = cleanupBackupDir(server, name, data.offlineUuid());
        Files.createDirectories(backupDir);

        int cleaned = 0;
        for (FilePair file : files) {
            if (!Files.exists(file.from())) continue;
            Path backup = backupDir.resolve(file.backupRelativeDir()).resolve(file.from().getFileName());
            Files.createDirectories(backup.getParent());
            Files.move(file.from(), backup, StandardCopyOption.REPLACE_EXISTING);
            cleaned++;
        }

        boolean cleanedGlobalRefs = removeUuidLinesFromGlobalTextFile(
                ftbRanksPlayers(server),
                data.offlineUuid(),
                backupDir.resolve("ftbranks").resolve("players.snbt")
        );

        return new CleanupResult(data.offlineUuid(), data.summary(), cleaned, cleanedGlobalRefs, backupDir);
    }

    private static void replaceGlobalTextFile(Path file, UUID from, UUID to, Path backup) throws IOException {
        if (!containsUuid(file, from)) return;
        backupFile(file, backup);
        String text = Files.readString(file, StandardCharsets.UTF_8);
        Files.writeString(file, replaceUuidText(text, from, to), StandardCharsets.UTF_8);
    }

    private static void backupFile(Path source, Path backup) throws IOException {
        Files.createDirectories(backup.getParent());
        Files.copy(source, backup, StandardCopyOption.REPLACE_EXISTING);
    }

    private static boolean removeUuidLinesFromGlobalTextFile(Path file, UUID uuid, Path backup) throws IOException {
        if (!containsUuid(file, uuid)) return false;
        backupFile(file, backup);
        String hyphen = uuid.toString();
        String compact = hyphen.replace("-", "");
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        List<String> kept = new ArrayList<>();
        for (String line : lines) {
            if (!line.contains(hyphen) && !line.contains(compact)) {
                kept.add(line);
            }
        }
        Files.write(file, kept, StandardCharsets.UTF_8);
        return true;
    }

    private static boolean containsUuid(Path file, UUID uuid) {
        if (!Files.exists(file) || uuid == null) return false;
        try {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            String hyphen = uuid.toString();
            return text.contains(hyphen) || text.contains(hyphen.replace("-", ""));
        } catch (IOException ignored) {
            return false;
        }
    }

    private static Path playerData(MinecraftServer server, UUID uuid) {
        return server.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(uuid + ".dat");
    }

    private static Path playerDataOld(MinecraftServer server, UUID uuid) {
        return server.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(uuid + ".dat_old");
    }

    private static Path cosmeticArmor(MinecraftServer server, UUID uuid) {
        return server.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(uuid + ".cosarmor");
    }

    private static Path advancements(MinecraftServer server, UUID uuid) {
        return server.getWorldPath(LevelResource.PLAYER_ADVANCEMENTS_DIR).resolve(uuid + ".json");
    }

    private static Path stats(MinecraftServer server, UUID uuid) {
        return server.getWorldPath(LevelResource.PLAYER_STATS_DIR).resolve(uuid + ".json");
    }

    private static Path opacPlayerClaims(MinecraftServer server, UUID uuid) {
        return server.getWorldPath(LevelResource.ROOT)
                .resolve("data")
                .resolve("openpartiesandclaims")
                .resolve("player-claims")
                .resolve(uuid + ".nbt");
    }

    private static Path opacPlayerConfig(MinecraftServer server, UUID uuid) {
        return server.getWorldPath(LevelResource.ROOT)
                .resolve("data")
                .resolve("openpartiesandclaims")
                .resolve("player-configs")
                .resolve(uuid + ".toml");
    }

    private static Path ftbChunksPlayer(MinecraftServer server, UUID uuid) {
        return server.getWorldPath(LevelResource.ROOT).resolve("ftbchunks").resolve(uuid + ".snbt");
    }

    private static Path ftbEssentialsPlayer(MinecraftServer server, UUID uuid) {
        return server.getWorldPath(LevelResource.ROOT).resolve("ftbessentials").resolve("playerdata").resolve(uuid + ".snbt");
    }

    private static Path ftbTeamsPlayer(MinecraftServer server, UUID uuid) {
        return server.getWorldPath(LevelResource.ROOT).resolve("ftbteams").resolve("player").resolve(uuid + ".snbt");
    }

    private static Path ftbQuestsPlayer(MinecraftServer server, UUID uuid) {
        return server.getWorldPath(LevelResource.ROOT).resolve("ftbquests").resolve(uuid + ".snbt");
    }

    private static Path customNpcsPlayer(MinecraftServer server, UUID uuid) {
        return server.getWorldPath(LevelResource.ROOT).resolve("customnpcs").resolve("playerdata").resolve(uuid + ".json");
    }

    private static Path ftbRanksPlayers(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve("serverconfig").resolve("ftbranks").resolve("players.snbt");
    }

    private static String replaceUuidText(String text, UUID from, UUID to) {
        String fromHyphen = from.toString();
        String toHyphen = to.toString();
        return text
                .replace(fromHyphen, toHyphen)
                .replace(fromHyphen.replace("-", ""), toHyphen.replace("-", ""));
    }

    private static Path backupDir(MinecraftServer server, String name, UUID offlineUuid, UUID verifiedUuid) {
        String safeName = name == null ? "unknown" : name.replaceAll("[^A-Za-z0-9_.-]", "_").toLowerCase(Locale.ROOT);
        String stamp = LocalDateTime.now().format(BACKUP_TIME);
        return server.getWorldPath(LevelResource.ROOT)
                .resolve("trueuuid-backups")
                .resolve("offline-upgrades")
                .resolve(stamp + "-" + safeName + "-" + offlineUuid + "-to-" + verifiedUuid);
    }

    private static Path cleanupBackupDir(MinecraftServer server, String name, UUID offlineUuid) {
        String safeName = name == null ? "unknown" : name.replaceAll("[^A-Za-z0-9_.-]", "_").toLowerCase(Locale.ROOT);
        String stamp = LocalDateTime.now().format(BACKUP_TIME);
        return server.getWorldPath(LevelResource.ROOT)
                .resolve("trueuuid-backups")
                .resolve("offline-cleanups")
                .resolve(stamp + "-" + safeName + "-" + offlineUuid);
    }

    private record FilePair(Path from, Path to, boolean replaceUuidText, String backupRelativeDir) {}

    private PlayerDataMigration() {}
}
