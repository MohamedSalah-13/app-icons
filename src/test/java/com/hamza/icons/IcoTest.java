package com.hamza.icons;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IcoTest {
    private static final int[] SIZES = {16, 24, 48, 256};

    @Test
    void theDirectoryListsEveryFrameAndWhereItIs() {
        ByteBuffer ico = little(Ico.encode(TestIcon.DRAWING, SIZES));

        assertEquals(0, ico.getShort(0));
        assertEquals(1, ico.getShort(2), "type: icon");
        assertEquals(SIZES.length, ico.getShort(4));
        int expectedOffset = 6 + 16 * SIZES.length;
        for (int i = 0; i < SIZES.length; i++) {
            int entry = 6 + 16 * i;
            int side = SIZES[i] == 256 ? 0 : SIZES[i];
            assertEquals(side, ico.get(entry) & 0xFF, "width of frame " + i);
            assertEquals(side, ico.get(entry + 1) & 0xFF, "height of frame " + i);
            assertEquals(1, ico.getShort(entry + 4), "planes");
            assertEquals(32, ico.getShort(entry + 6), "bits per pixel");
            assertEquals(expectedOffset, ico.getInt(entry + 12), "offset of frame " + i);
            expectedOffset += ico.getInt(entry + 8);
        }
        assertEquals(ico.capacity(), expectedOffset, "the frames fill the file exactly");
    }

    @Test
    void aSmallFrameIsABottomUpBgraBitmapWithItsMask() {
        BufferedImage source = TestIcon.DRAWING.render(24);
        ByteBuffer ico = little(Ico.encode(List.of(source)));
        int start = ico.getInt(6 + 12);
        int length = ico.getInt(6 + 8);

        assertEquals(40, ico.getInt(start), "BITMAPINFOHEADER");
        assertEquals(24, ico.getInt(start + 4));
        assertEquals(48, ico.getInt(start + 8), "the height counts the picture and the mask");
        assertEquals(32, ico.getShort(start + 14));
        assertEquals(0, ico.getInt(start + 16), "uncompressed");
        int maskBytes = 4 * 24; // 24 bits padded to 32 a row
        assertEquals(40 + 24 * 24 * 4 + maskBytes, length);
        for (int y = 0; y < 24; y++) {
            for (int x = 0; x < 24; x++) {
                int stored = ico.getInt(start + 40 + ((23 - y) * 24 + x) * 4);
                assertEquals(source.getRGB(x, y), stored, "pixel " + x + "," + y);
            }
        }
        for (int i = 0; i < maskBytes; i++) {
            assertEquals(0, ico.get(start + 40 + 24 * 24 * 4 + i), "mask byte " + i);
        }
    }

    @Test
    void the256FrameIsAPngOfTheSamePicture() throws IOException {
        BufferedImage source = TestIcon.DRAWING.render(256);
        byte[] bytes = Ico.encode(List.of(source));
        ByteBuffer ico = little(bytes);
        int start = ico.getInt(6 + 12);
        int length = ico.getInt(6 + 8);

        BufferedImage stored = ImageIO.read(new ByteArrayInputStream(bytes, start, length));

        assertSamePixels(source, stored);
    }

    @Test
    void theSizeFromWhichFramesArePngCanBeLowered() {
        List<BufferedImage> frames = List.of(TestIcon.DRAWING.render(64), TestIcon.DRAWING.render(128));
        ByteBuffer ico = little(Ico.encode(frames, 128));

        assertEquals(40, ico.getInt(ico.getInt(6 + 12)), "64 stays a bitmap");
        assertEquals(0x474E5089, ico.getInt(ico.getInt(6 + 16 + 12)), "128 starts with the PNG signature");
    }

    @Test
    void refusesFramesAnIcoCannotHold() {
        assertThrows(IllegalArgumentException.class, () -> Ico.encode(List.of()));
        assertThrows(IllegalArgumentException.class, () -> Ico.encode(TestIcon.DRAWING, 512));
        BufferedImage wide = new BufferedImage(32, 16, BufferedImage.TYPE_INT_ARGB);
        assertThrows(IllegalArgumentException.class, () -> Ico.encode(List.of(wide)));
    }

    static void assertSamePixels(BufferedImage expected, BufferedImage actual) {
        assertEquals(expected.getWidth(), actual.getWidth());
        assertEquals(expected.getHeight(), actual.getHeight());
        for (int y = 0; y < expected.getHeight(); y++) {
            for (int x = 0; x < expected.getWidth(); x++) {
                assertEquals(expected.getRGB(x, y), actual.getRGB(x, y), "pixel " + x + "," + y);
            }
        }
    }

    private static ByteBuffer little(byte[] bytes) {
        return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
    }
}
