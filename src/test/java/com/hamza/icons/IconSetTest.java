package com.hamza.icons;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IconSetTest {
    @TempDir
    Path root;

    @Test
    void writesEveryFileAskedForInOrderCreatingDirectories() throws IOException {
        List<Path> written = IconSet.of(TestIcon.DRAWING)
                .ico("packaging/app.ico")
                .icns("packaging/app.icns")
                .png("packaging/app.png", 512)
                .pngs("src/main/resources/img/icon-%d.png", 16, 32)
                .write(root);

        assertEquals(List.of(
                root.resolve("packaging/app.ico"),
                root.resolve("packaging/app.icns"),
                root.resolve("packaging/app.png"),
                root.resolve("src/main/resources/img/icon-16.png"),
                root.resolve("src/main/resources/img/icon-32.png")), written);
        for (Path file : written) {
            assertTrue(Files.size(file) > 0, file + " has content");
        }
        assertArrayEquals(Icns.encode(TestIcon.DRAWING), Files.readAllBytes(root.resolve("packaging/app.icns")));
        assertEquals(512, ImageIO.read(root.resolve("packaging/app.png").toFile()).getWidth());
    }

    @Test
    void anIcoHasTheUsualSizesUnlessToldOtherwise() throws IOException {
        IconSet.of(TestIcon.DRAWING).ico("usual.ico").ico("chosen.ico", 16, 32).write(root);

        assertEquals(IconSizes.ico().length, frames(root.resolve("usual.ico")));
        assertEquals(2, frames(root.resolve("chosen.ico")));
    }

    @Test
    void thePngsAreTheDrawingAtTheWindowSizes() throws IOException {
        IconSet.of(TestIcon.DRAWING).pngs("img/icon-%d.png").write(root);

        for (int size : IconSizes.window()) {
            BufferedImage image = ImageIO.read(root.resolve("img/icon-" + size + ".png").toFile());
            IcoTest.assertSamePixels(TestIcon.DRAWING.render(size), image);
        }
        try (var files = Files.list(root.resolve("img"))) {
            assertEquals(IconSizes.window().length, files.count());
        }
    }

    @Test
    void writingAgainReplacesTheFiles() throws IOException {
        Files.writeString(root.resolve("app.png"), "stale");

        IconSet.of(TestIcon.DRAWING).png("app.png", 32).write(root);

        assertEquals(32, ImageIO.read(root.resolve("app.png").toFile()).getWidth());
    }

    @Test
    void refusesAPatternWithoutASizeAndAnEmptyListOfSizes() {
        IconSet set = IconSet.of(TestIcon.DRAWING);

        assertThrows(IllegalArgumentException.class, () -> set.pngs("img/icon.png"));
        assertThrows(IllegalArgumentException.class, () -> set.pngs("img/icon-%d.png", new int[0]));
        assertThrows(IllegalArgumentException.class, () -> set.ico("app.ico", new int[0]));
    }

    private static int frames(Path ico) throws IOException {
        return ByteBuffer.wrap(Files.readAllBytes(ico)).order(ByteOrder.LITTLE_ENDIAN).getShort(4);
    }
}
