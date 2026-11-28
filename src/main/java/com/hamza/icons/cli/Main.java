package com.hamza.icons.cli;

import com.hamza.icons.IconDrawing;
import com.hamza.icons.IconSet;
import com.hamza.icons.svg.SvgDrawing;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Command-line entry point for the standalone CLI jar. */
public final class Main {
    private static final String USAGE = "Usage: java -jar app-icons-<version>-cli.jar [--check] [--preview] "
            + "[--android [--foreground <file.svg>] [--monochrome <file.svg>]] [--pwa [--manifest-template <file>]] "
            + "[--background-color <#RRGGBB>] <input.svg> <output-directory>";

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
            System.out.println("--android writes legacy and adaptive launcher icons into an Android res directory.");
            System.out.println("--foreground supplies a separate adaptive foreground SVG (default: input SVG).");
            System.out.println("--monochrome supplies a separate themed icon SVG (default: adaptive foreground).");
            System.out.println("--preview writes a self-contained preview.html with Android views when --android is set.");
            System.out.println("--manifest-template preserves custom manifest fields and replaces the top-level icons array.");
            System.out.println("--background-color sets the Android adaptive, maskable and Apple touch backgrounds (default: #ffffff).");
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
            // ImageIO would otherwise write a temporary cache while discovering linked images.
            ImageIO.setUseCache(false);
            Set<Path> inputs = new LinkedHashSet<>();
            IconDrawing drawing = SvgDrawing.of(input, inputs::add);
            IconDrawing foreground = options.foreground() == null ? drawing : SvgDrawing.of(options.foreground(), inputs::add);
            IconDrawing monochrome = options.monochrome() == null ? null : SvgDrawing.of(options.monochrome(), inputs::add);
            IconSet icons = options.iconSet(drawing, foreground, monochrome, null);
            if (options.template() != null) {
                inputs.add(options.template());
            }
            List<Path> outputs = icons.outputPaths(output);
            rejectInputOverwrites(outputs, inputs);
            if (options.android()) {
                AndroidResourceConflicts.check(output.toAbsolutePath().normalize(), outputs);
            }
            if (options.template() != null) {
                // A template changes the manifest contents, never the planned output paths.
                icons = options.iconSet(drawing, foreground, monochrome, Files.readString(options.template()));
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

    private static void rejectInputOverwrites(List<Path> outputs, Set<Path> inputs) throws IOException {
        for (Path output : outputs) {
            if (Files.notExists(output)) {
                continue;
            }
            for (Path input : inputs) {
                if (Files.isSameFile(output, input)) {
                    throw new IOException("Output " + output + " conflicts with input file " + input);
                }
            }
        }
    }

    private record Options(Path input, Path output, boolean pwa, boolean check, boolean preview,
                           boolean android, Path foreground, Path monochrome, Path template, Color background) {
        static Options parse(String[] args) {
            List<String> paths = new ArrayList<>(2);
            Set<String> flags = new HashSet<>();
            Path template = null;
            Path foreground = null;
            Path monochrome = null;
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
                    case "--pwa", "--check", "--preview", "--android" -> { }
                    case "--foreground" -> foreground = Path.of(value(args, ++i, arg));
                    case "--monochrome" -> monochrome = Path.of(value(args, ++i, arg));
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
            boolean preview = flags.contains("--preview");
            boolean android = flags.contains("--android");
            if (!android && foreground != null) {
                throw new IllegalArgumentException("--foreground requires --android");
            }
            if (!android && monochrome != null) {
                throw new IllegalArgumentException("--monochrome requires --android");
            }
            if (!pwa && template != null) {
                throw new IllegalArgumentException("Manifest templates require --pwa");
            }
            if (!pwa && !preview && !android && flags.contains("--background-color")) {
                throw new IllegalArgumentException("Icon backgrounds require --android, --pwa or --preview");
            }
            if (paths.size() != 2) {
                throw new IllegalArgumentException("Expected the SVG path and output directory");
            }
            return new Options(Path.of(paths.get(0)), Path.of(paths.get(1)), pwa, flags.contains("--check"), preview,
                    android, foreground, monochrome, template, background);
        }

        private static String value(String[] args, int index, String option) {
            if (index == args.length || args[index].isBlank() || args[index].startsWith("--")) {
                throw new IllegalArgumentException("Missing value for " + option);
            }
            return args[index];
        }

        IconSet iconSet(IconDrawing drawing, IconDrawing foregroundDrawing, IconDrawing monochromeDrawing,
                        String manifestTemplate) {
            IconSet icons = IconSet.of(drawing);
            if (android) {
                icons.androidLauncher("").androidAdaptive("", foregroundDrawing, background, background, monochromeDrawing);
            }
            if (pwa) {
                String name = input.getFileName().toString().replaceFirst("(?i)\\.svg$", "").strip();
                icons.pwa(name.isEmpty() ? "App" : name, background, manifestTemplate);
            } else if (!preview && !android) {
                icons.ico("app.ico").icns("app.icns").png("app.png", 512).pngs("png/icon-%d.png");
            }
            if (preview) {
                if (android) {
                    icons.androidPreview(foregroundDrawing, background, pwa, monochromeDrawing);
                } else {
                    icons.preview(background);
                }
            }
            return icons;
        }
    }
}
