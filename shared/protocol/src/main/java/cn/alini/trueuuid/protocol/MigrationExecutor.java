package cn.alini.trueuuid.protocol;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Loader-neutral filesystem transaction engine for offline→verified player-data
 * migration and cleanup. Operates purely on {@link Path}s (no Minecraft types):
 * each adapter supplies the world/mod path mapping and calls in here. Preserves
 * every security invariant of the original Forge 1.20.1 implementation — symlink
 * refusal, text size bounds, atomic writes, a commit journal, and full rollback
 * of applied targets and deleted sources on any failure.
 */
public final class MigrationExecutor {
    /** Text sources larger than this are refused (UUID rewrite reads them fully). */
    public static final long MAX_TEXT_MIGRATION_BYTES = 64L * 1024L * 1024L;
    private static final DateTimeFormatter BACKUP_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    /** One file to move aside during cleanup, with its backup sub-tree group. */
    public record GroupedPath(Path path, String group) {}
    /** Result of a cleanup pass. */
    public record CleanupOutcome(int cleanedFiles, boolean cleanedGlobalRefs) {}

    private record AppliedTarget(Path target, Path backup) {}
    private record DeletedSource(Path source, Path backup) {}

    /** The offline UUID Minecraft derives for an offline-mode name. */
    public static UUID offlineUuid(String name) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Executes one offline→verified migration transaction: back up each source and
     * any colliding target, apply (binary copy or UUID text rewrite) atomically,
     * optionally rewrite one shared global text file, then delete the sources and
     * commit. Any failure rolls back every applied target and restores every
     * deleted source.
     *
     * @param plan            preflighted operations from {@link MigrationPlanner#preflight}
     * @param backupDir       transaction backup directory (see {@link #upgradeBackupDir})
     * @param globalTextFile  optional file with cross-player UUID references, or {@code null}
     */
    public static void execute(MigrationPlanner.Plan plan, Path backupDir, UUID from, UUID to, Path globalTextFile) throws IOException {
        boolean updateGlobal = globalTextFile != null && containsUuid(globalTextFile, from);
        fsPreflight(plan, updateGlobal ? globalTextFile : null);

        Files.createDirectories(backupDir);
        Path journal = backupDir.resolve("journal.log");
        Files.writeString(journal, "PREPARED\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        List<AppliedTarget> applied = new ArrayList<>();
        List<DeletedSource> sources = new ArrayList<>();
        try {
            for (MigrationPlanner.Operation operation : plan.operations()) {
                Path sourceBackup = backupDir.resolve("offline").resolve(operation.group()).resolve(operation.source().getFileName());
                backupFile(operation.source(), sourceBackup);
                sources.add(new DeletedSource(operation.source(), sourceBackup));
                Path targetBackup = null;
                if (operation.targetExists()) {
                    targetBackup = backupDir.resolve("verified-existing").resolve(operation.group()).resolve(operation.target().getFileName());
                    backupFile(operation.target(), targetBackup);
                }
                appendJournal(journal, "APPLY " + operation.target());
                writeTargetAtomically(operation, from, to);
                applied.add(new AppliedTarget(operation.target(), targetBackup));
            }
            if (updateGlobal) {
                Path globalBackup = backupDir.resolve("global").resolve(globalTextFile.getFileName());
                backupFile(globalTextFile, globalBackup);
                appendJournal(journal, "APPLY " + globalTextFile);
                writeTextAtomically(globalTextFile, replaceUuidText(Files.readString(globalTextFile, StandardCharsets.UTF_8), from, to));
                applied.add(new AppliedTarget(globalTextFile, globalBackup));
            }
            for (DeletedSource source : sources) {
                appendJournal(journal, "DELETE_SOURCE " + source.source());
                Files.delete(source.source());
            }
            appendJournal(journal, "COMMITTED");
        } catch (Throwable failure) {
            IOException rollbackFailure = rollback(applied, sources, journal);
            if (rollbackFailure != null) failure.addSuppressed(rollbackFailure);
            if (failure instanceof IOException io) throw io;
            throw new IOException("migration failed and was rolled back", failure);
        }
    }

    /**
     * Moves every existing offline file aside into {@code backupDir} and removes
     * the offline UUID's lines from an optional shared global text file.
     */
    public static CleanupOutcome cleanup(List<GroupedPath> files, Path backupDir, UUID offlineUuid, Path globalTextFile) throws IOException {
        Files.createDirectories(backupDir);
        int cleaned = 0;
        for (GroupedPath file : files) {
            if (!Files.exists(file.path())) continue;
            Path backup = backupDir.resolve(file.group()).resolve(file.path().getFileName());
            Files.createDirectories(backup.getParent());
            Files.move(file.path(), backup, StandardCopyOption.REPLACE_EXISTING);
            cleaned++;
        }
        boolean cleanedGlobalRefs = globalTextFile != null && removeUuidLines(globalTextFile, offlineUuid,
                backupDir.resolve("global").resolve(globalTextFile.getFileName()));
        return new CleanupOutcome(cleaned, cleanedGlobalRefs);
    }

    /** Backup directory for a migration, under {@code worldRoot}. */
    public static Path upgradeBackupDir(Path worldRoot, String name, UUID from, UUID to) {
        return worldRoot.resolve("trueuuid-backups").resolve("offline-upgrades")
                .resolve(stamp() + "-" + safeName(name) + "-" + from + "-to-" + to);
    }

    /** Backup directory for a cleanup, under {@code worldRoot}. */
    public static Path cleanupBackupDir(Path worldRoot, String name, UUID offlineUuid) {
        return worldRoot.resolve("trueuuid-backups").resolve("offline-cleanups")
                .resolve(stamp() + "-" + safeName(name) + "-" + offlineUuid);
    }

    /** True if {@code file} textually contains {@code uuid} (hyphenated or compact). */
    public static boolean containsUuid(Path file, UUID uuid) {
        if (!Files.exists(file) || uuid == null) return false;
        try {
            if (Files.size(file) > MAX_TEXT_MIGRATION_BYTES) return false;
            String text = Files.readString(file, StandardCharsets.UTF_8);
            String hyphen = uuid.toString();
            return text.contains(hyphen) || text.contains(hyphen.replace("-", ""));
        } catch (IOException ignored) {
            return false;
        }
    }

    /** Replaces every hyphenated and compact occurrence of {@code from} with {@code to}. */
    public static String replaceUuidText(String text, UUID from, UUID to) {
        String fromHyphen = from.toString();
        String toHyphen = to.toString();
        return text
                .replace(fromHyphen, toHyphen)
                .replace(fromHyphen.replace("-", ""), toHyphen.replace("-", ""));
    }

    private static void fsPreflight(MigrationPlanner.Plan plan, Path global) throws IOException {
        for (MigrationPlanner.Operation operation : plan.operations()) {
            if (!Files.isRegularFile(operation.source()) || !Files.isReadable(operation.source()) || Files.isSymbolicLink(operation.source()))
                throw new IOException("migration source is not a readable regular file: " + operation.source());
            if (operation.mode() == MigrationPlanner.Mode.UUID_TEXT_REWRITE && Files.size(operation.source()) > MAX_TEXT_MIGRATION_BYTES)
                throw new IOException("migration text source is too large: " + operation.source());
            if (Files.exists(operation.target()) && Files.isSymbolicLink(operation.target()))
                throw new IOException("migration target cannot be a symbolic link: " + operation.target());
        }
        if (global != null && (!Files.isRegularFile(global) || !Files.isReadable(global)
                || Files.isSymbolicLink(global) || Files.size(global) > MAX_TEXT_MIGRATION_BYTES)) {
            throw new IOException("global migration file failed preflight: " + global);
        }
    }

    private static void writeTargetAtomically(MigrationPlanner.Operation operation, UUID from, UUID to) throws IOException {
        Files.createDirectories(operation.target().getParent());
        if (operation.mode() == MigrationPlanner.Mode.UUID_TEXT_REWRITE) {
            writeTextAtomically(operation.target(), replaceUuidText(Files.readString(operation.source(), StandardCharsets.UTF_8), from, to));
        } else {
            Path temp = Files.createTempFile(operation.target().getParent(), ".trueuuid-", ".tmp");
            try {
                Files.copy(operation.source(), temp, StandardCopyOption.REPLACE_EXISTING);
                moveAtomically(temp, operation.target());
            } finally { Files.deleteIfExists(temp); }
        }
    }

    private static void writeTextAtomically(Path target, String text) throws IOException {
        Files.createDirectories(target.getParent());
        Path temp = Files.createTempFile(target.getParent(), ".trueuuid-", ".tmp");
        try {
            Files.writeString(temp, text, StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING);
            moveAtomically(temp, target);
        } finally { Files.deleteIfExists(temp); }
    }

    private static void moveAtomically(Path from, Path to) throws IOException {
        try { Files.move(from, to, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
        catch (AtomicMoveNotSupportedException ignored) { Files.move(from, to, StandardCopyOption.REPLACE_EXISTING); }
    }

    private static IOException rollback(List<AppliedTarget> applied, List<DeletedSource> sources, Path journal) {
        IOException failure = null;
        for (int i = applied.size() - 1; i >= 0; i--) {
            AppliedTarget target = applied.get(i);
            try {
                if (target.backup() == null) Files.deleteIfExists(target.target());
                else Files.copy(target.backup(), target.target(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ex) {
                if (failure == null) failure = ex; else failure.addSuppressed(ex);
            }
        }
        for (DeletedSource source : sources) {
            if (Files.exists(source.source())) continue;
            try {
                Files.createDirectories(source.source().getParent());
                Files.copy(source.backup(), source.source(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ex) {
                if (failure == null) failure = ex; else failure.addSuppressed(ex);
            }
        }
        try { appendJournal(journal, failure == null ? "ROLLED_BACK" : "ROLLBACK_FAILED"); }
        catch (IOException ex) { if (failure == null) failure = ex; else failure.addSuppressed(ex); }
        return failure;
    }

    private static boolean removeUuidLines(Path file, UUID uuid, Path backup) throws IOException {
        if (!containsUuid(file, uuid)) return false;
        if (Files.size(file) > MAX_TEXT_MIGRATION_BYTES) throw new IOException("global cleanup file is too large: " + file);
        backupFile(file, backup);
        String hyphen = uuid.toString();
        String compact = hyphen.replace("-", "");
        List<String> kept = new ArrayList<>();
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            if (!line.contains(hyphen) && !line.contains(compact)) kept.add(line);
        }
        Files.write(file, kept, StandardCharsets.UTF_8);
        return true;
    }

    private static void backupFile(Path source, Path backup) throws IOException {
        Files.createDirectories(backup.getParent());
        Files.copy(source, backup, StandardCopyOption.REPLACE_EXISTING);
    }

    private static void appendJournal(Path journal, String line) throws IOException {
        Files.writeString(journal, line + "\n", StandardCharsets.UTF_8, StandardOpenOption.APPEND);
    }

    private static String safeName(String name) {
        return name == null ? "unknown" : name.replaceAll("[^A-Za-z0-9_.-]", "_").toLowerCase(Locale.ROOT);
    }

    private static String stamp() {
        return LocalDateTime.now().format(BACKUP_TIME);
    }

    private MigrationExecutor() {}
}
