package io.github.jvmdc.dungeongen.common;

/** A carved path from roomA to roomB. path holds cell indices, start through end. */
public final class Corridor {
    public enum Kind {
        /** Tree edge that carved on the first try. */
        MST,
        /** Replacement edge used when a tree corridor could not be dug. */
        REPAIR,
        /** Extra loop added after the dungeon is already connected. */
        SHORTCUT
    }

    public final int id;
    public final int roomA;
    public final int roomB;
    public final int[] path;
    public final Kind kind;

    public Corridor(int id, int roomA, int roomB, int[] path, Kind kind) {
        this.id = id;
        this.roomA = roomA;
        this.roomB = roomB;
        this.path = path.clone();
        this.kind = kind;
    }
}
