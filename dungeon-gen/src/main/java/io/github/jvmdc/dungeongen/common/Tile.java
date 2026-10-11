package io.github.jvmdc.dungeongen.common;

/** One cell of the dungeon grid. Codes are stable so a byte array can store them. */
public enum Tile {
    WALL((byte) 0),
    ROOM_FLOOR((byte) 1),
    CORRIDOR_FLOOR((byte) 2),
    DOOR((byte) 3),
    LOCKED_DOOR((byte) 4),
    SECRET_DOOR((byte) 5),
    STAIRS_UP((byte) 6),
    STAIRS_DOWN((byte) 7),
    WATER((byte) 8),
    DEEP_WATER((byte) 9),
    LAVA((byte) 10),
    PIT((byte) 11),
    TRAP_PLATE((byte) 12),
    /** Solid fill and the map border. A built room wall is {@link #WALL}. */
    ROCK((byte) 13),
    /** Walkable open ground outside rooms. */
    GROUND((byte) 14);

    public final byte code;

    Tile(byte code) {
        this.code = code;
    }

    public static Tile from(byte code) {
        return switch (code) {
            case 0 -> WALL;
            case 1 -> ROOM_FLOOR;
            case 2 -> CORRIDOR_FLOOR;
            case 3 -> DOOR;
            case 4 -> LOCKED_DOOR;
            case 5 -> SECRET_DOOR;
            case 6 -> STAIRS_UP;
            case 7 -> STAIRS_DOWN;
            case 8 -> WATER;
            case 9 -> DEEP_WATER;
            case 10 -> LAVA;
            case 11 -> PIT;
            case 12 -> TRAP_PLATE;
            case 13 -> ROCK;
            case 14 -> GROUND;
            default -> throw new IllegalArgumentException("tile " + code);
        };
    }

    public boolean isDoor() {
        return this == DOOR || this == LOCKED_DOOR || this == SECRET_DOOR;
    }

    /** Floor a walker can stand on. Locked and secret doors stay shut. */
    public boolean isOpen() {
        return this == ROOM_FLOOR || this == CORRIDOR_FLOOR || this == GROUND || this == DOOR
                || this == STAIRS_UP || this == STAIRS_DOWN
                || this == WATER || this == TRAP_PLATE;
    }
}
