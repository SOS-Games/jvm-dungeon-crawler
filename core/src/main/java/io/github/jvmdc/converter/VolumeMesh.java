package io.github.jvmdc.converter;

import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;

/**
 * A chunk of greedy-meshed voxels, ready to draw. Vertices match the other
 * voxel meshes: position, normal, packed color, corner shadow. Several parts
 * exist only when one part would run out of 16-bit indices.
 */
public final class VolumeMesh {
    public static final int VERTEX_SIZE = 8;

    public final float[][] vertices;
    public final short[][] indices;
    /** Rectangles that were actually emitted. */
    public final int quads;
    /** Exposed voxel faces before rectangles were joined. */
    public final int exposed;

    VolumeMesh(float[][] vertices, short[][] indices, int quads, int exposed) {
        this.vertices = vertices;
        this.indices = indices;
        this.quads = quads;
        this.exposed = exposed;
    }

    /** Uploads the parts. Needs a live GL context. */
    public VoxMesher.VoxMesh upload() {
        VertexAttributes attributes = new VertexAttributes(
                VertexAttribute.Position(),
                VertexAttribute.Normal(),
                VertexAttribute.ColorPacked(),
                new VertexAttribute(VertexAttributes.Usage.Generic, 1, "a_ao")
        );
        Mesh[] parts = new Mesh[vertices.length];
        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        float maxZ = Float.NEGATIVE_INFINITY;
        for (int i = 0; i < vertices.length; i++) {
            float[] verts = vertices[i];
            Mesh mesh = new Mesh(true, verts.length / VERTEX_SIZE, indices[i].length, attributes);
            mesh.setVertices(verts);
            mesh.setIndices(indices[i]);
            parts[i] = mesh;
            for (int v = 0; v < verts.length; v += VERTEX_SIZE) {
                minX = Math.min(minX, verts[v]);
                minY = Math.min(minY, verts[v + 1]);
                minZ = Math.min(minZ, verts[v + 2]);
                maxX = Math.max(maxX, verts[v]);
                maxY = Math.max(maxY, verts[v + 1]);
                maxZ = Math.max(maxZ, verts[v + 2]);
            }
        }
        float span = 1f;
        if (minX <= maxX) {
            span = Math.max(1f, Math.max(maxX - minX, Math.max(maxY - minY, maxZ - minZ)));
        }
        return new VoxMesher.VoxMesh(parts, span, minX, minY, minZ, maxX, maxY, maxZ);
    }
}
