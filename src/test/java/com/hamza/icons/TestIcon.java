package com.hamza.icons;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Ellipse2D;

/** A drawing with opaque, half-transparent and transparent pixels, so a wrong channel order or row order shows. */
final class TestIcon {
    static final IconDrawing DRAWING = IconDrawing.grid(256, TestIcon::draw);

    private TestIcon() {
    }

    private static void draw(Graphics2D g, int size) {
        g.setColor(new Color(0x1D4ED8));
        g.fillRect(0, 0, 256, 128);                          // the top half blue, the bottom left transparent
        g.setColor(new Color(0xF5, 0x9E, 0x0B, 128));
        g.fill(new Ellipse2D.Double(96, 96, 128, 128));      // a half-transparent disc towards the bottom right
    }
}
