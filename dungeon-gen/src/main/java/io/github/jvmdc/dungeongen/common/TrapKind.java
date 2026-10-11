package io.github.jvmdc.dungeongen.common;

/** Hazard marked by a trap placement. Floor kinds replace the tile with a plate. */
public enum TrapKind {
    SPIKE_FLOOR(true),
    ARROW_WALL(false),
    PIT_LID(true),
    MAGIC_WALL(false),
    BEAR_TRAP(true);

    public final boolean floor;

    TrapKind(boolean floor) {
        this.floor = floor;
    }
}
