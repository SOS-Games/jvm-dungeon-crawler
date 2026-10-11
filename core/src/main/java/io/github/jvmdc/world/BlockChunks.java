package io.github.jvmdc.world;

import io.github.jvmdc.converter.VolumeMesh;
import io.github.jvmdc.converter.VoxMesher;
import io.github.jvmdc.dungeongen.common.Dungeon;
import io.github.jvmdc.dungeongen.common.Tile;
import io.github.jvmdc.dungeongen.common.TileGrid;
import io.github.jvmdc.world.BlockPalette.Kind;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns a generated floor into greedy-meshed chunks. One tile is
 * {@link #VOXELS_PER_TILE} voxels across. Walls and rock fill the story.
 * Floors and ceilings are slabs, with air between them. Chunks are
 * {@link #CHUNK_TILES} tiles on a side.
 */
public final class BlockChunks {
    public static final int VOXELS_PER_TILE = 11;
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
                byte[] material = fill(grid, tileX, tileY, tilesWide, tilesHigh);
                chunks.add(mesh(palette, material, tileX, tileY, tilesWide, tilesHigh, Band.ALL));
            }
        }
        return chunks;
    }

    /** Shell without ceiling slabs, plus the slabs on their own, so a cutaway can hide them. */
    public static LevelMesh buildParts(Dungeon dungeon, BlockPalette palette) {
        TileGrid grid = dungeon.grid;
        List<ChunkMesh> shell = new ArrayList<>();
        List<ChunkMesh> ceiling = new ArrayList<>();
        for (int tileY = 0; tileY < grid.height; tileY += CHUNK_TILES) {
            for (int tileX = 0; tileX < grid.width; tileX += CHUNK_TILES) {
                int tilesWide = Math.min(CHUNK_TILES, grid.width - tileX);
                int tilesHigh = Math.min(CHUNK_TILES, grid.height - tileY);
                byte[] material = fill(grid, tileX, tileY, tilesWide, tilesHigh);
                shell.add(mesh(palette, material, tileX, tileY, tilesWide, tilesHigh, Band.SHELL));
                ceiling.add(mesh(palette, material, tileX, tileY, tilesWide, tilesHigh, Band.LID));
            }
        }
        return new LevelMesh(shell, ceiling);
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

    private static byte[] fill(TileGrid grid, int tileX, int tileY, int tilesWide, int tilesHigh) {
        int sizeX = tilesWide * VOXELS_PER_TILE + 2;
        int sizeY = tilesHigh * VOXELS_PER_TILE + 2;
        byte[] material = new byte[sizeX * sizeY * HEIGHT];
        int originX = tileX * VOXELS_PER_TILE;
        int originY = tileY * VOXELS_PER_TILE;
        for (int z = 0; z < HEIGHT; z++) {
            for (int y = 0; y < sizeY; y++) {
                for (int x = 0; x < sizeX; x++) {
                    Kind kind = kindAt(tileAt(grid, originX + x - 1, originY + y - 1), z);
                    if (kind != null) {
                        material[x + sizeX * (y + sizeY * z)] = (byte) (kind.ordinal() + 1);
                    }
                }
            }
        }
        return material;
    }

    private static ChunkMesh mesh(BlockPalette palette, byte[] material,
                                  int tileX, int tileY, int tilesWide, int tilesHigh, Band band) {
        int sizeX = tilesWide * VOXELS_PER_TILE + 2;
        int sizeY = tilesHigh * VOXELS_PER_TILE + 2;
        int originX = tileX * VOXELS_PER_TILE;
        int originY = tileY * VOXELS_PER_TILE;
        byte[] voxels = new byte[material.length];
        for (int z = 0; z < HEIGHT; z++) {
            for (int y = 0; y < sizeY; y++) {
                for (int x = 0; x < sizeX; x++) {
                    int index = x + sizeX * (y + sizeY * z);
                    int ordinal = material[index] & 0xFF;
                    if (ordinal == 0) {
                        continue;
                    }
                    Kind kind = Kind.values()[ordinal - 1];
                    boolean lid = kind == Kind.CEILING || kind == Kind.PIT;
                    if (band == Band.SHELL && lid) {
                        continue;
                    }
                    if (band == Band.LID && !lid) {
                        continue;
                    }
                    boolean edge = open(material, sizeX, sizeY, x, y, z);
                    voxels[index] = (byte) palette.paint(kind, originX + x - 1, originY + y - 1, z, edge);
                }
            }
        }
        VolumeMesh mesh = VoxMesher.meshVolume(voxels, sizeX, sizeY, HEIGHT, palette.rgbaBySlot());
        return new ChunkMesh(originX, originY, mesh);
    }

    private static Tile tileAt(TileGrid grid, int worldX, int worldY) {
        int tileX = Math.floorDiv(worldX, VOXELS_PER_TILE);
        int tileY = Math.floorDiv(worldY, VOXELS_PER_TILE);
        if (!grid.inBounds(tileX, tileY)) {
            return null;
        }
        return grid.get(tileX, tileY);
    }

    private static boolean open(byte[] material, int sizeX, int sizeY, int x, int y, int z) {
        return empty(material, sizeX, sizeY, x + 1, y, z)
                || empty(material, sizeX, sizeY, x - 1, y, z)
                || empty(material, sizeX, sizeY, x, y + 1, z)
                || empty(material, sizeX, sizeY, x, y - 1, z)
                || empty(material, sizeX, sizeY, x, y, z + 1)
                || empty(material, sizeX, sizeY, x, y, z - 1);
    }

    private static boolean empty(byte[] material, int sizeX, int sizeY, int x, int y, int z) {
        if (x < 0 || y < 0 || z < 0 || x >= sizeX || y >= sizeY || z >= HEIGHT) {
            return true;
        }
        return material[x + sizeX * (y + sizeY * z)] == 0;
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
