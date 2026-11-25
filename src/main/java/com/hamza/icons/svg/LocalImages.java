package com.hamza.icons.svg;

import com.github.weisj.jsvg.parser.DomDocument;
import com.github.weisj.jsvg.parser.LoaderContext;
import com.github.weisj.jsvg.parser.resources.RenderableResource;
import com.github.weisj.jsvg.parser.resources.ResourceLoader;
import com.github.weisj.jsvg.parser.resources.ResourcePolicy;
import com.github.weisj.jsvg.parser.resources.ResourceSupplier;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

/** Per-document resolution and error reporting for the synchronous JSVG image loader. */
final class LocalImages implements ResourcePolicy {
    private final ResourceLoader decoder = LoaderContext.createDefault().resourceLoader();
    private final Set<URI> loading = new HashSet<>();
    private final Consumer<Path> inputFile;
    private IOException failure;

    LocalImages(Consumer<Path> inputFile) {
        this.inputFile = inputFile;
    }

    LoaderContext context() {
        return LoaderContext.builder().externalResourcePolicy(this).resourceLoader(this::loadImage).build();
    }

    @Override
    public URI resolveResourceURI(URI base, String href) {
        if (href.startsWith("data:")) {
            return DENY_EXTERNAL.resolveResourceURI(base, href);
        }
        URI ref = URI.create(href.replace(" ", "%20"));
        if (ref.isAbsolute() || ref.getRawAuthority() != null || ref.getPath().startsWith("/")) {
            return null;
        }
        return base.resolve(ref);
    }

    @Override
    public URI resolveResourceURI(URI base, URI resolved) {
        // The image decoder calls the policy again with the URI resolved above.
        if ("file".equals(resolved.getScheme()) && resolved.getAuthority() == null) {
            return resolved;
        }
        return DENY_EXTERNAL.resolveResourceURI(base, resolved);
    }

    private ResourceSupplier<RenderableResource> loadImage(DomDocument document, URI uri) throws IOException {
        if (!"file".equals(uri.getScheme())) {
            return decoder.loadImage(document, uri);
        }
        if (!loading.add(uri)) {
            throw recordFailure(uri, new IOException("Circular image reference"));
        }
        try {
            // Queries and fragments belong to the URL, not to the local filename.
            inputFile.accept(Path.of(uri.resolve(uri.getRawPath())));
            return decoder.loadImage(document, uri);
        } catch (IOException e) {
            throw recordFailure(uri, e);
        } finally {
            loading.remove(uri);
        }
    }

    private IOException recordFailure(URI uri, IOException cause) {
        // JSVG catches IOExceptions on individual image nodes and continues building the document.
        if (failure == null) {
            failure = new IOException("Cannot load SVG image " + uri.getPath() + ": " + cause.getMessage(), cause);
        }
        return failure;
    }

    void checkForFailure() throws IOException {
        if (failure != null) {
            throw failure;
        }
    }
}
