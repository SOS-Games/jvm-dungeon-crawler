package io.github.jvmdc.prop;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import io.github.jvmdc.assets.AssetRoots;
import io.github.jvmdc.assets.ManifestLibrary;
import io.github.jvmdc.assets.ModelPaths;
import io.github.jvmdc.converter.VoxReader;
import io.github.jvmdc.world.BlockChunks;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Custom models, multi-part rigs, and placeable gameplay data.
 * A rig and a solid model may share a name; both are kept.
 */
public final class PropCatalog {
    public final Map<String, ModelDef> models = new LinkedHashMap<>();
    public final Map<String, RigDef> rigs = new LinkedHashMap<>();
    public final Map<String, PlaceableDef> placeables = new LinkedHashMap<>();
    private final ManifestLibrary library;

    private PropCatalog(ManifestLibrary library) {
        this.library = library;
    }

    public static PropCatalog load(AssetRoots roots) throws IOException {
        ManifestLibrary library = ManifestLibrary.load(roots);
        PropCatalog catalog = new PropCatalog(library);
        Path manifest = roots.custom.resolve("manifest");
        catalog.readModels(manifest.resolve("models.json"));
        catalog.readPlaceables(manifest.resolve("placeables.json"));
        catalog.addSprites();
        return catalog;
    }

    /** Voxels deep along the axis that points out from a wall. */
    public int mountDepth(String modelId) {
        ModelDef model = models.get(modelId);
        if (model != null && model.sy > 0f) {
            return Math.max(1, Math.round(model.sy));
        }
        String specifier = vox(modelId);
        if (specifier.isEmpty()) {
            return 1;
        }
        try {
            var file = ModelPaths.locate().find(specifier);
            if (file.isEmpty()) {
                return 1;
            }
            VoxReader.VoxScene scene = VoxReader.readScene(file.get().toFile());
            if (scene.models.isEmpty()) {
                return 1;
            }
            return Math.max(1, scene.models.get(0).sizeY);
        } catch (IOException | RuntimeException e) {
            return 1;
        }
    }

    public String vox(String modelId) {
        ModelDef model = models.get(modelId);
        if (model != null) {
            return model.vox;
        }
        String specifier = library.specifier(modelId);
        return specifier == null ? "" : specifier;
    }

    private void readModels(Path file) throws IOException {
        JsonValue root = read(file);
        JsonValue modelsNode = root.get("models");
        if (modelsNode != null) {
            for (JsonValue child = modelsNode.child; child != null; child = child.next) {
                models.put(child.name, model(child));
            }
        }
        JsonValue rigsNode = root.get("rigs");
        if (rigsNode != null) {
            for (JsonValue child = rigsNode.child; child != null; child = child.next) {
                rigs.put(child.name, rig(child));
            }
        }
    }

    private void readPlaceables(Path file) throws IOException {
        JsonValue root = read(file);
        JsonValue list = root.get("placeables");
        if (list == null) {
            return;
        }
        for (JsonValue child = list.child; child != null; child = child.next) {
            placeables.put(child.name, placeable(child));
        }
    }

    /** Doors, chests, and torches live in the converted manifests, not the placeable list. */
    private void addSprites() {
        placeables.put("chest", sprite("chest", "Chest", true, false, "open", false));
        placeables.put("torch", lightSprite("torch", "WallSconce", false, true, 4f, 8f, 4.2f, 1.4f));
        placeables.put("door", sprite("door", "Door", true, true, "open", false));
        placeables.put("door_locked", sprite("door_locked", "KeyDoor", true, true, "unlock", false));
        placeables.put("door_secret", sprite("door_secret", "DoorDark", true, true, "open", false));
        PlaceableDef crystal = placeables.get("power_crystal");
        if (crystal != null) {
            crystal.light = true;
            crystal.lightR = 2.2f;
            crystal.lightG = 4.5f;
            crystal.lightB = 6.5f;
            crystal.lightRadius = 3.5f * BlockChunks.VOXELS_PER_TILE;
        }
    }

    private static PlaceableDef sprite(String id, String manifestId, boolean blocksMovement, boolean blocksSight,
                                       String verb, boolean wall) {
        PlaceableDef def = new PlaceableDef();
        def.id = id;
        def.model = manifestId;
        def.blocksMovement = blocksMovement;
        def.blocksSight = blocksSight;
        def.verb = verb;
        def.wall = wall;
        def.placement = wall ? "wall" : "floor";
        def.state = "idle";
        return def;
    }

    private static PlaceableDef lightSprite(String id, String manifestId, boolean blocksMovement, boolean wall,
                                            float tiles, float r, float g, float b) {
        PlaceableDef def = sprite(id, manifestId, blocksMovement, false, "", wall);
        def.light = true;
        def.lightR = r;
        def.lightG = g;
        def.lightB = b;
        def.lightRadius = tiles * BlockChunks.VOXELS_PER_TILE;
        return def;
    }

    private static ModelDef model(JsonValue node) {
        ModelDef model = new ModelDef();
        model.id = node.name;
        model.vox = node.getString("vox", "");
        float[] size = vec(node.get("size"), 1f);
        model.sx = size[0];
        model.sy = size[1];
        model.sz = size[2];
        float[] offset = vec(node.get("offset"), 0f);
        model.ox = offset[0];
        model.oy = offset[1];
        model.oz = offset[2];
        JsonValue pivot = node.get("pivot");
        if (pivot != null && pivot.isArray()) {
            float[] p = vec(pivot, 0f);
            model.px = p[0];
            model.py = p[1];
            model.pz = p[2];
            model.hasPivot = true;
        }
        return model;
    }

    private static RigDef rig(JsonValue node) {
        RigDef rig = new RigDef();
        rig.id = node.name;
        JsonValue parts = node.get("parts");
        if (parts != null) {
            for (JsonValue child = parts.child; child != null; child = child.next) {
                rig.parts.add(part(child));
            }
        }
        return rig;
    }

    private static RigPart part(JsonValue node) {
        RigPart part = new RigPart();
        part.model = node.getString("model", "");
        float[] at = vec(node.get("at"), 0f);
        part.ax = at[0];
        part.ay = at[1];
        part.az = at[2];
        JsonValue joint = node.get("joint");
        if (joint != null && joint.isObject()) {
            part.joint = joint.getString("type", "");
            float[] axis = vec(joint.get("axis"), 0f);
            if (joint.get("axis") == null) {
                axis[2] = 1f;
            }
            part.axisX = axis[0];
            part.axisY = axis[1];
            part.axisZ = axis[2];
            JsonValue states = joint.get("states");
            if (states != null) {
                for (JsonValue state = states.child; state != null; state = state.next) {
                    if (state.isArray()) {
                        part.states.put(state.name, vec(state, 0f));
                    } else if (state.isNumber()) {
                        part.states.put(state.name, new float[] {state.asFloat()});
                    }
                }
            }
        }
        return part;
    }

    private static PlaceableDef placeable(JsonValue node) {
        PlaceableDef def = new PlaceableDef();
        def.id = node.name;
        def.model = node.getString("model", "");
        def.blocksMovement = node.getBoolean("blocks_movement", false);
        def.blocksSight = node.getBoolean("blocks_sight", false);
        String where = node.getString("placement", "floor");
        def.wall = "wall".equals(where) || "wall_face".equals(where);
        def.placement = where;
        JsonValue states = node.get("states");
        if (states != null && states.child != null && states.child.isString()) {
            def.state = states.child.asString();
        }
        JsonValue interact = node.get("interactable");
        if (interact != null && interact.isObject()) {
            def.verb = interact.getString("verb", "");
        }
        JsonValue light = node.get("light");
        if (light != null && light.isObject()) {
            def.light = true;
            JsonValue color = light.get("color");
            if (color != null && color.size >= 3) {
                def.lightR = color.getFloat(0) / 255f * 8f;
                def.lightG = color.getFloat(1) / 255f * 8f;
                def.lightB = color.getFloat(2) / 255f * 8f;
            }
            def.lightRadius = light.getFloat("radius_tiles", 3f) * BlockChunks.VOXELS_PER_TILE;
        }
        return def;
    }

    private static float[] vec(JsonValue node, float fill) {
        float[] out = {fill, fill, fill};
        if (node == null || !node.isArray()) {
            return out;
        }
        int i = 0;
        for (JsonValue child = node.child; child != null && i < 3; child = child.next, i++) {
            if (child.isNumber()) {
                out[i] = child.asFloat();
            }
        }
        return out;
    }

    private static JsonValue read(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            return new JsonReader().parse(in);
        }
    }

    public static final class ModelDef {
        public String id = "";
        public String vox = "";
        public float sx = 1f;
        public float sy = 1f;
        public float sz = 1f;
        public float ox;
        public float oy;
        public float oz;
        public float px;
        public float py;
        public float pz;
        public boolean hasPivot;
    }

    public static final class RigDef {
        public String id = "";
        public final List<RigPart> parts = new ArrayList<>();
    }

    public static final class RigPart {
        public String model = "";
        public float ax;
        public float ay;
        public float az;
        public String joint = "";
        public float axisX;
        public float axisY;
        public float axisZ = 1f;
        public final Map<String, float[]> states = new LinkedHashMap<>();
    }

    public static final class PlaceableDef {
        public String id = "";
        public String model = "";
        public boolean blocksMovement;
        public boolean blocksSight;
        public boolean wall;
        /** floor, wall, or wall_face. Wall mounts use the facing as the outward normal. */
        public String placement = "floor";
        public String verb = "";
        public String state = "";
        public boolean light;
        public float lightR = 8f;
        public float lightG = 4.6f;
        public float lightB = 1.3f;
        public float lightRadius = 44f;
    }
}
