package io.github.jvmdc.converter;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.utils.FloatArray;
import com.badlogic.gdx.utils.ShortArray;

public class VoxMesher {

    // Vertex layout: Position (3), Normal (3), ColorPacked (1) = 7 floats per vertex
    private static final int VERTEX_SIZE = 7;

    public static Mesh buildMesh(VoxReader.VoxData vox) {
        FloatArray vertices = new FloatArray();
        ShortArray indices = new ShortArray();

        int sizeX = vox.sizeX;
        int sizeY = vox.sizeY;
        int sizeZ = vox.sizeZ;
        byte[][][] grid = vox.grid;

        // Center model on pivot (0, 0, 0)
        float halfX = sizeX / 2.0f;
        float halfY = sizeY / 2.0f;
        float halfZ = sizeZ / 2.0f;

        for (int x = 0; x < sizeX; x++) {
            for (int y = 0; y < sizeY; y++) {
                for (int z = 0; z < sizeZ; z++) {
                    byte colIdx = grid[x][y][z];
                    if (colIdx == 0) continue; // Empty voxel

                    // Color lookup (MagicaVoxel 1-based index)
                    int rgba = vox.palette[(colIdx & 0xFF) - 1];
                    float packedColor = Color.toFloatBits(
                        ((rgba >> 24) & 0xFF) / 255.0f,
                        ((rgba >> 16) & 0xFF) / 255.0f,
                        ((rgba >> 8) & 0xFF) / 255.0f,
                        1.0f
                    );

                    // Coordinate conversion: vox(X, Y, Z) -> gdx(X, Z, -Y)
                    float gx = x - halfX;
                    float gy = z - halfZ;
                    float gz = -(y - halfY);

                    // Check 6 adjacent neighbors
                    // +Y (Vox +Z / Top face)
                    if (z + 1 >= sizeZ || grid[x][y][z + 1] == 0) {
                        addQuad(vertices, indices,
                            gx, gy + 1, gz,
                            gx + 1, gy + 1, gz,
                            gx + 1, gy + 1, gz - 1,
                            gx, gy + 1, gz - 1,
                            0, 1, 0, packedColor);
                    }
                    // -Y (Vox -Z / Bottom face)
                    if (z - 1 < 0 || grid[x][y][z - 1] == 0) {
                        addQuad(vertices, indices,
                            gx, gy, gz - 1,
                            gx + 1, gy, gz - 1,
                            gx + 1, gy, gz,
                            gx, gy, gz,
                            0, -1, 0, packedColor);
                    }
                    // +X face
                    if (x + 1 >= sizeX || grid[x + 1][y][z] == 0) {
                        addQuad(vertices, indices,
                            gx + 1, gy, gz,
                            gx + 1, gy, gz - 1,
                            gx + 1, gy + 1, gz - 1,
                            gx + 1, gy + 1, gz,
                            1, 0, 0, packedColor);
                    }
                    // -X face
                    if (x - 1 < 0 || grid[x - 1][y][z] == 0) {
                        addQuad(vertices, indices,
                            gx, gy, gz - 1,
                            gx, gy, gz,
                            gx, gy + 1, gz,
                            gx, gy + 1, gz - 1,
                            -1, 0, 0, packedColor);
                    }
                    // +Z face (Vox -Y)
                    if (y - 1 < 0 || grid[x][y - 1][z] == 0) {
                        addQuad(vertices, indices,
                            gx, gy, gz,
                            gx + 1, gy, gz,
                            gx + 1, gy + 1, gz,
                            gx, gy + 1, gz,
                            0, 0, 1, packedColor);
                    }
                    // -Z face (Vox +Y)
                    if (y + 1 >= sizeY || grid[x][y + 1][z] == 0) {
                        addQuad(vertices, indices,
                            gx + 1, gy, gz - 1,
                            gx, gy, gz - 1,
                            gx, gy + 1, gz - 1,
                            gx + 1, gy + 1, gz - 1,
                            0, 0, -1, packedColor);
                    }
                }
            }
        }

        VertexAttributes attributes = new VertexAttributes(
            VertexAttribute.Position(),
            VertexAttribute.Normal(),
            VertexAttribute.ColorPacked()
        );

        Mesh mesh = new Mesh(true, vertices.size / VERTEX_SIZE, indices.size, attributes);
        mesh.setVertices(vertices.items, 0, vertices.size);
        mesh.setIndices(indices.items, 0, indices.size);
        return mesh;
    }

    private static void addQuad(FloatArray v, ShortArray idx,
                                float x1, float y1, float z1,
                                float x2, float y2, float z2,
                                float x3, float y3, float z3,
                                float x4, float y4, float z4,
                                float nx, float ny, float nz, float color) {
        short offset = (short) (v.size / VERTEX_SIZE);

        // Vertex 1
        v.add(x1); v.add(y1); v.add(z1); v.add(nx); v.add(ny); v.add(nz); v.add(color);
        // Vertex 2
        v.add(x2); v.add(y2); v.add(z2); v.add(nx); v.add(ny); v.add(nz); v.add(color);
        // Vertex 3
        v.add(x3); v.add(y3); v.add(z3); v.add(nx); v.add(ny); v.add(nz); v.add(color);
        // Vertex 4
        v.add(x4); v.add(y4); v.add(z4); v.add(nx); v.add(ny); v.add(nz); v.add(color);

        // 2 triangles per quad
        idx.add(offset);
        idx.add((short) (offset + 1));
        idx.add((short) (offset + 2));

        idx.add((short) (offset + 2));
        idx.add((short) (offset + 3));
        idx.add(offset);
    }
}