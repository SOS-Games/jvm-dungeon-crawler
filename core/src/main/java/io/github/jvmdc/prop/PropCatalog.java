package io.github.jvmdc.prop;

import com.badlogic.gdx.utils.JsonValue;
import io.github.jvmdc.assets.AssetDef;
import io.github.jvmdc.assets.AssetRoots;
import io.github.jvmdc.assets.CustomAssetRegistry;
import io.github.jvmdc.assets.ManifestLibrary;
import io.github.jvmdc.assets.ModelPaths;
import io.github.jvmdc.converter.VoxReader;
import io.github.jvmdc.world.BlockChunks;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Custom models, multi-part rigs, and placeable gameplay data.
 * Populated from CustomAssetRegistry.
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

        CustomAssetRegistry registry = CustomAssetRegistry.load(roots);
        for (AssetDef asset : registry.all().values()) {
            catalog.absorbAsset(asset);
        }

        catalog.addSprites();
        return catalog;
    }

    private void absorbAsset(AssetDef asset) {
        String leafName = asset.id.contains(".")
                ? asset.id.substring(asset.id.lastIndexOf('.') + 1)
                : asset.id;

        // Models
        for (AssetDef.PartDef p : asset.parts) {
            ModelDef m = new ModelDef();
            m.id = p.modelId != null ? p.modelId : (asset.id + "." + p.name);
            m.vox = p.modelId != null ? ("custom.voxel." + p.modelId) : ("custom.voxel." + asset.id);
            if (p.size != null) {
                m.sx = p.size[0];
                m.sy = p.size[1];
                m.sz = p.size[2];
            }
            if (p.offset != null) {
                m.ox = p.offset[0];
                m.oy = p.offset[1];
                m.oz = p.offset[2];
            }
            if (p.pivot != null) {
                m.px = p.pivot[0];
                m.py = p.pivot[1];
                m.pz = p.pivot[2];
                m.hasPivot = true;
            }
            models.put(m.id, m);
            if (p.name != null) {
                models.put(asset.id + "." + p.name, m);
                models.put(leafName + "." + p.name, m);
            }
        }

        // Rig
        if (asset.parts.size() >= 2) {
            RigDef rig = new RigDef();
            rig.id = asset.id;
            for (AssetDef.PartDef p : asset.parts) {
                RigPart part = new RigPart();
                part.model = p.modelId != null ? p.modelId : (asset.id + "." + p.name);
                if (p.at != null) {
                    part.ax = p.at[0];
                    part.ay = p.at[1];
                    part.az = p.at[2];
                }
                if (p.joint != null) {
                    part.joint = p.joint.type != null ? p.joint.type : "";
                    part.degPerSec = p.joint.degPerSec;
                    if (p.joint.axis != null) {
                        part.axisX = p.joint.axis[0];
                        part.axisY = p.joint.axis[1];
                        part.axisZ = p.joint.axis[2];
                    }
                    for (Map.Entry<String, float[]> st : p.joint.states.entrySet()) {
                        part.states.put(st.getKey(), st.getValue());
                    }
                }
                part.hiddenIn.addAll(p.hiddenIn);
                rig.parts.add(part);
            }
            rigs.put(asset.id, rig);
            rigs.put(leafName, rig);
            if ("trap.spike_trap".equals(asset.id)) {
                rigs.put("trap.spikes", rig);
                rigs.put("spikes", rig);
            }
        }

        // Placeable
        if (asset.gameplayData != null) {
            PlaceableDef def = parsePlaceable(leafName, asset.id, asset.gameplayData);
            placeables.put(asset.id, def);
            placeables.put(leafName, def);
        }
    }

    private static PlaceableDef parsePlaceable(String id, String model, JsonValue node) {
        PlaceableDef def = new PlaceableDef();
        def.id = id;
        def.model = model;
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
        if (specifier == null || specifier.isEmpty()) {
            if ("Door".equals(modelId)) return "voxygen.voxel.sprite.door.door-0";
            if ("Chest".equals(modelId)) return "voxygen.voxel.sprite.chest.chest-0";
            if ("WallSconce".equals(modelId)) return "voxygen.voxel.sprite.sconce.sconce-0";
        }
        return specifier == null ? "" : specifier;
    }

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
        public float degPerSec;
        public float axisX;
        public float axisY;
        public float axisZ = 1f;
        public final Map<String, float[]> states = new LinkedHashMap<>();
        public final java.util.Set<String> hiddenIn = new java.util.HashSet<>();
    }

    public static final class PlaceableDef {
        public String id = "";
        public String model = "";
        public boolean blocksMovement;
        public boolean blocksSight;
        public boolean wall;
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
