package io.github.jvmdc.viewer;

import java.util.HashMap;
import java.util.Map;

/**
 * Rest stance for a golem. The numbers are the idle bone offsets. Hands hang
 * from the shoulders, and feet hang from the legs.
 */
final class GolemPose {
    private static final Map<String, float[]> ATTR = new HashMap<>();
    private static final float[] SHOULDER_ROT = rotX(-0.2f);
    private static final float[] HAND_ROT = rotX(0.2f);

    static {
        // head y z, jaw y z, upper y z, lower y z, shoulder xyz, hand xyz, leg xyz, foot xyz
        put("stonegolem", 0, 2, 0, 0, 0, 34.5f, 0, -10.5f, 8, -1.5f, 4, 12.5f, -1, -7, 4, 0, -3.5f, 3.5f, 0.5f, -9.5f);
        put("treant", 18, -8, -6.5f, -1, 0, 28.5f, 0, -10.5f, 8, 4.5f, -3, 8.5f, -1, -7, 2, 9.5f, -1, 3.5f, -5, -8.5f);
        put("claygolem", 0, 7, 0, 0, 0, 23, 0, -7.5f, 8, -1, -1, 6.5f, 0, -2, 1, 0, -3, 3.5f, 0, -5.5f);
        put("woodgolem", 3, 6, 0, 0, 0, 24.5f, 0, -4.5f, 6, 2, 1, 5.5f, -1, -6, 2, 0.5f, -6, 2.5f, 1, -5.5f);
        put("coralgolem", -1, 3, 0, 0, 0, 25, 0, -11.5f, 11, 1, 0, 2.5f, -1.5f, -5, 2.5f, 0.5f, -3, 2.5f, 1, -1.5f);
        put("gravewarden", -2, 7, 0, 0, 0, 26.5f, 0, -4.5f, 8, 2, 3, 8.5f, -1, -7, 1, 0.5f, -6, 3.5f, -1, -8.5f);
        put("ancienteffigy", -2, 8, 0, 0, 0, 18, 0, -4.5f, 8, 2, 3, 8.5f, -1, -7, 1, 0.5f, -6, 3.5f, -1, -8.5f);
        put("mogwai", -8, 2, -6, -5, 0, 18, 0, -4.5f, 8, 2, 3, 8.5f, -1, -7, 1, 0.5f, -6, 3.5f, -1, -8.5f);
        put("irongolem", 0, 3, 0, 0, 0, 34.5f, 0, -10.5f, 8, -1.5f, 0, 12.5f, -1, -7, 1.5f, 1.5f, -3.5f, 3.5f, 0.5f, -9.5f);
    }

    private GolemPose() {
    }

    /** Local idle offsets, or null when this is not a golem. */
    static float[] local(String speciesSex) {
        return attr(speciesSex);
    }

    /** Stacked position for the still tile, or null when this is not a golem. */
    static float[] bone(String speciesSex, String bone) {
        float[] a = attr(speciesSex);
        if (a == null) {
            return null;
        }
        float upperY = a[4];
        float upperZ = a[5];
        float lowerY = upperY + a[6];
        float lowerZ = upperZ + a[7];
        float headY = upperY + a[0];
        float headZ = upperZ + a[1];
        float shoulderY = upperY + a[9];
        float shoulderZ = upperZ + a[10];
        float legY = lowerY + a[15];
        float legZ = lowerZ + a[16];
        return switch (bone) {
            case "torso_upper" -> new float[] {0, upperY, upperZ};
            case "torso_lower" -> new float[] {0, lowerY, lowerZ};
            case "head" -> new float[] {0, headY, headZ};
            case "jaw" -> new float[] {0, headY + a[2], headZ + a[3]};
            case "shoulder_l" -> new float[] {-a[8], shoulderY, shoulderZ};
            case "shoulder_r" -> new float[] {a[8], shoulderY, shoulderZ};
            case "hand_l" -> new float[] {-a[8] - a[11], shoulderY + a[12], shoulderZ + a[13]};
            case "hand_r" -> new float[] {a[8] + a[11], shoulderY + a[12], shoulderZ + a[13]};
            case "leg_l" -> new float[] {-a[14], legY, legZ};
            case "leg_r" -> new float[] {a[14], legY, legZ};
            case "foot_l" -> new float[] {-a[14] - a[17], legY + a[18], legZ + a[19]};
            case "foot_r" -> new float[] {a[14] + a[17], legY + a[18], legZ + a[19]};
            default -> new float[] {0, 0, 0};
        };
    }

    /** Constant shoulder and hand tilt, or null when the bone is unturned. */
    static float[] rot(String speciesSex, String bone) {
        if (attr(speciesSex) == null) {
            return null;
        }
        return switch (bone) {
            case "shoulder_l", "shoulder_r" -> SHOULDER_ROT;
            case "hand_l", "hand_r" -> HAND_ROT;
            default -> null;
        };
    }

    private static float[] attr(String speciesSex) {
        if (speciesSex == null) {
            return null;
        }
        int slash = speciesSex.indexOf('/');
        String species = slash < 0 ? speciesSex : speciesSex.substring(0, slash);
        return ATTR.get(species);
    }

    private static void put(String species, float... values) {
        ATTR.put(species, values);
    }

    private static float[] rotX(float theta) {
        float half = theta * 0.5f;
        float x = (float) Math.sin(half);
        float w = (float) Math.cos(half);
        return new float[] {
                1, 0, 0,
                0, 1 - 2 * x * x, -2 * w * x,
                0, 2 * w * x, 1 - 2 * x * x
        };
    }
}
