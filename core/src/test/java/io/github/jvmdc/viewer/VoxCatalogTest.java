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
                entry.path.endsWith("trap/lever/lever_base.vox") && entry.name.equals("lever_base")));
        assertTrue(entries.stream().anyMatch(entry ->
                entry.path.endsWith("placement/altar/altar.vox")));
        assertTrue(entries.stream().anyMatch(entry ->
                entry.path.endsWith("item/spellbook/spellbook_plain.vox")));
        assertTrue(entries.stream().noneMatch(entry -> entry.path.endsWith(".json")));
    }
}
