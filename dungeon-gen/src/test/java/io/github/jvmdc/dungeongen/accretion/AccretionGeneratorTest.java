package io.github.jvmdc.dungeongen.accretion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.jvmdc.dungeongen.LayoutConfig;
import io.github.jvmdc.dungeongen.SnapshotAssert;
import io.github.jvmdc.dungeongen.common.AsciiMap;
import io.github.jvmdc.dungeongen.common.Dungeon;
import io.github.jvmdc.dungeongen.common.FloodFill;
import io.github.jvmdc.dungeongen.common.Room;
import io.github.jvmdc.dungeongen.common.Tile;
import io.github.jvmdc.dungeongen.common.TileGrid;
import org.junit.jupiter.api.Test;

class AccretionGeneratorTest {
    private static final LayoutConfig MAP = LayoutConfig.attached(36, 36);
    private static final LayoutConfig GOLDEN = LayoutConfig.attached(28, 28);

    private final AccretionGenerator generator = new AccretionGenerator();

    @Test
    void sameSeedProducesTheSameDungeon() {
        Dungeon first = generator.generate(MAP, 42L);
        Dungeon second = generator.generate(MAP, 42L);
        assertEquals(AsciiMap.render(first), AsciiMap.render(second));
        assertEquals(first.stairsUp, second.stairsUp);
        assertEquals(first.stairsDown, second.stairsDown);
    }

    @Test
    void layoutHoldsAcrossAThousandSeeds() {
        for (int seed = 0; seed < 1000; seed++) {
            assertValid(generator.generate(MAP, seed), seed);
        }
    }

    @Test
    void attachedRoomsLeaveOpenGroundAndRock() {
        int looped = 0;
        for (int seed = 0; seed < 40; seed++) {
            Dungeon dungeon = generator.generate(LayoutConfig.attached(), seed);
            assertValid(dungeon, seed);
            if (hasLoop(dungeon)) {
                looped++;
            }
        }
        assertTrue(looped >= 20, "loops " + looped);
    }

    @Test
    void cavesDoNotCutOffOpenRooms() {
        LayoutConfig caves = new LayoutConfig(
                56, 40, 8, 4, 6, 4, 6, 1, 0.25, 3, 0.5, 0.25, true, 1);
        for (int seed = 0; seed < 25; seed++) {
            assertValid(generator.generate(caves, 5000L + seed), 5000L + seed);
        }
    }

    @Test
    void goldenMaps() {
        check("golden/accretion-seed-1.txt", 1L);
        check("golden/accretion-seed-3.txt", 3L);
        check("golden/accretion-seed-11.txt", 11L);
    }

    private void check(String resource, long seed) {
        Dungeon dungeon = generator.generate(GOLDEN, seed);
        assertValid(dungeon, seed);
        SnapshotAssert.matches(resource, AsciiMap.renderWithLegend(dungeon));
    }

    private static void assertValid(Dungeon dungeon, long seed) {
        String where = "seed " + seed;
        assertTrue(dungeon.stairsUp >= 0, where);
        assertTrue(dungeon.stairsDown >= 0, where);
        assertTrue(dungeon.stairsUp != dungeon.stairsDown, where);
        TileGrid grid = dungeon.grid;
        assertEquals(Tile.STAIRS_UP, grid.get(dungeon.stairsUp), where);
        assertEquals(Tile.STAIRS_DOWN, grid.get(dungeon.stairsDown), where);
        assertTrue(openBlock(grid, dungeon.stairsUp), where);
        assertTrue(openBlock(grid, dungeon.stairsDown), where);
        assertFalse(inClosedRoom(dungeon, dungeon.stairsUp), where);
        assertFalse(inClosedRoom(dungeon, dungeon.stairsDown), where);

        if (dungeon.config.floorRatio > 0 && grid.width >= 56 && grid.height >= 56) {
            double open = openFraction(dungeon);
            assertTrue(dungeon.rooms.size() <= 35, where + " rooms " + dungeon.rooms.size());
            assertTrue(dungeon.rooms.size() >= 18, where + " rooms " + dungeon.rooms.size());
            assertTrue(open >= 0.34 && open <= 0.44, where + " open " + open);
            assertTrue(groundShare(dungeon) >= 0.15, where + " ground " + groundShare(dungeon));
            assertTrue(rockMasses(grid, dungeon.config.rockBorder, 40) >= 2, where);
        }
        boolean[] reached = FloodFill.region(grid, dungeon.stairsUp, Tile::isOpen);
        assertTrue(reached[dungeon.stairsDown], where);
        for (int i = 0; i < grid.width * grid.height; i++) {
            if (grid.get(i) == Tile.GROUND) {
                assertTrue(reached[i], where + " ground " + grid.x(i) + "," + grid.y(i));
            }
        }
        for (Room room : dungeon.rooms) {
            assertTrue(ringIntact(grid, room), where + " ring " + room.id);
            if (room.secret) {
                continue;
            }
            boolean hit = false;
            for (int y = room.y; y < room.y + room.height && !hit; y++) {
                for (int x = room.x; x < room.x + room.width; x++) {
                    if (room.contains(x, y) && grid.get(x, y).isOpen() && reached[grid.index(x, y)]) {
                        hit = true;
                    }
                }
            }
            assertTrue(hit, where + " room " + room.id);
        }

        for (int y = 0; y < grid.height; y++) {
            for (int x = 0; x < grid.width; x++) {
                if (!grid.get(x, y).isDoor()) {
                    continue;
                }
                assertTrue(doorOk(grid, x, y), where + " door " + x + "," + y);
            }
        }
        assertOpenRegions(grid, where);
        assertNoDeadCorridor(grid, where);

        for (int i = 0; i < dungeon.rooms.size(); i++) {
            for (int j = i + 1; j < dungeon.rooms.size(); j++) {
                assertFalse(overlaps(dungeon.rooms.get(i), dungeon.rooms.get(j)), where);
            }
        }
    }

    private static boolean openBlock(TileGrid grid, int index) {
        int x = grid.x(index);
        int y = grid.y(index);
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (!grid.inBounds(x + dx, y + dy) || !grid.get(x + dx, y + dy).isOpen()) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean inClosedRoom(Dungeon dungeon, int index) {
        int x = dungeon.grid.x(index);
        int y = dungeon.grid.y(index);
        for (Room room : dungeon.rooms) {
            if (room.contains(x, y) && (room.secret || room.locked)) {
                return true;
            }
        }
        return false;
    }

    private static boolean doorOk(TileGrid grid, int x, int y) {
        return sideOk(grid, x - 1, y, x + 1, y) || sideOk(grid, x, y - 1, x, y + 1);
    }

    /** Both cells are walkable, and one of them has two walkable neighbors. */
    private static boolean sideOk(TileGrid grid, int ax, int ay, int bx, int by) {
        if (!walkable(grid, ax, ay) || !walkable(grid, bx, by)) {
            return false;
        }
        return neighbors(grid, ax, ay) >= 2 || neighbors(grid, bx, by) >= 2;
    }

    private static boolean walkable(TileGrid grid, int x, int y) {
        return grid.inBounds(x, y) && grid.get(x, y).isOpen();
    }

    private static int neighbors(TileGrid grid, int x, int y) {
        int count = 0;
        for (int dir = 0; dir < 4; dir++) {
            int nx = x + (dir == 0 ? 0 : dir == 1 ? 1 : dir == 2 ? 0 : -1);
            int ny = y + (dir == 0 ? -1 : dir == 1 ? 0 : dir == 2 ? 1 : 0);
            if (walkable(grid, nx, ny)) {
                count++;
            }
        }
        return count;
    }

    private static void assertOpenRegions(TileGrid grid, String where) {
        int area = grid.width * grid.height;
        boolean[] seen = new boolean[area];
        int[] queue = new int[area];
        for (int i = 0; i < area; i++) {
            if (seen[i] || grid.get(i) != Tile.GROUND) {
                continue;
            }
            int head = 0;
            int tail = 0;
            seen[i] = true;
            queue[tail++] = i;
            int minX = grid.x(i);
            int minY = grid.y(i);
            int maxX = minX;
            int maxY = minY;
            int size = 0;
            while (head < tail) {
                int cell = queue[head++];
                size++;
                int x = grid.x(cell);
                int y = grid.y(cell);
                minX = Math.min(minX, x);
                minY = Math.min(minY, y);
                maxX = Math.max(maxX, x);
                maxY = Math.max(maxY, y);
                for (int dir = 0; dir < 4; dir++) {
                    int nx = x + (dir == 0 ? 0 : dir == 1 ? 1 : dir == 2 ? 0 : -1);
                    int ny = y + (dir == 0 ? -1 : dir == 1 ? 0 : dir == 2 ? 1 : 0);
                    if (!grid.inBounds(nx, ny)) {
                        continue;
                    }
                    int next = grid.index(nx, ny);
                    if (!seen[next] && grid.get(next) == Tile.GROUND) {
                        seen[next] = true;
                        queue[tail++] = next;
                    }
                }
            }
            if (size < 12 || maxX - minX < 3 || maxY - minY < 3) {
                continue;
            }
            assertTrue(openings(grid, seen, i) >= 2, where + " openings");
        }
    }

    private static int openings(TileGrid grid, boolean[] region, int sample) {
        int area = region.length;
        boolean[] component = new boolean[area];
        int[] queue = new int[area];
        int head = 0;
        int tail = 0;
        component[sample] = true;
        queue[tail++] = sample;
        while (head < tail) {
            int cell = queue[head++];
            int x = grid.x(cell);
            int y = grid.y(cell);
            for (int dir = 0; dir < 4; dir++) {
                int nx = x + (dir == 0 ? 0 : dir == 1 ? 1 : dir == 2 ? 0 : -1);
                int ny = y + (dir == 0 ? -1 : dir == 1 ? 0 : dir == 2 ? 1 : 0);
                if (!grid.inBounds(nx, ny)) {
                    continue;
                }
                int next = grid.index(nx, ny);
                if (!component[next] && grid.get(next) == Tile.GROUND) {
                    component[next] = true;
                    queue[tail++] = next;
                }
            }
        }
        boolean[] touch = new boolean[area];
        boolean[] counted = new boolean[area];
        for (int i = 0; i < area; i++) {
            if (!component[i]) {
                continue;
            }
            int x = grid.x(i);
            int y = grid.y(i);
            for (int dir = 0; dir < 4; dir++) {
                int nx = x + (dir == 0 ? 0 : dir == 1 ? 1 : dir == 2 ? 0 : -1);
                int ny = y + (dir == 0 ? -1 : dir == 1 ? 0 : dir == 2 ? 1 : 0);
                if (!grid.inBounds(nx, ny)) {
                    continue;
                }
                Tile tile = grid.get(nx, ny);
                if (tile == Tile.DOOR || tile == Tile.ROOM_FLOOR || tile == Tile.CORRIDOR_FLOOR
                        || tile == Tile.STAIRS_UP || tile == Tile.STAIRS_DOWN) {
                    touch[grid.index(nx, ny)] = true;
                }
            }
        }
        int openings = 0;
        for (int i = 0; i < area; i++) {
            if (!touch[i] || counted[i]) {
                continue;
            }
            if (grid.get(i) == Tile.DOOR) {
                counted[i] = true;
                openings++;
                continue;
            }
            head = 0;
            tail = 0;
            counted[i] = true;
            queue[tail++] = i;
            while (head < tail) {
                int cell = queue[head++];
                int x = grid.x(cell);
                int y = grid.y(cell);
                for (int dir = 0; dir < 4; dir++) {
                    int nx = x + (dir == 0 ? 0 : dir == 1 ? 1 : dir == 2 ? 0 : -1);
                    int ny = y + (dir == 0 ? -1 : dir == 1 ? 0 : dir == 2 ? 1 : 0);
                    if (!grid.inBounds(nx, ny)) {
                        continue;
                    }
                    int next = grid.index(nx, ny);
                    if (!counted[next] && touch[next] && grid.get(next) != Tile.DOOR) {
                        counted[next] = true;
                        queue[tail++] = next;
                    }
                }
            }
            openings++;
        }
        return openings;
    }

    private static void assertNoDeadCorridor(TileGrid grid, String where) {
        for (int y = 0; y < grid.height; y++) {
            for (int x = 0; x < grid.width; x++) {
                if (grid.get(x, y) != Tile.CORRIDOR_FLOOR) {
                    continue;
                }
                assertTrue(neighbors(grid, x, y) >= 2, where + " hall " + x + "," + y);
            }
        }
    }

    private static boolean spansFloor(TileGrid grid, int x, int y) {
        return pair(grid, x - 1, y, x + 1, y) || pair(grid, x, y - 1, x, y + 1);
    }

    private static boolean pair(TileGrid grid, int ax, int ay, int bx, int by) {
        return grid.inBounds(ax, ay) && grid.inBounds(bx, by)
                && floor(grid.get(ax, ay)) && floor(grid.get(bx, by));
    }

    private static boolean floor(Tile tile) {
        return tile.isOpen();
    }

    private static boolean ringIntact(TileGrid grid, Room room) {
        for (int y = room.y; y < room.y + room.height; y++) {
            for (int x = room.x; x < room.x + room.width; x++) {
                if (!room.contains(x, y) || !grid.get(x, y).isOpen()) {
                    continue;
                }
                for (int dir = 0; dir < 4; dir++) {
                    int nx = x + (dir == 0 ? 0 : dir == 1 ? 1 : dir == 2 ? 0 : -1);
                    int ny = y + (dir == 0 ? -1 : dir == 1 ? 0 : dir == 2 ? 1 : 0);
                    if (room.contains(nx, ny)) {
                        continue;
                    }
                    if (!grid.inBounds(nx, ny)) {
                        return false;
                    }
                    Tile outside = grid.get(nx, ny);
                    if (outside != Tile.WALL && !outside.isDoor()) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private static double groundShare(Dungeon dungeon) {
        TileGrid grid = dungeon.grid;
        int open = 0;
        int ground = 0;
        int area = grid.width * grid.height;
        for (int i = 0; i < area; i++) {
            if (grid.get(i).isOpen()) {
                open++;
            }
            if (grid.get(i) == Tile.GROUND) {
                ground++;
            }
        }
        return open == 0 ? 0 : ground / (double) open;
    }

    private static int rockMasses(TileGrid grid, int border, int minSize) {
        int area = grid.width * grid.height;
        boolean[] seen = new boolean[area];
        int masses = 0;
        int[] queue = new int[area];
        for (int y = border; y < grid.height - border; y++) {
            for (int x = border; x < grid.width - border; x++) {
                int start = grid.index(x, y);
                if (seen[start] || grid.get(start) != Tile.ROCK) {
                    continue;
                }
                int head = 0;
                int tail = 0;
                seen[start] = true;
                queue[tail++] = start;
                int size = 0;
                while (head < tail) {
                    int cell = queue[head++];
                    size++;
                    int cx = grid.x(cell);
                    int cy = grid.y(cell);
                    for (int dir = 0; dir < 4; dir++) {
                        int nx = cx + (dir == 0 ? 0 : dir == 1 ? 1 : dir == 2 ? 0 : -1);
                        int ny = cy + (dir == 0 ? -1 : dir == 1 ? 0 : dir == 2 ? 1 : 0);
                        if (nx < border || ny < border || nx >= grid.width - border || ny >= grid.height - border) {
                            continue;
                        }
                        int next = grid.index(nx, ny);
                        if (seen[next] || grid.get(next) != Tile.ROCK) {
                            continue;
                        }
                        seen[next] = true;
                        queue[tail++] = next;
                    }
                }
                if (size >= minSize) {
                    masses++;
                }
            }
        }
        return masses;
    }

    private static double openFraction(Dungeon dungeon) {
        TileGrid grid = dungeon.grid;
        int open = 0;
        int area = grid.width * grid.height;
        for (int i = 0; i < area; i++) {
            if (grid.get(i).isOpen()) {
                open++;
            }
        }
        return open / (double) area;
    }

    private static boolean hasLoop(Dungeon dungeon) {
        int edges = 0;
        for (Room room : dungeon.rooms) {
            edges += dungeon.graph.neighbors(room.id).length;
        }
        return edges / 2 >= dungeon.rooms.size();
    }

    private static boolean overlaps(Room a, Room b) {
        int y0 = Math.max(a.y, b.y);
        int y1 = Math.min(a.y + a.height, b.y + b.height);
        int x0 = Math.max(a.x, b.x);
        int x1 = Math.min(a.x + a.width, b.x + b.width);
        for (int y = y0; y < y1; y++) {
            for (int x = x0; x < x1; x++) {
                if (a.contains(x, y) && b.contains(x, y)) {
                    return true;
                }
            }
        }
        return false;
    }
}
