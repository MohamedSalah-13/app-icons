package com.hamza.icons;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IcnsTest {

    @Test
    void holdsEachSizeAsAPngUnderItsType() throws IOException {
        byte[] bytes = Icns.encode(TestIcon.DRAWING);
        ByteBuffer icns = ByteBuffer.wrap(bytes); // big-endian

        assertEquals("icns", text(bytes, 0));
        assertEquals(bytes.length, icns.getInt(4), "the header gives the whole file's length");

        Map<String, Integer> sides = new LinkedHashMap<>();
        int at = 8;
        while (at < bytes.length) {
            int length = icns.getInt(at + 4);
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes, at + 8, length - 8));
            assertEquals(image.getWidth(), image.getHeight());
            sides.put(text(bytes, at), image.getWidth());
            at += length;
        }
        assertEquals(bytes.length, at, "the entries fill the file exactly");

        Map<String, Integer> expected = new LinkedHashMap<>();
        expected.put("ic11", 32);
        expected.put("ic12", 64);
        expected.put("ic07", 128);
        expected.put("ic08", 256);
        expected.put("ic09", 512);
        expected.put("ic10", 1024);
        assertEquals(expected, sides);
    }

    private static String text(byte[] bytes, int at) {
        return new String(bytes, at, 4, StandardCharsets.US_ASCII);
    }
}
