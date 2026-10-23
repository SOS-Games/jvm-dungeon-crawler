package io.github.jvmdc.assets;

import com.badlogic.gdx.utils.JsonValue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Definition parsed from a custom asset JSON definition file.
 */
public final class AssetDef {
    public static final class JointDef {
        public final String type;
        public final float[] axis;
        public final float[] hinge;
        public final float degPerSec;
        public final Map<String, float[]> states = new HashMap<>();

        public JointDef(String type, float[] axis, float[] hinge, float degPerSec) {
            this.type = type;
            this.axis = axis;
            this.hinge = hinge;
            this.degPerSec = degPerSec;
        }
    }

    public static final class AttachDef {
        public final float[] pos;
        public final float[] dir;

        public AttachDef(float[] pos, float[] dir) {
            this.pos = pos;
            this.dir = dir;
        }
    }

    public static final class PartDef {
        public final String name;
        public final String file;
        public final Path resolvedPath;
        public final int[] size;
        public final float[] offset;
        public final float[] pivot;
        public final float[] at;
        public final JointDef joint;
        public final Set<String> hiddenIn = new HashSet<>();
        public final String slot;
        public final String kind;
        public final boolean mirror;
        public final String modelId;
        public final float scale;
        public final boolean emissive;
        public final String anim;
        public final Map<String, AttachDef> attaches = new HashMap<>();

        public PartDef(String name, String file, Path resolvedPath, int[] size, float[] offset,
                       float[] pivot, float[] at, JointDef joint, String slot, String kind,
                       boolean mirror, String modelId, float scale, boolean emissive, String anim) {
            this.name = name;
            this.file = file;
            this.resolvedPath = resolvedPath;
            this.size = size;
            this.offset = offset;
            this.pivot = pivot;
            this.at = at;
            this.joint = joint;
            this.slot = slot;
            this.kind = kind;
            this.mirror = mirror;
            this.modelId = modelId;
            this.scale = scale;
            this.emissive = emissive;
            this.anim = anim;
        }

        public boolean isHiddenIn(String state) {
            if (state == null || hiddenIn.isEmpty()) {
                return false;
            }
            return hiddenIn.contains(state.toLowerCase());
        }
    }

    public static final class BodyDef {
        public final String family;
        public final String species;
        public final String bodyType;

        public BodyDef(String family, String species, String bodyType) {
            this.family = family;
            this.species = species;
            this.bodyType = bodyType;
        }
    }

    public static final class PaletteDef {
        /** Named palettes mapping index (1-256) to RGB int array [r, g, b]. */
        public final Map<String, Map<Integer, int[]>> namedPalettes = new HashMap<>();
        /** Cover indices e.g. [1, 2, 3] */
        public final int[] coverIndices;

        public PaletteDef(int[] coverIndices) {
            this.coverIndices = coverIndices;
        }
    }

    public final String id;
    public final String category;
    public final Path jsonPath;
    public final Path folder;
    public final float scale;
    public final String units;
    public final List<PartDef> parts = new ArrayList<>();
    public final Map<String, float[]> attachPoints = new HashMap<>();
    public final PaletteDef palette;
    public final String paletteSet;
    public final Map<String, Object> tonePalettes = new HashMap<>();
    public final JsonValue gameplayData;
    public final String licence;
    public final BodyDef body;
    public final float[] skeletonAttr;
    public final JsonValue pivots;
    public final JsonValue states;
    public final JsonValue removedBakedPreviews;
    public final JsonValue rawJson;

    public int[] getSwappedPalette(int[] basePalette, String paletteName) {
        if (basePalette == null) {
            return null;
        }
        int[] newPalette = basePalette.clone();
        if (palette != null && paletteName != null) {
            Map<Integer, int[]> swap = palette.namedPalettes.get(paletteName.toLowerCase());
            if (swap != null) {
                io.github.jvmdc.converter.VoxMesher.applyPaletteSwap(newPalette, swap);
            }
        }
        return newPalette;
    }

    public List<PartDef> visibleParts(String activeState) {
        if (activeState == null || activeState.isBlank()) {
            return parts;
        }
        List<PartDef> result = new ArrayList<>();
        for (PartDef part : parts) {
            if (!part.isHiddenIn(activeState)) {
                result.add(part);
            }
        }
        return result;
    }

    public AssetDef(String id, String category, Path jsonPath, Path folder, float scale, String units,
                    PaletteDef palette, String paletteSet, JsonValue gameplayData, String licence, BodyDef body,
                    float[] skeletonAttr, JsonValue pivots, JsonValue states,
                    JsonValue removedBakedPreviews, JsonValue rawJson) {
        this.id = id;
        this.category = category;
        this.jsonPath = jsonPath;
        this.folder = folder;
        this.scale = scale;
        this.units = units;
        this.palette = palette;
        this.paletteSet = paletteSet;
        this.gameplayData = gameplayData;
        this.licence = licence;
        this.body = body;
        this.skeletonAttr = skeletonAttr;
        this.pivots = pivots;
        this.states = states;
        this.removedBakedPreviews = removedBakedPreviews;
        this.rawJson = rawJson;
    }
}
