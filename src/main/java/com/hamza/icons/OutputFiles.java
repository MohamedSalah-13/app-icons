package com.hamza.icons;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.NoSuchFileException;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFileAttributeView;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import static java.nio.file.LinkOption.NOFOLLOW_LINKS;
import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static java.nio.file.StandardCopyOption.COPY_ATTRIBUTES;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

/** Stages output beside each destination so that replacing a single file is an atomic rename. */
final class OutputFiles implements AutoCloseable {
    private final List<Output> outputs = new ArrayList<>();

    // An overlapping write must not change a file between our comparison and commit.
    static synchronized List<Path> write(Path base, Map<String, Supplier<byte[]>> files) throws IOException {
        return writeBatch(base, targetsUnder(base, files), null, Map.of());
    }

    static synchronized List<Path> writeTracked(Path base, Map<String, Supplier<byte[]>> files,
            Set<Path> possible, Set<Path> inputs, boolean clean) throws IOException {
        Files.createDirectories(base);
        // CREATE_NEW serializes separate CLI processes as well as calls in this JVM.
        Path lock = Files.createFile(base.resolve(GeneratedFiles.LOCK));
        try {
            protectInput(base.resolve(GeneratedFiles.NAME), inputs);
            var ownership = new GeneratedFiles(base, possible);
            var targets = targetsUnder(base, files);
            for (Path target : targets.keySet()) {
                protectInput(target, inputs);
            }
            var removals = new LinkedHashMap<Path, String>();
            for (Path stale : ownership.stale(targets.keySet())) {
                if (clean) {
                    protectInput(stale, inputs);
                    removals.put(stale, ownership.takeForCleanup(stale));
                }
            }
            return writeBatch(base, targets, ownership, removals);
        } finally {
            Files.delete(lock);
        }
    }

    private static void protectInput(Path target, Set<Path> inputs) throws IOException {
        if (Files.exists(target)) {
            for (Path input : inputs) {
                if (Files.isSameFile(target, input)) {
                    throw new IOException("Output " + target + " conflicts with input file " + input);
                }
            }
        }
    }

    private static List<Path> writeBatch(Path base, Map<Path, Supplier<byte[]>> targets,
            GeneratedFiles ownership, Map<Path, String> removals) throws IOException {
        if (targets.isEmpty() && ownership == null) {
            return new ArrayList<>();
        }
        Files.createDirectories(base);
        Path realBase = base.toRealPath();
        try (OutputFiles batch = new OutputFiles()) {
            for (var entry : targets.entrySet()) {
                Path target = entry.getKey();
                checkParent(base, realBase, target.getParent());
                byte[] expected = entry.getValue().get();
                boolean changed = batch.stage(target, expected);
                if (ownership != null) {
                    ownership.record(target, expected, changed);
                }
            }
            for (var entry : removals.entrySet()) {
                batch.prepare(entry.getKey(), entry.getValue());
            }
            if (ownership != null) {
                batch.stage(ownership.index, ownership.contents());
            }
            commit(batch.outputs);
            return new ArrayList<>(targets.keySet());
        }
    }

    private boolean stage(Path target, byte[] expected) throws IOException {
        if (Files.isRegularFile(target, NOFOLLOW_LINKS) && matches(target, Files.size(target), expected)) {
            return false;
        }
        Files.createDirectories(target.getParent());
        Files.write(prepare(target, null).next(), expected);
        return true;
    }

    private Output prepare(Path target, String removalHash) throws IOException {
        Output output = new Output(target, Files.createTempDirectory(target.getParent(), ".app-icons-"), removalHash);
        outputs.add(output);
        return output;
    }

    static Map<Path, IconSet.Difference> check(Path base, Map<String, Supplier<byte[]>> files) throws IOException {
        var targets = targetsUnder(base, files);
        Map<Path, IconSet.Difference> differences = new LinkedHashMap<>();
        if (Files.notExists(base, NOFOLLOW_LINKS)) {
            targets.keySet().forEach(target -> differences.put(target, IconSet.Difference.MISSING));
            return differences;
        }
        Path realBase = base.toRealPath();
        for (var entry : targets.entrySet()) {
            Path target = entry.getKey();
            checkParent(base, realBase, target.getParent());
            try {
                var attributes = Files.readAttributes(target, BasicFileAttributes.class, NOFOLLOW_LINKS);
                if (!attributes.isRegularFile() || !matches(target, attributes.size(), entry.getValue().get())) {
                    differences.put(target, IconSet.Difference.DIFFERENT);
                }
            } catch (NoSuchFileException missing) {
                differences.put(target, IconSet.Difference.MISSING);
            }
        }
        return differences;
    }

    private static boolean matches(Path target, long size, byte[] expected) throws IOException {
        if (size != expected.length) {
            return false;
        }
        try (var input = Files.newInputStream(target, NOFOLLOW_LINKS)) {
            return Arrays.equals(expected, input.readNBytes(expected.length)) && input.read() == -1;
        }
    }

    private static void checkParent(Path base, Path realBase, Path parent) throws IOException {
        Path existing = parent;
        while (!Files.exists(existing, NOFOLLOW_LINKS) && !existing.equals(base)) {
            existing = existing.getParent();
        }
        if (!existing.toRealPath().startsWith(realBase)) {
            throw new IOException("Output directory points outside " + base + ": " + parent);
        }
    }

    static Map<Path, Supplier<byte[]>> targetsUnder(Path base, Map<String, Supplier<byte[]>> files) {
        Map<Path, Supplier<byte[]>> targets = new LinkedHashMap<>();
        for (var entry : files.entrySet()) {
            Path relative = base.getFileSystem().getPath(entry.getKey());
            Path target = base.resolve(relative).normalize();
            if (relative.getRoot() != null || target.equals(base) || !target.startsWith(base)) {
                throw new IllegalArgumentException("Expected an output path under " + base + ": " + relative);
            }
            targets.remove(target); // keep the last request, including its position
            targets.put(target, entry.getValue());
        }
        for (Path target : targets.keySet()) {
            for (Path parent = target.getParent(); !parent.equals(base); parent = parent.getParent()) {
                if (targets.containsKey(parent)) {
                    throw new IllegalArgumentException("Requested both a file and a directory at " + parent);
                }
            }
        }
        return targets;
    }

    private static void commit(List<Output> outputs) throws IOException {
        for (Output output : outputs) {
            if (Files.exists(output.target, NOFOLLOW_LINKS)) {
                if (!Files.isRegularFile(output.target, NOFOLLOW_LINKS)) {
                    throw new IOException("Expected a regular output file: " + output.target);
                }
                if (!Files.isWritable(output.target)) {
                    throw new IOException("Output file is read-only: " + output.target);
                }
                if (output.removalHash == null) {
                    Files.copy(output.target, output.previous(), COPY_ATTRIBUTES);
                    var permissions = Files.getFileAttributeView(output.target, PosixFileAttributeView.class);
                    if (permissions != null) {
                        Files.setPosixFilePermissions(output.next(), permissions.readAttributes().permissions());
                    }
                } else if (!GeneratedFiles.matchesRecordedFile(output.target, output.removalHash)) {
                    throw new IOException("Refusing to remove modified generated file: " + output.target);
                }
                output.hadOriginal = true;
            }
        }
        int replaced = 0;
        try {
            for (Output output : outputs) {
                if (output.removalHash == null) {
                    Files.move(output.next(), output.target, ATOMIC_MOVE, REPLACE_EXISTING);
                    replaced++;
                } else {
                    Files.move(output.target, output.previous(), ATOMIC_MOVE);
                    output.hadOriginal = true;
                    replaced++;
                    // Verify the actual file we moved. A mismatch restores it along with the rest of the batch.
                    if (!GeneratedFiles.matchesRecordedFile(output.previous(), output.removalHash)) {
                        throw new IOException("Refusing to remove modified generated file: " + output.target);
                    }
                }
            }
        } catch (IOException | RuntimeException failure) {
            for (int i = replaced - 1; i >= 0; i--) {
                Output output = outputs.get(i);
                try {
                    if (output.hadOriginal) {
                        Files.move(output.previous(), output.target, ATOMIC_MOVE, REPLACE_EXISTING);
                    } else {
                        Files.delete(output.target);
                    }
                } catch (IOException | RuntimeException restoreFailure) {
                    output.keepBackup = output.hadOriginal;
                    failure.addSuppressed(new IOException("Could not restore " + output.target
                            + " (staging directory: " + output.directory + ")", restoreFailure));
                }
            }
            throw failure;
        }
    }

    @Override
    public void close() throws IOException {
        IOException failure = null;
        for (Output output : outputs) {
            try {
                Files.deleteIfExists(output.next());
                if (!output.keepBackup) {
                    Files.deleteIfExists(output.previous());
                    Files.delete(output.directory);
                }
            } catch (IOException cleanupFailure) {
                if (failure == null) {
                    failure = cleanupFailure;
                } else {
                    failure.addSuppressed(cleanupFailure);
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    private static final class Output {
        final Path target;
        final Path directory;
        final String removalHash;
        boolean hadOriginal;
        boolean keepBackup;

        Output(Path target, Path directory, String removalHash) {
            this.target = target;
            this.directory = directory;
            this.removalHash = removalHash;
        }

        Path next() {
            return directory.resolve("next");
        }

        Path previous() {
            return directory.resolve("previous");
        }
    }
}
