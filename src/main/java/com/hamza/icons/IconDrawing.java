package com.hamza.icons;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.Objects;

/**
 * A program's icon as a drawing, so every size is drawn rather than scaled down from one picture: a drawing can drop
 * detail at 16 pixels, where a scaled picture only blurs.
 */
@FunctionalInterface
public interface IconDrawing {

    /**
     * Draws the icon into a transparent square of {@code size} pixels. Antialiasing is on; a drawing that wants
     * pixel-aligned shapes at small sizes turns it off itself.
     *
     * @param size the side of the picture in pixels, for choosing how much detail to draw
     */
    void draw(Graphics2D g, int size);

    /**
     * A drawing made on a fixed grid, for example 256 units a side, whatever the size in pixels.
     *
     * @param units the side of the grid the drawing's coordinates are in
     */
    static IconDrawing grid(double units, IconDrawing drawing) {
        Objects.requireNonNull(drawing, "drawing");
        if (!(units > 0)) {
            throw new IllegalArgumentException("The grid needs a positive side: " + units);
        }
        return (g, size) -> {
            g.scale(size / units, size / units);
            drawing.draw(g, size);
        };
    }

    /** The icon at one size, on a transparent background. */
    default BufferedImage render(int size) {
        if (size < 1) {
            throw new IllegalArgumentException("An icon needs a positive size: " + size);
        }
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            draw(g, size);
        } finally {
            g.dispose();
        }
        return image;
    }
}
