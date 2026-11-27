package com.hamza.icons;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
    void regenerationLeavesMatchingFilesUntouchedAndRepairsChangedAndMissingFiles() throws IOException {
        IconSet set = IconSet.of(TestIcon.DRAWING).pngs("icon-%d.png", 16, 32, 64);
        List<Path> outputs = set.write(root);
        FileTime oldTime = FileTime.fromMillis(1_600_000_000_000L);
        for (Path file : outputs) {
            Files.setLastModifiedTime(file, oldTime);
        }
        assertEquals(outputs, set.write(root));
        for (Path file : outputs) {
            assertEquals(oldTime, Files.getLastModifiedTime(file));
        }
        Path changed = outputs.get(1);
        byte[] expected = Files.readAllBytes(changed);
        byte[] damaged = expected.clone();
        damaged[damaged.length - 1] ^= 1;
        Files.write(changed, damaged);
        Files.setLastModifiedTime(changed, oldTime);
        Files.delete(outputs.get(2));
        assertEquals(outputs, set.write(root));
        assertArrayEquals(expected, Files.readAllBytes(changed));
        assertEquals(oldTime, Files.getLastModifiedTime(outputs.get(0)));
        assertTrue(set.check(root).isEmpty());
    }

    @Test
    void renderFailureKeepsOriginalFilesAndDiscardsStagedOutputs() throws IOException {
        Path original = root.resolve("old.png");
        Files.writeString(original, "original");
        IconDrawing failsAt32 = (g, size) -> {
            if (size == 32) {
                throw new IllegalStateException("drawing failed");
            }
        };
        IconSet set = IconSet.of(failsAt32).png("old.png", 16).png("new.png", 16).png("last.png", 32);
        assertThrows(IllegalStateException.class, () -> set.write(root));
        assertEquals("original", Files.readString(original));
        try (var files = Files.list(root)) {
            assertEquals(List.of(original), files.toList());
        }
    }

    @Test
    void refusesEscapingPathsAndDirectoryTargetsBeforeReplacingFiles() throws IOException {
        Files.writeString(root.resolve("old.png"), "original");
        IconSet set = IconSet.of(TestIcon.DRAWING).png("old.png", 16);
        assertThrows(IllegalArgumentException.class, () -> set.png("../outside.png", 16).write(root));
        assertThrows(IllegalArgumentException.class,
                () -> IconSet.of(TestIcon.DRAWING).png(root.resolve("absolute.png").toString(), 16).write(root));
        Files.createDirectory(root.resolve("folder"));
        assertThrows(IOException.class,
                () -> IconSet.of(TestIcon.DRAWING).png("old.png", 16).png("folder", 16).write(root));
        assertEquals("original", Files.readString(root.resolve("old.png")));
    }

    @Test
    void rejectsAFileThatWouldAlsoBeAnOutputDirectory() {
        IconSet set = IconSet.of(TestIcon.DRAWING).png("icons/small.png", 16).png("icons", 32);
        assertThrows(IllegalArgumentException.class, () -> set.write(root));
        assertFalse(Files.exists(root.resolve("icons")));
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void restoresAlreadyReplacedFilesWhenWindowsDeniesTheLastRename() throws IOException {
        Path old = Files.writeString(root.resolve("old.png"), "old");
        Path locked = Files.writeString(root.resolve("locked.png"), "locked");
        Path unchanged = IconSet.of(TestIcon.DRAWING).png("unchanged.png", 16).write(root).get(0);
        FileTime oldTime = FileTime.fromMillis(1_600_000_000_000L);
        Files.setLastModifiedTime(unchanged, oldTime);
        Files.setLastModifiedTime(old, oldTime);
        // RandomAccessFile shares reads and writes on Windows, but does not share deletes/renames.
        try (var handle = new RandomAccessFile(locked.toFile(), "r")) {
            IconSet set = IconSet.of(TestIcon.DRAWING).png("old.png", 16).png("unchanged.png", 16)
                    .png("new.png", 16).png("locked.png", 16);
            assertThrows(IOException.class, () -> set.write(root));
            assertEquals("old", Files.readString(old));
            assertEquals(oldTime, Files.getLastModifiedTime(old));
            assertEquals(oldTime, Files.getLastModifiedTime(unchanged));
            assertFalse(Files.exists(root.resolve("new.png")));
            assertEquals("locked", Files.readString(locked));
        }
        try (var files = Files.list(root)) {
            assertEquals(3, files.count());
        }
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
