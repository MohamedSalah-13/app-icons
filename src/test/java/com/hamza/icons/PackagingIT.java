package com.hamza.icons;

import com.github.weisj.jsvg.parser.SVGLoader;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.Strictness;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Compile and launch as a consumer would, using the artifacts from the package phase. */
class PackagingIT {
    private static final String SVG = """
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 32 32">
              <rect width="16" height="32" fill="#ff0000"/>
            </svg>
            """;

    @TempDir
    Path folder;

    @Test
    void modularDrawingUsesOnlyTheLibraryJar() throws Exception {
        runConsumer("", System.getProperty("packagedJar"),
                "com.hamza.icons.IconSet.of((g, size) -> {}).png(\"result.png\", 16).write();");
        assertEquals(16, ImageIO.read(folder.resolve("result.png").toFile()).getWidth());
    }

    @Test
    void modularWindowIconsUseJavaFxWithoutJsvg() throws Exception {
        Path graphics = Path.of(javafx.stage.Stage.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        Path base = Path.of(javafx.beans.Observable.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        String modules = String.join(File.pathSeparator, System.getProperty("packagedJar"),
                graphics.toString(), base.toString());
        runConsumer("requires javafx.graphics;", modules, """
                var icons = com.hamza.icons.fx.AppIcons.load(name -> null, "icon-%d.png", 16);
                if (icons.largest().isPresent()) throw new AssertionError("expected an empty icon set");
                """);
    }

    @Test
    void modularSvgUsesJsvgWithoutJavaFx() throws Exception {
        Path jsvg = Path.of(SVGLoader.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        Files.writeString(folder.resolve("icon.svg"), SVG);
        runConsumer("requires com.github.weisj.jsvg;", System.getProperty("packagedJar") + File.pathSeparator + jsvg,
                """
                var drawing = com.hamza.icons.svg.SvgDrawing.of(java.nio.file.Path.of("icon.svg"));
                com.hamza.icons.IconSet.of(drawing).png("result.png", 16).write();
                """);
        assertEquals(0xffff0000, ImageIO.read(folder.resolve("result.png").toFile()).getRGB(2, 2));
    }

    private void runConsumer(String extraRequires, String modulePath, String body) throws Exception {
        Files.writeString(folder.resolve("module-info.java"), "module consumer { requires com.hamza.icons; "
                + extraRequires + " }");
        Files.writeString(folder.resolve("Main.java"),
                "package consumer; public class Main { public static void main(String[] args) throws Exception {"
                        + body + "} }");
        run(0, "javac", "--module-path", modulePath, "-d", "classes", "module-info.java", "Main.java");
        run(0, "java", "--module-path", modulePath + File.pathSeparator + "classes", "-m", "consumer/consumer.Main");
    }

    @Test
    void standaloneCliWritesDesktopIconsFromSvg() throws Exception {
        Files.writeString(folder.resolve("my icon.svg"), SVG);
        run(0, "java", "-jar", System.getProperty("cliJar"), "my icon.svg", "app icons");
        Path out = folder.resolve("app icons");
        assertEquals(512, ImageIO.read(out.resolve("app.png").toFile()).getWidth());
        for (int size : IconSizes.window()) {
            var image = ImageIO.read(out.resolve("png/icon-" + size + ".png").toFile());
            assertEquals(size, image.getWidth());
            assertEquals(0xffff0000, image.getRGB(0, 0));
            assertEquals(0, image.getRGB(size - 1, 0));
        }
        assertTrue(Files.size(out.resolve("app.ico")) > 0);
        assertTrue(Files.size(out.resolve("app.icns")) > 0);
    }

    @Test
    void standaloneCliWritesPwaIconsReferencedByAValidManifest() throws Exception {
        Files.writeString(folder.resolve("My App.SVG"), SVG);
        run(0, "java", "-jar", System.getProperty("cliJar"), "--pwa", "My App.SVG", "web assets");
        Path out = folder.resolve("web assets");
        var json = new GsonBuilder().setStrictness(Strictness.STRICT).create();
        var manifest = json.fromJson(Files.readString(out.resolve("manifest.webmanifest")), JsonObject.class);
        assertEquals("My App", manifest.get("name").getAsString());
        assertEquals("My App", manifest.get("short_name").getAsString());
        assertEquals("./", manifest.get("start_url").getAsString());
        assertEquals("./", manifest.get("scope").getAsString());
        assertEquals("standalone", manifest.get("display").getAsString());
        Set<String> expected = new HashSet<>(Set.of("any-192", "any-512", "maskable-192", "maskable-512"));
        for (var entry : manifest.getAsJsonArray("icons")) {
            var icon = entry.getAsJsonObject();
            String purpose = icon.get("purpose").getAsString();
            String src = icon.get("src").getAsString();
            assertFalse(Path.of(src).isAbsolute());
            var image = ImageIO.read(out.resolve(src).toFile());
            int size = image.getWidth();
            assertEquals(size, image.getHeight());
            assertEquals(size + "x" + size, icon.get("sizes").getAsString());
            assertEquals("image/png", icon.get("type").getAsString());
            assertTrue(expected.remove(purpose + "-" + size));
            assertEquals(0xffff0000, image.getRGB(size / 3, size / 2));
            if (purpose.equals("maskable")) {
                assertFalse(image.getColorModel().hasAlpha());
                // The red mark must survive every supported mask, even near the square's corners.
                for (int y = 0; y < size; y++) {
                    for (int x = 0; x < size; x++) {
                        if (Math.hypot(x + 0.5 - size / 2.0, y + 0.5 - size / 2.0) > size * 0.4) {
                            assertEquals(0xffffffff, image.getRGB(x, y));
                        }
                    }
                }
            } else {
                assertEquals(0xffff0000, image.getRGB(0, 0));
                assertEquals(0, image.getRGB(size - 1, 0));
            }
        }
        assertTrue(expected.isEmpty());
        var apple = ImageIO.read(out.resolve("apple-touch-icon.png").toFile());
        assertEquals(180, apple.getWidth());
        assertEquals(180, apple.getHeight());
        assertEquals(0xffff0000, apple.getRGB(0, 0));
        assertEquals(0xffffffff, apple.getRGB(179, 0));
        assertFalse(Files.exists(out.resolve("app.ico")));
    }

    @Test
    void checkComparesDesktopFilesWithoutUpdatingThem() throws Exception {
        Path svg = Files.writeString(folder.resolve("logo.svg"), SVG);
        String jar = System.getProperty("cliJar");
        run(0, "java", "-jar", jar, "logo.svg", "desktop");
        Path out = folder.resolve("desktop");
        Path ico = out.resolve("app.ico");
        byte[] original = Files.readAllBytes(ico);
        Files.setLastModifiedTime(ico, FileTime.fromMillis(1000000000000L));
        FileTime modified = Files.getLastModifiedTime(ico);
        Files.writeString(out.resolve("unrelated.txt"), "keep");
        run(0, "java", "-Djava.io.tmpdir=absent-temp", "-jar", jar, "--check", "logo.svg", "desktop");
        assertEquals(modified, Files.getLastModifiedTime(ico));
        assertFalse(Files.exists(folder.resolve("absent-temp")));

        Files.writeString(svg, SVG.replace("#ff0000", "#0000ff"));
        String report = run(1, "java", "-jar", jar, "--check", "logo.svg", "desktop");
        assertTrue(report.contains("DIFFERENT " + ico));
        assertTrue(report.contains("DIFFERENT " + out.resolve("app.icns")));
        assertTrue(report.contains("DIFFERENT " + out.resolve("png/icon-16.png")));
        assertFalse(report.contains("unrelated.txt"));
        assertEquals(modified, Files.getLastModifiedTime(ico));
        assertArrayEquals(original, Files.readAllBytes(ico));
    }

    @Test
    void checkReportsMissingPwaIconAndSameLengthManifestEditTogether() throws Exception {
        Files.writeString(folder.resolve("My App.svg"), SVG);
        String jar = System.getProperty("cliJar");
        run(0, "java", "-jar", jar, "--pwa", "My App.svg", "site");
        run(0, "java", "-jar", jar, "--check", "--pwa", "My App.svg", "site");
        Path site = folder.resolve("site");
        Path missing = site.resolve("maskable-512.png");
        Files.delete(missing);
        Path manifest = site.resolve("manifest.webmanifest");
        String edited = Files.readString(manifest).replace("My App", "My Old");
        Files.writeString(manifest, edited);
        String report = run(1, "java", "-jar", jar, "--pwa", "--check", "My App.svg", "site");
        assertTrue(report.contains("MISSING " + missing));
        assertTrue(report.contains("DIFFERENT " + manifest));
        assertFalse(report.contains("apple-touch-icon.png"));
        assertFalse(Files.exists(missing));
        assertEquals(edited, Files.readString(manifest));
    }

    @Test
    void checkLeavesAMissingOutputDirectoryAbsent() throws Exception {
        Files.writeString(folder.resolve("logo.svg"), SVG);
        String report = run(1, "java", "-jar", System.getProperty("cliJar"),
                "--check", "logo.svg", "absent/nested");
        assertEquals(10, report.lines().filter(line -> line.startsWith("MISSING ")).count());
        assertFalse(Files.exists(folder.resolve("absent")));
    }

    @Test
    void pwaFailureLeavesExistingOutputsIntact() throws Exception {
        Files.writeString(folder.resolve("logo.svg"), SVG);
        Path out = Files.createDirectory(folder.resolve("site"));
        Files.createDirectory(out.resolve("manifest.webmanifest"));
        Path oldIcon = Files.writeString(out.resolve("icon-192.png"), "previous");
        run(1, "java", "-jar", System.getProperty("cliJar"), "--pwa", "logo.svg", "site");
        assertEquals("previous", Files.readString(oldIcon));
        assertFalse(Files.exists(out.resolve("apple-touch-icon.png")));
    }

    @Test
    void standaloneCliReportsHelpAndErrors() throws Exception {
        String jar = System.getProperty("cliJar");
        assertTrue(run(0, "java", "-jar", jar, "--help").contains("--pwa"));
        assertTrue(run(2, "java", "-jar", jar, "--pwa", "input.svg").contains("Usage"));
        assertTrue(run(2, "java", "-jar", jar).contains("Usage"));
        Files.writeString(folder.resolve("invalid.svg"), "invalid");
        assertTrue(run(1, "java", "-jar", jar, "invalid.svg", "icons").contains("app-icons:"));
        assertFalse(Files.exists(folder.resolve("icons")));
    }

    private String run(int expected, String tool, String... args) throws Exception {
        List<String> command = new ArrayList<>();
        command.add(Path.of(System.getProperty("java.home"), "bin", tool).toString());
        command.addAll(List.of(args));
        Process process = new ProcessBuilder(command).directory(folder.toFile()).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(expected, process.waitFor(), () -> command + System.lineSeparator() + output);
        return output;
    }
}
