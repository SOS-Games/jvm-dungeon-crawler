package io.github.jvmdc.dungeongen.corridor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.jvmdc.dungeongen.LayoutConfig;
import io.github.jvmdc.dungeongen.SnapshotAssert;
import io.github.jvmdc.dungeongen.common.AsciiMap;
import io.github.jvmdc.dungeongen.common.Corridor;
import io.github.jvmdc.dungeongen.common.Dungeon;
import io.github.jvmdc.dungeongen.common.Room;
import io.github.jvmdc.dungeongen.common.Tile;
import io.github.jvmdc.dungeongen.common.TileGrid;
import java.util.List;
import org.junit.jupiter.api.Test;

class CorridorNetworkGeneratorTest {
    private static final LayoutConfig SMALL = new LayoutConfig(80, 48, 10, 3, 3, 3, 3, 5, 0.2);
    private static final LayoutConfig VARIED = new LayoutConfig(100, 70, 8, 3, 6, 3, 6, 5, 0.25);

    private final CorridorNetworkGenerator generator = new CorridorNetworkGenerator();

    @Test
    void sameSeedProducesTheSameDungeon() {
        Dungeon first = generator.generate(SMALL, 42L);
        Dungeon second = generator.generate(SMALL, 42L);
        assertEquals(AsciiMap.render(first), AsciiMap.render(second));
        assertEquals(first.corridors.size(), second.corridors.size());
        for (int i = 0; i < first.corridors.size(); i++) {
            assertEquals(first.corridors.get(i).id, second.corridors.get(i).id);
            assertEquals(0, first.corridors.get(0).id);
        }
        Dungeon again = generator.generate(SMALL, 42L);
        assertEquals(0, again.corridors.get(0).id);
    }

    @Test
    void fixedRoomsStayValidAcrossSeeds() {
        for (int seed = 0; seed < 500; seed++) {
            assertValid(generator.generate(SMALL, seed), seed);
        }
    }

    @Test
    void largerRoomsStayValidAcrossSeeds() {
        for (int seed = 0; seed < 50; seed++) {
            assertValid(generator.generate(VARIED, 10_000L + seed), 10_000L + seed);
        }
    }

    @Test
    void goldenMaps() {
        assertGolden("golden/seed-1.txt", new LayoutConfig(36, 22, 6, 3, 3, 3, 3, 5, 0.2), 1L);
        assertGolden("golden/seed-42.txt", new LayoutConfig(36, 22, 6, 3, 3, 3, 3, 5, 0.2), 42L);
        assertGolden("golden/seed-7-large-rooms.txt",
                new LayoutConfig(42, 28, 5, 3, 5, 3, 5, 5, 0.3), 7L);
    }

    private void assertGolden(String resource, LayoutConfig config, long seed) {
        SnapshotAssert.matches(resource, AsciiMap.render(generator.generate(config, seed)));
    }

    private static void assertValid(Dungeon dungeon, long seed) {
        String where = "seed " + seed;
        List<Room> rooms = dungeon.rooms;
        assertTrue(rooms.size() >= 2, where);
        int buffer = dungeon.config.roomBuffer;
        for (int i = 0; i < rooms.size(); i++) {
            for (int j = i + 1; j < rooms.size(); j++) {
                Room a = rooms.get(i);
                Room b = rooms.get(j);
                int gapX = gap(a.x, a.x + a.width, b.x, b.x + b.width);
                int gapY = gap(a.y, a.y + a.height, b.y, b.y + b.height);
                assertTrue(gapX >= buffer || gapY >= buffer, where);
            }
        }
        assertTrue(dungeon.graph.allReachable(), where);
        int shortcuts = 0;
        for (Corridor corridor : dungeon.corridors) {
            if (corridor.kind == Corridor.Kind.SHORTCUT) {
                shortcuts++;
            }
            assertEquals(corridor.id, dungeon.corridors.indexOf(corridor), where);
            int[] path = corridor.path;
            assertTrue(path.length >= CorridorNetworkGenerator.MIN_CORRIDOR_TILES, where);
            Room a = rooms.get(corridor.roomA);
            Room b = rooms.get(corridor.roomB);
            assertTrue(touches(dungeon.grid, a, path[0]), where);
            assertTrue(touches(dungeon.grid, b, path[path.length - 1]), where);
            assertTrue(dungeon.graph.adjacent(corridor.roomA, corridor.roomB), where);
            for (int i = 0; i < path.length; i++) {
                int cell = path[i];
                assertEquals(Tile.CORRIDOR_FLOOR, dungeon.grid.get(cell), where);
                int x = dungeon.grid.x(cell);
                int y = dungeon.grid.y(cell);
                for (Room room : rooms) {
                    assertFalse(room.contains(x, y), where);
                }
                if (i > 0) {
                    int prev = path[i - 1];
                    int step = Math.abs(dungeon.grid.x(prev) - x) + Math.abs(dungeon.grid.y(prev) - y);
                    assertEquals(1, step, where);
                }
            }
        }
        int cap = (int) (rooms.size() * dungeon.config.loopRatio);
        assertTrue(shortcuts <= cap, where);
    }

    /** Empty tiles between two half-open ranges, or 0 when the ranges overlap. */
    private static int gap(int a0, int a1, int b0, int b1) {
        if (a1 <= b0) {
            return b0 - a1;
        }
        if (b1 <= a0) {
            return a0 - b1;
        }
        return 0;
    }

    /** Shares an edge with the room. A diagonal corner does not count. */
    private static boolean touches(TileGrid grid, Room room, int cell) {
        int x = grid.x(cell);
        int y = grid.y(cell);
        if (room.contains(x, y)) {
            return false;
        }
        return room.contains(x - 1, y) || room.contains(x + 1, y)
                || room.contains(x, y - 1) || room.contains(x, y + 1);
    }
}
