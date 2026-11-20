package com.hamza.icons;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** The macOS icon file. */
public final class Icns {
    /** The entries that hold a PNG, with the size each one is drawn at ({@code ic11} and {@code ic12} are the 2x of 16 and 32). */
    private static final String[] TYPES = {"ic11", "ic12", "ic07", "ic08", "ic09", "ic10"};
    private static final int[] SIZES = {32, 64, 128, 256, 512, 1024};

    private Icns() {
    }

    /** An .icns of the drawing: a big-endian header, then each size as a PNG under its four-letter type. */
    public static byte[] encode(IconDrawing drawing) {
        Objects.requireNonNull(drawing, "drawing");
        ByteArrayOutputStream entries = new ByteArrayOutputStream();
        for (int i = 0; i < TYPES.length; i++) {
            byte[] png = Images.png(drawing.render(SIZES[i]));
            entries.writeBytes(TYPES[i].getBytes(StandardCharsets.US_ASCII));
            entries.writeBytes(length(8 + png.length));
            entries.writeBytes(png);
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes("icns".getBytes(StandardCharsets.US_ASCII));
        out.writeBytes(length(8 + entries.size()));
        out.writeBytes(entries.toByteArray());
        return out.toByteArray();
    }

    private static byte[] length(int value) {
        return ByteBuffer.allocate(4).putInt(value).array();
    }
}
