package io.github.jvmdc.dungeongen.place;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.jvmdc.dungeongen.LayoutConfig;
import io.github.jvmdc.dungeongen.SnapshotAssert;
import io.github.jvmdc.dungeongen.accretion.AccretionGenerator;
import io.github.jvmdc.dungeongen.common.Dungeon;
import io.github.jvmdc.dungeongen.common.Facing;
import io.github.jvmdc.dungeongen.common.Placement;
import io.github.jvmdc.dungeongen.common.PlacementType;
import io.github.jvmdc.dungeongen.common.Room;
import io.github.jvmdc.dungeongen.common.Tile;
import io.github.jvmdc.dungeongen.common.TileGrid;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PlacementPassTest {
    private static final LayoutConfig FULL = LayoutConfig.attached();
    private static final LayoutConfig SMALL = LayoutConfig.attached(28, 28);
    private final AccretionGenerator generator = new AccretionGenerator();

    @Test
    void sameSeedPlacesTheSameThings() {
        Dungeon first = generator.generate(FULL, 5L);
        Dungeon second = generator.generate(FULL, 5L);
        assertEquals(PlacementText.render(first), PlacementText.render(second));
    }

    @Test
    void rulesHoldAcrossAThousandSeeds() {
        int bosses = 0;
        int shops = 0;
        int fountains = 0;
        for (int seed = 0; seed < 1000; seed++) {
            Dungeon dungeon = generator.generate(FULL, seed);
            assertRules(dungeon, seed);
            if (has(dungeon, PlacementType.BOSS_SPAWN)) {
                bosses++;
            }
            if (has(dungeon, PlacementType.SHOP)) {
                shops++;
            }
            if (has(dungeon, PlacementType.FOUNTAIN)) {
                fountains++;
            }
        }
        assertTrue(bosses > 80 && bosses < 700, "bosses " + bosses);
        assertTrue(shops > 80 && shops < 800, "shops " + shops);
        assertTrue(fountains > 80 && fountains < 800, "fountains " + fountains);
    }

    @Test
    void placementSnapshots() {
        SnapshotAssert.matches("golden/placement-seed-1.txt",
                PlacementText.render(generator.generate(SMALL, 1L)));
        SnapshotAssert.matches("golden/placement-seed-11.txt",
                PlacementText.render(generator.generate(SMALL, 11L)));
    }

    private static void assertRules(Dungeon dungeon, long seed) {
        String where = "seed " + seed;
        TileGrid grid = dungeon.grid;
        int start = roomOf(dungeon, dungeon.stairsUp);
        int exit = roomOf(dungeon, dungeon.stairsDown);
        assertTrue(start >= 0, where);
        assertTrue(exit >= 0, where);
        Set<Long> used = new HashSet<>();
        int traps = 0;
        int spawns = 0;
        int bosses = 0;
        int shops = 0;
        int fountains = 0;
        int[] monsters = new int[dungeon.rooms.size()];
        int maxHop = 0;
        for (Room room : dungeon.rooms) {
            int hop = dungeon.graph.hops(start, room.id);
            if (hop > maxHop) {
                maxHop = hop;
            }
        }
        for (Placement placement : dungeon.placements) {
            assertTrue(grid.inBounds(placement.x, placement.y), where);
            assertTrue(used.add(key(placement.x, placement.y)), where + " stacked " + placement.x + "," + placement.y);
            int index = grid.index(placement.x, placement.y);
            if (placement.type == PlacementType.TRAP || placement.type == PlacementType.CHEST
                    || placement.type == PlacementType.MONSTER_SPAWN) {
                assertTrue(placement.roomId != start, where + " start " + placement.type);
                assertTrue(!nearStairs(grid, dungeon.stairsUp, placement.x, placement.y), where + " stairs " + placement.type);
            }
            if (placement.type == PlacementType.TRAP) {
                traps++;
                assertTrue(placement.trap != null, where);
                assertTrue(!grid.get(index).isDoor(), where);
                assertTrue(index != dungeon.stairsUp && index != dungeon.stairsDown, where);
                if (placement.trap.floor) {
                    assertEquals(Tile.TRAP_PLATE, grid.get(index), where);
                    assertEquals(Facing.NONE, placement.facing, where);
                    assertTrue(!besideDoor(grid, placement.x, placement.y), where);
                } else {
                    assertEquals(Tile.WALL, grid.get(index), where);
                    assertTrue(placement.facing != Facing.NONE, where);
                    int fx = placement.x + placement.facing.dx;
                    int fy = placement.y + placement.facing.dy;
                    assertTrue(grid.inBounds(fx, fy) && grid.get(fx, fy).isOpen(), where);
                }
            }
            if (placement.type == PlacementType.CHEST) {
                assertTrue(againstWall(grid, placement.x, placement.y), where + " chest");
                assertEquals(Tile.ROOM_FLOOR, grid.get(index), where);
            }
            if (placement.type == PlacementType.MONSTER_SPAWN || placement.type == PlacementType.ITEM_SPAWN
                    || placement.type == PlacementType.GOLD || placement.type == PlacementType.DECOR) {
                spawns++;
                assertTrue(placement.dangerTier >= 0 && placement.dangerTier <= 3, where);
                if (placement.roomId >= 0) {
                    int hop = dungeon.graph.hops(start, placement.roomId);
                    assertEquals(PlacementPass.dangerTier(hop, maxHop), placement.dangerTier, where);
                }
            }
            if (placement.type == PlacementType.MONSTER_SPAWN && placement.roomId >= 0) {
                monsters[placement.roomId]++;
            }
            if (placement.type == PlacementType.BOSS_SPAWN) {
                bosses++;
                assertTrue(placement.dangerTier >= 0 && placement.dangerTier <= 3, where);
                assertTrue(dungeon.graph.hops(placement.roomId, exit) <= 2, where);
                assertTrue(hasChest(dungeon, placement.roomId), where);
                assertTrue(placement.roomId != start, where);
            }
            if (placement.type == PlacementType.SHOP) {
                shops++;
                assertTrue(dungeon.graph.neighbors(placement.roomId).length <= 1, where);
                assertTrue(placement.roomId != start, where);
            }
            if (placement.type == PlacementType.FOUNTAIN) {
                fountains++;
                assertEquals(Tile.GROUND, grid.get(index), where);
                assertTrue(placement.regionId >= 0, where);
            }
            if (placement.type == PlacementType.LIGHT) {
                assertTrue(placement.facing != Facing.NONE, where);
                if (placement.roomId >= 0) {
                    assertTrue(!dungeon.rooms.get(placement.roomId).secret, where);
                }
                int fx = placement.x + placement.facing.dx;
                int fy = placement.y + placement.facing.dy;
                assertTrue(grid.inBounds(fx, fy) && grid.get(fx, fy).isOpen(), where);
            }
        }
        assertTrue(traps >= PlacementPass.trapLo(dungeon.config) && traps <= PlacementPass.trapHi(dungeon.config),
                where + " traps " + traps);
        assertTrue(spawns >= PlacementPass.spawnLo(dungeon.config) && spawns <= PlacementPass.spawnHi(dungeon.config),
                where + " spawns " + spawns);
        assertTrue(bosses <= 1 && shops <= 1 && fountains <= 1, where);
        for (Room room : dungeon.rooms) {
            assertTrue(monsters[room.id] <= PlacementPass.monsterCap(room), where + " monsters " + room.id);
            if ((room.secret || room.locked) && !entirelySafe(dungeon, room)) {
                assertTrue(hasTag(dungeon, room.id, "treasure"), where + " treasure " + room.id);
            }
            if (room.id == shopsRoom(dungeon)) {
                assertTrue(count(dungeon, room.id, PlacementType.TRAP) == 0, where);
                assertTrue(count(dungeon, room.id, PlacementType.MONSTER_SPAWN) == 0, where);
            }
        }
        int deadTreasure = 0;
        int deadEnds = 0;
        for (Room room : dungeon.rooms) {
            boolean dead = dungeon.graph.neighbors(room.id).length <= 1
                    && room.id != start && room.id != exit && !room.secret && !room.locked;
            if (dead) {
                deadEnds++;
            }
            if (hasTag(dungeon, room.id, "treasure") && !room.secret && !room.locked) {
                assertTrue(dead, where + " treasure room " + room.id);
                deadTreasure++;
            }
        }
        if (deadEnds > 0) {
            assertTrue(deadTreasure >= 1 && deadTreasure <= 2, where + " dead treasures " + deadTreasure);
        }
        assertLightsSpaced(dungeon, where);
        assertReachable(dungeon, where);
    }

    private static void assertLightsSpaced(Dungeon dungeon, String where) {
        for (int i = 0; i < dungeon.placements.size(); i++) {
            Placement a = dungeon.placements.get(i);
            if (a.type != PlacementType.LIGHT) {
                continue;
            }
            for (int j = i + 1; j < dungeon.placements.size(); j++) {
                Placement b = dungeon.placements.get(j);
                if (b.type != PlacementType.LIGHT) {
                    continue;
                }
                boolean sameRoom = a.roomId >= 0 && a.roomId == b.roomId;
                boolean sameRegion = a.roomId < 0 && b.roomId < 0 && a.regionId == b.regionId;
                if (!sameRoom && !sameRegion) {
                    continue;
                }
                int dist = Math.max(Math.abs(a.x - b.x), Math.abs(a.y - b.y));
                assertTrue(dist >= 5, where + " lights " + a.x + "," + a.y);
            }
        }
    }

    private static void assertReachable(Dungeon dungeon, String where) {
        TileGrid grid = dungeon.grid;
        boolean[] solid = new boolean[grid.width * grid.height];
        for (Placement placement : dungeon.placements) {
            if (placement.type == PlacementType.CHEST || placement.type == PlacementType.DECOR
                    || placement.type == PlacementType.FOUNTAIN) {
                solid[grid.index(placement.x, placement.y)] = true;
            }
        }
        boolean[] seen = flood(dungeon, solid);
        assertTrue(seen[dungeon.stairsUp], where + " up");
        assertTrue(seen[dungeon.stairsDown], where + " down");
        for (Room room : dungeon.rooms) {
            if (room.secret) {
                continue;
            }
            boolean hit = false;
            for (int[] part : room.parts()) {
                for (int y = part[1]; y < part[1] + part[3] && !hit; y++) {
                    for (int x = part[0]; x < part[0] + part[2]; x++) {
                        if (room.contains(x, y) && grid.inBounds(x, y) && seen[grid.index(x, y)]) {
                            hit = true;
                        }
                    }
                }
            }
            assertTrue(hit, where + " room " + room.id);
        }
    }

    private static boolean[] flood(Dungeon dungeon, boolean[] solid) {
        TileGrid grid = dungeon.grid;
        boolean[] seen = new boolean[solid.length];
        int[] queue = new int[solid.length];
        int head = 0;
        int tail = 0;
        int origin = dungeon.stairsUp;
        seen[origin] = true;
        queue[tail++] = origin;
        int[] dx = {0, 1, 0, -1};
        int[] dy = {-1, 0, 1, 0};
        while (head < tail) {
            int cell = queue[head++];
            int x = grid.x(cell);
            int y = grid.y(cell);
            for (int dir = 0; dir < 4; dir++) {
                int nx = x + dx[dir];
                int ny = y + dy[dir];
                if (!grid.inBounds(nx, ny)) {
                    continue;
                }
                int next = grid.index(nx, ny);
                if (seen[next] || solid[next] || !grid.get(next).isOpen()) {
                    continue;
                }
                seen[next] = true;
                queue[tail++] = next;
            }
        }
        return seen;
    }

    private static boolean hasChest(Dungeon dungeon, int roomId) {
        return count(dungeon, roomId, PlacementType.CHEST) > 0;
    }

    private static boolean hasTag(Dungeon dungeon, int roomId, String tag) {
        for (Placement placement : dungeon.placements) {
            if (placement.roomId == roomId && placement.tags.contains(tag)) {
                return true;
            }
        }
        return false;
    }

    private static int count(Dungeon dungeon, int roomId, PlacementType type) {
        int n = 0;
        for (Placement placement : dungeon.placements) {
            if (placement.roomId == roomId && placement.type == type) {
                n++;
            }
        }
        return n;
    }

    private static int shopsRoom(Dungeon dungeon) {
        for (Placement placement : dungeon.placements) {
            if (placement.type == PlacementType.SHOP) {
                return placement.roomId;
            }
        }
        return -1;
    }

    private static boolean has(Dungeon dungeon, PlacementType type) {
        for (Placement placement : dungeon.placements) {
            if (placement.type == type) {
                return true;
            }
        }
        return false;
    }

    private static int roomOf(Dungeon dungeon, int cell) {
        if (cell < 0) {
            return -1;
        }
        TileGrid grid = dungeon.grid;
        int x = grid.x(cell);
        int y = grid.y(cell);
        for (Room room : dungeon.rooms) {
            if (room.contains(x, y)) {
                return room.id;
            }
        }
        return -1;
    }

    private static boolean entirelySafe(Dungeon dungeon, Room room) {
        TileGrid grid = dungeon.grid;
        boolean any = false;
        for (int[] part : room.parts()) {
            for (int y = part[1]; y < part[1] + part[3]; y++) {
                for (int x = part[0]; x < part[0] + part[2]; x++) {
                    if (!room.contains(x, y) || !grid.inBounds(x, y) || grid.get(x, y) != Tile.ROOM_FLOOR) {
                        continue;
                    }
                    any = true;
                    if (dungeon.stairsUp >= 0 && !nearStairs(grid, dungeon.stairsUp, x, y)) {
                        return false;
                    }
                }
            }
        }
        return any;
    }

    private static boolean nearStairs(TileGrid grid, int stairs, int x, int y) {
        return Math.max(Math.abs(x - grid.x(stairs)), Math.abs(y - grid.y(stairs))) <= 4;
    }

    private static boolean besideDoor(TileGrid grid, int x, int y) {
        int[] dx = {0, 1, 0, -1};
        int[] dy = {-1, 0, 1, 0};
        for (int dir = 0; dir < 4; dir++) {
            int nx = x + dx[dir];
            int ny = y + dy[dir];
            if (grid.inBounds(nx, ny) && grid.get(nx, ny).isDoor()) {
                return true;
            }
        }
        return false;
    }

    private static boolean againstWall(TileGrid grid, int x, int y) {
        int[] dx = {0, 1, 0, -1};
        int[] dy = {-1, 0, 1, 0};
        for (int dir = 0; dir < 4; dir++) {
            int nx = x + dx[dir];
            int ny = y + dy[dir];
            if (grid.inBounds(nx, ny) && grid.get(nx, ny) == Tile.WALL) {
                return true;
            }
        }
        return false;
    }

    private static long key(int x, int y) {
        return ((long) x << 32) | (y & 0xffffffffL);
    }
}
