package io.github.jvmdc.viewer;

import java.util.HashMap;
import java.util.Map;

/**
 * Idle stance for a large biped (ogre, troll, oni, and the rest). Numbers are
 * bone positions. Mindflayer and cursekeeper sit a little higher, since they float.
 */
final class LargePose {
    private static final Map<String, Attr> SPECIES = new HashMap<>();

    static {
        // head y z, jaw y z, upper y z, lower y z, tail y z,
        // shoulder x y z, hand x y z, leg x y z, foot x y z, float
        put("ogre/male", 5, 6, 0, 0, 0, 27.5f, 1, -7, 0, 0, 12, 0.5f, 3, 14.5f, 0, -4, 0, 0, -4, 4, 1, -12, false);
        put("ogre/female", 1, 7.5f, 0, 0, 0, 28, 0, -6, 0, 0, 8, 0.5f, 2, 9, 0.5f, -4.5f, 0, 0, -2, 4, 0.5f, -13.5f, false);
        put("cyclops", 9.5f, 7.5f, -4.5f, -6, -2, 31, 1, -8.5f, 0, 0, 15, 3.5f, 1.5f, 14, 2, -5.5f, 4.5f, 1, -8.5f, 6, 3.5f, -15.5f, false);
        put("wendigo", 3, 7.5f, 0, 0, -1, 29, -1.5f, -6, 0, 0, 9, 0.5f, 2.5f, 12, 0, -3.5f, 2, 2, -2.5f, 5, 2.5f, -17, false);
        put("cavetroll", 9, 7, 0, -4, -1, 26.5f, 1, -9.5f, 0, 0, 13, 0, 0.5f, 13.5f, 1, -6, 4.5f, -1, -7.5f, 5.5f, 0, -14, false);
        put("mountaintroll", 13, 2, -1, -8, -1, 30.5f, 1, -13.5f, 0, 0, 14, -0.5f, -2, 13.5f, 0, -10, 3.5f, 0, -7.5f, 4.5f, 1, -14, false);
        put("swamptroll", 11, 2, -4, -4.5f, -1, 28.5f, 1.5f, -11.5f, 0, 0, 14, 0, 0, 17, 1, -8, 4.5f, -0.5f, -7.5f, 5.5f, 0, -14, false);
        put("dullahan", 3, 6, 0, 0, 0, 29, 0, -6.5f, 0, 0, 14, 0.5f, 3.5f, 14.5f, 0, -2.5f, 0, 0, -5, 4, 2.5f, -14, false);
        put("werewolf", 11.5f, 1, 5, -4.5f, 3, 26, 1, -10, -5.5f, -2, 9, 4, -3, 10, 2.5f, -11, 4.5f, 1, -5, 5.5f, 3, -6.5f, false);
        put("occultsaurok", 6, 3.5f, 1, -2.5f, 3, 24, 0, -5, -4.5f, -6, 7.5f, 1, 1.5f, 8, 1.5f, -5.5f, 3, 0.5f, -4, 3.5f, 3.5f, -10, false);
        put("mightysaurok", 6, 3.5f, 1, -2.5f, 3, 24, 0, -5, -4.5f, -6, 7.5f, 1, 1.5f, 8, 1.5f, -5.5f, 3, 0.5f, -4, 3.5f, 3.5f, -10, false);
        put("slysaurok", 6, 3.5f, 1, -2.5f, 3, 24, 0, -6, -4.5f, -6, 7.5f, 1, 1.5f, 8, 1.5f, -5.5f, 3, 0.5f, -4, 3.5f, 3.5f, -10, false);
        put("mindflayer", 5, 5.5f, 0, 0, 0, 30.5f, 3.5f, -10, 0, 0, 8, 0.5f, -1, 9, 0.5f, -4.5f, 6, -2, 6.5f, 4.5f, 1.5f, -16, true);
        put("minotaur", 6, 3, 2, -4, -1, 31.5f, 1.5f, -8.5f, -3, -6, 10, 1, -1, 12.5f, 0.5f, -7, 5, 0, -10, 6, 4.5f, -17.5f, false);
        put("tidalwarrior", 13, 2, -1, -5, -3, 22, 1.5f, -5, -4.5f, -6.5f, 12, 4.5f, -2.5f, 16.5f, 4.5f, -10.5f, 5, 0.5f, -6.5f, 5.5f, 4.5f, -13.5f, false);
        put("yeti", 8.5f, 4, -5, -5, -1, 23.5f, 0, -6.5f, 0, 0, 10.5f, 1, -2.5f, 12, 1.5f, -6, 4, 0, -5.5f, 4.5f, 0.5f, -12.5f, false);
        put("harvester", 6, 11, -2, -7, -1, 18, -1, -4.5f, 0, 0, 8, 1, -1.5f, 11.5f, 1.5f, -5.5f, 3.5f, 1, -4, 4.5f, 0.5f, -9.5f, false);
        put("blueoni", 10.5f, -3, 0, 3.5f, -1, 26.5f, 0, -8.5f, 0, 0, 11, 2, -5.5f, 13.5f, 0.5f, -8, 4.5f, 2, -5.5f, 5, 5, -12.5f, false);
        put("redoni", 10.5f, -3, 0, 3.5f, -1, 26.5f, 0, -8.5f, 0, 0, 11, 2, -5.5f, 13.5f, 0.5f, -8, 4.5f, 2, -5.5f, 5, 5, -12.5f, false);
        put("cultistwarlord", 0.5f, 14.5f, 0, 3.5f, -1, 18.5f, 0, -1.5f, 0, 0, 11.5f, -1, 4.5f, 11.5f, -1, -1, 3.5f, -1, -8.5f, 3.5f, 0, -12.5f, false);
        put("cultistwarlock", 0.5f, 11, 0, 3.5f, -1, 17.5f, 1, -2.5f, 0, 0, 8, 0, 3.5f, 9.5f, -1, 1, 3.5f, -1, -8.5f, 3.5f, 0, -10.5f, false);
        put("huskbrute", 8.5f, 4, -5, -5, -1, 23.5f, -0.5f, -7, 0, 0, 10.5f, 0, -1.5f, 13, 0.5f, -4, 4, 0, -7.5f, 4.5f, 0.5f, -12.5f, false);
        put("tursus", -4.5f, -14, 4, 10.5f, 3, 26, -5, -9, 0, 0, 12.5f, -2.5f, -2, 15.5f, 0, -7, 4.5f, 1, -9, 5.5f, 3, -14.5f, false);
        put("gigasfrost", -1.5f, 5, -1, 5.5f, -1, 30, 0, -5.5f, 0, 0, 10.5f, 0.5f, 0, 17, 0.5f, -6, 6, 0, -10, 6.5f, 2, -19.5f, false);
        put("adletelder", -8, 10, 10.5f, -7, 3, 19, 0, -4, -4.5f, -6, 8.5f, 1, 2.5f, 8, 1.5f, -2.5f, 3, -1.5f, -4, 4, 3.5f, -10, false);
        put("seabishop", 0, 9.5f, 5, -4.5f, 0, 15, 0, -1, 0, 0, 7, 0, 1, 10, 0, -3, 3, 1, -14, 5.5f, 3, -6.5f, false);
        put("haniwageneral", -1.5f, 10, 10.5f, -7, 3, 16, -1, -3.5f, 0, 0, 9, -1, 4.5f, 10, -1, -3, 3, 0, -5, 3, 1, -10, false);
        put("terracottabesieger", -2.5f, 16, 10.5f, -7, 3, 21.5f, -1, -4.5f, 0, 0, 13, -1, 2, 13.5f, -1, -3.5f, 5, 0.5f, -6, 5.5f, 2.5f, -13, false);
        put("terracottademolisher", -2.5f, 10, 10.5f, -7, 3, 16.5f, -2, -3.5f, 0, 0, 9, -1, 3, 10, -1, -1.5f, 3.5f, 1.5f, -5, 3.5f, 3, -10.5f, false);
        put("terracottapunisher", -2.5f, 10, 10.5f, -7, 3, 15.5f, -1.5f, -2.5f, 0, 0, 9, -1, 4, 10, -1, -1.5f, 3.5f, 1, -5, 3.5f, 2, -10.5f, false);
        put("terracottapursuer", -2, 13.5f, 10.5f, -7, 3, 15.5f, -1.5f, -2.5f, 0, 0, 9, -1, 4, 10, -1, -1.5f, 3.5f, 1, -5, 3.5f, 2.5f, -10.5f, false);
        put("cursekeeper", 2, 6.5f, 10.5f, -7, -4, 20, -1.5f, -4.5f, 0, 0, 9.5f, -0.5f, 2.5f, 11, -1, -4, 5, 0.5f, -6, 5.5f, 2.5f, -13, true);
        put("forgemaster", 5, 6, -1, 5.5f, -1, 32, 0, -5.5f, 0, 0, 20, 4, 13, 19, 4, -1, 9, 0, -10, 8.5f, 2, -19.5f, false);
        put("strigoi", 10, 8, -2, -4, -4, 27.5f, 3, -9, -4.5f, -8, 13.5f, 2, 0.5f, 17, 2.5f, -5.5f, 5, 1, -6, 6, 2.5f, -14, false);
        put("executioner", 0, 10, -2, -4, 0, 24, -3, -5, 0, 0, 8.5f, 0, 4, 9, 0.5f, -1.5f, 3, 1, -7, 3, 7.5f, -13, false);
        put("gigasfire", 3, 7, -1, 3.5f, 0.5f, 30, 0, -5.5f, 0, 0, 19, 0.5f, 3, 19.5f, 0.5f, -5, 6, 0, -10, 6.5f, 2, -19.5f, false);
    }

    private LargePose() {
    }

    /** Null when this is not a large biped. */
    static float[] bone(String species, String bone) {
        Attr attr = SPECIES.get(species);
        if (attr == null) {
            return null;
        }
        float upperY = attr.upperY;
        float upperZ = attr.upperZ + (attr.floating ? 4 : 0);
        float lowerY = upperY + attr.lowerY;
        float lowerZ = upperZ + attr.lowerZ;
        float headY = upperY + attr.headY;
        float headZ = upperZ + attr.headZ;
        return switch (bone) {
            case "torso_upper" -> new float[] {0, upperY, upperZ};
            case "torso_lower" -> new float[] {0, lowerY, lowerZ};
            case "head" -> new float[] {0, headY, headZ};
            case "jaw" -> new float[] {0, headY + attr.jawY, headZ + attr.jawZ};
            case "tail" -> new float[] {0, lowerY + attr.tailY, lowerZ + attr.tailZ};
            case "shoulder_l" -> new float[] {-attr.shoulderX, upperY + attr.shoulderY, upperZ + attr.shoulderZ};
            case "shoulder_r" -> new float[] {attr.shoulderX, upperY + attr.shoulderY, upperZ + attr.shoulderZ};
            case "hand_l" -> new float[] {-attr.handX, upperY + attr.handY, upperZ + attr.handZ};
            case "hand_r" -> new float[] {attr.handX, upperY + attr.handY, upperZ + attr.handZ};
            case "leg_l" -> new float[] {-attr.legX, lowerY + attr.legY, lowerZ + attr.legZ};
            case "leg_r" -> new float[] {attr.legX, lowerY + attr.legY, lowerZ + attr.legZ};
            case "foot_l" -> new float[] {-attr.footX, lowerY + attr.footY, lowerZ + attr.footZ};
            case "foot_r" -> new float[] {attr.footX, lowerY + attr.footY, lowerZ + attr.footZ};
            default -> new float[] {0, 0, 0};
        };
    }

    private static void put(String species,
                            float headY, float headZ, float jawY, float jawZ,
                            float upperY, float upperZ, float lowerY, float lowerZ,
                            float tailY, float tailZ,
                            float shoulderX, float shoulderY, float shoulderZ,
                            float handX, float handY, float handZ,
                            float legX, float legY, float legZ,
                            float footX, float footY, float footZ,
                            boolean floating) {
        SPECIES.put(species, new Attr(headY, headZ, jawY, jawZ, upperY, upperZ, lowerY, lowerZ,
                tailY, tailZ, shoulderX, shoulderY, shoulderZ, handX, handY, handZ,
                legX, legY, legZ, footX, footY, footZ, floating));
    }

    private record Attr(
            float headY, float headZ, float jawY, float jawZ,
            float upperY, float upperZ, float lowerY, float lowerZ,
            float tailY, float tailZ,
            float shoulderX, float shoulderY, float shoulderZ,
            float handX, float handY, float handZ,
            float legX, float legY, float legZ,
            float footX, float footY, float footZ,
            boolean floating) {
    }
}
