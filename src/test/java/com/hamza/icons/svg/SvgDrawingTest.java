package com.hamza.icons.svg;

import com.hamza.icons.IconDrawing;
import com.hamza.icons.IconSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SvgDrawingTest {
    /** The left half red and opaque, the right half left transparent, on a 512 grid. */
    private static final String LEFT_HALF = """
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512">
              <rect width="256" height="512" fill="#ff0000"/>
            </svg>
            """;

    @Test
    void theDocumentFillsTheSquareAtEverySize() {
        IconDrawing drawing = SvgDrawing.of(LEFT_HALF);

        for (int size : new int[]{16, 48, 180, 512}) {
            BufferedImage image = drawing.render(size);
            assertEquals(size, image.getWidth());
            assertEquals(0xFFFF0000, image.getRGB(size / 4, size / 2), "left of " + size);
            assertEquals(0, image.getRGB(3 * size / 4, size / 2) >>> 24, "right of " + size);
        }
    }

    @Test
    void aGradientAndARoundedTileAreDrawn() {
        IconDrawing tile = SvgDrawing.of("""
                <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512">
                  <defs><linearGradient id="bg" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="0" stop-color="#0000ff"/><stop offset="1" stop-color="#00ff00"/>
                  </linearGradient></defs>
                  <rect width="512" height="512" rx="112" fill="url(#bg)"/>
                </svg>
                """);

        BufferedImage image = tile.render(256);

        assertEquals(0, image.getRGB(2, 2) >>> 24, "the rounded corner is cut away");
        int top = image.getRGB(128, 8), bottom = image.getRGB(128, 247);
        assertTrue((top & 0xFF) > 200 && ((top >> 8) & 0xFF) < 60, "blue at the top: " + Integer.toHexString(top));
        assertTrue(((bottom >> 8) & 0xFF) > 200 && (bottom & 0xFF) < 60, "green at the bottom: " + Integer.toHexString(bottom));
    }

    @Test
    void aFileIsReadAsUtf8(@TempDir Path folder) throws IOException {
        Path file = folder.resolve("icon.svg");
        Files.writeString(file, LEFT_HALF.replace("<rect", "<title>أيقونة</title><rect"));

        BufferedImage image = SvgDrawing.of(file).render(32);

        assertEquals(0xFFFF0000, image.getRGB(8, 16));
    }

    @Test
    void itIsADrawingLikeAnyOtherToAnIconSet(@TempDir Path root) throws IOException {
        IconSet.of(SvgDrawing.of(LEFT_HALF)).png("icons/app-192.png", 192).ico("favicon.ico", 16, 32).write(root);

        BufferedImage png = ImageIO.read(root.resolve("icons/app-192.png").toFile());
        assertEquals(192, png.getWidth());
        assertEquals(0xFFFF0000, png.getRGB(48, 96));
        assertTrue(Files.size(root.resolve("favicon.ico")) > 0);
    }

    @Test
    void refusesTextThatIsNotSvg() {
        assertThrows(IllegalArgumentException.class, () -> SvgDrawing.of("not an svg at all"));
    }
}
