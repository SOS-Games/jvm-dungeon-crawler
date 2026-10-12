package io.github.jvmdc.world;

/** Near and far planes for a dungeon fly view, in voxels. */
public final class ViewRange {
    private ViewRange() {
    }

    /** A tenth of a map tile. Close enough to stand at a wall, far enough for a stable depth buffer. */
    public static float near() {
        return 0.1f * BlockChunks.VOXELS_PER_TILE;
    }

    /** Far corner of a map this many tiles across, plus one spare tile. */
    public static float far(int tilesWide, int tilesHigh) {
        float tile = BlockChunks.VOXELS_PER_TILE;
        float w = tilesWide * tile;
        float h = tilesHigh * tile;
        float span = (float) Math.sqrt(w * w + h * h + (float) BlockChunks.HEIGHT * BlockChunks.HEIGHT);
        return span + tile;
    }
}
