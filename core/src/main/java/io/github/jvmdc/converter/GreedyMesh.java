package io.github.jvmdc.converter;

/**
 * Joins coplanar voxel faces that share a colour into rectangles.
 * The grid has a one-voxel pad on x and y. Only faces owned by the inner
 * volume are kept, so a chunk does not draw a wall against the next chunk.
 */
final class GreedyMesh {
    private GreedyMesh() {
    }

    static VolumeMesh build(byte[] voxels, int sizeX, int sizeY, int sizeZ, int[] rgbaBySlot) {
        return build(voxels, sizeX, sizeY, sizeZ, rgbaBySlot, 0, 0, 0, null);
    }

    /**
     * {@code block} stops a rectangle on a block boundary so each face can
     * carry a different colour at each corner. {@code color} samples that
     * colour in chunk voxel coordinates (x, map y, up).
     */
    static VolumeMesh build(byte[] voxels, int sizeX, int sizeY, int sizeZ, int[] rgbaBySlot,
                            int originX, int originY, int block, VoxMesher.VoxelColor color) {
        int innerX = sizeX - 2;
        int innerY = sizeY - 2;
        int innerZ = sizeZ;
        if (innerX <= 0 || innerY <= 0 || innerZ <= 0) {
            return new VolumeMesh(new float[0][], new short[0][], 0, 0);
        }
        int[] dims = {innerX, innerY, innerZ};
        int[] origin = {originX, originY, 0};
        int maskLen = Math.max(innerX * innerY, Math.max(innerX * innerZ, innerY * innerZ));
        int[] mask = new int[maskLen];
        int[] x = new int[3];
        int[] du = new int[3];
        int[] dv = new int[3];
        QuadBatch batch = new QuadBatch();

        for (int d = 0; d < 3; d++) {
            int u = (d + 1) % 3;
            int v = (d + 2) % 3;
            for (x[d] = -1; x[d] < dims[d]; ) {
                int n = 0;
                for (x[v] = 0; x[v] < dims[v]; x[v]++) {
                    for (x[u] = 0; x[u] < dims[u]; x[u]++, n++) {
                        int a = slot(voxels, sizeX, sizeY, sizeZ, x[0], x[1], x[2]);
                        int bx = x[0];
                        int by = x[1];
                        int bz = x[2];
                        if (d == 0) {
                            bx++;
                        } else if (d == 1) {
                            by++;
                        } else {
                            bz++;
                        }
                        int b = slot(voxels, sizeX, sizeY, sizeZ, bx, by, bz);
                        if (a != 0 && b == 0) {
                            mask[n] = a;
                        } else if (b != 0 && a == 0) {
                            mask[n] = -b;
                        } else {
                            mask[n] = 0;
                        }
                    }
                }
                x[d]++;
                n = 0;
                for (int j = 0; j < dims[v]; j++) {
                    for (int i = 0; i < dims[u]; ) {
                        int c = mask[n];
                        if (c == 0) {
                            i++;
                            n++;
                            continue;
                        }
                        int w = 1;
                        while (i + w < dims[u] && mask[n + w] == c
                                && sameBlock(origin[u], i, i + w, block)) {
                            w++;
                        }
                        int h = 1;
                        boolean stopped = false;
                        while (j + h < dims[v] && sameBlock(origin[v], j, j + h, block)) {
                            for (int k = 0; k < w; k++) {
                                if (mask[n + k + h * dims[u]] != c) {
                                    stopped = true;
                                    break;
                                }
                            }
                            if (stopped) {
                                break;
                            }
                            h++;
                        }
                        x[u] = i;
                        x[v] = j;
                        du[0] = du[1] = du[2] = 0;
                        dv[0] = dv[1] = dv[2] = 0;
                        du[u] = w;
                        dv[v] = h;
                        batch.emit(c, x, du, dv, rgbaBySlot, origin[0], origin[1], color, w * h);
                        for (int ly = 0; ly < h; ly++) {
                            for (int lx = 0; lx < w; lx++) {
                                mask[n + lx + ly * dims[u]] = 0;
                            }
                        }
                        i += w;
                        n += w;
                    }
                }
            }
        }
        return batch.finish();
    }

    /** Palette slot at an inner coordinate. The grid is padded by one voxel on x and y. */
    private static int slot(byte[] voxels, int sizeX, int sizeY, int sizeZ, int x, int y, int z) {
        int gx = x + 1;
        int gy = y + 1;
        if (gx < 0 || gy < 0 || z < 0 || gx >= sizeX || gy >= sizeY || z >= sizeZ) {
            return 0;
        }
        return voxels[gx + sizeX * (gy + sizeY * z)] & 0xFF;
    }

    private static boolean sameBlock(int origin, int start, int next, int block) {
        if (block <= 1) {
            return true;
        }
        return Math.floorDiv(origin + start, block) == Math.floorDiv(origin + next, block);
    }
}
