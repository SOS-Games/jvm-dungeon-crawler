package io.github.jvmdc.world;

import io.github.jvmdc.assets.AssetRoots;
import io.github.jvmdc.converter.VolumeMesh;
import io.github.jvmdc.converter.VoxMesher;
import io.github.jvmdc.dungeongen.LayoutConfig;
import io.github.jvmdc.dungeongen.Layouts;
import io.github.jvmdc.dungeongen.common.Dungeon;
import io.github.jvmdc.dungeongen.common.Tile;
import io.github.jvmdc.dungeongen.common.TileGrid;
import io.github.jvmdc.world.BlockPalette.Kind;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockChunksTest {
    @Test
    void oneVoxelHasSixFaces() {
        VolumeMesh mesh = cube(1, 1, 1, 1);
        assertEquals(6, mesh.quads);
        assertEquals(6, mesh.exposed);
        assertTrue(hasNormal(mesh, 0f, 1f, 0f));
        assertTrue(hasNormal(mesh, 0f, -1f, 0f));
        assertTrue(hasNormal(mesh, 1f, 0f, 0f));
        assertTrue(hasNormal(mesh, -1f, 0f, 0f));
        assertTrue(hasNormal(mesh, 0f, 0f, 1f));
        assertTrue(hasNormal(mesh, 0f, 0f, -1f));
    }

    @Test
    void matchingFacesJoin() {
        VolumeMesh mesh = cube(2, 1, 1, 1);
        assertTrue(mesh.exposed > mesh.quads, "exposed " + mesh.exposed + " quads " + mesh.quads);
        assertEquals(6, mesh.quads);
    }

    @Test
    void mapTileIsThreeBlocksWideAndLong() throws Exception {
        assertEquals(3, BlockChunks.BLOCKS_PER_TILE);
        assertEquals(33, BlockChunks.VOXELS_PER_TILE);
        assertEquals(33, BlockChunks.STORY);
        TileGrid grid = new TileGrid(1, 1);
        grid.set(0, 0, Tile.WALL);
        Dungeon dungeon = new Dungeon(0, LayoutConfig.attached(), grid,
                java.util.List.of(), java.util.List.of(), null, -1, -1);
        BlockChunks.ChunkMesh chunk = BlockChunks.build(dungeon, BlockPalette.load(paletteFile())).get(0);
        float maxX = 0f;
        float maxY = 0f;
        float minZ = 0f;
        for (float[] verts : chunk.mesh.vertices) {
            for (int i = 0; i < verts.length; i += VolumeMesh.VERTEX_SIZE) {
                maxX = Math.max(maxX, verts[i]);
                maxY = Math.max(maxY, verts[i + 1]);
                minZ = Math.min(minZ, verts[i + 2]);
            }
        }
        assertEquals(BlockChunks.VOXELS_PER_TILE, maxX, 0.01f);
        assertEquals(-BlockChunks.VOXELS_PER_TILE, minZ, 0.01f);
        assertEquals(BlockChunks.HEIGHT, maxY, 0.01f);
        int side = 4 * 3 * 4;
        int caps = 2 * 3 * 3;
        assertEquals(side + caps, chunk.mesh.quads);
        assertEquals(4 * 33 * BlockChunks.HEIGHT + 2 * 33 * 33, chunk.mesh.exposed);
    }

    @Test
    void floorAndCeilingAreSeparate() throws Exception {
        TileGrid grid = new TileGrid(1, 1);
        grid.set(0, 0, Tile.ROOM_FLOOR);
        Dungeon dungeon = new Dungeon(0, LayoutConfig.attached(), grid,
                java.util.List.of(), java.util.List.of(), null, -1, -1);
        BlockChunks.LevelMesh parts = BlockChunks.buildParts(dungeon, BlockPalette.load(paletteFile()));
        assertEquals(30, parts.shell.get(0).mesh.quads);
        assertEquals(30, parts.ceiling.get(0).mesh.quads);
        assertTrue(hasNormal(parts.shell.get(0).mesh, 0f, 1f, 0f));
        assertTrue(hasNormal(parts.ceiling.get(0).mesh, 0f, -1f, 0f));
    }

    @Test
    void colourEasesBetweenTiles() throws Exception {
        TileGrid grid = new TileGrid(2, 1);
        grid.set(0, 0, Tile.ROOM_FLOOR);
        grid.set(1, 0, Tile.CORRIDOR_FLOOR);
        BlockPalette palette = BlockPalette.load(paletteFile());
        int center = BlockChunks.sampleRgb(palette, grid, 16.5f, 16.5f, 0.5f) >> 16;
        int edge = BlockChunks.sampleRgb(palette, grid, 33f, 16.5f, 0.5f) >> 16;
        assertTrue(center > edge, "center " + center + " edge " + edge);
    }

    @Test
    void floorIsOpenAndWallIsSolid() {
        assertEquals(Kind.ROOM_FLOOR, BlockChunks.kindAt(Tile.ROOM_FLOOR, 0));
        assertNullKind(BlockChunks.kindAt(Tile.ROOM_FLOOR, BlockChunks.FLOOR));
        assertEquals(Kind.CEILING, BlockChunks.kindAt(Tile.ROOM_FLOOR, BlockChunks.HEIGHT - 1));
        assertEquals(Kind.CORRIDOR, BlockChunks.kindAt(Tile.CORRIDOR_FLOOR, 0));
        assertEquals(Kind.GROUND, BlockChunks.kindAt(Tile.GROUND, 0));
        assertEquals(Kind.ROCK, BlockChunks.kindAt(Tile.ROCK, BlockChunks.STORY));
        assertEquals(Kind.WALL, BlockChunks.kindAt(Tile.WALL, BlockChunks.STORY));
        assertNullKind(BlockChunks.kindAt(Tile.PIT, 0));
        assertEquals(Kind.PIT, BlockChunks.kindAt(Tile.PIT, BlockChunks.HEIGHT - 1));
    }

    @Test
    void edgeShadeIsDarker() throws Exception {
        BlockPalette palette = BlockPalette.load(paletteFile());
        int plain = palette.paint(Kind.WALL, 4, 6, 2, false);
        int edge = palette.paint(Kind.WALL, 4, 6, 2, true);
        assertTrue(plain != edge);
        assertTrue(palette.red(edge) < palette.red(plain));
    }

    @Test
    void seedsOneAndEightMesh() throws Exception {
        BlockPalette palette = BlockPalette.load(paletteFile());
        meshSeed(palette, 1);
        meshSeed(palette, 8);
    }

    private static void meshSeed(BlockPalette palette, long seed) {
        Dungeon dungeon = Layouts.of(Layouts.DEFAULT).generate(LayoutConfig.attached(), seed);
        var chunks = BlockChunks.build(dungeon, palette);
        assertEquals(16, chunks.size(), "seed " + seed);
        int quads = 0;
        int exposed = 0;
        for (BlockChunks.ChunkMesh chunk : chunks) {
            assertNotNull(chunk.mesh);
            quads += chunk.mesh.quads;
            exposed += chunk.mesh.exposed;
            assertTrue(chunk.mesh.quads > 0, "seed " + seed);
        }
        assertTrue(quads < exposed, "seed " + seed + " quads " + quads + " exposed " + exposed);
        if (seed == 1) {
            BlockChunks.LevelMesh parts = BlockChunks.buildParts(dungeon, palette);
            int shell = 0;
            int lid = 0;
            for (BlockChunks.ChunkMesh chunk : parts.shell) {
                shell += chunk.mesh.quads;
            }
            for (BlockChunks.ChunkMesh chunk : parts.ceiling) {
                lid += chunk.mesh.quads;
            }
            assertTrue(shell > 0);
            assertTrue(lid > 0);
        }
    }

    private static Path paletteFile() {
        return AssetRoots.locate().repo.resolve("game_data").resolve("block_palette.json");
    }

    private static void assertNullKind(Kind kind) {
        assertEquals(null, kind);
    }

    private static VolumeMesh cube(int sx, int sy, int sz, int slot) {
        int sizeX = sx + 2;
        int sizeY = sy + 2;
        byte[] voxels = new byte[sizeX * sizeY * sz];
        for (int z = 0; z < sz; z++) {
            for (int y = 0; y < sy; y++) {
                for (int x = 0; x < sx; x++) {
                    int gx = x + 1;
                    int gy = y + 1;
                    voxels[gx + sizeX * (gy + sizeY * z)] = (byte) slot;
                }
            }
        }
        int[] rgba = new int[slot + 1];
        rgba[slot] = (180 << 24) | (160 << 16) | (140 << 8);
        return VoxMesher.meshVolume(voxels, sizeX, sizeY, sz, rgba);
    }

    private static boolean hasNormal(VolumeMesh mesh, float x, float y, float z) {
        for (float[] verts : mesh.vertices) {
            for (int i = 0; i < verts.length; i += VolumeMesh.VERTEX_SIZE) {
                if (close(verts[i + 3], x) && close(verts[i + 4], y) && close(verts[i + 5], z)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean close(float a, float b) {
        return Math.abs(a - b) < 0.01f;
    }
}
