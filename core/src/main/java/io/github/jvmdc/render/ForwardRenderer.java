package io.github.jvmdc.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.glutils.HdpiUtils;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;

/**
 * Draws voxel meshes. A browser tile and the large view use the same shader:
 * Veloren's sun, a nearby yellow lamp, corner shadows, and a tone curve.
 * The backdrop is green toward the ground and blue overhead, with the sun
 * drawn in that blue. Glowing voxels add their own light. The model matrix
 * is how the large view spins a mesh.
 */
public final class ForwardRenderer {
    /** Direction toward the sun, Y-up. High enough to sit in the blue, still inside the default view. */
    static final Vector3 SUN_TO = new Vector3(-0.37f, 0.22f, 0.90f).nor();

    private final ShaderProgram shader;
    private final ShaderProgram lampShader;
    private final ShaderProgram skyShader;
    private final Mesh lampMesh;
    private final Mesh skyMesh;
    private final Matrix4 mvp = new Matrix4();
    private final Matrix4 lampXform = new Matrix4();
    private final Vector3 lampPos = new Vector3();
    private float lampRadius;

    public ForwardRenderer() {
        shader = new ShaderProgram(VERT, FRAG);
        if (!shader.isCompiled()) {
            throw new IllegalStateException(shader.getLog());
        }
        lampShader = new ShaderProgram(LAMP_VERT, LAMP_FRAG);
        if (!lampShader.isCompiled()) {
            throw new IllegalStateException(lampShader.getLog());
        }
        skyShader = new ShaderProgram(SKY_VERT, SKY_FRAG);
        if (!skyShader.isCompiled()) {
            throw new IllegalStateException(skyShader.getLog());
        }
        lampMesh = lampSphere();
        skyMesh = skyQuad();
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
        lampRadius = 0f;
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
        shader.setUniformf("u_cam_pos", camera.position.x, camera.position.y, camera.position.z);
        shader.setUniformf("u_sun_to", SUN_TO.x, SUN_TO.y, SUN_TO.z);
        shader.setUniformf("u_lamp_pos", lampPos.x, lampPos.y, lampPos.z);
        shader.setUniformf("u_lamp_radius", lampRadius);
        for (Mesh part : parts) {
            part.render(shader, GL20.GL_TRIANGLES);
        }
    }

    /**
     * Green toward the ground, blue overhead, and a yellow sun. This is only
     * the backdrop. It is not a floor you can stand on.
     */
    public void backdrop(PerspectiveCamera camera) {
        skyShader.bind();
        skyShader.setUniformMatrix("u_inv_projview", camera.invProjectionView);
        skyShader.setUniformf("u_cam_pos", camera.position.x, camera.position.y, camera.position.z);
        skyShader.setUniformf("u_sun_to", SUN_TO.x, SUN_TO.y, SUN_TO.z);
        Gdx.gl.glDisable(GL20.GL_CULL_FACE);
        skyMesh.render(skyShader, GL20.GL_TRIANGLES);
        Gdx.gl.glEnable(GL20.GL_CULL_FACE);
        shader.bind();
    }

    /** Put the yellow lamp above the starting spot. Its light falls off with distance. */
    public void placeLamp(float span) {
        float size = Math.max(span, 1f);
        lampPos.set(0f, size * 0.62f, 0f);
        lampRadius = size * 0.9f;
    }

    /** The lamp mesh. Call {@link #placeLamp} first so the ball sits on the light. */
    public void drawLamp(PerspectiveCamera camera, float span) {
        float radius = Math.max(span, 1f) * 0.07f;
        lampXform.setToTranslation(lampPos);
        lampXform.scale(radius, radius, radius);
        mvp.set(camera.combined).mul(lampXform);
        lampShader.bind();
        lampShader.setUniformMatrix("u_mvp", mvp);
        Gdx.gl.glDisable(GL20.GL_CULL_FACE);
        lampMesh.render(lampShader, GL20.GL_TRIANGLES);
        Gdx.gl.glEnable(GL20.GL_CULL_FACE);
        shader.bind();
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
        lampShader.dispose();
        skyShader.dispose();
        lampMesh.dispose();
        skyMesh.dispose();
    }

    /** A coarse sphere of radius 1, used only as the lamp. */
    private static Mesh lampSphere() {
        int stacks = 8;
        int slices = 12;
        int verts = (stacks + 1) * (slices + 1);
        float[] positions = new float[verts * 3];
        short[] indices = new short[stacks * slices * 6];
        int p = 0;
        for (int i = 0; i <= stacks; i++) {
            float phi = (float) (Math.PI * i / stacks);
            for (int j = 0; j <= slices; j++) {
                float theta = (float) (Math.PI * 2.0 * j / slices);
                positions[p++] = (float) (Math.sin(phi) * Math.cos(theta));
                positions[p++] = (float) Math.cos(phi);
                positions[p++] = (float) (Math.sin(phi) * Math.sin(theta));
            }
        }
        int t = 0;
        for (int i = 0; i < stacks; i++) {
            for (int j = 0; j < slices; j++) {
                int row = i * (slices + 1);
                int next = row + slices + 1;
                indices[t++] = (short) (row + j);
                indices[t++] = (short) (next + j);
                indices[t++] = (short) (row + j + 1);
                indices[t++] = (short) (row + j + 1);
                indices[t++] = (short) (next + j);
                indices[t++] = (short) (next + j + 1);
            }
        }
        Mesh mesh = new Mesh(true, verts, indices.length,
                new VertexAttribute(VertexAttributes.Usage.Position, 3, "a_position"));
        mesh.setVertices(positions);
        mesh.setIndices(indices);
        return mesh;
    }

    /** A quad covering the viewport. The sky shader places it on the far plane. */
    private static Mesh skyQuad() {
        float[] clip = {
                -1f, -1f,
                1f, -1f,
                1f, 1f,
                -1f, -1f,
                1f, 1f,
                -1f, 1f
        };
        Mesh mesh = new Mesh(true, 6, 0,
                new VertexAttribute(VertexAttributes.Usage.Position, 2, "a_clip"));
        mesh.setVertices(clip);
        return mesh;
    }

    // Sun, tone curve, and sRGB conversion ported from Veloren
    // (https://gitlab.com/veloren/veloren), assets/voxygen/shaders/include/sky.glsl,
    // include/srgb.glsl, and postprocess-frag.glsl, GPL-3.0-or-later.
    // The sun stays fixed in the sky. The yellow ball is a separate nearby lamp.
    // Water, clouds, and shadows are left out.
    private static final String VERT = """
        #version 330
        in vec3 a_position;
        in vec3 a_normal;
        in vec4 a_color;
        in float a_ao;
        uniform mat4 u_mvp;
        uniform mat4 u_model;
        out vec3 v_normal;
        out vec3 v_wpos;
        out vec4 v_color;
        out float v_ao;
        void main() {
            vec4 world = u_model * vec4(a_position, 1.0);
            v_wpos = world.xyz;
            v_normal = mat3(u_model) * a_normal;
            v_color = a_color;
            v_ao = a_ao;
            // u_mvp already includes the spin. Multiplying world again would turn
            // the mesh twice and leave the sun looking stuck to it.
            gl_Position = u_mvp * vec4(a_position, 1.0);
        }
        """;

    private static final String FRAG = """
        #version 330
        in vec3 v_normal;
        in vec3 v_wpos;
        in vec4 v_color;
        in float v_ao;
        uniform vec3 u_cam_pos;
        uniform vec3 u_sun_to;
        uniform vec3 u_lamp_pos;
        uniform float u_lamp_radius;
        out vec4 frag;

        vec3 srgbToLinear(vec3 srgb) {
            bvec3 cutoff = lessThan(srgb, vec3(0.04045));
            vec3 higher = pow((srgb + vec3(0.055)) / vec3(1.055), vec3(2.4));
            vec3 lower = srgb / vec3(12.92);
            return mix(higher, lower, cutoff);
        }

        vec3 linearToSrgb(vec3 col) {
            vec3 s1 = vec3(sqrt(col.r), sqrt(col.g), sqrt(col.b));
            vec3 s2 = vec3(sqrt(s1.r), sqrt(s1.g), sqrt(s1.b));
            vec3 s3 = vec3(sqrt(s2.r), sqrt(s2.g), sqrt(s2.b));
            return vec3(
                mix(11.500726 * col.r, (0.585122381 * s1.r + 0.783140355 * s2.r - 0.368262736 * s3.r), clamp((col.r - 0.0060) * 10000.0, 0.0, 1.0)),
                mix(11.500726 * col.g, (0.585122381 * s1.g + 0.783140355 * s2.g - 0.368262736 * s3.g), clamp((col.g - 0.0060) * 10000.0, 0.0, 1.0)),
                mix(11.500726 * col.b, (0.585122381 * s1.b + 0.783140355 * s2.b - 0.368262736 * s3.b), clamp((col.b - 0.0060) * 10000.0, 0.0, 1.0))
            );
        }

        vec3 blinn(vec3 n, vec3 viewDir, vec3 lightDir) {
            const float ks = 0.04;
            float ndotL = max(dot(n, -lightDir), 0.0);
            if (ndotL <= 0.0) {
                return vec3(0.0);
            }
            vec3 halfDir = normalize(-lightDir + viewDir);
            float ndotH = max(dot(n, halfDir), 0.0);
            return vec3((1.0 - ks) / 3.14159265 * ndotL + ks * pow(ndotH, 1.41421356));
        }

        void main() {
            vec3 nY = normalize(v_normal);
            vec3 viewY = normalize(u_cam_pos - v_wpos);
            // Mesh space is Y-up. The sun math is Z-up: x stays, up is z, forward is y.
            vec3 n = vec3(nY.x, -nY.z, nY.y);
            vec3 viewDir = vec3(viewY.x, -viewY.z, viewY.y);

            // Direction of the sun drawn in the blue part of the backdrop.
            vec3 sunToY = normalize(u_sun_to);
            vec3 sunTo = vec3(sunToY.x, -sunToY.z, sunToY.y);
            vec3 sunDir = -sunTo;
            float sunHeight = clamp(-sunDir.z, 0.0, 1.0);
            vec3 dayLight = mix(vec3(5.0, 2.0, 1.15), vec3(3.8, 3.0, 1.8), sunHeight);
            const vec3 scatter = vec3(0.05, 0.10, 0.23);
            vec3 sunChroma = dayLight * 5.0 * max(-sunDir.z + 0.5, 0.0);
            float sinBeta = dot(vec3(0.0, 1.0, 0.0), n);
            float skyFace = sqrt(max(0.0, 1.0 - sinBeta * sinBeta));
            float ground = max(0.2, 0.96) * (1.0 - skyFace) * 0.5;
            float sky = (1.0 + skyFace) * 0.5;
            float top = pow(dot(n, vec3(0.0, 0.0, 1.0)) + 1.0, 2.0) * 0.25;
            vec3 fromSky = vec3(skyFace + ground);
            vec3 fromSun = vec3(sky + ground);
            vec3 emitted = fromSky * (sunChroma * scatter) * top;
            vec3 reflected = fromSun * ((vec3(1.0) - scatter) * sunChroma * blinn(n, viewDir, sunDir));

            // The yellow ball is a lamp. Bright up close, dim a body-length away.
            if (u_lamp_radius > 0.0) {
                vec3 toLamp = u_lamp_pos - v_wpos;
                float dist2 = dot(toLamp, toLamp);
                float reach = u_lamp_radius * u_lamp_radius;
                float atten = 1.0 / (1.0 + dist2 / reach);
                vec3 lampDir = toLamp * inversesqrt(max(dist2, 0.0001));
                float lampLit = max(dot(nY, lampDir), 0.0);
                reflected += vec3(7.0, 4.6, 1.3) * atten * lampLit;
            }

            float ao = v_ao * sqrt(max(v_ao, 0.0));
            emitted *= ao;
            reflected *= ao;

            vec3 col = srgbToLinear(v_color.rgb);
            // Palette slots marked glowing add light after the corner shadow.
            if (v_color.a > 0.5) {
                emitted += 20.0 * col;
            }
            vec3 hdr = col * emitted + col * reflected;
            vec3 mapped = vec3(1.0) - exp(-hdr * 2.0);
            mapped = pow(max(mapped, vec3(0.0)), vec3(1.3));
            frag = vec4(linearToSrgb(mapped), 1.0);
        }
        """;

    private static final String LAMP_VERT = """
        #version 330
        in vec3 a_position;
        uniform mat4 u_mvp;
        void main() {
            gl_Position = u_mvp * vec4(a_position, 1.0);
        }
        """;

    private static final String LAMP_FRAG = """
        #version 330
        out vec4 frag;
        void main() {
            frag = vec4(1.0, 0.86, 0.25, 1.0);
        }
        """;

    private static final String SKY_VERT = """
        #version 330
        in vec2 a_clip;
        out vec2 v_clip;
        void main() {
            v_clip = a_clip;
            gl_Position = vec4(a_clip, 0.999, 1.0);
        }
        """;

    private static final String SKY_FRAG = """
        #version 330
        in vec2 v_clip;
        uniform vec3 u_cam_pos;
        uniform mat4 u_inv_projview;
        uniform vec3 u_sun_to;
        out vec4 frag;
        void main() {
            vec4 world = u_inv_projview * vec4(v_clip, 1.0, 1.0);
            vec3 dir = normalize(world.xyz / world.w - u_cam_pos);
            float lift = smoothstep(-0.25, 0.35, dir.y);
            vec3 col = mix(vec3(0.27, 0.48, 0.24), vec3(0.36, 0.56, 0.86), lift);
            float facing = max(dot(dir, normalize(u_sun_to)), 0.0);
            float glow = pow(facing, 90.0);
            float disc = smoothstep(0.9978, 0.9993, facing);
            col = mix(col, vec3(1.0, 0.90, 0.45), glow * 0.75);
            col = mix(col, vec3(1.0, 0.92, 0.35), disc);
            frag = vec4(col, 1.0);
        }
        """;
}
