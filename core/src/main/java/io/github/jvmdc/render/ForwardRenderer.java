package io.github.jvmdc.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.glutils.HdpiUtils;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Matrix4;

/**
 * Draws voxel meshes. A browser tile and the large view use the same shader:
 * palette colors and one directional light. The model matrix is how the large
 * view spins a mesh when the mouse drags.
 */
public final class ForwardRenderer {
    private final ShaderProgram shader;
    private final Matrix4 mvp = new Matrix4();

    public ForwardRenderer() {
        shader = new ShaderProgram(VERT, FRAG);
        if (!shader.isCompiled()) {
            throw new IllegalStateException(shader.getLog());
        }
    }

    /** Clear the window. Call before the tiles or the large view. */
    public void begin() {
        Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);
        HdpiUtils.glViewport(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        Gdx.gl.glClearColor(0.62f, 0.64f, 0.68f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glEnable(GL20.GL_CULL_FACE);
        Gdx.gl.glCullFace(GL20.GL_BACK);
        shader.bind();
    }

    /**
     * Limit the next draw to a rectangle. x and y are the bottom-left, in the
     * same pixels as the mouse.
     */
    public void tile(int x, int y, int width, int height) {
        if (width <= 0 || height <= 0) {
            return;
        }
        Gdx.gl.glEnable(GL20.GL_SCISSOR_TEST);
        HdpiUtils.glViewport(x, y, width, height);
        HdpiUtils.glScissor(x, y, width, height);
        Gdx.gl.glClearColor(0.40f, 0.42f, 0.46f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
    }

    public void draw(PerspectiveCamera camera, Mesh[] parts, Matrix4 model) {
        if (parts == null || parts.length == 0) {
            return;
        }
        mvp.set(camera.combined).mul(model);
        shader.setUniformMatrix("u_mvp", mvp);
        shader.setUniformMatrix("u_model", model);
        for (Mesh part : parts) {
            part.render(shader, GL20.GL_TRIANGLES);
        }
    }

    /** Full window again, so text is not stuck in the last tile. */
    public void end() {
        Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);
        HdpiUtils.glViewport(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDisable(GL20.GL_CULL_FACE);
    }

    public void dispose() {
        shader.dispose();
    }

    private static final String VERT = """
        #version 330
        in vec3 a_position;
        in vec3 a_normal;
        in vec4 a_color;
        uniform mat4 u_mvp;
        uniform mat4 u_model;
        out vec3 v_normal;
        out vec4 v_color;
        void main() {
            v_normal = mat3(u_model) * a_normal;
            v_color = a_color;
            gl_Position = u_mvp * vec4(a_position, 1.0);
        }
        """;

    private static final String FRAG = """
        #version 330
        in vec3 v_normal;
        in vec4 v_color;
        out vec4 frag;
        void main() {
            vec3 n = normalize(v_normal);
            vec3 light = normalize(vec3(0.35, 0.85, 0.45));
            float ndl = max(dot(n, light), 0.0);
            vec3 color = v_color.rgb * (0.32 + 0.68 * ndl);
            frag = vec4(color, 1.0);
        }
        """;
}
