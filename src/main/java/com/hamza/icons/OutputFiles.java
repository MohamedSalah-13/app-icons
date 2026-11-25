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
import java.util.function.Supplier;

import static java.nio.file.LinkOption.NOFOLLOW_LINKS;
import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static java.nio.file.StandardCopyOption.COPY_ATTRIBUTES;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

/** Stages output beside each destination so that replacing a single file is an atomic rename. */
final class OutputFiles implements AutoCloseable {
    private final List<Output> outputs = new ArrayList<>();

    static List<Path> write(Path base, Map<String, Supplier<byte[]>> files) throws IOException {
        if (files.isEmpty()) {
            return new ArrayList<>();
        }
        Map<Path, Supplier<byte[]>> targets = targetsUnder(base, files);
        Files.createDirectories(base);
        Path realBase = base.toRealPath();
        try (OutputFiles batch = new OutputFiles()) {
            for (var entry : targets.entrySet()) {
                Path target = entry.getKey();
                Path parent = target.getParent();
                checkParent(base, realBase, parent);
                Files.createDirectories(parent);
                Output output = new Output(target, Files.createTempDirectory(parent, ".app-icons-"));
                batch.outputs.add(output);
                Files.write(output.next(), entry.getValue().get());
            }
            commit(batch.outputs);
            return new ArrayList<>(targets.keySet());
        }
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

    /** Keep backups and replacements ordered even when two callers write overlapping sets. */
    private static synchronized void commit(List<Output> outputs) throws IOException {
        for (Output output : outputs) {
            if (Files.exists(output.target, NOFOLLOW_LINKS)) {
                if (!Files.isRegularFile(output.target, NOFOLLOW_LINKS)) {
                    throw new IOException("Expected a regular output file: " + output.target);
                }
                if (!Files.isWritable(output.target)) {
                    throw new IOException("Output file is read-only: " + output.target);
                }
                Files.copy(output.target, output.previous(), COPY_ATTRIBUTES);
                var permissions = Files.getFileAttributeView(output.target, PosixFileAttributeView.class);
                if (permissions != null) {
                    Files.setPosixFilePermissions(output.next(), permissions.readAttributes().permissions());
                }
                output.hadOriginal = true;
            }
        }
        int replaced = 0;
        try {
            for (Output output : outputs) {
                Files.move(output.next(), output.target, ATOMIC_MOVE, REPLACE_EXISTING);
                replaced++;
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
        boolean hadOriginal;
        boolean keepBackup;

        Output(Path target, Path directory) {
            this.target = target;
            this.directory = directory;
        }

        Path next() {
            return directory.resolve("next");
        }

        Path previous() {
            return directory.resolve("previous");
        }
    }
}
