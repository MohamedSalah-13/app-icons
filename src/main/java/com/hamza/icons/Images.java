package com.hamza.icons;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

final class Images {
    private Images() {
    }

    static byte[] png(BufferedImage image) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            if (!ImageIO.write(image, "png", out)) {
                throw new IllegalStateException("This Java runtime has no PNG writer");
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e); // an in-memory stream does not fail
        }
        return out.toByteArray();
    }
}
