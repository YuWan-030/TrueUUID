package cn.alini.trueuuid.protocol;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

public final class MigrationPlanner {
    public enum Mode { BINARY_COPY, UUID_TEXT_REWRITE }
    public record Candidate(Path source, Path target, Mode mode, String group) {}
    public record Operation(Path source, Path target, Mode mode, String group, boolean targetExists) {}
    public record Plan(List<Operation> operations) {
        public Plan { operations = List.copyOf(operations); }
        public boolean isEmpty() { return operations.isEmpty(); }
    }

    public static Plan preflight(List<Candidate> candidates, Predicate<Path> exists) {
        List<Operation> operations = new ArrayList<>();
        Set<Path> targets = new HashSet<>();
        for (Candidate candidate : candidates) {
            Path source = candidate.source().toAbsolutePath().normalize();
            Path target = candidate.target().toAbsolutePath().normalize();
            if (source.equals(target)) throw new IllegalArgumentException("source and target must differ");
            if (!targets.add(target)) throw new IllegalArgumentException("duplicate migration target: " + target);
            if (exists.test(source)) operations.add(new Operation(source, target, candidate.mode(), candidate.group(), exists.test(target)));
        }
        return new Plan(operations);
    }

    private MigrationPlanner() {}
}
