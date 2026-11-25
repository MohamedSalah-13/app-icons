package com.hamza.icons;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;

final class Pwa {
    private Pwa() {
    }

    static int safeSquare(int size) {
        // Fit the entire square drawing inside the maskable safe circle (diameter = 80%).
        // An even side centers the mark on whole pixels in our even-sized output.
        return 2 * (int) Math.floor(0.4 * size / Math.sqrt(2));
    }

    static byte[] opaquePng(IconDrawing drawing, int size, int markSize) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setColor(Color.WHITE);
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

    static byte[] manifest(String name, String icons) {
        return """
                {
                  "name": "%1$s",
                  "short_name": "%1$s",
                  "start_url": "./",
                  "scope": "./",
                  "display": "standalone",
                  "background_color": "#ffffff",
                  "icons": [
                    %2$s
                  ]
                }
                """.formatted(escapeJson(name), icons).getBytes(StandardCharsets.UTF_8);
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
