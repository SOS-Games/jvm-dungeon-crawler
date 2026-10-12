package io.github.jvmdc.prop;

import io.github.jvmdc.converter.VoxReader;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Pulls overlapping voxels out of later rig parts. A shaft that sits inside
 * its base, or a hatch door that sits in its frame, keeps only the voxels
 * the earlier part does not already fill. Faces pressed against that part
 * are left to the earlier mesh.
 */
public final class RigParts {
    private RigParts() {
    }

    public static final class Piece {
        public VoxReader.VoxModel model;
        public int x;
        public int y;
        public int z;
        /** Cells this part occupied before later parts were cut. */
        public final Set<Long> solid = new HashSet<>();
    }

    public static List<Piece> cut(List<VoxReader.VoxModel> models, int[][] corner) {
        List<Piece> pieces = new ArrayList<>();
        Set<Long> taken = new HashSet<>();
        Set<Long> columns = new HashSet<>();
        for (int i = 0; i < models.size(); i++) {
            VoxReader.VoxModel source = models.get(i);
            Piece piece = new Piece();
            piece.model = copy(source);
            piece.x = corner[i][0];
            piece.y = corner[i][1];
            piece.z = corner[i][2];
            if (piece.model.grid != null) {
                boolean overlapped = clearShared(piece, taken);
                // A shaft that starts just under the floor loses those tips. A part
                // placed well below the floor, such as a hatch ladder, keeps them.
                if (overlapped && piece.z >= -1) {
                    clearBuriedTips(piece, columns);
                }
                occupy(piece, taken, columns);
            }
            pieces.add(piece);
        }
        return pieces;
    }

    /** True when an earlier part already fills this cell of the current part. */
    public static boolean covered(List<Piece> pieces, int index, int x, int y, int z) {
        Piece piece = pieces.get(index);
        long key = key(piece.x + x, piece.y + y, piece.z + z);
        for (int i = 0; i < index; i++) {
            if (pieces.get(i).solid.contains(key)) {
                return true;
            }
        }
        return false;
    }

    /** Drops voxels that sit in a cell an earlier part already fills. */
    private static boolean clearShared(Piece piece, Set<Long> taken) {
        VoxReader.VoxModel model = piece.model;
        boolean overlapped = false;
        for (int x = 0; x < model.sizeX; x++) {
            for (int y = 0; y < model.sizeY; y++) {
                for (int z = 0; z < model.sizeZ; z++) {
                    if (model.grid[x][y][z] == 0) {
                        continue;
                    }
                    if (taken.contains(key(piece.x + x, piece.y + y, piece.z + z))) {
                        model.grid[x][y][z] = 0;
                        overlapped = true;
                    }
                }
            }
        }
        return overlapped;
    }

    /** Tips that poke through the floor under a part this one already overlaps. */
    private static void clearBuriedTips(Piece piece, Set<Long> columns) {
        VoxReader.VoxModel model = piece.model;
        for (int x = 0; x < model.sizeX; x++) {
            for (int y = 0; y < model.sizeY; y++) {
                for (int z = 0; z < model.sizeZ; z++) {
                    if (model.grid[x][y][z] == 0) {
                        continue;
                    }
                    int px = piece.x + x;
                    int py = piece.y + y;
                    if (piece.z + z < 0 && columns.contains(column(px, py))) {
                        model.grid[x][y][z] = 0;
                    }
                }
            }
        }
    }

    private static void occupy(Piece piece, Set<Long> taken, Set<Long> columns) {
        VoxReader.VoxModel model = piece.model;
        for (int x = 0; x < model.sizeX; x++) {
            for (int y = 0; y < model.sizeY; y++) {
                for (int z = 0; z < model.sizeZ; z++) {
                    if (model.grid[x][y][z] == 0) {
                        continue;
                    }
                    int px = piece.x + x;
                    int py = piece.y + y;
                    int pz = piece.z + z;
                    long key = key(px, py, pz);
                    piece.solid.add(key);
                    taken.add(key);
                    if (pz >= 0) {
                        columns.add(column(px, py));
                    }
                }
            }
        }
    }

    private static VoxReader.VoxModel copy(VoxReader.VoxModel source) {
        VoxReader.VoxModel model = new VoxReader.VoxModel();
        model.palette = source.palette;
        if (source.grid == null) {
            return model;
        }
        model.sizeX = source.sizeX;
        model.sizeY = source.sizeY;
        model.sizeZ = source.sizeZ;
        model.grid = new byte[source.sizeX][source.sizeY][source.sizeZ];
        for (int x = 0; x < source.sizeX; x++) {
            for (int y = 0; y < source.sizeY; y++) {
                System.arraycopy(source.grid[x][y], 0, model.grid[x][y], 0, source.sizeZ);
            }
        }
        return model;
    }

    private static long key(int x, int y, int z) {
        return ((long) (x + 1024) << 42) | ((long) (y + 1024) << 21) | (z + 1024);
    }

    private static long column(int x, int y) {
        return ((long) (x + 1024) << 21) | (y + 1024);
    }
}
