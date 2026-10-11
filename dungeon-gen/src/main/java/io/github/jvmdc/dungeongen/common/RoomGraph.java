package io.github.jvmdc.dungeongen.common;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Which rooms share a walkable floor. Built by walking open corridor and door tiles out of each
 * room, so a passage that brushes a third room still counts. Secret and locked doors are not walked.
 */
public final class RoomGraph {
    private static final int[] DX = {0, 0, -1, 1};
    private static final int[] DY = {-1, 1, 0, 0};

    private final int[][] neighbors;

    private RoomGraph(int[][] neighbors) {
        this.neighbors = neighbors;
    }

    public static RoomGraph derive(TileGrid grid, List<Room> rooms) {
        int count = rooms.size();
        int[] at = new int[grid.width * grid.height];
        Arrays.fill(at, -1);
        for (Room room : rooms) {
            for (int y = room.y; y < room.y + room.height; y++) {
                for (int x = room.x; x < room.x + room.width; x++) {
                    if (grid.inBounds(x, y) && room.contains(x, y)) {
                        at[grid.index(x, y)] = room.id;
                    }
                }
            }
        }

        boolean[][] linked = new boolean[count][count];
        int[] stamp = new int[at.length];
        int[] queue = new int[at.length];
        int generation = 0;
        for (Room room : rooms) {
            generation++;
            int head = 0;
            int tail = 0;
            for (int y = room.y; y < room.y + room.height; y++) {
                for (int x = room.x; x < room.x + room.width; x++) {
                    if (!room.contains(x, y)) {
                        continue;
                    }
                    tail = lookAround(grid, at, linked, stamp, queue, generation, room.id, x, y, tail);
                }
            }
            while (head < tail) {
                int cell = queue[head++];
                tail = lookAround(grid, at, linked, stamp, queue, generation, room.id,
                        grid.x(cell), grid.y(cell), tail);
            }
        }

        int[][] neighbors = new int[count][];
        for (int i = 0; i < count; i++) {
            List<Integer> list = new ArrayList<>();
            for (int j = 0; j < count; j++) {
                if (linked[i][j]) {
                    list.add(j);
                }
            }
            neighbors[i] = new int[list.size()];
            for (int j = 0; j < list.size(); j++) {
                neighbors[i][j] = list.get(j);
            }
        }
        return new RoomGraph(neighbors);
    }

    public int roomCount() {
        return neighbors.length;
    }

    public int[] neighbors(int room) {
        return neighbors[room].clone();
    }

    public boolean adjacent(int a, int b) {
        for (int neighbor : neighbors[a]) {
            if (neighbor == b) {
                return true;
            }
        }
        return false;
    }

    /** Room hops, or -1 when no open floor path joins them. */
    public int hops(int start, int end) {
        if (start == end) {
            return 0;
        }
        int[] dist = new int[neighbors.length];
        Arrays.fill(dist, -1);
        int[] queue = new int[neighbors.length];
        int head = 0;
        int tail = 0;
        dist[start] = 0;
        queue[tail++] = start;
        while (head < tail) {
            int current = queue[head++];
            if (current == end) {
                return dist[current];
            }
            for (int neighbor : neighbors[current]) {
                if (dist[neighbor] < 0) {
                    dist[neighbor] = dist[current] + 1;
                    queue[tail++] = neighbor;
                }
            }
        }
        return -1;
    }

    public boolean allReachable() {
        if (neighbors.length <= 1) {
            return true;
        }
        for (int i = 1; i < neighbors.length; i++) {
            if (hops(0, i) < 0) {
                return false;
            }
        }
        return true;
    }

    private static int lookAround(TileGrid grid, int[] at, boolean[][] linked, int[] stamp, int[] queue,
            int generation, int roomId, int x, int y, int tail) {
        if (!grid.inBounds(x, y)) {
            return tail;
        }
        for (int dir = 0; dir < DX.length; dir++) {
            int nx = x + DX[dir];
            int ny = y + DY[dir];
            if (!grid.inBounds(nx, ny)) {
                continue;
            }
            int index = grid.index(nx, ny);
            int owner = at[index];
            if (owner == roomId) {
                continue;
            }
            if (owner >= 0) {
                linked[roomId][owner] = true;
                linked[owner][roomId] = true;
                continue;
            }
            Tile tile = grid.get(index);
            if ((tile != Tile.CORRIDOR_FLOOR && tile != Tile.DOOR && tile != Tile.GROUND)
                    || stamp[index] == generation) {
                continue;
            }
            stamp[index] = generation;
            queue[tail++] = index;
        }
        return tail;
    }
}
