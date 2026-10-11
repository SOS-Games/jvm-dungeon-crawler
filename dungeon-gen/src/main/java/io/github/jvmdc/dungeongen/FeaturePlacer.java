package io.github.jvmdc.dungeongen;

import io.github.jvmdc.dungeongen.common.Dungeon;
import io.github.jvmdc.dungeongen.common.SeededRandom;

/**
 * A later pass over a finished layout. Trap, loot, and spawn rules are not written yet.
 * A placer may read the grid and stamp tiles, but this module ships no concrete rules.
 */
public interface FeaturePlacer {
    void place(Dungeon dungeon, SeededRandom random);
}
