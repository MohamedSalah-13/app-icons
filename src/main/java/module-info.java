/**
 * A program's icon files from one drawing.
 *
 * <p>{@code com.hamza.icons} draws the icon and writes its files (.ico, .icns, PNG) when the icon changes; it needs
 * {@code java.desktop}. {@code com.hamza.icons.fx} loads the window icons while the program runs; it needs JavaFX.
 * Both are optional, so a program that uses one half does not carry the other's module.
 */
module com.hamza.icons {
    requires static transitive java.desktop;
    requires static transitive javafx.graphics;

    exports com.hamza.icons;
    exports com.hamza.icons.fx;
}
