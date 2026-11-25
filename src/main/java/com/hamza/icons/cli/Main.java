package com.hamza.icons.cli;

import com.hamza.icons.IconSet;
import com.hamza.icons.svg.SvgDrawing;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Command-line entry point for the standalone CLI jar. */
public final class Main {
    private static final String USAGE = "Usage: java -jar app-icons-<version>-cli.jar [--check] "
            + "[--pwa [--manifest-template <file>] [--background-color <#RRGGBB>]] <input.svg> <output-directory>";

    private Main() {
    }

    public static void main(String[] args) {
        int status = run(args);
        if (status != 0) {
            System.exit(status);
        }
    }

    private static int run(String[] args) {
        if (args.length == 1 && ("--help".equals(args[0]) || "-h".equals(args[0]))) {
            System.out.println(USAGE);
            System.out.println("Writes app.ico, app.icns, app.png (512px) and png/icon-<size>.png.");
            System.out.println("--pwa writes web icons, maskable icons, apple-touch-icon.png and manifest.webmanifest.");
            System.out.println("--manifest-template preserves custom manifest fields and replaces the top-level icons array.");
            System.out.println("--background-color fills maskable and Apple touch icons (default: #ffffff).");
            System.out.println("--check compares expected files without writing. Exit 0 for a match, 1 for differences or errors.");
            return 0;
        }
        Options options;
        try {
            options = Options.parse(args);
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            System.err.println(USAGE);
            return 2;
        }
        try {
            Path input = options.input();
            Path output = options.output();
            if (options.check()) {
                // JSVG decodes linked images through ImageIO, which otherwise caches URL input on disk.
                ImageIO.setUseCache(false);
            }
            IconSet icons = IconSet.of(SvgDrawing.of(input));
            if (options.pwa()) {
                String name = input.getFileName().toString().replaceFirst("(?i)\\.svg$", "").strip();
                icons.pwa(name.isEmpty() ? "App" : name, options.background(), options.readTemplate());
            } else {
                icons.ico("app.ico").icns("app.icns").png("app.png", 512).pngs("png/icon-%d.png");
            }
            if (options.check()) {
                var differences = icons.check(output);
                differences.forEach((path, reason) -> System.err.println(reason + " " + path));
                if (!differences.isEmpty()) {
                    return 1;
                }
                System.out.println("All expected files match.");
            } else {
                icons.write(output).forEach(System.out::println);
            }
            return 0;
        } catch (IOException | IllegalArgumentException e) {
            System.err.println("app-icons: " + e.getMessage());
            return 1;
        }
    }

    private record Options(Path input, Path output, boolean pwa, boolean check, Path template, Color background) {
        static Options parse(String[] args) {
            List<String> paths = new ArrayList<>(2);
            Set<String> flags = new HashSet<>();
            Path template = null;
            Color background = Color.WHITE;
            for (int i = 0; i < args.length; i++) {
                String arg = args[i];
                if (!arg.startsWith("--")) {
                    paths.add(arg);
                    continue;
                }
                if (!flags.add(arg)) {
                    throw new IllegalArgumentException("Repeated option: " + arg);
                }
                switch (arg) {
                    case "--pwa", "--check" -> { }
                    case "--manifest-template" -> template = Path.of(value(args, ++i, arg));
                    case "--background-color" -> {
                        String color = value(args, ++i, arg);
                        if (!color.matches("#[0-9a-fA-F]{6}")) {
                            throw new IllegalArgumentException("Expected #RRGGBB for " + arg);
                        }
                        background = Color.decode(color);
                    }
                    default -> throw new IllegalArgumentException("Unknown option: " + arg);
                }
            }
            boolean pwa = flags.contains("--pwa");
            if (!pwa && (template != null || flags.contains("--background-color"))) {
                throw new IllegalArgumentException("Manifest templates and icon backgrounds require --pwa");
            }
            if (paths.size() != 2) {
                throw new IllegalArgumentException("Expected the SVG path and output directory");
            }
            return new Options(Path.of(paths.get(0)), Path.of(paths.get(1)), pwa, flags.contains("--check"),
                    template, background);
        }

        private static String value(String[] args, int index, String option) {
            if (index == args.length || args[index].isBlank() || args[index].startsWith("--")) {
                throw new IllegalArgumentException("Missing value for " + option);
            }
            return args[index];
        }

        String readTemplate() throws IOException {
            if (template == null) {
                return null;
            }
            Path manifest = output.resolve("manifest.webmanifest");
            if (Files.exists(manifest) && Files.isSameFile(manifest, template)) {
                throw new IllegalArgumentException("Use a separate template file from the output manifest: " + template);
            }
            return Files.readString(template);
        }
    }
}
