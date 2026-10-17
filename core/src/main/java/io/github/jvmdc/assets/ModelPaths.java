package io.github.jvmdc.assets;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Turns a model name into a file Path. Checked in custom_assets/ first,
 * then in converted assets. Delegates to AssetResolver.
 */
public final class ModelPaths {
    private final AssetResolver resolver;

    public ModelPaths(AssetRoots roots) {
        this(new AssetResolver(roots));
    }

    public ModelPaths(AssetResolver resolver) {
        this.resolver = resolver;
    }

    public static ModelPaths locate() {
        return new ModelPaths(AssetResolver.locate());
    }

    /** First path that exists, if the file is on disk. */
    public Optional<Path> find(String specifier) {
        return resolver.find(specifier);
    }

    /** Preferred path, even when the file has not been copied yet. */
    public Path path(String specifier) {
        List<Path> candidates = resolver.candidates(specifier);
        return candidates.isEmpty() ? resolver.roots().custom.resolve(specifier) : candidates.get(0);
    }
}
