package com.hamza.icons.cli;

import com.hamza.icons.IconSet;
import com.hamza.icons.svg.SvgDrawing;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Command-line entry point for the standalone CLI jar. */
public final class Main {
    private static final String USAGE = "Usage: java -jar app-icons-<version>-cli.jar [--check] [--pwa] <input.svg> <output-directory>";

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
            System.out.println("--check compares expected files without writing. Exit 0 for a match, 1 for differences or errors.");
            return 0;
        }
        boolean pwa = false;
        boolean check = false;
        List<String> paths = new ArrayList<>(2);
        for (String arg : args) {
            if ("--pwa".equals(arg) && !pwa) {
                pwa = true;
            } else if ("--check".equals(arg) && !check) {
                check = true;
            } else if (arg.startsWith("--")) {
                System.err.println(USAGE);
                return 2;
            } else {
                paths.add(arg);
            }
        }
        if (paths.size() != 2) {
            System.err.println(USAGE);
            return 2;
        }
        try {
            Path input = Path.of(paths.get(0));
            Path output = Path.of(paths.get(1));
            if (check) {
                // JSVG decodes linked images through ImageIO, which otherwise caches URL input on disk.
                ImageIO.setUseCache(false);
            }
            IconSet icons = IconSet.of(SvgDrawing.of(input));
            if (pwa) {
                String name = input.getFileName().toString().replaceFirst("(?i)\\.svg$", "").strip();
                icons.pwa(name.isEmpty() ? "App" : name);
            } else {
                icons.ico("app.ico").icns("app.icns").png("app.png", 512).pngs("png/icon-%d.png");
            }
            if (check) {
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
}
