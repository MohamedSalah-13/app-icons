package com.hamza.icons;

import com.github.weisj.jsvg.parser.SVGLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    void standaloneCliReportsHelpAndErrors() throws Exception {
        String jar = System.getProperty("cliJar");
        assertTrue(run(0, "java", "-jar", jar, "--help").contains("Usage"));
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
