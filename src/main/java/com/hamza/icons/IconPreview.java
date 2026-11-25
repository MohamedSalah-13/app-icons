package com.hamza.icons;

import java.awt.Color;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

final class IconPreview {
    private IconPreview() {
    }

    static byte[] html(IconDrawing drawing, Color background) {
        try (var in = IconPreview.class.getResourceAsStream("icon-preview.html")) {
            if (in == null) {
                throw new IllegalStateException("Missing icon-preview.html in the app-icons jar");
            }
            String template = new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
            return template.replace("{{small16}}", pngUrl(Images.png(drawing.render(16))))
                    .replace("{{small32}}", pngUrl(Images.png(drawing.render(32))))
                    .replace("{{apple}}", pngUrl(Pwa.appleTouchPng(drawing, background)))
                    .replace("{{maskable}}", pngUrl(Pwa.maskablePng(drawing, 192, background)))
                    .getBytes(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read the icon preview template", e);
        }
    }

    private static String pngUrl(byte[] png) {
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(png);
    }
}
