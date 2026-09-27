package io.github.jvmdc.viewer;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import io.github.jvmdc.converter.VoxMesher;
import io.github.jvmdc.converter.VoxReader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Small biped NPCs such as the myrmidon marksman store a head, chest, hands,
 * and feet as separate files. The converted armor JSON says where each piece
 * sits, and the idle pose stacks those bones into one body.
 */
final class NpcFigure {
    static final class Part {
        final Path file;
        final float ox, oy, oz;
        final boolean mirror;
        final String bone;
        final boolean left;
        /** Held weapon kind, such as Bow. Null for armor parts. */
        final String tool;

        Part(Path file, float ox, float oy, float oz, boolean mirror, String bone, boolean left) {
            this(file, ox, oy, oz, mirror, bone, left, null);
        }

        Part(Path file, float ox, float oy, float oz, boolean mirror, String bone, boolean left,
             String tool) {
            this.file = file;
            this.ox = ox;
            this.oy = oy;
            this.oz = oz;
            this.mirror = mirror;
            this.bone = bone;
            this.left = left;
            this.tool = tool;
        }
    }

    static final class Assembly {
        final String name;
        final String path;
        final String species;
        final List<Part> parts;

        Assembly(String name, String path, String species, List<Part> parts) {
            this.name = name;
            this.path = path;
            this.species = species;
            this.parts = parts;
        }
    }

    private static final String[][] MANIFESTS = {
            {"biped_small_armor_head_manifest.json", "head"},
            {"biped_small_armor_chest_manifest.json", "chest"},
            {"biped_small_armor_pants_manifest.json", "pants"},
            {"biped_small_armor_tail_manifest.json", "tail"},
            {"biped_small_armor_hand_manifest.json", "hand"},
            {"biped_small_armor_foot_manifest.json", "foot"},
    };

    private static final float[] IDENTITY = {1, 0, 0, 0, 1, 0, 0, 0, 1};
    private static final float[] MIRROR_X = {-1, 0, 0, 0, 1, 0, 0, 0, 1};

    private NpcFigure() {
    }

    static List<Assembly> find(Path assetsRoot) throws IOException {
        Map<String, List<Part>> grouped = new LinkedHashMap<>();
        Map<String, String> itemToFolder = new LinkedHashMap<>();
        for (String[] manifest : MANIFESTS) {
            Path file = jsonManifest(manifest[0]);
            if (!Files.isRegularFile(file)) {
                throw new IOException("missing " + file);
            }
            readManifest(file, manifest[1], assetsRoot, grouped, itemToFolder);
        }
        attachWeapons(assetsRoot, grouped, itemToFolder);
        List<Assembly> assemblies = new ArrayList<>();
        for (Map.Entry<String, List<Part>> entry : grouped.entrySet()) {
            List<Part> parts = entry.getValue();
            if (parts.size() < 2) {
                continue;
            }
            String folder = entry.getKey();
            int slash = folder.indexOf('/');
            String species = slash < 0 ? folder : folder.substring(0, slash);
            String leaf = folder.substring(folder.lastIndexOf('/') + 1);
            String name = leaf;
            if (slash >= 0 && ("male".equals(leaf) || "female".equals(leaf))) {
                String parent = folder.substring(0, folder.lastIndexOf('/'));
                String parentLeaf = parent.substring(parent.lastIndexOf('/') + 1);
                name = parentLeaf + " " + leaf;
            }
            assemblies.add(new Assembly(
                    name,
                    "assets/voxygen/voxel/npc/" + folder,
                    species,
                    parts));
        }
        return assemblies;
    }

    static VoxMesher.VoxMesh build(Assembly assembly) throws IOException {
        String tool = null;
        for (Part part : assembly.parts) {
            if (part.tool != null && "main".equals(part.bone)) {
                tool = part.tool;
            }
        }
        Hold hold = tool == null ? null : Hold.of(assembly.species, tool);
        VoxReader.VoxScene combined = new VoxReader.VoxScene();
        for (Part part : assembly.parts) {
            VoxReader.VoxScene scene = VoxReader.readScene(part.file.toFile());
            if (scene.models.isEmpty() || scene.models.get(0).grid == null) {
                continue;
            }
            VoxReader.VoxModel model = scene.models.get(0);
            int index = combined.models.size();
            combined.models.add(model);
            float[] bone = FigurePose.bone(assembly.species, part.bone, part.left);
            float[] boneRot = null;
            if (hold != null && "hand".equals(part.bone)) {
                bone = part.left ? hold.leftPos : hold.rightPos;
                boneRot = part.left ? hold.leftRot : hold.rightRot;
            } else if (hold != null && "main".equals(part.bone)) {
                bone = hold.weaponPos;
                boneRot = hold.weaponRot;
            } else if (hold != null && "second".equals(part.bone)) {
                bone = hold.leftPos;
                boneRot = hold.leftRot;
            }
            float ox = part.ox;
            float oy = part.oy;
            float oz = part.oz;
            float[] rotation;
            float[] translation;
            if (boneRot != null) {
                if (part.mirror) {
                    ox = -part.ox - model.sizeX;
                }
                float cx = ox + model.sizeX * 0.5f;
                float cy = oy + model.sizeY * 0.5f;
                float cz = oz + model.sizeZ * 0.5f;
                translation = new float[] {
                        bone[0] + boneRot[0] * cx + boneRot[1] * cy + boneRot[2] * cz,
                        bone[1] + boneRot[3] * cx + boneRot[4] * cy + boneRot[5] * cz,
                        bone[2] + boneRot[6] * cx + boneRot[7] * cy + boneRot[8] * cz
                };
                rotation = part.mirror ? mulMat(boneRot, MIRROR_X) : boneRot;
            } else {
                translation = new float[] {
                        bone[0] + ox + model.sizeX * 0.5f,
                        bone[1] + oy + model.sizeY * 0.5f,
                        bone[2] + oz + model.sizeZ * 0.5f
                };
                rotation = part.mirror ? MIRROR_X : IDENTITY;
            }
            combined.instances.add(new VoxReader.VoxInstance(index, rotation, translation));
        }
        return VoxMesher.build(combined);
    }

    /** Converted manifests live in this project's assets folder, not in Veloren. */
    private static Path jsonManifest(String filename) {
        Path local = Path.of("assets", "voxygen", "voxel", filename);
        if (Files.isRegularFile(local)) {
            return local;
        }
        return Path.of("voxygen", "voxel", filename);
    }

    private static void readManifest(Path file, String bone, Path assetsRoot,
                                     Map<String, List<Part>> grouped,
                                     Map<String, String> itemToFolder) throws IOException {
        JsonValue root;
        try (InputStream in = Files.newInputStream(file)) {
            root = new JsonReader().parse(in);
        }
        JsonValue doc = root.isArray() ? root.child : root;
        if (doc == null) {
            return;
        }
        JsonValue map = doc.get("map");
        if (map == null) {
            return;
        }
        boolean sided = "hand".equals(bone) || "foot".equals(bone);
        for (JsonValue item = map.child; item != null; item = item.next) {
            if (sided) {
                addPart(item.get("left"), bone, true, assetsRoot, grouped, item.name(), itemToFolder);
                addPart(item.get("right"), bone, false, assetsRoot, grouped, item.name(), itemToFolder);
            } else {
                addPart(item, bone, false, assetsRoot, grouped, item.name(), itemToFolder);
            }
        }
    }

    private static void addPart(JsonValue node, String bone, boolean left, Path assetsRoot,
                                Map<String, List<Part>> grouped, String itemId,
                                Map<String, String> itemToFolder) {
        if (node == null) {
            return;
        }
        JsonValue spec = node.get("vox_spec");
        if (spec == null || spec.size < 2) {
            return;
        }
        String name = spec.getString(0);
        if (name.startsWith("armor.") || !name.startsWith("npc.")) {
            return;
        }
        JsonValue offset = spec.get(1);
        float ox = offset.getFloat(0);
        float oy = offset.getFloat(1);
        float oz = offset.getFloat(2);
        String dotted = name.substring("npc.".length());
        int cut = dotted.lastIndexOf('.');
        if (cut <= 0) {
            return;
        }
        String folder = dotted.substring(0, cut).replace('.', '/');
        Path vox = assetsRoot.resolve("voxygen/voxel/npc/" + folder + "/"
                + dotted.substring(cut + 1) + ".vox");
        if (!Files.isRegularFile(vox)) {
            return;
        }
        boolean mirror = left && ("hand".equals(bone) || "foot".equals(bone));
        itemToFolder.put(itemId, folder);
        grouped.computeIfAbsent(folder, key -> new ArrayList<>())
                .add(new Part(vox, ox, oy, oz, mirror, bone, left));
    }

    private record Grip(String mesh, float ox, float oy, float oz) {
    }

    /**
     * Entity loadouts name the armor set and the weapon in each hand. The weapon
     * manifest gives the mesh and where it sits in the grip.
     */
    private static void attachWeapons(Path assetsRoot, Map<String, List<Part>> grouped,
                                      Map<String, String> itemToFolder) throws IOException {
        Map<String, Grip> grips = weaponGrips();
        Path entities = Path.of("assets", "common", "entity");
        if (!Files.isDirectory(entities) || grips.isEmpty()) {
            return;
        }
        try (var walk = Files.walk(entities)) {
            for (Path file : walk.filter(path -> path.getFileName().toString().endsWith(".json")).toList()) {
                attachEntityWeapons(file, assetsRoot, grouped, itemToFolder, grips);
            }
        }
    }

    private static Map<String, Grip> weaponGrips() throws IOException {
        Map<String, Grip> grips = new HashMap<>();
        Path file = jsonManifest("biped_weapon_manifest.json");
        if (!Files.isRegularFile(file)) {
            return grips;
        }
        JsonValue root = parse(file);
        JsonValue doc = root.isArray() ? root.child : root;
        if (doc == null) {
            return grips;
        }
        for (JsonValue item = doc.child; item != null; item = item.next) {
            String key = item.name();
            int open = key.indexOf('"');
            int close = key.lastIndexOf('"');
            if (open < 0 || close <= open) {
                continue;
            }
            JsonValue spec = item.get("vox_spec");
            if (spec == null || spec.size < 2) {
                continue;
            }
            JsonValue offset = spec.get(1);
            grips.put(key.substring(open + 1, close), new Grip(
                    spec.getString(0), offset.getFloat(0), offset.getFloat(1), offset.getFloat(2)));
        }
        return grips;
    }

    private static void attachEntityWeapons(Path file, Path assetsRoot,
                                            Map<String, List<Part>> grouped,
                                            Map<String, String> itemToFolder,
                                            Map<String, Grip> grips) throws IOException {
        JsonValue root = parse(file);
        JsonValue hands = find(root, "InHands");
        if (hands == null || !hands.isArray()) {
            return;
        }
        String folder = folderFor(root, itemToFolder);
        if (folder == null) {
            JsonValue asset = find(root, "Asset");
            if (asset != null && asset.isString()) {
                folder = folderFor(loadout(asset.asString()), itemToFolder);
            }
        }
        List<Part> parts = grouped.get(folder);
        if (parts == null) {
            return;
        }
        JsonValue slot = hands.child;
        addWeapon(slot, false, "main", assetsRoot, grips, parts);
        if (slot != null) {
            addWeapon(slot.next, true, "second", assetsRoot, grips, parts);
        }
    }

    private static void addWeapon(JsonValue slot, boolean offhand, String bone,
                                  Path assetsRoot, Map<String, Grip> grips, List<Part> parts) {
        if (slot == null || !slot.isObject()) {
            return;
        }
        JsonValue item = slot.get("Item");
        if (item == null || !item.isString()) {
            return;
        }
        Grip grip = grips.get(item.asString());
        if (grip == null) {
            return;
        }
        Path vox = assetsRoot.resolve("voxygen/voxel/" + grip.mesh.replace('.', '/') + ".vox");
        if (!Files.isRegularFile(vox)) {
            return;
        }
        for (Part part : parts) {
            if (bone.equals(part.bone)) {
                return;
            }
        }
        parts.add(new Part(vox, grip.ox, grip.oy, grip.oz, offhand, bone, false, toolKind(item.asString())));
    }

    private static String toolKind(String itemId) {
        if (itemId == null || !itemId.startsWith("common.items.")) {
            return "Sword";
        }
        Path file = Path.of("assets", "common",
                itemId.substring("common.".length()).replace('.', '/') + ".json");
        if (!Files.isRegularFile(file)) {
            return "Sword";
        }
        try {
            JsonValue kind = parse(file).get("kind");
            if (kind != null) {
                JsonValue tool = kind.get("Tool");
                if (tool != null && tool.get("kind") != null && tool.get("kind").isString()) {
                    return tool.get("kind").asString();
                }
            }
        } catch (IOException ignored) {
            return "Sword";
        }
        return "Sword";
    }

    private static String folderFor(JsonValue node, Map<String, String> itemToFolder) {
        if (node == null) {
            return null;
        }
        if (node.isObject()) {
            JsonValue item = node.get("Item");
            if (item != null && item.isString()) {
                String folder = itemToFolder.get(item.asString());
                if (folder != null) {
                    return folder;
                }
            }
        }
        if (node.isObject() || node.isArray()) {
            for (JsonValue child = node.child; child != null; child = child.next) {
                String folder = folderFor(child, itemToFolder);
                if (folder != null) {
                    return folder;
                }
            }
        }
        return null;
    }

    private static JsonValue loadout(String asset) throws IOException {
        if (asset == null || !asset.startsWith("common.")) {
            return null;
        }
        Path file = Path.of("assets", "common", asset.substring("common.".length()).replace('.', '/') + ".json");
        if (!Files.isRegularFile(file)) {
            return null;
        }
        return parse(file);
    }

    private static JsonValue find(JsonValue node, String key) {
        if (node == null) {
            return null;
        }
        if (node.isObject()) {
            JsonValue direct = node.get(key);
            if (direct != null) {
                return direct;
            }
        }
        if (node.isObject() || node.isArray()) {
            for (JsonValue child = node.child; child != null; child = child.next) {
                JsonValue found = find(child, key);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static JsonValue parse(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            return new JsonReader().parse(in);
        }
    }

    private static final class Hold {
        final float[] weaponPos;
        final float[] weaponRot;
        final float[] leftPos;
        final float[] leftRot;
        final float[] rightPos;
        final float[] rightRot;

        private Hold(float[] weaponPos, float[] weaponRot, float[] leftPos, float[] leftRot,
                     float[] rightPos, float[] rightRot) {
            this.weaponPos = weaponPos;
            this.weaponRot = weaponRot;
            this.leftPos = leftPos;
            this.leftRot = leftRot;
            this.rightPos = rightPos;
            this.rightRot = rightRot;
        }

        /** Wield stance: the weapon sits in the grip and both hands close on it. */
        static Hold of(String species, String tool) {
            float[] chest = FigurePose.bone(species, "chest", false);
            float[] grip = FigurePose.grip(species);
            float gx = grip[0];
            float gz = grip[1];
            float[] controlPos;
            float[] controlRot;
            float[] leftPos;
            float[] leftRot;
            float[] rightPos;
            float[] rightRot;
            if ("Bow".equals(tool)) {
                controlPos = new float[] {-1, 2 + gz, 3 - gz / 2.5f - 2 * gx};
                controlRot = mat(rotX(-0.3f));
                leftPos = new float[] {-1 - gx * 2, 0, 0};
                leftRot = mat(mul(rotX((float) (Math.PI / 2)), rotY(-0.3f)));
                rightPos = new float[] {1 + gx * 2, 3, -2};
                rightRot = mat(mul(rotX((float) (Math.PI / 2 + gx * 0.2f)), rotY(0.5f + gx * 0.2f)));
            } else if ("Spear".equals(tool)) {
                controlPos = new float[] {-3, gz, -gz / 2.5f - 2 * gx};
                controlRot = mat(rotX(-1.35f));
                leftPos = new float[] {1 - gx * 2, 2, -2};
                leftRot = mat(mul(rotX((float) (Math.PI / 1.5)), rotY(-0.3f)));
                rightPos = new float[] {-1 + gx * 2, 2, 2};
                rightRot = mat(mul(rotX((float) (Math.PI / 1.5 + gx * 0.2f)), rotY(0.5f + gx * 0.2f)));
            } else if ("Staff".equals(tool)) {
                controlPos = new float[] {-5, -1 + gz, -2 - gz / 2.5f - 2 * gx};
                controlRot = mat(mul(rotX(-0.3f), rotZ(0.5f)));
                leftPos = new float[] {2 - gx * 2, 1, 3};
                leftRot = mat(mul(mul(rotX((float) (Math.PI / 2)), rotY(-0.3f)), rotZ(-0.3f)));
                rightPos = new float[] {7 + gx * 2, -4, 3};
                rightRot = mat(mul(rotX((float) (Math.PI / 2 + gx * 0.2f)), rotY(-0.4f + gx * 0.2f)));
            } else if ("Blowgun".equals(tool)) {
                controlPos = new float[] {0, gz, 4 - gz / 2.5f - 2 * gx};
                controlRot = mat(rotX(-2.2f));
                leftPos = new float[] {1 - gx * 2, 0, 3};
                leftRot = mat(mul(rotX(3.8f), rotY(-0.3f)));
                rightPos = new float[] {-1 + gx * 2, 0, 4};
                rightRot = mat(mul(rotX(3.5f + gx * 0.2f), rotY(0.5f + gx * 0.2f)));
            } else {
                controlPos = new float[] {-5, -1 + gz, -1 - gz / 2.5f - 2 * gx};
                controlRot = mat(mul(rotX(-0.3f), rotZ(-0.3f)));
                leftPos = new float[] {2 - gx * 2, 1, 3};
                leftRot = mat(rotX((float) (Math.PI / 2)));
                rightPos = new float[] {9 + gx * 2, -1, -2};
                rightRot = mat(mul(rotX(0.5f + gx * 0.2f), rotY(0.2f + gx * 0.2f)));
            }
            float[] handL = {gx * 4, 0, gz};
            float[] handR = {-gx * 4, 0, gz};
            return new Hold(
                    add(chest, controlPos),
                    controlRot,
                    handOrigin(chest, controlPos, controlRot, leftPos, leftRot, handL),
                    mulMat(controlRot, leftRot),
                    handOrigin(chest, controlPos, controlRot, rightPos, rightRot, handR),
                    mulMat(controlRot, rightRot));
        }
    }

    private static float[] handOrigin(float[] chest, float[] controlPos, float[] controlRot,
                                      float[] sidePos, float[] sideRot, float[] hand) {
        float[] inControl = add(sidePos, mulVec(sideRot, hand));
        return add(chest, controlPos, mulVec(controlRot, inControl));
    }

    private static float[] add(float[] a, float[] b) {
        return new float[] {a[0] + b[0], a[1] + b[1], a[2] + b[2]};
    }

    private static float[] add(float[] a, float[] b, float[] c) {
        return new float[] {a[0] + b[0] + c[0], a[1] + b[1] + c[1], a[2] + b[2] + c[2]};
    }

    private static float[] mulVec(float[] m, float[] v) {
        return new float[] {
                m[0] * v[0] + m[1] * v[1] + m[2] * v[2],
                m[3] * v[0] + m[4] * v[1] + m[5] * v[2],
                m[6] * v[0] + m[7] * v[1] + m[8] * v[2]
        };
    }

    private static float[] rotX(float theta) {
        float half = theta * 0.5f;
        return new float[] {(float) Math.sin(half), 0, 0, (float) Math.cos(half)};
    }

    private static float[] rotY(float theta) {
        float half = theta * 0.5f;
        return new float[] {0, (float) Math.sin(half), 0, (float) Math.cos(half)};
    }

    private static float[] rotZ(float theta) {
        float half = theta * 0.5f;
        return new float[] {0, 0, (float) Math.sin(half), (float) Math.cos(half)};
    }

    /** Hamilton product. The right quaternion is applied first. */
    private static float[] mul(float[] a, float[] b) {
        return new float[] {
                a[3] * b[0] + a[0] * b[3] + a[1] * b[2] - a[2] * b[1],
                a[3] * b[1] - a[0] * b[2] + a[1] * b[3] + a[2] * b[0],
                a[3] * b[2] + a[0] * b[1] - a[1] * b[0] + a[2] * b[3],
                a[3] * b[3] - a[0] * b[0] - a[1] * b[1] - a[2] * b[2]
        };
    }

    private static float[] mat(float[] q) {
        float x = q[0];
        float y = q[1];
        float z = q[2];
        float w = q[3];
        return new float[] {
                1 - 2 * (y * y + z * z), 2 * (x * y - w * z), 2 * (x * z + w * y),
                2 * (x * y + w * z), 1 - 2 * (x * x + z * z), 2 * (y * z - w * x),
                2 * (x * z - w * y), 2 * (y * z + w * x), 1 - 2 * (x * x + y * y)
        };
    }

    private static float[] mulMat(float[] a, float[] b) {
        float[] out = new float[9];
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                out[row * 3 + col] = a[row * 3] * b[col]
                        + a[row * 3 + 1] * b[3 + col]
                        + a[row * 3 + 2] * b[6 + col];
            }
        }
        return out;
    }
}
