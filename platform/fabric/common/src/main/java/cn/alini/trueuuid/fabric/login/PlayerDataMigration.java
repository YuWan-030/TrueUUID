package cn.alini.trueuuid.fabric.login;

import cn.alini.trueuuid.protocol.MigrationExecutor;
import cn.alini.trueuuid.protocol.MigrationPlanner;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.WorldSavePath;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Fabric path-map over the shared loader-neutral migration engine. */
public final class PlayerDataMigration {
    public record OfflineData(UUID offlineUuid, String summary) {}
    public record CleanupResult(UUID offlineUuid, String summary, int cleanedFiles, boolean cleanedGlobalRefs, Path backupDir) {}

    public static UUID offlineUuid(String name) {
        return MigrationExecutor.offlineUuid(name);
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
        if (MigrationExecutor.containsUuid(ftbRanksPlayers(server), offlineUuid)) found.add("FTB Ranks");
        if (found.isEmpty()) return null;
        return new OfflineData(offlineUuid, String.join(", ", found));
    }

    public static void migrateOfflineToVerified(MinecraftServer server, String name, UUID verifiedUuid) throws IOException {
        OfflineData data = findOfflineData(server, name);
        if (data == null || verifiedUuid == null || data.offlineUuid().equals(verifiedUuid)) return;
        UUID offline = data.offlineUuid();
        MigrationPlanner.Plan plan = MigrationPlanner.preflight(migrateCandidates(server, offline, verifiedUuid), Files::exists);
        Path backupDir = MigrationExecutor.upgradeBackupDir(worldRoot(server), name, offline, verifiedUuid);
        MigrationExecutor.execute(plan, backupDir, offline, verifiedUuid, ftbRanksPlayers(server));
    }

    public static CleanupResult cleanupOfflineData(MinecraftServer server, String name) throws IOException {
        OfflineData data = findOfflineData(server, name);
        if (data == null) return null;
        UUID offline = data.offlineUuid();
        Path backupDir = MigrationExecutor.cleanupBackupDir(worldRoot(server), name, offline);
        MigrationExecutor.CleanupOutcome outcome = MigrationExecutor.cleanup(
                cleanupFiles(server, offline), backupDir, offline, ftbRanksPlayers(server));
        return new CleanupResult(offline, data.summary(), outcome.cleanedFiles(), outcome.cleanedGlobalRefs(), backupDir);
    }

    private static List<MigrationPlanner.Candidate> migrateCandidates(MinecraftServer server, UUID from, UUID to) {
        return List.of(
                candidate(playerData(server, from), playerData(server, to), false, "vanilla"),
                candidate(playerDataOld(server, from), playerDataOld(server, to), false, "vanilla"),
                candidate(cosmeticArmor(server, from), cosmeticArmor(server, to), false, "cosarmor"),
                candidate(advancements(server, from), advancements(server, to), false, "vanilla/advancements"),
                candidate(stats(server, from), stats(server, to), false, "vanilla"),
                candidate(opacPlayerClaims(server, from), opacPlayerClaims(server, to), false, "opac"),
                candidate(opacPlayerConfig(server, from), opacPlayerConfig(server, to), true, "opac"),
                candidate(ftbChunksPlayer(server, from), ftbChunksPlayer(server, to), true, "ftbchunks"),
                candidate(ftbEssentialsPlayer(server, from), ftbEssentialsPlayer(server, to), true, "ftbessentials"),
                candidate(ftbTeamsPlayer(server, from), ftbTeamsPlayer(server, to), true, "ftbteams"),
                candidate(ftbQuestsPlayer(server, from), ftbQuestsPlayer(server, to), true, "ftbquests"),
                candidate(customNpcsPlayer(server, from), customNpcsPlayer(server, to), true, "customnpcs")
        );
    }

    private static List<MigrationExecutor.GroupedPath> cleanupFiles(MinecraftServer server, UUID offline) {
        return List.of(
                new MigrationExecutor.GroupedPath(playerData(server, offline), "vanilla"),
                new MigrationExecutor.GroupedPath(playerDataOld(server, offline), "vanilla"),
                new MigrationExecutor.GroupedPath(cosmeticArmor(server, offline), "cosarmor"),
                new MigrationExecutor.GroupedPath(advancements(server, offline), "vanilla/advancements"),
                new MigrationExecutor.GroupedPath(stats(server, offline), "vanilla"),
                new MigrationExecutor.GroupedPath(opacPlayerClaims(server, offline), "opac"),
                new MigrationExecutor.GroupedPath(opacPlayerConfig(server, offline), "opac"),
                new MigrationExecutor.GroupedPath(ftbChunksPlayer(server, offline), "ftbchunks"),
                new MigrationExecutor.GroupedPath(ftbEssentialsPlayer(server, offline), "ftbessentials"),
                new MigrationExecutor.GroupedPath(ftbTeamsPlayer(server, offline), "ftbteams"),
                new MigrationExecutor.GroupedPath(ftbQuestsPlayer(server, offline), "ftbquests"),
                new MigrationExecutor.GroupedPath(customNpcsPlayer(server, offline), "customnpcs")
        );
    }

    private static MigrationPlanner.Candidate candidate(Path from, Path to, boolean text, String group) {
        return new MigrationPlanner.Candidate(from, to,
                text ? MigrationPlanner.Mode.UUID_TEXT_REWRITE : MigrationPlanner.Mode.BINARY_COPY, group);
    }

    private static Path worldRoot(MinecraftServer server) {
        return server.getSavePath(WorldSavePath.ROOT);
    }

    private static Path playerData(MinecraftServer server, UUID uuid) {
        return server.getSavePath(WorldSavePath.PLAYERDATA).resolve(uuid + ".dat");
    }

    private static Path playerDataOld(MinecraftServer server, UUID uuid) {
        return server.getSavePath(WorldSavePath.PLAYERDATA).resolve(uuid + ".dat_old");
    }

    private static Path cosmeticArmor(MinecraftServer server, UUID uuid) {
        return server.getSavePath(WorldSavePath.PLAYERDATA).resolve(uuid + ".cosarmor");
    }

    private static Path advancements(MinecraftServer server, UUID uuid) {
        return server.getSavePath(WorldSavePath.ADVANCEMENTS).resolve(uuid + ".json");
    }

    private static Path stats(MinecraftServer server, UUID uuid) {
        return server.getSavePath(WorldSavePath.STATS).resolve(uuid + ".json");
    }

    private static Path opacPlayerClaims(MinecraftServer server, UUID uuid) {
        return worldRoot(server).resolve("data").resolve("openpartiesandclaims")
                .resolve("player-claims").resolve(uuid + ".nbt");
    }

    private static Path opacPlayerConfig(MinecraftServer server, UUID uuid) {
        return worldRoot(server).resolve("data").resolve("openpartiesandclaims")
                .resolve("player-configs").resolve(uuid + ".toml");
    }

    private static Path ftbChunksPlayer(MinecraftServer server, UUID uuid) {
        return worldRoot(server).resolve("ftbchunks").resolve(uuid + ".snbt");
    }

    private static Path ftbEssentialsPlayer(MinecraftServer server, UUID uuid) {
        return worldRoot(server).resolve("ftbessentials").resolve("playerdata").resolve(uuid + ".snbt");
    }

    private static Path ftbTeamsPlayer(MinecraftServer server, UUID uuid) {
        return worldRoot(server).resolve("ftbteams").resolve("player").resolve(uuid + ".snbt");
    }

    private static Path ftbQuestsPlayer(MinecraftServer server, UUID uuid) {
        return worldRoot(server).resolve("ftbquests").resolve(uuid + ".snbt");
    }

    private static Path customNpcsPlayer(MinecraftServer server, UUID uuid) {
        return worldRoot(server).resolve("customnpcs").resolve("playerdata").resolve(uuid + ".json");
    }

    private static Path ftbRanksPlayers(MinecraftServer server) {
        return worldRoot(server).resolve("serverconfig").resolve("ftbranks").resolve("players.snbt");
    }

    private PlayerDataMigration() {}
}
