package io.github.jvmdc.dungeongen.place;

import io.github.jvmdc.dungeongen.LayoutConfig;
import io.github.jvmdc.dungeongen.common.Corridor;
import io.github.jvmdc.dungeongen.common.Dungeon;
import io.github.jvmdc.dungeongen.common.Facing;
import io.github.jvmdc.dungeongen.common.Placement;
import io.github.jvmdc.dungeongen.common.PlacementType;
import io.github.jvmdc.dungeongen.common.Room;
import io.github.jvmdc.dungeongen.common.SeededRandom;
import io.github.jvmdc.dungeongen.common.Tile;
import io.github.jvmdc.dungeongen.common.TileGrid;
import io.github.jvmdc.dungeongen.common.TrapKind;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Marks traps, chests, spawns, lights, and a few room roles on a finished layout.
 * Uses its own random stream, mixed from the floor seed, so the rooms stay put.
 * Floor traps become plates. Chests, decor, and fountains are treated as solid
 * and are skipped when they would cut a room off from the stairs.
 */
public final class PlacementPass {
    private static final int[] DX = {0, 1, 0, -1};
    private static final int[] DY = {-1, 0, 1, 0};
    private static final long STREAM = 0x51F3C9A7B2E4D681L;

    private PlacementPass() {
    }

    public static void apply(Dungeon dungeon) {
        if (dungeon.rooms.isEmpty()) {
            return;
        }
        Context ctx = new Context(dungeon, new SeededRandom(dungeon.seed ^ STREAM));
        assignRoles(ctx);
        placeChests(ctx);
        placeTraps(ctx);
        placeFountain(ctx);
        placeSpawns(ctx);
        placeLights(ctx);
        placeMarkers(ctx);
        dungeon.placements.sort((a, b) -> {
            if (a.y != b.y) {
                return Integer.compare(a.y, b.y);
            }
            if (a.x != b.x) {
                return Integer.compare(a.x, b.x);
            }
            return a.type.compareTo(b.type);
        });
    }

    /** Inclusive trap count for this map. Full floors use the config range. */
    public static int trapLo(LayoutConfig config) {
        if (config.width >= 56 && config.height >= 56) {
            return config.trapMin;
        }
        return Math.max(2, config.trapMin / 2);
    }

    public static int trapHi(LayoutConfig config) {
        if (config.width >= 56 && config.height >= 56) {
            return config.trapMax;
        }
        return Math.max(trapLo(config), config.trapMax / 2);
    }

    /** Inclusive spawn-slot count for this map. */
    public static int spawnLo(LayoutConfig config) {
        if (config.width >= 56 && config.height >= 56) {
            return config.spawnMin;
        }
        int scaled = config.spawnMin * config.width * config.height / (64 * 64);
        return Math.max(6, scaled);
    }

    public static int spawnHi(LayoutConfig config) {
        if (config.width >= 56 && config.height >= 56) {
            return config.spawnMax;
        }
        return Math.max(spawnLo(config), config.spawnMax * config.width * config.height / (64 * 64));
    }

    /** 0 at the start room, 3 in the rooms farthest from it along the graph. */
    public static int dangerTier(int hops, int maxHop) {
        if (hops <= 0 || maxHop <= 0) {
            return 0;
        }
        return Math.min(3, hops * 3 / maxHop);
    }

    /** How many monsters a room may hold, from its floor area. */
    public static int monsterCap(Room room) {
        int area = 0;
        for (int[] part : room.parts()) {
            area += part[2] * part[3];
        }
        if (area < 4) {
            return 0;
        }
        return Math.max(1, area / 8) * 3;
    }

    private static void assignRoles(Context ctx) {
        Dungeon dungeon = ctx.dungeon;
        ctx.roles.start = roomAt(ctx, dungeon.stairsUp);
        ctx.roles.exit = roomAt(ctx, dungeon.stairsDown);
        int origin = ctx.roles.start >= 0 ? ctx.roles.start : 0;
        hopsFrom(ctx, origin);
        for (Room room : dungeon.rooms) {
            if (room.secret || room.locked) {
                ctx.roles.treasure[room.id] = true;
            }
        }
        List<Integer> dead = new ArrayList<>();
        for (Room room : dungeon.rooms) {
            if (room.id == ctx.roles.start || room.id == ctx.roles.exit || ctx.roles.treasure[room.id]) {
                continue;
            }
            if (dungeon.graph.neighbors(room.id).length <= 1) {
                dead.add(room.id);
            }
        }
        dead.sort((a, b) -> {
            int hop = Integer.compare(ctx.hops[b], ctx.hops[a]);
            return hop != 0 ? hop : Integer.compare(a, b);
        });
        int extras = dead.isEmpty() ? 0 : 1 + ctx.rng.nextInt(2);
        for (int i = 0; i < extras && i < dead.size(); i++) {
            ctx.roles.treasure[dead.get(i)] = true;
        }
        if (ctx.roles.exit >= 0 && ctx.rng.nextDouble() < dungeon.config.bossChance) {
            int best = -1;
            int bestArea = -1;
            for (Room room : dungeon.rooms) {
                if (room.id == ctx.roles.start || room.secret || ctx.roles.treasure[room.id]) {
                    continue;
                }
                int hop = dungeon.graph.hops(room.id, ctx.roles.exit);
                if (hop < 0 || hop > 2) {
                    continue;
                }
                int area = roomArea(room);
                if (area > bestArea || (area == bestArea && (best < 0 || room.id < best))) {
                    best = room.id;
                    bestArea = area;
                }
            }
            ctx.roles.boss = best;
        }
        if (ctx.rng.nextDouble() < dungeon.config.shopChance) {
            List<Integer> medium = new ArrayList<>();
            for (int id : dead) {
                if (ctx.roles.treasure[id] || id == ctx.roles.boss) {
                    continue;
                }
                medium.add(id);
            }
            if (!medium.isEmpty()) {
                medium.sort((a, b) -> Integer.compare(roomArea(dungeon.rooms.get(a)), roomArea(dungeon.rooms.get(b))));
                int mid = roomArea(dungeon.rooms.get(medium.get(medium.size() / 2)));
                List<Integer> close = new ArrayList<>();
                for (int id : medium) {
                    int area = roomArea(dungeon.rooms.get(id));
                    if (area >= mid - mid / 4 && area <= mid + mid / 4 + 2) {
                        close.add(id);
                    }
                }
                List<Integer> pool = close.isEmpty() ? medium : close;
                ctx.roles.shop = pool.get(ctx.rng.nextInt(pool.size()));
            }
        }
    }

    private static void placeTraps(Context ctx) {
        int want = ctx.rng.nextInt(trapLo(ctx.dungeon.config), trapHi(ctx.dungeon.config) + 1);
        boolean[] approach = approaches(ctx);
        List<Integer> cells = new ArrayList<>();
        TileGrid grid = ctx.dungeon.grid;
        for (int i = 0; i < grid.width * grid.height; i++) {
            if (trapSite(ctx, i, false)) {
                cells.add(i);
            }
        }
        shuffle(cells, ctx.rng);
        cells.sort((a, b) -> Integer.compare(trapScore(ctx, approach, b), trapScore(ctx, approach, a)));
        int placed = 0;
        for (int cell : cells) {
            if (placed >= want) {
                break;
            }
            if (!trapSite(ctx, cell, false)) {
                continue;
            }
            int x = grid.x(cell);
            int y = grid.y(cell);
            int wall = wallMount(ctx, x, y);
            boolean pinned = ctx.path[cell];
            if (pinned && (wall < 0 || safe(ctx, wall))) {
                continue;
            }
            boolean wallTrap = pinned || (wall >= 0 && !safe(ctx, wall) && ctx.rng.nextDouble() < 0.45);
            if (wallTrap) {
                int wx = grid.x(wall);
                int wy = grid.y(wall);
                Facing facing = Facing.of(x - wx, y - wy);
                TrapKind kind = ctx.rng.nextInt(2) == 0 ? TrapKind.ARROW_WALL : TrapKind.MAGIC_WALL;
                add(ctx, PlacementType.TRAP, wx, wy, facing, ctx.roomOf[wall], -1, -1, kind, List.of());
                ctx.taken[wall] = true;
                ctx.taken[cell] = true;
            } else {
                TrapKind kind = switch (ctx.rng.nextInt(3)) {
                    case 0 -> TrapKind.SPIKE_FLOOR;
                    case 1 -> TrapKind.PIT_LID;
                    default -> TrapKind.BEAR_TRAP;
                };
                grid.set(cell, Tile.TRAP_PLATE);
                add(ctx, PlacementType.TRAP, x, y, Facing.NONE, ctx.roomOf[cell], -1, -1, kind, List.of());
                ctx.taken[cell] = true;
            }
            placed++;
        }
    }

    private static void placeChests(Context ctx) {
        for (Room room : ctx.dungeon.rooms) {
            boolean treasure = ctx.roles.treasure[room.id];
            boolean boss = room.id == ctx.roles.boss;
            if (!treasure && !boss && (room.id == ctx.roles.start || room.id == ctx.roles.shop
                    || ctx.rng.nextDouble() >= ctx.dungeon.config.chestChance)) {
                continue;
            }
            List<String> tags = new ArrayList<>();
            if (treasure) {
                tags.add("treasure");
            }
            if (boss) {
                tags.add("boss");
            }
            placeChest(ctx, room, tags);
        }
    }

    private static void placeChest(Context ctx, Room room, List<String> tags) {
        List<Integer> cells = chestCells(ctx, room);
        for (int cell : cells) {
            if (!keepsReach(ctx, cell)) {
                continue;
            }
            TileGrid grid = ctx.dungeon.grid;
            ctx.solid[cell] = true;
            ctx.taken[cell] = true;
            add(ctx, PlacementType.CHEST, grid.x(cell), grid.y(cell), Facing.NONE,
                    room.id, -1, -1, null, tags);
            return;
        }
    }

    private static void placeFountain(Context ctx) {
        if (ctx.rng.nextDouble() >= ctx.dungeon.config.fountainChance) {
            return;
        }
        TileGrid grid = ctx.dungeon.grid;
        List<Integer> cells = new ArrayList<>();
        for (int i = 0; i < ctx.regionOf.length; i++) {
            if (ctx.regionOf[i] >= 0 && grid.get(i) == Tile.GROUND && !ctx.taken[i] && !safe(ctx, i)) {
                cells.add(i);
            }
        }
        shuffle(cells, ctx.rng);
        for (int cell : cells) {
            if (!keepsReach(ctx, cell)) {
                continue;
            }
            ctx.solid[cell] = true;
            ctx.taken[cell] = true;
            add(ctx, PlacementType.FOUNTAIN, grid.x(cell), grid.y(cell), Facing.NONE,
                    -1, ctx.regionOf[cell], -1, null, List.of());
            return;
        }
    }

    private static void placeSpawns(Context ctx) {
        int want = ctx.rng.nextInt(spawnLo(ctx.dungeon.config), spawnHi(ctx.dungeon.config) + 1);
        TileGrid grid = ctx.dungeon.grid;
        List<Integer> cells = new ArrayList<>();
        for (int i = 0; i < ctx.taken.length; i++) {
            if (spawnSite(ctx, i)) {
                cells.add(i);
            }
        }
        int[] monsters = new int[ctx.dungeon.rooms.size()];
        int placed = 0;
        while (placed < want && !cells.isEmpty()) {
            int pick = weighted(ctx, cells);
            int cell = cells.remove(pick);
            if (!spawnSite(ctx, cell)) {
                continue;
            }
            int room = ctx.roomOf[cell];
            int tier = tierAt(ctx, cell);
            boolean allowMonster = !nearStairs(ctx, cell);
            PlacementType type = spawnType(ctx, room, monsters, tier, allowMonster);
            if (type == null) {
                continue;
            }
            if (type == PlacementType.DECOR && !keepsReach(ctx, cell)) {
                type = nonSolid(ctx, room, monsters, allowMonster);
                if (type == null) {
                    continue;
                }
            }
            if (type == PlacementType.DECOR) {
                ctx.solid[cell] = true;
            }
            if (type == PlacementType.MONSTER_SPAWN && room >= 0) {
                monsters[room]++;
            }
            ctx.taken[cell] = true;
            add(ctx, type, grid.x(cell), grid.y(cell), Facing.NONE, room, room < 0 ? ctx.regionOf[cell] : -1,
                    tier, null, List.of());
            placed++;
        }
    }

    private static void placeLights(Context ctx) {
        for (Room room : ctx.dungeon.rooms) {
            if (room.secret) {
                continue;
            }
            List<Integer> walls = new ArrayList<>();
            List<Facing> faces = new ArrayList<>();
            collectWalls(ctx, room, walls, faces);
            spaceLights(ctx, walls, faces, 5 + ctx.rng.nextInt(4), room.id, -1);
        }
        int regions = 0;
        for (int id : ctx.regionOf) {
            regions = Math.max(regions, id + 1);
        }
        for (int region = 0; region < regions; region++) {
            List<Integer> walls = new ArrayList<>();
            List<Facing> faces = new ArrayList<>();
            collectCourtWalls(ctx, region, walls, faces);
            if (!walls.isEmpty()) {
                spaceLights(ctx, walls, faces, 5 + ctx.rng.nextInt(4), -1, region);
            }
        }
    }

    private static void placeMarkers(Context ctx) {
        if (ctx.roles.shop >= 0) {
            int cell = freeFloor(ctx, ctx.dungeon.rooms.get(ctx.roles.shop));
            if (cell >= 0) {
                TileGrid grid = ctx.dungeon.grid;
                ctx.taken[cell] = true;
                add(ctx, PlacementType.SHOP, grid.x(cell), grid.y(cell), Facing.NONE,
                        ctx.roles.shop, -1, -1, null, List.of("shop"));
            }
        }
        if (ctx.roles.boss >= 0) {
            int cell = freeFloor(ctx, ctx.dungeon.rooms.get(ctx.roles.boss));
            if (cell >= 0) {
                TileGrid grid = ctx.dungeon.grid;
                ctx.taken[cell] = true;
                int tier = dangerTier(ctx.hops[ctx.roles.boss], ctx.maxHop);
                add(ctx, PlacementType.BOSS_SPAWN, grid.x(cell), grid.y(cell), Facing.NONE,
                        ctx.roles.boss, -1, tier, null, List.of("boss"));
            }
        }
    }

    private static void spaceLights(Context ctx, List<Integer> walls, List<Facing> faces, int gap,
            int roomId, int regionId) {
        List<Integer> placed = new ArrayList<>();
        TileGrid grid = ctx.dungeon.grid;
        for (int i = 0; i < walls.size(); i++) {
            int cell = walls.get(i);
            if (ctx.taken[cell]) {
                continue;
            }
            int x = grid.x(cell);
            int y = grid.y(cell);
            boolean close = false;
            for (int earlier : placed) {
                int dist = Math.max(Math.abs(x - grid.x(earlier)), Math.abs(y - grid.y(earlier)));
                if (dist < gap) {
                    close = true;
                    break;
                }
            }
            if (close) {
                continue;
            }
            ctx.taken[cell] = true;
            placed.add(cell);
            add(ctx, PlacementType.LIGHT, x, y, faces.get(i), roomId, regionId, -1, null, List.of());
        }
    }

    private static void collectWalls(Context ctx, Room room, List<Integer> walls, List<Facing> faces) {
        TileGrid grid = ctx.dungeon.grid;
        boolean[] seen = new boolean[ctx.taken.length];
        for (int[] part : room.parts()) {
            for (int y = part[1]; y < part[1] + part[3]; y++) {
                for (int x = part[0]; x < part[0] + part[2]; x++) {
                    if (!room.contains(x, y) || !grid.inBounds(x, y) || !grid.get(x, y).isOpen()) {
                        continue;
                    }
                    for (int dir = 0; dir < 4; dir++) {
                        int wx = x + DX[dir];
                        int wy = y + DY[dir];
                        if (!grid.inBounds(wx, wy) || grid.get(wx, wy) != Tile.WALL) {
                            continue;
                        }
                        int index = grid.index(wx, wy);
                        if (seen[index]) {
                            continue;
                        }
                        seen[index] = true;
                        walls.add(index);
                        faces.add(Facing.of(-DX[dir], -DY[dir]));
                    }
                }
            }
        }
        sortSites(grid, walls, faces);
    }

    private static void collectCourtWalls(Context ctx, int region, List<Integer> walls, List<Facing> faces) {
        TileGrid grid = ctx.dungeon.grid;
        boolean[] seen = new boolean[ctx.taken.length];
        for (int i = 0; i < ctx.regionOf.length; i++) {
            if (ctx.regionOf[i] != region || grid.get(i) != Tile.GROUND) {
                continue;
            }
            int x = grid.x(i);
            int y = grid.y(i);
            for (int dir = 0; dir < 4; dir++) {
                int wx = x + DX[dir];
                int wy = y + DY[dir];
                if (!grid.inBounds(wx, wy)) {
                    continue;
                }
                Tile tile = grid.get(wx, wy);
                if (tile != Tile.WALL && tile != Tile.ROCK) {
                    continue;
                }
                int index = grid.index(wx, wy);
                if (seen[index]) {
                    continue;
                }
                seen[index] = true;
                walls.add(index);
                faces.add(Facing.of(-DX[dir], -DY[dir]));
            }
        }
        sortSites(grid, walls, faces);
    }

    private static void sortSites(TileGrid grid, List<Integer> walls, List<Facing> faces) {
        Integer[] order = new Integer[walls.size()];
        for (int i = 0; i < order.length; i++) {
            order[i] = i;
        }
        Arrays.sort(order, (a, b) -> {
            int ay = grid.y(walls.get(a));
            int by = grid.y(walls.get(b));
            if (ay != by) {
                return Integer.compare(ay, by);
            }
            return Integer.compare(grid.x(walls.get(a)), grid.x(walls.get(b)));
        });
        List<Integer> sortedWalls = new ArrayList<>(walls.size());
        List<Facing> sortedFaces = new ArrayList<>(faces.size());
        for (int index : order) {
            sortedWalls.add(walls.get(index));
            sortedFaces.add(faces.get(index));
        }
        walls.clear();
        walls.addAll(sortedWalls);
        faces.clear();
        faces.addAll(sortedFaces);
    }

    private static int freeFloor(Context ctx, Room room) {
        TileGrid grid = ctx.dungeon.grid;
        int fallback = -1;
        for (int[] part : room.parts()) {
            for (int y = part[1]; y < part[1] + part[3]; y++) {
                for (int x = part[0]; x < part[0] + part[2]; x++) {
                    if (!room.contains(x, y) || !grid.inBounds(x, y)) {
                        continue;
                    }
                    int index = grid.index(x, y);
                    if (ctx.taken[index] || !standable(grid.get(index)) || index == ctx.dungeon.stairsUp
                            || index == ctx.dungeon.stairsDown) {
                        continue;
                    }
                    if (x == room.centerX && y == room.centerY) {
                        return index;
                    }
                    if (fallback < 0) {
                        fallback = index;
                    }
                }
            }
        }
        return fallback;
    }

    private static List<Integer> chestCells(Context ctx, Room room) {
        TileGrid grid = ctx.dungeon.grid;
        List<Integer> quiet = new ArrayList<>();
        List<Integer> nearDoor = new ArrayList<>();
        for (int[] part : room.parts()) {
            for (int y = part[1]; y < part[1] + part[3]; y++) {
                for (int x = part[0]; x < part[0] + part[2]; x++) {
                    if (!room.contains(x, y) || !grid.inBounds(x, y)) {
                        continue;
                    }
                    int index = grid.index(x, y);
                    if (!chestSite(ctx, index) || !againstWall(grid, x, y)) {
                        continue;
                    }
                    if (besideDoor(grid, x, y)) {
                        nearDoor.add(index);
                    } else {
                        quiet.add(index);
                    }
                }
            }
        }
        shuffle(quiet, ctx.rng);
        shuffle(nearDoor, ctx.rng);
        quiet.addAll(nearDoor);
        if (quiet.isEmpty()) {
            for (int[] part : room.parts()) {
                for (int y = part[1]; y < part[1] + part[3]; y++) {
                    for (int x = part[0]; x < part[0] + part[2]; x++) {
                        if (room.contains(x, y) && grid.inBounds(x, y) && chestSite(ctx, grid.index(x, y))) {
                            quiet.add(grid.index(x, y));
                        }
                    }
                }
            }
            shuffle(quiet, ctx.rng);
        }
        return quiet;
    }

    private static boolean chestSite(Context ctx, int index) {
        TileGrid grid = ctx.dungeon.grid;
        if (ctx.taken[index] || safe(ctx, index)) {
            return false;
        }
        Tile tile = grid.get(index);
        return tile == Tile.ROOM_FLOOR;
    }

    private static boolean spawnSite(Context ctx, int index) {
        if (ctx.taken[index] || index == ctx.dungeon.stairsUp || index == ctx.dungeon.stairsDown) {
            return false;
        }
        Tile tile = ctx.dungeon.grid.get(index);
        if (tile != Tile.ROOM_FLOOR && tile != Tile.CORRIDOR_FLOOR && tile != Tile.GROUND) {
            return false;
        }
        int room = ctx.roomOf[index];
        return room != ctx.roles.start;
    }

    /** True when a monster, trap, or chest must stay off this cell. */
    private static boolean safe(Context ctx, int index) {
        if (ctx.roomOf[index] == ctx.roles.start && ctx.roles.start >= 0) {
            return true;
        }
        return nearStairs(ctx, index);
    }

    private static boolean nearStairs(Context ctx, int index) {
        if (ctx.dungeon.stairsUp < 0) {
            return false;
        }
        TileGrid grid = ctx.dungeon.grid;
        int dx = Math.abs(grid.x(index) - grid.x(ctx.dungeon.stairsUp));
        int dy = Math.abs(grid.y(index) - grid.y(ctx.dungeon.stairsUp));
        return Math.max(dx, dy) <= 4;
    }

    private static boolean trapSite(Context ctx, int index, boolean ignoreTaken) {
        if ((!ignoreTaken && ctx.taken[index]) || safe(ctx, index)) {
            return false;
        }
        TileGrid grid = ctx.dungeon.grid;
        int x = grid.x(index);
        int y = grid.y(index);
        Tile tile = grid.get(index);
        if (tile != Tile.ROOM_FLOOR && tile != Tile.CORRIDOR_FLOOR) {
            return false;
        }
        if (ctx.roomOf[index] == ctx.roles.shop) {
            return false;
        }
        if (besideDoor(grid, x, y) || isShell(ctx, x, y)) {
            return false;
        }
        return true;
    }

    private static int trapScore(Context ctx, boolean[] approach, int index) {
        int score = ctx.dungeon.grid.get(index) == Tile.CORRIDOR_FLOOR ? 3 : 1;
        if (approach[index]) {
            score += 4;
        }
        int room = ctx.roomOf[index];
        if (room >= 0 && ctx.roles.treasure[room] && approach[index]) {
            score += 5;
        }
        return score;
    }

    private static boolean[] approaches(Context ctx) {
        TileGrid grid = ctx.dungeon.grid;
        boolean[] mark = new boolean[ctx.taken.length];
        for (int i = 0; i < mark.length; i++) {
            if (!grid.get(i).isDoor()) {
                continue;
            }
            int x = grid.x(i);
            int y = grid.y(i);
            for (int dir = 0; dir < 4; dir++) {
                int x2 = x + DX[dir] * 2;
                int y2 = y + DY[dir] * 2;
                if (!grid.inBounds(x2, y2)) {
                    continue;
                }
                int mid = grid.index(x + DX[dir], y + DY[dir]);
                int far = grid.index(x2, y2);
                if (!grid.get(mid).isOpen() || grid.get(mid).isDoor()) {
                    continue;
                }
                Tile farTile = grid.get(far);
                if (farTile == Tile.ROOM_FLOOR || farTile == Tile.CORRIDOR_FLOOR) {
                    mark[far] = true;
                }
            }
        }
        return mark;
    }

    private static int wallMount(Context ctx, int x, int y) {
        TileGrid grid = ctx.dungeon.grid;
        for (int dir = 0; dir < 4; dir++) {
            int wx = x + DX[dir];
            int wy = y + DY[dir];
            if (!grid.inBounds(wx, wy) || grid.get(wx, wy) != Tile.WALL) {
                continue;
            }
            int index = grid.index(wx, wy);
            if (!ctx.taken[index]) {
                return index;
            }
        }
        return -1;
    }

    private static PlacementType spawnType(Context ctx, int room, int[] monsters, int tier, boolean allowMonster) {
        int monster = allowMonster ? 3 * (5 + tier * 3) : 0;
        int item = 4;
        int gold = 3;
        int decor = 3;
        if (room >= 0 && (room == ctx.roles.shop || room == ctx.roles.boss
                || monsters[room] >= monsterCap(ctx.dungeon.rooms.get(room)))) {
            monster = 0;
        }
        if (room < 0 && allowMonster) {
            monster = Math.max(monster, 4);
        }
        int total = monster + item + gold + decor;
        int roll = ctx.rng.nextInt(total);
        if (roll < monster) {
            return PlacementType.MONSTER_SPAWN;
        }
        roll -= monster;
        if (roll < item) {
            return PlacementType.ITEM_SPAWN;
        }
        roll -= item;
        if (roll < gold) {
            return PlacementType.GOLD;
        }
        return PlacementType.DECOR;
    }

    private static PlacementType nonSolid(Context ctx, int room, int[] monsters, boolean allowMonster) {
        if (allowMonster && room >= 0 && room != ctx.roles.shop && room != ctx.roles.boss
                && monsters[room] < monsterCap(ctx.dungeon.rooms.get(room))) {
            return PlacementType.MONSTER_SPAWN;
        }
        return ctx.rng.nextInt(2) == 0 ? PlacementType.ITEM_SPAWN : PlacementType.GOLD;
    }

    private static int weighted(Context ctx, List<Integer> cells) {
        int total = 0;
        for (int cell : cells) {
            total += 1 + tierAt(ctx, cell) * tierAt(ctx, cell);
        }
        int roll = ctx.rng.nextInt(Math.max(1, total));
        int cursor = 0;
        for (int i = 0; i < cells.size(); i++) {
            cursor += 1 + tierAt(ctx, cells.get(i)) * tierAt(ctx, cells.get(i));
            if (roll < cursor) {
                return i;
            }
        }
        return cells.size() - 1;
    }

    private static int tierAt(Context ctx, int index) {
        int room = ctx.roomOf[index];
        if (room >= 0) {
            return dangerTier(ctx.hops[room], ctx.maxHop);
        }
        int best = 0;
        TileGrid grid = ctx.dungeon.grid;
        int x = grid.x(index);
        int y = grid.y(index);
        for (int dir = 0; dir < 4; dir++) {
            int nx = x + DX[dir];
            int ny = y + DY[dir];
            if (!grid.inBounds(nx, ny)) {
                continue;
            }
            int other = ctx.roomOf[grid.index(nx, ny)];
            if (other >= 0) {
                best = Math.max(best, dangerTier(ctx.hops[other], ctx.maxHop));
            }
        }
        return best;
    }

    private static boolean keepsReach(Context ctx, int extra) {
        ctx.solid[extra] = true;
        flood(ctx);
        ctx.solid[extra] = false;
        if (ctx.dungeon.stairsUp >= 0 && ctx.baseReached[ctx.dungeon.stairsUp] && !ctx.seen[ctx.dungeon.stairsUp]) {
            return false;
        }
        if (ctx.dungeon.stairsDown >= 0 && ctx.baseReached[ctx.dungeon.stairsDown] && !ctx.seen[ctx.dungeon.stairsDown]) {
            return false;
        }
        for (Room room : ctx.dungeon.rooms) {
            if (room.secret || !ctx.roomReached[room.id]) {
                continue;
            }
            if (!roomReachedNow(ctx, room)) {
                return false;
            }
        }
        return true;
    }

    private static boolean roomReachedNow(Context ctx, Room room) {
        TileGrid grid = ctx.dungeon.grid;
        for (int[] part : room.parts()) {
            for (int y = part[1]; y < part[1] + part[3]; y++) {
                for (int x = part[0]; x < part[0] + part[2]; x++) {
                    if (!room.contains(x, y) || !grid.inBounds(x, y)) {
                        continue;
                    }
                    int index = grid.index(x, y);
                    if (ctx.seen[index]) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static void flood(Context ctx) {
        Arrays.fill(ctx.seen, false);
        int origin = ctx.dungeon.stairsUp;
        TileGrid grid = ctx.dungeon.grid;
        if (origin < 0 || ctx.solid[origin] || !grid.get(origin).isOpen()) {
            origin = -1;
            for (int i = 0; i < ctx.seen.length; i++) {
                if (!ctx.solid[i] && grid.get(i).isOpen()) {
                    origin = i;
                    break;
                }
            }
        }
        if (origin < 0) {
            return;
        }
        int head = 0;
        int tail = 0;
        ctx.seen[origin] = true;
        ctx.queue[tail++] = origin;
        while (head < tail) {
            int cell = ctx.queue[head++];
            int x = grid.x(cell);
            int y = grid.y(cell);
            for (int dir = 0; dir < 4; dir++) {
                int nx = x + DX[dir];
                int ny = y + DY[dir];
                if (!grid.inBounds(nx, ny)) {
                    continue;
                }
                int next = grid.index(nx, ny);
                if (ctx.seen[next] || ctx.solid[next] || !grid.get(next).isOpen()) {
                    continue;
                }
                ctx.seen[next] = true;
                ctx.queue[tail++] = next;
            }
        }
    }

    private static void hopsFrom(Context ctx, int origin) {
        Arrays.fill(ctx.hops, -1);
        int[] queue = new int[ctx.hops.length];
        int head = 0;
        int tail = 0;
        ctx.hops[origin] = 0;
        queue[tail++] = origin;
        ctx.maxHop = 0;
        while (head < tail) {
            int room = queue[head++];
            for (int next : ctx.dungeon.graph.neighbors(room)) {
                if (ctx.hops[next] < 0) {
                    ctx.hops[next] = ctx.hops[room] + 1;
                    ctx.maxHop = Math.max(ctx.maxHop, ctx.hops[next]);
                    queue[tail++] = next;
                }
            }
        }
    }

    private static int roomAt(Context ctx, int cell) {
        if (cell < 0 || cell >= ctx.roomOf.length) {
            return -1;
        }
        return ctx.roomOf[cell];
    }

    private static boolean againstWall(TileGrid grid, int x, int y) {
        for (int dir = 0; dir < 4; dir++) {
            int nx = x + DX[dir];
            int ny = y + DY[dir];
            if (grid.inBounds(nx, ny) && grid.get(nx, ny) == Tile.WALL) {
                return true;
            }
        }
        return false;
    }

    private static boolean besideDoor(TileGrid grid, int x, int y) {
        for (int dir = 0; dir < 4; dir++) {
            int nx = x + DX[dir];
            int ny = y + DY[dir];
            if (grid.inBounds(nx, ny) && grid.get(nx, ny).isDoor()) {
                return true;
            }
        }
        return false;
    }

    private static boolean isShell(Context ctx, int x, int y) {
        if (ctx.roomOf[ctx.dungeon.grid.index(x, y)] >= 0) {
            return false;
        }
        TileGrid grid = ctx.dungeon.grid;
        for (int dir = 0; dir < 4; dir++) {
            int nx = x + DX[dir];
            int ny = y + DY[dir];
            if (!grid.inBounds(nx, ny)) {
                continue;
            }
            int index = grid.index(nx, ny);
            if (ctx.roomOf[index] >= 0 && grid.get(index).isOpen()) {
                return true;
            }
        }
        return false;
    }

    private static boolean standable(Tile tile) {
        return tile == Tile.ROOM_FLOOR || tile == Tile.CORRIDOR_FLOOR || tile == Tile.GROUND
                || tile == Tile.TRAP_PLATE;
    }

    private static int roomArea(Room room) {
        int area = 0;
        for (int[] part : room.parts()) {
            area += part[2] * part[3];
        }
        return area;
    }

    private static void add(Context ctx, PlacementType type, int x, int y, Facing facing, int roomId, int regionId,
            int tier, TrapKind trap, List<String> tags) {
        ctx.dungeon.placements.add(new Placement(type, x, y, facing, roomId, regionId, tier, trap, tags));
    }

    private static void shuffle(List<Integer> cells, SeededRandom rng) {
        for (int i = cells.size() - 1; i > 0; i--) {
            int j = rng.nextInt(i + 1);
            int swap = cells.get(i);
            cells.set(i, cells.get(j));
            cells.set(j, swap);
        }
    }

    private static final class Context {
        final Dungeon dungeon;
        final SeededRandom rng;
        final int[] roomOf;
        final int[] regionOf;
        final int[] hops;
        final boolean[] taken;
        final boolean[] path;
        final boolean[] solid;
        final boolean[] seen;
        final boolean[] baseReached;
        final boolean[] roomReached;
        final int[] queue;
        final Roles roles;
        int maxHop;

        Context(Dungeon dungeon, SeededRandom rng) {
            this.dungeon = dungeon;
            this.rng = rng;
            int area = dungeon.grid.width * dungeon.grid.height;
            roomOf = new int[area];
            Arrays.fill(roomOf, -1);
            for (Room room : dungeon.rooms) {
                for (int[] part : room.parts()) {
                    for (int y = part[1]; y < part[1] + part[3]; y++) {
                        for (int x = part[0]; x < part[0] + part[2]; x++) {
                            if (dungeon.grid.inBounds(x, y) && room.contains(x, y)) {
                                roomOf[dungeon.grid.index(x, y)] = room.id;
                            }
                        }
                    }
                }
            }
            regionOf = new int[area];
            Arrays.fill(regionOf, -1);
            labelGround(dungeon.grid, regionOf);
            hops = new int[dungeon.rooms.size()];
            taken = new boolean[area];
            path = new boolean[area];
            for (Corridor corridor : dungeon.corridors) {
                for (int cell : corridor.path) {
                    if (cell >= 0 && cell < path.length) {
                        path[cell] = true;
                    }
                }
            }
            solid = new boolean[area];
            seen = new boolean[area];
            queue = new int[area];
            roles = new Roles(dungeon.rooms.size());
            roomReached = new boolean[dungeon.rooms.size()];
            flood(this);
            baseReached = seen.clone();
            for (Room room : dungeon.rooms) {
                roomReached[room.id] = roomReachedNow(this, room);
            }
        }
    }

    private static void labelGround(TileGrid grid, int[] regionOf) {
        int next = 0;
        boolean[] seen = new boolean[regionOf.length];
        int[] queue = new int[regionOf.length];
        for (int i = 0; i < regionOf.length; i++) {
            if (seen[i] || grid.get(i) != Tile.GROUND) {
                continue;
            }
            int head = 0;
            int tail = 0;
            seen[i] = true;
            queue[tail++] = i;
            while (head < tail) {
                int cell = queue[head++];
                regionOf[cell] = next;
                int x = grid.x(cell);
                int y = grid.y(cell);
                for (int dir = 0; dir < 4; dir++) {
                    int nx = x + DX[dir];
                    int ny = y + DY[dir];
                    if (!grid.inBounds(nx, ny)) {
                        continue;
                    }
                    int n = grid.index(nx, ny);
                    if (seen[n] || grid.get(n) != Tile.GROUND) {
                        continue;
                    }
                    seen[n] = true;
                    queue[tail++] = n;
                }
            }
            next++;
        }
    }

    private static final class Roles {
        final boolean[] treasure;
        int start = -1;
        int exit = -1;
        int shop = -1;
        int boss = -1;

        Roles(int rooms) {
            treasure = new boolean[rooms];
        }
    }
}
