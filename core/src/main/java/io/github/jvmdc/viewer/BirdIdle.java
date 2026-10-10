/*
 * Ported from Veloren (https://gitlab.com/veloren/veloren), GPL-3.0-or-later.
 * Source: voxygen/anim/src/bird_medium/idle.rs and bird_medium/mod.rs.
 * Copyright (C) Veloren contributors. Modified for jvm-dungeon-crawler.
 */
package io.github.jvmdc.viewer;

import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import io.github.jvmdc.converter.VoxMesher;
import io.github.jvmdc.converter.VoxReader;
import io.github.jvmdc.render.ForwardRenderer;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * A medium bird's idle: the chest bobs, the wings twitch, and the head looks
 * around. The parts stay in voxel space and the bone poses move them. Other
 * animals have their own idle. People stay in the still pose.
 */
public final class BirdIdle {
    static final int HEAD = 0;
    static final int CHEST = 1;
    static final int TAIL = 2;
    static final int WING_IN_L = 3;
    static final int WING_IN_R = 4;
    static final int WING_OUT_L = 5;
    static final int WING_OUT_R = 6;
    static final int LEG_L = 7;
    static final int LEG_R = 8;
    private static final int COUNT = 9;

    /** Z-up bone space to the viewer's Y-up. Applied once, outside the bones. */
    private static final Matrix4 ROOT_FIX = new Matrix4().setToRotation(Vector3.X, -90f);

    private static final Map<String, Integer> BONES = Map.of(
            "head", HEAD,
            "chest", CHEST,
            "tail", TAIL,
            "wing_in_l", WING_IN_L,
            "wing_in_r", WING_IN_R,
            "wing_out_l", WING_OUT_L,
            "wing_out_r", WING_OUT_R,
            "leg_l", LEG_L,
            "leg_r", LEG_R);

    /** chestY, chestZ, headY, headZ, tailY, tailZ, wingIn xyz, wingOut xyz, leg xyz. Scaler is separate. */
    private static final Map<String, float[]> ATTR = new HashMap<>();

    static {
        put("snowyowl", 0, 4.5f, 3.5f, 5, -6, -2, 2.5f, 1, 1.5f, 4.5f, 3.5f, 1, 1.5f, -2.5f, 4.8f, 0.75f);
        put("hornedowl", 0, 4.5f, 3.5f, 5, -6, -2, 2.5f, 1, 1.5f, 4.5f, 3.5f, 1, 1.5f, -2.5f, 4.8f, 0.75f);
        put("duck", 0, 4, 2, 5.5f, -5, 1, 2.5f, 0.5f, 1, 3, 2, 0.5f, 1.5f, 0, 3, 0.75f);
        put("cockatiel", 0, 4, 3, 5.5f, -3, -0.5f, 1.5f, 0.5f, 1, 3, 2, 0.5f, 1, -1, 3, 0.75f);
        put("chicken", 0, 5.5f, 3, 4.5f, -7.5f, 3.5f, 2, 1, 1, 3, 2, 0.5f, 1, 0, 4.4f, 0.75f);
        put("chicken/female", 0, 5.5f, 3, 6, -4.5f, 3, 2, 1.5f, 1, 3, 2, 0.5f, 1, 0, 4.4f, 0.75f);
        put("bat", 0, 7, 2.5f, 5, -8, -4, 2, 2, -2, 5, 3, 0, 2.5f, -1, 6, 0.75f);
        put("penguin", 0, 7, 1.5f, 6, -3, -4, 3, 0.5f, 1, 3, 2.5f, 0.5f, 1.5f, -1.5f, 4.5f, 0.75f);
        put("goose", 0, 6.5f, 5, 4, -4, 3, 2.5f, 1, 2, 4, 3, 0.5f, 2, -2.5f, 4.4f, 0.75f);
        put("peacock", 0, 7.5f, 3, 5, -5.5f, 1, 2, 1, 1, 5, 3, 0.5f, 2, -2, 7, 0.75f);
        put("eagle", 0, 6, 4.5f, 5, -6, -2, 3, 1.5f, 1, 8, 4.5f, 0.5f, 1.5f, -4, 4.4f, 0.75f);
        put("parrot", 0, 5, 1.5f, 4.5f, -6, 0, 2, 0.5f, 0, 5, 3, 0.5f, 1.5f, -1, 2.2f, 0.75f);
        put("crow", 0, 4, 4.5f, 4, -5, -1.5f, 2, 0.5f, 1, 5, 3, 0.5f, 1.5f, -2.5f, 2.1f, 0.75f);
        put("dodo", 0, 6, 3.5f, 4.5f, -7.5f, -0.5f, 3, -1, 1, 3, 3, 0.5f, 1.5f, -3, 3, 0.75f);
        put("parakeet", 0, 3.5f, 2, 4, -5, -1, 1, 0.5f, 0, 3, 0, 0.5f, 1, -2, 1.3f, 0.75f);
        put("puffin", 0, 6, 3.5f, 5.5f, -7, -2, 2, 0, 1, 5, 3, 0.5f, 1.5f, -2.2f, 2.5f, 0.75f);
        put("toucan", 0, 5, 2.5f, 4.5f, -6, 0, 2, 0.5f, 0, 5, 3, 0.5f, 1.5f, -3, 2.3f, 0.75f);
        put("bloodmoonbat", 0, 7, 4, 5, -6, 1, 4, 3, 1.5f, 11, 6, 0, 1.5f, -3.5f, 6, 1.05f);
        put("vampirebat", 0, 7.5f, 2.5f, 5, -5, -4, 2, 2, -2, 5, 5, 0, 2.5f, -1, 6, 0.75f);
    }

    private static void put(String key, float chestY, float chestZ, float headY, float headZ,
                            float tailY, float tailZ, float wingInX, float wingInY, float wingInZ,
                            float wingOutX, float wingOutY, float wingOutZ,
                            float legX, float legY, float legZ, float scaler) {
        ATTR.put(key, new float[] {
                chestY, chestZ, headY, headZ, tailY, tailZ,
                wingInX, wingInY, wingInZ, wingOutX, wingOutY, wingOutZ,
                legX, legY, legZ, scaler
        });
    }

    static final class Part {
        final VoxMesher.VoxMesh mesh;
        final int bone;

        Part(VoxMesher.VoxMesh mesh, int bone) {
            this.mesh = mesh;
            this.bone = bone;
        }
    }

    /** One bird, ready to idle in the large view. */
    public static final class Rig implements FigureAnim {
        private static final String[] CLIPS = {
                "No anim", "Idle", "Walk", "Run", "Fly", "Swim", "Strike", "Stunned"
        };
        final Part[] parts;
        private final float[] attr;
        private final Matrix4[] local = new Matrix4[COUNT];
        private final Matrix4[] bones = new Matrix4[COUNT];
        private final Matrix4 recenter = new Matrix4();
        private final Matrix4 draw = new Matrix4();
        private final Vector3 corner = new Vector3();
        private final Quaternion qa = new Quaternion();
        private final Quaternion qb = new Quaternion();
        private final Quaternion qc = new Quaternion();
        private final Quaternion identity = new Quaternion(0f, 0f, 0f, 1f);
        private final Vector3[] curPos = new Vector3[COUNT];
        private final Vector3[] tgtPos = new Vector3[COUNT];
        private final Vector3[] curScale = new Vector3[COUNT];
        private final Vector3[] tgtScale = new Vector3[COUNT];
        private final Quaternion[] curRot = new Quaternion[COUNT];
        private final Quaternion[] tgtRot = new Quaternion[COUNT];
        private float time;
        private int clip;

        Rig(Part[] parts, float[] attr) {
            this.parts = parts;
            this.attr = attr;
            for (int i = 0; i < COUNT; i++) {
                local[i] = new Matrix4();
                bones[i] = new Matrix4();
                curPos[i] = new Vector3();
                tgtPos[i] = new Vector3();
                curScale[i] = new Vector3(1f, 1f, 1f);
                tgtScale[i] = new Vector3(1f, 1f, 1f);
                curRot[i] = new Quaternion(0f, 0f, 0f, 1f);
                tgtRot[i] = new Quaternion(0f, 0f, 0f, 1f);
            }
            clip = 1;
            pose(0f);
            snap();
            compose();
            recenter.setToTranslation(-mid(0), -mid(1), -mid(2));
            clip = 0;
        }

        public void tick(float dt) {
            float step = Math.min(Math.max(dt, 0f), 0.1f);
            time += step;
            pose(time);
            // The idle look picks a new angle and holds it. Ease toward that pose
            // so the head turns instead of popping.
            float blend = Math.min(15f * step, 1f);
            for (int i = 0; i < COUNT; i++) {
                curPos[i].lerp(tgtPos[i], blend);
                curScale[i].lerp(tgtScale[i], blend);
                curRot[i].slerp(tgtRot[i], blend);
            }
        }

        public void draw(ForwardRenderer renderer, PerspectiveCamera camera, Matrix4 view) {
            compose();
            for (Part part : parts) {
                if (part.mesh.parts.length == 0) {
                    continue;
                }
                draw.set(view).mul(ROOT_FIX).mul(recenter).mul(bones[part.bone]);
                renderer.draw(camera, part.mesh.parts, draw);
            }
        }

        public void dispose() {
            for (Part part : parts) {
                part.mesh.dispose();
            }
        }

        @Override
        public int clipCount() {
            return CLIPS.length;
        }

        @Override
        public String clipName(int index) {
            return CLIPS[Math.max(0, Math.min(index, CLIPS.length - 1))];
        }

        @Override
        public void setClip(int index) {
            clip = Math.max(0, Math.min(index, CLIPS.length - 1));
            time = 0f;
        }

        @Override
        public int clip() {
            return clip;
        }

        private void pose(float t) {
            switch (CLIPS[clip]) {
                case "No anim" -> {
                    return;
                }
                case "Walk" -> {
                    run(t * 0.5f);
                    return;
                }
                case "Run" -> {
                    run(t);
                    return;
                }
                case "Fly" -> {
                    fly(t);
                    return;
                }
                case "Swim" -> {
                    swim(t);
                    return;
                }
                case "Strike" -> {
                    strike(t);
                    return;
                }
                case "Stunned" -> {
                    stunned(t);
                    return;
                }
                default -> {
                }
            }
            float wave = (float) Math.cos(t * 4.5);
            float phase = (float) Math.floor(t / 2.0 + t / 8.0);
            float lookX = (float) Math.sin(phase * 7331.0) * 0.5f;
            float lookY = (float) Math.sin(phase * 1337.0) * 0.25f;
            float scaler = attr[15];

            set(HEAD, 0f, attr[2], attr[3],
                    qa.setFromAxisRad(0f, 0f, 1f, lookX)
                            .mul(qb.setFromAxisRad(1f, 0f, 0f, -Math.abs(lookY) + wave * 0.01f)),
                    0.99f);
            set(CHEST, 0f, attr[0] * scaler, (attr[1] + wave * 0.06f + 1.5f) * scaler,
                    identity, scaler * 0.99f);
            set(TAIL, 0f, attr[4], attr[5], qa.setFromAxisRad(1f, 0f, 0f, 0.6f), 1.01f);
            set(WING_IN_L, -attr[6], attr[7], attr[8], wingIn(wave, true), scaler * 0.99f);
            set(WING_IN_R, attr[6], attr[7], attr[8], wingIn(wave, false), scaler * 0.99f);
            set(WING_OUT_L, -attr[9] / 2.5f, attr[10], attr[11] + 1f, wingOut(true), 1f);
            set(WING_OUT_R, attr[9] / 2.5f, attr[10], attr[11] + 1f, wingOut(false), 1f);
            set(LEG_L, -attr[12], attr[13], attr[14], identity, scaler * 0.99f);
            set(LEG_R, attr[12], attr[13], attr[14], identity, scaler * 0.99f);
        }

        private void run(float t) {
            float speednorm = (float) Math.pow(22.0 / 13.0, 0.5);
            float mixed = t * 27f;
            float lab = 0.8f * 0.8f;
            float wave = mixed * lab + (float) Math.PI * -0.15f - 0.5f;
            float s = (float) Math.sin(wave);
            float brief = (float) Math.sqrt(1.0 / (0.72 + 0.28 * s * s)) * s * speednorm;
            float briefAlt = (float) Math.sin(mixed * lab + (float) Math.PI * 3f / 8f - 0.5f) * speednorm;
            float foot1a = (float) Math.sin(mixed * 2f * lab + (float) Math.PI) * speednorm;
            float foot1b = (float) Math.sin(mixed * 2f * lab + (float) Math.PI / 2f + (float) Math.PI) * speednorm;
            float foot2a = (float) Math.sin(mixed * 2f * lab) * speednorm;
            float foot2b = (float) Math.sin(mixed * 2f * lab + (float) Math.PI / 2f) * speednorm;
            float scaler = attr[15];
            set(HEAD, 0f, attr[2], attr[3],
                    qa.setFromAxisRad(1f, 0f, 0f, -0.1f * speednorm + brief * -0.05f)
                            .mul(qb.setFromAxisRad(0f, 1f, 0f, foot1a * 0.25f))
                            .mul(qc.setFromAxisRad(0f, 0f, 1f, briefAlt * -0.05f)),
                    0.99f);
            set(CHEST, 0f, attr[0] * scaler,
                    (attr[1] + 1.5f + brief * 0.5f + Math.max(foot1b, foot2b) * 2f + 0.5f * speednorm) * scaler,
                    qa.setFromAxisRad(1f, 0f, 0f, brief * 0.07f)
                            .mul(qb.setFromAxisRad(0f, 1f, 0f, -foot1a * 0.25f))
                            .mul(qc.setFromAxisRad(0f, 0f, 1f, briefAlt * 0.10f)),
                    scaler * 0.99f);
            set(TAIL, 0f, attr[4], attr[5], qa.setFromAxisRad(1f, 0f, 0f, 0.6f + brief * -0.02f), 1.01f);
            set(WING_IN_L, -attr[6], attr[7], attr[8],
                    qa.setFromAxisRad(1f, 0f, 0f, -(float) Math.PI / 1.5f)
                            .mul(qb.setFromAxisRad(0f, 1f, 0f, -(float) Math.PI / 2.5f))
                            .mul(qc.setFromAxisRad(0f, 0f, 1f, -(float) Math.PI / 3f)),
                    scaler * 0.99f);
            set(WING_IN_R, attr[6], attr[7], attr[8],
                    qa.setFromAxisRad(1f, 0f, 0f, -(float) Math.PI / 1.5f)
                            .mul(qb.setFromAxisRad(0f, 1f, 0f, (float) Math.PI / 2.5f))
                            .mul(qc.setFromAxisRad(0f, 0f, 1f, (float) Math.PI / 3f)),
                    scaler * 0.99f);
            set(WING_OUT_L, -attr[9], attr[10], attr[11],
                    qa.setFromAxisRad(0f, 1f, 0f, -0.2f + brief * 0.05f)
                            .mul(qb.setFromAxisRad(0f, 0f, 1f, 0.2f)), 1f);
            set(WING_OUT_R, attr[9], attr[10], attr[11],
                    qa.setFromAxisRad(0f, 1f, 0f, 0.2f + brief * -0.05f)
                            .mul(qb.setFromAxisRad(0f, 0f, 1f, -0.2f)), 1f);
            set(LEG_L, -attr[12], attr[13] + foot1b * -1.8f, attr[14] + (foot1b + 1f) * 0.75f,
                    qa.setFromAxisRad(1f, 0f, 0f, -0.2f * speednorm + foot1a * 0.75f)
                            .mul(qb.setFromAxisRad(0f, 1f, 0f, Math.max(foot1b, 0f) * 0.6f)),
                    scaler * 0.99f);
            set(LEG_R, attr[12], attr[13] + foot2b * -1.8f, attr[14] + (foot2b + 1f) * 0.75f,
                    qa.setFromAxisRad(1f, 0f, 0f, -0.2f * speednorm + foot2a * 0.75f)
                            .mul(qb.setFromAxisRad(0f, 1f, 0f, -Math.max(foot2b, 0f) * 0.6f)),
                    scaler * 0.99f);
        }

        private void fly(float t) {
            float fast = (float) Math.sin(t * 4.0);
            float flap1 = flap(t, 0f);
            float flap2 = flap(t, -1.7f);
            float flap3 = flap(t, -2.0f);
            float flap4 = flap(t, -2.4f);
            float scaler = attr[15];
            set(HEAD, 0f, attr[2], attr[3],
                    qa.setFromAxisRad(1f, 0f, 0f, -0.5f + fast * 0.05f), 0.99f);
            set(CHEST, 0f, attr[0], attr[1] - flap4 * 1.5f,
                    qa.setFromAxisRad(1f, 0f, 0f, 0.2f - flap1 * 0.2f), scaler * 0.99f);
            set(TAIL, 0f, attr[4], attr[5],
                    qa.setFromAxisRad(1f, 0f, 0f, -flap2 * 0.2f + 0.1f), 1.01f);
            set(WING_IN_L, -attr[6], attr[7], attr[8],
                    qa.setFromAxisRad(0f, 1f, 0f, -flap1 * 1.9f + 0.2f)
                            .mul(qb.setFromAxisRad(1f, 0f, 0f, 0.4f)), 1f);
            set(WING_IN_R, attr[6], attr[7], attr[8],
                    qa.setFromAxisRad(0f, 1f, 0f, flap1 * 1.9f - 0.2f)
                            .mul(qb.setFromAxisRad(1f, 0f, 0f, 0.4f)), 1f);
            set(WING_OUT_L, -attr[9], attr[10], attr[11],
                    qa.setFromAxisRad(0f, 1f, 0f, -flap3 * 1.2f - 0.3f), 1f);
            set(WING_OUT_R, attr[9], attr[10], attr[11],
                    qa.setFromAxisRad(0f, 1f, 0f, flap3 * 1.2f + 0.3f), 1f);
            set(LEG_L, -attr[12], attr[13], attr[14] - flap4 * 1.5f,
                    qa.setFromAxisRad(1f, 0f, 0f, flap1 * -0.1f), scaler * 0.99f);
            set(LEG_R, attr[12], attr[13], attr[14] - flap4 * 1.5f,
                    qa.setFromAxisRad(1f, 0f, 0f, flap1 * -0.1f), scaler * 0.99f);
        }

        private void swim(float t) {
            float phase = (float) Math.floor(t / 2.0 + t / 8.0);
            float lookX = (float) Math.sin(phase * 7331.0) * 0.5f;
            float lookY = (float) Math.sin(phase * 1337.0) * 0.25f;
            float slow = (float) Math.cos(t * 4.5);
            float fast = (float) Math.sin(t * 6.0);
            float fastCos = (float) Math.cos(t * 6.0);
            float scaler = attr[15];
            set(HEAD, 0f, attr[2], attr[3],
                    qa.setFromAxisRad(0f, 0f, 1f, lookX)
                            .mul(qb.setFromAxisRad(1f, 0f, 0f, -Math.abs(lookY) + slow * 0.01f)),
                    0.99f);
            set(CHEST, 0f, attr[0], attr[1] + slow * 0.06f + 1.5f, identity, scaler * 0.99f);
            set(TAIL, 0f, attr[4], attr[5], qa.setFromAxisRad(1f, 0f, 0f, 0.6f), 1.01f);
            set(WING_IN_L, -attr[6], attr[7], attr[8],
                    qa.setFromAxisRad(0f, 1f, 0f, -0.8f + slow * 0.06f)
                            .mul(qb.setFromAxisRad(0f, 0f, 1f, 0.2f)), 1f);
            set(WING_IN_R, attr[6], attr[7], attr[8],
                    qa.setFromAxisRad(0f, 1f, 0f, 0.8f - slow * 0.06f)
                            .mul(qb.setFromAxisRad(0f, 0f, 1f, -0.2f)), 1f);
            set(WING_OUT_L, -attr[9], attr[10], attr[11],
                    qa.setFromAxisRad(0f, 1f, 0f, -0.4f)
                            .mul(qb.setFromAxisRad(0f, 0f, 1f, 0.2f)), 1f);
            set(WING_OUT_R, attr[9], attr[10], attr[11],
                    qa.setFromAxisRad(0f, 1f, 0f, 0.4f)
                            .mul(qb.setFromAxisRad(0f, 0f, 1f, -0.2f)), 1f);
            set(LEG_L, -attr[12], attr[13], attr[14],
                    qa.setFromAxisRad(1f, 0f, 0f, -0.8f + fast * 0.5f), scaler * 0.99f);
            set(LEG_R, attr[12], attr[13], attr[14],
                    qa.setFromAxisRad(1f, 0f, 0f, -0.8f + fastCos * 0.5f), scaler * 0.99f);
        }

        private static float flap(float t, float off) {
            float freq = 8f;
            return 7f / 16f * (float) Math.sin(freq * t + off)
                    + 7f / 64f * (float) Math.sin(freq * 2f * t + off)
                    + 1f / 48f * (float) Math.sin(freq * 3f * t + off);
        }

        private void stunned(float t) {
            float cycle = 1.15f;
            float u = t - cycle * (float) Math.floor(t / cycle);
            float base;
            float twitch;
            float pull;
            if (u < 0.35f) {
                float along = u / 0.35f;
                base = (float) Math.pow(along, 0.1);
                twitch = along;
                pull = 1f;
            } else {
                float along = Math.min(1f, (u - 0.35f) / 0.8f);
                base = 1f;
                twitch = 1f;
                pull = 1f - (float) Math.pow(along, 4.0);
            }
            float shiver = (float) Math.sin(twitch * 20f) * pull;
            float wave = (float) Math.cos(t * 4.5);
            float scaler = attr[15];
            float held = base * pull;
            set(HEAD, 0f, attr[2], attr[3],
                    qa.setFromAxisRad(0f, 0f, 1f, shiver * 0.8f)
                            .mul(qb.setFromAxisRad(1f, 0f, 0f, wave * 0.01f)), 0.99f);
            set(CHEST, 0f, attr[0] * scaler, (attr[1] + wave * 0.06f) * scaler,
                    qa.setFromAxisRad(1f, 0f, 0f, base * 0.5f), scaler * 0.99f);
            set(TAIL, 0f, attr[4], attr[5], identity, 1.01f);
            set(WING_IN_L, -attr[6], attr[7], attr[8],
                    qa.setFromAxisRad(0f, 1f, 0f, wave * 0.06f + shiver * 0.8f)
                            .mul(qb.setFromAxisRad(0f, 0f, 1f, 0.2f - held)), 1f);
            set(WING_IN_R, attr[6], attr[7], attr[8],
                    qa.setFromAxisRad(0f, 1f, 0f, wave * 0.06f - shiver * 0.8f)
                            .mul(qb.setFromAxisRad(0f, 0f, 1f, -0.2f + held)), 1f);
            set(WING_OUT_L, -attr[9], attr[10], attr[11],
                    qa.setFromAxisRad(0f, 1f, 0f, -0.2f).mul(qb.setFromAxisRad(0f, 0f, 1f, 0.2f)), 1f);
            set(WING_OUT_R, attr[9], attr[10], attr[11],
                    qa.setFromAxisRad(0f, 1f, 0f, 0.2f).mul(qb.setFromAxisRad(0f, 0f, 1f, -0.2f)), 1f);
            set(LEG_L, -attr[12], attr[13], attr[14],
                    qa.setFromAxisRad(1f, 0f, 0f, held * 0.8f), scaler * 0.99f);
            set(LEG_R, attr[12], attr[13], attr[14], identity, scaler * 0.99f);
        }

        private void strike(float t) {
            float along = strikeAlong(t);
            float slow = (float) Math.cos(t * 4.5);
            float scaler = attr[15];
            set(HEAD, 0f, attr[2], attr[3],
                    qa.setFromAxisRad(1f, 0f, 0f, along * -0.4f)
                            .mul(qb.setFromAxisRad(0f, 1f, 0f, along * 0.5f)), 0.99f);
            set(CHEST, 0f, attr[0], attr[1] + slow * 0.06f + along,
                    qa.setFromAxisRad(1f, 0f, 0f, along * -0.3f), scaler * 0.99f);
            set(TAIL, 0f, attr[4], attr[5], qa.setFromAxisRad(1f, 0f, 0f, along * -0.2f), 1.01f);
            set(WING_IN_L, -attr[6], attr[7], attr[8],
                    qa.setFromAxisRad(0f, 1f, 0f, -1f + slow * 0.06f + along * 1.5f)
                            .mul(qb.setFromAxisRad(0f, 0f, 1f, 0.2f)), 1f);
            set(WING_IN_R, attr[6], attr[7], attr[8],
                    qa.setFromAxisRad(0f, 1f, 0f, 1f - slow * 0.06f - along * 1.5f)
                            .mul(qb.setFromAxisRad(0f, 0f, 1f, -0.2f)), 1f);
            set(WING_OUT_L, -attr[9], attr[10], attr[11],
                    qa.setFromAxisRad(0f, 1f, 0f, -0.2f + along * -0.3f)
                            .mul(qb.setFromAxisRad(0f, 0f, 1f, 0.2f)), 1f);
            set(WING_OUT_R, attr[9], attr[10], attr[11],
                    qa.setFromAxisRad(0f, 1f, 0f, 0.2f + along * 0.3f)
                            .mul(qb.setFromAxisRad(0f, 0f, 1f, -0.2f)), 1f);
            set(LEG_L, -attr[12], attr[13], attr[14], identity, scaler * 0.99f);
            set(LEG_R, attr[12], attr[13], attr[14], identity, scaler * 0.99f);
        }

        /** How far through the peck: wind up, then the hit, then back. */
        private static float strikeAlong(float t) {
            float cycle = 1.35f;
            float u = t - cycle * (float) Math.floor(t / cycle);
            float base1;
            float base2;
            float recover;
            if (u < 0.4f) {
                base1 = (float) Math.pow(u / 0.4f, 0.25);
                base2 = 0f;
                recover = 0f;
            } else if (u < 0.65f) {
                base1 = 1f;
                base2 = (u - 0.4f) / 0.25f;
                recover = 0f;
            } else {
                base1 = 1f;
                base2 = 1f;
                recover = (float) Math.pow((u - 0.65f) / 0.7f, 4.0);
            }
            return (base2 - base1 * 0.3f) * (1f - recover);
        }

        private void snap() {
            for (int i = 0; i < COUNT; i++) {
                curPos[i].set(tgtPos[i]);
                curScale[i].set(tgtScale[i]);
                curRot[i].set(tgtRot[i]);
            }
        }

        private void compose() {
            for (int i = 0; i < COUNT; i++) {
                local[i].set(curPos[i], curRot[i], curScale[i]);
            }
            bones[CHEST].set(local[CHEST]);
            bones[HEAD].set(bones[CHEST]).mul(local[HEAD]);
            bones[TAIL].set(bones[CHEST]).mul(local[TAIL]);
            bones[WING_IN_L].set(bones[CHEST]).mul(local[WING_IN_L]);
            bones[WING_IN_R].set(bones[CHEST]).mul(local[WING_IN_R]);
            bones[WING_OUT_L].set(bones[WING_IN_L]).mul(local[WING_OUT_L]);
            bones[WING_OUT_R].set(bones[WING_IN_R]).mul(local[WING_OUT_R]);
            bones[LEG_L].set(local[LEG_L]);
            bones[LEG_R].set(local[LEG_R]);
        }

        private Quaternion wingIn(float wave, boolean left) {
            float yaw = left ? -(float) Math.PI / 2.5f + wave * 0.06f : (float) Math.PI / 2.5f - wave * 0.06f;
            float roll = left ? -(float) Math.PI / 4f : (float) Math.PI / 4f;
            return qa.setFromAxisRad(1f, 0f, 0f, -(float) Math.PI / 2f)
                    .mul(qb.setFromAxisRad(0f, 1f, 0f, yaw))
                    .mul(qc.setFromAxisRad(0f, 0f, 1f, roll));
        }

        private Quaternion wingOut(boolean left) {
            float side = left ? 1f : -1f;
            return qa.setFromAxisRad(0f, 1f, 0f, side * 0.1f)
                    .mul(qb.setFromAxisRad(0f, 0f, 1f, side * 0.2f));
        }

        private void set(int bone, float x, float y, float z, Quaternion orientation, float size) {
            tgtPos[bone].set(x, y, z);
            tgtScale[bone].set(size, size, size);
            tgtRot[bone].set(orientation);
        }

        /** Middle of the bird at the start of the idle, so it stays in frame while it moves. */
        private float mid(int axis) {
            float min = Float.POSITIVE_INFINITY;
            float max = Float.NEGATIVE_INFINITY;
            for (Part part : parts) {
                VoxMesher.VoxMesh mesh = part.mesh;
                if (mesh.parts.length == 0 || mesh.minX > mesh.maxX) {
                    continue;
                }
                float[] xs = {mesh.minX, mesh.maxX};
                float[] ys = {mesh.minY, mesh.maxY};
                float[] zs = {mesh.minZ, mesh.maxZ};
                for (float x : xs) {
                    for (float y : ys) {
                        for (float z : zs) {
                            corner.set(x, y, z).mul(bones[part.bone]);
                            float value = axis == 0 ? corner.x : axis == 1 ? corner.y : corner.z;
                            min = Math.min(min, value);
                            max = Math.max(max, value);
                        }
                    }
                }
            }
            if (min > max) {
                return 0f;
            }
            return (min + max) * 0.5f;
        }
    }

    static Rig load(NpcFigure.Assembly assembly) throws IOException {
        float[] attr = attr(assembly.species);
        Part[] parts = new Part[assembly.parts.size()];
        int n = 0;
        for (NpcFigure.Part part : assembly.parts) {
            Integer bone = BONES.get(part.bone);
            if (bone == null) {
                continue;
            }
            VoxReader.VoxScene scene = VoxReader.readScene(part.file.toFile());
            if (scene.models.size() <= part.model || scene.models.get(part.model).grid == null) {
                continue;
            }
            VoxMesher.VoxMesh mesh = VoxMesher.buildLocal(
                    scene.models.get(part.model), part.ox, part.oy, part.oz, part.mirror);
            if (mesh.parts.length == 0) {
                continue;
            }
            parts[n++] = new Part(mesh, bone);
        }
        if (n == 0) {
            return null;
        }
        Part[] trimmed = new Part[n];
        System.arraycopy(parts, 0, trimmed, 0, n);
        return new Rig(trimmed, attr);
    }

    private static float[] attr(String speciesSex) {
        int slash = speciesSex.indexOf('/');
        String species = slash < 0 ? speciesSex : speciesSex.substring(0, slash);
        String sex = slash < 0 ? "male" : speciesSex.substring(slash + 1);
        if ("chicken".equals(species) && "female".equals(sex)) {
            return ATTR.get("chicken/female");
        }
        float[] found = ATTR.get(species);
        return found != null ? found : ATTR.get("parrot");
    }

    private BirdIdle() {
    }
}
