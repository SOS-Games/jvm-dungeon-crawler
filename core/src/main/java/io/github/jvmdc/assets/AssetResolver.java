package io.github.jvmdc.assets;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Resolves model and file references by checking custom_assets/ first,
 * then converted assets. Paths inside an asset JSON are relative to that asset's folder.
 */
public final class AssetResolver {
    private final AssetRoots roots;

    public AssetResolver(AssetRoots roots) {
        this.roots = roots;
    }

    public static AssetResolver locate() {
        return new AssetResolver(AssetRoots.locate());
    }

    public AssetRoots roots() {
        return roots;
    }

    /**
     * Resolves a file path relative to an asset's directory folder.
     */
    public Path resolveRelative(Path assetFolder, String relativePath) {
        if (assetFolder == null || relativePath == null) {
            return null;
        }
        return assetFolder.resolve(relativePath).normalize();
    }

    /**
     * Finds the first existing file matching a model specifier or path.
     * Checks custom_assets/ first, then converted assets.
     */
    public Optional<Path> find(String specifier) {
        for (Path candidate : candidates(specifier)) {
            if (Files.isRegularFile(candidate)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    /**
     * Candidates tried in priority order: custom_assets/ first, then assets/.
     */
    public List<Path> candidates(String specifier) {
        List<Path> paths = new ArrayList<>();
        if (specifier == null || specifier.isBlank()) {
            return paths;
        }

        String raw = specifier.replace('\\', '/');
        String stripped = raw.startsWith("custom.") ? raw.substring("custom.".length()) : raw;
        String dottedAsPath = stripped.replace('.', '/');

        // 1. Check custom_assets/
        addCandidatesForRoot(paths, roots.custom, raw, stripped, dottedAsPath);

        // 2. Lookup in registry by part model_id or asset id
        CustomAssetRegistry registry = CustomAssetRegistry.getInstance();
        registry.getVoxForModelId(specifier).ifPresent(p -> {
            if (!paths.contains(p)) paths.add(p);
        });

        // 3. Check assets/
        addCandidatesForRoot(paths, roots.assets, raw, stripped, dottedAsPath);

        // 4. Check source if present
        if (roots.source != null) {
            addCandidatesForRoot(paths, roots.source, raw, stripped, dottedAsPath);
        }

        // Filter out any .json files so candidates NEVER return a .json file
        paths.removeIf(p -> p.toString().toLowerCase().endsWith(".json"));

        return paths;
    }

    private void addCandidatesForRoot(List<Path> paths, Path root, String raw, String stripped, String dottedAsPath) {
        if (root == null) {
            return;
        }

        // 1. Direct path relative to root
        Path direct = root.resolve(raw);
        if (!paths.contains(direct)) paths.add(direct);

        // 2. Dotted path converted to slash relative to root
        Path dottedPath = root.resolve(dottedAsPath);
        if (!paths.contains(dottedPath)) paths.add(dottedPath);

        // 3. With .vox extension
        if (!dottedAsPath.toLowerCase().endsWith(".vox")) {
            Path voxPath = root.resolve(dottedAsPath + ".vox");
            if (!paths.contains(voxPath)) paths.add(voxPath);
        }

        // 4. If raw starts with custom.voxel., convert custom.voxel. to voxel/
        if (raw.startsWith("custom.voxel.")) {
            String rawVox = raw.substring("custom.".length()).replace('.', '/') + ".vox";
            Path customVoxPath = root.resolve(rawVox);
            if (!paths.contains(customVoxPath)) paths.add(customVoxPath);
        }

        // 5. Category/asset/<asset>.vox pattern
        int dotIdx = stripped.indexOf('.');
        if (dotIdx > 0 && dotIdx < stripped.length() - 1) {
            String cat = stripped.substring(0, dotIdx);
            String asset = stripped.substring(dotIdx + 1);
            Path assetFolder = root.resolve(cat).resolve(asset);

            Path voxFile = assetFolder.resolve(asset + ".vox");
            if (!paths.contains(voxFile)) paths.add(voxFile);
        }
    }
}
