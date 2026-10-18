package io.github.jvmdc.viewer;

import io.github.jvmdc.assets.AssetRoots;

import java.io.IOException;
import java.nio.file.Path;

/** Loads one posed body from the converted figure manifests. */
public final class FigureLibrary {
    private FigureLibrary() {
    }

    public static boolean available(String species, String body) {
        if (species == null || species.isBlank()) {
            return false;
        }
        try {
            if (find(species, body) != null) {
                return true;
            }
        } catch (IOException ignored) {
        }
        return AnimalAttrs.get(body, species).length > 0 || "biped_large".equals(body) || "biped_small".equals(body);
    }

    /** Idle clip when the body has one. The rig is ready to draw. */
    public static FigureAnim load(String species, String body) throws IOException {
        NpcFigure.Assembly assembly = find(species, body);
        if (assembly == null) {
            return null;
        }
        FigureAnim rig = "bird_medium".equals(assembly.body)
                ? BirdIdle.load(assembly)
                : AnimalIdle.load(assembly);
        if (rig == null) {
            return null;
        }
        for (int i = 0; i < rig.clipCount(); i++) {
            if ("Idle".equals(rig.clipName(i))) {
                rig.setClip(i);
                break;
            }
        }
        return rig;
    }

    private static NpcFigure.Assembly find(String species, String body) throws IOException {
        AssetRoots roots = AssetRoots.locate();
        Path assets = roots.source != null ? roots.source : roots.assets;
        for (NpcFigure.Assembly assembly : NpcFigure.bodies(assets)) {
            if (body.equals(assembly.body) && species.equals(assembly.species)) {
                return assembly;
            }
        }
        return null;
    }
}
