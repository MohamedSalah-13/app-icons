/**
 * A program's icon files from one drawing.
 *
 * <p>{@code com.hamza.icons} draws the icon and writes its files (.ico, .icns, PNG) when the icon changes; it needs
 * {@code java.desktop}. {@code com.hamza.icons.fx} loads the window icons while the program runs; it needs JavaFX.
 * {@code com.hamza.icons.svg} takes the drawing from an SVG instead of from code; it needs the JSVG library.
 * Applications using JavaFX or SVG declare the corresponding optional module in their own descriptor.
 */
module com.hamza.icons {
    requires transitive java.desktop;
    requires static javafx.graphics;
    requires static com.github.weisj.jsvg;

    exports com.hamza.icons;
    exports com.hamza.icons.fx;
    exports com.hamza.icons.svg;
}
