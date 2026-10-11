package io.github.jvmdc.dungeongen.corridor;

import io.github.jvmdc.dungeongen.FeatureHooks;
import io.github.jvmdc.dungeongen.LayoutConfig;
import io.github.jvmdc.dungeongen.LayoutGenerator;
import io.github.jvmdc.dungeongen.common.Corridor;
import io.github.jvmdc.dungeongen.common.Dungeon;
import io.github.jvmdc.dungeongen.common.Room;
import io.github.jvmdc.dungeongen.common.RoomGraph;
import io.github.jvmdc.dungeongen.common.SeededRandom;
import io.github.jvmdc.dungeongen.common.Tile;
import io.github.jvmdc.dungeongen.common.TileGrid;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Places rooms, links their centres with a Manhattan spanning tree, digs corridors, then adds
 * a few extra loops. One seeded generator is used for the whole run and is never reseeded.
 */
public final class CorridorNetworkGenerator implements LayoutGenerator {
    /** The original minimum length is 4, and the path must be at least that plus the far end. */
    public static final int MIN_CORRIDOR_TILES = 5;

    private static final Comparator<Edge> EDGE_ORDER = (a, b) -> {
        int byDist = Integer.compare(a.dist, b.dist);
        if (byDist != 0) {
            return byDist;
        }
        int byU = Integer.compare(a.u, b.u);
        if (byU != 0) {
            return byU;
        }
        return Integer.compare(a.v, b.v);
    };

    @Override
    public Dungeon generate(LayoutConfig config, long seed) {
        SeededRandom rng = new SeededRandom(seed);
        TileGrid grid = new TileGrid(config.width, config.height);
        List<Room> rooms = placeRooms(config, grid, rng);
        List<Corridor> corridors = new ArrayList<>();
        if (rooms.size() >= 2) {
            carveConnections(config, grid, rooms, corridors);
        }
        Dungeon dungeon = new Dungeon(seed, config, grid, List.copyOf(rooms), List.copyOf(corridors),
                RoomGraph.derive(grid, rooms), -1, -1);
        FeatureHooks.apply(dungeon, config);
        return dungeon;
    }

    private static List<Room> placeRooms(LayoutConfig config, TileGrid grid, SeededRandom rng) {
        List<Room> rooms = new ArrayList<>();
        int attempts = 0;
        int maxAttempts = config.roomCount * 20;
        while (rooms.size() < config.roomCount && attempts < maxAttempts) {
            attempts++;
            int roomW = rng.nextInt(config.minRoomWidth, config.maxRoomWidth + 1);
            int roomH = rng.nextInt(config.minRoomHeight, config.maxRoomHeight + 1);
            // The range includes both ends. nextInt's upper bound is exclusive, so this is
            // [buffer, width - room - buffer - 1]. Size is rolled first so a larger room still fits.
            int x = rng.nextInt(config.roomBuffer, config.width - roomW - config.roomBuffer);
            int y = rng.nextInt(config.roomBuffer, config.height - roomH - config.roomBuffer);
            if (!fits(grid, config.roomBuffer, x, y, roomW, roomH)) {
                continue;
            }
            Room room = new Room(rooms.size(), x, y, roomW, roomH);
            rooms.add(room);
            for (int ty = y; ty < y + roomH; ty++) {
                for (int tx = x; tx < x + roomW; tx++) {
                    grid.set(tx, ty, Tile.ROOM_FLOOR);
                }
            }
        }
        return rooms;
    }

    private static boolean fits(TileGrid grid, int buffer, int x, int y, int roomW, int roomH) {
        int x0 = x - buffer;
        int y0 = y - buffer;
        int x1 = x + roomW + buffer;
        int y1 = y + roomH + buffer;
        if (x0 < 0 || y0 < 0 || x1 > grid.width || y1 > grid.height) {
            return false;
        }
        for (int ty = y0; ty < y1; ty++) {
            for (int tx = x0; tx < x1; tx++) {
                if (grid.get(tx, ty) != Tile.WALL) {
                    return false;
                }
            }
        }
        return true;
    }

    private static void carveConnections(LayoutConfig config, TileGrid grid,
            List<Room> rooms, List<Corridor> corridors) {
        List<Edge> edges = allEdges(rooms);
        edges.sort(EDGE_ORDER);
        CorridorRouter router = new CorridorRouter(grid.width, grid.height);
        int[] nextId = {0};
        Set<Long> tried = new HashSet<>();
        DisjointSet sets = new DisjointSet(rooms.size());

        for (Edge edge : kruskal(edges, rooms.size())) {
            tried.add(pairKey(edge.u, edge.v));
            if (carve(grid, rooms, corridors, router, nextId, edge.u, edge.v, Corridor.Kind.MST)) {
                sets.unite(edge.u, edge.v);
            }
        }
        // A failed MST edge used to be dropped, which could split the dungeon. Keep trying the
        // next shortest pair that still joins two separate pieces.
        if (sets.components() > 1) {
            for (Edge edge : edges) {
                if (sets.components() == 1) {
                    break;
                }
                long key = pairKey(edge.u, edge.v);
                if (!tried.add(key) || sets.find(edge.u) == sets.find(edge.v)) {
                    continue;
                }
                if (carve(grid, rooms, corridors, router, nextId, edge.u, edge.v, Corridor.Kind.REPAIR)) {
                    sets.unite(edge.u, edge.v);
                }
            }
        }
        addShortcuts(config, grid, rooms, corridors, router, nextId);
    }

    private static List<Edge> allEdges(List<Room> rooms) {
        List<Edge> edges = new ArrayList<>();
        for (int i = 0; i < rooms.size(); i++) {
            for (int j = i + 1; j < rooms.size(); j++) {
                edges.add(new Edge(manhattan(rooms.get(i), rooms.get(j)), i, j));
            }
        }
        return edges;
    }

    private static List<Edge> kruskal(List<Edge> sortedEdges, int roomCount) {
        DisjointSet sets = new DisjointSet(roomCount);
        List<Edge> mst = new ArrayList<>(Math.max(0, roomCount - 1));
        for (Edge edge : sortedEdges) {
            if (sets.unite(edge.u, edge.v)) {
                mst.add(edge);
                if (mst.size() == roomCount - 1) {
                    break;
                }
            }
        }
        return mst;
    }

    private static void addShortcuts(LayoutConfig config, TileGrid grid, List<Room> rooms,
            List<Corridor> corridors, CorridorRouter router, int[] nextId) {
        int count = rooms.size();
        List<List<Integer>> declared = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            declared.add(new ArrayList<>());
        }
        boolean[][] linked = new boolean[count][count];
        for (Corridor corridor : corridors) {
            link(declared, linked, corridor.roomA, corridor.roomB);
        }
        int[][] hops = new int[count][count];
        for (int i = 0; i < count; i++) {
            Arrays.fill(hops[i], -1);
            fillHops(declared, hops[i], i);
        }

        List<Shortcut> candidates = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            for (int j = i + 1; j < count; j++) {
                if (linked[i][j]) {
                    continue;
                }
                int hop = hops[i][j];
                // Drop unreachable pairs and pairs that are already one or two hops apart.
                if (hop <= 2) {
                    continue;
                }
                candidates.add(new Shortcut(manhattan(rooms.get(i), rooms.get(j)), hop, i, j));
            }
        }
        candidates.sort((a, b) -> {
            int byGeom = Integer.compare(a.geom, b.geom);
            if (byGeom != 0) {
                return byGeom;
            }
            int byHops = Integer.compare(b.hops, a.hops);
            if (byHops != 0) {
                return byHops;
            }
            int byU = Integer.compare(a.u, b.u);
            if (byU != 0) {
                return byU;
            }
            return Integer.compare(a.v, b.v);
        });

        int wanted = (int) (count * config.loopRatio);
        int added = 0;
        for (Shortcut shortcut : candidates) {
            if (added >= wanted) {
                break;
            }
            added++;
            carve(grid, rooms, corridors, router, nextId, shortcut.u, shortcut.v, Corridor.Kind.SHORTCUT);
        }
    }

    private static void fillHops(List<List<Integer>> declared, int[] dist, int start) {
        int[] queue = new int[dist.length];
        int head = 0;
        int tail = 0;
        dist[start] = 0;
        queue[tail++] = start;
        while (head < tail) {
            int current = queue[head++];
            for (int neighbor : declared.get(current)) {
                if (dist[neighbor] < 0) {
                    dist[neighbor] = dist[current] + 1;
                    queue[tail++] = neighbor;
                }
            }
        }
    }

    private static void link(List<List<Integer>> declared, boolean[][] linked, int a, int b) {
        if (linked[a][b]) {
            return;
        }
        linked[a][b] = true;
        linked[b][a] = true;
        declared.get(a).add(b);
        declared.get(b).add(a);
    }

    private static boolean carve(TileGrid grid, List<Room> rooms, List<Corridor> corridors,
            CorridorRouter router, int[] nextId, int roomA, int roomB, Corridor.Kind kind) {
        Room a = rooms.get(roomA);
        Room b = rooms.get(roomB);
        List<Integer> starts = doors(grid, a);
        List<Integer> ends = doors(grid, b);
        if (starts.isEmpty() || ends.isEmpty()) {
            return false;
        }
        List<DoorPair> pairs = new ArrayList<>(starts.size() * ends.size());
        for (int start : starts) {
            for (int end : ends) {
                int dist = Math.abs(grid.x(start) - grid.x(end)) + Math.abs(grid.y(start) - grid.y(end));
                pairs.add(new DoorPair(dist, start, end));
            }
        }
        // Equal distances break by cell index so a seed does not depend on an unstable sort.
        pairs.sort((p, q) -> {
            int byDist = Integer.compare(p.dist, q.dist);
            if (byDist != 0) {
                return byDist;
            }
            int byStart = Integer.compare(p.start, q.start);
            if (byStart != 0) {
                return byStart;
            }
            return Integer.compare(p.end, q.end);
        });
        for (DoorPair pair : pairs) {
            if (grid.get(pair.start) != Tile.WALL || grid.get(pair.end) != Tile.WALL) {
                continue;
            }
            CorridorRouter.Axis axis = CorridorRouter.axis(
                    grid.x(pair.start), grid.y(pair.start), grid.x(pair.end), grid.y(pair.end));
            int[] path = router.find(grid, pair.start, pair.end, axis);
            if (path == null) {
                continue;
            }
            for (int cell : path) {
                grid.set(cell, Tile.CORRIDOR_FLOOR);
            }
            int id = nextId[0]++;
            corridors.add(new Corridor(id, roomA, roomB, path, kind));
            a.addCorridor(id);
            b.addCorridor(id);
            return true;
        }
        return false;
    }

    /**
     * Wall tiles that share an edge with the room. Diagonal corners are left out, because a
     * walker moving only up, down, left, and right could not step from those into the room.
     */
    static List<Integer> doors(TileGrid grid, Room room) {
        List<Integer> points = new ArrayList<>();
        for (int offset = 0; offset < room.width; offset++) {
            addDoor(grid, points, room.x + offset, room.y - 1);
            addDoor(grid, points, room.x + offset, room.y + room.height);
        }
        for (int offset = 0; offset < room.height; offset++) {
            addDoor(grid, points, room.x - 1, room.y + offset);
            addDoor(grid, points, room.x + room.width, room.y + offset);
        }
        return points;
    }

    private static void addDoor(TileGrid grid, List<Integer> points, int x, int y) {
        if (grid.inBounds(x, y) && grid.get(x, y) == Tile.WALL) {
            points.add(grid.index(x, y));
        }
    }

    private static int manhattan(Room a, Room b) {
        return Math.abs(a.centerX - b.centerX) + Math.abs(a.centerY - b.centerY);
    }

    private static long pairKey(int a, int b) {
        int lo = Math.min(a, b);
        int hi = Math.max(a, b);
        return ((long) lo << 32) | (hi & 0xffffffffL);
    }

    private record Edge(int dist, int u, int v) {
    }

    private record DoorPair(int dist, int start, int end) {
    }

    private record Shortcut(int geom, int hops, int u, int v) {
    }
}
