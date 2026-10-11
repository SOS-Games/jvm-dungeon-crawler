package io.github.jvmdc.dungeongen;

import io.github.jvmdc.dungeongen.common.Dungeon;
import io.github.jvmdc.dungeongen.common.SeededRandom;

/** Runs optional placers after the rooms exist. An empty list does not touch the random stream. */
public final class FeatureHooks {
    private FeatureHooks() {
    }

    public static void apply(Dungeon dungeon, LayoutConfig config) {
        if (config.placers.isEmpty()) {
            return;
        }
        SeededRandom random = new SeededRandom(dungeon.seed ^ 0x9E3779B97F4A7C15L);
        for (FeaturePlacer placer : config.placers) {
            placer.place(dungeon, random);
        }
    }
}
