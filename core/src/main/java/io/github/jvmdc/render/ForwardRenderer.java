package io.github.jvmdc.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.GL30;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.GLFrameBuffer;
import com.badlogic.gdx.graphics.glutils.HdpiUtils;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;

/**
 * Draws voxel meshes. A browser tile and the large view use the same shader:
 * Veloren's sun, a nearby yellow lamp, and corner shadows. The picture is
 * stored bright, then a tone curve brings it down for the screen. The large
 * view also blurs that bright picture into a soft halo around glowing voxels.
 * The backdrop is Veloren's day sky: brighter blue overhead, darker blue
 * toward the ground, and the sun in that sky. The model matrix is how the
 * large view spins a mesh.
 */
public final class ForwardRenderer {
    /** Direction toward the sun, Y-up. High enough to sit in the blue, still inside the default view. */
    static final Vector3 SUN_TO = new Vector3(-0.37f, 0.22f, 0.90f).nor();

    private static final int BLOOM_LEVELS = 5;
    private static final float BLOOM_MIX = 0.2f;
    private static final float[] CLEAR_WINDOW = displayToHdr(0.62f, 0.64f, 0.68f);
    private static final float[] CLEAR_TILE = displayToHdr(0.40f, 0.42f, 0.46f);

    private final ShaderProgram shader;
    private final ShaderProgram lampShader;
    private final ShaderProgram skyShader;
    private final ShaderProgram downShader;
    private final ShaderProgram upShader;
    private final ShaderProgram postShader;
    private final Mesh lampMesh;
    private final Mesh skyMesh;
    private FrameBuffer hdr;
    private final FrameBuffer[] bloom = new FrameBuffer[BLOOM_LEVELS];
    private int bufW;
    private int bufH;
    private final Matrix4 mvp = new Matrix4();
    private final Matrix4 lampXform = new Matrix4();
    private static final int MAX_LIGHTS = 32;
    private final Vector3 lampPos = new Vector3();
    private float lampRadius;
    private float sunScale = 1f;
    private int lightCount;
    private final float[] lightPos = new float[MAX_LIGHTS * 3];
    private final float[] lightColor = new float[MAX_LIGHTS * 3];
    private final float[] lightRadius = new float[MAX_LIGHTS];
    private float clearR = CLEAR_WINDOW[0];
    private float clearG = CLEAR_WINDOW[1];
    private float clearB = CLEAR_WINDOW[2];
    private float faceNoise;

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
        downShader = new ShaderProgram(BLIT_VERT, DOWN_FRAG);
        if (!downShader.isCompiled()) {
            throw new IllegalStateException(downShader.getLog());
        }
        upShader = new ShaderProgram(BLIT_VERT, UP_FRAG);
        if (!upShader.isCompiled()) {
            throw new IllegalStateException(upShader.getLog());
        }
        postShader = new ShaderProgram(BLIT_VERT, POST_FRAG);
        if (!postShader.isCompiled()) {
            throw new IllegalStateException(postShader.getLog());
        }
        lampMesh = lampSphere();
        skyMesh = skyQuad();
    }

    /** Clear the offscreen picture. Call before the tiles or the large view. */
    public void begin() {
        ensureTargets();
        hdr.begin();
        Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);
        Gdx.gl.glDisable(GL20.GL_BLEND);
        HdpiUtils.glViewport(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        Gdx.gl.glClearColor(clearR, clearG, clearB, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
        opaque();
        lampRadius = 0f;
        sunScale = 1f;
        lightCount = 0;
        faceNoise = 0f;
        shader.bind();
    }

    /** Background colour for the next {@link #begin}. */
    public void setClear(float r, float g, float b) {
        clearR = r;
        clearG = g;
        clearB = b;
    }

    public void resetClear() {
        clearR = CLEAR_WINDOW[0];
        clearG = CLEAR_WINDOW[1];
        clearB = CLEAR_WINDOW[2];
    }

    /** Fine grain on block faces. Off for figures and props. */
    public void setFaceNoise(boolean on) {
        faceNoise = on ? 1f : 0f;
    }

    /** 1 keeps the outdoor sun. A small value leaves the floor dark apart from point lights. */
    public void setSunScale(float scale) {
        sunScale = scale;
    }

    /** Replaces the point lights. At most 32 are used. */
    public void setLights(float[] positions, float[] colors, float[] radii, int count) {
        lightCount = Math.min(count, MAX_LIGHTS);
        System.arraycopy(positions, 0, lightPos, 0, lightCount * 3);
        System.arraycopy(colors, 0, lightColor, 0, lightCount * 3);
        System.arraycopy(radii, 0, lightRadius, 0, lightCount);
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
        Gdx.gl.glClearColor(CLEAR_TILE[0], CLEAR_TILE[1], CLEAR_TILE[2], 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
    }

    /** Depth test on, depth write on, back faces culled. Used for the floor and for props. */
    public void opaque() {
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDepthFunc(GL20.GL_LEQUAL);
        Gdx.gl.glDepthMask(true);
        Gdx.gl.glEnable(GL20.GL_CULL_FACE);
        Gdx.gl.glCullFace(GL20.GL_BACK);
        Gdx.gl.glFrontFace(GL20.GL_CCW);
    }

    public void draw(Camera camera, Mesh[] parts, Matrix4 model) {
        if (parts == null || parts.length == 0) {
            return;
        }
        opaque();
        mvp.set(camera.combined).mul(model);
        shader.setUniformMatrix("u_mvp", mvp);
        shader.setUniformMatrix("u_model", model);
        shader.setUniformf("u_cam_pos", camera.position.x, camera.position.y, camera.position.z);
        shader.setUniformf("u_sun_to", SUN_TO.x, SUN_TO.y, SUN_TO.z);
        shader.setUniformf("u_sun_scale", sunScale);
        shader.setUniformi("u_light_count", lightCount);
        shader.setUniform3fv("u_light_pos", lightPos, 0, MAX_LIGHTS * 3);
        shader.setUniform3fv("u_light_color", lightColor, 0, MAX_LIGHTS * 3);
        shader.setUniform1fv("u_light_radius", lightRadius, 0, MAX_LIGHTS);
        shader.setUniformf("u_face_noise", faceNoise);
        for (Mesh part : parts) {
            part.render(shader, GL20.GL_TRIANGLES);
        }
    }

    /**
     * Veloren's day sky behind the model. Brighter overhead, darker toward
     * the ground, with the sun in it. This is only the backdrop.
     */
    public void backdrop(Camera camera) {
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
        lightCount = 1;
        lightPos[0] = lampPos.x;
        lightPos[1] = lampPos.y;
        lightPos[2] = lampPos.z;
        lightColor[0] = 7f;
        lightColor[1] = 4.6f;
        lightColor[2] = 1.3f;
        lightRadius[0] = lampRadius;
    }

    /** The lamp mesh. Call {@link #placeLamp} first so the ball sits on the light. */
    public void drawLamp(Camera camera, float span) {
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

    /**
     * Copy the picture to the window. The large view passes true so glowing
     * voxels pick up a soft halo. Tiles pass false and stay sharp.
     */
    public void end(boolean halo) {
        if (hdr != null) {
            hdr.end();
        }
        Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDisable(GL20.GL_CULL_FACE);
        Gdx.gl.glDisable(GL20.GL_BLEND);
        if (halo) {
            runBloom();
        }
        present(halo ? BLOOM_MIX : 0f);
        HdpiUtils.glViewport(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
    }

    public void dispose() {
        shader.dispose();
        lampShader.dispose();
        skyShader.dispose();
        downShader.dispose();
        upShader.dispose();
        postShader.dispose();
        lampMesh.dispose();
        skyMesh.dispose();
        disposeTargets();
    }

    /** HDR picture plus five smaller copies used to blur the halo. */
    private void ensureTargets() {
        int w = Math.max(1, Gdx.graphics.getBackBufferWidth());
        int h = Math.max(1, Gdx.graphics.getBackBufferHeight());
        if (hdr != null && bufW == w && bufH == h) {
            return;
        }
        disposeTargets();
        bufW = w;
        bufH = h;
        hdr = floatTarget(w, h, true);
        int bw = w;
        int bh = h;
        for (int i = 0; i < BLOOM_LEVELS; i++) {
            bloom[i] = floatTarget(bw, bh, false);
            bw = Math.max(1, bw / 2);
            bh = Math.max(1, bh / 2);
        }
    }

    private static FrameBuffer floatTarget(int width, int height, boolean depth) {
        GLFrameBuffer.FrameBufferBuilder builder = new GLFrameBuffer.FrameBufferBuilder(width, height);
        builder.addFloatAttachment(GL30.GL_RGBA16F, GL30.GL_RGBA, GL30.GL_FLOAT, false);
        if (depth) {
            builder.addDepthRenderBuffer(GL30.GL_DEPTH_COMPONENT24);
        }
        FrameBuffer buffer = builder.build();
        Texture texture = buffer.getColorBufferTexture();
        texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        texture.setWrap(Texture.TextureWrap.ClampToEdge, Texture.TextureWrap.ClampToEdge);
        return buffer;
    }

    private void disposeTargets() {
        if (hdr != null) {
            hdr.dispose();
            hdr = null;
        }
        for (int i = 0; i < bloom.length; i++) {
            if (bloom[i] != null) {
                bloom[i].dispose();
                bloom[i] = null;
            }
        }
    }

    /**
     * Blur the bright picture down to a small size and back up, adding each
     * coarser blur onto the sharper one. The full-size result is about four
     * times too strong, which the tone pass divides back out.
     */
    private void runBloom() {
        blit(hdr, bloom[1], downShader, false);
        blit(bloom[1], bloom[2], downShader, false);
        blit(bloom[2], bloom[3], downShader, false);
        blit(bloom[3], bloom[4], downShader, false);
        blit(bloom[4], bloom[3], upShader, true);
        blit(bloom[3], bloom[2], upShader, true);
        blit(bloom[2], bloom[1], upShader, true);
        blit(bloom[1], bloom[0], upShader, false);
    }

    private void blit(FrameBuffer src, FrameBuffer dst, ShaderProgram program, boolean add) {
        dst.begin();
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDisable(GL20.GL_CULL_FACE);
        Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);
        if (add) {
            Gdx.gl.glEnable(GL20.GL_BLEND);
            Gdx.gl.glBlendFunc(GL20.GL_ONE, GL20.GL_ONE);
        } else {
            Gdx.gl.glDisable(GL20.GL_BLEND);
        }
        program.bind();
        src.getColorBufferTexture().bind(0);
        program.setUniformi("u_src", 0);
        program.setUniformf("u_halfpixel", 0.5f / src.getWidth(), 0.5f / src.getHeight());
        skyMesh.render(program, GL20.GL_TRIANGLES);
        dst.end();
    }

    /** Tone curve onto the window. mix is 0.2 for the halo and 0 for tiles. */
    private void present(float mix) {
        Gdx.gl.glDisable(GL20.GL_BLEND);
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);
        HdpiUtils.glViewport(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        postShader.bind();
        hdr.getColorBufferTexture().bind(0);
        bloom[0].getColorBufferTexture().bind(1);
        postShader.setUniformi("u_color", 0);
        postShader.setUniformi("u_bloom", 1);
        postShader.setUniformf("u_mix", mix);
        skyMesh.render(postShader, GL20.GL_TRIANGLES);
        // bind(1) leaves the card on the halo picture. Text then paints solid blocks.
        Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
    }

    /** Undo the tone curve so a display color survives the final pass. */
    private static float[] displayToHdr(float r, float g, float b) {
        return new float[] {displayToHdr(r), displayToHdr(g), displayToHdr(b)};
    }

    private static float displayToHdr(float display) {
        float mapped = Math.min(srgbToLinear(display), 0.98f);
        float preGamma = (float) Math.pow(mapped, 1.0 / 1.3);
        return (float) (-0.5 * Math.log(Math.max(1.0 - preGamma, 1.0e-4)));
    }

    private static float srgbToLinear(float c) {
        if (c <= 0.04045f) {
            return c / 12.92f;
        }
        return (float) Math.pow((c + 0.055) / 1.055, 2.4);
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

    // Sun, tone curve, sRGB conversion, and the halo blur are ported from Veloren
    // (https://gitlab.com/veloren/veloren), assets/voxygen/shaders/include/sky.glsl,
    // include/srgb.glsl, postprocess-frag.glsl, dual-downsample-filtered-frag.glsl,
    // dual-downsample-frag.glsl, and dual-upsample-frag.glsl, GPL-3.0-or-later.
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
        uniform float u_sun_scale;
        const int MAX_LIGHTS = 32;
        uniform int u_light_count;
        uniform vec3 u_light_pos[MAX_LIGHTS];
        uniform vec3 u_light_color[MAX_LIGHTS];
        uniform float u_light_radius[MAX_LIGHTS];
        uniform float u_face_noise;
        out vec4 frag;

        float hash4(vec4 p) {
            p = fract(p * 0.3183099 + 0.1) - fract(p + 23.22121);
            p *= 17.0;
            return (fract(p.x * p.y * (1.0 - p.z) * p.w * (p.x + p.y + p.z + p.w)) - 0.5) * 2.0;
        }

        vec3 srgbToLinear(vec3 srgb) {
            bvec3 cutoff = lessThan(srgb, vec3(0.04045));
            vec3 higher = pow((srgb + vec3(0.055)) / vec3(1.055), vec3(2.4));
            vec3 lower = srgb / vec3(12.92);
            return mix(higher, lower, cutoff);
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
            vec3 emitted = fromSky * (sunChroma * scatter) * top * u_sun_scale;
            vec3 reflected = fromSun * ((vec3(1.0) - scatter) * sunChroma * blinn(n, viewDir, sunDir)) * u_sun_scale;
            reflected += vec3(0.03) * (1.0 - u_sun_scale);

            for (int i = 0; i < MAX_LIGHTS; i++) {
                if (i >= u_light_count || u_light_radius[i] <= 0.0) {
                    continue;
                }
                vec3 toLight = u_light_pos[i] - v_wpos;
                float dist2 = dot(toLight, toLight);
                float reach = u_light_radius[i] * u_light_radius[i];
                float atten = 1.0 / (1.0 + dist2 / reach);
                vec3 lightDir = toLight * inversesqrt(max(dist2, 0.0001));
                float lit = max(dot(nY, lightDir), 0.0);
                reflected += u_light_color[i] * atten * lit;
            }

            float ao = v_ao * sqrt(max(v_ao, 0.0));
            emitted *= ao;
            reflected *= ao;

            vec3 col = srgbToLinear(v_color.rgb);
            if (u_face_noise > 0.5) {
                vec3 blockPos = v_wpos / 11.0;
                vec3 face = normalize(v_normal);
                float noise = hash4(vec4(floor(blockPos * 3.0 - face * 0.5), 0.0));
                const float W_INV = 1.0 / 1.055;
                vec3 nd = sqrt(max(col, vec3(0.0))) * W_INV + noise * 0.015;
                col = nd * nd * (W_INV * W_INV);
            }
            // Palette slots marked glowing add light after the corner shadow.
            if (v_color.a > 0.5) {
                emitted += 20.0 * col;
            }
            vec3 hdr = col * emitted + col * reflected;
            frag = vec4(hdr, 1.0);
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

        vec3 srgbToLinear(vec3 srgb) {
            bvec3 cutoff = lessThan(srgb, vec3(0.04045));
            vec3 higher = pow((srgb + vec3(0.055)) / vec3(1.055), vec3(2.4));
            vec3 lower = srgb / vec3(12.92);
            return mix(higher, lower, cutoff);
        }

        vec3 displayToHdr(vec3 display) {
            vec3 mapped = min(srgbToLinear(display), vec3(0.98));
            vec3 preGamma = pow(mapped, vec3(1.0 / 1.3));
            return -0.5 * log(max(vec3(1.0) - preGamma, vec3(0.0001)));
        }

        void main() {
            frag = vec4(displayToHdr(vec3(1.0, 0.86, 0.25)), 1.0);
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

        // Day colors from Veloren sky.glsl. Stars, the moon, and clouds are left out.
        // The viewer's sun sits low so it stays on screen, so the gradient stays at noon
        // instead of shifting toward dusk.
        const vec3 SKY_DAY_TOP = vec3(0.1, 0.5, 0.9);
        const vec3 SKY_DAY_MID = vec3(0.18, 0.28, 0.6);
        const vec3 SKY_DAY_BOT = vec3(0.1, 0.2, 0.3);
        const vec3 SUN_HALO_DAY = vec3(0.15, 0.15, 0.001);
        const vec3 SUN_SURF_COLOR = vec3(1.5, 0.9, 0.35) * 10.0;
        const float SUN_COLOR_FACTOR = 5.0;

        void main() {
            vec4 world = u_inv_projview * vec4(v_clip, 1.0, 1.0);
            vec3 dirY = normalize(world.xyz / world.w - u_cam_pos);
            // Mesh space is Y-up. Sky math is Z-up: x stays, up is z, forward is y.
            vec3 dir = vec3(dirY.x, -dirY.z, dirY.y);
            vec3 sunToY = normalize(u_sun_to);
            vec3 sunTo = vec3(sunToY.x, -sunToY.z, sunToY.y);
            vec3 sunDir = -sunTo;

            vec3 sky = mix(mix(SKY_DAY_MID, SKY_DAY_BOT, max(-dir.z, 0.0)), SKY_DAY_TOP, max(dir.z, 0.0));
            float facing = max(dot(dir, -sunDir), 0.0);
            vec3 halo = SUN_HALO_DAY * 25.0 * pow(facing, 20.0);
            float angle = 0.00035;
            float disc = clamp((facing - (1.0 - angle)) * 4.0 / angle, 0.0, 1.0);
            vec3 surf = disc * SUN_SURF_COLOR * SUN_COLOR_FACTOR * 0.1;
            frag = vec4(sky + halo + surf, 1.0);
        }
        """;

    private static final String BLIT_VERT = """
        #version 330
        in vec2 a_clip;
        out vec2 v_uv;
        void main() {
            v_uv = a_clip * 0.5 + 0.5;
            gl_Position = vec4(a_clip, 0.0, 1.0);
        }
        """;

    private static final String DOWN_FRAG = """
        #version 330
        in vec2 v_uv;
        uniform sampler2D u_src;
        uniform vec2 u_halfpixel;
        out vec4 frag;

        vec4 sampleSrc(vec2 uv) {
            vec4 color = texture(u_src, uv);
            color = mix(color, vec4(0.0), isnan(color));
            color = mix(color, vec4(100.0), isinf(color));
            return color;
        }

        void main() {
            vec2 hp = u_halfpixel;
            vec4 sum = sampleSrc(v_uv) * 4.0;
            sum += sampleSrc(v_uv - hp);
            sum += sampleSrc(v_uv + hp);
            sum += sampleSrc(v_uv + vec2(hp.x, -hp.y));
            sum += sampleSrc(v_uv - vec2(hp.x, -hp.y));
            frag = sum / 8.0;
        }
        """;

    private static final String UP_FRAG = """
        #version 330
        in vec2 v_uv;
        uniform sampler2D u_src;
        uniform vec2 u_halfpixel;
        out vec4 frag;

        vec4 sampleSrc(vec2 uv) {
            return texture(u_src, uv);
        }

        void main() {
            vec2 hp = u_halfpixel;
            vec4 sum = sampleSrc(v_uv + vec2(-hp.x * 2.0, 0.0));
            sum += sampleSrc(v_uv + vec2(-hp.x, hp.y)) * 2.0;
            sum += sampleSrc(v_uv + vec2(0.0, hp.y * 2.0));
            sum += sampleSrc(v_uv + vec2(hp.x, hp.y)) * 2.0;
            sum += sampleSrc(v_uv + vec2(hp.x * 2.0, 0.0));
            sum += sampleSrc(v_uv + vec2(hp.x, -hp.y)) * 2.0;
            sum += sampleSrc(v_uv + vec2(0.0, -hp.y * 2.0));
            sum += sampleSrc(v_uv + vec2(-hp.x, -hp.y)) * 2.0;
            frag = sum / 12.0;
        }
        """;

    private static final String POST_FRAG = """
        #version 330
        in vec2 v_uv;
        uniform sampler2D u_color;
        uniform sampler2D u_bloom;
        uniform float u_mix;
        out vec4 frag;

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

        void main() {
            vec3 color = texture(u_color, v_uv).rgb;
            vec3 hdr = color;
            if (u_mix > 0.0) {
                vec3 bloom = texture(u_bloom, v_uv).rgb / 4.0;
                hdr = mix(color, bloom, u_mix);
            }
            vec3 mapped = vec3(1.0) - exp(-hdr * 2.0);
            mapped = pow(max(mapped, vec3(0.0)), vec3(1.3));
            frag = vec4(linearToSrgb(mapped), 1.0);
        }
        """;
}
