package com.hamza.icons.fx;

import com.hamza.icons.IconSizes;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.io.IOException;
import java.io.InputStream;
import java.lang.System.Logger.Level;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * The program's icon on its windows, at every size it ships in. Windows picks the size that fits the title bar, the
 * taskbar and Alt+Tab; given one size only it scales that one, and the edges show it.
 *
 * <pre>{@code
 * private static final AppIcons ICONS = AppIcons.load(Main.class::getResourceAsStream, "/img/icon-%d.png");
 * ...
 * ICONS.applyTo(stage);
 * }</pre>
 *
 * The images are the PNGs {@link com.hamza.icons.IconSet#pngs(String)} writes into the program's resources.
 */
public final class AppIcons {
    private static final System.Logger LOG = System.getLogger(AppIcons.class.getName());
    private static final String SIZE = "%d";

    private final List<Image> images;

    private AppIcons(List<Image> images) {
        this.images = List.copyOf(images);
    }

    /**
     * Loads the icon at {@link IconSizes#window() the window sizes}, or at the sizes given. A size whose file is
     * missing or unreadable is logged and left out, so a program never fails to start over its icon.
     *
     * @param resources opens a resource of the program by name, or returns null when there is none. Pass
     *                  {@code Main.class::getResourceAsStream}: written in the program, it reads the program's
     *                  resources even from a module that does not open their package
     * @param pattern   the resources' name with {@code %d} where the size goes: {@code /img/icon-%d.png}
     */
    public static AppIcons load(Function<String, InputStream> resources, String pattern, int... sizes) {
        Objects.requireNonNull(resources, "resources");
        List<Image> loaded = new ArrayList<>();
        for (String name : names(pattern, sizes)) {
            try (InputStream in = resources.apply(name)) {
                if (in == null) {
                    LOG.log(Level.WARNING, "Missing window icon resource: {0}", name);
                    continue;
                }
                Image image = new Image(in);
                if (image.isError()) {
                    LOG.log(Level.WARNING, "Unreadable window icon " + name, image.getException());
                    continue;
                }
                loaded.add(image);
            } catch (IOException | RuntimeException e) {
                LOG.log(Level.WARNING, "Unable to load window icon " + name, e);
            }
        }
        return new AppIcons(loaded);
    }

    /** The resource names the pattern stands for, smallest size first as given. */
    static List<String> names(String pattern, int... sizes) {
        Objects.requireNonNull(pattern, "pattern");
        if (!pattern.contains(SIZE)) {
            throw new IllegalArgumentException("The pattern needs %d where the size goes: " + pattern);
        }
        int[] chosen = sizes == null || sizes.length == 0 ? IconSizes.window() : sizes;
        List<String> names = new ArrayList<>();
        for (int size : chosen) {
            names.add(pattern.replace(SIZE, Integer.toString(size)));
        }
        return names;
    }

    /** The images that loaded, in the order of their sizes. */
    public List<Image> images() {
        return images;
    }

    /** The largest image that loaded, for an About box or a login screen. */
    public Optional<Image> largest() {
        return images.stream().max((a, b) -> Double.compare(a.getWidth(), b.getWidth()));
    }

    /** Puts the icon on a window; does nothing for null. */
    public void applyTo(Stage stage) {
        if (stage != null && !images.isEmpty()) {
            stage.getIcons().setAll(images);
        }
    }

    /** Puts the icon on a dialog's window: a dialog does not take its owner's icon. */
    public void applyTo(Window window) {
        if (window instanceof Stage stage) {
            applyTo(stage);
        }
    }
}
