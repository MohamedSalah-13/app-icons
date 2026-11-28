package com.hamza.icons;

import java.awt.Color;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;
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
 * An Android app's launcher icon is written the same way, into its {@code res} folder: see
 * {@link #androidLauncher(String)} and {@link #androidAdaptive(String, IconDrawing, Color, Color)}.
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
        int[] chosen = Ico.checkedSizes(sizes);
        return file(path, () -> Ico.renderFrames(drawing, chosen));
    }

    /** A macOS icon. */
    public IconSet icns(String path) {
        return file(path, () -> Icns.encode(drawing));
    }

    /** One PNG: 512 for a Linux package, or any size for a README. */
    public IconSet png(String path, int size) {
        checkPngSize(size);
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
        if (sizes == null || sizes.length == 0) {
            throw new IllegalArgumentException("At least one size is needed");
        }
        int[] chosen = sizes.clone();
        for (int size : chosen) {
            checkPngSize(size);
        }
        for (int size : chosen) {
            png(pattern.replace(SIZE, Integer.toString(size)), size);
        }
        return this;
    }

    /**
     * An Android app's launcher icon as launchers before Android 8 show it: the whole picture, at 48 dp in each of
     * the five densities, as {@code mipmap-<density>/ic_launcher.png}.
     *
     * @param res the app's resource folder: {@code android/app/src/main/res}
     */
    public IconSet androidLauncher(String res) {
        Objects.requireNonNull(res, "res");
        for (int density = 0; density < AndroidRes.DENSITIES.length; density++) {
            png(AndroidRes.mipmap(res, density, AndroidRes.NAME + ".png"), AndroidRes.ICON_PIXELS[density]);
        }
        return this;
    }

    /** An adaptive icon on one colour; see {@link #androidAdaptive(String, IconDrawing, Color, Color)}. */
    public IconSet androidAdaptive(String res, IconDrawing foreground, Color background) {
        return androidAdaptive(res, foreground, background, background);
    }

    /**
     * An Android app's adaptive icon, which Android 8 and later cut to the launcher's own shape: the mark alone as
     * {@code mipmap-<density>/ic_launcher_foreground.png}, the colours behind it as
     * {@code drawable/ic_launcher_background.xml}, and {@code mipmap-anydpi-v26/ic_launcher.xml} naming the two.
     * The foreground is also given as the monochrome layer, which a themed launcher tints by its shape alone.
     *
     * <p>Call {@link #androidLauncher(String)} as well: older launchers, and this set's own drawing, need it.
     *
     * @param foreground the mark with no tile, on a transparent square of 108 dp. A launcher shows the middle 72
     *                   and may cut that to a circle of 66, so the mark stays within the middle 66
     * @param top        the background's colour at the top
     * @param bottom     its colour at the bottom; the same colour for a plain background
     */
    public IconSet androidAdaptive(String res, IconDrawing foreground, Color top, Color bottom) {
        Objects.requireNonNull(res, "res");
        Objects.requireNonNull(foreground, "foreground");
        Objects.requireNonNull(top, "top");
        Objects.requireNonNull(bottom, "bottom");
        for (int density = 0; density < AndroidRes.DENSITIES.length; density++) {
            int pixels = AndroidRes.LAYER_PIXELS[density];
            file(AndroidRes.mipmap(res, density, AndroidRes.NAME + "_foreground.png"),
                    () -> Images.png(foreground.render(pixels)));
        }
        file(AndroidRes.path(res, "mipmap-anydpi-v26/" + AndroidRes.NAME + ".xml"),
                () -> AndroidRes.adaptiveIcon().getBytes(StandardCharsets.UTF_8));
        file(AndroidRes.path(res, "drawable/" + AndroidRes.NAME + "_background.xml"),
                () -> AndroidRes.background(top, bottom).getBytes(StandardCharsets.UTF_8));
        return this;
    }

    /** Adds web and maskable icons, an Apple touch icon, and a manifest rooted at the output directory. */
    public IconSet pwa(String appName) {
        return pwa(appName, Color.WHITE, null);
    }

    /**
     * Adds a PWA set with an opaque background for the maskable and Apple touch icons.
     * An optional JSON object supplies manifest fields; the generated top-level icons array
     * replaces the template's array. Passing a template requires Gson.
     */
    public IconSet pwa(String appName, Color background, String manifestTemplate) {
        Objects.requireNonNull(appName, "appName");
        if (appName.isBlank()) {
            throw new IllegalArgumentException("The manifest needs an application name");
        }
        checkOpaqueBackground(background);
        PwaTemplate template = manifestTemplate == null ? null : new PwaTemplate(manifestTemplate);
        StringJoiner entries = new StringJoiner(",\n    ");
        for (int size : new int[]{192, 512}) {
            String standard = "icon-" + size + ".png";
            String maskable = "maskable-" + size + ".png";
            png(standard, size);
            file(maskable, () -> Pwa.maskablePng(drawing, size, background));
            entries.add(Pwa.icon(standard, size, "any"));
            entries.add(Pwa.icon(maskable, size, "maskable"));
        }
        String defaults = Pwa.manifest(appName, entries.toString(), background);
        String manifest = template == null ? defaults : template.merge(defaults);
        file("apple-touch-icon.png", () -> Pwa.appleTouchPng(drawing, background));
        return file("manifest.webmanifest", () -> manifest.getBytes(StandardCharsets.UTF_8));
    }

    /** Adds a standalone HTML preview with the small, Apple touch and masked launcher icons. */
    public IconSet preview(Color background) {
        checkOpaqueBackground(background);
        return file("preview.html", () -> IconPreview.html(drawing, background));
    }

    /** Adds an Android launcher preview and optionally the web icon previews on the same page. */
    public IconSet androidPreview(IconDrawing foreground, Color background, boolean includeWeb) {
        Objects.requireNonNull(foreground, "foreground");
        checkOpaqueBackground(background);
        return file("preview.html", () -> IconPreview.androidHtml(drawing, foreground, background, includeWeb));
    }

    private static void checkOpaqueBackground(Color background) {
        if (Objects.requireNonNull(background, "background").getAlpha() != 255) {
            throw new IllegalArgumentException("The PWA icon background must be opaque");
        }
    }

    /** Lists the absolute output paths without rendering or creating any files. */
    public List<Path> outputPaths(Path root) {
        return List.copyOf(OutputFiles.targetsUnder(root.toAbsolutePath().normalize(), files).keySet());
    }

    /** Writes the files under the current directory; see {@link #write(Path)}. */
    public List<Path> write() throws IOException {
        return write(Path.of(""));
    }

    /**
     * Leaves byte-identical files untouched and stages new or changed files. A failed replacement triggers restoration of
     * earlier files. Individual replacements require atomic moves on the destination filesystem.
     *
     * @param root the directory the paths are relative to, usually the project's
     * @return all requested output paths, including unchanged files, in request order
     */
    public List<Path> write(Path root) throws IOException {
        Path base = Objects.requireNonNull(root, "root").toAbsolutePath().normalize();
        return OutputFiles.write(base, files);
    }

    /** Reasons an expected output failed its byte-for-byte check. */
    public enum Difference { MISSING, DIFFERENT }

    /**
     * Compares this set with the files under root without creating outputs or directories.
     * Returns only differing or missing paths, in request order. Unrelated files are ignored.
     */
    public Map<Path, Difference> check(Path root) throws IOException {
        Path base = Objects.requireNonNull(root, "root").toAbsolutePath().normalize();
        return OutputFiles.check(base, files);
    }

    private IconSet file(String path, Supplier<byte[]> content) {
        Objects.requireNonNull(path, "path");
        files.remove(path); // so that the order is that of the last request
        files.put(path, content);
        return this;
    }

    private static void checkPngSize(int size) {
        if (size < 1) {
            throw new IllegalArgumentException("An icon needs a positive size: " + size);
        }
    }
}
