package com.hamza.icons;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Every file a program's icon ships as, written from one drawing:
 *
 * <pre>{@code
 * IconSet.of(IconDrawing.grid(256, MyIcon::draw))
 *         .ico("packaging/app.ico")                         // the Windows launcher and installer
 *         .icns("packaging/app.icns")                       // the macOS app
 *         .png("packaging/app.png", 512)                    // the Linux package
 *         .pngs("src/main/resources/img/icon-%d.png")       // the windows of the running program
 *         .write(projectRoot);
 * }</pre>
 *
 * The files are meant to be committed; the program that draws them runs only when the icon changes.
 */
public final class IconSet {
    private static final String SIZE = "%d";

    private final IconDrawing drawing;
    /** In the order asked for; a path asked for twice keeps its last content. */
    private final Map<String, Supplier<byte[]>> files = new LinkedHashMap<>();

    private IconSet(IconDrawing drawing) {
        this.drawing = Objects.requireNonNull(drawing, "drawing");
    }

    public static IconSet of(IconDrawing drawing) {
        return new IconSet(drawing);
    }

    /** A Windows icon at {@link IconSizes#ico() the usual sizes}. */
    public IconSet ico(String path) {
        return ico(path, IconSizes.ico());
    }

    /** A Windows icon at the sizes given, each at most {@value Ico#MAX_SIZE}. */
    public IconSet ico(String path, int... sizes) {
        int[] chosen = some(sizes);
        return file(path, () -> Ico.encode(drawing, chosen));
    }

    /** A macOS icon. */
    public IconSet icns(String path) {
        return file(path, () -> Icns.encode(drawing));
    }

    /** One PNG: 512 for a Linux package, or any size for a README. */
    public IconSet png(String path, int size) {
        return file(path, () -> Images.png(drawing.render(size)));
    }

    /** A PNG for each of {@link IconSizes#window() the window sizes}; see {@link #pngs(String, int...)}. */
    public IconSet pngs(String pattern) {
        return pngs(pattern, IconSizes.window());
    }

    /**
     * A PNG for each size.
     *
     * @param pattern the files' path with {@code %d} where the size goes: {@code src/main/resources/img/icon-%d.png}
     */
    public IconSet pngs(String pattern, int... sizes) {
        Objects.requireNonNull(pattern, "pattern");
        if (!pattern.contains(SIZE)) {
            throw new IllegalArgumentException("The pattern needs %d where the size goes: " + pattern);
        }
        for (int size : some(sizes)) {
            png(pattern.replace(SIZE, Integer.toString(size)), size);
        }
        return this;
    }

    /** Writes the files under the current directory; see {@link #write(Path)}. */
    public List<Path> write() throws IOException {
        return write(Path.of(""));
    }

    /**
     * Writes the files, creating their directories and replacing what is there.
     *
     * @param root the directory the paths are relative to, usually the project's
     * @return the files written, in the order they were asked for
     */
    public List<Path> write(Path root) throws IOException {
        Path base = Objects.requireNonNull(root, "root").toAbsolutePath().normalize();
        List<Path> written = new ArrayList<>();
        for (Map.Entry<String, Supplier<byte[]>> file : files.entrySet()) {
            Path target = base.resolve(file.getKey()).normalize();
            Files.createDirectories(target.getParent());
            Files.write(target, file.getValue().get());
            written.add(target);
        }
        return written;
    }

    private IconSet file(String path, Supplier<byte[]> content) {
        Objects.requireNonNull(path, "path");
        files.remove(path); // so that the order is that of the last request
        files.put(path, content);
        return this;
    }

    private static int[] some(int[] sizes) {
        if (sizes == null || sizes.length == 0) {
            throw new IllegalArgumentException("At least one size is needed");
        }
        return sizes.clone();
    }
}
