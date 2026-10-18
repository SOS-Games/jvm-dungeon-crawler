package io.github.jvmdc.assets;

import io.github.jvmdc.converter.Converter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Where converted assets and the custom override folder live. */
public final class AssetRoots {
    public final Path repo;
    public final Path assets;
    public final Path custom;
    /** Extra asset tree, when one is configured beside the converted copy. */
    public final Path source;

    public AssetRoots(Path repo, Path assets, Path custom, Path source) {
        this.repo = repo;
        this.assets = assets;
        this.custom = custom;
        this.source = source;
    }

    public static AssetRoots locate() {
        Path repo = repoRoot(Path.of("").toAbsolutePath());
        Path assets = repo.resolve("assets");
        Path custom = repo.resolve("custom_assets");
        if (!Files.isDirectory(custom)) {
            custom = assets.resolve("custom");
        }
        return new AssetRoots(repo, assets, custom, sourceAssets(repo));
    }

    static Path repoRoot(Path start) {
        Path dir = start;
        for (int i = 0; i < 5 && dir != null; i++) {
            if (Files.isDirectory(dir.resolve("custom_assets")) || Files.isDirectory(dir.resolve("game_data"))) {
                return dir;
            }
            dir = dir.getParent();
        }
        return start;
    }

    private static Path sourceAssets(Path repo) {
        Path local = repo.resolve("local.properties");
        Properties properties = new Properties();
        if (Files.isRegularFile(local)) {
            try (var in = Files.newInputStream(local)) {
                properties.load(in);
            } catch (Exception ignored) {
                properties = Converter.getLocalPropeties();
            }
        }
        for (String key : properties.stringPropertyNames()) {
            String value = properties.getProperty(key);
            if (value == null || value.isBlank()) {
                continue;
            }
            Path candidate = Path.of(value).resolve("assets");
            if (Files.isDirectory(candidate.resolve("voxygen"))) {
                return candidate;
            }
        }
        return null;
    }
}
