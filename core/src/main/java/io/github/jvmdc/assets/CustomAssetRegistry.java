package io.github.jvmdc.assets;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Scans custom asset folders at startup and parses each definition into an AssetDef.
 * Custom IDs replace base/Veloren IDs with the same key. Malformed files are logged and skipped.
 */
public final class CustomAssetRegistry {
    private static CustomAssetRegistry instance;

    private final Map<String, AssetDef> assets = new LinkedHashMap<>();
    private final Map<String, Path> modelIdToVoxPath = new LinkedHashMap<>();

    public CustomAssetRegistry() {
    }

    public static synchronized CustomAssetRegistry getInstance() {
        if (instance == null) {
            instance = load(AssetRoots.locate());
        }
        return instance;
    }

    public static synchronized void reset() {
        instance = null;
    }

    public static CustomAssetRegistry load(AssetRoots roots) {
        CustomAssetRegistry registry = new CustomAssetRegistry();
        registry.scan(roots);
        return registry;
    }

    public void scan(AssetRoots roots) {
        Path customRoot = roots.custom;
        if (customRoot == null || !Files.isDirectory(customRoot)) {
            return;
        }

        try (Stream<Path> categories = Files.list(customRoot)) {
            List<Path> catDirs = categories.filter(Files::isDirectory).sorted().toList();
            for (Path catDir : catDirs) {
                String category = catDir.getFileName().toString();
                try (Stream<Path> assetDirs = Files.list(catDir)) {
                    List<Path> aDirs = assetDirs.filter(Files::isDirectory).sorted().toList();
                    for (Path assetFolder : aDirs) {
                        String folderName = assetFolder.getFileName().toString();
                        Path jsonFile = assetFolder.resolve(folderName + ".json");
                        if (Files.isRegularFile(jsonFile)) {
                            parseAndRegister(category, folderName, assetFolder, jsonFile);
                        }
                    }
                } catch (Exception e) {
                    logError("Error listing asset directory: " + catDir, e);
                }
            }
        } catch (Exception e) {
            logError("Error scanning custom_assets root: " + customRoot, e);
        }
    }

    public void register(AssetDef def) {
        if (def != null && def.id != null) {
            assets.put(def.id, def);
        }
    }

    public Optional<AssetDef> get(String id) {
        return Optional.ofNullable(assets.get(id));
    }

    public Optional<Path> getVoxForModelId(String modelId) {
        if (modelId == null) {
            return Optional.empty();
        }
        String stripped = modelId.startsWith("custom.voxel.") ? modelId.substring("custom.voxel.".length()) : modelId;
        Path path = modelIdToVoxPath.get(stripped);
        if (path == null) {
            path = modelIdToVoxPath.get(modelId);
        }
        return Optional.ofNullable(path);
    }

    public boolean has(String id) {
        return assets.containsKey(id);
    }

    public Map<String, AssetDef> all() {
        return assets;
    }

    private void parseAndRegister(String category, String folderName, Path folder, Path jsonFile) {
        try (InputStream in = Files.newInputStream(jsonFile)) {
            JsonValue root = new JsonReader().parse(in);
            if (root == null || !root.isObject()) {
                logError("Malformed asset JSON (not an object): " + jsonFile, null);
                return;
            }

            String id = root.has("id") && root.get("id").isString()
                    ? root.getString("id")
                    : category + "." + folderName;

            float scale = root.has("scale") ? root.getFloat("scale") : (1f / 11f);
            String units = root.has("units") ? root.getString("units") : null;

            // Body
            AssetDef.BodyDef body = null;
            if (root.has("body") && root.get("body").isObject()) {
                JsonValue b = root.get("body");
                body = new AssetDef.BodyDef(
                        b.has("family") ? b.getString("family") : null,
                        b.has("species") ? b.getString("species") : null,
                        b.has("body_type") ? b.getString("body_type") : null
                );
            }

            // Licence / Credit / Source
            String licence = buildLicenceString(root);

            // Palette & Palette Set
            String paletteSet = root.has("palette_set") ? root.getString("palette_set") : null;
            JsonValue palNode = root.has("palette") ? root.get("palette") : root.get("appearance");
            AssetDef.PaletteDef palette = parsePalette(palNode);
            if (palette != null && root.has("appearance") && root.get("appearance").isObject() && root.get("appearance").has("palettes")) {
                JsonValue appPals = root.get("appearance").get("palettes");
                if (appPals != null && appPals.isObject()) {
                    for (JsonValue nameNode = appPals.child; nameNode != null; nameNode = nameNode.next) {
                        String pName = nameNode.name;
                        Map<Integer, int[]> indexColors = new LinkedHashMap<>();
                        if (nameNode.isObject()) {
                            for (JsonValue idxNode = nameNode.child; idxNode != null; idxNode = idxNode.next) {
                                try {
                                    int idx = Integer.parseInt(idxNode.name);
                                    if (idxNode.isArray() && idxNode.size >= 3) {
                                        indexColors.put(idx, new int[]{idxNode.getInt(0), idxNode.getInt(1), idxNode.getInt(2)});
                                    }
                                } catch (NumberFormatException ignored) {
                                }
                            }
                        }
                        palette.namedPalettes.put(pName.toLowerCase(), indexColors);
                    }
                }
            }

            // Skeleton Attr
            float[] skeletonAttr = parseSkeletonAttr(root.get("skeleton_attr"));

            // Gameplay data / Placeable
            JsonValue gameplayData = root.get("placeable");

            // Pivots & States & Removed Baked Previews
            JsonValue pivots = root.get("pivots");
            JsonValue states = root.get("states");
            JsonValue removedBakedPreviews = root.get("removed_baked_previews");

            AssetDef def = new AssetDef(
                    id, category, jsonFile, folder, scale, units, palette, paletteSet, gameplayData,
                    licence, body, skeletonAttr, pivots, states, removedBakedPreviews, root
            );

            // Tone palettes in palette object (e.g. stone, wood, metal, water, candle, offering)
            if (root.has("palette") && root.get("palette").isObject()) {
                for (JsonValue child = root.get("palette").child; child != null; child = child.next) {
                    if (child.isArray() || child.isObject()) {
                        def.tonePalettes.put(child.name, child);
                    }
                }
            }

            // Parts
            if (root.has("parts") && root.get("parts").isArray()) {
                for (JsonValue p = root.get("parts").child; p != null; p = p.next) {
                    AssetDef.PartDef part = parsePart(p, folder, states, scale);
                    if (part != null) {
                        def.parts.add(part);
                        if (part.resolvedPath != null) {
                            if (part.modelId != null) {
                                modelIdToVoxPath.put(part.modelId, part.resolvedPath);
                                modelIdToVoxPath.put("custom.voxel." + part.modelId, part.resolvedPath);
                            }
                            if (part.name != null) {
                                modelIdToVoxPath.put(id + "." + part.name, part.resolvedPath);
                                modelIdToVoxPath.put("custom.voxel." + id + "." + part.name, part.resolvedPath);
                            }
                        }
                    }
                }
            }

            if (root.has("source_model_id") && !def.parts.isEmpty()) {
                for (AssetDef.PartDef p : def.parts) {
                    if (p.resolvedPath != null) {
                        modelIdToVoxPath.put(root.getString("source_model_id"), p.resolvedPath);
                        break;
                    }
                }
            }
            if (!def.parts.isEmpty()) {
                for (AssetDef.PartDef p : def.parts) {
                    if (p.resolvedPath != null) {
                        modelIdToVoxPath.putIfAbsent(id, p.resolvedPath);
                        break;
                    }
                }
            }

            // Read placeable.attach (name -> [x,y,z])
            if (gameplayData != null && gameplayData.has("attach") && gameplayData.get("attach").isObject()) {
                for (JsonValue a = gameplayData.get("attach").child; a != null; a = a.next) {
                    if (a.isArray() && a.size >= 3) {
                        def.attachPoints.put(a.name, new float[]{a.getFloat(0), a.getFloat(1), a.getFloat(2)});
                    }
                }
            }

            register(def);
        } catch (Exception e) {
            logError("Skipping malformed asset JSON: " + jsonFile, e);
        }
    }

    private static String buildLicenceString(JsonValue root) {
        StringBuilder sb = new StringBuilder();
        if (root.has("license")) sb.append(root.getString("license"));
        if (root.has("credit")) {
            if (sb.length() > 0) sb.append("; ");
            sb.append(root.getString("credit"));
        }
        if (root.has("source")) {
            if (sb.length() > 0) sb.append("; Source: ");
            sb.append(root.getString("source"));
        }
        return sb.length() > 0 ? sb.toString() : "Unspecified";
    }

    private static AssetDef.PartDef parsePart(JsonValue p, Path folder, JsonValue states, float assetScale) {
        if (p == null || !p.isObject()) {
            return null;
        }

        String name = p.has("name") ? p.getString("name") : null;
        String file = p.has("file") && p.get("file").isString() ? p.getString("file") : null;
        Path resolvedPath = (file != null && !file.isBlank()) ? folder.resolve(file) : null;

        int[] size = p.has("size") && p.get("size").isArray()
                ? new int[]{p.get("size").getInt(0), p.get("size").getInt(1), p.get("size").getInt(2)}
                : null;

        float[] offset = p.has("offset") && p.get("offset").isArray()
                ? new float[]{p.get("offset").getFloat(0), p.get("offset").getFloat(1), p.get("offset").getFloat(2)}
                : new float[3];

        float[] pivot = p.has("pivot") && p.get("pivot").isArray()
                ? new float[]{p.get("pivot").getFloat(0), p.get("pivot").getFloat(1), p.get("pivot").getFloat(2)}
                : null;

        float[] at = p.has("at") && p.get("at").isArray()
                ? new float[]{p.get("at").getFloat(0), p.get("at").getFloat(1), p.get("at").getFloat(2)}
                : new float[3];

        String slot = p.has("slot") ? p.getString("slot") : null;
        String kind = p.has("kind") ? p.getString("kind") : null;
        boolean mirror = p.has("mirror") && p.getBoolean("mirror");
        String modelId = p.has("model_id") ? p.getString("model_id") : null;

        float partScale = p.has("scale") ? p.getFloat("scale") : assetScale;
        boolean emissive = p.has("emissive") && p.getBoolean("emissive");
        String anim = p.has("anim") ? p.getString("anim") : null;

        // Joint
        AssetDef.JointDef joint = null;
        if (p.has("joint") && p.get("joint").isObject()) {
            JsonValue j = p.get("joint");
            String type = j.has("type") ? j.getString("type") : null;
            float degPerSec = j.has("deg_per_sec") ? j.getFloat("deg_per_sec") : 0f;
            float[] axis = null;
            if (j.has("axis") && j.get("axis").isArray() && j.get("axis").size >= 3) {
                JsonValue ax = j.get("axis");
                axis = new float[]{
                        ax.get(0).isNull() ? 0f : ax.getFloat(0),
                        ax.get(1).isNull() ? 0f : ax.getFloat(1),
                        ax.get(2).isNull() ? 0f : ax.getFloat(2)
                };
            }
            float[] hinge = null;
            if (j.has("hinge") && j.get("hinge").isArray() && j.get("hinge").size >= 3) {
                JsonValue hg = j.get("hinge");
                hinge = new float[]{
                        hg.get(0).isNull() ? 0f : hg.getFloat(0),
                        hg.get(1).isNull() ? 0f : hg.getFloat(1),
                        hg.get(2).isNull() ? 0f : hg.getFloat(2)
                };
            }
            joint = new AssetDef.JointDef(type, axis, hinge, degPerSec);
            if (j.has("states") && j.get("states").isObject()) {
                for (JsonValue st = j.get("states").child; st != null; st = st.next) {
                    if (st.isNumber()) {
                        joint.states.put(st.name.toLowerCase(), new float[]{st.asFloat()});
                    } else if (st.isArray()) {
                        float[] vec = new float[st.size];
                        for (int k = 0; k < st.size; k++) {
                            vec[k] = st.get(k).isNull() ? 0f : st.getFloat(k);
                        }
                        joint.states.put(st.name.toLowerCase(), vec);
                    }
                }
            }
        }

        AssetDef.PartDef partDef = new AssetDef.PartDef(
                name, file, resolvedPath, size, offset, pivot, at, joint, slot, kind, mirror, modelId,
                partScale, emissive, anim
        );

        // Per-part attaches
        if (p.has("attach") && p.get("attach").isObject()) {
            for (JsonValue attNode = p.get("attach").child; attNode != null; attNode = attNode.next) {
                if (attNode.isObject()) {
                    float[] posVec = attNode.has("pos") && attNode.get("pos").isArray() && attNode.get("pos").size >= 3
                            ? new float[]{attNode.get("pos").getFloat(0), attNode.get("pos").getFloat(1), attNode.get("pos").getFloat(2)}
                            : new float[3];
                    float[] dirVec = attNode.has("dir") && attNode.get("dir").isArray() && attNode.get("dir").size >= 3
                            ? new float[]{attNode.get("dir").getFloat(0), attNode.get("dir").getFloat(1), attNode.get("dir").getFloat(2)}
                            : new float[3];
                    partDef.attaches.put(attNode.name, new AssetDef.AttachDef(posVec, dirVec));
                }
            }
        }

        // Visibility / Hidden-in
        if (p.has("hidden_in") && p.get("hidden_in").isArray()) {
            for (JsonValue h = p.get("hidden_in").child; h != null; h = h.next) {
                partDef.hiddenIn.add(h.asString().toLowerCase());
            }
        }
        if (p.has("hidden_states") && p.get("hidden_states").isArray()) {
            for (JsonValue h = p.get("hidden_states").child; h != null; h = h.next) {
                partDef.hiddenIn.add(h.asString().toLowerCase());
            }
        }
        if (p.has("hidden") && p.get("hidden").isArray()) {
            for (JsonValue h = p.get("hidden").child; h != null; h = h.next) {
                partDef.hiddenIn.add(h.asString().toLowerCase());
            }
        }


        return partDef;
    }

    private static AssetDef.PaletteDef parsePalette(JsonValue pal) {
        if (pal == null || !pal.isObject()) {
            return null;
        }

        int[] coverIndices = null;
        if (pal.has("cover_indices") && pal.get("cover_indices").isArray()) {
            JsonValue ci = pal.get("cover_indices");
            coverIndices = new int[ci.size];
            for (int i = 0; i < ci.size; i++) {
                coverIndices[i] = ci.getInt(i);
            }
        }

        AssetDef.PaletteDef paletteDef = new AssetDef.PaletteDef(coverIndices);

        // Check cover_palettes, palettes, colors, appearance
        JsonValue coverPalettes = pal.has("cover_palettes") ? pal.get("cover_palettes") : pal.get("palettes");
        if (coverPalettes != null && coverPalettes.isObject()) {
            for (JsonValue nameNode = coverPalettes.child; nameNode != null; nameNode = nameNode.next) {
                String pName = nameNode.name;
                Map<Integer, int[]> indexColors = new LinkedHashMap<>();
                if (nameNode.isObject()) {
                    for (JsonValue idxNode = nameNode.child; idxNode != null; idxNode = idxNode.next) {
                        try {
                            int idx = Integer.parseInt(idxNode.name);
                            if (idxNode.isArray() && idxNode.size >= 3) {
                                indexColors.put(idx, new int[]{idxNode.getInt(0), idxNode.getInt(1), idxNode.getInt(2)});
                            }
                        } catch (NumberFormatException ignored) {
                        }
                    }
                }
                paletteDef.namedPalettes.put(pName.toLowerCase(), indexColors);
            }
        }

        // Check general colors map e.g. "colors": { "1": [100,70,41] }
        if (pal.has("colors") && pal.get("colors").isObject()) {
            Map<Integer, int[]> indexColors = new LinkedHashMap<>();
            for (JsonValue idxNode = pal.get("colors").child; idxNode != null; idxNode = idxNode.next) {
                try {
                    int idx = Integer.parseInt(idxNode.name);
                    if (idxNode.isArray() && idxNode.size >= 3) {
                        indexColors.put(idx, new int[]{idxNode.getInt(0), idxNode.getInt(1), idxNode.getInt(2)});
                    }
                } catch (NumberFormatException ignored) {
                }
            }
            paletteDef.namedPalettes.put("default", indexColors);
        }

        return paletteDef;
    }

    private static float[] parseSkeletonAttr(JsonValue skel) {
        if (skel == null) {
            return null;
        }
        if (skel.has("_flat_array_used_in_test") && skel.get("_flat_array_used_in_test").isArray()) {
            JsonValue arr = skel.get("_flat_array_used_in_test");
            float[] res = new float[arr.size];
            for (int i = 0; i < arr.size; i++) {
                res[i] = arr.getFloat(i);
            }
            return res;
        }
        if (skel.isArray()) {
            float[] res = new float[skel.size];
            for (int i = 0; i < skel.size; i++) {
                res[i] = skel.getFloat(i);
            }
            return res;
        }
        if (skel.isObject()) {
            List<Float> floats = new ArrayList<>();
            for (JsonValue child = skel.child; child != null; child = child.next) {
                if (child.name != null && child.name.startsWith("_")) {
                    continue;
                }
                if (child.isArray()) {
                    for (int i = 0; i < child.size; i++) {
                        floats.add(child.getFloat(i));
                    }
                } else if (child.isNumber()) {
                    floats.add(child.asFloat());
                }
            }
            float[] res = new float[floats.size()];
            for (int i = 0; i < floats.size(); i++) {
                res[i] = floats.get(i);
            }
            return res;
        }
        return null;
    }

    private static void logError(String message, Throwable t) {
        if (Gdx.app != null) {
            if (t != null) {
                Gdx.app.error("CustomAssetRegistry", message, t);
            } else {
                Gdx.app.error("CustomAssetRegistry", message);
            }
        } else {
            System.err.println("[CustomAssetRegistry] " + message);
            if (t != null) {
                t.printStackTrace(System.err);
            }
        }
    }
}
