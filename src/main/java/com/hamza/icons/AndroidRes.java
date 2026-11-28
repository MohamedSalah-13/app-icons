package com.hamza.icons;

import java.awt.Color;
import java.util.Locale;

/** What an Android app's {@code res} folder holds for its launcher icon, named {@code ic_launcher} as Android expects. */
final class AndroidRes {
    static final String NAME = "ic_launcher";

    /** The launcher's densities, with the pixels of the 48 dp icon and of the 108 dp adaptive layer in each. */
    static final String[] DENSITIES = {"mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi"};
    static final int[] ICON_PIXELS = {48, 72, 96, 144, 192};
    static final int[] LAYER_PIXELS = {108, 162, 216, 324, 432};

    private static final String WRITTEN_BY =
            "<!-- Written by app-icons (IconSet.androidAdaptive): change the drawing and write it again rather than edit. -->\n";

    private AndroidRes() {
    }

    static String mipmap(String res, int density, String file) {
        return path(res, "mipmap-" + DENSITIES[density] + "/" + file);
    }

    static String path(String res, String file) {
        return res.isEmpty() || res.endsWith("/") ? res + file : res + "/" + file;
    }

    /** The adaptive icon and the layer a themed launcher tints. */
    static String adaptiveIcon(boolean separateMonochrome) {
        String monochrome = separateMonochrome ? "_monochrome" : "_foreground";
        return "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                + WRITTEN_BY
                + "<adaptive-icon xmlns:android=\"http://schemas.android.com/apk/res/android\">\n"
                + "    <background android:drawable=\"@drawable/" + NAME + "_background\"/>\n"
                + "    <foreground android:drawable=\"@mipmap/" + NAME + "_foreground\"/>\n"
                + "    <monochrome android:drawable=\"@mipmap/" + NAME + monochrome + "\"/>\n"
                + "</adaptive-icon>\n";
    }

    /** The background layer as a drawable: one colour, or two running from the top to the bottom. */
    static String background(Color top, Color bottom) {
        String fill = top.equals(bottom)
                ? "    <solid android:color=\"" + color(top) + "\"/>\n"
                : "    <gradient android:angle=\"270\" android:startColor=\"" + color(top)
                        + "\" android:endColor=\"" + color(bottom) + "\"/>\n";
        return "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                + WRITTEN_BY
                + "<shape xmlns:android=\"http://schemas.android.com/apk/res/android\" android:shape=\"rectangle\">\n"
                + fill
                + "</shape>\n";
    }

    /** {@code #RRGGBB}, or {@code #AARRGGBB} for a colour that is not opaque. */
    static String color(Color color) {
        int rgb = color.getRGB() & 0xFFFFFF;
        return color.getAlpha() == 255
                ? String.format(Locale.ROOT, "#%06X", rgb)
                : String.format(Locale.ROOT, "#%02X%06X", color.getAlpha(), rgb);
    }
}
