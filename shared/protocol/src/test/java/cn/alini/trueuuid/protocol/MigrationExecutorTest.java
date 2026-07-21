package cn.alini.trueuuid.protocol;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class MigrationExecutorTest {
    @TempDir Path temp;

    private final UUID from = MigrationExecutor.offlineUuid("Alice");
    private final UUID to = UUID.fromString("11111111-2222-3333-4444-555555555555");

    private MigrationPlanner.Plan plan(List<MigrationPlanner.Candidate> candidates) {
        return MigrationPlanner.preflight(candidates, Files::exists);
    }

    @Test void migratesBinaryAndTextRewritesAndDeletesSources() throws Exception {
        Path binSrc = Files.writeString(temp.resolve(from + ".dat"), "profile-bytes");
        Path binDst = temp.resolve(to + ".dat");
        Path txtSrc = Files.writeString(temp.resolve(from + ".snbt"), "owner=" + from + " compact=" + from.toString().replace("-", ""));
        Path txtDst = temp.resolve(to + ".snbt");
        Path backup = temp.resolve("bk");

        MigrationExecutor.execute(plan(List.of(
                new MigrationPlanner.Candidate(binSrc, binDst, MigrationPlanner.Mode.BINARY_COPY, "vanilla"),
                new MigrationPlanner.Candidate(txtSrc, txtDst, MigrationPlanner.Mode.UUID_TEXT_REWRITE, "mod"))),
                backup, from, to, null);

        assertEquals("profile-bytes", Files.readString(binDst));
        assertFalse(Files.exists(binSrc), "binary source deleted");
        String rewritten = Files.readString(txtDst);
        assertTrue(rewritten.contains(to.toString()) && rewritten.contains(to.toString().replace("-", "")));
        assertFalse(rewritten.contains(from.toString()), "offline uuid fully rewritten");
        assertFalse(Files.exists(txtSrc), "text source deleted");
        assertTrue(Files.readString(backup.resolve("journal.log")).contains("COMMITTED"));
        assertEquals("profile-bytes", Files.readString(backup.resolve("offline").resolve("vanilla").resolve(from + ".dat")));
    }

    @Test void backsUpCollidingTargetBeforeOverwrite() throws Exception {
        Path src = Files.writeString(temp.resolve(from + ".dat"), "new");
        Path dst = Files.writeString(temp.resolve(to + ".dat"), "existing-verified");
        Path backup = temp.resolve("bk");

        MigrationExecutor.execute(plan(List.of(
                new MigrationPlanner.Candidate(src, dst, MigrationPlanner.Mode.BINARY_COPY, "vanilla"))),
                backup, from, to, null);

        assertEquals("new", Files.readString(dst));
        assertEquals("existing-verified",
                Files.readString(backup.resolve("verified-existing").resolve("vanilla").resolve(to + ".dat")));
    }

    @Test void rewritesGlobalFileWhenItReferencesTheOfflineUuid() throws Exception {
        Path src = Files.writeString(temp.resolve(from + ".dat"), "x");
        Path dst = temp.resolve(to + ".dat");
        Path global = Files.writeString(temp.resolve("players.snbt"), "a:" + from + "\nb:someone-else");
        Path backup = temp.resolve("bk");

        MigrationExecutor.execute(plan(List.of(
                new MigrationPlanner.Candidate(src, dst, MigrationPlanner.Mode.BINARY_COPY, "vanilla"))),
                backup, from, to, global);

        String g = Files.readString(global);
        assertTrue(g.contains(to.toString()) && !g.contains(from.toString()));
        assertTrue(g.contains("someone-else"));
    }

    @EnabledOnOs({OS.LINUX, OS.MAC})
    @Test void refusesSymlinkSourceWithoutMutating() throws Exception {
        Path real = Files.writeString(temp.resolve("real.dat"), "data");
        Path link = Files.createSymbolicLink(temp.resolve(from + ".dat"), real);
        Path dst = temp.resolve(to + ".dat");

        assertThrows(IOException.class, () -> MigrationExecutor.execute(plan(List.of(
                new MigrationPlanner.Candidate(link, dst, MigrationPlanner.Mode.BINARY_COPY, "vanilla"))),
                temp.resolve("bk"), from, to, null));
        assertFalse(Files.exists(dst));
        assertTrue(Files.exists(link));
    }

    @EnabledOnOs({OS.LINUX, OS.MAC})
    @Test void refusesSymlinkTarget() throws Exception {
        Path src = Files.writeString(temp.resolve(from + ".dat"), "data");
        Path real = Files.writeString(temp.resolve("real.dat"), "old");
        Path dst = Files.createSymbolicLink(temp.resolve(to + ".dat"), real);

        assertThrows(IOException.class, () -> MigrationExecutor.execute(plan(List.of(
                new MigrationPlanner.Candidate(src, dst, MigrationPlanner.Mode.BINARY_COPY, "vanilla"))),
                temp.resolve("bk"), from, to, null));
    }

    @Test void refusesOversizedTextSource() throws Exception {
        Path src = temp.resolve(from + ".snbt");
        try (RandomAccessFile raf = new RandomAccessFile(src.toFile(), "rw")) {
            raf.setLength(MigrationExecutor.MAX_TEXT_MIGRATION_BYTES + 1); // sparse, instant
        }
        Path dst = temp.resolve(to + ".snbt");

        assertThrows(IOException.class, () -> MigrationExecutor.execute(plan(List.of(
                new MigrationPlanner.Candidate(src, dst, MigrationPlanner.Mode.UUID_TEXT_REWRITE, "mod"))),
                temp.resolve("bk"), from, to, null));
        assertFalse(Files.exists(dst));
    }

    @Test void rollsBackAppliedTargetsWhenALaterOperationFails() throws Exception {
        Path src1 = Files.writeString(temp.resolve(from + ".dat"), "one");
        Path dst1 = temp.resolve(to + ".dat");                       // new target: rollback must delete it
        Path src2 = Files.writeString(temp.resolve(from + ".json"), "two");
        // Make op2's target parent a regular FILE so createDirectories fails mid-apply.
        Path blocker = Files.writeString(temp.resolve("blocker"), "");
        Path dst2 = blocker.resolve("nested").resolve(to + ".json");
        Path backup = temp.resolve("bk");

        assertThrows(IOException.class, () -> MigrationExecutor.execute(plan(List.of(
                new MigrationPlanner.Candidate(src1, dst1, MigrationPlanner.Mode.BINARY_COPY, "vanilla"),
                new MigrationPlanner.Candidate(src2, dst2, MigrationPlanner.Mode.BINARY_COPY, "mod"))),
                backup, from, to, null));

        assertFalse(Files.exists(dst1), "op1 target rolled back (deleted)");
        assertEquals("one", Files.readString(src1), "op1 source restored/never lost");
        assertEquals("two", Files.readString(src2), "op2 source untouched");
        assertTrue(Files.readString(backup.resolve("journal.log")).contains("ROLLED_BACK"));
    }

    @Test void cleanupMovesFilesAsideAndStripsGlobalLines() throws Exception {
        Path a = Files.writeString(temp.resolve(from + ".dat"), "a");
        Path b = Files.writeString(temp.resolve(from + ".json"), "b");
        Path missing = temp.resolve(from + ".cosarmor");
        Path global = Files.writeString(temp.resolve("players.snbt"),
                "keep-me\nrank:" + from + "\nkeep-too\ncompact:" + from.toString().replace("-", ""));
        Path backup = temp.resolve("bk");

        MigrationExecutor.CleanupOutcome outcome = MigrationExecutor.cleanup(List.of(
                new MigrationExecutor.GroupedPath(a, "vanilla"),
                new MigrationExecutor.GroupedPath(b, "mod"),
                new MigrationExecutor.GroupedPath(missing, "vanilla")), backup, from, global);

        assertEquals(2, outcome.cleanedFiles());
        assertTrue(outcome.cleanedGlobalRefs());
        assertFalse(Files.exists(a));
        assertFalse(Files.exists(b));
        assertEquals("a", Files.readString(backup.resolve("vanilla").resolve(from + ".dat")));
        List<String> kept = Files.readAllLines(global);
        assertEquals(List.of("keep-me", "keep-too"), kept);
    }

    @Test void backupDirNamesCarryContext() {
        Path up = MigrationExecutor.upgradeBackupDir(temp, "Al ice!", from, to);
        assertTrue(up.toString().contains("offline-upgrades"));
        assertTrue(up.getFileName().toString().contains("al_ice_") && up.getFileName().toString().contains("-to-" + to));
        Path cl = MigrationExecutor.cleanupBackupDir(temp, "Alice", from);
        assertTrue(cl.toString().contains("offline-cleanups") && cl.getFileName().toString().endsWith(from.toString()));
    }
}
