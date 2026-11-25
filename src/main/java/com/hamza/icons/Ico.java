package com.hamza.icons;

import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** The Windows icon file: one file holding the icon at several sizes. */
public final class Ico {
    /** The largest side an .ico frame can have. */
    public static final int MAX_SIZE = 256;

    private static final int FILE_HEADER = 6;
    private static final int ENTRY = 16;
    private static final int BITMAP_HEADER = 40;

    private Ico() {
    }

    /** The drawing at each of the sizes, in the order given. */
    public static byte[] encode(IconDrawing drawing, int... sizes) {
        Objects.requireNonNull(drawing, "drawing");
        return renderFrames(drawing, checkedSizes(sizes));
    }

    static int[] checkedSizes(int[] sizes) {
        Objects.requireNonNull(sizes, "sizes");
        checkFrameCount(sizes.length);
        int[] chosen = sizes.clone();
        for (int size : chosen) {
            checkSide(size);
        }
        return chosen;
    }

    static byte[] renderFrames(IconDrawing drawing, int[] sizes) {
        List<BufferedImage> frames = new ArrayList<>(sizes.length);
        for (int size : sizes) {
            frames.add(drawing.render(size));
        }
        return encode(frames);
    }

    /**
     * An .ico of the frames, in the order given: 256 as a PNG, which every Windows since Vista reads, and the rest as
     * 32-bit bitmaps, which every tool that handles an icon reads (Inno Setup and the resource compiler among them).
     */
    public static byte[] encode(List<BufferedImage> frames) {
        return encode(frames, MAX_SIZE);
    }

    /**
     * An .ico of the frames, in the order given.
     *
     * @param pngFrom frames of this side and larger are stored as PNG, smaller ones as 32-bit bitmaps
     */
    public static byte[] encode(List<BufferedImage> frames, int pngFrom) {
        Objects.requireNonNull(frames, "frames");
        checkFrameCount(frames.size());
        List<byte[]> bodies = new ArrayList<>();
        int total = FILE_HEADER + ENTRY * frames.size();
        for (BufferedImage frame : frames) {
            int size = side(frame);
            byte[] body = size >= pngFrom ? Images.png(frame) : bitmap(frame);
            bodies.add(body);
            total += body.length;
        }

        ByteBuffer out = ByteBuffer.allocate(total).order(ByteOrder.LITTLE_ENDIAN);
        out.putShort((short) 0);               // reserved
        out.putShort((short) 1);               // type: icon
        out.putShort((short) frames.size());
        int offset = FILE_HEADER + ENTRY * frames.size();
        for (int i = 0; i < frames.size(); i++) {
            int size = frames.get(i).getWidth();
            byte side = (byte) (size == MAX_SIZE ? 0 : size);   // 0 stands for 256
            out.put(side).put(side);
            out.put((byte) 0);                 // no palette
            out.put((byte) 0);                 // reserved
            out.putShort((short) 1);           // planes
            out.putShort((short) 32);          // bits per pixel
            out.putInt(bodies.get(i).length);
            out.putInt(offset);
            offset += bodies.get(i).length;
        }
        for (byte[] body : bodies) {
            out.put(body);
        }
        return out.array();
    }

    private static void checkFrameCount(int count) {
        if (count < 1 || count > 0xFFFF) {
            throw new IllegalArgumentException("An .ico needs between 1 and 65535 frames: " + count);
        }
    }

    private static int side(BufferedImage frame) {
        Objects.requireNonNull(frame, "frame");
        int size = frame.getWidth();
        if (size != frame.getHeight()) {
            throw new IllegalArgumentException("An .ico frame is square, not " + size + "x" + frame.getHeight());
        }
        checkSide(size);
        return size;
    }

    private static void checkSide(int size) {
        if (size < 1 || size > MAX_SIZE) {
            throw new IllegalArgumentException("An .ico frame is 1 to " + MAX_SIZE + " pixels a side, not " + size);
        }
    }

    /**
     * A frame as .ico stores a bitmap: a BITMAPINFOHEADER whose height counts the picture and its mask, the pixels
     * bottom-up as BGRA, then a 1-bit mask. The mask is all zero; the alpha channel carries the transparency, but the
     * format requires the mask to be there.
     */
    private static byte[] bitmap(BufferedImage image) {
        int size = image.getWidth();
        int maskRow = ((size + 31) / 32) * 4;
        int pixels = size * size * 4;
        int mask = maskRow * size;
        ByteBuffer out = ByteBuffer.allocate(BITMAP_HEADER + pixels + mask).order(ByteOrder.LITTLE_ENDIAN);
        out.putInt(BITMAP_HEADER);
        out.putInt(size);
        out.putInt(size * 2);                  // picture + mask
        out.putShort((short) 1);               // planes
        out.putShort((short) 32);              // bits per pixel
        out.putInt(0);                         // uncompressed
        out.putInt(pixels + mask);
        out.putInt(0).putInt(0);               // resolution: unused
        out.putInt(0).putInt(0);               // palette: none
        for (int y = size - 1; y >= 0; y--) {
            for (int x = 0; x < size; x++) {
                out.putInt(image.getRGB(x, y)); // ARGB written little-endian is B, G, R, A
            }
        }
        return out.array();                    // the mask bytes are already zero
    }
}
