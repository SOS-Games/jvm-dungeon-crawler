package io.github.jvmdc.dungeongen.common;

/** Text picture of a dungeon, one row per line, y downward. */
public final class AsciiMap {
    /** What each character means. Rock is a space so a built wall can stay {@code #}. */
    public static final String LEGEND =
            "legend: space=rock #=wall :=ground .=room ,=hall +=door %=locked ?=secret <=up >=down ~=water !=lava _=pit";

    private AsciiMap() {
    }

    public static char glyph(Tile tile) {
        return switch (tile) {
            case WALL -> '#';
            case ROCK -> ' ';
            case GROUND -> ':';
            case ROOM_FLOOR -> '.';
            case CORRIDOR_FLOOR -> ',';
            case DOOR -> '+';
            case LOCKED_DOOR -> '%';
            case SECRET_DOOR -> '?';
            case STAIRS_UP -> '<';
            case STAIRS_DOWN -> '>';
            case WATER -> '~';
            case DEEP_WATER -> 'W';
            case LAVA -> '!';
            case PIT -> '_';
            case TRAP_PLATE -> '^';
        };
    }

    public static String renderWithLegend(Dungeon dungeon) {
        return LEGEND + "\n" + render(dungeon);
    }

    public static String render(Dungeon dungeon) {
        TileGrid grid = dungeon.grid;
        StringBuilder out = new StringBuilder(grid.height * (grid.width + 1));
        for (int y = 0; y < grid.height; y++) {
            for (int x = 0; x < grid.width; x++) {
                out.append(glyph(grid.get(x, y)));
            }
            out.append('\n');
        }
        return out.toString();
    }
}
