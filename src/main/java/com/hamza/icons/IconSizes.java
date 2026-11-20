package com.hamza.icons;

/** The sizes, in pixels, an icon is shipped in. Each call returns a fresh array. */
public final class IconSizes {
    private static final int[] ICO = {16, 20, 24, 32, 40, 48, 64, 96, 128, 256};
    private static final int[] WINDOW = {16, 24, 32, 48, 64, 128, 256};

    private IconSizes() {
    }

    /** What Explorer, the taskbar and a shortcut ask an .ico for at 100% to 250% display scaling. */
    public static int[] ico() {
        return ICO.clone();
    }

    /** What a window's title bar, the taskbar and Alt+Tab pick from. */
    public static int[] window() {
        return WINDOW.clone();
    }
}
