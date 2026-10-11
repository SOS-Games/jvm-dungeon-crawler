package io.github.jvmdc.converter;

import com.badlogic.gdx.graphics.Color;

import java.util.ArrayList;
import java.util.List;

/**
 * Joins coplanar voxel faces that share a colour into rectangles.
 * The grid has a one-voxel pad on x and y. Only faces owned by the inner
 * volume are kept, so a chunk does not draw a wall against the next chunk.
 */
final class GreedyMesh {
    private static final int VERTEX_SIZE = VolumeMesh.VERTEX_SIZE;
    private static final int MAX_QUADS = 16000;

    private GreedyMesh() {
    }

    static VolumeMesh build(byte[] voxels, int sizeX, int sizeY, int sizeZ, int[] rgbaBySlot) {
        int innerX = sizeX - 2;
        int innerY = sizeY - 2;
        int innerZ = sizeZ;
        if (innerX <= 0 || innerY <= 0 || innerZ <= 0) {
            return new VolumeMesh(new float[0][], new short[0][], 0, 0);
        }
        int[] dims = {innerX, innerY, innerZ};
        int[] mask = new int[innerX * innerY];
        int[] x = new int[3];
        int[] du = new int[3];
        int[] dv = new int[3];
        List<float[]> vertParts = new ArrayList<>();
        List<short[]> indexParts = new ArrayList<>();
        float[] verts = new float[MAX_QUADS * 4 * VERTEX_SIZE];
        short[] inds = new short[MAX_QUADS * 6];
        int exposed = 0;
        int[] cursor = new int[3];

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
                        while (i + w < dims[u] && mask[n + w] == c) {
                            w++;
                        }
                        int h = 1;
                        boolean stopped = false;
                        while (j + h < dims[v]) {
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
                        exposed += w * h;
                        if (cursor[1] / 6 + 1 > MAX_QUADS) {
                            vertParts.add(copy(verts, cursor[0]));
                            indexParts.add(copyShorts(inds, cursor[1]));
                            cursor[0] = 0;
                            cursor[1] = 0;
                        }
                        emit(verts, inds, cursor, rgbaBySlot, c, x, du, dv);
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
        if (cursor[0] > 0) {
            vertParts.add(copy(verts, cursor[0]));
            indexParts.add(copyShorts(inds, cursor[1]));
        }
        return new VolumeMesh(
                vertParts.toArray(new float[0][]),
                indexParts.toArray(new short[0][]),
                cursor[2],
                exposed);
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

    private static void emit(float[] verts, short[] inds, int[] cursor, int[] rgbaBySlot,
                             int c, int[] x, int[] du, int[] dv) {
        int slot = Math.abs(c);
        int rgba = slot < rgbaBySlot.length ? rgbaBySlot[slot] : 0;
        float color = Color.toFloatBits(
                ((rgba >> 24) & 0xFF) / 255f,
                ((rgba >> 16) & 0xFF) / 255f,
                ((rgba >> 8) & 0xFF) / 255f,
                0f);
        float[][] p = new float[4][3];
        for (int i = 0; i < 4; i++) {
            int ox = (i == 1 || i == 2) ? 1 : 0;
            int oy = (i == 2 || i == 3) ? 1 : 0;
            float vx = x[0] + du[0] * ox + dv[0] * oy;
            float vy = x[1] + du[1] * ox + dv[1] * oy;
            float vz = x[2] + du[2] * ox + dv[2] * oy;
            p[i][0] = vx;
            p[i][1] = vz;
            p[i][2] = -vy;
        }
        if (c < 0) {
            float[] swap = p[1];
            p[1] = p[3];
            p[3] = swap;
        }
        float ex = p[1][0] - p[0][0];
        float ey = p[1][1] - p[0][1];
        float ez = p[1][2] - p[0][2];
        float fx = p[2][0] - p[0][0];
        float fy = p[2][1] - p[0][1];
        float fz = p[2][2] - p[0][2];
        float nx = ey * fz - ez * fy;
        float ny = ez * fx - ex * fz;
        float nz = ex * fy - ey * fx;
        float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (len > 1e-8f) {
            nx /= len;
            ny /= len;
            nz /= len;
        }
        int base = cursor[0] / VERTEX_SIZE;
        for (int i = 0; i < 4; i++) {
            int o = cursor[0];
            verts[o] = p[i][0];
            verts[o + 1] = p[i][1];
            verts[o + 2] = p[i][2];
            verts[o + 3] = nx;
            verts[o + 4] = ny;
            verts[o + 5] = nz;
            verts[o + 6] = color;
            verts[o + 7] = 1f;
            cursor[0] += VERTEX_SIZE;
        }
        int k = cursor[1];
        inds[k] = (short) base;
        inds[k + 1] = (short) (base + 1);
        inds[k + 2] = (short) (base + 2);
        inds[k + 3] = (short) base;
        inds[k + 4] = (short) (base + 2);
        inds[k + 5] = (short) (base + 3);
        cursor[1] += 6;
        cursor[2]++;
    }

    private static float[] copy(float[] src, int length) {
        float[] out = new float[length];
        System.arraycopy(src, 0, out, 0, length);
        return out;
    }

    private static short[] copyShorts(short[] src, int length) {
        short[] out = new short[length];
        System.arraycopy(src, 0, out, 0, length);
        return out;
    }
}
