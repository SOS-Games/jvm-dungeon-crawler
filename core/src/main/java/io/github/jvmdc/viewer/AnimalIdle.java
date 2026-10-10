/*
 * Ported from Veloren (https://gitlab.com/veloren/veloren), GPL-3.0-or-later.
 * Source: voxygen/anim/src idle.rs and mod.rs for quadruped_small, quadruped_medium,
 * quadruped_low, theropod, bird_large, fish_small, fish_medium, dragon, arthropod,
 * crustacean, biped_large, golem, and biped_small.
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
 * Idle for animals, trolls, golems, and people. The chest, head, and limbs ease
 * between the same poses Veloren uses. Medium birds stay on their own idle.
 */
public final class AnimalIdle {
    /** Z-up bone space to the viewer's Y-up. Applied once, outside the bones. */
    private static final Matrix4 ROOT_FIX = new Matrix4().setToRotation(Vector3.X, -90f);
    private static final float PI = (float) Math.PI;
    private static final Map<String, Spec> SPECS = new HashMap<>();

    static {
        spec("quadruped_small",
                new String[] {"chest", "head", "tail", "leg_fl", "leg_fr", "leg_bl", "leg_br"},
                new int[] {-1, 0, 0, 0, 0, 0, 0});
        alias("quadruped_small", "left_front", "leg_fl");
        alias("quadruped_small", "right_front", "leg_fr");
        alias("quadruped_small", "left_back", "leg_bl");
        alias("quadruped_small", "right_back", "leg_br");

        spec("quadruped_medium",
                new String[] {
                        "torso_front", "torso_back", "neck", "head", "jaw", "tail", "ears",
                        "leg_fl", "leg_fr", "leg_bl", "leg_br",
                        "foot_fl", "foot_fr", "foot_bl", "foot_br"
                },
                new int[] {-1, 0, 0, 2, 3, 1, 3, 0, 0, 1, 1, 7, 8, 9, 10});

        spec("quadruped_low",
                new String[] {
                        "chest", "tail_front", "tail_rear",
                        "head_c_lower", "head_c_upper", "jaw_c",
                        "head_l_lower", "head_l_upper", "jaw_l",
                        "head_r_lower", "head_r_upper", "jaw_r",
                        "foot_fl", "foot_fr", "foot_bl", "foot_br"
                },
                new int[] {-1, 0, 1, 0, 3, 4, 0, 6, 7, 0, 9, 10, 0, 0, 0, 0});
        alias("quadruped_low", "lower", "head_c_lower");
        alias("quadruped_low", "upper", "head_c_upper");
        alias("quadruped_low", "jaw", "jaw_c");
        alias("quadruped_low", "left_lower", "head_l_lower");
        alias("quadruped_low", "left_upper", "head_l_upper");
        alias("quadruped_low", "left_jaw", "jaw_l");
        alias("quadruped_low", "right_lower", "head_r_lower");
        alias("quadruped_low", "right_upper", "head_r_upper");
        alias("quadruped_low", "right_jaw", "jaw_r");
        alias("quadruped_low", "front_left", "foot_fl");
        alias("quadruped_low", "front_right", "foot_fr");
        alias("quadruped_low", "back_left", "foot_bl");
        alias("quadruped_low", "back_right", "foot_br");

        spec("theropod",
                new String[] {
                        "chest_front", "neck", "head", "jaw", "chest_back",
                        "tail_front", "tail_back", "hand_l", "hand_r",
                        "leg_l", "leg_r", "foot_l", "foot_r"
                },
                new int[] {-1, 0, 1, 2, 0, 4, 5, 0, 0, 4, 4, 9, 10});

        spec("bird_large",
                new String[] {
                        "chest", "neck", "head", "beak", "tail_front", "tail_rear",
                        "wing_in_l", "wing_in_r", "wing_mid_l", "wing_mid_r",
                        "wing_out_l", "wing_out_r", "leg_l", "leg_r", "foot_l", "foot_r"
                },
                new int[] {-1, 0, 1, 2, 0, 4, 0, 0, 6, 7, 8, 9, -1, -1, 12, 13});

        spec("fish_small",
                new String[] {"chest", "tail", "fin_l", "fin_r"},
                new int[] {-1, 0, 0, 0});

        spec("fish_medium",
                new String[] {"chest_front", "chest_back", "head", "jaw", "tail", "fin_l", "fin_r"},
                new int[] {-1, 0, 0, 2, 1, 0, 0});

        spec("dragon",
                new String[] {
                        "chest_front", "chest_rear", "head_lower", "head_upper", "jaw",
                        "wing_in_l", "wing_in_r", "wing_out_l", "wing_out_r",
                        "tail_front", "tail_rear", "foot_fl", "foot_fr", "foot_bl", "foot_br"
                },
                new int[] {-1, 0, 0, 2, 3, 0, 0, 5, 6, 1, 9, 0, 0, 1, 1});
        alias("dragon", "lower", "head_lower");
        alias("dragon", "upper", "head_upper");

        spec("arthropod",
                new String[] {
                        "chest", "head", "mandible_l", "mandible_r",
                        "wing_fl", "wing_fr", "wing_bl", "wing_br",
                        "leg_fl", "leg_fr", "leg_fcl", "leg_fcr",
                        "leg_bcl", "leg_bcr", "leg_bl", "leg_br"
                },
                new int[] {-1, 0, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0});

        spec("crustacean",
                new String[] {
                        "chest", "arm_l", "pincer_l0", "pincer_l1", "arm_r", "pincer_r0", "pincer_r1",
                        "leg_fl", "leg_fr", "leg_cl", "leg_cr", "leg_bl", "leg_br"
                },
                new int[] {-1, 0, 1, 2, 0, 4, 5, 0, 0, 0, 0, 0, 0});

        spec("biped_large",
                new String[] {
                        "torso_upper", "torso_lower", "head", "jaw", "tail",
                        "shoulder_l", "shoulder_r", "hand_l", "hand_r",
                        "leg_l", "leg_r", "foot_l", "foot_r"
                },
                new int[] {-1, 0, 0, 2, 1, 0, 0, 0, 0, 1, 1, 1, 1});

        spec("golem",
                new String[] {
                        "torso_upper", "torso_lower", "head", "jaw",
                        "shoulder_l", "shoulder_r", "hand_l", "hand_r",
                        "leg_l", "leg_r", "foot_l", "foot_r"
                },
                new int[] {-1, 0, 0, 2, 0, 0, 4, 5, 1, 1, 8, 9});

        spec("biped_small",
                new String[] {
                        "chest", "head", "pants", "tail",
                        "hand_l", "hand_r", "foot_l", "foot_r", "main", "second"
                },
                new int[] {-1, 0, 0, 2, 0, 0, -1, -1, 0, 0});
    }

    private static void spec(String family, String[] names, int[] parent) {
        SPECS.put(family, new Spec(family, names, parent));
    }

    private static void alias(String family, String manifestName, String bone) {
        Spec spec = SPECS.get(family);
        spec.bones.put(manifestName, spec.bones.get(bone));
    }

    static final class Part {
        final VoxMesher.VoxMesh mesh;
        final int bone;

        Part(VoxMesher.VoxMesh mesh, int bone) {
            this.mesh = mesh;
            this.bone = bone;
        }
    }

    /** One animal, ready to idle in the large view. */
    public static final class Rig implements FigureAnim {
        final Part[] parts;
        private final Spec spec;
        private final float[] attr;
        private final String species;
        private final boolean trunk;
        private final Matrix4[] local;
        private final Matrix4[] bones;
        private final Matrix4 recenter = new Matrix4();
        private final Matrix4 draw = new Matrix4();
        private final Vector3 corner = new Vector3();
        private final Quaternion qa = new Quaternion();
        private final Quaternion qb = new Quaternion();
        private final Quaternion qc = new Quaternion();
        private final Quaternion identity = new Quaternion(0f, 0f, 0f, 1f);
        private final Vector3[] curPos;
        private final Vector3[] tgtPos;
        private final Vector3[] curScale;
        private final Vector3[] tgtScale;
        private final Quaternion[] curRot;
        private final Quaternion[] tgtRot;
        private float time;
        private int clip;
        private final Vector3 scratch = new Vector3();
        private final Quaternion qd = new Quaternion();

        Rig(Part[] parts, Spec spec, float[] attr, String species) {
            this.parts = parts;
            this.spec = spec;
            this.attr = attr;
            this.species = species == null ? "" : species;
            this.trunk = this.species.startsWith("elephant");
            int count = spec.names.length;
            local = new Matrix4[count];
            bones = new Matrix4[count];
            curPos = new Vector3[count];
            tgtPos = new Vector3[count];
            curScale = new Vector3[count];
            tgtScale = new Vector3[count];
            curRot = new Quaternion[count];
            tgtRot = new Quaternion[count];
            for (int i = 0; i < count; i++) {
                local[i] = new Matrix4();
                bones[i] = new Matrix4();
                curPos[i] = new Vector3();
                tgtPos[i] = new Vector3();
                curScale[i] = new Vector3(1f, 1f, 1f);
                tgtScale[i] = new Vector3(1f, 1f, 1f);
                curRot[i] = new Quaternion(0f, 0f, 0f, 1f);
                tgtRot[i] = new Quaternion(0f, 0f, 0f, 1f);
            }
            pose(0f);
            snap();
            compose();
            recenter.setToTranslation(-mid(0), -mid(1), -mid(2));
        }

        @Override
        public void tick(float dt) {
            float step = Math.min(Math.max(dt, 0f), 0.1f);
            time += step;
            pose(time);
            float blend = Math.min(15f * step, 1f);
            for (int i = 0; i < spec.names.length; i++) {
                curPos[i].lerp(tgtPos[i], blend);
                curScale[i].lerp(tgtScale[i], blend);
                curRot[i].slerp(tgtRot[i], blend);
            }
        }

        @Override
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

        @Override
        public void dispose() {
            for (Part part : parts) {
                part.mesh.dispose();
            }
        }

        @Override
        public int clipCount() {
            return 2;
        }

        @Override
        public String clipName(int index) {
            if (index == 1 && spec.family.startsWith("fish")) {
                return "Swim";
            }
            return index == 0 ? "Idle" : "Run";
        }

        @Override
        public void setClip(int index) {
            clip = index <= 0 ? 0 : 1;
            time = 0f;
        }

        @Override
        public int clip() {
            return clip;
        }

        private void pose(float t) {
            for (int i = 0; i < spec.names.length; i++) {
                tgtPos[i].set(0f, 0f, 0f);
                tgtScale[i].set(1f, 1f, 1f);
                tgtRot[i].set(identity);
            }
            if (clip == 1) {
                switch (spec.family) {
                    case "quadruped_small" -> runSmall(t);
                    case "quadruped_medium" -> runMedium(t);
                    case "quadruped_low" -> runLow(t);
                    case "theropod" -> runTheropod(t);
                    case "bird_large" -> runBirdLarge(t);
                    case "fish_small" -> swimFishSmall(t);
                    case "fish_medium" -> swimFishMedium(t);
                    case "dragon" -> runDragon(t);
                    case "arthropod" -> runBug(t);
                    case "crustacean" -> runCrab(t);
                    case "biped_large" -> runTroll(t);
                    case "golem" -> runGolem(t);
                    case "biped_small" -> runPerson(t);
                    default -> {
                    }
                }
                return;
            }
            switch (spec.family) {
                case "quadruped_small" -> poseSmall(t);
                case "quadruped_medium" -> poseMedium(t);
                case "quadruped_low" -> poseLow(t);
                case "theropod" -> poseTheropod(t);
                case "bird_large" -> poseBird(t);
                case "fish_small" -> poseFishSmall(t);
                case "fish_medium" -> poseFishMedium(t);
                case "dragon" -> poseDragon(t);
                case "arthropod" -> poseBug(t);
                case "crustacean" -> poseCrab(t);
                case "biped_large" -> poseTroll(t);
                case "golem" -> poseGolem(t);
                case "biped_small" -> posePerson(t);
                default -> {
                }
            }
        }

        private void poseSmall(float t) {
            float slow = sin(t * 3.5f);
            float slowAlt = sin(t * 3.5f + PI / 2f);
            float lookX = glance(t, 7331f, 0.5f);
            float lookY = glance(t, 1337f, 0.25f);
            set(b("head"), 0f, a(AnimalAttrs.QS_HEAD), a(AnimalAttrs.QS_HEAD + 1) + slow * 0.2f,
                    z(lookX).mul(x(lookY + slowAlt * 0.03f)), 1f);
            set(b("chest"), slow * 0.05f, a(AnimalAttrs.QS_CHEST), a(AnimalAttrs.QS_CHEST + 1),
                    y(slow * 0.05f), 1f);
            legPair("leg_fl", "leg_fr", AnimalAttrs.QS_FEET_F, slow, slowAlt, -0.2f, 0.2f, true);
            legPair("leg_bl", "leg_br", AnimalAttrs.QS_FEET_B, slow, slowAlt, -0.2f, 0.2f, false);
            set(b("tail"), 0f, a(AnimalAttrs.QS_TAIL), a(AnimalAttrs.QS_TAIL + 1), z(slow * 0.4f), 1f);
        }

        /** Front pair uses slow on the left. Back pair swaps that. */
        private void legPair(String left, String right, int feet, float slow, float slowAlt,
                             float leftZ, float rightZ, boolean front) {
            float lx = front ? slow * 0.03f : slowAlt * 0.03f;
            float rx = front ? slowAlt * 0.03f : slow * 0.03f;
            float yaw = slow * -0.05f;
            set(b(left), -a(feet), a(feet + 1), a(feet + 2) + slow * leftZ, x(lx).mul(y(yaw)), 1f);
            set(b(right), a(feet), a(feet + 1), a(feet + 2) + slow * rightZ, x(rx).mul(y(yaw)), 1f);
        }

        private void poseMedium(float t) {
            float slower = sin(t + PI);
            float slow = sin(t * 3.5f + PI);
            float lookX = glance(t, 7331f, 0.5f);
            float lookY = glance(t, 1337f, 0.25f);
            float tailPhase = (float) Math.floor(t / 2.0 + t / 2.0);
            float tailX = sin(tailPhase * 7331f) * 0.25f;
            set(b("neck"), 0f, a(AnimalAttrs.QM_NECK), a(AnimalAttrs.QM_NECK + 1) + slower * 0.1f,
                    identity, 1.02f);
            set(b("head"), 0f, a(AnimalAttrs.QM_HEAD), a(AnimalAttrs.QM_HEAD + 1) + slower * 0.2f,
                    z(0.3f * lookX).mul(x(0.3f * lookY)), 1f);
            set(b("jaw"), 0f, a(AnimalAttrs.QM_JAW) - slower * 0.12f,
                    a(AnimalAttrs.QM_JAW + 1) + slow * 0.2f, x(slow * 0.05f - 0.08f), 1.02f);
            set(b("tail"), 0f, a(AnimalAttrs.QM_TAIL), a(AnimalAttrs.QM_TAIL + 1),
                    z(slow * 0.2f + tailX), 1f);
            set(b("torso_front"), 0f, a(AnimalAttrs.QM_TORSO_FRONT),
                    a(AnimalAttrs.QM_TORSO_FRONT + 1) + slower * 0.3f, y(slow * 0.02f), 1f);
            set(b("torso_back"), 0f, a(AnimalAttrs.QM_TORSO_BACK),
                    a(AnimalAttrs.QM_TORSO_BACK + 1) + slower * 0.2f, y(-slow * 0.005f), 1f);
            Quaternion ears = trunk ? identity : x(slower * 0.03f);
            set(b("ears"), 0f, a(AnimalAttrs.QM_EARS), a(AnimalAttrs.QM_EARS + 1), ears, 1.02f);
            float leg = 1.02f;
            set(b("leg_fl"), -a(AnimalAttrs.QM_LEG_F), a(AnimalAttrs.QM_LEG_F + 1),
                    a(AnimalAttrs.QM_LEG_F + 2) + slow * -0.15f + slower * -0.15f,
                    y(slow * -0.02f), leg);
            set(b("leg_fr"), a(AnimalAttrs.QM_LEG_F), a(AnimalAttrs.QM_LEG_F + 1),
                    a(AnimalAttrs.QM_LEG_F + 2) + slow * 0.15f + slower * -0.15f,
                    y(slow * -0.02f), leg);
            set(b("leg_bl"), -a(AnimalAttrs.QM_LEG_B), a(AnimalAttrs.QM_LEG_B + 1),
                    a(AnimalAttrs.QM_LEG_B + 2) + slower * -0.3f, y(slow * -0.02f), leg);
            set(b("leg_br"), a(AnimalAttrs.QM_LEG_B), a(AnimalAttrs.QM_LEG_B + 1),
                    a(AnimalAttrs.QM_LEG_B + 2) + slower * -0.3f, y(slow * -0.02f), leg);
            float foot = 0.96f;
            set(b("foot_fl"), -a(AnimalAttrs.QM_FEET_F), a(AnimalAttrs.QM_FEET_F + 1),
                    a(AnimalAttrs.QM_FEET_F + 2) + slower * -0.2f, identity, foot);
            set(b("foot_fr"), a(AnimalAttrs.QM_FEET_F), a(AnimalAttrs.QM_FEET_F + 1),
                    a(AnimalAttrs.QM_FEET_F + 2) + slower * -0.2f, identity, foot);
            set(b("foot_bl"), -a(AnimalAttrs.QM_FEET_B), a(AnimalAttrs.QM_FEET_B + 1),
                    a(AnimalAttrs.QM_FEET_B + 2) + slower * -0.2f, identity, foot);
            set(b("foot_br"), a(AnimalAttrs.QM_FEET_B), a(AnimalAttrs.QM_FEET_B + 1),
                    a(AnimalAttrs.QM_FEET_B + 2) + slower * -0.2f, identity, foot);
        }

        private void poseLow(float t) {
            float slower = sin(t * 1.25f);
            float slow = sin(t * 2.5f);
            float slowAlt = sin(t * 2.5f + PI / 2f);
            float look1x = glance(t, 7331f, 0.2f);
            float look1y = glance(t, 1337f, 0.1f);
            float look2x = glance(t, 1553f, 0.2f);
            float look2y = glance(t, 7777f, 0.1f);
            float look3x = glance(t, 3551f, 0.2f);
            float look3y = glance(t, 4587f, 0.1f);
            boolean tongue = species.startsWith("snaretongue");
            set(b("head_c_upper"), 0f, a(AnimalAttrs.QL_HEAD_UPPER),
                    a(AnimalAttrs.QL_HEAD_UPPER + 1) + slower * 0.2f,
                    z(0.8f * look1x).mul(x(0.8f * look1y)), 1f);
            set(b("head_c_lower"), 0f, a(AnimalAttrs.QL_HEAD_LOWER),
                    a(AnimalAttrs.QL_HEAD_LOWER + 1) + slower * 0.2f,
                    z(0.8f * look1x).mul(x(0.8f * look1y)), 1f);
            set(b("jaw_c"), 0f, a(AnimalAttrs.QL_JAW), a(AnimalAttrs.QL_JAW + 1),
                    x(slow * 0.05f - 0.05f), 0.98f);
            set(b("head_l_upper"), -a(AnimalAttrs.QL_SIDE_HEAD_UPPER), a(AnimalAttrs.QL_SIDE_HEAD_UPPER + 1),
                    a(AnimalAttrs.QL_SIDE_HEAD_UPPER + 2) + slower * 0.2f,
                    z(0.8f * look2x).mul(x(0.8f * look2y)), 1f);
            set(b("head_l_lower"), -a(AnimalAttrs.QL_SIDE_HEAD_LOWER), a(AnimalAttrs.QL_SIDE_HEAD_LOWER + 1),
                    a(AnimalAttrs.QL_SIDE_HEAD_LOWER + 2) + slower * 0.2f,
                    z(0.8f * look2x).mul(x(0.8f * look2y))
                            .mul(y(-Math.max(look1x, 0f) + Math.min(look2x, 0f))), 1f);
            set(b("jaw_l"), 0f, a(AnimalAttrs.QL_JAW), a(AnimalAttrs.QL_JAW + 1),
                    x(slow * 0.05f - 0.05f), 0.98f);
            set(b("head_r_upper"), a(AnimalAttrs.QL_SIDE_HEAD_UPPER), a(AnimalAttrs.QL_SIDE_HEAD_UPPER + 1),
                    a(AnimalAttrs.QL_SIDE_HEAD_UPPER + 2) + slower * 0.2f,
                    z(0.8f * look3x).mul(x(0.8f * look3y)), 1f);
            set(b("head_r_lower"), a(AnimalAttrs.QL_SIDE_HEAD_LOWER), a(AnimalAttrs.QL_SIDE_HEAD_LOWER + 1),
                    a(AnimalAttrs.QL_SIDE_HEAD_LOWER + 2) + slower * 0.2f,
                    z(0.8f * look3x).mul(x(0.8f * look3y))
                            .mul(y(-Math.min(look1x, 0f) + Math.max(look3x, 0f))), 1f);
            set(b("jaw_r"), 0f, a(AnimalAttrs.QL_JAW), a(AnimalAttrs.QL_JAW + 1),
                    x(slow * 0.05f - 0.05f), 0.98f);
            set(b("chest"), 0f, a(AnimalAttrs.QL_CHEST), a(AnimalAttrs.QL_CHEST + 1), y(slow * 0.03f), 1f);
            if (tongue) {
                set(b("tail_front"), 0f, a(AnimalAttrs.QL_TAIL_FRONT), a(AnimalAttrs.QL_TAIL_FRONT + 1),
                        identity, 0.98f);
                set(b("tail_rear"), 0f, a(AnimalAttrs.QL_TAIL_REAR), a(AnimalAttrs.QL_TAIL_REAR + 1),
                        identity, 0.98f);
            } else {
                set(b("tail_front"), 0f, a(AnimalAttrs.QL_TAIL_FRONT), a(AnimalAttrs.QL_TAIL_FRONT + 1),
                        x(0.15f).mul(z(slowAlt * 0.12f)), 0.98f);
                set(b("tail_rear"), 0f, a(AnimalAttrs.QL_TAIL_REAR), a(AnimalAttrs.QL_TAIL_REAR + 1),
                        z(slowAlt * 0.12f).mul(x(-0.12f)), 0.98f);
            }
            float yaw = slow * -0.05f;
            set(b("foot_fl"), -a(AnimalAttrs.QL_FEET_F), a(AnimalAttrs.QL_FEET_F + 1),
                    a(AnimalAttrs.QL_FEET_F + 2), y(yaw), 1f);
            set(b("foot_fr"), a(AnimalAttrs.QL_FEET_F), a(AnimalAttrs.QL_FEET_F + 1),
                    a(AnimalAttrs.QL_FEET_F + 2), y(yaw), 1f);
            set(b("foot_bl"), -a(AnimalAttrs.QL_FEET_B), a(AnimalAttrs.QL_FEET_B + 1),
                    a(AnimalAttrs.QL_FEET_B + 2), y(yaw), 1f);
            set(b("foot_br"), a(AnimalAttrs.QL_FEET_B), a(AnimalAttrs.QL_FEET_B + 1),
                    a(AnimalAttrs.QL_FEET_B + 2), y(yaw), 1f);
        }

        private void poseTheropod(float t) {
            float breathe = sin(t * 0.8f);
            float lookX = glance(t, 7331f, 0.5f);
            float lookY = glance(t, 1337f, 0.25f);
            set(b("head"), 0f, a(AnimalAttrs.TH_HEAD), a(AnimalAttrs.TH_HEAD + 1) + breathe * 0.3f,
                    x(lookY + breathe * 0.1f - 0.1f).mul(z(lookX)), 1.02f);
            set(b("jaw"), 0f, a(AnimalAttrs.TH_JAW), a(AnimalAttrs.TH_JAW + 1),
                    x(breathe * 0.05f - 0.05f), 0.98f);
            set(b("neck"), 0f, a(AnimalAttrs.TH_NECK), a(AnimalAttrs.TH_NECK + 1) + breathe * 0.2f,
                    x(-0.1f), 0.98f);
            set(b("chest_front"), 0f, a(AnimalAttrs.TH_CHEST_FRONT),
                    a(AnimalAttrs.TH_CHEST_FRONT + 1) + breathe * 0.3f, x(breathe * 0.04f), 1f);
            set(b("chest_back"), 0f, a(AnimalAttrs.TH_CHEST_BACK), a(AnimalAttrs.TH_CHEST_BACK + 1),
                    x(breathe * -0.04f), 1f);
            set(b("tail_front"), 0f, a(AnimalAttrs.TH_TAIL_FRONT), a(AnimalAttrs.TH_TAIL_FRONT + 1),
                    x(0.1f), 1.02f);
            set(b("tail_back"), 0f, a(AnimalAttrs.TH_TAIL_BACK), a(AnimalAttrs.TH_TAIL_BACK + 1),
                    x(0.1f), 0.98f);
            set(b("hand_l"), -a(AnimalAttrs.TH_HAND), a(AnimalAttrs.TH_HAND + 1), a(AnimalAttrs.TH_HAND + 2),
                    x(breathe * 0.2f), 0.98f);
            set(b("hand_r"), a(AnimalAttrs.TH_HAND), a(AnimalAttrs.TH_HAND + 1), a(AnimalAttrs.TH_HAND + 2),
                    x(breathe * 0.2f), 0.98f);
            set(b("leg_l"), -a(AnimalAttrs.TH_LEG), a(AnimalAttrs.TH_LEG + 1),
                    a(AnimalAttrs.TH_LEG + 2) + breathe * 0.05f, identity, 1.02f);
            set(b("leg_r"), a(AnimalAttrs.TH_LEG), a(AnimalAttrs.TH_LEG + 1),
                    a(AnimalAttrs.TH_LEG + 2) + breathe * 0.05f, identity, 1.02f);
            set(b("foot_l"), -a(AnimalAttrs.TH_FOOT), a(AnimalAttrs.TH_FOOT + 1),
                    a(AnimalAttrs.TH_FOOT + 2) + breathe * -0.15f, identity, 0.96f);
            set(b("foot_r"), a(AnimalAttrs.TH_FOOT), a(AnimalAttrs.TH_FOOT + 1),
                    a(AnimalAttrs.TH_FOOT + 2) + breathe * -0.15f, identity, 0.96f);
        }

        private void poseBird(float t) {
            float wave = (float) Math.cos(t * 4.5);
            float lookX = glance(t, 7331f, 0.5f);
            float lookY = glance(t, 1337f, 0.25f);
            float scaler = a(AnimalAttrs.BL_SCALER);
            boolean wyvern = speciesKind().endsWith("wyvern");
            set(b("neck"), 0f, a(AnimalAttrs.BL_NECK), a(AnimalAttrs.BL_NECK + 1), identity, 1.01f);
            set(b("beak"), 0f, a(AnimalAttrs.BL_BEAK), a(AnimalAttrs.BL_BEAK + 1),
                    x(wave * -0.02f - 0.02f), 1f);
            set(b("leg_l"), -a(AnimalAttrs.BL_LEG), a(AnimalAttrs.BL_LEG + 1), a(AnimalAttrs.BL_LEG + 2),
                    identity, 1f);
            set(b("leg_r"), a(AnimalAttrs.BL_LEG), a(AnimalAttrs.BL_LEG + 1), a(AnimalAttrs.BL_LEG + 2),
                    identity, 1f);
            set(b("foot_l"), -a(AnimalAttrs.BL_FOOT), a(AnimalAttrs.BL_FOOT + 1), a(AnimalAttrs.BL_FOOT + 2),
                    identity, 1.01f);
            set(b("foot_r"), a(AnimalAttrs.BL_FOOT), a(AnimalAttrs.BL_FOOT + 1), a(AnimalAttrs.BL_FOOT + 2),
                    identity, 1.01f);
            set(b("chest"), 0f, a(AnimalAttrs.BL_CHEST) * scaler,
                    (a(AnimalAttrs.BL_CHEST + 1) + wave * 0.06f + (wyvern ? 0f : 1.5f)) * scaler,
                    wyvern ? x(-0.1f) : identity, scaler * 0.99f);
            set(b("head"), 0f, a(AnimalAttrs.BL_HEAD), a(AnimalAttrs.BL_HEAD + 1),
                    z(lookX).mul(x((wyvern ? 0.4f : 0f) - Math.abs(lookY) + wave * 0.01f)), 0.99f);
            if (wyvern) {
                set(b("tail_front"), 0f, a(AnimalAttrs.BL_TAIL_FRONT), a(AnimalAttrs.BL_TAIL_FRONT + 1),
                        x(0.38f), 1.01f);
                set(b("tail_rear"), 0f, a(AnimalAttrs.BL_TAIL_REAR), a(AnimalAttrs.BL_TAIL_REAR + 1),
                        x(-0.1f), 0.99f);
                wing("wing_in_l", "wing_in_r", AnimalAttrs.BL_WING_IN, -0.8f, 0.4f, wave);
                wingMid(true);
                wingOut(0.1f, 0.55f);
            } else {
                set(b("tail_front"), 0f, a(AnimalAttrs.BL_TAIL_FRONT), a(AnimalAttrs.BL_TAIL_FRONT + 1),
                        x(0.6f), 1.01f);
                set(b("tail_rear"), 0f, a(AnimalAttrs.BL_TAIL_REAR), a(AnimalAttrs.BL_TAIL_REAR + 1),
                        x(-0.2f), 0.99f);
                wing("wing_in_l", "wing_in_r", AnimalAttrs.BL_WING_IN, -0.8f, 0.2f, wave);
                set(b("wing_mid_l"), -a(AnimalAttrs.BL_WING_MID), a(AnimalAttrs.BL_WING_MID + 1),
                        a(AnimalAttrs.BL_WING_MID + 2), y(-0.1f).mul(z(0.7f)), 1f);
                set(b("wing_mid_r"), a(AnimalAttrs.BL_WING_MID), a(AnimalAttrs.BL_WING_MID + 1),
                        a(AnimalAttrs.BL_WING_MID + 2), y(0.1f).mul(z(-0.7f)), 1f);
                set(b("wing_out_l"), -a(AnimalAttrs.BL_WING_OUT), a(AnimalAttrs.BL_WING_OUT + 1),
                        a(AnimalAttrs.BL_WING_OUT + 2), y(-0.4f).mul(z(0.2f)), 1f);
                set(b("wing_out_r"), a(AnimalAttrs.BL_WING_OUT), a(AnimalAttrs.BL_WING_OUT + 1),
                        a(AnimalAttrs.BL_WING_OUT + 2), y(0.4f).mul(z(-0.2f)), 1f);
            }
        }

        private void wing(String left, String right, int at, float yaw, float roll, float wave) {
            set(b(left), -a(at), a(at + 1), a(at + 2), y(yaw + wave * 0.06f).mul(z(roll)), 1f);
            set(b(right), a(at), a(at + 1), a(at + 2), y(-yaw - wave * 0.06f).mul(z(-roll)), 1f);
        }

        private void wingMid(boolean wyvern) {
            if (!wyvern) {
                return;
            }
            set(b("wing_mid_l"), -a(AnimalAttrs.BL_WING_MID), a(AnimalAttrs.BL_WING_MID + 1),
                    a(AnimalAttrs.BL_WING_MID + 2),
                    x(0.1f).mul(y(-0.1f)).mul(z(0.7f)), 1f);
            set(b("wing_mid_r"), a(AnimalAttrs.BL_WING_MID), a(AnimalAttrs.BL_WING_MID + 1),
                    a(AnimalAttrs.BL_WING_MID + 2),
                    x(0.1f).mul(y(0.1f)).mul(z(-0.7f)), 1f);
        }

        private void wingOut(float yaw, float roll) {
            set(b("wing_out_l"), -a(AnimalAttrs.BL_WING_OUT), a(AnimalAttrs.BL_WING_OUT + 1),
                    a(AnimalAttrs.BL_WING_OUT + 2), y(yaw).mul(z(roll)), 1f);
            set(b("wing_out_r"), a(AnimalAttrs.BL_WING_OUT), a(AnimalAttrs.BL_WING_OUT + 1),
                    a(AnimalAttrs.BL_WING_OUT + 2), y(-yaw).mul(z(-roll)), 1f);
        }

        private void poseFishSmall(float t) {
            float slow = sin(t * 3.5f + PI);
            set(b("chest"), 0f, a(AnimalAttrs.FS_CHEST), a(AnimalAttrs.FS_CHEST + 1), identity, 1f);
            set(b("tail"), 0f, a(AnimalAttrs.FS_TAIL), a(AnimalAttrs.FS_TAIL + 1), z(slow * 0.1f), 1f);
            set(b("fin_l"), -a(AnimalAttrs.FS_FIN), a(AnimalAttrs.FS_FIN + 1), a(AnimalAttrs.FS_FIN + 2),
                    z(slow * 0.1f - 0.1f), 1f);
            set(b("fin_r"), a(AnimalAttrs.FS_FIN), a(AnimalAttrs.FS_FIN + 1), a(AnimalAttrs.FS_FIN + 2),
                    z(-slow * 0.1f + 0.1f), 1f);
        }

        private void poseFishMedium(float t) {
            float slow = sin(t * 3.5f + PI);
            float slowAlt = sin(t * 3.5f + PI + 0.2f);
            set(b("head"), 0f, a(AnimalAttrs.FM_HEAD), a(AnimalAttrs.FM_HEAD + 1), z(slowAlt * -0.1f), 1f);
            set(b("jaw"), 0f, a(AnimalAttrs.FM_JAW), a(AnimalAttrs.FM_JAW + 1), identity, 0.98f);
            set(b("chest_front"), 0f, a(AnimalAttrs.FM_CHEST_FRONT), a(AnimalAttrs.FM_CHEST_FRONT + 1),
                    identity, 1f);
            set(b("chest_back"), 0f, a(AnimalAttrs.FM_CHEST_BACK), a(AnimalAttrs.FM_CHEST_BACK + 1),
                    z(slowAlt * 0.1f), 1f);
            set(b("tail"), 0f, a(AnimalAttrs.FM_TAIL), a(AnimalAttrs.FM_TAIL + 1), z(slow * 0.1f), 1f);
            set(b("fin_l"), -a(AnimalAttrs.FM_FIN), a(AnimalAttrs.FM_FIN + 1), a(AnimalAttrs.FM_FIN + 2),
                    z(slow * 0.1f - 0.1f), 1f);
            set(b("fin_r"), a(AnimalAttrs.FM_FIN), a(AnimalAttrs.FM_FIN + 1), a(AnimalAttrs.FM_FIN + 2),
                    z(-slow * 0.1f + 0.1f), 1f);
        }

        private void poseDragon(float t) {
            float ultra = sin(t);
            float slow = sin(t * 2.5f);
            float slowAlt = sin(t * 2.5f + PI / 2f);
            float lookX = glance(t, 7331f, 0.5f);
            float lookY = glance(t, 1337f, 0.25f);
            set(b("head_upper"), 0f, a(AnimalAttrs.DR_HEAD_UPPER),
                    a(AnimalAttrs.DR_HEAD_UPPER + 1) + ultra * 0.2f,
                    z(0.8f * lookX).mul(x(0.8f * lookY)), 1.05f);
            set(b("head_lower"), 0f, a(AnimalAttrs.DR_HEAD_LOWER),
                    a(AnimalAttrs.DR_HEAD_LOWER + 1) + ultra * 0.2f,
                    z(0.8f * lookX).mul(x(-0.2f + 0.8f * lookY)), 1.05f);
            set(b("jaw"), 0f, a(AnimalAttrs.DR_JAW), a(AnimalAttrs.DR_JAW + 1), x(slow * 0.04f), 1.05f);
            set(b("chest_front"), 0f, a(AnimalAttrs.DR_CHEST_FRONT), a(AnimalAttrs.DR_CHEST_FRONT + 1),
                    identity, 1f);
            set(b("chest_rear"), 0f, a(AnimalAttrs.DR_CHEST_REAR), a(AnimalAttrs.DR_CHEST_REAR + 1),
                    identity, 1f);
            set(b("tail_front"), 0f, a(AnimalAttrs.DR_TAIL_FRONT), a(AnimalAttrs.DR_TAIL_FRONT + 1),
                    z(slowAlt * 0.1f).mul(x(0.1f)), 0.98f);
            set(b("tail_rear"), 0f, a(AnimalAttrs.DR_TAIL_REAR), a(AnimalAttrs.DR_TAIL_REAR + 1),
                    z(slowAlt * 0.12f).mul(x(0.05f)), 0.98f);
            set(b("foot_fl"), -a(AnimalAttrs.DR_FEET_F), a(AnimalAttrs.DR_FEET_F + 1),
                    a(AnimalAttrs.DR_FEET_F + 2), identity, 1f);
            set(b("foot_fr"), a(AnimalAttrs.DR_FEET_F), a(AnimalAttrs.DR_FEET_F + 1),
                    a(AnimalAttrs.DR_FEET_F + 2), identity, 1f);
            set(b("foot_bl"), -a(AnimalAttrs.DR_FEET_B), a(AnimalAttrs.DR_FEET_B + 1),
                    a(AnimalAttrs.DR_FEET_B + 2), identity, 1f);
            set(b("foot_br"), a(AnimalAttrs.DR_FEET_B), a(AnimalAttrs.DR_FEET_B + 1),
                    a(AnimalAttrs.DR_FEET_B + 2), identity, 1f);
            set(b("wing_in_l"), -a(AnimalAttrs.DR_WING_IN), a(AnimalAttrs.DR_WING_IN + 1),
                    a(AnimalAttrs.DR_WING_IN + 2), y(0.8f + slow * 0.02f), 1f);
            set(b("wing_in_r"), a(AnimalAttrs.DR_WING_IN), a(AnimalAttrs.DR_WING_IN + 1),
                    a(AnimalAttrs.DR_WING_IN + 2), y(-0.8f - slow * 0.02f), 1f);
            set(b("wing_out_l"), -a(AnimalAttrs.DR_WING_OUT), a(AnimalAttrs.DR_WING_OUT + 1),
                    a(AnimalAttrs.DR_WING_OUT + 2), y(-2f + slow * 0.02f), 1f);
            set(b("wing_out_r"), a(AnimalAttrs.DR_WING_OUT), a(AnimalAttrs.DR_WING_OUT + 1),
                    a(AnimalAttrs.DR_WING_OUT + 2), y(2f - slow * 0.02f), 1f);
        }

        private void poseBug(float t) {
            float scaler = a(AnimalAttrs.AR_SCALER);
            set(b("head"), 0f, a(AnimalAttrs.AR_HEAD), a(AnimalAttrs.AR_HEAD + 1), identity, 1f);
            set(b("chest"), 0f, a(AnimalAttrs.AR_CHEST), a(AnimalAttrs.AR_CHEST + 1), identity, scaler);
            set(b("mandible_l"), -a(AnimalAttrs.AR_MANDIBLE), a(AnimalAttrs.AR_MANDIBLE + 1),
                    a(AnimalAttrs.AR_MANDIBLE + 2), identity, 1f);
            set(b("mandible_r"), a(AnimalAttrs.AR_MANDIBLE), a(AnimalAttrs.AR_MANDIBLE + 1),
                    a(AnimalAttrs.AR_MANDIBLE + 2), identity, 1f);
            set(b("wing_fl"), -a(AnimalAttrs.AR_WING_F), a(AnimalAttrs.AR_WING_F + 1),
                    a(AnimalAttrs.AR_WING_F + 2), identity, 1f);
            set(b("wing_fr"), a(AnimalAttrs.AR_WING_F), a(AnimalAttrs.AR_WING_F + 1),
                    a(AnimalAttrs.AR_WING_F + 2), identity, 1f);
            set(b("wing_bl"), -a(AnimalAttrs.AR_WING_B), a(AnimalAttrs.AR_WING_B + 1),
                    a(AnimalAttrs.AR_WING_B + 2), identity, 0.98f);
            set(b("wing_br"), a(AnimalAttrs.AR_WING_B), a(AnimalAttrs.AR_WING_B + 1),
                    a(AnimalAttrs.AR_WING_B + 2), identity, 0.98f);
            if (steps()) {
                poseAntlion(t, scaler);
                return;
            }
            bugLeg("leg_fl", "leg_fr", AnimalAttrs.AR_LEG_F, a(AnimalAttrs.AR_LEG_ORI));
            bugLeg("leg_fcl", "leg_fcr", AnimalAttrs.AR_LEG_FC, a(AnimalAttrs.AR_LEG_ORI + 1));
            bugLeg("leg_bcl", "leg_bcr", AnimalAttrs.AR_LEG_BC, a(AnimalAttrs.AR_LEG_ORI + 2));
            bugLeg("leg_bl", "leg_br", AnimalAttrs.AR_LEG_B, a(AnimalAttrs.AR_LEG_ORI + 3));
        }

        /** Antlions, beetles, and spiders stand still in Veloren. This is their walk. */
        private boolean steps() {
            return switch (speciesKind()) {
                case "antlion", "hornbeetle", "leafbeetle", "stagbeetle", "weevil",
                        "tarantula", "blackwidow", "cavespider" -> true;
                default -> false;
            };
        }

        /** Walk cycle at the rate Veloren uses while a bug is moving. */
        private void poseAntlion(float t, float scaler) {
            float mixed = t * 4.8f;
            float wave1 = sin(mixed);
            float wave2 = sin(mixed - PI / 2f);
            float wave3 = sin(mixed + PI / 2f);
            float wave4 = sin(mixed + PI);
            float foot1 = squareish(wave1);
            float foot2 = squareish(wave2);
            float foot3 = squareish(wave3);
            float foot4 = squareish(wave4);
            float ori0 = a(AnimalAttrs.AR_LEG_ORI);
            float ori1 = a(AnimalAttrs.AR_LEG_ORI + 1);
            float ori2 = a(AnimalAttrs.AR_LEG_ORI + 2);
            float ori3 = a(AnimalAttrs.AR_LEG_ORI + 3);
            set(b("head"), 0f, a(AnimalAttrs.AR_HEAD), a(AnimalAttrs.AR_HEAD + 1),
                    x(wave1 * 0.1f).mul(y(Math.min(wave1, 0f) * 0.08f))
                            .mul(z(sin(mixed + PI * 1.5f) * 0.08f)),
                    1f);
            set(b("chest"), 0f, a(AnimalAttrs.AR_CHEST), a(AnimalAttrs.AR_CHEST + 1),
                    x(Math.max(wave1, 0f) * 0.06f).mul(z(sin(mixed + PI / 2f) * 0.06f)), scaler);
            stepped("leg_fl", AnimalAttrs.AR_LEG_F, -1f, Math.max(foot4, 0f) * 0.7f, 0f,
                    ori0 + foot2 * 0.4f);
            stepped("leg_fr", AnimalAttrs.AR_LEG_F, 1f, Math.max(foot1, 0f) * 0.7f, 0f,
                    -ori0 - foot3 * 0.4f);
            stepped("leg_fcl", AnimalAttrs.AR_LEG_FC, -1f, foot4 * 0.2f, Math.max(foot1, 0f) * 0.7f,
                    ori1 + foot3 * 0.2f);
            stepped("leg_fcr", AnimalAttrs.AR_LEG_FC, 1f, foot1 * 0.2f, Math.min(foot1, 0f) * 0.7f,
                    -ori1 - foot2 * 0.2f);
            stepped("leg_bcl", AnimalAttrs.AR_LEG_BC, -1f, foot4 * 0.2f, Math.max(foot4, 0f) * 0.7f,
                    ori2 + foot2 * 0.3f);
            stepped("leg_bcr", AnimalAttrs.AR_LEG_BC, 1f, foot1 * 0.2f, Math.min(foot4, 0f) * 0.7f,
                    -ori2 - foot3 * 0.3f);
            stepped("leg_bl", AnimalAttrs.AR_LEG_B, -1f, foot4 * 0.2f, Math.max(foot1, 0f) * 0.7f,
                    ori3 + foot3 * 0.2f);
            stepped("leg_br", AnimalAttrs.AR_LEG_B, 1f, foot1 * 0.2f, Math.min(foot1, 0f) * 0.7f,
                    -ori3 - foot2 * 0.2f);
        }

        private void stepped(String bone, int at, float side, float pitch, float yaw, float roll) {
            set(b(bone), side * a(at), a(at + 1), a(at + 2),
                    x(pitch).mul(y(yaw)).mul(z(roll)), 1f);
        }

        private void bugLeg(String left, String right, int at, float roll) {
            set(b(left), -a(at), a(at + 1), a(at + 2), z(roll), 1f);
            set(b(right), a(at), a(at + 1), a(at + 2), z(-roll), 1f);
        }

        /** Ogres, trolls, and the other big bipeds. Werewolves breathe. Mindflayers float. */
        private void poseTroll(float t) {
            float wave = sin(t + 1.5f * PI) * 1.5f;
            float slower = sin(t * 2f + PI) * 1.5f;
            float slow = sin(t * 7f + PI) * 1.5f;
            float lookX = glance(t, 7331f, 0.5f);
            float lookY = glance(t, 1337f, 0.25f);
            boolean beast = species.startsWith("werewolf");
            boolean floating = a(22) > 0.5f;
            float breathe = 0f;
            if (beast) {
                breathe = 0.04f * sin(1.5f * t)
                        + 0.05f * 0.2f * sin(t * 60f) * (float) Math.cos(t * 1.5);
            }
            set(b("head"), 0f, a(0), a(1) + wave * 0.2f,
                    z(lookX * 0.6f).mul(x(lookY * 0.6f + breathe)), 1f);
            float upperZ = floating ? a(5) + slower + 4f : a(5) + wave * -0.5f;
            set(b("torso_upper"), 0f, a(4), upperZ, x(-breathe), 1f);
            set(b("torso_lower"), 0f, a(6), a(7) + wave * 0.5f, x(breathe), 1.02f);
            float jawY = beast ? a(2) : a(2) - slower * 0.12f;
            float jawZ = beast ? a(3) : a(3) + slow * 0.2f;
            set(b("jaw"), 0f, jawY, jawZ, x(-0.1f + breathe * 2f), 1.02f);
            set(b("tail"), 0f, a(8), a(9), z(slow * 0.2f), 1f);
            set(b("shoulder_l"), -a(10), a(11), a(12), x(breathe), 1.1f);
            set(b("shoulder_r"), a(10), a(11), a(12), x(breathe), 1.1f);
            set(b("hand_l"), -a(13), a(14), a(15) + wave * -0.1f, identity, 1.04f);
            set(b("hand_r"), a(13), a(14), a(15) + wave * -0.1f, identity, 1.04f);
            set(b("leg_l"), -a(16), a(17), a(18) + wave * -0.2f, identity, 1f);
            set(b("leg_r"), a(16), a(17), a(18) + wave * -0.2f, identity, 1f);
            Quaternion feet = floating ? x(-0.5f + slow * 0.1f) : identity;
            set(b("foot_l"), -a(19), a(20), a(21), feet, 1f);
            set(b("foot_r"), a(19), a(20), a(21), feet, 1f);
        }

        private void poseGolem(float t) {
            float breathe = sin(t + 1.5f * PI);
            float lookX = glance(t, 7331f, 0.5f);
            float lookY = glance(t, 1337f, 0.25f);
            set(b("head"), 0f, a(0) * 1.02f, (a(1) + breathe * 0.2f) * 1.02f,
                    z(lookX * 0.6f).mul(x(lookY * 0.6f)), 1.02f);
            set(b("jaw"), 0f, (a(2) - breathe * 0.12f) * 1.02f, (a(3) + breathe * 0.2f) * 1.02f,
                    x(-0.1f + breathe * 0.1f), 1.02f);
            set(b("torso_upper"), 0f, a(4), a(5) + breathe * 0.5f, identity, 1f);
            set(b("torso_lower"), 0f, a(6), a(7) + breathe * -0.2f, identity, 1f);
            set(b("shoulder_l"), -a(8), a(9), a(10), x(-0.2f), 1f);
            set(b("shoulder_r"), a(8), a(9), a(10), x(-0.2f), 1f);
            set(b("hand_l"), -a(11), a(12), a(13) + breathe * 0.6f, x(0.2f), 1.04f);
            set(b("hand_r"), a(11), a(12), a(13) + breathe * 0.6f, x(0.2f), 1.04f);
            set(b("leg_l"), -a(14) * 1.02f, a(15) * 1.02f, (a(16) + breathe * -0.2f) * 1.02f,
                    identity, 1.02f);
            set(b("leg_r"), a(14) * 1.02f, a(15) * 1.02f, (a(16) + breathe * -0.2f) * 1.02f,
                    identity, 1.02f);
            set(b("foot_l"), -a(17), a(18), a(19) + breathe * -0.2f, identity, 1f);
            set(b("foot_r"), a(17), a(18), a(19) + breathe * -0.2f, identity, 1f);
        }

        private void posePerson(float t) {
            float slow = sin(t * 4f);
            float medium = sin(t * 10f);
            boolean wings = a(14) > 0.5f;
            set(b("head"), 0f, a(0), a(1) + slow * -0.1f, identity, 1f);
            set(b("chest"), 0f, a(2), a(3) + slow * 0.3f, identity, 1f);
            set(b("pants"), 0f, a(4), a(5), identity, 1f);
            set(b("tail"), 0f, a(6), a(7), identity, 1f);
            set(b("hand_l"), -a(8), a(9), a(10) + slow * -0.1f, identity, 1f);
            set(b("hand_r"), a(8), a(9), a(10) + slow * -0.1f, identity, 1f);
            Quaternion leftFoot = wings
                    ? x(-0.6f - medium * 0.6f).mul(z(medium * 0.4f)) : identity;
            set(b("foot_l"), -a(11), a(12), a(13), leftFoot, 1f);
            Quaternion rightFoot = wings
                    ? x(-0.6f - medium * 0.6f).mul(z(medium * -0.4f)) : identity;
            set(b("foot_r"), a(11), a(12), a(13), rightFoot, 1f);
            set(b("main"), 4f, -4f, -3f, y(-0.5f).mul(z(PI / 2f)), 1f);
            set(b("second"), -4f, -4f, -3f, y(0.5f).mul(z(-PI / 2f)), 1f);
        }

        /** Parent rotation and offset folded into a child, for controls the rig does not keep. */
        private void fold(String bone, float ax, float ay, float az, float apitch,
                          float hx, float hy, float hz, float hpitch, float hyaw, float size) {
            qd.setFromAxisRad(1f, 0f, 0f, apitch);
            qa.setFromAxisRad(1f, 0f, 0f, hpitch).mul(qb.setFromAxisRad(0f, 1f, 0f, hyaw));
            scratch.set(hx, hy, hz);
            qd.transform(scratch);
            set(b(bone), ax + scratch.x, ay + scratch.y, az + scratch.z, qd.mul(qa), size);
        }

        private void runSmall(float t) {
            float speednorm = 1f;
            float mixed = t * 24f;
            float tempo = a(AnimalAttrs.QS_TEMPO);
            float phase = mixed * 0.42f * tempo;
            float lateral = a(AnimalAttrs.QS_LATERAL);
            float spring = a(AnimalAttrs.QS_SPRING);
            float maximize = a(AnimalAttrs.QS_MAXIMIZE);
            float minimize = a(AnimalAttrs.QS_MINIMIZE);
            float vertcancel = 1f - lateral;
            float brief = sin(phase + PI);
            float briefAlt = sin(phase + PI * 0.5f);
            float foot = sin(phase);
            float foott = sin(phase + PI * 0.4f);
            float footf = sin(phase + PI * 0.3f);
            float foottf = sin(phase + PI * 0.7f);
            set(b("head"), 0f, a(AnimalAttrs.QS_HEAD), a(AnimalAttrs.QS_HEAD + 1),
                    x(vertcancel * brief * -0.2f).mul(z(lateral * -brief * 0.2f)), 1f);
            set(b("chest"), 0f, a(AnimalAttrs.QS_CHEST),
                    a(AnimalAttrs.QS_CHEST + 1) + 2f * spring + briefAlt * 3f * spring,
                    x(vertcancel * brief * 0.2f * spring).mul(z(lateral * brief * 0.2f)), 1f);
            set(b("leg_fl"), -a(AnimalAttrs.QS_FEET_F),
                    a(AnimalAttrs.QS_FEET_F + 1) + foottf * 3f * minimize,
                    a(AnimalAttrs.QS_FEET_F + 2) + Math.max(foottf * -1.5f, -1f),
                    x(0.2f + maximize * foottf * 0.65f), 1.02f);
            set(b("leg_fr"), a(AnimalAttrs.QS_FEET_F),
                    a(AnimalAttrs.QS_FEET_F + 1) + foott * 3f * minimize,
                    a(AnimalAttrs.QS_FEET_F + 2) + Math.max(foot * -1.5f, -1f),
                    x(0.2f + maximize * foott * 0.65f), 1.02f);
            set(b("leg_bl"), -a(AnimalAttrs.QS_FEET_B),
                    a(AnimalAttrs.QS_FEET_B + 1) + foott * -1.4f,
                    a(AnimalAttrs.QS_FEET_B + 2) + Math.max(foot * 1.5f, -1f),
                    x(-0.25f + maximize * foott * -0.8f), 1.02f);
            set(b("leg_br"), a(AnimalAttrs.QS_FEET_B),
                    a(AnimalAttrs.QS_FEET_B + 1) + foottf * -1.4f,
                    a(AnimalAttrs.QS_FEET_B + 2) + Math.max(foottf * 1.5f, -1f),
                    x(-0.25f + maximize * foottf * -0.8f), 1.02f);
            set(b("tail"), 0f, a(AnimalAttrs.QS_TAIL), a(AnimalAttrs.QS_TAIL + 1),
                    x(vertcancel * brief * 0.2f).mul(z(lateral * -brief * 0.2f)), 1f);
        }

        private void runMedium(float t) {
            float scaler = Math.max(a(AnimalAttrs.QM_SCALER), 0.05f);
            float tempo = a(AnimalAttrs.QM_TEMPO);
            float spring = a(AnimalAttrs.QM_SPRING);
            float mixed = t * 26.5f;
            float lab = 0.72f * tempo;
            float wave = mixed * lab + PI * -0.15f - 0.5f;
            float s = sin(wave);
            float brief = (float) Math.sqrt(1.0 / (0.72 + 0.28 * s * s)) * s;
            float briefAlt = sin(mixed * lab + PI * 3f / 8f - 0.5f);
            float shift1 = 1f - PI / 2f - PI * 3f / 4f;
            float shift2 = 1f + PI / 2f + PI / 2f;
            float shift3 = 1f;
            float shift4 = 1f - PI * 3f / 4f + PI / 2f;
            float foot1a = sin(mixed * lab + 0.05f + shift1);
            float foot1b = sin(mixed * lab + 1.1f + 0.05f + shift1);
            float foot2a = sin(mixed * lab + shift2 + PI);
            float foot2b = sin(mixed * lab + 1.1f + shift2 + PI);
            float foot3a = sin(mixed * lab + shift3 + PI);
            float foot3b = sin(mixed * lab + PI / 2f + shift3 + PI);
            float foot4a = sin(mixed * lab + 0.05f + shift4);
            float foot4b = sin(mixed * lab + PI / 2f + 0.05f + shift4);
            float amp = 1f / scaler;
            float phase = (float) Math.floor(t);
            float lookX = sin(phase * 7331f) * 0.5f;
            float lookY = sin(phase * 1337f) * 0.25f;
            set(b("head"), 0f, a(AnimalAttrs.QM_HEAD), a(AnimalAttrs.QM_HEAD + 1) + briefAlt * -0.2f,
                    x(lookY * 0.3f / 1f + amp * brief * 0.2f - 0.1f).mul(z(lookX * 0.3f)), 1f);
            set(b("neck"), 0f, a(AnimalAttrs.QM_NECK), a(AnimalAttrs.QM_NECK + 1) + briefAlt * -0.8f,
                    x(amp * brief * 0.05f), 1.02f);
            set(b("jaw"), 0f, a(AnimalAttrs.QM_JAW), a(AnimalAttrs.QM_JAW + 1), identity, 1.02f);
            set(b("tail"), 0f, a(AnimalAttrs.QM_TAIL), a(AnimalAttrs.QM_TAIL + 1),
                    x(amp * briefAlt * -0.3f), 1f);
            set(b("torso_front"), 0f, a(AnimalAttrs.QM_TORSO_FRONT),
                    a(AnimalAttrs.QM_TORSO_FRONT + 1) + 1f + briefAlt * 2.5f * spring,
                    x(Math.min((amp * Math.max(brief * -0.13f, -0.2f)) * spring, 0.1f)), 1f);
            set(b("torso_back"), 0f, a(AnimalAttrs.QM_TORSO_BACK),
                    a(AnimalAttrs.QM_TORSO_BACK + 1) + amp * briefAlt * 0.2f - 0.2f,
                    x(amp * brief * -0.07f), 1f);
            Quaternion ears = trunk ? identity : x(amp * briefAlt * 0.2f + 0.2f);
            set(b("ears"), 0f, a(AnimalAttrs.QM_EARS), a(AnimalAttrs.QM_EARS + 1), ears, 1.02f);
            set(b("leg_fl"), -a(AnimalAttrs.QM_LEG_F),
                    a(AnimalAttrs.QM_LEG_F + 1) + amp * foot1b * -1.6f,
                    a(AnimalAttrs.QM_LEG_F + 2) + amp * foot1a * 2.3f,
                    x(0.2f + amp * foot1a), 1.02f);
            set(b("leg_fr"), a(AnimalAttrs.QM_LEG_F),
                    a(AnimalAttrs.QM_LEG_F + 1) + amp * foot2b * -1.6f,
                    a(AnimalAttrs.QM_LEG_F + 2) + amp * foot2a * 2.3f,
                    x(0.2f + amp * foot2a), 1.02f);
            set(b("leg_bl"), -a(AnimalAttrs.QM_LEG_B),
                    a(AnimalAttrs.QM_LEG_B + 1) + amp * foot3a * -4.5f,
                    a(AnimalAttrs.QM_LEG_B + 2) + amp * foot3b * -2.2f,
                    x(-0.1f + amp * foot3a * -1.2f), 1.02f);
            set(b("leg_br"), a(AnimalAttrs.QM_LEG_B),
                    a(AnimalAttrs.QM_LEG_B + 1) + amp * foot4a * -4.5f,
                    a(AnimalAttrs.QM_LEG_B + 2) + amp * foot4b * -2.2f,
                    x(-0.1f + amp * foot4a * -1.2f), 1.02f);
            float start = a(AnimalAttrs.QM_STARTANGLE);
            set(b("foot_fl"), -a(AnimalAttrs.QM_FEET_F), a(AnimalAttrs.QM_FEET_F + 1),
                    a(AnimalAttrs.QM_FEET_F + 2) + Math.max(foot1a * 2f, -1f) * amp + 1f,
                    x(start + amp * foot1b * -0.85f), 0.96f);
            set(b("foot_fr"), a(AnimalAttrs.QM_FEET_F), a(AnimalAttrs.QM_FEET_F + 1),
                    a(AnimalAttrs.QM_FEET_F + 2) + Math.max(foot2a * 2f, -1f) * amp + 1f,
                    x(start + amp * foot2b * -0.85f), 0.96f);
            set(b("foot_bl"), -a(AnimalAttrs.QM_FEET_B), a(AnimalAttrs.QM_FEET_B + 1),
                    a(AnimalAttrs.QM_FEET_B + 2) + Math.max(foot3a * 0.8f, 0f) * amp + 1f,
                    x(amp * foot3b * -0.7f - 0.2f), 0.96f);
            set(b("foot_br"), a(AnimalAttrs.QM_FEET_B), a(AnimalAttrs.QM_FEET_B + 1),
                    a(AnimalAttrs.QM_FEET_B + 2) + Math.max(foot4a * 0.8f, 0f) * amp + 1f,
                    x(amp * foot4b * -0.7f - 0.2f), 0.96f);
        }

        private void runLow(float t) {
            float tempo = a(AnimalAttrs.QL_TEMPO);
            float lean = a(AnimalAttrs.QL_LEAN);
            float lean2 = a(AnimalAttrs.QL_LEAN + 1);
            float mixed = t * 21f;
            float lab = 0.8f * tempo;
            float s = sin(mixed * lab + PI * 0.25f);
            float brief = (float) Math.sqrt(1.0 / (0.72 + 0.28 * s * s)) * s;
            float briefAlt = s;
            float flH = shaped(0.4f, 0.6f, mixed * lab + PI * 1.45f);
            float flV = sin(mixed * lab);
            float frH = shaped(0.4f, 0.6f, mixed * lab + PI * 0.45f);
            float frV = sin(mixed * lab + PI);
            float blH = shaped(0.4f, 0.6f, mixed * lab + PI * 1.05f);
            float blV = sin(mixed * lab + PI * -0.4f);
            float brH = shaped(0.4f, 0.6f, mixed * lab + PI * 0.05f);
            float brV = sin(mixed * lab + PI * 0.6f);
            boolean tongue = speciesKind().startsWith("snaretongue");
            set(b("head_c_upper"), 0f, a(AnimalAttrs.QL_HEAD_UPPER), a(AnimalAttrs.QL_HEAD_UPPER + 1),
                    x(-lean).mul(z(brief * -0.06f)), 1f);
            set(b("head_c_lower"), 0f, a(AnimalAttrs.QL_HEAD_LOWER), a(AnimalAttrs.QL_HEAD_LOWER + 1),
                    z(brief * -0.15f), 1f);
            set(b("jaw_c"), 0f, a(AnimalAttrs.QL_JAW), a(AnimalAttrs.QL_JAW + 1), identity, 0.98f);
            set(b("head_l_upper"), -a(AnimalAttrs.QL_SIDE_HEAD_UPPER), a(AnimalAttrs.QL_SIDE_HEAD_UPPER + 1),
                    a(AnimalAttrs.QL_SIDE_HEAD_UPPER + 2), x(-lean).mul(z(brief * -0.06f)), 1f);
            set(b("head_l_lower"), -a(AnimalAttrs.QL_SIDE_HEAD_LOWER), a(AnimalAttrs.QL_SIDE_HEAD_LOWER + 1),
                    a(AnimalAttrs.QL_SIDE_HEAD_LOWER + 2), z(brief * -0.15f), 1f);
            set(b("jaw_l"), 0f, a(AnimalAttrs.QL_JAW), a(AnimalAttrs.QL_JAW + 1), identity, 0.98f);
            set(b("head_r_upper"), a(AnimalAttrs.QL_SIDE_HEAD_UPPER), a(AnimalAttrs.QL_SIDE_HEAD_UPPER + 1),
                    a(AnimalAttrs.QL_SIDE_HEAD_UPPER + 2), x(-lean).mul(z(brief * -0.06f)), 1f);
            set(b("head_r_lower"), a(AnimalAttrs.QL_SIDE_HEAD_LOWER), a(AnimalAttrs.QL_SIDE_HEAD_LOWER + 1),
                    a(AnimalAttrs.QL_SIDE_HEAD_LOWER + 2), z(brief * -0.15f), 1f);
            set(b("jaw_r"), 0f, a(AnimalAttrs.QL_JAW), a(AnimalAttrs.QL_JAW + 1), identity, 0.98f);
            if (tongue) {
                set(b("tail_front"), 0f, a(AnimalAttrs.QL_TAIL_FRONT), a(AnimalAttrs.QL_TAIL_FRONT + 1),
                        identity, 0.98f);
                set(b("tail_rear"), 0f, a(AnimalAttrs.QL_TAIL_REAR), a(AnimalAttrs.QL_TAIL_REAR + 1),
                        identity, 0.98f);
            } else {
                set(b("tail_front"), 0f, a(AnimalAttrs.QL_TAIL_FRONT) + lean * 2f,
                        a(AnimalAttrs.QL_TAIL_FRONT + 1) + lean * 2f,
                        z(briefAlt * -0.18f * lean2).mul(y(briefAlt * -0.1f)).mul(x(0.06f - lean * 1.2f)),
                        0.98f);
                set(b("tail_rear"), 0f, a(AnimalAttrs.QL_TAIL_REAR), a(AnimalAttrs.QL_TAIL_REAR + 1),
                        z(briefAlt * -0.25f * lean2).mul(y(briefAlt * 0.08f)).mul(x(-0.04f)), 0.98f);
            }
            set(b("chest"), 0f, a(AnimalAttrs.QL_CHEST), a(AnimalAttrs.QL_CHEST + 1),
                    z(brief * 0.13f).mul(y(brief * 0.12f)).mul(x(lean)), 1f);
            set(b("foot_fl"), -a(AnimalAttrs.QL_FEET_F),
                    a(AnimalAttrs.QL_FEET_F + 1) + flH * -2f,
                    a(AnimalAttrs.QL_FEET_F + 2) + 1f + Math.max(flV * -1.8f, 0f),
                    x(-0.2f + flV * -0.45f * lean2 - lean).mul(z(flH * 0.4f * lean2)), 1f);
            set(b("foot_fr"), a(AnimalAttrs.QL_FEET_F),
                    a(AnimalAttrs.QL_FEET_F + 1) + frH * -2f,
                    a(AnimalAttrs.QL_FEET_F + 2) + 1f + Math.max(frV * -1.8f, 0f),
                    x(-0.2f + frV * -0.45f * lean2 - lean).mul(z(frH * -0.4f * lean2)), 1f);
            set(b("foot_bl"), -a(AnimalAttrs.QL_FEET_B),
                    a(AnimalAttrs.QL_FEET_B + 1) + blH * -1f,
                    a(AnimalAttrs.QL_FEET_B + 2) + Math.max(blV * -1.2f, 0f),
                    x(-0.2f + blV * -0.5f - lean).mul(z(blH * 0.4f)), 1f);
            set(b("foot_br"), a(AnimalAttrs.QL_FEET_B),
                    a(AnimalAttrs.QL_FEET_B + 1) + brH * -1f,
                    a(AnimalAttrs.QL_FEET_B + 2) + Math.max(brV * -1.2f, 0f),
                    x(-0.2f + brV * -0.5f - lean).mul(z(brH * -0.4f)), 1f);
        }

        private void runTheropod(float t) {
            float speednorm = 1f;
            float mixed = t * 19f;
            float lab = 0.45f;
            float wave = mixed * lab + PI * -0.15f - 0.5f;
            float s = sin(wave);
            float brief = (float) Math.sqrt(1.0 / (0.72 + 0.28 * s * s)) * s;
            float briefAlt = sin(mixed * lab + PI * 3f / 8f - 0.5f);
            float foot1a = sin(mixed * lab + PI);
            float foot1b = sin(mixed * lab + PI / 2f + PI);
            float foot2a = sin(mixed * lab);
            float foot2b = sin(mixed * lab + PI / 2f);
            boolean steady = "axebeak".equals(speciesKind());
            set(b("head"), 0f, a(AnimalAttrs.TH_HEAD), a(AnimalAttrs.TH_HEAD + 1),
                    x(-0.1f + brief * -0.05f).mul(z(briefAlt * -0.2f)), 1.02f);
            set(b("jaw"), 0f, a(AnimalAttrs.TH_JAW), a(AnimalAttrs.TH_JAW + 1), x(brief * -0.03f), 0.98f);
            set(b("neck"), 0f, a(AnimalAttrs.TH_NECK), a(AnimalAttrs.TH_NECK + 1),
                    x(-0.1f + brief * -0.04f).mul(z(briefAlt * -0.1f)), 0.98f);
            set(b("chest_front"), 0f, a(AnimalAttrs.TH_CHEST_FRONT),
                    a(AnimalAttrs.TH_CHEST_FRONT + 1) + brief * 0.5f + 0.5f,
                    x(brief * 0.07f).mul(z(briefAlt * 0.15f)), 1f);
            set(b("chest_back"), 0f, a(AnimalAttrs.TH_CHEST_BACK), a(AnimalAttrs.TH_CHEST_BACK + 1),
                    x(brief * -0.04f).mul(z(briefAlt * -0.15f)), 1f);
            set(b("tail_front"), 0f, a(AnimalAttrs.TH_TAIL_FRONT), a(AnimalAttrs.TH_TAIL_FRONT + 1),
                    x(0.1f + brief * -0.02f).mul(z(briefAlt * -0.1f)), 1f);
            set(b("tail_back"), 0f, a(AnimalAttrs.TH_TAIL_BACK), a(AnimalAttrs.TH_TAIL_BACK + 1),
                    x(0.2f + brief * -0.1f).mul(z(briefAlt * -0.2f)), 1f);
            if (steady) {
                set(b("hand_l"), -a(AnimalAttrs.TH_HAND) - 8f, a(AnimalAttrs.TH_HAND + 1),
                        a(AnimalAttrs.TH_HAND + 2), x(-0.2f).mul(z(-0.6f)), 1f);
                set(b("hand_r"), a(AnimalAttrs.TH_HAND) + 8f, a(AnimalAttrs.TH_HAND + 1),
                        a(AnimalAttrs.TH_HAND + 2), x(-0.2f).mul(z(0.6f)), 1f);
            } else {
                set(b("hand_l"), -a(AnimalAttrs.TH_HAND), a(AnimalAttrs.TH_HAND + 1),
                        a(AnimalAttrs.TH_HAND + 2), x(-0.2f + foot2a * 0.3f), 1f);
                set(b("hand_r"), a(AnimalAttrs.TH_HAND), a(AnimalAttrs.TH_HAND + 1),
                        a(AnimalAttrs.TH_HAND + 2), x(-0.2f + foot1a * 0.3f), 1f);
            }
            set(b("leg_l"), -a(AnimalAttrs.TH_LEG) + 1.5f,
                    a(AnimalAttrs.TH_LEG + 1) + foot1b * -2.3f,
                    a(AnimalAttrs.TH_LEG + 2) * 1.5f + Math.max(foot1a, 0f) * 3f,
                    x(-0.2f + foot1a * 0.15f).mul(z(foot1a * -0.3f)), 1f);
            set(b("leg_r"), a(AnimalAttrs.TH_LEG) - 1.5f,
                    a(AnimalAttrs.TH_LEG + 1) + foot2b * -2.3f,
                    a(AnimalAttrs.TH_LEG + 2) * 1.5f + Math.max(foot2a, 0f) * 3f,
                    x(-0.2f + foot2a * 0.15f).mul(z(foot2a * 0.3f)), 1f);
            set(b("foot_l"), -a(AnimalAttrs.TH_FOOT),
                    a(AnimalAttrs.TH_FOOT + 1) + foot1b * -2f,
                    a(AnimalAttrs.TH_FOOT + 2) + 0.5f + Math.max(foot1a * 1.5f, 0f),
                    x(0.2f + foot2b * 0.6f), 0.96f);
            set(b("foot_r"), a(AnimalAttrs.TH_FOOT),
                    a(AnimalAttrs.TH_FOOT + 1) + foot2b * -2f,
                    a(AnimalAttrs.TH_FOOT + 2) + 0.5f + Math.max(foot2a * 1.5f, 0f),
                    x(0.2f + foot1b * 0.6f), 0.96f);
        }

        private void runBirdLarge(float t) {
            float speednorm = (float) Math.pow(22.0 / 13.0, 0.25);
            float mixed = t * 27f;
            float lab = 0.9f * 0.8f;
            float wave = mixed * lab + PI * -0.15f - 0.5f;
            float s = sin(wave);
            float brief = (float) Math.sqrt(1.0 / (0.72 + 0.28 * s * s)) * s * speednorm;
            float briefAlt = sin(mixed * lab + PI * 3f / 8f - 0.5f) * speednorm;
            float foot1a = sin(mixed * lab + PI) * speednorm;
            float foot1b = sin(mixed * lab + PI / 2f + PI) * speednorm;
            float foot2a = sin(mixed * lab) * speednorm;
            float foot2b = sin(mixed * lab + PI / 2f) * speednorm;
            float scaler = a(AnimalAttrs.BL_SCALER);
            boolean wyvern = speciesKind().endsWith("wyvern");
            set(b("head"), 0f, a(AnimalAttrs.BL_HEAD), a(AnimalAttrs.BL_HEAD + 1),
                    x(-0.1f * speednorm + brief * -0.05f).mul(z(briefAlt * -0.05f)), 0.99f);
            set(b("beak"), 0f, a(AnimalAttrs.BL_BEAK), a(AnimalAttrs.BL_BEAK + 1),
                    x(brief * -0.02f - 0.02f), 1f);
            Quaternion neck = x(-0.1f * speednorm + brief * -0.04f).mul(z(briefAlt * -0.1f));
            qd.set(neck).mul(y(briefAlt * (wyvern ? 0.2f : -0.2f)));
            set(b("neck"), 0f, a(AnimalAttrs.BL_NECK), a(AnimalAttrs.BL_NECK + 1), qd, 1.01f);
            set(b("foot_l"), -a(AnimalAttrs.BL_FOOT),
                    a(AnimalAttrs.BL_FOOT + 1) + foot1b * -1f,
                    a(AnimalAttrs.BL_FOOT + 2) + Math.max(foot1a * 4f, 0f),
                    x(0.2f * speednorm + foot1b * -0.3f + 0.1f), 1.01f);
            set(b("foot_r"), a(AnimalAttrs.BL_FOOT),
                    a(AnimalAttrs.BL_FOOT + 1) + foot2b * -1f,
                    a(AnimalAttrs.BL_FOOT + 2) + Math.max(foot2a * 4f, 0f),
                    x(0.2f * speednorm + foot2b * -0.3f + 0.1f), 1.01f);
            if (wyvern) {
                set(b("chest"), 0f, a(AnimalAttrs.BL_CHEST) * scaler,
                        (a(AnimalAttrs.BL_CHEST + 1) + brief * 0.5f + speednorm * -2f) * scaler,
                        x(-0.1f + brief * 0.07f).mul(y(briefAlt * -0.2f)).mul(z(briefAlt * 0.1f)),
                        scaler * 0.99f);
                set(b("tail_front"), 0f, a(AnimalAttrs.BL_TAIL_FRONT), a(AnimalAttrs.BL_TAIL_FRONT + 1),
                        x(0.3f + brief * -0.02f).mul(y(briefAlt * 0.2f)), 1.01f);
                set(b("tail_rear"), 0f, a(AnimalAttrs.BL_TAIL_REAR), a(AnimalAttrs.BL_TAIL_REAR + 1),
                        x(-0.1f + brief * -0.1f), 0.99f);
                set(b("wing_in_l"), -a(AnimalAttrs.BL_WING_IN) + 1.5f,
                        a(AnimalAttrs.BL_WING_IN + 1) + foot1a * 2f,
                        a(AnimalAttrs.BL_WING_IN + 2) + speednorm + foot1b - 3f,
                        x(foot2a * -0.05f + speednorm * -0.3f)
                                .mul(y(-0.8f + speednorm * 0.5f + foot2b * -0.2f + briefAlt * 0.3f))
                                .mul(z(0.2f + foot2a * 0.6f)), 1f);
                set(b("wing_in_r"), a(AnimalAttrs.BL_WING_IN) - 1.5f,
                        a(AnimalAttrs.BL_WING_IN + 1) + foot2a * 2f,
                        a(AnimalAttrs.BL_WING_IN + 2) + speednorm + foot2b - 3f,
                        x(foot1a * -0.05f + speednorm * -0.3f)
                                .mul(y(0.8f + speednorm * -0.55f + foot1b * 0.2f + briefAlt * 0.3f))
                                .mul(z(-0.2f + foot1a * -0.6f)), 1f);
                set(b("wing_mid_l"), -a(AnimalAttrs.BL_WING_MID), a(AnimalAttrs.BL_WING_MID + 1),
                        a(AnimalAttrs.BL_WING_MID + 2), x(0.1f).mul(y(-0.1f)).mul(z(0.7f)), 1f);
                set(b("wing_mid_r"), a(AnimalAttrs.BL_WING_MID), a(AnimalAttrs.BL_WING_MID + 1),
                        a(AnimalAttrs.BL_WING_MID + 2), x(0.1f).mul(y(0.1f)).mul(z(-0.7f)), 1f);
                set(b("wing_out_l"), -a(AnimalAttrs.BL_WING_OUT), a(AnimalAttrs.BL_WING_OUT + 1),
                        a(AnimalAttrs.BL_WING_OUT + 2),
                        y(0.2f + brief * 0.05f).mul(z(0.3f + foot2a * 0.3f)), 1f);
                set(b("wing_out_r"), a(AnimalAttrs.BL_WING_OUT), a(AnimalAttrs.BL_WING_OUT + 1),
                        a(AnimalAttrs.BL_WING_OUT + 2),
                        y(-0.2f + brief * -0.05f).mul(z(-0.3f + foot1a * 0.3f)), 1f);
                set(b("leg_l"), -a(AnimalAttrs.BL_LEG),
                        a(AnimalAttrs.BL_LEG + 1) + foot1b * -4f,
                        a(AnimalAttrs.BL_LEG + 2) + foot2b * -1.5f,
                        x(-0.3f * speednorm + foot1a * 0.15f), 1f);
                set(b("leg_r"), a(AnimalAttrs.BL_LEG),
                        a(AnimalAttrs.BL_LEG + 1) + foot2b * -4f,
                        a(AnimalAttrs.BL_LEG + 2) + foot1b * -1.5f,
                        x(-0.3f * speednorm + foot2a * 0.15f), 1f);
            } else {
                set(b("chest"), 0f, a(AnimalAttrs.BL_CHEST) * scaler,
                        (a(AnimalAttrs.BL_CHEST + 1) + brief * 0.5f + 0.5f * speednorm) * scaler,
                        x(brief * 0.07f + Math.max(foot1b, foot2b) * 0.2f)
                                .mul(y(foot1b * -0.2f)).mul(z(briefAlt * 0.1f)),
                        scaler * 0.99f);
                set(b("tail_front"), 0f, a(AnimalAttrs.BL_TAIL_FRONT), a(AnimalAttrs.BL_TAIL_FRONT + 1),
                        x(0.6f + brief * -0.02f), 1.01f);
                set(b("tail_rear"), 0f, a(AnimalAttrs.BL_TAIL_REAR), a(AnimalAttrs.BL_TAIL_REAR + 1),
                        x(-0.2f + brief * -0.1f), 0.99f);
                set(b("wing_in_l"), -a(AnimalAttrs.BL_WING_IN), a(AnimalAttrs.BL_WING_IN + 1),
                        a(AnimalAttrs.BL_WING_IN + 2), y(-0.8f).mul(z(0.2f)), 1f);
                set(b("wing_in_r"), a(AnimalAttrs.BL_WING_IN), a(AnimalAttrs.BL_WING_IN + 1),
                        a(AnimalAttrs.BL_WING_IN + 2), y(0.8f).mul(z(-0.2f)), 1f);
                set(b("wing_mid_l"), -a(AnimalAttrs.BL_WING_MID), a(AnimalAttrs.BL_WING_MID + 1),
                        a(AnimalAttrs.BL_WING_MID + 2), y(-0.1f).mul(z(0.7f)), 1f);
                set(b("wing_mid_r"), a(AnimalAttrs.BL_WING_MID), a(AnimalAttrs.BL_WING_MID + 1),
                        a(AnimalAttrs.BL_WING_MID + 2), y(0.1f).mul(z(-0.7f)), 1f);
                set(b("wing_out_l"), -a(AnimalAttrs.BL_WING_OUT), a(AnimalAttrs.BL_WING_OUT + 1),
                        a(AnimalAttrs.BL_WING_OUT + 2), y(-0.2f + brief * 0.05f).mul(z(0.2f)), 1f);
                set(b("wing_out_r"), a(AnimalAttrs.BL_WING_OUT), a(AnimalAttrs.BL_WING_OUT + 1),
                        a(AnimalAttrs.BL_WING_OUT + 2), y(0.2f + brief * -0.05f).mul(z(-0.2f)), 1f);
                set(b("leg_l"), -a(AnimalAttrs.BL_LEG), a(AnimalAttrs.BL_LEG + 1) + foot1b * -5f,
                        a(AnimalAttrs.BL_LEG + 2),
                        x(-0.2f * speednorm + foot1a * 0.1f).mul(y((foot1a + 1f) * 0.15f)), 1f);
                set(b("leg_r"), a(AnimalAttrs.BL_LEG), a(AnimalAttrs.BL_LEG + 1) + foot2b * -5f,
                        a(AnimalAttrs.BL_LEG + 2),
                        x(-0.2f * speednorm + foot2a * 0.1f).mul(y(-(foot2a + 1f) * 0.15f)), 1f);
            }
        }

        private void swimFishSmall(float t) {
            float tempo = a(AnimalAttrs.FS_TEMPO);
            float amp = a(AnimalAttrs.FS_AMPLITUDE);
            float fast = sin(amp * t * tempo + PI);
            float slow = amp * 0.1f;
            set(b("chest"), 0f, a(AnimalAttrs.FS_CHEST), a(AnimalAttrs.FS_CHEST + 1), z(fast * -0.1f), 1f);
            set(b("tail"), 0f, a(AnimalAttrs.FS_TAIL), a(AnimalAttrs.FS_TAIL + 1), z(fast * -slow), 1f);
            set(b("fin_l"), -a(AnimalAttrs.FS_FIN), a(AnimalAttrs.FS_FIN + 1), a(AnimalAttrs.FS_FIN + 2),
                    z(fast * 0.6f * slow - 0.3f), 1f);
            set(b("fin_r"), a(AnimalAttrs.FS_FIN), a(AnimalAttrs.FS_FIN + 1), a(AnimalAttrs.FS_FIN + 2),
                    z(fast * -0.6f * slow + 0.3f), 1f);
        }

        private void swimFishMedium(float t) {
            float tempo = a(AnimalAttrs.FM_TEMPO);
            float amp = a(AnimalAttrs.FM_AMPLITUDE);
            float slowAlt = sin(amp * t * tempo / 1.5f + PI + 0.2f);
            float fast = sin(amp * t * tempo + PI);
            float fastAlt = sin(amp * t * tempo + PI + 0.2f);
            float slow = amp * 0.1f;
            set(b("head"), 0f, a(AnimalAttrs.FM_HEAD), a(AnimalAttrs.FM_HEAD + 1), z(slowAlt * -0.1f), 1f);
            set(b("jaw"), 0f, a(AnimalAttrs.FM_JAW), a(AnimalAttrs.FM_JAW + 1), identity, 1f);
            set(b("chest_front"), 0f, a(AnimalAttrs.FM_CHEST_FRONT), a(AnimalAttrs.FM_CHEST_FRONT + 1),
                    z(fast * amp * -0.02f), 1f);
            set(b("chest_back"), 0f, a(AnimalAttrs.FM_CHEST_BACK), a(AnimalAttrs.FM_CHEST_BACK + 1),
                    z(fastAlt * slow), 1f);
            set(b("tail"), 0f, a(AnimalAttrs.FM_TAIL), a(AnimalAttrs.FM_TAIL + 1), z(fast * slow), 1f);
            set(b("fin_l"), -a(AnimalAttrs.FM_FIN), a(AnimalAttrs.FM_FIN + 1), a(AnimalAttrs.FM_FIN + 2),
                    z(fast * slow - 0.1f), 1f);
            set(b("fin_r"), a(AnimalAttrs.FM_FIN), a(AnimalAttrs.FM_FIN + 1), a(AnimalAttrs.FM_FIN + 2),
                    z(fast * -slow + 0.1f), 1f);
        }

        private void runDragon(float t) {
            float lab = 0.6f;
            float wave = t * 16f * lab + PI;
            float s = sin(wave);
            float brief = (float) Math.sqrt(1.0 / (0.72 + 0.28 * s * s)) * s;
            float briefAlt = sin(t * 16f * lab + PI * 0.5f);
            float step = 14f;
            float vertlf = Math.max(sin(t * step + PI * 1.8f), 0.15f);
            float vertrf = Math.max(sin(t * step + PI * 0.80f), 0.15f);
            float vertlb = Math.max(sin(t * step), 0.15f);
            float vertrb = Math.max(sin(t * step + PI), 0.15f);
            float horilf = sin(t * step + PI * 1.2f);
            float horirf = sin(t * step + PI * 0.20f);
            float horilb = sin(t * step + PI * 1.4f);
            float horirb = sin(t * step + PI * 0.4f);
            float center = sin(t * step + PI / 2f);
            float centerOff = sin(t * step + PI * 1.5f);
            float jawWave = (float) Math.cos(t * 3.0 + PI);
            float slow = sin(t * 4.5f);
            float height = a(AnimalAttrs.DR_HEIGHT);
            set(b("head_upper"), 0f, a(AnimalAttrs.DR_HEAD_UPPER), a(AnimalAttrs.DR_HEAD_UPPER + 1),
                    x(brief * -0.03f - 0.1f), 1f);
            set(b("head_lower"), 0f, a(AnimalAttrs.DR_HEAD_LOWER), a(AnimalAttrs.DR_HEAD_LOWER + 1),
                    x(brief * -0.05f), 1.02f);
            set(b("jaw"), 0f, a(AnimalAttrs.DR_JAW) - jawWave * 0.12f,
                    a(AnimalAttrs.DR_JAW + 1) + slow * 0.2f, x(slow * 0.03f), 1.05f);
            set(b("tail_front"), 0f, a(AnimalAttrs.DR_TAIL_FRONT),
                    a(AnimalAttrs.DR_TAIL_FRONT + 1) + centerOff * 0.6f, x(center * 0.03f), 0.98f);
            set(b("tail_rear"), 0f, a(AnimalAttrs.DR_TAIL_REAR),
                    a(AnimalAttrs.DR_TAIL_REAR + 1) + centerOff * 0.6f, x(center * 0.03f), 0.98f);
            set(b("chest_front"), 0f, a(AnimalAttrs.DR_CHEST_FRONT),
                    a(AnimalAttrs.DR_CHEST_FRONT + 1) + briefAlt * 2.5f, x(brief * 0.13f), 1f);
            set(b("chest_rear"), 0f, a(AnimalAttrs.DR_CHEST_REAR),
                    a(AnimalAttrs.DR_CHEST_REAR + 1) + briefAlt * 0.2f, x(brief * 0.1f), 1f);
            set(b("foot_fl"), -a(AnimalAttrs.DR_FEET_F),
                    a(AnimalAttrs.DR_FEET_F + 1) + horilf * 2.5f,
                    a(AnimalAttrs.DR_FEET_F + 2) + vertlf * 5f * height - 0.5f, x(horilf * 0.6f), 1f);
            set(b("foot_fr"), a(AnimalAttrs.DR_FEET_F),
                    a(AnimalAttrs.DR_FEET_F + 1) + horirf * 2.5f,
                    a(AnimalAttrs.DR_FEET_F + 2) + vertrf * 5f * height - 0.5f, x(horirb * 0.6f), 1f);
            set(b("foot_bl"), -a(AnimalAttrs.DR_FEET_B),
                    a(AnimalAttrs.DR_FEET_B + 1) + horilb * 3f,
                    a(AnimalAttrs.DR_FEET_B + 2) + vertlb * 5f * height - 0.5f, x(horilf * 0.55f), 1f);
            set(b("foot_br"), a(AnimalAttrs.DR_FEET_B),
                    a(AnimalAttrs.DR_FEET_B + 1) + horirb * 3f,
                    a(AnimalAttrs.DR_FEET_B + 2) + vertrb * 5f * height - 0.5f, x(horirb * 0.55f), 1f);
            set(b("wing_in_l"), -a(AnimalAttrs.DR_WING_IN), a(AnimalAttrs.DR_WING_IN + 1),
                    a(AnimalAttrs.DR_WING_IN + 2), y(0.8f), 1f);
            set(b("wing_in_r"), a(AnimalAttrs.DR_WING_IN), a(AnimalAttrs.DR_WING_IN + 1),
                    a(AnimalAttrs.DR_WING_IN + 2), y(-0.8f), 1f);
            set(b("wing_out_l"), -a(AnimalAttrs.DR_WING_OUT), a(AnimalAttrs.DR_WING_OUT + 1),
                    a(AnimalAttrs.DR_WING_OUT + 2), y(-2f), 1f);
            set(b("wing_out_r"), a(AnimalAttrs.DR_WING_OUT), a(AnimalAttrs.DR_WING_OUT + 1),
                    a(AnimalAttrs.DR_WING_OUT + 2), y(2f), 1f);
        }

        private void runBug(float t) {
            float scaler = a(AnimalAttrs.AR_SCALER);
            float mixed = t * 22.4f;
            float wave1 = sin(mixed);
            float wave2 = sin(mixed - PI / 2f);
            float wave3 = sin(mixed + PI / 2f);
            float wave4 = sin(mixed + PI);
            float foot1 = squareish(wave1);
            float foot2 = squareish(wave2);
            float foot3 = squareish(wave3);
            float foot4 = squareish(wave4);
            float ori0 = a(AnimalAttrs.AR_LEG_ORI);
            float ori1 = a(AnimalAttrs.AR_LEG_ORI + 1);
            float ori2 = a(AnimalAttrs.AR_LEG_ORI + 2);
            float ori3 = a(AnimalAttrs.AR_LEG_ORI + 3);
            set(b("head"), 0f, a(AnimalAttrs.AR_HEAD), a(AnimalAttrs.AR_HEAD + 1),
                    x(wave1 * 0.1f).mul(y(Math.min(wave1, 0f) * 0.08f))
                            .mul(z(sin(mixed + PI * 1.5f) * 0.08f)), 1f);
            set(b("chest"), 0f, a(AnimalAttrs.AR_CHEST), a(AnimalAttrs.AR_CHEST + 1),
                    x(Math.max(wave1, 0f) * 0.06f).mul(z(sin(mixed + PI / 2f) * 0.06f)), scaler);
            set(b("mandible_l"), -a(AnimalAttrs.AR_MANDIBLE), a(AnimalAttrs.AR_MANDIBLE + 1),
                    a(AnimalAttrs.AR_MANDIBLE + 2), identity, 1f);
            set(b("mandible_r"), a(AnimalAttrs.AR_MANDIBLE), a(AnimalAttrs.AR_MANDIBLE + 1),
                    a(AnimalAttrs.AR_MANDIBLE + 2), identity, 1f);
            set(b("wing_fl"), -a(AnimalAttrs.AR_WING_F), a(AnimalAttrs.AR_WING_F + 1),
                    a(AnimalAttrs.AR_WING_F + 2), identity, 1f);
            set(b("wing_fr"), a(AnimalAttrs.AR_WING_F), a(AnimalAttrs.AR_WING_F + 1),
                    a(AnimalAttrs.AR_WING_F + 2), identity, 1f);
            set(b("wing_bl"), -a(AnimalAttrs.AR_WING_B), a(AnimalAttrs.AR_WING_B + 1),
                    a(AnimalAttrs.AR_WING_B + 2), identity, 0.98f);
            set(b("wing_br"), a(AnimalAttrs.AR_WING_B), a(AnimalAttrs.AR_WING_B + 1),
                    a(AnimalAttrs.AR_WING_B + 2), identity, 0.98f);
            stepped("leg_fl", AnimalAttrs.AR_LEG_F, -1f, Math.max(foot4, 0f) * 0.7f, 0f,
                    ori0 + foot2 * 0.4f);
            stepped("leg_fr", AnimalAttrs.AR_LEG_F, 1f, Math.max(foot1, 0f) * 0.7f, 0f,
                    -ori0 - foot3 * 0.4f);
            stepped("leg_fcl", AnimalAttrs.AR_LEG_FC, -1f, foot4 * 0.2f, Math.max(foot1, 0f) * 0.7f,
                    ori1 + foot3 * 0.2f);
            stepped("leg_fcr", AnimalAttrs.AR_LEG_FC, 1f, foot1 * 0.2f, Math.min(foot1, 0f) * 0.7f,
                    -ori1 - foot2 * 0.2f);
            stepped("leg_bcl", AnimalAttrs.AR_LEG_BC, -1f, foot4 * 0.2f, Math.max(foot4, 0f) * 0.7f,
                    ori2 + foot2 * 0.3f);
            stepped("leg_bcr", AnimalAttrs.AR_LEG_BC, 1f, foot1 * 0.2f, Math.min(foot4, 0f) * 0.7f,
                    -ori2 - foot3 * 0.3f);
            stepped("leg_bl", AnimalAttrs.AR_LEG_B, -1f, foot4 * 0.2f, Math.max(foot1, 0f) * 0.7f,
                    ori3 + foot3 * 0.2f);
            stepped("leg_br", AnimalAttrs.AR_LEG_B, 1f, foot1 * 0.2f, Math.min(foot1, 0f) * 0.7f,
                    -ori3 - foot2 * 0.2f);
        }

        private void runCrab(float t) {
            float mixed = t * 22.4f;
            float direction = 0.098f * 22f;
            float wave1 = sin(mixed);
            float foot1 = squareish(wave1);
            float foot2 = squareish(sin(mixed - PI / 2f));
            float foot3 = squareish(sin(mixed + PI / 2f));
            float foot4 = squareish(sin(mixed + PI));
            boolean sideways = a(AnimalAttrs.CR_MOVE_SIDEWAYS) > 0.5f;
            float up = 0.3f;
            set(b("chest"), 0f, a(AnimalAttrs.CR_CHEST), a(AnimalAttrs.CR_CHEST + 1),
                    sideways
                            ? x(Math.max(wave1, 0f) * 0.06f).mul(z(sin(mixed + PI / 2f) * 0.06f))
                            : x(Math.max(wave1, 0f) * 0.06f),
                    a(AnimalAttrs.CR_SCALER));
            set(b("arm_l"), 0f, a(AnimalAttrs.CR_ARM + 1), 0f,
                    sideways
                            ? x(1f).mul(y(Math.max(foot4, 0f) * up)).mul(z(0.3f - foot2 * 0.1f * direction))
                            : z(0.3f - foot2 * 0.2f * direction),
                    1f);
            set(b("arm_r"), 0f, a(AnimalAttrs.CR_ARM + 1), 0f,
                    sideways
                            ? x(1f).mul(y(-Math.max(foot1, 0f) * up)).mul(z(-0.2f - foot3 * 0.1f * direction))
                            : z(-0.2f + foot3 * 0.2f * direction),
                    1f);
            if (!sideways) {
                set(b("pincer_l0"), 0f, 0f, 0f, x(Math.max(wave1, 0f) * 0.2f), 1f);
                set(b("pincer_l1"), 0f, -4f, 1f, x(Math.max(wave1, 0f) * -0.4f), 1f);
                set(b("pincer_r0"), 0f, 0f, 0f, x(Math.max(wave1, 0f) * 0.2f), 1f);
                set(b("pincer_r1"), 0f, -4f, 3f, x(Math.max(wave1, 0f) * -0.4f), 1f);
            }
            set(b("leg_fl"), -a(AnimalAttrs.CR_LEG_F), a(AnimalAttrs.CR_LEG_F + 1), a(AnimalAttrs.CR_LEG_F + 2),
                    y(Math.max(foot4, 0f) * up).mul(z(a(AnimalAttrs.CR_LEG_ORI) + foot2 * 0.1f * direction)), 1f);
            set(b("leg_fr"), a(AnimalAttrs.CR_LEG_F), a(AnimalAttrs.CR_LEG_F + 1), a(AnimalAttrs.CR_LEG_F + 2),
                    y(-Math.max(foot1, 0f) * up).mul(z(-a(AnimalAttrs.CR_LEG_ORI) - foot3 * 0.1f * direction)), 1f);
            set(b("leg_cl"), -a(AnimalAttrs.CR_LEG_C), a(AnimalAttrs.CR_LEG_C + 1), a(AnimalAttrs.CR_LEG_C + 2),
                    x(foot4 * 0.1f * direction).mul(y(Math.max(foot1, 0f) * up))
                            .mul(z(a(AnimalAttrs.CR_LEG_ORI + 1) + foot3 * 0.2f * direction)), 1f);
            set(b("leg_cr"), a(AnimalAttrs.CR_LEG_C), a(AnimalAttrs.CR_LEG_C + 1), a(AnimalAttrs.CR_LEG_C + 2),
                    x(foot1 * 0.1f * direction).mul(y(Math.min(foot1, 0f) * up))
                            .mul(z(-a(AnimalAttrs.CR_LEG_ORI + 1) - foot2 * 0.2f * direction)), 1f);
            set(b("leg_bl"), -a(AnimalAttrs.CR_LEG_B), a(AnimalAttrs.CR_LEG_B + 1), a(AnimalAttrs.CR_LEG_B + 2),
                    x(foot4 * 0.2f).mul(y(Math.max(foot4, 0f) * up))
                            .mul(z(a(AnimalAttrs.CR_LEG_ORI + 2) + foot3 * 0.2f * -direction)), 1f);
            set(b("leg_br"), a(AnimalAttrs.CR_LEG_B), a(AnimalAttrs.CR_LEG_B + 1), a(AnimalAttrs.CR_LEG_B + 2),
                    x(foot1 * 0.2f * direction).mul(y(Math.min(foot4, 0f) * up))
                            .mul(z(-a(AnimalAttrs.CR_LEG_ORI + 2) - foot2 * 0.2f * -direction)), 1f);
        }

        private float trollTempo() {
            return switch (speciesKind()) {
                case "ogre", "cavetroll", "mountaintroll", "swamptroll" -> 0.9f;
                case "cyclops", "dullahan", "minotaur", "terracottademolisher",
                        "terracottapunisher", "cursekeeper" -> 0.8f;
                case "terracottabesieger", "terracottapursuer" -> 0.7f;
                default -> 1f;
            };
        }

        private void runTroll(float t) {
            float tempo = trollTempo();
            float speed = 16f;
            float speednorm = (float) Math.pow(speed / 12.0, 0.6);
            float speednormlow = (float) Math.pow(speed / 12.0, 4.0);
            float lab = 0.65f * tempo;
            float acc = speed * t;
            float footvertl = sin(acc * lab + PI * -0.2f) * speednorm;
            float footvertr = sin(acc * lab + PI * -1.2f) * speednorm;
            float footrotl = shaped(0.5f, 0.5f, acc * lab + PI * 1.4f);
            float footrotr = shaped(0.5f, 0.5f, acc * lab + PI * 0.4f);
            float foothoril = sin(acc * lab + PI * 1.45f) * speednorm;
            float foothorir = sin(acc * lab + PI * 0.45f) * speednorm;
            float brief = sin(acc * lab);
            float briefAlt = sin(acc * lab + PI * 3f + 0.7f);
            float slow = sin(acc * lab + PI);
            boolean beast = species.startsWith("werewolf");
            boolean floating = a(22) > 0.5f;
            if (beast) {
                float amplitude = Math.max(speed / 21f, 0.25f);
                float amplitude2 = Math.max((float) Math.sqrt(speed * 1.4f / 21f), 0.6f);
                float amplitude3 = Math.max((float) Math.sqrt(speed / 21f), 0.35f);
                float canceler = (float) Math.sqrt(speed / 21f);
                float adjust = speed / 21f;
                float shift1 = adjust - PI / 2f - adjust * PI * 3f / 4f;
                float shift2 = adjust + PI / 2f + adjust * PI / 2f;
                float shift3 = adjust + PI / 4f - adjust * PI / 4f;
                float shift4 = adjust - PI * 3f / 4f + adjust * PI / 2f;
                float foot1a = sin(acc * lab + canceler * 0.05f + shift1);
                float foot1b = sin(acc * lab + 1.1f + canceler * 0.05f + shift1);
                float foot2a = sin(acc * lab + shift2);
                float foot2b = sin(acc * lab + 1.1f + shift2);
                float foot3a = sin(acc * lab + shift3);
                float foot3b = sin(acc * lab + 0.3f + shift3);
                float foot4a = sin(acc * lab + canceler * 0.05f + shift4);
                float foot4b = sin(acc * lab + PI / 2f + canceler * 0.05f + shift4);
                float phase = (float) Math.floor(t);
                float lookX = sin(phase * 7331f) * 0.5f;
                float lookY = sin(phase * 1337f) * 0.25f;
                set(b("head"), 0f, a(0), 3f + a(1),
                        x(lookY * 0.3f + speed * 0.05f + amplitude * brief * -0.18f).mul(z(lookX * 0.3f)), 1f);
                set(b("jaw"), 0f, a(2), a(3), identity, 1.02f);
                set(b("tail"), 0f, a(8), a(9), x(canceler + amplitude * briefAlt * 0.1f), 1f);
                float bob = Math.max(brief * 6f, -16f);
                qd.setFromAxisRad(1f, 0f, 0f, amplitude * brief * 0.1f + speed * -0.045f);
                scratch.set(0f, a(4), a(5));
                qd.transform(scratch);
                set(b("torso_upper"), scratch.x, scratch.y + bob, scratch.z + speed * 1.2f + bob, qd, 1f);
                set(b("torso_lower"), 0f, a(6), a(7),
                        x(amplitude * brief * -0.25f + canceler * -0.4f), 1f);
                fold("shoulder_l", 0f, amplitude3 * foot1b * -1.5f + canceler * -2f,
                        amplitude3 * foot1a * 1.5f, 0.3f * canceler + amplitude3 * foot1a * 0.4f,
                        -a(10), a(11), a(12), 0f, 0f, 1.1f);
                fold("shoulder_r", 0f, amplitude3 * foot2b * -1.5f + canceler * -2f,
                        amplitude3 * foot2a * 1.5f, 0.3f * canceler + amplitude3 * foot2a * 0.4f,
                        a(10), a(11), a(12), 0f, 0f, 1.1f);
                fold("hand_l", 0f, amplitude3 * foot1b * -1.5f + canceler * -2f,
                        amplitude3 * foot1a * 1.5f, 0.3f * canceler + amplitude3 * foot1a * 0.4f,
                        -a(13), a(14) + foot1a, a(15) + Math.max(foot1a * -3f, 1f) * amplitude2,
                        amplitude2 * foot1b * 0.9f + canceler * 0.9f, 0f, 1.04f);
                fold("hand_r", 0f, amplitude3 * foot2b * -1.5f + canceler * -2f,
                        amplitude3 * foot2a * 1.5f, 0.3f * canceler + amplitude3 * foot2a * 0.4f,
                        a(13), a(14) + foot2a, a(15) + Math.max(foot2a * -3f, 1f) * amplitude2,
                        amplitude2 * foot2b * 0.9f + canceler * 0.7f, 0f, 1.04f);
                fold("leg_l", 0f, amplitude3 * foot3b * -1f + canceler * -2f,
                        amplitude3 * foot3a * -2.5f, canceler * -0.4f + amplitude3 * foot3b * 0.4f + 0.5f,
                        -a(16), a(17), a(18), 0f, 0f, 1f);
                fold("leg_r", 0f, amplitude3 * foot4b * -1f + canceler * -2f,
                        amplitude3 * foot4a * -2.5f, canceler * -0.4f + amplitude3 * foot4b * 0.4f + 0.5f,
                        a(16), a(17), a(18), 0f, 0f, 1f);
                fold("foot_l", 0f, amplitude3 * foot3b * -1f + canceler * -2f,
                        amplitude3 * foot3a * -2.5f, canceler * -0.4f + amplitude3 * foot3b * 0.4f + 0.5f,
                        -a(19), a(20) + foot3a * 2f, a(21) + Math.max(foot3b * 4f * amplitude2, -1f),
                        amplitude2 * foot3b * 0.45f + 0.5f, 0f, 1f);
                fold("foot_r", 0f, amplitude3 * foot4b * -1f + canceler * -2f,
                        amplitude3 * foot4a * -2.5f, canceler * -0.4f + amplitude3 * foot4b * 0.4f + 0.5f,
                        a(19), a(20) + foot4a * 2f, a(21) + Math.max(foot4b * 4f * amplitude2, -1f),
                        amplitude2 * foot4b * 0.45f + 0.5f, 0f, 1f);
            } else {
                set(b("head"), 0f, a(0) * 1.02f, a(1) * 1.02f,
                        z(brief * -0.18f * speednorm).mul(x(0.25f * speednorm)), 1f);
                set(b("torso_upper"), 0f, a(4), a(5) + briefAlt * -1.5f * speednorm,
                        x(-0.25f * speednorm).mul(z(brief * 0.07f * speednorm)), 1f);
                set(b("torso_lower"), 0f, a(6), a(7),
                        z(brief * 0.05f * speednorm).mul(x(0.14f * speednorm)), 1f);
                set(b("jaw"), 0f, a(2), a(3), identity, 1.02f);
                set(b("tail"), 0f, a(8), a(9), x(briefAlt * 0.3f * speednorm), 1f);
                set(b("shoulder_l"), -a(10), a(11), a(12) - foothorir * 1.5f,
                        x(0.6f * speednormlow + footrotr * -0.8f * speednorm), 1.1f);
                set(b("shoulder_r"), a(10), a(11), a(12) - foothoril * 1.5f,
                        x(0.6f * speednormlow + footrotl * -0.8f * speednorm), 1.1f);
                set(b("hand_l"), -a(13) + foothorir * -1.3f,
                        2f * speednorm + a(14) + foothorir * -5f,
                        1.5f * speednorm + a(15) - foothorir * 4.5f,
                        x(0.6f * speednorm + footrotr * -1.2f * speednorm)
                                .mul(y(footrotr * 0.4f * speednorm)), 1.04f);
                set(b("hand_r"), a(13) + foothoril * 1.3f,
                        2f * speednorm + a(14) + foothoril * -5f,
                        1.5f * speednorm + a(15) - foothoril * 4.5f,
                        x(0.6f * speednorm + footrotl * -1.2f * speednorm)
                                .mul(y(footrotl * -0.4f * speednorm)), 1.04f);
                set(b("leg_l"), -a(16) * 0.98f,
                        (a(17) + foothoril * -3.5f) * 0.98f,
                        (a(18) + footvertl * -3f) * 0.98f,
                        z(brief * 0.18f * speednorm).mul(x(foothoril * -0.8f)), 1f);
                set(b("leg_r"), a(16) * 0.98f,
                        (a(17) + foothorir * -3.5f) * 0.98f,
                        (a(18) + footvertr * -3f) * 0.98f,
                        z(brief * 0.18f * speednorm).mul(x(foothorir * -0.8f)), 1f);
                float direction = -0.098f * speed;
                set(b("foot_l"), -a(19),
                        a(20) + (-1.5f * speednorm + foothoril * -10.5f) + Math.max(direction * 5f, 0f),
                        a(21) + (speednorm + Math.max(footvertl * -4.1f, -1f)),
                        x(-0.2f + foothoril * -0.9f), 1f);
                set(b("foot_r"), a(19),
                        a(20) + (-1.5f * speednorm + foothorir * -10.5f) + Math.max(direction * 5f, 0f),
                        a(21) + (speednorm + Math.max(footvertr * -4.1f, -1f)),
                        x(-0.2f + foothorir * -0.9f), 1f);
            }
            if (floating) {
                set(b("head"), 0f, a(0), a(1), x(slow * 0.1f), 1f);
                set(b("torso_upper"), 0f, a(4), a(5) + slow * 2f + 4f * speednorm,
                        x(-0.1f * speednorm + slow * 0.05f), 1f);
                set(b("torso_lower"), 0f, a(6), a(7),
                        z(brief * 0.05f * speednorm).mul(x(0.14f * speednorm)), 1f);
                set(b("shoulder_l"), -a(10), a(11), a(12), x(-0.4f * speednormlow + slow * 0.1f), 1f);
                set(b("shoulder_r"), a(10), a(11), a(12), x(-0.4f * speednormlow + slow * 0.1f), 1f);
                set(b("hand_l"), -a(13), a(14), a(15), x(-0.4f * speednorm + slow * 0.1f), 1f);
                set(b("hand_r"), a(13), a(14), a(15), x(-0.4f * speednorm + slow * 0.1f), 1f);
                set(b("foot_l"), -a(19), a(20), a(21), x(-0.5f * speednorm + slow * 0.1f), 1f);
                set(b("foot_r"), a(19), a(20), a(21), x(-0.5f * speednorm + slow * 0.1f), 1f);
            }
        }

        private void runGolem(float t) {
            float mixed = t * 9f;
            float lab = 0.45f;
            float speednorm = 1f;
            float foothoril = shaped(0.4f, 0.6f, mixed * 2f * lab + PI * 1.4f);
            float foothorir = shaped(0.4f, 0.6f, mixed * 2f * lab + PI * 0.4f);
            float footvertl = sin(mixed * 2f * lab);
            float footvertr = sin(mixed * 2f * lab + PI);
            float footrotl = shaped(0.5f, 0.5f, mixed * 2f * lab + PI * 1.4f);
            float footrotr = shaped(0.2f, 0.8f, mixed * 2f * lab + PI * 0.4f);
            float brief = sin(mixed * lab * 2f);
            float briefAlt = sin(mixed * lab * 2f + PI / 2f);
            set(b("head"), 0f, a(0) * 1.02f, a(1) * 1.02f,
                    z(brief * -0.3f).mul(x(-0.2f)), 1.02f);
            set(b("jaw"), 0f, a(2) * 1.02f, a(3) * 1.02f, identity, 1.02f);
            set(b("torso_upper"), 0f, a(4), a(5) + brief,
                    x(-0.2f).mul(z(brief * 0.40f)), 1f);
            set(b("torso_lower"), 0f, a(6), a(7), z(briefAlt * 0.2f), 1f);
            set(b("shoulder_l"), -a(8), a(9), a(10),
                    z(footrotl * 0.07f).mul(y(0.15f)).mul(x(-0.2f + footrotl * -0.25f)), 1f);
            set(b("shoulder_r"), a(8), a(9), a(10),
                    z(footrotr * -0.07f).mul(y(-0.15f)).mul(x(-0.2f + footrotr * -0.25f)), 1f);
            set(b("hand_l"), -a(11), a(12), a(13),
                    x(0.3f + footrotl * -0.06f).mul(y(0.1f)).mul(z(-0.35f + footrotl * -0.1f)), 1.04f);
            set(b("hand_r"), a(11), a(12), a(13),
                    x(0.3f + footrotr * -0.06f).mul(y(-0.1f)).mul(z(0.35f + footrotr * 0.1f)), 1.04f);
            set(b("leg_l"), -a(14) * 1.02f, a(15) * 1.02f, a(16) * 1.02f,
                    x(footrotl * 0.3f).mul(y(0.1f)).mul(z(footrotl * -0.2f)), 1.02f);
            set(b("leg_r"), a(14) * 1.02f, a(15) * 1.02f, a(16) * 1.02f,
                    x(footrotr * 0.3f).mul(y(-0.1f)).mul(z(footrotr * 0.2f)), 1.02f);
            set(b("foot_l"), -a(17), a(18) + foothoril * 2f, a(19) + Math.max(footvertl * 3f, 0f),
                    x(footrotl * 0.2f).mul(y(-0.08f)), 1f);
            set(b("foot_r"), a(17), a(18) + foothorir * 2f, a(19) + Math.max(footvertr * 3f, 0f),
                    x(footrotr * 0.2f).mul(y(0.08f)), 1f);
        }

        private void runPerson(float t) {
            float speed = 9.4f;
            float speednorm = 1f;
            float acc = speed * t;
            float lab = 1f;
            float footrotl = shaped(0.5f, 5.5f, acc * 1.4f * lab + PI * 1.4f)
                    * (float) Math.sqrt(5.0);
            float footrotr = shaped(0.5f, 5.5f, acc * 1.4f * lab + PI * 0.4f)
                    * (float) Math.sqrt(5.0);
            float briefAlt = sin(acc * lab * 1.4f + PI / -2f);
            float fast = sin(t * 20f);
            float foothoril = sin(acc * 1.4f * lab + PI * 1.45f);
            float foothorir = sin(acc * 1.4f * lab + PI * 0.45f);
            float footvertl = sin(acc * 1.4f * lab);
            float footvertr = sin(acc * 1.4f * lab + PI);
            float brief = shaped(1.5f, 3.5f, acc * lab * 1.4f) * (float) Math.sqrt(5.0);
            float shortalt = sin(acc * lab * 1.4f + PI / 2f);
            float phase = (float) Math.floor(t + t / 18f);
            float lookX = sin(phase * 7331f) * 0.2f;
            float lookY = sin(phase * 1137f) * 0.1f;
            boolean wings = a(14) > 0.5f;
            set(b("head"), 0f, -1f + a(0), a(1) + brief * 0.1f,
                    z(lookX * 0.2f - brief * 0.02f).mul(x(lookY + 0.45f)), 1f);
            set(b("chest"), 0f, a(2), a(3) + 1f + shortalt * -0.8f,
                    z(brief * 0.06f).mul(x(briefAlt * 0.035f - 0.4f)), 1f);
            set(b("main"), 4f, -4f, -3f, y(-0.5f).mul(z(PI / 2f)), 1f);
            set(b("second"), -4f, -4f, -3f, y(0.5f).mul(z(-PI / 2f)), 1f);
            set(b("pants"), 0f, a(4), a(5), x(0.1f).mul(z(brief * 0.25f)), 1f);
            set(b("tail"), 0f, a(6), a(7), identity, 1f);
            if (wings) {
                set(b("hand_l"), -a(8), a(9) - 2f, a(10), x(0.4f), 1f);
                set(b("hand_r"), a(8), a(9) - 2f, a(10), x(0.4f), 1f);
                set(b("foot_l"), -a(11), a(12), a(13),
                        x(-0.6f - fast * 0.6f).mul(z(fast * 0.4f)), 1f);
                set(b("foot_r"), a(11), a(12), a(13),
                        x(-0.6f - fast * 0.6f).mul(z(fast * -0.4f)), 1f);
            } else {
                set(b("hand_l"), -a(8) + footrotr * -1.3f,
                        1f + a(9) + footrotr * -2.5f, a(10) - footrotr * 1.5f,
                        x(0.4f + footrotr * -1.2f).mul(y(footrotr * 0.4f)), 1f);
                set(b("hand_r"), a(8) + footrotl * 1.3f,
                        1f + a(9) + footrotl * -2.5f, a(10) - footrotl * 1.5f,
                        x(0.4f + footrotl * -1.2f).mul(y(footrotl * -0.4f)), 1f);
                set(b("foot_l"), -a(11),
                        a(12) + (-1f + footrotl * -2.5f),
                        a(13) + (2f + Math.max(footvertl * -1.1f, -1f)),
                        x(-0.2f + foothoril * -0.9f), 1f);
                set(b("foot_r"), a(11),
                        a(12) + (-1f + footrotr * -2.5f),
                        a(13) + (2f + Math.max(footvertr * -1.1f, -1f)),
                        x(-0.2f + foothorir * -0.9f), 1f);
            }
        }

        private void poseCrab(float t) {
            float scaler = a(AnimalAttrs.CR_SCALER);
            float arm = sin(t * 0.2f) * 0.03f;
            float claspL = clasp(t * 0.1f, sin(t * 2f));
            float claspR = clasp(t * 0.105f + 33f, sin(t * 1.95f + 20f));
            set(b("chest"), 0f, a(AnimalAttrs.CR_CHEST), a(AnimalAttrs.CR_CHEST + 1), identity, scaler);
            set(b("arm_l"), 0f, 0f, 0f, x(arm), 1f);
            set(b("arm_r"), 0f, 0f, 0f, x(arm), 1f);
            set(b("pincer_l1"), 0f, 0f, 0f, z(claspL * 0.15f), 1f);
            set(b("pincer_r1"), 0f, 0f, 0f, z(-claspR * 0.15f), 1f);
            bugLeg("leg_fl", "leg_fr", AnimalAttrs.CR_LEG_F, a(AnimalAttrs.CR_LEG_ORI));
            bugLeg("leg_cl", "leg_cr", AnimalAttrs.CR_LEG_C, a(AnimalAttrs.CR_LEG_ORI + 1));
            bugLeg("leg_bl", "leg_br", AnimalAttrs.CR_LEG_B, a(AnimalAttrs.CR_LEG_ORI + 2));
        }

        private void snap() {
            for (int i = 0; i < spec.names.length; i++) {
                curPos[i].set(tgtPos[i]);
                curScale[i].set(tgtScale[i]);
                curRot[i].set(tgtRot[i]);
            }
        }

        private void compose() {
            int ears = spec.bones.getOrDefault("ears", -1);
            int jaw = spec.bones.getOrDefault("jaw", -1);
            for (int i = 0; i < spec.names.length; i++) {
                local[i].set(curPos[i], curRot[i], curScale[i]);
            }
            for (int i = 0; i < spec.names.length; i++) {
                int parent = spec.parent[i];
                if (trunk && i == ears && jaw >= 0) {
                    parent = jaw;
                }
                if (parent < 0) {
                    bones[i].set(local[i]);
                } else {
                    bones[i].set(bones[parent]).mul(local[i]);
                }
            }
        }

        private void set(int bone, float x, float y, float z, Quaternion orientation, float size) {
            tgtPos[bone].set(x, y, z);
            tgtScale[bone].set(size, size, size);
            tgtRot[bone].set(orientation);
        }

        private int b(String name) {
            return spec.bones.get(name);
        }

        private float a(int index) {
            return attr[index];
        }

        private String speciesKind() {
            int slash = species.indexOf('/');
            return slash < 0 ? species : species.substring(0, slash);
        }

        private Quaternion x(float radians) {
            return qa.setFromAxisRad(1f, 0f, 0f, radians);
        }

        private Quaternion y(float radians) {
            return qb.setFromAxisRad(0f, 1f, 0f, radians);
        }

        private Quaternion z(float radians) {
            return qc.setFromAxisRad(0f, 0f, 1f, radians);
        }

        /** Middle of the animal at the start of the idle, so it stays in frame. */
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
        Spec spec = SPECS.get(assembly.body);
        if (spec == null) {
            return null;
        }
        float[] attr;
        int need;
        if ("biped_large".equals(assembly.body)) {
            attr = LargePose.local(assembly.species);
            if (attr == null) {
                return null;
            }
            need = attr.length;
        } else if ("golem".equals(assembly.body)) {
            attr = GolemPose.local(assembly.species);
            if (attr == null) {
                return null;
            }
            need = attr.length;
        } else if ("biped_small".equals(assembly.body)) {
            attr = FigurePose.local(assembly.species);
            need = attr.length;
        } else {
            attr = AnimalAttrs.get(assembly.body, assembly.species);
            need = switch (assembly.body) {
            case "quadruped_small" -> AnimalAttrs.QS_LEN;
            case "quadruped_medium" -> AnimalAttrs.QM_LEN;
            case "quadruped_low" -> AnimalAttrs.QL_LEN;
            case "theropod" -> AnimalAttrs.TH_LEN;
            case "bird_large" -> AnimalAttrs.BL_LEN;
            case "fish_small" -> AnimalAttrs.FS_LEN;
            case "fish_medium" -> AnimalAttrs.FM_LEN;
            case "dragon" -> AnimalAttrs.DR_LEN;
            case "arthropod" -> AnimalAttrs.AR_LEN;
            case "crustacean" -> AnimalAttrs.CR_LEN;
            default -> 0;
        };
        }
        if (attr.length < need) {
            return null;
        }
        Part[] parts = new Part[assembly.parts.size()];
        int n = 0;
        for (NpcFigure.Part part : assembly.parts) {
            Integer bone = spec.bones.get(boneKey(part));
            if (bone == null || part.file == null) {
                continue;
            }
            VoxReader.VoxScene scene = VoxReader.readScene(part.file.toFile());
            if (scene.models.size() <= part.model || scene.models.get(part.model).grid == null) {
                continue;
            }
            VoxReader.VoxModel model = scene.models.get(part.model);
            float ox = part.ox;
            if (part.mirror && part.tool != null) {
                ox = -part.ox - model.sizeX;
            }
            VoxMesher.VoxMesh mesh = VoxMesher.buildLocal(model, ox, part.oy, part.oz, part.mirror);
            if (mesh.parts.length == 0) {
                mesh.dispose();
                continue;
            }
            parts[n++] = new Part(mesh, bone);
        }
        if (n == 0) {
            return null;
        }
        Part[] trimmed = new Part[n];
        System.arraycopy(parts, 0, trimmed, 0, n);
        return new Rig(trimmed, spec, attr, assembly.species);
    }

    private static String boneKey(NpcFigure.Part part) {
        if ("hand".equals(part.bone)) {
            return part.left ? "hand_l" : "hand_r";
        }
        if ("foot".equals(part.bone)) {
            return part.left ? "foot_l" : "foot_r";
        }
        return part.bone;
    }

    private static float sin(float radians) {
        return (float) Math.sin(radians);
    }

    /** Almost a square wave, the same mix Veloren uses for bug footsteps. */
    private static float squareish(float wave) {
        float mag = (float) Math.pow(Math.abs(wave), 0.1);
        return mag * Math.signum(wave);
    }

    /** sqrt(1 / (a + b sin^2)) * sin, the step shape Veloren uses. */
    private static float shaped(float a, float b, float angle) {
        float s = sin(angle);
        return (float) Math.sqrt(1.0 / (a + b * s * s)) * s;
    }

    /** The idle look: a new angle every couple of seconds, from the clock. */
    private static float glance(float t, float scramble, float amount) {
        float phase = (float) Math.floor(t / 2.0 + t / 8.0);
        return sin(phase * scramble) * amount;
    }

    private static float clasp(float clock, float wave) {
        float frac = clock - (float) Math.floor(clock);
        float v = (frac * 2f - 1f) * wave * wave;
        return (float) Math.pow(v, 16);
    }

    private AnimalIdle() {
    }

    private static final class Spec {
        final String family;
        final String[] names;
        final int[] parent;
        final Map<String, Integer> bones = new HashMap<>();

        Spec(String family, String[] names, int[] parent) {
            this.family = family;
            this.names = names;
            this.parent = parent;
            for (int i = 0; i < names.length; i++) {
                bones.put(names[i], i);
            }
        }
    }
}
