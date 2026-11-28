package com.hamza.icons.svg;

import com.github.weisj.jsvg.SVGDocument;
import com.github.weisj.jsvg.parser.LoaderContext;
import com.github.weisj.jsvg.parser.SVGLoader;
import com.github.weisj.jsvg.view.ViewBox;
import com.hamza.icons.IconDrawing;

import java.awt.Component;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * An icon that is kept as an SVG rather than drawn in code: the document is the drawing, and every size is drawn
 * from it afresh, so a web app's icon files need no browser to make them.
 *
 * <pre>{@code
 * IconSet.of(SvgDrawing.of(Path.of("src/main/resources/static/icons/app.svg")))
 *         .png("src/main/resources/static/icons/app-192.png", 192)
 *         .png("src/main/resources/static/icons/app-512.png", 512)
 *         .write(projectRoot);
 * }</pre>
 *
 * This is the one part of the library that needs another: the program that uses it adds
 * {@code com.github.weisj:jsvg}, which reads the SVG. The other parts work without it.
 */
public final class SvgDrawing {

    private SvgDrawing() {
    }

    /** The picture an SVG file describes, including images referenced relative to that file. */
    public static IconDrawing of(Path file) throws IOException {
        return of(file, path -> {});
    }

    /** Also reports the SVG and each local image it reads to the caller. */
    public static IconDrawing of(Path file, Consumer<Path> inputFile) throws IOException {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(inputFile, "inputFile").accept(file);
        var images = new LocalImages(file, inputFile);
        try (InputStream in = Files.newInputStream(file)) {
            SVGDocument document = new SVGLoader().load(in, file.toUri(), images.context());
            images.checkForFailure();
            if (document == null) {
                throw new IllegalArgumentException("Not an SVG that can be read: " + file);
            }
            return drawing(document);
        }
    }

    /**
     * The picture a piece of SVG describes, fitted to the icon's square. The document should carry a square
     * {@code viewBox}; what it draws there fills the icon at every size.
     *
     * @throws IllegalArgumentException if the text is not an SVG that can be read
     */
    public static IconDrawing of(String svg) {
        Objects.requireNonNull(svg, "svg");
        SVGDocument document = new SVGLoader().load(
                new ByteArrayInputStream(svg.getBytes(StandardCharsets.UTF_8)), null, LoaderContext.createDefault());
        if (document == null) {
            throw new IllegalArgumentException("Not an SVG that can be read: " + abbreviated(svg));
        }
        return drawing(document);
    }

    private static IconDrawing drawing(SVGDocument document) {
        return (g, size) -> document.render((Component) null, g, new ViewBox(0, 0, size, size));
    }

    private static String abbreviated(String svg) {
        String oneLine = svg.strip().replaceAll("\\s+", " ");
        return oneLine.length() <= 60 ? oneLine : oneLine.substring(0, 60) + "...";
    }
}
