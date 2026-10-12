package io.github.jvmdc.world;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ViewRangeTest {
    @Test
    void nearIsATenthOfATileAndFarCoversTheMap() {
        assertEquals(0.1f * BlockChunks.VOXELS_PER_TILE, ViewRange.near(), 0.001f);
        assertTrue(ViewRange.near() > 1f);
        float far = ViewRange.far(64, 64);
        float tile = BlockChunks.VOXELS_PER_TILE;
        float span = (float) Math.sqrt(2f * (64f * tile) * (64f * tile));
        assertTrue(far > span);
        assertTrue(far < span + tile * 2f);
        assertTrue(far < 4000f);
    }
}
