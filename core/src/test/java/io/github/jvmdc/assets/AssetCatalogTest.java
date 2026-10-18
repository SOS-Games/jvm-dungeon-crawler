package io.github.jvmdc.assets;

import com.badlogic.gdx.utils.JsonValue;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssetCatalogTest {
    @Test
    void customModelResolvesBeforeConvertedAssets() throws Exception {
        Path root = Files.createTempDirectory("models");
        Path assets = root.resolve("assets");
        Path custom = root.resolve("custom");
        Path converted = assets.resolve("voxygen/voxel/sprite/door/door-0.vox");
        Path override = custom.resolve("voxygen/voxel/sprite/door/door-0.vox");
        Path lever = custom.resolve("voxel/trap/lever_base.vox");
        Files.createDirectories(converted.getParent());
        Files.createDirectories(override.getParent());
        Files.createDirectories(lever.getParent());
        Files.write(converted, new byte[] {1});
        Files.write(override, new byte[] {2});
        Files.write(lever, new byte[] {3});

        ModelPaths paths = new ModelPaths(new AssetRoots(root, assets, custom, null));
        assertEquals(override, paths.find("voxygen.voxel.sprite.door.door-0").orElseThrow());
        assertEquals(lever, paths.find("custom.voxel.trap.lever_base").orElseThrow());
    }

    @Test
    void customIdsReplaceConvertedOnes() throws Exception {
        Path root = Files.createTempDirectory("manifests");
        Path assets = root.resolve("assets");
        Path custom = root.resolve("custom");
        Path base = assets.resolve("voxygen/voxel/sprite_manifest.json");
        Path overlay = custom.resolve("manifest/models.json");
        Files.createDirectories(base.getParent());
        Files.createDirectories(overlay.getParent());
        Files.writeString(base, """
                { "Door": [ { "model": "voxygen.voxel.sprite.door.door-0" } ] }
                """);
        Files.writeString(overlay, """
                { "models": { "Door": { "vox": "custom.voxel.trap.lever_base" } } }
                """);

        ManifestLibrary library = ManifestLibrary.load(new AssetRoots(root, assets, custom, null));
        assertEquals("custom.voxel.trap.lever_base", library.specifier("Door"));
    }

    @Test
    void shippedManifestsLoad() throws Exception {
        AssetRoots roots = AssetRoots.locate();
        ManifestLibrary library = ManifestLibrary.load(roots);
        JsonValue lever = library.get("trap.lever");
        assertNotNull(lever, "Custom asset trap.lever should be loaded in library");

        var voxPath = ModelPaths.locate().find("trap.lever_base");
        assertTrue(voxPath.isPresent(), "Model trap.lever_base should resolve to a file");
        assertTrue(voxPath.get().toString().endsWith(".vox"), "Resolved model file must end with .vox: " + voxPath.get());
    }
}
