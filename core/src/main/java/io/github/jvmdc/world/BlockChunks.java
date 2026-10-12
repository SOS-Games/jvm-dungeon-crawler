package io.github.jvmdc.world;

import io.github.jvmdc.converter.QuadBatch;
import io.github.jvmdc.converter.VolumeMesh;
import io.github.jvmdc.converter.VoxMesher;
import io.github.jvmdc.debug.LoadTrace;
import io.github.jvmdc.dungeongen.common.Dungeon;
import io.github.jvmdc.dungeongen.common.Tile;
import io.github.jvmdc.dungeongen.common.TileGrid;
import io.github.jvmdc.world.BlockPalette.Kind;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns a generated floor into chunk meshes. One map tile is
 * {@link #BLOCKS_PER_TILE} blocks wide and long, and the story is that
 * many blocks tall. Walls and rock fill the story. Floors and ceilings
 * are slabs, with air between them. Chunks are {@link #CHUNK_TILES} tiles
 * on a side.
 */
public final class BlockChunks {
    /** Voxels in one block. Props are built on this grid. */
    public static final int VOXELS_PER_BLOCK = 11;
    /** Map tiles become a square of blocks on the floor. Height uses the same count. */
    public static final int BLOCKS_PER_TILE = 3;
    public static final int VOXELS_PER_TILE = VOXELS_PER_BLOCK * BLOCKS_PER_TILE;
    public static final int CHUNK_TILES = 16;
    /** Floor slab, in voxels. */
    public static final int FLOOR = 2;
    /** Open height between the floor slab and the ceiling. Tall enough for a door. */
    public static final int STORY = 33;
    public static final int CEILING = 2;
    public static final int HEIGHT = FLOOR + STORY + CEILING;

    private BlockChunks() {
    }

    public static List<ChunkMesh> build(Dungeon dungeon, BlockPalette palette) {
        TileGrid grid = dungeon.grid;
        List<ChunkMesh> chunks = new ArrayList<>();
        for (int tileY = 0; tileY < grid.height; tileY += CHUNK_TILES) {
            for (int tileX = 0; tileX < grid.width; tileX += CHUNK_TILES) {
                int tilesWide = Math.min(CHUNK_TILES, grid.width - tileX);
                int tilesHigh = Math.min(CHUNK_TILES, grid.height - tileY);
                chunks.add(mesh(dungeon.grid, palette, tileX, tileY, tilesWide, tilesHigh, Band.ALL, null));
            }
        }
        return chunks;
    }

    /** Shell without ceiling slabs, plus the slabs on their own, so a cutaway can hide them. */
    public static LevelMesh buildParts(Dungeon dungeon, BlockPalette palette) {
        List<ChunkMesh> shell = new ArrayList<>();
        List<ChunkMesh> ceiling = new ArrayList<>();
        forEachPart(dungeon, palette, (shellChunk, ceilingChunk) -> {
            shell.add(shellChunk);
            ceiling.add(ceilingChunk);
        });
        return new LevelMesh(shell, ceiling);
    }

    /**
     * One chunk at a time. The viewer uploads a chunk and drops it before the next,
     * so a full floor is not held as vertex arrays.
     */
    public static void forEachPart(Dungeon dungeon, BlockPalette palette, ChunkPair consumer) {
        TileGrid grid = dungeon.grid;
        for (int tileY = 0; tileY < grid.height; tileY += CHUNK_TILES) {
            for (int tileX = 0; tileX < grid.width; tileX += CHUNK_TILES) {
                partAt(dungeon, palette, tileX, tileY, consumer);
            }
        }
    }

    /** One chunk of the floor. {@code tileX} and {@code tileY} are the chunk corner, in map tiles. */
    public static void partAt(Dungeon dungeon, BlockPalette palette, int tileX, int tileY, ChunkPair consumer) {
        partAt(dungeon, palette, tileX, tileY, consumer, null);
    }

    /** Same chunk, with each step added to {@code trace} when it is not null. */
    public static void partAt(Dungeon dungeon, BlockPalette palette, int tileX, int tileY,
                              ChunkPair consumer, LoadTrace trace) {
        TileGrid grid = dungeon.grid;
        if (tileX >= grid.width || tileY >= grid.height) {
            return;
        }
        int tilesWide = Math.min(CHUNK_TILES, grid.width - tileX);
        int tilesHigh = Math.min(CHUNK_TILES, grid.height - tileY);
        ChunkMesh shell = mesh(grid, palette, tileX, tileY, tilesWide, tilesHigh, Band.SHELL, trace);
        ChunkMesh ceiling = mesh(grid, palette, tileX, tileY, tilesWide, tilesHigh, Band.LID, trace);
        consumer.accept(shell, ceiling);
    }

    public interface ChunkPair {
        void accept(ChunkMesh shell, ChunkMesh ceiling);
    }

    /** Colour of a voxel in a tile column, or null when that height is air. */
    public static Kind kindAt(Tile tile, int z) {
        if (tile == null || z < 0 || z >= HEIGHT) {
            return null;
        }
        if (tile == Tile.WALL) {
            return Kind.WALL;
        }
        if (tile == Tile.ROCK) {
            return Kind.ROCK;
        }
        boolean floor = z < FLOOR;
        boolean ceiling = z >= HEIGHT - CEILING;
        if (tile == Tile.PIT) {
            return ceiling ? Kind.PIT : null;
        }
        if (!floor && !ceiling) {
            return null;
        }
        if (ceiling) {
            return Kind.CEILING;
        }
        return switch (tile) {
            case CORRIDOR_FLOOR -> Kind.CORRIDOR;
            case GROUND -> Kind.GROUND;
            case WATER -> Kind.WATER;
            case DEEP_WATER -> Kind.DEEP_WATER;
            case LAVA -> Kind.LAVA;
            default -> Kind.ROOM_FLOOR;
        };
    }

    private enum Band { ALL, SHELL, LID }

    /**
     * One rectangle per exposed block face. A tile is solid or air for a whole
     * block, and faces already stop on those edges, so the voxel grid is not built.
     */
    private static ChunkMesh mesh(TileGrid grid, BlockPalette palette,
                                  int tileX, int tileY, int tilesWide, int tilesHigh, Band band, LoadTrace trace) {
        String meshName = band == Band.LID ? "mesh ceiling" : "mesh shell";
        if (trace != null) {
            trace.begin(meshName);
        }
        try {
            int originX = tileX * VOXELS_PER_TILE;
            int originY = tileY * VOXELS_PER_TILE;
            return new ChunkMesh(originX, originY, blockFaces(grid, palette, originX, originY, tilesWide, tilesHigh, band));
        } finally {
            if (trace != null) {
                trace.end();
            }
        }
    }

    private static VolumeMesh blockFaces(TileGrid grid, BlockPalette palette,
                                         int originX, int originY, int tilesWide, int tilesHigh, Band band) {
        int blocksX = tilesWide * BLOCKS_PER_TILE;
        int blocksY = tilesHigh * BLOCKS_PER_TILE;
        int stride = blocksX + 2;
        Column[] columns = new Column[stride * (blocksY + 2)];
        for (int by = -1; by <= blocksY; by++) {
            for (int bx = -1; bx <= blocksX; bx++) {
                columns[(bx + 1) + stride * (by + 1)] = columnAt(grid, originX, originY, bx, by, blocksX, blocksY);
            }
        }
        QuadBatch batch = new QuadBatch();
        VoxMesher.VoxelColor color = (x, y, z) -> sampleRgb(palette, grid, x, y, z);
        int[] at = new int[3];
        int[] du = new int[3];
        int[] dv = new int[3];
        for (int by = 0; by < blocksY; by++) {
            for (int bx = 0; bx <= blocksX; bx++) {
                vertical(batch, color, originX, originY, at, du, dv, band, true,
                        columns[bx + stride * (by + 1)], columns[(bx + 1) + stride * (by + 1)],
                        bx * VOXELS_PER_BLOCK, by * VOXELS_PER_BLOCK);
            }
        }
        for (int bx = 0; bx < blocksX; bx++) {
            for (int by = 0; by <= blocksY; by++) {
                vertical(batch, color, originX, originY, at, du, dv, band, false,
                        columns[(bx + 1) + stride * by], columns[(bx + 1) + stride * (by + 1)],
                        by * VOXELS_PER_BLOCK, bx * VOXELS_PER_BLOCK);
            }
        }
        for (int by = 0; by < blocksY; by++) {
            for (int bx = 0; bx < blocksX; bx++) {
                horizontal(batch, color, originX, originY, at, du, dv, band,
                        columns[(bx + 1) + stride * (by + 1)],
                        bx * VOXELS_PER_BLOCK, by * VOXELS_PER_BLOCK);
            }
        }
        return batch.finish();
    }

    /** Faces standing on a block edge. {@code alongX} is the wall whose normal is X. */
    private static void vertical(QuadBatch batch, VoxMesher.VoxelColor color, int originX, int originY,
                                 int[] at, int[] du, int[] dv, Band band, boolean alongX,
                                 Column back, Column front, int plane, int across) {
        int z = 0;
        while (z < HEIGHT) {
            boolean aSolid = back.at(z) != null;
            boolean bSolid = front.at(z) != null;
            if (aSolid == bSolid) {
                z++;
                continue;
            }
            int blockEnd = Math.min(HEIGHT, (z / VOXELS_PER_BLOCK + 1) * VOXELS_PER_BLOCK);
            int z1 = z + 1;
            while (z1 < blockEnd && (back.at(z1) != null) == aSolid && (front.at(z1) != null) == bSolid) {
                z1++;
            }
            Kind solid = aSolid ? back.at(z) : front.at(z);
            if (wanted(band, solid)) {
                int height = z1 - z;
                at[0] = at[1] = at[2] = 0;
                du[0] = du[1] = du[2] = 0;
                dv[0] = dv[1] = dv[2] = 0;
                if (alongX) {
                    at[0] = plane;
                    at[1] = across;
                    at[2] = z;
                    du[1] = VOXELS_PER_BLOCK;
                    dv[2] = height;
                } else {
                    at[0] = across;
                    at[1] = plane;
                    at[2] = z;
                    du[2] = height;
                    dv[0] = VOXELS_PER_BLOCK;
                }
                batch.emit(aSolid ? 1 : -1, at, du, dv, null, originX, originY, color, VOXELS_PER_BLOCK * height);
            }
            z = z1;
        }
    }

    private static void horizontal(QuadBatch batch, VoxMesher.VoxelColor color, int originX, int originY,
                                   int[] at, int[] du, int[] dv, Band band, Column column, int x, int y) {
        cap(batch, color, originX, originY, at, du, dv, band, column, x, y, column.z0, -1);
        cap(batch, color, originX, originY, at, du, dv, band, column, x, y, column.z1, 1);
        if (column.z2 < column.z3) {
            cap(batch, color, originX, originY, at, du, dv, band, column, x, y, column.z2, -1);
            cap(batch, color, originX, originY, at, du, dv, band, column, x, y, column.z3, 1);
        }
    }

    /** {@code sign} is -1 for the underside and +1 for the top. */
    private static void cap(QuadBatch batch, VoxMesher.VoxelColor color, int originX, int originY,
                            int[] at, int[] du, int[] dv, Band band, Column column, int x, int y, int z, int sign) {
        Kind below = z > 0 ? column.at(z - 1) : null;
        Kind above = z < HEIGHT ? column.at(z) : null;
        Kind solid = sign > 0 ? below : above;
        Kind other = sign > 0 ? above : below;
        if (solid == null || other != null || !wanted(band, solid)) {
            return;
        }
        at[0] = x;
        at[1] = y;
        at[2] = z;
        du[0] = VOXELS_PER_BLOCK;
        du[1] = 0;
        du[2] = 0;
        dv[0] = 0;
        dv[1] = VOXELS_PER_BLOCK;
        dv[2] = 0;
        batch.emit(sign, at, du, dv, null, originX, originY, color, VOXELS_PER_BLOCK * VOXELS_PER_BLOCK);
    }

    private static boolean wanted(Band band, Kind kind) {
        boolean lid = kind == Kind.CEILING || kind == Kind.PIT;
        if (band == Band.SHELL) {
            return !lid;
        }
        if (band == Band.LID) {
            return lid;
        }
        return true;
    }

    private static Column columnAt(TileGrid grid, int originX, int originY, int bx, int by, int blocksX, int blocksY) {
        int vx = bx < 0 ? originX - 1 : bx >= blocksX ? originX + blocksX * VOXELS_PER_BLOCK : originX + bx * VOXELS_PER_BLOCK;
        int vy = by < 0 ? originY - 1 : by >= blocksY ? originY + blocksY * VOXELS_PER_BLOCK : originY + by * VOXELS_PER_BLOCK;
        Tile tile = tileAt(grid, vx, vy);
        Column column = new Column();
        Kind mid = kindAt(tile, FLOOR);
        if (mid != null) {
            column.z0 = 0;
            column.z1 = HEIGHT;
            column.k0 = mid;
            return column;
        }
        Kind floor = kindAt(tile, 0);
        Kind ceil = kindAt(tile, HEIGHT - 1);
        if (floor != null) {
            column.z0 = 0;
            column.z1 = FLOOR;
            column.k0 = floor;
        }
        if (ceil != null) {
            if (floor == null) {
                column.z0 = HEIGHT - CEILING;
                column.z1 = HEIGHT;
                column.k0 = ceil;
            } else {
                column.z2 = HEIGHT - CEILING;
                column.z3 = HEIGHT;
                column.k1 = ceil;
            }
        }
        return column;
    }

    private static final class Column {
        int z0;
        int z1;
        int z2;
        int z3;
        Kind k0;
        Kind k1;

        Kind at(int z) {
            if (z >= z0 && z < z1) {
                return k0;
            }
            if (z >= z2 && z < z3) {
                return k1;
            }
            return null;
        }
    }

    /**
     * Colour at a point in map voxels. Neighbouring tiles are mixed by distance,
     * then a 2×2 brick shift is mixed in the same way.
     */
    static int sampleRgb(BlockPalette palette, TileGrid grid, float x, float y, float z) {
        float fx = x / VOXELS_PER_TILE - 0.5f;
        float fy = y / VOXELS_PER_TILE - 0.5f;
        int x0 = (int) Math.floor(fx);
        int y0 = (int) Math.floor(fy);
        float tx = fx - x0;
        float ty = fy - y0;
        float w00 = (1f - tx) * (1f - ty);
        float w10 = tx * (1f - ty);
        float w01 = (1f - tx) * ty;
        float w11 = tx * ty;
        float r = 0f;
        float g = 0f;
        float b = 0f;
        float range = 0f;
        float sum = 0f;
        Kind k00 = w00 > 0f ? kindNear(grid, x0, y0, z) : null;
        if (k00 != null) {
            sum += w00;
            r += w00 * palette.base(k00, 0);
            g += w00 * palette.base(k00, 1);
            b += w00 * palette.base(k00, 2);
            range += w00 * palette.range(k00);
        }
        Kind k10 = w10 > 0f ? kindNear(grid, x0 + 1, y0, z) : null;
        if (k10 != null) {
            sum += w10;
            r += w10 * palette.base(k10, 0);
            g += w10 * palette.base(k10, 1);
            b += w10 * palette.base(k10, 2);
            range += w10 * palette.range(k10);
        }
        Kind k01 = w01 > 0f ? kindNear(grid, x0, y0 + 1, z) : null;
        if (k01 != null) {
            sum += w01;
            r += w01 * palette.base(k01, 0);
            g += w01 * palette.base(k01, 1);
            b += w01 * palette.base(k01, 2);
            range += w01 * palette.range(k01);
        }
        Kind k11 = w11 > 0f ? kindNear(grid, x0 + 1, y0 + 1, z) : null;
        if (k11 != null) {
            sum += w11;
            r += w11 * palette.base(k11, 0);
            g += w11 * palette.base(k11, 1);
            b += w11 * palette.base(k11, 2);
            range += w11 * palette.range(k11);
        }
        if (sum <= 0f) {
            return 0;
        }
        float shift = brick(x, y, z) * (range / sum);
        int ri = clamp(Math.round(r / sum + shift));
        int gi = clamp(Math.round(g / sum + shift));
        int bi = clamp(Math.round(b / sum + shift));
        return (ri << 16) | (gi << 8) | bi;
    }

    private static Kind kindNear(TileGrid grid, int tx, int ty, float z) {
        if (!grid.inBounds(tx, ty)) {
            return null;
        }
        int zi = (int) Math.floor(z);
        Kind at = kindAt(grid.get(tx, ty), zi);
        if (at == null && z == zi && zi > 0) {
            at = kindAt(grid.get(tx, ty), zi - 1);
        }
        return at;
    }

    /** 0..1, smooth across the 2×2×1 brick cells. */
    private static float brick(float x, float y, float z) {
        float px = (x + z) / (2f * VOXELS_PER_BLOCK);
        float py = (y + z) / (2f * VOXELS_PER_BLOCK);
        float pz = z / VOXELS_PER_BLOCK;
        return trilerp(px, py, pz);
    }

    private static float trilerp(float x, float y, float z) {
        int x0 = (int) Math.floor(x);
        int y0 = (int) Math.floor(y);
        int z0 = (int) Math.floor(z);
        float tx = x - x0;
        float ty = y - y0;
        float tz = z - z0;
        float c00 = lerp(BlockPalette.hash01(x0, y0, z0), BlockPalette.hash01(x0 + 1, y0, z0), tx);
        float c10 = lerp(BlockPalette.hash01(x0, y0 + 1, z0), BlockPalette.hash01(x0 + 1, y0 + 1, z0), tx);
        float c01 = lerp(BlockPalette.hash01(x0, y0, z0 + 1), BlockPalette.hash01(x0 + 1, y0, z0 + 1), tx);
        float c11 = lerp(BlockPalette.hash01(x0, y0 + 1, z0 + 1), BlockPalette.hash01(x0 + 1, y0 + 1, z0 + 1), tx);
        return lerp(lerp(c00, c10, ty), lerp(c01, c11, ty), tz);
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static int clamp(int channel) {
        return Math.max(0, Math.min(255, channel));
    }

    private static Tile tileAt(TileGrid grid, int worldX, int worldY) {
        int tileX = Math.floorDiv(worldX, VOXELS_PER_TILE);
        int tileY = Math.floorDiv(worldY, VOXELS_PER_TILE);
        if (!grid.inBounds(tileX, tileY)) {
            return null;
        }
        return grid.get(tileX, tileY);
    }

    /** Shell and ceiling meshes for one floor. */
    public static final class LevelMesh {
        public final List<ChunkMesh> shell;
        public final List<ChunkMesh> ceiling;

        LevelMesh(List<ChunkMesh> shell, List<ChunkMesh> ceiling) {
            this.shell = shell;
            this.ceiling = ceiling;
        }
    }

    /** One chunk. Vertices are relative to {@code originX}, {@code originY} in voxel space. */
    public static final class ChunkMesh {
        public final int originX;
        public final int originY;
        public final VolumeMesh mesh;

        ChunkMesh(int originX, int originY, VolumeMesh mesh) {
            this.originX = originX;
            this.originY = originY;
            this.mesh = mesh;
        }
    }
}
