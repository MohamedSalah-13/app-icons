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
        return page(webSection(drawing, background), "");
    }

    static byte[] androidHtml(IconDrawing drawing, IconDrawing foreground, Color background, boolean includeWeb) {
        int density = 2; // xhdpi: use the same pixel sizes as the generated Android files.
        String android = template("icon-preview-android.html")
                .replace("{{legacy}}", pngUrl(Images.png(drawing.render(AndroidRes.ICON_PIXELS[density]))))
                .replace("{{foreground}}", pngUrl(Images.png(foreground.render(AndroidRes.LAYER_PIXELS[density]))))
                .replace("{{background}}", AndroidRes.color(background));
        return page(includeWeb ? android + webSection(drawing, background) : android,
                template("icon-preview-android.css"));
    }

    private static String webSection(IconDrawing drawing, Color background) {
        return template("icon-preview-web.html")
                .replace("{{small16}}", pngUrl(Images.png(drawing.render(16))))
                .replace("{{small32}}", pngUrl(Images.png(drawing.render(32))))
                .replace("{{apple}}", pngUrl(Pwa.appleTouchPng(drawing, background)))
                .replace("{{maskable}}", pngUrl(Pwa.maskablePng(drawing, 192, background)));
    }

    private static byte[] page(String sections, String androidStyles) {
        return template("icon-preview.html").replace("{{sections}}", sections)
                .replace("{{androidStyles}}", androidStyles).getBytes(StandardCharsets.UTF_8);
    }

    private static String template(String name) {
        try (var in = IconPreview.class.getResourceAsStream(name)) {
            if (in == null) {
                throw new IllegalStateException("Missing " + name + " in the app-icons jar");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + name, e);
        }
    }

    private static String pngUrl(byte[] png) {
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(png);
    }
}
