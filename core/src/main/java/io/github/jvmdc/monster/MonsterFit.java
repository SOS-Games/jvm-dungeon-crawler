package io.github.jvmdc.monster;

import io.github.jvmdc.world.BlockChunks;

/** Keeps a stood-up body on the floor and inside its tile. */
public final class MonsterFit {
    private MonsterFit() {
    }

    /**
     * Scale that fits the stood bounds into one map tile and under the ceiling.
     * Bounds are figure voxels, Y up: minX, minY, minZ, maxX, maxY, maxZ.
     */
    public static float scaleToTile(float requested, float[] bounds) {
        float reach = 0f;
        float[] xs = {bounds[0], bounds[3]};
        float[] zs = {bounds[2], bounds[5]};
        for (float x : xs) {
            for (float z : zs) {
                reach = Math.max(reach, (float) Math.hypot(x, z));
            }
        }
        float height = bounds[4] - bounds[1];
        float scale = requested;
        float limit = BlockChunks.VOXELS_PER_TILE * 0.5f - 1f;
        if (reach > 0.01f && reach * scale > limit) {
            scale = limit / reach;
        }
        float maxH = BlockChunks.STORY - 1f;
        if (height > 0.01f && height * scale > maxH) {
            scale = Math.min(scale, maxH / height);
        }
        return scale;
    }

    /** World height whose scaled sole sits on the floor slab. */
    public static float footY(float scale, float minY) {
        return BlockChunks.FLOOR - minY * scale;
    }
}
