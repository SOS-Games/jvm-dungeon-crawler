package io.github.jvmdc.assets;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Turns a dotted model name into a .vox file. Custom names are read from the
 * custom folder. Every name is tried there first, then in the converted assets.
 */
public final class ModelPaths {
    private final AssetRoots roots;

    public ModelPaths(AssetRoots roots) {
        this.roots = roots;
    }

    public static ModelPaths locate() {
        return new ModelPaths(AssetRoots.locate());
    }

    /** First path that exists, if the file is on disk. */
    public Optional<Path> find(String specifier) {
        for (Path candidate : candidates(specifier)) {
            if (Files.isRegularFile(candidate)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    /** Preferred path, even when the file has not been copied yet. */
    public Path path(String specifier) {
        List<Path> candidates = candidates(specifier);
        return candidates.get(0);
    }

    private List<Path> candidates(String specifier) {
        String dotted = specifier.startsWith("custom.") ? specifier.substring("custom.".length()) : specifier;
        String relative = dotted.replace('.', '/') + ".vox";
        String full = specifier.replace('.', '/') + ".vox";
        List<Path> paths = new ArrayList<>();
        if (specifier.startsWith("custom.")) {
            paths.add(roots.custom.resolve(relative));
        }
        paths.add(roots.custom.resolve(full));
        paths.add(roots.assets.resolve(full));
        if (!full.equals(relative)) {
            paths.add(roots.assets.resolve(relative));
        }
        if (roots.source != null) {
            paths.add(roots.source.resolve(full));
            if (!full.equals(relative)) {
                paths.add(roots.source.resolve(relative));
            }
        }
        return paths;
    }
}
