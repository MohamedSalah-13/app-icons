package com.hamza.icons;

import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IconDrawingTest {

    @Test
    void rendersATransparentSquareOfTheSizeAsked() {
        BufferedImage image = ((IconDrawing) (g, size) -> { }).render(48);

        assertEquals(48, image.getWidth());
        assertEquals(48, image.getHeight());
        assertEquals(0, image.getRGB(0, 0) >>> 24);
        assertEquals(0, image.getRGB(47, 47) >>> 24);
    }

    @Test
    void aGridDrawingFillsTheSameShareOfEverySize() {
        IconDrawing leftHalf = IconDrawing.grid(256, (g, size) -> {
            g.setColor(Color.RED);
            g.fillRect(0, 0, 128, 256);
        });

        for (int size : new int[]{16, 64, 256}) {
            BufferedImage image = leftHalf.render(size);
            assertEquals(0xFFFF0000, image.getRGB(size / 4, size / 2), "left of " + size);
            assertEquals(0, image.getRGB(3 * size / 4, size / 2), "right of " + size);
        }
    }

    @Test
    void theDrawingIsToldTheSizeInPixels() {
        int[] seen = new int[1];
        IconDrawing.grid(256, (g, size) -> seen[0] = size).render(24);

        assertEquals(24, seen[0]);
    }

    @Test
    void refusesASizeOrGridThatIsNotPositive() {
        assertThrows(IllegalArgumentException.class, () -> TestIcon.DRAWING.render(0));
        assertThrows(IllegalArgumentException.class, () -> IconDrawing.grid(0, (g, size) -> { }));
        assertThrows(IllegalArgumentException.class, () -> IconDrawing.grid(Double.NaN, (g, size) -> { }));
    }
}
