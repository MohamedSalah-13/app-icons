package com.hamza.icons;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Locale;

final class Pwa {
    private Pwa() {
    }

    static byte[] maskablePng(IconDrawing drawing, int size, Color background) {
        // Fit the entire square drawing inside the maskable safe circle (diameter = 80%).
        // An even side centers the mark on whole pixels in our even-sized output.
        int safeSide = 2 * (int) Math.floor(0.4 * size / Math.sqrt(2));
        return opaquePng(drawing, size, safeSide, background);
    }

    static byte[] appleTouchPng(IconDrawing drawing, Color background) {
        return opaquePng(drawing, 180, 180, background);
    }

    private static byte[] opaquePng(IconDrawing drawing, int size, int markSize, Color background) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setColor(background);
            g.fillRect(0, 0, size, size);
            int inset = (size - markSize) / 2;
            g.drawImage(drawing.render(markSize), inset, inset, null);
        } finally {
            g.dispose();
        }
        return Images.png(image);
    }

    static String icon(String file, int size, String purpose) {
        return "{\"src\":\"" + file + "\",\"sizes\":\"" + size + "x" + size
                + "\",\"type\":\"image/png\",\"purpose\":\"" + purpose + "\"}";
    }

    static String manifest(String name, String icons, Color background) {
        return """
                {
                  "name": "%1$s",
                  "short_name": "%1$s",
                  "start_url": "./",
                  "scope": "./",
                  "display": "standalone",
                  "background_color": "%3$s",
                  "icons": [
                    %2$s
                  ]
                }
                """.formatted(escapeJson(name), icons, String.format(Locale.ROOT, "#%06x", background.getRGB() & 0xffffff));
    }

    private static String escapeJson(String text) {
        StringBuilder escaped = new StringBuilder();
        for (char c : text.toCharArray()) {
            if (c == '"' || c == '\\') {
                escaped.append('\\').append(c);
            } else if (c < 0x20 || Character.isSurrogate(c)) {
                escaped.append("\\u%04x".formatted((int) c));
            } else {
                escaped.append(c);
            }
        }
        return escaped.toString();
    }
}
