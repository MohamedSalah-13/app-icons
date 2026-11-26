package com.hamza.icons;

import com.github.weisj.jsvg.parser.SVGLoader;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.Strictness;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Base64;
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
                "com.hamza.icons.IconSet.of((g, size) -> {}).png(\"result.png\", 16).pwa(\"App\").write();");
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
    void pwaTemplateKeepsNestedFieldsAndCheckUsesTheSameInputs() throws Exception {
        String jar = System.getProperty("cliJar");
        Files.writeString(folder.resolve("logo.svg"), SVG);
        Path template = folder.resolve("base manifest.json");
        String source = """
                {
                  "name": "بوابة الطلبات",
                  "start_url": "./inbox",
                  "background_color": "#070809",
                  "icons": [{"src":"obsolete.png"}],
                  "shortcuts": [{"name":"Inbox", "url":"./inbox", "icons":[{"src":"mail.png","extra":7}]}],
                  "custom": {"name":7, "shortcuts":false, "values":[1, null, false]}
                }
                """;
        Files.writeString(template, source);
        String[] options = {"--manifest-template", "base manifest.json", "--background-color", "#153A60",
                "--pwa", "logo.svg", "public"};
        var generate = new ArrayList<>(List.of("-jar", jar));
        generate.addAll(List.of(options));
        run(0, "java", generate.toArray(String[]::new));
        var check = new ArrayList<>(generate);
        check.add("--check");
        run(0, "java", check.toArray(String[]::new));
        assertEquals(source, Files.readString(template));

        Path out = folder.resolve("public");
        Path manifestPath = out.resolve("manifest.webmanifest");
        String originalManifest = Files.readString(manifestPath);
        var json = new GsonBuilder().setStrictness(Strictness.STRICT).create();
        var manifest = json.fromJson(originalManifest, JsonObject.class);
        var fields = json.fromJson(source, JsonObject.class);
        for (String key : List.of("name", "start_url", "background_color", "shortcuts", "custom")) {
            assertEquals(fields.get(key), manifest.get(key), key);
        }
        assertEquals(fields.get("name"), manifest.get("short_name"));
        assertEquals("standalone", manifest.get("display").getAsString());
        assertEquals(4, manifest.getAsJsonArray("icons").size());
        assertFalse(originalManifest.contains("obsolete.png"));
        for (String file : List.of("maskable-192.png", "maskable-512.png", "apple-touch-icon.png")) {
            var image = ImageIO.read(out.resolve(file).toFile());
            assertEquals(0xff153a60, image.getRGB(image.getWidth() - 1, 0));
        }
        assertEquals(0, ImageIO.read(out.resolve("icon-192.png").toFile()).getRGB(191, 0));

        check.set(check.indexOf("#153A60"), "#224466");
        String colorChanges = run(1, "java", check.toArray(String[]::new));
        assertTrue(colorChanges.contains("DIFFERENT " + out.resolve("maskable-192.png")));
        assertTrue(colorChanges.contains("DIFFERENT " + out.resolve("apple-touch-icon.png")));
        check.set(check.indexOf("#224466"), "#153A60");
        Files.writeString(template, source.replace("Inbox", "Messages"));
        String templateChanges = run(1, "java", check.toArray(String[]::new));
        assertTrue(templateChanges.contains("DIFFERENT " + manifestPath));
        assertFalse(templateChanges.contains("maskable-192.png"));
        assertEquals(originalManifest, Files.readString(manifestPath));
    }

    @Test
    void invalidTemplateFieldTypesFailGenerationAndCheckBeforeTouchingOutputs() throws Exception {
        Files.writeString(folder.resolve("logo.svg"), SVG);
        Files.writeString(folder.resolve("base.json"), """
                {"shortcuts":[{"name":"Inbox", "url":7}]}
                """);
        Path out = Files.createDirectory(folder.resolve("public"));
        Path manifest = Files.writeString(out.resolve("manifest.webmanifest"), "previous contents");
        String jar = System.getProperty("cliJar");
        String generate = run(1, "java", "-jar", jar, "--pwa", "--manifest-template", "base.json", "logo.svg", "public");
        String check = run(1, "java", "-jar", jar, "--check", "--pwa", "--manifest-template", "base.json", "logo.svg", "public");
        assertTrue(generate.contains("'shortcuts[0].url': expected string"), generate);
        assertEquals(generate, check);
        assertEquals("previous contents", Files.readString(manifest));
        try (var files = Files.list(out)) {
            assertEquals(List.of(manifest), files.toList());
        }
    }

    @Test
    void templateAndBackgroundAreIndependentlyOptional() throws Exception {
        String jar = System.getProperty("cliJar");
        Files.writeString(folder.resolve("logo.svg"), SVG);
        Files.writeString(folder.resolve("base.json"), "{\"theme_color\":\"navy\"}");
        Path out = folder.resolve("web");
        run(0, "java", "-jar", jar, "--pwa", "--manifest-template", "base.json", "logo.svg", "web");
        assertEquals(0xffffffff, ImageIO.read(out.resolve("maskable-512.png").toFile()).getRGB(0, 0));
        run(0, "java", "-jar", jar, "--pwa", "--background-color", "#112233", "logo.svg", "web");
        assertEquals(0xff112233, ImageIO.read(out.resolve("maskable-512.png").toFile()).getRGB(0, 0));
        assertTrue(Files.readString(out.resolve("manifest.webmanifest")).contains("\"background_color\": \"#112233\""));
    }

    @Test
    void invalidPwaInputsFailBeforeChangingExistingOutputs() throws Exception {
        String jar = System.getProperty("cliJar");
        Files.writeString(folder.resolve("logo.svg"), SVG);
        Path out = Files.createDirectory(folder.resolve("web"));
        Path manifest = Files.writeString(out.resolve("manifest.webmanifest"), "{}");
        Path bad = Files.writeString(folder.resolve("invalid.json"), "{broken");
        run(1, "java", "-jar", jar, "--pwa", "--manifest-template", bad.toString(), "logo.svg", "web");
        run(1, "java", "-jar", jar, "--pwa", "--manifest-template", manifest.toString(), "logo.svg", "web");
        run(2, "java", "-jar", jar, "--pwa", "--background-color", "#ffffff80", "logo.svg", "web");
        run(2, "java", "-jar", jar, "--manifest-template", "invalid.json", "logo.svg", "web");
        assertEquals("{}", Files.readString(manifest));
        assertFalse(Files.exists(out.resolve("icon-192.png")));
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
    void generationAndCheckResolveLinkedImagesBesideTheSvg() throws Exception {
        Path assets = Files.createDirectories(folder.resolve("art/images"));
        Path png = assets.resolve("brand mark.png");
        BufferedImage mark = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        mark.setRGB(0, 0, 0xffff0000);
        ImageIO.write(mark, "png", png.toFile());
        String svg = """
                <svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink" viewBox="0 0 2 1">
                  <image width="1" height="1" href="images/brand%20mark.png"/>
                  <image x="1" width="1" height="1" xlink:href="images/brand mark.png"/>
                </svg>
                """;
        Files.writeString(folder.resolve("art/logo.svg"), svg);
        String jar = System.getProperty("cliJar");
        run(0, "java", "-jar", jar, "--pwa", "art/logo.svg", "site");
        Path output = folder.resolve("site/icon-192.png");
        var image = ImageIO.read(output.toFile());
        assertEquals(0xffff0000, image.getRGB(48, 96));
        assertEquals(0xffff0000, image.getRGB(144, 96));
        run(0, "java", "-Djava.io.tmpdir=absent-temp", "-jar", jar, "--check", "--pwa", "art/logo.svg", "site");
        assertFalse(Files.exists(folder.resolve("absent-temp")));
        mark.setRGB(0, 0, 0xff0000ff);
        ImageIO.write(mark, "png", png.toFile());
        assertTrue(run(1, "java", "-jar", jar, "--check", "--pwa", "art/logo.svg", "site").contains("DIFFERENT"));

        byte[] previous = Files.readAllBytes(output);
        Files.delete(png);
        assertTrue(run(1, "java", "-jar", jar, "--pwa", "art/logo.svg", "site").contains("brand mark.png"));
        assertArrayEquals(previous, Files.readAllBytes(output));
        assertTrue(run(1, "java", "-jar", jar, "--check", "art/logo.svg", "absent").contains("brand mark.png"));
        assertFalse(Files.exists(folder.resolve("absent")));
    }

    @Test
    void refusesToReplaceALinkedImageBeforeWritingAnyPwaFiles() throws Exception {
        Path art = Files.createDirectory(folder.resolve("art"));
        Path site = Files.createDirectory(folder.resolve("site"));
        Path image = site.resolve("maskable-192.png");
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB), "png", image.toFile());
        byte[] original = Files.readAllBytes(image);
        Path sentinel = Files.writeString(site.resolve("icon-192.png"), "previous output");
        Files.writeString(art.resolve("logo.svg"), """
                <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 2 2">
                  <image width="2" height="2" href="../site/maskable%2D192.png"/>
                </svg>
                """);
        String jar = System.getProperty("cliJar");
        String generate = run(1, "java", "-Djava.io.tmpdir=absent-temp", "-jar", jar, "--pwa", "art/logo.svg", "site");
        String check = run(1, "java", "-jar", jar, "--check", "--pwa", "art/logo.svg", "site");
        for (String message : List.of(generate, check)) {
            assertTrue(message.contains("conflicts with input file") && message.contains("maskable-192.png"), message);
        }
        assertArrayEquals(original, Files.readAllBytes(image));
        assertEquals("previous output", Files.readString(sentinel));
        try (var files = Files.list(site)) {
            assertEquals(Set.of(image, sentinel), Set.copyOf(files.toList()));
        }
        assertFalse(Files.exists(folder.resolve("absent-temp")));
    }

    @Test
    void rejectsDesktopOutputThatAliasesTheSvgBeforeCreatingPngDirectory() throws Exception {
        Path svg = Files.writeString(folder.resolve("logo.svg"), SVG);
        Files.createLink(folder.resolve("app.png"), svg);
        String message = run(1, "java", "-jar", System.getProperty("cliJar"), "logo.svg", ".");
        assertTrue(message.contains("app.png") && message.contains("logo.svg"), message);
        assertTrue(Files.isSameFile(svg, folder.resolve("app.png")));
        assertEquals(SVG, Files.readString(svg));
        assertFalse(Files.exists(folder.resolve("app.ico")));
        assertFalse(Files.exists(folder.resolve("png")));
    }

    @Test
    void protectsTheTemplateAgainstAllSelectedOutputs() throws Exception {
        Files.writeString(folder.resolve("logo.svg"), SVG);
        Path template = Files.writeString(folder.resolve("apple-touch-icon.png"), "{}");
        String message = run(1, "java", "-jar", System.getProperty("cliJar"), "--pwa", "--preview",
                "--manifest-template", template.toString(), "logo.svg", ".");
        assertTrue(message.contains("conflicts with input file") && message.contains("apple-touch-icon.png"), message);
        assertEquals("{}", Files.readString(template));
        assertFalse(Files.exists(folder.resolve("preview.html")));
        assertFalse(Files.exists(folder.resolve("icon-192.png")));

        // The same directory is fine if the selected mode doesn't write the template's filename.
        Files.move(template, folder.resolve("preview.html"));
        run(0, "java", "-jar", System.getProperty("cliJar"), "--pwa", "--manifest-template", "preview.html",
                "logo.svg", ".");
        assertEquals("{}", Files.readString(folder.resolve("preview.html")));
    }

    @Test
    void pwaCheckMatchesAFreshGitCheckoutWithAutocrlf() throws Exception {
        String jar = System.getProperty("cliJar");
        Files.writeString(folder.resolve("logo.svg"), SVG);
        Files.writeString(folder.resolve(".gitattributes"), "*.webmanifest text eol=lf\n");
        run(0, "java", "-jar", jar, "--pwa", "logo.svg", "public");
        run(0, "git", "init", "--quiet");
        run(0, "git", "-c", "core.autocrlf=true", "add", "--", ".gitattributes", "logo.svg", "public");
        run(0, "git", "-c", "core.autocrlf=true", "checkout-index", "--all", "--prefix=checkout/");
        run(0, "java", "-jar", jar, "--check", "--pwa", "checkout/logo.svg", "checkout/public");
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
    void previewPageEmbedsActualSizeIconsAndBothCropsFromThePwaSet() throws Exception {
        String jar = System.getProperty("cliJar");
        Files.writeString(folder.resolve("logo.svg"), SVG);
        Path out = folder.resolve("preview");
        run(0, "java", "-jar", jar, "--preview", "--background-color", "#17324d", "logo.svg", "preview");
        Path page = out.resolve("preview.html");
        try (var files = Files.list(out)) {
            assertEquals(List.of(page), files.toList());
        }
        run(0, "java", "-jar", jar, "--check", "--preview", "--background-color", "#17324d", "logo.svg", "preview");
        byte[] standalone = Files.readAllBytes(page);
        run(0, "java", "-jar", jar, "--preview", "--pwa", "--background-color", "#17324d", "logo.svg", "preview");
        assertArrayEquals(standalone, Files.readAllBytes(page));
        assertTrue(Files.exists(out.resolve("manifest.webmanifest")));
        var document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(page.toFile());
        var images = document.getElementsByTagName("img");
        assertEquals(5, images.getLength());
        List<Integer> sizes = List.of(16, 32, 180, 192, 192);
        for (int i = 0; i < images.getLength(); i++) {
            Element element = (Element) images.item(i);
            int size = sizes.get(i);
            assertEquals(String.valueOf(size), element.getAttribute("width"));
            assertEquals(String.valueOf(size), element.getAttribute("height"));
            String src = element.getAttribute("src");
            String prefix = "data:image/png;base64,";
            assertTrue(src.startsWith(prefix));
            byte[] png = Base64.getDecoder().decode(src.substring(prefix.length()));
            var image = ImageIO.read(new ByteArrayInputStream(png));
            assertEquals(size, image.getWidth());
            assertEquals(size, image.getHeight());
            if (size <= 32) {
                assertEquals(0xffff0000, image.getRGB(0, 0));
                assertEquals(0, image.getRGB(size - 1, 0));
            } else {
                String asset = size == 180 ? "apple-touch-icon.png" : "maskable-192.png";
                assertArrayEquals(Files.readAllBytes(out.resolve(asset)), png);
                assertEquals(0xff17324d, image.getRGB(size - 1, 0));
            }
        }
        assertEquals("circle", ((Element) images.item(3)).getAttribute("class"));
        assertEquals("rounded-square", ((Element) images.item(4)).getAttribute("class"));
    }

    @Test
    void checkReportsAnOutdatedOrMissingPreviewWithoutRewritingIt() throws Exception {
        String jar = System.getProperty("cliJar");
        Files.writeString(folder.resolve("logo.svg"), SVG);
        Path page = folder.resolve("out/preview.html");
        assertTrue(run(1, "java", "-jar", jar, "--check", "--preview", "logo.svg", "out").contains("MISSING " + page));
        assertFalse(Files.exists(page.getParent()));
        run(0, "java", "-jar", jar, "--preview", "logo.svg", "out");
        byte[] original = Files.readAllBytes(page);
        assertTrue(run(1, "java", "-jar", jar, "--check", "--preview", "--background-color", "#222222",
                "logo.svg", "out").contains("DIFFERENT " + page));
        assertArrayEquals(original, Files.readAllBytes(page));
    }

    @Test
    void androidCliGeneratesAndChecksBothLauncherLayers() throws Exception {
        String jar = System.getProperty("cliJar");
        Files.writeString(folder.resolve("logo.svg"), SVG);
        Path art = Files.createDirectory(folder.resolve("foreground art"));
        BufferedImage green = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        green.setRGB(0, 0, 0xff00ff00);
        Path mark = art.resolve("mark.png");
        ImageIO.write(green, "png", mark.toFile());
        Files.writeString(art.resolve("layer.svg"), """
                <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 32 32">
                  <image href="mark.png" width="32" height="32"/>
                </svg>
                """);
        var args = new ArrayList<>(List.of("-jar", jar, "--android", "--foreground", "foreground art/layer.svg",
                "--background-color", "#17324d", "logo.svg", "res"));
        run(0, "java", args.toArray(String[]::new));
        Path res = folder.resolve("res");
        Path legacy = res.resolve("mipmap-mdpi/ic_launcher.png");
        Path foreground = res.resolve("mipmap-xxxhdpi/ic_launcher_foreground.png");
        var icon = ImageIO.read(legacy.toFile());
        assertEquals(48, icon.getWidth());
        assertEquals(0xffff0000, icon.getRGB(2, 2));
        var layer = ImageIO.read(foreground.toFile());
        assertEquals(432, layer.getWidth());
        assertEquals(0xff00ff00, layer.getRGB(216, 216));
        assertTrue(Files.readString(res.resolve("drawable/ic_launcher_background.xml")).contains("#17324D"));
        assertTrue(Files.readString(res.resolve("mipmap-anydpi-v26/ic_launcher.xml")).contains("@mipmap/ic_launcher_foreground"));
        try (var files = Files.walk(res)) {
            assertEquals(12, files.filter(Files::isRegularFile).count());
        }
        args.add("--check");
        run(0, "java", args.toArray(String[]::new));
        byte[] original = Files.readAllBytes(foreground);
        green.setRGB(0, 0, 0xff0000ff);
        ImageIO.write(green, "png", mark.toFile());
        Files.delete(legacy);
        String report = run(1, "java", args.toArray(String[]::new));
        assertTrue(report.contains("DIFFERENT " + foreground));
        assertTrue(report.contains("MISSING " + legacy));
        assertFalse(Files.exists(legacy));
        assertArrayEquals(original, Files.readAllBytes(foreground));
    }

    @Test
    void androidCliDefaultsToInputSvgAndWhiteBackground() throws Exception {
        String jar = System.getProperty("cliJar");
        Files.writeString(folder.resolve("logo.svg"), SVG);
        run(0, "java", "-jar", jar, "--android", "logo.svg", "res");
        var layer = ImageIO.read(folder.resolve("res/mipmap-mdpi/ic_launcher_foreground.png").toFile());
        assertEquals(0xffff0000, layer.getRGB(1, 1));
        assertEquals(0, layer.getRGB(107, 1) >>> 24);
        assertTrue(Files.readString(folder.resolve("res/drawable/ic_launcher_background.xml")).contains("#FFFFFF"));
        run(2, "java", "-jar", jar, "--foreground", "layer.svg", "logo.svg", "unused");
        assertFalse(Files.exists(folder.resolve("unused")));
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
        command.add(tool.equals("git") ? "git" : Path.of(System.getProperty("java.home"), "bin", tool).toString());
        command.addAll(List.of(args));
        Process process = new ProcessBuilder(command).directory(folder.toFile()).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(expected, process.waitFor(), () -> command + System.lineSeparator() + output);
        return output;
    }
}
