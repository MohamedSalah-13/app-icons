package com.hamza.icons.cli;

import com.hamza.icons.IconSet;
import com.hamza.icons.svg.SvgDrawing;

import java.io.IOException;
import java.nio.file.Path;

/** Command-line entry point for the standalone CLI jar. */
public final class Main {
    private static final String USAGE = "Usage: java -jar app-icons-<version>-cli.jar [--pwa] <input.svg> <output-directory>";

    private Main() {
    }

    public static void main(String[] args) {
        if (args.length == 1 && ("--help".equals(args[0]) || "-h".equals(args[0]))) {
            System.out.println(USAGE);
            System.out.println("Writes app.ico, app.icns, app.png (512px) and png/icon-<size>.png.");
            System.out.println("--pwa writes web icons, maskable icons, apple-touch-icon.png and manifest.webmanifest.");
            return;
        }
        boolean pwa = args.length > 0 && "--pwa".equals(args[0]);
        int firstPath = pwa ? 1 : 0;
        if (args.length != firstPath + 2) {
            System.err.println(USAGE);
            System.exit(2);
        }
        try {
            Path input = Path.of(args[firstPath]);
            IconSet icons = IconSet.of(SvgDrawing.of(input));
            if (pwa) {
                String name = input.getFileName().toString().replaceFirst("(?i)\\.svg$", "").strip();
                icons.pwa(name.isEmpty() ? "App" : name);
            } else {
                icons.ico("app.ico").icns("app.icns").png("app.png", 512).pngs("png/icon-%d.png");
            }
            icons.write(Path.of(args[firstPath + 1])).forEach(System.out::println);
        } catch (IOException | IllegalArgumentException e) {
            System.err.println("app-icons: " + e.getMessage());
            System.exit(1);
        }
    }
}
