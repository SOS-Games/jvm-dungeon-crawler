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
 * slots 14 to 16 are marked glowing in the color alpha. Slot 17 is a hole.
 * Each face corner carries how open the neighboring cells are, so creases shade darker.
 */
public class VoxMesher {

    // Position (3), normal (3), packed color (1), corner shadow (1)
    private static final int VERTEX_SIZE = 8;
    private static final int MAX_VERTS = 65532;

    public static final class VoxMesh {
        public final Mesh[] parts;
        public final float span;

        /** Bounds before the mesh is moved so its middle sits at the origin. */
        public final float minX, minY, minZ, maxX, maxY, maxZ;

        VoxMesh(Mesh[] parts, float span) {
            this(parts, span, 0f, 0f, 0f, 0f, 0f, 0f);
        }

        VoxMesh(Mesh[] parts, float span, float minX, float minY, float minZ,
                float maxX, float maxY, float maxZ) {
            this.parts = parts;
            this.span = span;
            this.minX = minX;
            this.minY = minY;
            this.minZ = minZ;
            this.maxX = maxX;
            this.maxY = maxY;
            this.maxZ = maxZ;
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
        return build(scene, null);
    }

    public static VoxMesh build(VoxReader.VoxScene scene, java.util.Map<Integer, int[]> paletteSwap) {
        if (paletteSwap != null && !paletteSwap.isEmpty()) {
            applyPaletteSwap(scene.palette, paletteSwap);
            for (VoxReader.VoxModel model : scene.models) {
                if (model.palette != null) {
                    applyPaletteSwap(model.palette, paletteSwap);
                }
            }
        }
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
        return builder.finish(true);
    }

    /**
     * One bone, still in voxel space (Z up). The manifest offset is added as-is.
     * A mirrored limb flips the voxels instead of the offset.
     */
    public static VoxMesh buildLocal(VoxReader.VoxModel model, float ox, float oy, float oz, boolean mirror) {
        if (model.grid == null) {
            return new VoxMesh(new Mesh[0], 1f);
        }
        VoxReader.VoxModel source = model;
        if (mirror) {
            source = flipX(model);
        }
        Builder builder = new Builder(model.palette);
        builder.local = true;
        // The faces are rebuilt from the flipped grid, so the outside still faces out.
        builder.addModel(source, new float[] {1, 0, 0, 0, 1, 0, 0, 0, 1}, new float[] {ox, oy, oz});
        return builder.finish(false);
    }

    /**
     * Y-up mesh whose origin is voxel (0,0,0). {@code outside} hides a face that
     * sits against another part, so the two surfaces are not drawn on top of each other.
     */
    public static VoxMesh buildCorner(VoxReader.VoxModel model, Outside outside) {
        if (model == null || model.grid == null) {
            return new VoxMesh(new Mesh[0], 1f);
        }
        Builder builder = new Builder(model.palette);
        builder.cornerOrigin = true;
        builder.outside = outside;
        builder.addModel(model, new float[] {1, 0, 0, 0, 1, 0, 0, 0, 1}, new float[] {0, 0, 0});
        return builder.finish(false);
    }

    /** True when a cell outside this part is already solid in an earlier part. */
    public interface Outside {
        boolean covers(int x, int y, int z);
    }

    /**
     * Greedy-meshes one chunk. {@code voxels} is x + sizeX * (y + sizeY * z),
     * with a one-voxel pad on x and y so a face on the chunk edge is dropped
     * when the next chunk is solid. 0 is empty. Any other byte is a slot in
     * {@code rgbaBySlot} (red in the high byte). Vertices are Y-up, in voxels,
     * and 0 is the chunk corner rather than the pad.
     */
    public static VolumeMesh meshVolume(byte[] voxels, int sizeX, int sizeY, int sizeZ, int[] rgbaBySlot) {
        return GreedyMesh.build(voxels, sizeX, sizeY, sizeZ, rgbaBySlot);
    }

    /**
     * Same as {@link #meshVolume}, but rectangles stop on block boundaries and
     * each corner takes its colour from {@code color} (chunk voxel x, map y, up).
     */
    public static VolumeMesh meshVolume(byte[] voxels, int sizeX, int sizeY, int sizeZ, int[] rgbaBySlot,
                                        int originX, int originY, int block, VoxelColor color) {
        return GreedyMesh.build(voxels, sizeX, sizeY, sizeZ, rgbaBySlot, originX, originY, block, color);
    }

    public static void applyPaletteSwap(int[] palette, java.util.Map<Integer, int[]> paletteSwap) {
        if (palette == null || paletteSwap == null || paletteSwap.isEmpty()) {
            return;
        }
        for (java.util.Map.Entry<Integer, int[]> entry : paletteSwap.entrySet()) {
            int slot = entry.getKey();
            if (slot >= 1 && slot <= palette.length) {
                int[] rgb = entry.getValue();
                if (rgb != null && rgb.length >= 3) {
                    int r = rgb[0] & 0xFF;
                    int g = rgb[1] & 0xFF;
                    int b = rgb[2] & 0xFF;
                    int a = rgb.length >= 4 ? (rgb[3] & 0xFF) : 0xFF;
                    palette[slot - 1] = (r << 24) | (g << 16) | (b << 8) | a;
                }
            }
        }
    }

    /** Colour at a point in the chunk, packed as red, green, blue. */
    public interface VoxelColor {
        int at(float x, float y, float z);
    }

    private static VoxReader.VoxModel flipX(VoxReader.VoxModel model) {
        VoxReader.VoxModel copy = new VoxReader.VoxModel();
        copy.sizeX = model.sizeX;
        copy.sizeY = model.sizeY;
        copy.sizeZ = model.sizeZ;
        copy.palette = model.palette;
        copy.grid = new byte[model.sizeX][model.sizeY][model.sizeZ];
        for (int x = 0; x < model.sizeX; x++) {
            for (int y = 0; y < model.sizeY; y++) {
                for (int z = 0; z < model.sizeZ; z++) {
                    copy.grid[model.sizeX - 1 - x][y][z] = model.grid[x][y][z];
                }
            }
        }
        return copy;
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
        private boolean local;
        /** Y-up, with voxel (0,0,0) at the mesh origin instead of the centre. */
        private boolean cornerOrigin;
        private Outside outside;
        private boolean flipWinding;
        private final VertexAttributes attributes = new VertexAttributes(
                VertexAttribute.Position(),
                VertexAttribute.Normal(),
                VertexAttribute.ColorPacked(),
                new VertexAttribute(VertexAttributes.Usage.Generic, 1, "a_ao")
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
                        if (!covered(grid, sizeX, sizeY, sizeZ, x, y, z + 1)) {
                            addQuad(model, grid, x, y, z, 0, 0, 1, rotation, translation, packedColor,
                                    x, y, z + 1, x + 1, y, z + 1, x + 1, y + 1, z + 1, x, y + 1, z + 1);
                        }
                        if (!covered(grid, sizeX, sizeY, sizeZ, x, y, z - 1)) {
                            addQuad(model, grid, x, y, z, 0, 0, -1, rotation, translation, packedColor,
                                    x, y + 1, z, x + 1, y + 1, z, x + 1, y, z, x, y, z);
                        }
                        if (!covered(grid, sizeX, sizeY, sizeZ, x + 1, y, z)) {
                            addQuad(model, grid, x, y, z, 1, 0, 0, rotation, translation, packedColor,
                                    x + 1, y, z, x + 1, y + 1, z, x + 1, y + 1, z + 1, x + 1, y, z + 1);
                        }
                        if (!covered(grid, sizeX, sizeY, sizeZ, x - 1, y, z)) {
                            addQuad(model, grid, x, y, z, -1, 0, 0, rotation, translation, packedColor,
                                    x, y + 1, z, x, y, z, x, y, z + 1, x, y + 1, z + 1);
                        }
                        if (!covered(grid, sizeX, sizeY, sizeZ, x, y - 1, z)) {
                            addQuad(model, grid, x, y, z, 0, -1, 0, rotation, translation, packedColor,
                                    x, y, z, x + 1, y, z, x + 1, y, z + 1, x, y, z + 1);
                        }
                        if (!covered(grid, sizeX, sizeY, sizeZ, x, y + 1, z)) {
                            addQuad(model, grid, x, y, z, 0, 1, 0, rotation, translation, packedColor,
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
            boolean fromCorner = local || cornerOrigin;
            float lx = fromCorner ? x : x - model.sizeX * 0.5f;
            float ly = fromCorner ? y : y - model.sizeY * 0.5f;
            float lz = fromCorner ? z : z - model.sizeZ * 0.5f;
            float wx = rotation[0] * lx + rotation[1] * ly + rotation[2] * lz + translation[0];
            float wy = rotation[3] * lx + rotation[4] * ly + rotation[5] * lz + translation[1];
            float wz = rotation[6] * lx + rotation[7] * ly + rotation[8] * lz + translation[2];
            if (local) {
                out[0] = wx;
                out[1] = wy;
                out[2] = wz;
            } else {
                out[0] = wx;
                out[1] = wz;
                out[2] = -wy;
            }
        }

        private void addQuad(VoxReader.VoxModel model, byte[][][] grid,
                             int vx, int vy, int vz, int nx, int ny, int nz,
                             float[] rotation, float[] translation, float color,
                             int x0, int y0, int z0, int x1, int y1, int z1,
                             int x2, int y2, int z2, int x3, int y3, int z3) {
            if (vertices.size / VERTEX_SIZE + 4 > MAX_VERTS) {
                vertices = new FloatArray();
                indices = new ShortArray();
                vertParts.add(vertices);
                indexParts.add(indices);
            }
            float ao0 = cornerAo(grid, vx, vy, vz, x0, y0, z0, nx, ny, nz);
            float ao1 = cornerAo(grid, vx, vy, vz, x1, y1, z1, nx, ny, nz);
            float ao2 = cornerAo(grid, vx, vy, vz, x2, y2, z2, nx, ny, nz);
            float ao3 = cornerAo(grid, vx, vy, vz, x3, y3, z3, nx, ny, nz);
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
            float gx = ey * fz - ez * fy;
            float gy = ez * fx - ex * fz;
            float gz = ex * fy - ey * fx;
            float len = (float) Math.sqrt(gx * gx + gy * gy + gz * gz);
            if (len > 1e-8f) {
                gx /= len;
                gy /= len;
                gz /= len;
            }
            // A mirror (left limbs) flips the triangle order. Swap a corner and
            // the normal so the outside face stays the one that is drawn.
            if (flipWinding || determinant(rotation) < 0f) {
                float sx = p1[0];
                float sy = p1[1];
                float sz = p1[2];
                p1[0] = p3[0];
                p1[1] = p3[1];
                p1[2] = p3[2];
                p3[0] = sx;
                p3[1] = sy;
                p3[2] = sz;
                float swapAo = ao1;
                ao1 = ao3;
                ao3 = swapAo;
                gx = -gx;
                gy = -gy;
                gz = -gz;
            }
            include(p0);
            include(p1);
            include(p2);
            include(p3);
            short offset = (short) (vertices.size / VERTEX_SIZE);
            put(p0, gx, gy, gz, color, ao0);
            put(p1, gx, gy, gz, color, ao1);
            put(p2, gx, gy, gz, color, ao2);
            put(p3, gx, gy, gz, color, ao3);
            // The diagonal joins the brighter pair, so one dark corner shades a triangle
            // instead of a band across the face.
            if (ao0 + ao2 < ao1 + ao3) {
                indices.add(offset);
                indices.add((short) (offset + 1));
                indices.add((short) (offset + 3));
                indices.add((short) (offset + 1));
                indices.add((short) (offset + 2));
                indices.add((short) (offset + 3));
            } else {
                indices.add(offset);
                indices.add((short) (offset + 1));
                indices.add((short) (offset + 2));
                indices.add((short) (offset + 2));
                indices.add((short) (offset + 3));
                indices.add(offset);
            }
        }

        /**
         * How open the cells around this face corner are, from 0 to 1. The cell
         * the face looks into counts, plus the two beside the corner. The diagonal
         * counts only when one of those sides is open.
         */
        private static float cornerAo(byte[][][] grid, int vx, int vy, int vz,
                                      int cx, int cy, int cz, int nx, int ny, int nz) {
            int fx = vx + nx;
            int fy = vy + ny;
            int fz = vz + nz;
            int stepX = nx == 0 ? (cx == vx ? -1 : 1) : 0;
            int stepY = ny == 0 ? (cy == vy ? -1 : 1) : 0;
            int stepZ = nz == 0 ? (cz == vz ? -1 : 1) : 0;
            boolean side1;
            boolean side2;
            boolean diagonal;
            if (nx != 0) {
                side1 = open(grid, fx, fy + stepY, fz);
                side2 = open(grid, fx, fy, fz + stepZ);
                diagonal = open(grid, fx, fy + stepY, fz + stepZ);
            } else if (ny != 0) {
                side1 = open(grid, fx + stepX, fy, fz);
                side2 = open(grid, fx, fy, fz + stepZ);
                diagonal = open(grid, fx + stepX, fy, fz + stepZ);
            } else {
                side1 = open(grid, fx + stepX, fy, fz);
                side2 = open(grid, fx, fy + stepY, fz);
                diagonal = open(grid, fx + stepX, fy + stepY, fz);
            }
            float diag = (side1 || side2) && diagonal ? 1f : 0f;
            return ((side1 ? 1f : 0f) + (side2 ? 1f : 0f) + diag + 1f) * 0.25f;
        }

        /** Missing cells and holes let light in. */
        private static boolean open(byte[][][] grid, int x, int y, int z) {
            if (x < 0 || y < 0 || z < 0
                    || x >= grid.length || y >= grid[0].length || z >= grid[0][0].length) {
                return true;
            }
            return !occupied(grid[x][y][z]);
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

        private boolean covered(byte[][][] grid, int sx, int sy, int sz, int x, int y, int z) {
            if (x >= 0 && y >= 0 && z >= 0 && x < sx && y < sy && z < sz && occupied(grid[x][y][z])) {
                return true;
            }
            return outside != null && outside.covers(x, y, z);
        }

        /** Empty air and palette slot 17 (a hollow cell) do not hide a face. */
        private static boolean occupied(byte index) {
            int slot = index & 0xFF;
            return slot != 0 && slot != 17;
        }

        /** Palette slots 14, 15, and 16 are the glowing voxels. */
        private static boolean glows(byte index) {
            int slot = index & 0xFF;
            return slot >= 14 && slot <= 16;
        }

        private void put(float[] p, float nx, float ny, float nz, float color, float ao) {
            vertices.add(p[0]);
            vertices.add(p[1]);
            vertices.add(p[2]);
            vertices.add(nx);
            vertices.add(ny);
            vertices.add(nz);
            vertices.add(color);
            vertices.add(ao);
        }

        VoxMesh finish(boolean center) {
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
                if (center) {
                    for (int i = 0; i < verts.size; i += VERTEX_SIZE) {
                        items[i] -= cx;
                        items[i + 1] -= cy;
                        items[i + 2] -= cz;
                    }
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
            return new VoxMesh(meshes.toArray(new Mesh[0]), span, minX, minY, minZ, maxX, maxY, maxZ);
        }
    }
}
