package io.github.jvmdc.assets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CustomAssetTest {

    @BeforeEach
    void setUp() {
        CustomAssetRegistry.reset();
    }

    @Test
    void all17RealAssetsLoadAndReferencedVoxExist() {
        AssetRoots roots = AssetRoots.locate();
        CustomAssetRegistry registry = CustomAssetRegistry.load(roots);

        assertEquals(17, registry.all().size(), "Should load exactly 17 real assets from custom_assets/");

        for (AssetDef asset : registry.all().values()) {
            assertNotNull(asset.id, "Asset ID should not be null");
            assertNotNull(asset.category, "Asset category should not be null");
            assertTrue(Files.isRegularFile(asset.jsonPath), "Asset JSON file should exist: " + asset.jsonPath);

            for (AssetDef.PartDef part : asset.parts) {
                if (part.file != null) {
                    assertNotNull(part.resolvedPath, "Resolved path for part " + part.name + " in " + asset.id + " should not be null");
                    assertTrue(Files.isRegularFile(part.resolvedPath),
                            "Referenced .vox file does not exist for asset " + asset.id + " / part " + part.name + ": " + part.resolvedPath);
                }
            }
        }
    }

    @Test
    void overridePrecedence() throws Exception {
        Path tempDir = Files.createTempDirectory("override_test");
        Path customAssets = tempDir.resolve("custom_assets");
        Path categoryDir = customAssets.resolve("trap");
        Path assetDir = categoryDir.resolve("lever");
        Files.createDirectories(assetDir);

        Path jsonFile = assetDir.resolve("lever.json");
        Files.writeString(jsonFile, """
                {
                  "id": "trap.lever",
                  "scale": 0.5,
                  "license": "Custom Lever License"
                }
                """);

        AssetRoots roots = new AssetRoots(tempDir, tempDir.resolve("assets"), customAssets, null);
        CustomAssetRegistry registry = CustomAssetRegistry.load(roots);

        assertTrue(registry.has("trap.lever"));
        AssetDef def = registry.get("trap.lever").orElseThrow();
        assertEquals(0.5f, def.scale, 0.0001f);
        assertEquals("Custom Lever License", def.licence);
    }

    @Test
    void relativePathResolution() {
        AssetResolver resolver = AssetResolver.locate();
        Path folder = Path.of("custom_assets/trap/lever");
        Path resolved = resolver.resolveRelative(folder, "lever_base.vox");

        assertEquals(folder.resolve("lever_base.vox").normalize(), resolved);
    }

    @Test
    void poseRowLoadingAndNoSilentFallback() {
        AssetRoots roots = AssetRoots.locate();
        CustomAssetRegistry.load(roots);

        float[] mimicRow = io.github.jvmdc.viewer.AnimalAttrs.get("quadruped_low", "Mimic");
        assertNotNull(mimicRow);
        assertTrue(mimicRow.length > 0, "Pose row for Mimic should be loaded from custom asset JSON");

        float[] nonExistentRow = io.github.jvmdc.viewer.AnimalAttrs.get("quadruped_low", "NonExistentSpecies1234");
        assertNotNull(nonExistentRow);
        assertEquals(0, nonExistentRow.length, "Non-existent species should return empty row (no silent fallback)");
    }

    @Test
    void hiddenStateParts() {
        AssetRoots roots = AssetRoots.locate();
        CustomAssetRegistry registry = CustomAssetRegistry.load(roots);

        AssetDef mimic = registry.get("npc.mimic").orElseThrow();

        var disguisedParts = mimic.visibleParts("closed_disguised");
        var openParts = mimic.visibleParts("open_snarl");

        boolean disguisedHasLegs = disguisedParts.stream().anyMatch(p -> "legs".equalsIgnoreCase(p.name) || "front_left".equalsIgnoreCase(p.name) || "front_left".equalsIgnoreCase(p.slot));
        boolean openHasLegs = openParts.stream().anyMatch(p -> "legs".equalsIgnoreCase(p.name) || "front_left".equalsIgnoreCase(p.name) || "front_left".equalsIgnoreCase(p.slot));

        assertFalse(disguisedHasLegs, "Legs / lateral parts should be hidden in closed_disguised state");
        assertTrue(openHasLegs, "Legs / lateral parts should be visible in open_snarl state");
    }

    @Test
    void paletteSwapping() {
        AssetRoots roots = AssetRoots.locate();
        CustomAssetRegistry registry = CustomAssetRegistry.load(roots);

        AssetDef spellbook = registry.get("item.spellbook").orElseThrow();
        assertNotNull(spellbook.palette);
        assertTrue(spellbook.palette.namedPalettes.containsKey("crimson"));

        int[] basePalette = new int[256];
        basePalette[0] = 0x111111FF;
        basePalette[1] = 0x222222FF;
        basePalette[2] = 0x333333FF;

        int[] swapped = spellbook.getSwappedPalette(basePalette, "crimson");

        Map<Integer, int[]> crimsonSwap = spellbook.palette.namedPalettes.get("crimson");
        int[] expectedRgb1 = crimsonSwap.get(1);

        int expectedPacked1 = ((expectedRgb1[0] & 0xFF) << 24)
                | ((expectedRgb1[1] & 0xFF) << 16)
                | ((expectedRgb1[2] & 0xFF) << 8)
                | 0xFF;

        assertEquals(expectedPacked1, swapped[0], "Palette slot 1 should be overridden by crimson swap color");
    }

    @Test
    void malformedJsonSkipped() throws Exception {
        Path tempDir = Files.createTempDirectory("malformed_test");
        Path customAssets = tempDir.resolve("custom_assets");
        Path catDir = customAssets.resolve("trap");
        Path validAssetDir = catDir.resolve("valid_trap");
        Path badAssetDir = catDir.resolve("bad_trap");

        Files.createDirectories(validAssetDir);
        Files.createDirectories(badAssetDir);

        Files.writeString(validAssetDir.resolve("valid_trap.json"), """
                {
                  "id": "trap.valid",
                  "scale": 1.0
                }
                """);

        Files.writeString(badAssetDir.resolve("bad_trap.json"), "{ invalid_json_syntax: ");

        AssetRoots roots = new AssetRoots(tempDir, tempDir.resolve("assets"), customAssets, null);
        CustomAssetRegistry registry = CustomAssetRegistry.load(roots);

        assertTrue(registry.has("trap.valid"), "Valid asset should be loaded");
        assertFalse(registry.has("trap.bad_trap"), "Bad asset should be skipped");
        assertEquals(1, registry.all().size());
    }
}
