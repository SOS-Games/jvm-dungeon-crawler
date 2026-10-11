package io.github.jvmdc.dungeongen;

import io.github.jvmdc.dungeongen.common.Dungeon;

/** One way to fill a grid with rooms and passages. */
public interface LayoutGenerator {
    Dungeon generate(LayoutConfig config, long seed);
}
