package io.github.jvmdc.viewer;

import io.github.jvmdc.assets.AssetRoots;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class VoxCatalogTest {
    @Test
    void customVoxelsAreListed() throws Exception {
        List<VoxCatalog.Entry> entries = VoxCatalog.listFolder(AssetRoots.locate().custom, "assets/custom/");
        assertTrue(entries.stream().anyMatch(entry ->
                entry.path.equals("assets/custom/voxel/trap/lever_base.vox") && entry.name.equals("lever_base")));
        assertTrue(entries.stream().anyMatch(entry ->
                entry.path.equals("assets/custom/voxel/placement/altar.vox")));
        assertTrue(entries.stream().anyMatch(entry ->
                entry.path.equals("assets/custom/voxel/item/spellbook/spellbook_plain.vox")));
        assertTrue(entries.stream().noneMatch(entry -> entry.path.endsWith(".json")));
    }
}
