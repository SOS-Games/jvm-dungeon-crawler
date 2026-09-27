package io.github.jvmdc.viewer;

import java.util.HashMap;
import java.util.Map;

/**
 * Rest pose for a small biped, in voxel units. These numbers are the idle
 * bone positions for that body only (myrmidon, gnome, and the other small
 * bipeds). Other creatures, such as a bear or an akhlut, are not in this table.
 */
final class FigurePose {
    private static final Map<String, Attr> SPECIES = new HashMap<>();
    private static final Map<String, float[]> GRIP = new HashMap<>();

    static {
        put("gnome", -1, 9, 0, 9, 0, -3, 0, 0, 4, 0.5f, -1, 3, 0, 4);
        put("sahagin", 7, -3.5f, 0, 15, 0.5f, -7, -2.5f, -2, 3.5f, 3.5f, -2, 3, 1, 8);
        put("adlet", 0, 7, 0, 11, 0, -3, -4.5f, -2, 4.5f, -0.5f, 2, 3, 0.5f, 7);
        put("gnarling", 0, 6, 0, 7.5f, 0, -3, -2, 1.5f, 4, 0, 1.5f, 2.5f, 1, 5);
        put("mandragora", -1, 9, 0, 4, 0, 0, 0, -1, 4, -0.5f, 4, 3, 0, 4);
        put("kappa", 8, 3.5f, 0, 14.5f, 0, -3, 0, -4, 4, 3.5f, -0.5f, 3, 3, 9);
        put("cactid", -1, 9, 0, 7, 0, -2, 0, 0, 3, -0.5f, 1.5f, 2.5f, 0, 5);
        put("gnoll", 5.5f, -1, 0, 15.5f, 0.5f, -7.5f, -2.5f, -2, 3.5f, 0.5f, -1, 3, 1, 7);
        put("haniwa", 0, 7, 0, 11, 0, -3.5f, -4.5f, -2, 4.25f, -1, 1.5f, 3, 0.5f, 8);
        put("myrmidon", 0, 8, 0, 11, 0, -3, -2.5f, -1, 3.5f, 1.5f, 2, 3, 0.5f, 7);
        put("husk", 0.5f, 8.5f, 0, 13, -1, -3, 0, 0, 4, 0, 1, 4, 0.5f, 7);
        put("boreal", -0.5f, 13, 0, 12, 1.5f, -5, 0, 0, 5, 0.5f, 5, 3, 0, 9);
        put("ashen", -0.5f, 13, 0, 14.5f, 1.5f, -5, 0, 0, 6, 1, 2, 3, 1, 9);
        put("bushly", -1, 9, 0, 4, 0, 1, 0, -1, 5, 2, 8, 2.5f, 0, 7);
        put("irrwurz", -1, 9, 0, 6, -5.5f, -0.5f, 0, -1, 3.5f, 2, 3, 4, 0, 6);
        put("iron_dwarf", 3, 3.5f, 0, 14, -1, -8, 0, 0, 4, 1.5f, -3.5f, 3.5f, 3, 7);
        put("flamekeeper", 3, 3.5f, 0, 14, -1, -8, 0, 0, 4, 1.5f, -3.5f, 3.5f, 3, 7);
        put("shamanic_spirit", -0.5f, 4.5f, 0, 14.5f, 0, -8, 0, 0, 5, 0, 1, 3.5f, 3, 7);
        put("jiangshi", -1, 6.5f, 0, 14, 0.5f, -6, 0, 0, 5, -1, 3, 3, 0, 8);
        put("treasure_egg", -1, 9, 0, 3, 0, 1, 0, 0, 5, 2, 5, 2, 0.5f, 4);
        put("bloodmoon_heiress", 0, 3.5f, 0, 21, 0, -8, 0, 0, 2.5f, 2.5f, 7, 8, 0.5f, 32.5f);
        put("bloodservant", -1, 6.5f, 0, 14, 0, -6, 0, 0, 5, -1, 2, 2.5f, 1, 7);
        put("harlequin", 0, 8, 0, 13.5f, 0, -5.5f, 0, 0, 5, 0, 2.5f, 2.5f, 2, 10);
        put("goblin_thug", -0.5f, 3.5f, 0, 8.5f, 0, -4.5f, 0, 0, 4.5f, 0, 2, 3, 0.5f, 5);
        put("goblin_chucker", -0.5f, 3.5f, 0, 8.5f, 0, -4.5f, 0, 0, 4.5f, 0, 2, 3, 0.5f, 5);
        put("goblin_ruffian", -0.5f, 3.5f, 0, 8.5f, 0, -4.5f, 0, 0, 4.5f, 0, 2, 3, 0.5f, 5);
        put("green_legoom", 0, 3.5f, 0, 7, 0, -3.5f, 0, 0, 3, 0, 1.5f, 2, -0.5f, 4);
        put("ochre_legoom", 0, 3.5f, 0, 7, 0, -3.5f, 0, 0, 3, 0, 1.5f, 2, -0.5f, 4);
        put("red_legoom", 0, 3.5f, 0, 7, 0, -3.5f, 0, 0, 3, 0, 1.5f, 2, -0.5f, 4);
        put("umber_legoom", 0, 3.5f, 0, 7, 0, -3.5f, 0, 0, 3, 0, 1.5f, 2, -0.5f, 4);
        put("purple_legoom", -0.5f, 3.5f, 0, 7.5f, 0, -4, 0, 0, 3, 0, 1.5f, 2, -0.5f, 4);
        grip("gnome", 0, 5);
        grip("sahagin", 1, 13);
        grip("adlet", 0, 7);
        grip("gnarling", 0, 7);
        grip("mandragora", 0, 7);
        grip("kappa", 0.75f, 12);
        grip("cactid", 0, 8);
        grip("gnoll", 1, 9);
        grip("haniwa", 0, 8);
        grip("myrmidon", 0, 8);
        grip("husk", 0, 8);
        grip("boreal", 1, 5);
        grip("ashen", -1, 7);
        grip("bushly", 0, 7);
        grip("irrwurz", 0, 7);
        grip("iron_dwarf", 0, 8);
        grip("flamekeeper", 0, 8);
        grip("shamanic_spirit", 0, 8);
        grip("jiangshi", 0, 8);
        grip("treasure_egg", 0, 7);
        grip("bloodmoon_heiress", 0, 8);
        grip("bloodservant", 0, 8);
        grip("harlequin", 0, 8);
        grip("goblin_thug", 0, 8);
        grip("goblin_chucker", 0, 8);
        grip("goblin_ruffian", 0, 8);
        grip("green_legoom", 0, 8);
        grip("ochre_legoom", 0, 8);
        grip("red_legoom", 0, 8);
        grip("umber_legoom", 0, 8);
        grip("purple_legoom", 0, 8);
    }

    /** How far the hands sit from the weapon grip. x is sideways, z is up. */
    static float[] grip(String species) {
        return GRIP.getOrDefault(species, GRIP.get("myrmidon"));
    }

    private static void grip(String species, float x, float z) {
        GRIP.put(species, new float[] {x, z});
    }

    /** Voxel-space position of a bone. x is right, y is forward, z is up. */
    static float[] bone(String species, String bone, boolean left) {
        Attr attr = SPECIES.getOrDefault(species, SPECIES.get("myrmidon"));
        float chestY = attr.chestY;
        float chestZ = attr.chestZ;
        return switch (bone) {
            case "head" -> new float[] {0, chestY + attr.headY, chestZ + attr.headZ};
            case "chest" -> new float[] {0, chestY, chestZ};
            case "pants" -> new float[] {0, chestY + attr.pantsY, chestZ + attr.pantsZ};
            case "tail" -> new float[] {
                    0,
                    chestY + attr.pantsY + attr.tailY,
                    chestZ + attr.pantsZ + attr.tailZ
            };
            case "hand" -> new float[] {
                    (left ? -attr.handX : attr.handX),
                    chestY + attr.handY,
                    chestZ + attr.handZ
            };
            case "foot" -> new float[] {left ? -attr.footX : attr.footX, attr.footY, attr.footZ};
            default -> new float[] {0, 0, 0};
        };
    }

    private static void put(String species,
                            float headY, float headZ,
                            float chestY, float chestZ,
                            float pantsY, float pantsZ,
                            float tailY, float tailZ,
                            float handX, float handY, float handZ,
                            float footX, float footY, float footZ) {
        SPECIES.put(species, new Attr(headY, headZ, chestY, chestZ, pantsY, pantsZ, tailY, tailZ,
                handX, handY, handZ, footX, footY, footZ));
    }

    private record Attr(
            float headY, float headZ,
            float chestY, float chestZ,
            float pantsY, float pantsZ,
            float tailY, float tailZ,
            float handX, float handY, float handZ,
            float footX, float footY, float footZ) {
    }
}
