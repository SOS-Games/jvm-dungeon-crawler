package io.github.jvmdc.dungeongen.common;

/** A compass step on the grid. North is toward smaller y, because y grows downward. */
public enum Facing {
    N(0, -1),
    E(1, 0),
    S(0, 1),
    W(-1, 0),
    NONE(0, 0);

    public final int dx;
    public final int dy;

    Facing(int dx, int dy) {
        this.dx = dx;
        this.dy = dy;
    }

    public static Facing of(int dx, int dy) {
        for (Facing facing : values()) {
            if (facing.dx == dx && facing.dy == dy && facing != NONE) {
                return facing;
            }
        }
        return NONE;
    }
}
