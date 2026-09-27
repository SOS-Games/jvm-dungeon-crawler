package io.github.jvmdc.converter;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.utils.FloatArray;
import com.badlogic.gdx.utils.ShortArray;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns a voxel scene into triangle meshes. Hidden faces are dropped. A model
 * that does not fit in 16-bit indices is split into several meshes. Palette
 * slots 13 to 15 are marked glowing in the color alpha. Slot 16 is a hole.
 */
public class VoxMesher {

    // Position (3), normal (3), packed color (1)
    private static final int VERTEX_SIZE = 7;
    private static final int MAX_VERTS = 65532;

    public static final class VoxMesh {
        public final Mesh[] parts;
        public final float span;

        VoxMesh(Mesh[] parts, float span) {
            this.parts = parts;
            this.span = span;
        }

        public void dispose() {
            for (Mesh part : parts) {
                part.dispose();
            }
        }
    }

    /** One mesh for a single model. Prefer {@link #build(VoxReader.VoxScene)} for whole files. */
    public static Mesh buildMesh(VoxReader.VoxData vox) {
        VoxReader.VoxScene scene = new VoxReader.VoxScene();
        VoxReader.VoxModel model = new VoxReader.VoxModel();
        model.sizeX = vox.sizeX;
        model.sizeY = vox.sizeY;
        model.sizeZ = vox.sizeZ;
        model.grid = vox.grid;
        scene.models.add(model);
        System.arraycopy(vox.palette, 0, scene.palette, 0, Math.min(vox.palette.length, scene.palette.length));
        scene.instances.add(new VoxReader.VoxInstance(0,
                new float[] {1, 0, 0, 0, 1, 0, 0, 0, 1}, new float[3]));
        VoxMesh mesh = build(scene);
        if (mesh.parts.length == 0) {
            throw new IllegalArgumentException("vox has no solid voxels");
        }
        for (int i = 1; i < mesh.parts.length; i++) {
            mesh.parts[i].dispose();
        }
        return mesh.parts[0];
    }

    public static VoxMesh build(VoxReader.VoxScene scene) {
        Builder builder = new Builder(scene.palette);
        for (VoxReader.VoxInstance instance : scene.instances) {
            if (instance.model < 0 || instance.model >= scene.models.size()) {
                continue;
            }
            VoxReader.VoxModel model = scene.models.get(instance.model);
            if (model.grid == null) {
                continue;
            }
            builder.addModel(model, instance.rotation, instance.translation);
        }
        return builder.finish();
    }

    private static final class Builder {
        private final int[] palette;
        private final List<FloatArray> vertParts = new ArrayList<>();
        private final List<ShortArray> indexParts = new ArrayList<>();
        private FloatArray vertices = new FloatArray();
        private ShortArray indices = new ShortArray();
        private float minX = Float.POSITIVE_INFINITY;
        private float minY = Float.POSITIVE_INFINITY;
        private float minZ = Float.POSITIVE_INFINITY;
        private float maxX = Float.NEGATIVE_INFINITY;
        private float maxY = Float.NEGATIVE_INFINITY;
        private float maxZ = Float.NEGATIVE_INFINITY;
        private final float[] p0 = new float[3];
        private final float[] p1 = new float[3];
        private final float[] p2 = new float[3];
        private final float[] p3 = new float[3];
        private final VertexAttributes attributes = new VertexAttributes(
                VertexAttribute.Position(),
                VertexAttribute.Normal(),
                VertexAttribute.ColorPacked()
        );

        Builder(int[] palette) {
            this.palette = palette;
            vertParts.add(vertices);
            indexParts.add(indices);
        }

        void addModel(VoxReader.VoxModel model, float[] rotation, float[] translation) {
            int sizeX = model.sizeX;
            int sizeY = model.sizeY;
            int sizeZ = model.sizeZ;
            byte[][][] grid = model.grid;
            int[] colors = model.palette != null ? model.palette : palette;
            for (int x = 0; x < sizeX; x++) {
                for (int y = 0; y < sizeY; y++) {
                    for (int z = 0; z < sizeZ; z++) {
                        byte colIdx = grid[x][y][z];
                        if (!occupied(colIdx)) {
                            continue;
                        }
                        int paletteIndex = (colIdx & 0xFF) - 1;
                        if (paletteIndex < 0 || paletteIndex >= colors.length) {
                            continue;
                        }
                        int rgba = colors[paletteIndex];
                        float packedColor = Color.toFloatBits(
                                ((rgba >> 24) & 0xFF) / 255f,
                                ((rgba >> 16) & 0xFF) / 255f,
                                ((rgba >> 8) & 0xFF) / 255f,
                                glows(colIdx) ? 1f : 0f
                        );
                        if (z + 1 >= sizeZ || !occupied(grid[x][y][z + 1])) {
                            addQuad(model, rotation, translation, packedColor,
                                    x, y, z + 1, x + 1, y, z + 1, x + 1, y + 1, z + 1, x, y + 1, z + 1);
                        }
                        if (z - 1 < 0 || !occupied(grid[x][y][z - 1])) {
                            addQuad(model, rotation, translation, packedColor,
                                    x, y + 1, z, x + 1, y + 1, z, x + 1, y, z, x, y, z);
                        }
                        if (x + 1 >= sizeX || !occupied(grid[x + 1][y][z])) {
                            addQuad(model, rotation, translation, packedColor,
                                    x + 1, y, z, x + 1, y + 1, z, x + 1, y + 1, z + 1, x + 1, y, z + 1);
                        }
                        if (x - 1 < 0 || !occupied(grid[x - 1][y][z])) {
                            addQuad(model, rotation, translation, packedColor,
                                    x, y + 1, z, x, y, z, x, y, z + 1, x, y + 1, z + 1);
                        }
                        if (y - 1 < 0 || !occupied(grid[x][y - 1][z])) {
                            addQuad(model, rotation, translation, packedColor,
                                    x, y, z, x + 1, y, z, x + 1, y, z + 1, x, y, z + 1);
                        }
                        if (y + 1 >= sizeY || !occupied(grid[x][y + 1][z])) {
                            addQuad(model, rotation, translation, packedColor,
                                    x + 1, y + 1, z, x, y + 1, z, x, y + 1, z + 1, x + 1, y + 1, z + 1);
                        }
                    }
                }
            }
        }

        /**
         * Voxel corner to Y-up. The scene rotation and translation happen in
         * voxel space first (Z up), then Y becomes up.
         */
        private void corner(VoxReader.VoxModel model, float[] rotation, float[] translation,
                            int x, int y, int z, float[] out) {
            float lx = x - model.sizeX * 0.5f;
            float ly = y - model.sizeY * 0.5f;
            float lz = z - model.sizeZ * 0.5f;
            float wx = rotation[0] * lx + rotation[1] * ly + rotation[2] * lz + translation[0];
            float wy = rotation[3] * lx + rotation[4] * ly + rotation[5] * lz + translation[1];
            float wz = rotation[6] * lx + rotation[7] * ly + rotation[8] * lz + translation[2];
            out[0] = wx;
            out[1] = wz;
            out[2] = -wy;
        }

        private void addQuad(VoxReader.VoxModel model, float[] rotation, float[] translation, float color,
                             int x0, int y0, int z0, int x1, int y1, int z1,
                             int x2, int y2, int z2, int x3, int y3, int z3) {
            if (vertices.size / VERTEX_SIZE + 4 > MAX_VERTS) {
                vertices = new FloatArray();
                indices = new ShortArray();
                vertParts.add(vertices);
                indexParts.add(indices);
            }
            corner(model, rotation, translation, x0, y0, z0, p0);
            corner(model, rotation, translation, x1, y1, z1, p1);
            corner(model, rotation, translation, x2, y2, z2, p2);
            corner(model, rotation, translation, x3, y3, z3, p3);
            float ex = p1[0] - p0[0];
            float ey = p1[1] - p0[1];
            float ez = p1[2] - p0[2];
            float fx = p2[0] - p0[0];
            float fy = p2[1] - p0[1];
            float fz = p2[2] - p0[2];
            float nx = ey * fz - ez * fy;
            float ny = ez * fx - ex * fz;
            float nz = ex * fy - ey * fx;
            float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (len > 1e-8f) {
                nx /= len;
                ny /= len;
                nz /= len;
            }
            // A mirror (left limbs) flips the triangle order. Swap a corner and
            // the normal so the outside face stays the one that is drawn.
            if (determinant(rotation) < 0f) {
                float sx = p1[0];
                float sy = p1[1];
                float sz = p1[2];
                p1[0] = p3[0];
                p1[1] = p3[1];
                p1[2] = p3[2];
                p3[0] = sx;
                p3[1] = sy;
                p3[2] = sz;
                nx = -nx;
                ny = -ny;
                nz = -nz;
            }
            include(p0);
            include(p1);
            include(p2);
            include(p3);
            short offset = (short) (vertices.size / VERTEX_SIZE);
            put(p0, nx, ny, nz, color);
            put(p1, nx, ny, nz, color);
            put(p2, nx, ny, nz, color);
            put(p3, nx, ny, nz, color);
            indices.add(offset);
            indices.add((short) (offset + 1));
            indices.add((short) (offset + 2));
            indices.add((short) (offset + 2));
            indices.add((short) (offset + 3));
            indices.add(offset);
        }

        private static float determinant(float[] r) {
            return r[0] * (r[4] * r[8] - r[5] * r[7])
                    - r[1] * (r[3] * r[8] - r[5] * r[6])
                    + r[2] * (r[3] * r[7] - r[4] * r[6]);
        }

        private void include(float[] p) {
            minX = Math.min(minX, p[0]);
            minY = Math.min(minY, p[1]);
            minZ = Math.min(minZ, p[2]);
            maxX = Math.max(maxX, p[0]);
            maxY = Math.max(maxY, p[1]);
            maxZ = Math.max(maxZ, p[2]);
        }

        /** Empty air and palette slot 16 (a hollow cell) do not hide a face. */
        private static boolean occupied(byte index) {
            int slot = index & 0xFF;
            return slot != 0 && slot != 16;
        }

        /** Palette slots 13, 14, and 15 are the glowing voxels. */
        private static boolean glows(byte index) {
            int slot = index & 0xFF;
            return slot >= 13 && slot <= 15;
        }

        private void put(float[] p, float nx, float ny, float nz, float color) {
            vertices.add(p[0]);
            vertices.add(p[1]);
            vertices.add(p[2]);
            vertices.add(nx);
            vertices.add(ny);
            vertices.add(nz);
            vertices.add(color);
        }

        VoxMesh finish() {
            if (vertices.size == 0 && vertParts.size() == 1) {
                return new VoxMesh(new Mesh[0], 1f);
            }
            float cx = (minX + maxX) * 0.5f;
            float cy = (minY + maxY) * 0.5f;
            float cz = (minZ + maxZ) * 0.5f;
            List<Mesh> meshes = new ArrayList<>();
            for (int part = 0; part < vertParts.size(); part++) {
                FloatArray verts = vertParts.get(part);
                ShortArray inds = indexParts.get(part);
                if (verts.size == 0) {
                    continue;
                }
                float[] items = verts.items;
                for (int i = 0; i < verts.size; i += VERTEX_SIZE) {
                    items[i] -= cx;
                    items[i + 1] -= cy;
                    items[i + 2] -= cz;
                }
                Mesh mesh = new Mesh(true, verts.size / VERTEX_SIZE, inds.size, attributes);
                mesh.setVertices(items, 0, verts.size);
                mesh.setIndices(inds.items, 0, inds.size);
                meshes.add(mesh);
            }
            float span = 1f;
            if (minX <= maxX) {
                span = Math.max(maxX - minX, Math.max(maxY - minY, maxZ - minZ));
                span = Math.max(1f, span);
            }
            return new VoxMesh(meshes.toArray(new Mesh[0]), span);
        }
    }
}
