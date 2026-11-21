package com.hamza.icons;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.imageio.ImageIO;
import javax.xml.parsers.DocumentBuilderFactory;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AndroidIconsTest {
    private static final String ANDROID = "http://schemas.android.com/apk/res/android";
    private static final String RES = "android/app/src/main/res";
    /** A mark that is not the set's own drawing, so a foreground drawn from the wrong one shows. */
    private static final IconDrawing MARK = IconDrawing.grid(108, (g, size) -> {
        g.setColor(Color.RED);
        g.fillOval(21, 21, 66, 66);
    });

    @TempDir
    Path app;

    @Test
    void theLauncherIconIsTheWholePictureAt48DpInEveryDensity() throws IOException {
        IconSet.of(TestIcon.DRAWING).androidLauncher(RES).write(app);

        Map<String, Integer> pixels = Map.of("mdpi", 48, "hdpi", 72, "xhdpi", 96, "xxhdpi", 144, "xxxhdpi", 192);
        for (Map.Entry<String, Integer> density : pixels.entrySet()) {
            BufferedImage image = ImageIO.read(app.resolve(RES + "/mipmap-" + density.getKey() + "/ic_launcher.png").toFile());
            IcoTest.assertSamePixels(TestIcon.DRAWING.render(density.getValue()), image);
        }
    }

    @Test
    void theAdaptiveForegroundIsTheMarkAt108DpInEveryDensity() throws IOException {
        IconSet.of(TestIcon.DRAWING).androidAdaptive(RES, MARK, Color.BLUE).write(app);

        Map<String, Integer> pixels = Map.of("mdpi", 108, "hdpi", 162, "xhdpi", 216, "xxhdpi", 324, "xxxhdpi", 432);
        for (Map.Entry<String, Integer> density : pixels.entrySet()) {
            Path file = app.resolve(RES + "/mipmap-" + density.getKey() + "/ic_launcher_foreground.png");
            IcoTest.assertSamePixels(MARK.render(density.getValue()), ImageIO.read(file.toFile()));
        }
        assertFalse(Files.exists(app.resolve(RES + "/mipmap-mdpi/ic_launcher.png")),
                "the launcher icon is asked for on its own");
    }

    @Test
    void theAdaptiveIconNamesItsLayersAndTheForegroundAsMonochrome() throws Exception {
        IconSet.of(TestIcon.DRAWING).androidAdaptive(RES, MARK, Color.BLUE).write(app);

        Element icon = xml(app.resolve(RES + "/mipmap-anydpi-v26/ic_launcher.xml")).getDocumentElement();

        assertEquals("adaptive-icon", icon.getTagName());
        assertEquals("@drawable/ic_launcher_background", drawable(icon, "background"));
        assertEquals("@mipmap/ic_launcher_foreground", drawable(icon, "foreground"));
        assertEquals("@mipmap/ic_launcher_foreground", drawable(icon, "monochrome"));
    }

    @Test
    void twoColoursMakeABackgroundRunningFromTheTopToTheBottom() throws Exception {
        IconSet.of(TestIcon.DRAWING)
                .androidAdaptive(RES, MARK, new Color(0x13896B), new Color(0x0A5443))
                .write(app);

        Element shape = xml(app.resolve(RES + "/drawable/ic_launcher_background.xml")).getDocumentElement();
        Element gradient = (Element) shape.getElementsByTagName("gradient").item(0);

        assertEquals("rectangle", shape.getAttributeNS(ANDROID, "shape"));
        assertEquals("270", gradient.getAttributeNS(ANDROID, "angle"), "270 runs from the top down");
        assertEquals("#13896B", gradient.getAttributeNS(ANDROID, "startColor"));
        assertEquals("#0A5443", gradient.getAttributeNS(ANDROID, "endColor"));
    }

    @Test
    void oneColourMakesAPlainBackground() throws Exception {
        IconSet.of(TestIcon.DRAWING).androidAdaptive(RES, MARK, new Color(0x00, 0x0A, 0xFF)).write(app);

        Element shape = xml(app.resolve(RES + "/drawable/ic_launcher_background.xml")).getDocumentElement();

        assertEquals(0, shape.getElementsByTagName("gradient").getLength());
        assertEquals("#000AFF", ((Element) shape.getElementsByTagName("solid").item(0)).getAttributeNS(ANDROID, "color"));
    }

    @Test
    void aColourThatIsNotOpaqueCarriesItsAlphaFirst() {
        assertEquals("#80FF0000", AndroidRes.color(new Color(255, 0, 0, 128)));
        assertEquals("#FF0000", AndroidRes.color(Color.RED));
    }

    @Test
    void theResFolderMayEndWithASlash() throws IOException {
        List<Path> written = IconSet.of(TestIcon.DRAWING).androidLauncher("res/").write(app);

        assertEquals(app.resolve("res/mipmap-mdpi/ic_launcher.png"), written.get(0));
        assertTrue(Files.exists(app.resolve("res/mipmap-xxxhdpi/ic_launcher.png")));
    }

    private static Document xml(Path file) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory.newDocumentBuilder().parse(file.toFile());
    }

    private static String drawable(Element icon, String layer) {
        return ((Element) icon.getElementsByTagName(layer).item(0)).getAttributeNS(ANDROID, "drawable");
    }
}
