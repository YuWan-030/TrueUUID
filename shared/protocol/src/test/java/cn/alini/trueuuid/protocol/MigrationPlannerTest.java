package cn.alini.trueuuid.protocol;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MigrationPlannerTest {
    @TempDir Path temp;

    @Test void preflightIncludesOnlyExistingSourcesAndRecordsTargetCollision() throws Exception {
        Path source = Files.writeString(temp.resolve("offline.dat"), "data");
        Path target = Files.writeString(temp.resolve("verified.dat"), "old");
        Path missing = temp.resolve("missing.dat");
        var plan = MigrationPlanner.preflight(List.of(
                new MigrationPlanner.Candidate(source, target, MigrationPlanner.Mode.BINARY_COPY, "vanilla"),
                new MigrationPlanner.Candidate(missing, temp.resolve("other.dat"), MigrationPlanner.Mode.BINARY_COPY, "vanilla")
        ), Files::exists);
        assertEquals(1, plan.operations().size());
        assertTrue(plan.operations().get(0).targetExists());
    }

    @Test void rejectsDuplicateTargetsBeforeMutation() {
        Path target = temp.resolve("target");
        assertThrows(IllegalArgumentException.class, () -> MigrationPlanner.preflight(List.of(
                new MigrationPlanner.Candidate(temp.resolve("a"), target, MigrationPlanner.Mode.BINARY_COPY, "a"),
                new MigrationPlanner.Candidate(temp.resolve("b"), target, MigrationPlanner.Mode.BINARY_COPY, "b")
        ), ignored -> true));
    }
}
