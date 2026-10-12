package io.github.jvmdc.converter;

import com.badlogic.gdx.graphics.Color;

import java.util.ArrayList;
import java.util.List;

/** Collects rectangles into a mesh, flushing a part before the index limit. */
public final class QuadBatch {
    private static final int VERTEX_SIZE = VolumeMesh.VERTEX_SIZE;
    private static final int MAX_QUADS = 16000;

    private final List<float[]> vertParts = new ArrayList<>();
    private final List<short[]> indexParts = new ArrayList<>();
    private final float[] p0 = new float[3];
    private final float[] p1 = new float[3];
    private final float[] p2 = new float[3];
    private final float[] p3 = new float[3];
    private final float[][] p = {p0, p1, p2, p3};
    private final float[] corner = new float[4];
    private float[] verts = new float[MAX_QUADS * 4 * VERTEX_SIZE];
    private short[] inds = new short[MAX_QUADS * 6];
    private int vertCount;
    private int indexCount;
    private int quads;
    private int exposed;

    /**
     * One rectangle. {@code c} below zero flips the winding. The corner positions
     * are in chunk voxels, then stored Y-up. {@code area} is the voxel faces joined.
     */
    public void emit(int c, int[] x, int[] du, int[] dv, int[] rgbaBySlot, int originX, int originY,
                     VoxMesher.VoxelColor color, int area) {
        if (indexCount / 6 + 1 > MAX_QUADS) {
            flush();
        }
        int slot = Math.abs(c);
        int rgba = rgbaBySlot != null && slot < rgbaBySlot.length ? rgbaBySlot[slot] : 0;
        float flat = Color.toFloatBits(
                ((rgba >> 24) & 0xFF) / 255f,
                ((rgba >> 16) & 0xFF) / 255f,
                ((rgba >> 8) & 0xFF) / 255f,
                0f);
        for (int i = 0; i < 4; i++) {
            int ox = (i == 1 || i == 2) ? 1 : 0;
            int oy = (i == 2 || i == 3) ? 1 : 0;
            float vx = x[0] + du[0] * ox + dv[0] * oy;
            float vy = x[1] + du[1] * ox + dv[1] * oy;
            float vz = x[2] + du[2] * ox + dv[2] * oy;
            p[i][0] = vx;
            p[i][1] = vz;
            p[i][2] = -vy;
            corner[i] = color == null ? flat : packed(color.at(originX + vx, originY + vy, vz));
        }
        if (c < 0) {
            float sx = p1[0];
            float sy = p1[1];
            float sz = p1[2];
            p1[0] = p3[0];
            p1[1] = p3[1];
            p1[2] = p3[2];
            p3[0] = sx;
            p3[1] = sy;
            p3[2] = sz;
            float swapColor = corner[1];
            corner[1] = corner[3];
            corner[3] = swapColor;
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
        int base = vertCount / VERTEX_SIZE;
        for (int i = 0; i < 4; i++) {
            int o = vertCount;
            verts[o] = p[i][0];
            verts[o + 1] = p[i][1];
            verts[o + 2] = p[i][2];
            verts[o + 3] = nx;
            verts[o + 4] = ny;
            verts[o + 5] = nz;
            verts[o + 6] = corner[i];
            verts[o + 7] = 1f;
            vertCount += VERTEX_SIZE;
        }
        inds[indexCount] = (short) base;
        inds[indexCount + 1] = (short) (base + 1);
        inds[indexCount + 2] = (short) (base + 2);
        inds[indexCount + 3] = (short) base;
        inds[indexCount + 4] = (short) (base + 2);
        inds[indexCount + 5] = (short) (base + 3);
        indexCount += 6;
        quads++;
        exposed += area;
    }

    public VolumeMesh finish() {
        if (vertCount > 0) {
            flush();
        }
        return new VolumeMesh(
                vertParts.toArray(new float[0][]),
                indexParts.toArray(new short[0][]),
                quads,
                exposed);
    }

    private void flush() {
        vertParts.add(copy(verts, vertCount));
        indexParts.add(copyShorts(inds, indexCount));
        vertCount = 0;
        indexCount = 0;
    }

    private static float packed(int rgb) {
        return Color.toFloatBits(
                ((rgb >> 16) & 0xFF) / 255f,
                ((rgb >> 8) & 0xFF) / 255f,
                (rgb & 0xFF) / 255f,
                0f);
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
