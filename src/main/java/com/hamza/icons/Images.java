package com.hamza.icons;

import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

final class Images {
    private Images() {
    }

    static byte[] png(BufferedImage image) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (var cache = new MemoryCacheImageOutputStream(out)) {
            if (!ImageIO.write(image, "png", cache)) {
                throw new IllegalStateException("This Java runtime has no PNG writer");
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e); // an in-memory stream does not fail
        }
        return out.toByteArray();
    }
}
