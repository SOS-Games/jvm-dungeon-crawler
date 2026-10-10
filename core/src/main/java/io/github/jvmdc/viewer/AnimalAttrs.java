/*
 * Generated from Veloren voxygen/anim SkeletonAttr From impls.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.jvmdc.viewer;

import java.util.HashMap;
import java.util.Map;

/** Rest-pose numbers for each animal species. Idle motion reads these. */
final class AnimalAttrs {
    private AnimalAttrs() {}

    // quadruped_small
    static final int QS_HEAD = 0;
    static final int QS_CHEST = 2;
    static final int QS_FEET_F = 4;
    static final int QS_FEET_B = 7;
    static final int QS_TAIL = 10;
    static final int QS_SCALER = 12;
    static final int QS_TEMPO = 13;
    static final int QS_MAXIMIZE = 14;
    static final int QS_MINIMIZE = 15;
    static final int QS_SPRING = 16;
    static final int QS_FEED = 17;
    static final int QS_LATERAL = 18;
    static final int QS_LEN = 19;

    // quadruped_medium
    static final int QM_HEAD = 0;
    static final int QM_NECK = 2;
    static final int QM_JAW = 4;
    static final int QM_TAIL = 6;
    static final int QM_TORSO_FRONT = 8;
    static final int QM_TORSO_BACK = 10;
    static final int QM_EARS = 12;
    static final int QM_LEG_F = 14;
    static final int QM_LEG_B = 17;
    static final int QM_FEET_F = 20;
    static final int QM_FEET_B = 23;
    static final int QM_SCALER = 26;
    static final int QM_STARTANGLE = 27;
    static final int QM_TEMPO = 28;
    static final int QM_SPRING = 29;
    static final int QM_FEED = 30;
    static final int QM_LEN = 31;

    // quadruped_low
    static final int QL_HEAD_UPPER = 0;
    static final int QL_HEAD_LOWER = 2;
    static final int QL_SIDE_HEAD_LOWER = 4;
    static final int QL_SIDE_HEAD_UPPER = 7;
    static final int QL_JAW = 10;
    static final int QL_CHEST = 12;
    static final int QL_TAIL_REAR = 14;
    static final int QL_TAIL_REAR_LENGTH = 16;
    static final int QL_TAIL_FRONT = 17;
    static final int QL_FEET_F = 19;
    static final int QL_FEET_B = 22;
    static final int QL_LEAN = 25;
    static final int QL_SCALER = 27;
    static final int QL_TEMPO = 28;
    static final int QL_LEN = 29;

    // theropod
    static final int TH_HEAD = 0;
    static final int TH_JAW = 2;
    static final int TH_NECK = 4;
    static final int TH_CHEST_FRONT = 6;
    static final int TH_CHEST_BACK = 8;
    static final int TH_TAIL_FRONT = 10;
    static final int TH_TAIL_BACK = 12;
    static final int TH_HAND = 14;
    static final int TH_LEG = 17;
    static final int TH_FOOT = 20;
    static final int TH_SCALER = 23;
    static final int TH_LEN = 24;

    // bird_large
    static final int BL_CHEST = 0;
    static final int BL_NECK = 2;
    static final int BL_HEAD = 4;
    static final int BL_BEAK = 6;
    static final int BL_TAIL_FRONT = 8;
    static final int BL_TAIL_REAR = 10;
    static final int BL_WING_IN = 12;
    static final int BL_WING_MID = 15;
    static final int BL_WING_OUT = 18;
    static final int BL_LEG = 21;
    static final int BL_FOOT = 24;
    static final int BL_SCALER = 27;
    static final int BL_FEED = 28;
    static final int BL_LEN = 29;

    // fish_small
    static final int FS_CHEST = 0;
    static final int FS_TAIL = 2;
    static final int FS_FIN = 4;
    static final int FS_TEMPO = 7;
    static final int FS_AMPLITUDE = 8;
    static final int FS_LEN = 9;

    // fish_medium
    static final int FM_HEAD = 0;
    static final int FM_JAW = 2;
    static final int FM_CHEST_FRONT = 4;
    static final int FM_CHEST_BACK = 6;
    static final int FM_TAIL = 8;
    static final int FM_FIN = 10;
    static final int FM_TEMPO = 13;
    static final int FM_AMPLITUDE = 14;
    static final int FM_LEN = 15;

    // dragon
    static final int DR_HEAD_UPPER = 0;
    static final int DR_HEAD_LOWER = 2;
    static final int DR_JAW = 4;
    static final int DR_CHEST_FRONT = 6;
    static final int DR_CHEST_REAR = 8;
    static final int DR_TAIL_FRONT = 10;
    static final int DR_TAIL_REAR = 12;
    static final int DR_WING_IN = 14;
    static final int DR_WING_OUT = 17;
    static final int DR_FEET_F = 20;
    static final int DR_FEET_B = 23;
    static final int DR_HEIGHT = 26;
    static final int DR_LEN = 27;

    // arthropod
    static final int AR_HEAD = 0;
    static final int AR_CHEST = 2;
    static final int AR_MANDIBLE = 4;
    static final int AR_WING_F = 7;
    static final int AR_WING_B = 10;
    static final int AR_LEG_F = 13;
    static final int AR_LEG_FC = 16;
    static final int AR_LEG_BC = 19;
    static final int AR_LEG_B = 22;
    static final int AR_SCALER = 25;
    static final int AR_LEG_ORI = 26;
    static final int AR_SNAPPER = 30;
    static final int AR_LEN = 31;

    // crustacean
    static final int CR_CHEST = 0;
    static final int CR_ARM = 2;
    static final int CR_LEG_F = 5;
    static final int CR_LEG_C = 8;
    static final int CR_LEG_B = 11;
    static final int CR_LEG_ORI = 14;
    static final int CR_MOVE_SIDEWAYS = 17;
    static final int CR_SCALER = 18;
    static final int CR_LEN = 19;

    private static final Map<String, float[]> TABLE = new HashMap<>();
    static {
        put("quadruped_small/axolotl", new float[] {3f, 2f, 3f, 5f, 2f, 2f, -2f, 2f, -3f, -2f, -4f, -1f, 0.5000f, 1.2000f, 0.9000f, 0f, 0.8000f, 0.8000f, 1f});
        put("quadruped_small/batfox", new float[] {5f, 1f, -2f, 6f, 3f, 4f, -0.5000f, 3.5000f, -2f, -0.5000f, 0f, 5f, 0.9000f, 0f, 0f, 0f, 1.1000f, 0.7000f, 0f});
        put("quadruped_small/beaver", new float[] {5.5000f, 0f, 2f, 6f, 4.5000f, 4.5000f, -4f, 4f, -2.5000f, -3f, -6.5000f, -1f, 0f, 1.2000f, 0f, 0.9000f, 1.3000f, 0f, 0f});
        put("quadruped_small/boar", new float[] {7f, 0f, 0f, 9.5000f, 3.5000f, 6f, -5.5000f, 3f, -3f, -2.5000f, -6f, 0f, 0.9500f, 1.1000f, 1.4000f, 0f, 0.8000f, 0.6000f, 0f});
        put("quadruped_small/cat", new float[] {4f, 3f, 0f, 6f, 2f, 4f, -1f, 2f, -3.5000f, -1f, -3.5000f, 2f, 0.6700f, 1.1000f, 0f, 0.8000f, 1.4000f, 0f, 0f});
        put("quadruped_small/dog", new float[] {3f, 4.5000f, -2f, 8.5000f, 3.5000f, 3f, -2.5000f, 3f, -3.5000f, -2.5000f, -5f, 0.5000f, 0f, 0f, 0f, 0f, 0f, 0f, 0f});
        put("quadruped_small/fox", new float[] {4f, 3f, 0f, 8f, 3f, 5f, -5.5000f, 3f, -3f, -3f, -4.5000f, 2f, 0.7200f, 0f, 1.3000f, 1.3000f, 0f, 0f, 0f});
        put("quadruped_small/frog", new float[] {4f, 2f, -2f, 4.5000f, 4.5000f, 6.5000f, 0f, 5f, -3.5000f, 0f, 0f, 0f, 0.5600f, 1.1500f, 1.3000f, 0.8000f, 2.5000f, 0.7000f, 0f});
        put("quadruped_small/fungome", new float[] {1.5000f, -1.5000f, 4f, 4f, 3f, 2f, -1f, 3f, -3.5000f, -1f, -4f, -2f, 0.7200f, 0f, 0.7000f, 0.4000f, 0.8000f, 0.7000f, 1f});
        put("quadruped_small/gecko", new float[] {4f, 2f, 7.5000f, 4f, 2f, 4f, -0.5000f, 1.5000f, -2f, -0.5000f, -4f, 0f, 0.5600f, 1.1000f, 0f, 0f, 0.6000f, 0.8000f, 1f});
        put("quadruped_small/goat", new float[] {3.5000f, 4f, 2f, 7.5000f, 3f, 2.5000f, -3.5000f, 3f, -4f, -2f, -7f, 0f, 0f, 0f, 0f, 0.8000f, 1.2000f, 0f, 0f});
        put("quadruped_small/hare/female", new float[] {2.5000f, 3f, -2f, 6f, 3f, 0.5000f, -4f, 3.5000f, -3f, -2f, -4f, 2f, 0.6500f, 1.1500f, 1.3000f, 0.8000f, 2.5000f, 1.2000f, 0f});
        put("quadruped_small/hare/male", new float[] {3f, 2f, -2f, 7f, 3f, 1f, -3f, 3.5000f, -1f, -2f, -4f, -1f, 0.6500f, 1.1500f, 1.3000f, 0.8000f, 2.2000f, 1.2000f, 0f});
        put("quadruped_small/holladon", new float[] {7f, 1f, -2f, 9f, 5f, 4f, -2.5000f, 4f, -2f, -3f, -1f, 4f, 1.1200f, 0f, 0f, 0.7000f, 0.7000f, 0f, 0f});
        put("quadruped_small/hyena", new float[] {7.5000f, 2f, -2f, 9f, 2.5000f, 5f, -4f, 3f, -5f, -2.5000f, -7f, 0f, 0.9500f, 1.1000f, 1.4000f, 1.4000f, 1.4000f, 0f, 0f});
        put("quadruped_small/jackalope", new float[] {3f, 2f, -2f, 6f, 3f, 4f, -2f, 3.5000f, -2f, 0f, -4f, 2f, 0.6700f, 0f, 1.2000f, 0.8000f, 2.2000f, 0f, 0f});
        put("quadruped_small/mossysnail", new float[] {2f, 2f, -2f, 4.5000f, 4.5000f, 6.5000f, 0f, 5f, -3.5000f, 0f, 0f, 0f, 1f, 0.5000f, 0f, 0f, 0f, 0f, 0f});
        put("quadruped_small/pig", new float[] {5f, 2f, 0f, 6f, 4.5000f, 3.5000f, -1f, 3.5000f, -2f, 0f, -4.5000f, 2.5000f, 0.7200f, 0f, 0f, 0.6000f, 0f, 0f, 0f});
        put("quadruped_small/porcupine", new float[] {6f, 1f, 2f, 11f, 4f, 6.5000f, -9f, 4.5000f, -1f, -8f, -6f, 1f, 0.6500f, 1.2000f, 0f, 0.9000f, 1.3000f, 0f, 0f});
        put("quadruped_small/quokka", new float[] {6f, 2f, 2f, 6.5000f, 3f, 4f, -1f, 4f, -4f, -1f, -6f, 1f, 0f, 1.2000f, 0f, 0.9000f, 1.3000f, 0f, 0f});
        put("quadruped_small/rabbit", new float[] {4f, 3f, -2f, 6f, 3f, 3f, -3f, 3.5000f, -2f, -1f, -4f, 0f, 0.5600f, 1.1500f, 1.3000f, 0.8000f, 2.5000f, 1.2000f, 0f});
        put("quadruped_small/raccoon", new float[] {5f, 2f, 0f, 5.5000f, 4f, 4f, 0f, 4.5000f, -3f, 0.5000f, -4f, 1f, 0f, 0f, 0f, 0f, 1.1000f, 0.8000f, 0f});
        put("quadruped_small/rat", new float[] {5f, -1f, 6f, 5f, 3f, 2.5000f, -1f, 3f, -2f, 1f, -3f, 0f, 0.5000f, 1f, 0f, 0f, 0f, 0f, 0f});
        put("quadruped_small/seal", new float[] {4f, 2.5000f, -2f, 4f, 6.5000f, 3f, -2f, 4.5000f, -6f, -0.5000f, -1f, 4f, 0.9000f, 2.5000f, 0f, 0.7000f, 0.7000f, 0f, 0f});
        put("quadruped_small/sheep", new float[] {4f, 4f, 2f, 7f, 3.5000f, 2f, -2f, 3.5000f, -3.5000f, -2f, -5f, 0f, 0f, 0f, 1.1000f, 0.8000f, 1.2000f, 0f, 0f});
        put("quadruped_small/skunk", new float[] {5f, 1.5000f, 0f, 6f, 3.5000f, 4f, -1f, 3.5000f, -4f, -1.5000f, -4f, 0.5000f, 0.7200f, 0f, 0f, 0.9000f, 0f, 0.8000f, 0f});
        put("quadruped_small/squirrel", new float[] {3.5000f, 1f, 4f, 5f, 3.5000f, 3f, -1f, 3.5000f, -3f, 0f, -4f, 0f, 0.4000f, 1.1500f, 0f, 0f, 0f, 0f, 0f});
        put("quadruped_small/treantsapling", new float[] {5f, -2f, 1f, 13f, 5f, 4f, -10f, 5.5000f, -4f, -10f, -6f, -2f, 0f, 3f, 0f, 0.7000f, 0.5000f, 0f, 1f});
        put("quadruped_small/truffler", new float[] {7.5000f, -9f, -2f, 16f, 2.5000f, 5f, -9f, 3f, -5f, -9.5000f, 0f, 0f, 0f, 0f, 0f, 0f, 0.8000f, 0.6000f, 0f});
        put("quadruped_small/turtle", new float[] {5f, -2f, 1f, 6f, 5f, 4f, -2f, 5.5000f, -2.5000f, -2f, -6f, -2f, 0.6700f, 3f, 0.8000f, 0.8000f, 0.7000f, 0.5000f, 1f});
        put("quadruped_medium/akhlut", new float[] {1f, 3.5000f, 8.5000f, -1f, 0f, -4.5000f, -14f, -2f, 5.5000f, 14.5000f, -7f, 1f, 12f, -3f, 8f, -2f, 0.5000f, 6f, -7.5000f, -2f, 0f, 0f, -5f, 1.5000f, -1f, -4.5000f, 1.4000f, 0f, 0.9500f, 0f, 0f});
        put("quadruped_medium/alpaca", new float[] {0.5000f, 7.5000f, -1.5000f, 3f, 3f, -2.5000f, -8.5000f, 3.5000f, 7f, 11.5000f, -6f, 0f, 1f, 2f, 3.5000f, -2.5000f, -0.5000f, 3.5000f, -7f, 0f, 0f, -0.5000f, -5f, -0.5000f, -0.5000f, -5.5000f, 0f, 0f, 0f, 0f, 0f});
        put("quadruped_medium/antelope", new float[] {1.5000f, 2.5000f, 0.5000f, 2.5000f, 3f, 0.5000f, -10f, 2f, 10f, 14f, -7f, 0f, 2.5000f, 5f, 4f, -4.5000f, -2.5000f, 3.5000f, -7.5000f, -3.5000f, -0.5000f, 0f, -3.5000f, -0.5000f, -1.5000f, -3.5000f, 0.9500f, 0f, 0f, 1.2000f, 0.9000f});
        put("quadruped_medium/barghest", new float[] {0.5000f, -2.5000f, 0.5000f, -0.5000f, 6.5000f, -3f, -7f, -4f, 11.5000f, 15.5000f, -9f, -1.5000f, 12f, -3f, 9.5000f, 0f, -2.5000f, 7f, -3.5000f, -5.5000f, 2f, 2.5000f, -6f, 0.5000f, 1f, -4.5000f, 1.2000f, -0.5000f, 0.9500f, 0f, 0f});
        put("quadruped_medium/bear", new float[] {0.5000f, 1.5000f, 0.5000f, 0f, 3.5000f, -2f, -10f, -0.5000f, 7f, 14.5000f, -11f, -0.5000f, 1f, 4f, 5.5000f, -4.5000f, -3.5000f, 6.5000f, -6.5000f, -2f, 0f, 2f, -5.5000f, 0.5000f, 0.5000f, -6f, 1.4000f, 0f, 0.8000f, 0f, 0f});
        put("quadruped_medium/bonerattler", new float[] {0f, 1.5000f, 7f, -0.5000f, 3f, -2.5000f, -10f, 1.5000f, 6f, 11f, -5f, 0f, 2f, 3.5000f, 5.5000f, 5f, -2.5000f, 6f, -8f, -2.5000f, -0.5000f, -3f, -2.5000f, 0f, 3f, -2.5000f, 0f, -0.7000f, 0f, 1.1000f, 0f});
        put("quadruped_medium/bristleback", new float[] {-3f, -2f, 6f, 2.5000f, 8f, -6f, -7f, -5.5000f, 1.5000f, 9f, -4f, 2f, 6f, 1f, 6f, 1f, -2f, 4.5000f, -3f, -2f, 0f, -0.5000f, -2f, -0.5000f, 0f, -4f, 1.1000f, 0f, 0f, 0f, 0f});
        put("quadruped_medium/camel", new float[] {-0.5000f, 5f, 3.5000f, -1.5000f, 2f, 2.5000f, -14f, -1f, 11f, 22.5000f, -12f, -0.5000f, 2.5000f, 5f, 4.5000f, -3.5000f, -5.5000f, 4.5000f, -10.5000f, -5f, 0f, 0f, -8f, 0f, 0.5000f, -9f, 1.1500f, 0f, 1.8000f, 0.8500f, 0f});
        put("quadruped_medium/catoblepas", new float[] {-1f, -6.5000f, 19.5000f, -2f, 1f, -3.5000f, -8f, -2f, 7.5000f, 19.5000f, -8.5000f, -4.5000f, 11f, -3f, 7f, 2f, -5f, 6f, -2.5000f, -2.5000f, 1f, 0f, -6f, 0.5000f, 0.5000f, -4f, 1.0500f, -0.5000f, 1.1000f, 0.5500f, 0f});
        put("quadruped_medium/cattle/female", new float[] {2.5000f, 4f, 0f, 0f, 5f, -5f, -8f, 5.5000f, 7f, 14.5000f, -10f, -2f, 2f, -1.5000f, 5.5000f, -2.5000f, -1f, 4f, -6.5000f, 0f, -0.5000f, 0.5000f, -5.5000f, -0.5000f, -1f, -3.5000f, 1.2500f, 0f, 0.8000f, 0f, 0f});
        put("quadruped_medium/cattle/male", new float[] {2f, 3.5000f, 0f, 0f, 5f, -5.5000f, -8f, 3.5000f, 7f, 15.5000f, -8f, -0.5000f, 2f, -1.5000f, 5.5000f, -2f, -2.5000f, 4f, -7f, -1f, -0.5000f, 1f, -5f, -0.5000f, -0.5000f, -5f, 1.2500f, 0f, 0.8000f, 0f, 0f});
        put("quadruped_medium/claysteed", new float[] {-0.5000f, 6f, 1.5000f, 1.5000f, 4f, -1f, -11f, 4f, 7f, 15f, -6f, 0f, 1f, 3.5000f, 4f, -1.5000f, -2f, 4.5000f, -8f, -3f, -0.5000f, 0f, -6f, 0f, -0.5000f, -4f, 1.7500f, 0f, 0f, 0f, 0f});
        put("quadruped_medium/darkhound", new float[] {3f, -1f, 1f, 1.5000f, 2f, -2f, -9f, -3f, 7f, 14f, -12f, 0.5000f, 1f, 2.5000f, 4f, -6.5000f, -2f, 4f, -6.5000f, -3f, 0f, 0.5000f, -4f, 0f, -1f, -3.5000f, 0f, 0f, 1.1000f, 1.2000f, 0f});
        put("quadruped_medium/deer", new float[] {1.5000f, 3.5000f, -2.5000f, 1f, 3.5000f, 2.5000f, -8.5000f, 0.5000f, 11f, 13.5000f, -9f, 0.5000f, 2.5000f, 5f, 3.5000f, -4.5000f, -3.5000f, 3f, -6.5000f, -3.5000f, -0.5000f, -0.5000f, -2.5000f, -1f, -0.5000f, -2f, 0f, 0f, 0.8500f, 0.9000f, 1f});
        put("quadruped_medium/donkey", new float[] {4.5000f, -3f, 1f, 3.5000f, 1f, 1f, -11f, 1.5000f, 10f, 15.5000f, -6f, -1f, -1f, 8f, 4f, -3.5000f, -4f, 4f, -9f, -3f, 0.5000f, 1f, -3.5000f, 0.5000f, -1f, -3.5000f, 0.9500f, 0f, 0.8500f, 0.8500f, 1f});
        put("quadruped_medium/dreadhorn", new float[] {-2.5000f, 7f, 0.5000f, 0f, 7f, -5f, -5.5000f, 1.5000f, 1.5000f, 15.5000f, -20f, -1f, 1.5000f, 3f, 8.5000f, -7f, -0.5000f, 6f, 0f, -3f, -0.5000f, 0.5000f, -5f, -0.5000f, 0.5000f, -3.5000f, 1.6000f, 0f, 0f, 0.8500f, 0f});
        put("quadruped_medium/elephant", new float[] {0.5000f, -1.5000f, 0.5000f, -0.5000f, 9.5000f, -3f, -13f, -1.5000f, 11.5000f, 20.5000f, -13f, -2.5000f, 0f, 0f, 10f, -5f, -5f, 7.5000f, -7f, -5f, -0.5000f, -0.5000f, -6f, 0.5000f, -0.5000f, -4.5000f, 3f, 0f, 0.2500f, 0f, 0f});
        put("quadruped_medium/frostfang", new float[] {1f, -2f, 0.5000f, 1.5000f, 4f, -2.5000f, -7f, -3.5000f, 9f, 11.5000f, -10.5000f, 0f, 2f, 3.5000f, 5.5000f, -5.5000f, -2f, 3.5000f, -4.5000f, -2f, 0.5000f, 1.5000f, -3.5000f, 0f, -1.5000f, -3.5000f, 1f, 0f, 0f, 0f, 0f});
        put("quadruped_medium/grolgar", new float[] {0f, -1f, 1f, -1f, 7f, 2f, -11.5000f, -0.5000f, 10f, 13f, -10f, 1.5000f, 5f, 8f, 7.5000f, -5.5000f, -1f, 6f, -6.5000f, -4f, 0f, 0f, -4f, 0.5000f, -1.5000f, -3f, 1.0500f, -0.3000f, 0.8500f, 0.9000f, 0f});
        put("quadruped_medium/highland", new float[] {2.5000f, 5f, 0f, 1.5000f, 5f, -6f, -9f, 5f, 7f, 12.5000f, -8f, -0.5000f, 2f, -1.5000f, 5.5000f, -2.5000f, 0f, 4.5000f, -7f, 0f, -0.5000f, 0.5000f, -4.5000f, -0.5000f, -0.5000f, -3f, 1.3200f, 0f, 0.8000f, 0f, 0f});
        put("quadruped_medium/hirdrasil", new float[] {0f, 5f, -1f, 0.5000f, 2.5000f, 3f, -11f, 2f, 11f, 14.5000f, -9f, -0.5000f, 2.5000f, 5f, 4.5000f, -5f, -2.5000f, 4f, -6.5000f, -3f, -0.5000f, -3f, -3.5000f, -1f, -2f, -4.5000f, 0f, 0f, 0.8500f, 1.1000f, 0.9000f});
        put("quadruped_medium/horse", new float[] {4.5000f, 2.5000f, -2.5000f, -1.5000f, 4f, 1f, -9f, 1.5000f, 7f, 16f, -8f, -1.5000f, 1f, 7f, 4.5000f, -2.5000f, -3f, 3.5000f, -7f, -2f, -0.5000f, 0.5000f, -5f, 0.5000f, -1.5000f, -3.5000f, 1.2000f, 0f, 0.8500f, 0.8500f, 0.8500f});
        put("quadruped_medium/kelpie", new float[] {4f, -1f, 2f, 1f, 1f, 1f, -9f, 3f, 10f, 16f, -8f, -1f, 1f, 7.5000f, 4.5000f, -3.5000f, -3.5000f, 3.5000f, -7f, -2.5000f, -0.5000f, 0.5000f, -4.5000f, 0.5000f, -0.5000f, -3.5000f, 1.1000f, 0f, 0.8500f, 0.9500f, 1f});
        put("quadruped_medium/lion/female", new float[] {2.5000f, -2f, -2f, 8.5000f, 3.5000f, -4f, -11f, 1f, 10f, 13.5000f, -12f, -0.5000f, 2f, 1f, 6.5000f, -6.5000f, -1.5000f, 6f, -5f, -1.5000f, 0.5000f, 0.5000f, -3.5000f, 0.5000f, -1f, -3f, 1.0500f, -0.1000f, 0.9500f, 0f, 0f});
        put("quadruped_medium/lion/male", new float[] {4.5000f, 2f, -1.5000f, 1f, 3.5000f, -4f, -11f, 1f, 10f, 13f, -12f, -0.5000f, 2f, 3.5000f, 6.5000f, -6.5000f, -1.5000f, 6f, -5f, -1.5000f, 0.5000f, 0.5000f, -3.5000f, 0.5000f, -1f, -3f, 1.0500f, -0.1000f, 0.9500f, 0f, 0f});
        put("quadruped_medium/llama", new float[] {0.5000f, 10f, 2.5000f, 4.5000f, 4f, -1f, -9f, 6f, 7f, 15f, -8f, 0f, 1f, 3.5000f, 5f, -1.5000f, -1f, 5f, -7f, -2f, -0.5000f, 0.5000f, -6f, 0.5000f, -1.5000f, -3.5000f, 0f, 0f, 0f, 0f, 0f});
        put("quadruped_medium/mammoth", new float[] {0.5000f, -1.5000f, 0.5000f, -0.5000f, 9.5000f, -3f, -13f, -1.5000f, 11.5000f, 20.5000f, -13f, -2.5000f, 12f, -3f, 10f, -5f, -5f, 7.5000f, -7f, -5f, -0.5000f, -0.5000f, -6f, 0.5000f, -0.5000f, -4.5000f, 3f, 0f, 0f, 0f, 0f});
        put("quadruped_medium/moose/female", new float[] {3.5000f, 0.5000f, -0.5000f, 0.5000f, 6f, -2.5000f, -12.5000f, 3.5000f, 1.5000f, 19.5000f, -10f, -1f, 2f, 4.5000f, 5.5000f, -4f, 1f, 4.5000f, -10f, -2f, -1f, 1.5000f, -9.5000f, -1f, 0f, -6.5000f, 0.9500f, 0f, 0f, 0.9000f, 0f});
        put("quadruped_medium/moose/male", new float[] {-0.5000f, 5f, -0.5000f, 0.5000f, 10f, -7f, -12.5000f, 3.5000f, 1.5000f, 19.5000f, -10f, -1f, 6f, 1f, 5.5000f, -4f, 1f, 4.5000f, -10f, -2f, -1f, 1.5000f, -9.5000f, -1f, 0f, -6.5000f, 0.9500f, 0f, 0f, 0.9000f, 0f});
        put("quadruped_medium/mouflon", new float[] {0.5000f, 1.5000f, -1f, 1f, 6f, 1f, -10.5000f, 3f, 11f, 14f, -8.5000f, -0.5000f, 2.5000f, 5f, 4f, -5f, -4f, 3.5000f, -8f, -3.5000f, -0.5000f, -0.5000f, -3f, -1f, 0f, -0.5000f, 0f, 0f, 0.8500f, 0.9000f, 0.7000f});
        put("quadruped_medium/ngoubou", new float[] {0.5000f, -2.5000f, 2f, 1f, 1.5000f, -4f, -12f, 5.5000f, 9.5000f, 16.5000f, -8f, -2f, 12f, -3f, 7.5000f, -4f, -1.5000f, 4.5000f, -9.5000f, 0f, -1f, 0.5000f, -6f, 0.5000f, 1f, -5.5000f, 1.2000f, 0f, 0f, 0f, 0f});
        put("quadruped_medium/panda", new float[] {0f, 0.5000f, 0.5000f, 0f, 3f, -3f, -9.5000f, 0f, 7f, 13.5000f, -11f, -0.5000f, 1f, 4f, 7.5000f, -5.5000f, -2f, 7f, -7f, -2f, -1f, 2f, -4.5000f, -0.5000f, 0.5000f, -5f, 0f, 0f, 0f, 0f, 0f});
        put("quadruped_medium/roshwalr", new float[] {1f, 0.5000f, 0f, 1f, 4f, -1f, -8.5000f, -1f, 6f, 12.5000f, -9f, -3.5000f, 5f, 8f, 8f, -2.5000f, -2.5000f, 7f, -7f, -2.5000f, 0.5000f, 0f, -3f, 0.5000f, -1f, -3.5000f, 1.7500f, -0.3000f, 0.7500f, 0f, 0f});
        put("quadruped_medium/saber", new float[] {5f, -3f, -3.5000f, -2f, 2.5000f, -2f, -11f, 0f, 14f, 13f, -13.5000f, 0f, 3f, 5.5000f, 7f, -4f, -2.5000f, 6f, -7f, -3.5000f, 1f, -3.5000f, -2.5000f, 1f, -1f, -1f, 0.9000f, -0.2000f, 1.1000f, 0.9000f, 0f});
        put("quadruped_medium/snowleopard", new float[] {1.5000f, 0.5000f, 0f, 1.5000f, 3f, -3f, -10.5000f, 3f, 1.5000f, 13f, -11f, 0f, 1.5000f, 3f, 6.5000f, -4f, -2.5000f, 5.5000f, -5f, -1.5000f, 0.5000f, 0.5000f, -4.5000f, 0.5000f, 0.5000f, -5.5000f, 0.9500f, 0f, 0f, 1.1000f, 0f});
        put("quadruped_medium/tarasque", new float[] {-4f, 3.5000f, -1.5000f, -4f, 9f, -9.5000f, -11f, 0f, 11.5000f, 17.5000f, -14f, -1f, 12f, -3f, 7f, -8f, -6f, 6f, -6.5000f, -6.5000f, 1f, 0f, -3f, 1.5000f, -1f, -2.5000f, 1.0500f, -0.5000f, 0.9500f, 0f, 0f});
        put("quadruped_medium/tiger", new float[] {2f, 1f, 0f, 0f, 3f, -3.5000f, -13.5000f, 3f, 10f, 13f, -13f, -0.5000f, 2.5000f, 4f, 6f, -6f, -1.5000f, 6f, -7f, -1f, 0.5000f, 0f, -4.5000f, 0.5000f, -1f, -4f, 0.9500f, 0f, 0f, 0f, 0f});
        put("quadruped_medium/tuskram", new float[] {0f, 0f, -1f, 1f, 4f, -5f, -8f, 2f, 10f, 16f, -12f, -2.5000f, 0f, 0f, 8.5000f, -4.5000f, -2f, 6f, -5.5000f, -2.5000f, -1f, -1.5000f, -6f, 0.5000f, -1f, -3f, 0.9500f, 0.3000f, 0.9000f, 0.9000f, 0.5000f});
        put("quadruped_medium/wolf", new float[] {1.5000f, 3f, -4.5000f, 2f, 5f, -2.5000f, -11f, 0f, 12f, 13f, -12.5000f, 1f, 3f, 2.5000f, 4.5000f, -6.5000f, -1.5000f, 5f, -6.5000f, -3f, 0.5000f, 0f, -2f, 0f, -1f, -1.5000f, 0f, 0f, 1.1000f, 1.2000f, 0f});
        put("quadruped_medium/yak", new float[] {2.5000f, 5f, 0f, 0f, 6f, -8f, -8f, 2.5000f, 7f, 15.5000f, -8f, -0.5000f, 3f, -5f, 4.5000f, -2f, -1.5000f, 4.5000f, -6f, -1f, -0.5000f, 0f, -5f, -0.5000f, -0.5000f, -5f, 1.4000f, 0f, 0.8000f, 0f, 0f});
        put("quadruped_medium/zebra", new float[] {3f, -2f, 1f, 3.5000f, 4f, 0f, -10f, 1.5000f, 10f, 16.5000f, -6f, -1f, 0f, 7f, 4f, -2.5000f, -4.5000f, 3.5000f, -8f, -3.5000f, -0.5000f, 0.5000f, -4f, 0.5000f, -1f, -3f, 1.0500f, 0f, 0.8500f, 0.8500f, 1f});
        put("quadruped_low/alligator", new float[] {0.5000f, 2f, 9f, 0.2500f, 0f, 0f, 0f, 0f, 0f, 0f, 2.5000f, -2f, 0f, 5f, -13f, -1f, 1f, -5f, 0f, 4.5000f, 4.2500f, -1f, 4.5000f, -5.5000f, -1f, 0f, 0f, 1.1200f, 0.7000f});
        put("quadruped_low/asp", new float[] {4.5000f, 10.5000f, 6f, -2.5000f, 0f, 0f, 0f, 0f, 0f, 0f, 2f, -2f, 0f, 8f, -14f, -2f, 1f, -6f, -2f, 1.5000f, 4f, -1f, 2.5000f, -5.5000f, -1f, 0f, 0f, 1.1200f, 0f});
        put("quadruped_low/basilisk", new float[] {5f, 2.5000f, 12.5000f, -5.5000f, 0f, 0f, 0f, 0f, 0f, 0f, 0.5000f, -3f, 0f, 15f, -10f, -4f, 1f, -6.5000f, -5.5000f, 6.5000f, 4f, -2f, 5.5000f, -6.5000f, -2f, 0f, 0f, 1.3000f, 0.8000f});
        put("quadruped_low/crocodile", new float[] {1.5000f, 2f, 8f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 2.5000f, -3f, 0f, 5f, -12.5000f, -1f, 1f, -6f, 0f, 3.5000f, 6f, -1f, 3.5000f, -6f, -1f, 0f, 0f, 1.0500f, 0.7000f});
        put("quadruped_low/dagon", new float[] {8f, 10f, 12f, -6f, 0f, 0f, 0f, 0f, 0f, 0f, 2f, -2f, 0f, 13.5000f, -9f, -2f, 1f, -9f, -2f, 4.5000f, 2f, -4.5000f, 3.5000f, -8f, -4.5000f, 0f, 0f, 1.0500f, 1.2000f});
        put("quadruped_low/deadwood", new float[] {2f, -3f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, -1f, 4f, 0f, 12f, -15f, 4f, 1f, -1f, 4f, 3.5000f, 4f, -5f, 3.5000f, -6f, -5f, 0f, 0f, 0f, 0f});
        put("quadruped_low/driggle", new float[] {3f, 4f, 6f, -3f, 0f, 0f, 0f, 0f, 0f, 0f, -2f, -5f, 0f, 8f, -4f, 0f, 1f, -5.5000f, -4f, 4.5000f, 2.5000f, -4f, 3.5000f, -3.5000f, -5f, 0f, 0f, 0f, 0f});
        put("quadruped_low/elbst", new float[] {0.5000f, 1f, 9f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0.5000f, -1f, 0f, 5f, -6.5000f, 0f, 1f, -7f, 0f, 5f, 4.5000f, -2f, 3f, -6f, -2f, 0f, 0f, 1.1200f, 0.8500f});
        put("quadruped_low/hakulaq", new float[] {8f, 10f, 10.5000f, 1f, 0f, 0f, 0f, 0f, 0f, 0f, -6.5000f, -4f, 0f, 13.5000f, -9f, -2f, 1f, -6f, -5.5000f, 4.5000f, 2f, -4.5000f, 3.5000f, -8f, -4.5000f, 0f, 0f, 1.0500f, 1.2000f});
        put("quadruped_low/hydra", new float[] {12f, 19f, 8f, -6.5000f, 9f, 10f, -6.5000f, 1f, 7f, 17f, 1f, -2f, 0f, 16f, -16f, -1f, 10f, -14f, -7.5000f, 13f, 7f, -3f, 5f, -6.5000f, -5f, 0f, 0f, 1.5000f, 0.6000f});
        put("quadruped_low/icedrake", new float[] {7f, 8f, 11.5000f, -6f, 0f, 0f, 0f, 0f, 0f, 0f, -0.5000f, -8f, 0f, 16.5000f, -12f, 1f, 1f, -7f, -4.5000f, 4.5000f, 4f, -6.5000f, 3.5000f, -8f, -6.5000f, 0f, 0f, 1.1200f, 1.1000f});
        put("quadruped_low/lavadrake", new float[] {7f, 8f, 9f, -6f, 0f, 0f, 0f, 0f, 0f, 0f, 3f, -5f, 0f, 16.5000f, -12f, -2f, 1f, -7f, -4.5000f, 4.5000f, 4f, -6.5000f, 3.5000f, -8f, -6.5000f, 0f, 0f, 1.1200f, 1.1000f});
        put("quadruped_low/maneater", new float[] {7f, 11.5000f, 1f, 4.5000f, 0f, 0f, 0f, 0f, 0f, 0f, -1f, 4f, 0f, 12f, -15f, 4f, 1f, -1f, 4f, 4.5000f, 4f, -5.5000f, 4.5000f, -2.5000f, -3f, 0f, 0f, 1.1200f, 0.9000f});
        put("quadruped_low/monitor", new float[] {5.5000f, 3f, 7f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 3f, -1f, 0f, 5f, -12f, 0f, 1f, -6.5000f, 0f, 3f, 5f, 0f, 2.5000f, -6.5000f, 0f, 0f, 0f, 0.9000f, 1.4000f});
        put("quadruped_low/mossdrake", new float[] {7f, 8f, 9f, -6f, 0f, 0f, 0f, 0f, 0f, 0f, 3f, -5f, 0f, 16.5000f, -12f, -2f, 1f, -7f, -4.5000f, 4.5000f, 4f, -6.5000f, 3.5000f, -8f, -6.5000f, 0f, 0f, 1.1200f, 1.1000f});
        put("quadruped_low/pangolin", new float[] {-0.5000f, 8f, 8f, -9f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 7f, -7f, -3f, 1f, -7.5000f, -0.5000f, 5.5000f, 5.5000f, -1f, 6.5000f, -3.5000f, -1f, 0.4000f, 0f, 1.0500f, 1.1500f});
        put("quadruped_low/reefsnapper", new float[] {6f, 0.5000f, 12f, -9f, 0f, 0f, 0f, 0f, 0f, 0f, -5f, -1.5000f, 0f, 18.5000f, -14.5000f, -2f, 1f, -13.5000f, -6.5000f, 7.5000f, 5f, -8.5000f, 8f, -12f, -9.5000f, 0f, 0f, 1.1200f, 0.7000f});
        put("quadruped_low/rocksnapper", new float[] {6f, 0.5000f, 12f, -9f, 0f, 0f, 0f, 0f, 0f, 0f, -5f, -1.5000f, 0f, 18.5000f, -14.5000f, -2f, 1f, -13.5000f, -6.5000f, 7.5000f, 5f, -8.5000f, 8f, -12f, -9.5000f, 0f, 0f, 1.1200f, 0.7000f});
        put("quadruped_low/rootsnapper", new float[] {6f, 0.5000f, 12f, -9f, 0f, 0f, 0f, 0f, 0f, 0f, -5f, -1.5000f, 0f, 18.5000f, -14.5000f, -2f, 1f, -13.5000f, -6.5000f, 7.5000f, 5f, -8.5000f, 8f, -12f, -9.5000f, 0f, 0f, 1.1200f, 0.7000f});
        put("quadruped_low/salamander", new float[] {0.5000f, 1f, 9f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0.5000f, -1f, 0f, 5f, -6.5000f, 0f, 1f, -7.5000f, 0f, 5f, 4.5000f, -2f, 3f, -6f, -2f, 0f, 0f, 1.1200f, 0.8500f});
        put("quadruped_low/sandshark", new float[] {8.5000f, 0.5000f, 13.5000f, -10.5000f, 0f, 0f, 0f, 0f, 0f, 0f, -8f, -5.5000f, 0f, 20f, -10f, 0.5000f, 1f, -13f, -8f, 5.5000f, 2f, -8f, 3.5000f, -15f, -14f, 0f, 0f, 0f, 0f});
        put("quadruped_low/seacrocodile", new float[] {1.5000f, 2f, 8f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 2.5000f, -3f, 0f, 5f, -12.5000f, -1f, 1f, -6f, 0f, 3.5000f, 6f, -1f, 3.5000f, -6f, -1f, 0f, 0f, 1.0500f, 0.7000f});
        put("quadruped_low/snaretongue", new float[] {7f, 5.5000f, 8.5000f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, -7f, -7f, -8f, 9f, 5f, 0f, 1f, 5f, -2f, 6.5000f, 6.5000f, 1f, 1.5000f, 1.5000f, 2f, 0f, 0f, 1f, 0.7000f});
        put("quadruped_low/tortoise", new float[] {5f, 1f, 12f, -3.5000f, 0f, 0f, 0f, 0f, 0f, 0f, -3.5000f, -2f, 0f, 11f, -10f, -1.5000f, 1f, -13f, -3.5000f, 5.5000f, 6.5000f, -3f, 5.5000f, -11.5000f, -3f, 0f, 0f, 0f, 0.7000f});
        put("theropod/archaeos", new float[] {8f, 4f, 1f, -7f, 4.5000f, -2f, 0f, 20f, -5.5000f, -1f, -9f, -1.5000f, -8f, -0.5000f, 3f, 0f, -4f, 2.5000f, -3f, -4f, 3f, -0.5000f, -7f, 2.9300f});
        put("theropod/axebeak", new float[] {11.5000f, 8.5000f, 2.5000f, -4f, -5.5000f, 0f, 0f, 12f, -5f, 0f, -5.5000f, 4.5000f, -10f, 3f, 1.5000f, -10.5000f, 9.5000f, 2.5000f, -0.5000f, 0f, 2.5000f, 2.5000f, -7f, 1.1000f});
        put("theropod/dodarock", new float[] {2f, 1.5000f, 0f, -5f, 5f, -1f, 0f, 12f, -4.5000f, -2f, -4.5000f, -4.5000f, -8.5000f, -2f, 3.5000f, 3f, -5f, 3.5000f, 1.5000f, -4f, 1.5000f, -1f, -3.5000f, 1.1000f});
        put("theropod/ntouka", new float[] {2f, 2.5000f, 0f, -4f, 4f, 0f, 0f, 13f, -4.5000f, 1f, -9.5000f, -3.5000f, -9.5000f, -2f, 3.5000f, 3f, -4f, 4.5000f, -5.5000f, -4f, 1.5000f, -1f, -2.5000f, 2.9300f});
        put("theropod/odonto", new float[] {2f, 2f, 2f, -7f, 4f, 0f, 0f, 13f, -5f, 2f, -7f, -1f, -8f, 0.5000f, 3.5000f, 3f, -4f, 3.5000f, -2.5000f, -4f, 4f, -6.5000f, -3f, 2.9300f});
        put("theropod/sandraptor", new float[] {8f, 5f, 0f, -4f, 4f, 2.5000f, 0f, 15.5000f, -3f, 0.5000f, -9.5000f, -1f, -10.5000f, 0.5000f, 2.5000f, 3f, 1f, 1.5000f, -2.5000f, -3f, 2f, 0f, -3f, 0.8800f});
        put("theropod/snowraptor", new float[] {8f, 5f, 0f, -4f, 4f, 2.5000f, 0f, 15.5000f, -3f, 0.5000f, -9.5000f, -1f, -10.5000f, 1f, 2.5000f, 3f, 1f, 1.5000f, -2.5000f, -3f, 2f, 0f, -3f, 0.8500f});
        put("theropod/sunlizard", new float[] {6.5000f, 3.5000f, 2f, -2.5000f, 2.5000f, 1.5000f, 0f, 14f, -2f, 0f, -8.5000f, -2f, -10f, -0.5000f, 2.5000f, 1.5000f, -0.5000f, 2.5000f, -2.5000f, -3f, 1f, -0.5000f, -2.5000f, 1.1000f});
        put("theropod/woodraptor", new float[] {8f, 5f, 0f, -4f, 4f, 2.5000f, 0f, 15.5000f, -3f, 0.5000f, -9.5000f, -1f, -10.5000f, 0.5000f, 2.5000f, 3f, 1f, 1.5000f, -2.5000f, -3f, 2f, 0f, -3f, 0.9000f});
        put("theropod/yale", new float[] {7f, 14f, 2f, -9.5000f, 2f, 4f, 0f, 19.5000f, -3f, -1f, -9.5000f, -4f, -5f, -2.5000f, 3f, 2f, -0.5000f, 3f, -3.5000f, -4f, 1.5000f, 1f, -3.5000f, 1.2600f});
        put("bird_large/cloudwyvern", new float[] {2.5000f, 20.5000f, 11f, -0.5000f, 10f, -1.5000f, -3f, 2f, -10f, -5f, -11f, -1f, 3.5000f, 11.5000f, -1.5000f, 19f, 10.5000f, 1f, 11f, -2f, 0f, 5.5000f, 2f, 15.5000f, 0.5000f, 0f, -3.5000f, 1f, -0.6500f});
        put("bird_large/cockatrice", new float[] {2.5000f, 16f, 5f, -1.5000f, 8f, 4.5000f, 2f, -3f, -5f, -2.5000f, -8f, -3f, 3.5000f, 7f, 3.5000f, 6f, 0f, 0f, 4f, -1f, 1f, 3.5000f, 2.5000f, 13f, 0.5000f, -3f, -3f, 1f, -0.5000f});
        put("bird_large/flamewyvern", new float[] {2.5000f, 20.5000f, 11f, -0.5000f, 10f, -1.5000f, -3f, 2f, -10f, -5f, -11f, -1f, 6.5000f, 11.5000f, -2f, 19f, 11.5000f, 1f, 11f, -1f, 0f, 5.5000f, 2f, 15.5000f, 0.5000f, 0f, -3.5000f, 1f, -0.6500f});
        put("bird_large/frostwyvern", new float[] {2.5000f, 20.5000f, 11f, -0.5000f, 10f, -1.5000f, -3f, 2f, -10f, -5f, -11f, -1f, 5f, 10.5000f, -1.5000f, 18.5000f, 11.5000f, 0.5000f, 10f, -1.5000f, 0.5000f, 5.5000f, 2f, 15.5000f, 0.5000f, 0f, -3.5000f, 1f, -0.6500f});
        put("bird_large/phoenix", new float[] {2.5000f, 16f, 2.5000f, -5.5000f, 6f, 12f, 5f, 3f, -9.5000f, -1f, -11f, 0f, 3f, 2.5000f, 2f, 10f, 1f, 0f, 7f, 2f, 1.5000f, 4f, 1.5000f, 12f, 0.5000f, -0.5000f, -2.5000f, 1f, -0.6500f});
        put("bird_large/roc", new float[] {2.5000f, 27.5000f, 9.5000f, -1.5000f, 17f, -3.5000f, 0f, -3f, -7.5000f, -3.5000f, -8f, -3f, 5.5000f, 7.5000f, -1f, 12f, 1f, -0.5000f, 10f, -2f, 0f, 5.5000f, -1.5000f, 17.5000f, 2.5000f, -2.5000f, -5.5000f, 1f, -0.4000f});
        put("bird_large/seawyvern", new float[] {2.5000f, 20.5000f, 11f, -0.5000f, 10f, 2.5000f, -3f, 2f, -10f, -5f, -11f, -1f, 4f, 11.5000f, 0f, 19f, 11.5000f, 0.5000f, 12f, -1f, 0f, 5.5000f, 2f, 15.5000f, 0.5000f, 0f, -3.5000f, 1f, -0.6500f});
        put("bird_large/wealdwyvern", new float[] {2.5000f, 20.5000f, 11f, -0.5000f, 10f, -1.5000f, -3f, 2f, -10f, -5f, -11f, -1f, 5f, 11.5000f, -1f, 19f, 11.5000f, 0f, 16f, -4f, -1f, 5.5000f, 2f, 15.5000f, 0.5000f, 0f, -3.5000f, 1f, -0.6500f});
        put("fish_small/clownfish", new float[] {0f, 5f, -7.5000f, -0.5000f, 2f, 0.5000f, 1f, 5f, 4f});
        put("fish_small/piranha", new float[] {0f, 5f, -5.5000f, -0.5000f, 2f, 0.5000f, -0.5000f, 5f, 4f});
        put("fish_medium/icepike", new float[] {3f, 1f, 0f, 0f, 0f, 2.5000f, -4.5000f, 0f, -0.5000f, 1.5000f, 3.5000f, 3f, 0f, 4f, 4f});
        put("fish_medium/marlin", new float[] {2f, 1.5000f, 2.5000f, -3f, 0f, 2.5000f, -1f, 1f, -7f, 0f, 2.5000f, 1f, 3.5000f, 4f, 4f});
        put("dragon/reddragon", new float[] {2.5000f, 4.5000f, 7.5000f, 3.5000f, 6.5000f, -5f, 0f, 15f, -6.5000f, 0f, -6.5000f, 1.5000f, -11.5000f, -1f, 2.5000f, -16.5000f, 0f, 23f, 0.5000f, 4f, 6f, 1f, -13f, 6f, -2f, -10.5000f, 1f});
        put("arthropod/antlion", new float[] {4.5000f, 0f, -5f, 8.5000f, 8.5000f, 9f, -3.5000f, 3f, 0f, -4f, 3f, 0f, -4f, 4f, 11.5000f, -4f, 1.5000f, 6f, -4f, 6f, 7.5000f, -4f, 1.5000f, 7.5000f, -1.5000f, 1f, 0.7000f, -0.3000f, -0.4000f, 0.4000f, 1f});
        put("arthropod/blackwidow", new float[] {5.5000f, -3f, -5f, 10f, 2.5000f, 8f, 0f, 3f, 0f, -4f, 3f, 0f, -4f, 4f, 13.5000f, -6f, 2.5000f, 13f, -5.5000f, 2.5000f, 10f, -5.5000f, 2.5000f, 7f, -5.5000f, 1f, 0.1000f, -0.3000f, 0f, 0.4000f, 0f});
        put("arthropod/cavespider", new float[] {6f, 0.5000f, -5f, 7f, 2.5000f, 8f, -0.5000f, 3f, 0f, -4f, 3f, 0f, -4f, 4f, 13f, -3f, 2.5000f, 12.5000f, -2.5000f, 2.5000f, 9.5000f, -2.5000f, 2.5000f, 6.5000f, -2.5000f, 1f, 0.1000f, -0.3000f, 0f, 0.4000f, 0f});
        put("arthropod/dagonite", new float[] {4f, -1f, -6f, 8f, 2.5000f, 8f, 0f, 3f, 0f, -4f, 3f, 0f, -4f, 1.5000f, 14f, -3f, 2.5000f, 11f, -3f, 1.5000f, 8f, -3f, 2.5000f, 7f, -5.5000f, 1f, 0.1000f, -0.3000f, 0f, 0.4000f, 0f});
        put("arthropod/emberfly", new float[] {-1.5000f, 0.5000f, -5f, 2.5000f, 1.5000f, 7f, -0.5000f, 5.5000f, 6f, 3f, 4f, 6f, 2f, 2.5000f, 6f, -2.5000f, 1.5000f, 7.5000f, -2.5000f, 2.5000f, 3.5000f, -2.5000f, 1.5000f, 7.5000f, -1f, 0.5000f, 0.1000f, -0.3000f, 0f, 0.4000f, 0f});
        put("arthropod/hornbeetle", new float[] {5f, 3f, -5f, 7.5000f, 1.5000f, 7f, -0.5000f, 5.5000f, 5f, 3f, 4f, 6f, 2f, 5f, 6f, -3f, 1.5000f, 7.5000f, -3f, 5f, 6f, -3f, 1.5000f, 7.5000f, -1.5000f, 1f, 0.1000f, -0.3000f, 0f, 0.4000f, 0f});
        put("arthropod/leafbeetle", new float[] {4f, 0f, -5f, 6f, 1.5000f, 7f, -0.5000f, 0.5000f, 5f, 3f, 0.5000f, 4f, 2f, 5f, 6f, -1f, 1.5000f, 6.5000f, -1.5000f, 4.5000f, 5f, -2.5000f, 1.5000f, 7.5000f, -1.5000f, 0.8000f, 0.1000f, -0.3000f, 0f, 0.4000f, 0f});
        put("arthropod/moltencrawler", new float[] {4f, -1f, -7f, 6f, 2.5000f, 8f, 0f, 3f, 0f, -4f, 3f, 0f, -4f, 2.5000f, 14f, -3f, 3.5000f, 11f, -3f, 2.5000f, 8f, -3f, 2.5000f, 7f, -5.5000f, 1f, 0.1000f, -0.3000f, 0f, 0.4000f, 0f});
        put("arthropod/mosscrawler", new float[] {4f, -1.5000f, -7f, 6.5000f, 2.5000f, 8f, 0f, 3f, 0f, -4f, 3f, 0f, -4f, 1.5000f, 14f, -3.5000f, 2.5000f, 11f, -3.5000f, 1.5000f, 8f, -3.5000f, 2.5000f, 7f, -5.5000f, 1f, 0.1000f, -0.3000f, 0f, 0.4000f, 0f});
        put("arthropod/sandcrawler", new float[] {4f, -1f, -7f, 6f, 2.5000f, 8f, 0f, 3f, 0f, -4f, 3f, 0f, -4f, 1.5000f, 14f, -3f, 2.5000f, 11f, -3f, 1.5000f, 8f, -3f, 2.5000f, 7f, -5.5000f, 1f, 0.1000f, -0.3000f, 0f, 0.4000f, 0f});
        put("arthropod/stagbeetle", new float[] {5f, -1f, -5f, 6.5000f, 1.5000f, 10f, 1f, 0.5000f, 6f, 4.5000f, 0.5000f, 6f, 3f, 4.5000f, 6f, -2f, 1.5000f, 7.5000f, -2f, 5f, 6f, -2f, 1.5000f, 7.5000f, -1.5000f, 1f, 0.1000f, -0.3000f, 0f, 0.4000f, 1f});
        put("arthropod/tarantula", new float[] {6f, 0.5000f, -5f, 6f, 1.5000f, 7f, -0.5000f, 3f, 0f, -4f, 3f, 0f, -4f, 4f, 11f, -1.5000f, 1.5000f, 13.5000f, -1.5000f, 1.5000f, 10.5000f, -1.5000f, 1.5000f, 7.5000f, -1.5000f, 1f, 0.1000f, -0.3000f, 0f, 0.4000f, 0f});
        put("arthropod/weevil", new float[] {4f, 0f, -5f, 6f, 1.5000f, 7f, -0.5000f, 0.5000f, 5f, 3f, 0.5000f, 4f, 1.5000f, 5f, 9f, -2f, 1.5000f, 8.5000f, -2f, 6f, 5f, -2.5000f, 1.5000f, 7.5000f, -1.5000f, 0.7500f, 0.1000f, -0.3000f, 0f, 0.4000f, 0f});
        put("crustacean/crab", new float[] {0f, 0f, 0f, 5f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, -0.4000f, 0f, 0.4000f, 1f, 0.6200f});
        put("crustacean/karkatha", new float[] {0f, 0f, 0f, 0f, 0f, 3f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, -0.4000f, 0f, 0.4000f, 0f, 1.2000f});
        put("crustacean/soldiercrab", new float[] {0f, 0f, 0f, 5f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, -0.4000f, 0f, 0.4000f, 1f, 0.6200f});
    }

    private static void put(String key, float[] value) { TABLE.put(key, value); }

    /** speciesSex is "pig/male". Falls back to the species, then the first row. */
    static float[] get(String family, String speciesSex) {
        float[] row = TABLE.get(family + "/" + speciesSex);
        if (row == null && speciesSex != null) {
            int slash = speciesSex.indexOf('/');
            if (slash > 0) row = TABLE.get(family + "/" + speciesSex.substring(0, slash));
        }
        if (row == null) {
            for (var e : TABLE.entrySet()) {
                if (e.getKey().startsWith(family + "/")) return e.getValue();
            }
            return new float[0];
        }
        return row;
    }
}
