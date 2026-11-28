package com.hamza.icons;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.DigestInputStream;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static java.nio.file.LinkOption.NOFOLLOW_LINKS;

/** Portable ownership record. Only names supplied by the generator can become deletion targets. */
final class GeneratedFiles {
    static final String NAME = ".app-icons-generated";
    static final String LOCK = ".app-icons-write.lock";
    private static final String HEADER = "app-icons generated files v1";
    final Path index;
    private final Path base;
    private final Map<String, Path> allowed = new HashMap<>();
    private final Map<String, String> hashes = new TreeMap<>();

    GeneratedFiles(Path base, Set<Path> possible) throws IOException {
        this.base = base;
        index = base.resolve(NAME);
        for (Path path : possible) {
            if (!path.startsWith(base) || !path.equals(path.normalize()) || path.equals(base)
                    || path.equals(index) || path.equals(base.resolve(LOCK))) {
                throw new IllegalArgumentException("Invalid generated output path: " + path);
            }
            allowed.put(name(path), path);
        }
        if (Files.notExists(index, NOFOLLOW_LINKS)) {
            return;
        }
        if (!Files.isRegularFile(index, NOFOLLOW_LINKS)) {
            throw new IOException("Expected a regular tracking file: " + index);
        }
        List<String> lines = Files.readAllLines(index);
        if (lines.isEmpty() || !HEADER.equals(lines.getFirst())) {
            throw new IOException("Unrecognized tracking file: " + index);
        }
        for (String line : lines.subList(1, lines.size())) {
            int split = line.indexOf(' ');
            String name = line.substring(split + 1);
            if (split != 64 || !allowed.containsKey(name) || !line.substring(0, split).matches("[0-9a-f]{64}")
                    || hashes.putIfAbsent(name, line.substring(0, split)) != null) {
                throw new IOException("Invalid entry in " + index + ": " + line);
            }
        }
    }

    List<Path> stale(Collection<Path> current) throws IOException {
        List<Path> obsolete = new ArrayList<>();
        for (String name : List.copyOf(hashes.keySet())) {
            Path path = allowed.get(name);
            if (current.contains(path)) {
                continue;
            }
            // Never follow a redirected directory, including one pointing back inside the output tree.
            for (Path parent = path.getParent(); !parent.equals(base); parent = parent.getParent()) {
                if (Files.exists(parent, NOFOLLOW_LINKS)
                        && !parent.toRealPath().equals(base.toRealPath().resolve(base.relativize(parent)))) {
                    throw new IOException("Redirected generated output directory: " + path);
                }
            }
            if (Files.notExists(path, NOFOLLOW_LINKS)) {
                hashes.remove(name);
            } else {
                obsolete.add(path);
            }
        }
        return obsolete;
    }

    String takeForCleanup(Path path) {
        return hashes.remove(name(path));
    }

    void record(Path path, byte[] bytes, boolean changed) {
        String name = name(path);
        if (!allowed.containsKey(name)) {
            throw new IllegalArgumentException("Unknown generated output: " + path);
        }
        String hash = digest(bytes);
        if (changed || hash.equals(hashes.get(name))) {
            hashes.put(name, hash);
        } else {
            // A matching pre-existing file was never written by this generator.
            hashes.remove(name);
        }
    }

    byte[] contents() {
        StringBuilder text = new StringBuilder(HEADER).append('\n');
        hashes.forEach((name, hash) -> text.append(hash).append(' ').append(name).append('\n'));
        return text.toString().getBytes(StandardCharsets.UTF_8);
    }

    private String name(Path path) {
        return base.relativize(path).toString().replace(java.io.File.separatorChar, '/');
    }

    private static String digest(byte[] bytes) {
        return HexFormat.of().formatHex(sha256().digest(bytes));
    }

    static boolean matchesRecordedFile(Path path, String hash) throws IOException {
        if (!Files.isRegularFile(path, NOFOLLOW_LINKS)) {
            return false;
        }
        var digest = sha256();
        try (var in = new DigestInputStream(Files.newInputStream(path, NOFOLLOW_LINKS), digest)) {
            in.transferTo(OutputStream.nullOutputStream());
        }
        return HexFormat.of().formatHex(digest.digest()).equals(hash);
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }
}
