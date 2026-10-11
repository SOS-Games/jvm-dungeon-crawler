package io.github.jvmdc.dungeongen.accretion;

import io.github.jvmdc.dungeongen.common.SeededRandom;
import io.github.jvmdc.dungeongen.common.Tile;
import io.github.jvmdc.dungeongen.common.TileGrid;

/**
 * Optional cave pass. Wall cells are seeded at random and smoothed for a few rounds,
 * then one door joins the carved rock to a nearby room. Rooms that are already placed stay put.
 */
public final class CaveOverlay {
    private CaveOverlay() {
    }

    /** Turns solid rock inside a rectangle into an irregular patch of open ground. */
    public static void fillGround(TileGrid grid, SeededRandom rng, int x0, int y0, int width, int height) {
        boolean[] open = new boolean[grid.width * grid.height];
        for (int y = y0; y < y0 + height; y++) {
            for (int x = x0; x < x0 + width; x++) {
                if (grid.get(x, y) == Tile.ROCK && rng.nextDouble() < 0.55) {
                    open[grid.index(x, y)] = true;
                }
            }
        }
        for (int round = 0; round < 4; round++) {
            boolean[] next = new boolean[open.length];
            for (int y = y0; y < y0 + height; y++) {
                for (int x = x0; x < x0 + width; x++) {
                    if (grid.get(x, y) != Tile.ROCK) {
                        continue;
                    }
                    int near = 0;
                    for (int dy = -1; dy <= 1; dy++) {
                        for (int dx = -1; dx <= 1; dx++) {
                            if (dx == 0 && dy == 0 || !grid.inBounds(x + dx, y + dy)) {
                                continue;
                            }
                            if (open[grid.index(x + dx, y + dy)]) {
                                near++;
                            }
                        }
                    }
                    int index = grid.index(x, y);
                    next[index] = near >= 5 || (open[index] && near >= 4);
                }
            }
            open = next;
        }
        for (int y = y0; y < y0 + height; y++) {
            for (int x = x0; x < x0 + width; x++) {
                if (open[grid.index(x, y)] && grid.get(x, y) == Tile.ROCK) {
                    grid.set(x, y, Tile.GROUND);
                }
            }
        }
    }

    /**
     * Nicks and bumps the border of a filled ground rectangle.
     * The yard stays one connected piece. {@code yard} marks its cells. {@code blocked} cells are left alone.
     */
    public static void roughen(TileGrid grid, SeededRandom rng, int x0, int y0, int width, int height,
            boolean[] yard, boolean[] blocked) {
        int span = Math.max(width, height);
        if (span < 2) {
            return;
        }
        int bites = 2 + rng.nextInt(4);
        for (int bite = 0; bite < bites; bite++) {
            int edge = rng.nextInt(4);
            int alongSpan = edge == 0 || edge == 2 ? width : height;
            int len = 1 + rng.nextInt(Math.min(3, alongSpan));
            int along = rng.nextInt(alongSpan - len + 1);
            if (rng.nextInt(2) == 0) {
                bump(grid, yard, blocked, x0, y0, width, height, edge, along, Math.min(2, len));
            } else {
                notch(grid, yard, x0, y0, width, height, edge, along, len);
            }
        }
    }

    private static void notch(TileGrid grid, boolean[] yard, int x0, int y0, int width, int height,
            int edge, int along, int len) {
        int[] changed = new int[len];
        int n = 0;
        for (int i = 0; i < len; i++) {
            int x = edgeCellX(x0, width, edge, along + i);
            int y = edgeCellY(y0, height, edge, along + i);
            if (!grid.inBounds(x, y)) {
                continue;
            }
            int index = grid.index(x, y);
            if (grid.get(index) == Tile.GROUND && yard[index]) {
                changed[n++] = index;
            }
        }
        if (n == 0) {
            return;
        }
        int before = pieceSize(grid, yard, x0 + width / 2, y0 + height / 2);
        for (int i = 0; i < n; i++) {
            grid.set(changed[i], Tile.ROCK);
            yard[changed[i]] = false;
        }
        int after = pieceSize(grid, yard, x0 + width / 2, y0 + height / 2);
        if (after < 8 || after != before - n) {
            for (int i = 0; i < n; i++) {
                grid.set(changed[i], Tile.GROUND);
                yard[changed[i]] = true;
            }
        }
    }

    private static void bump(TileGrid grid, boolean[] yard, boolean[] blocked, int x0, int y0, int width, int height,
            int edge, int along, int len) {
        int ox = edge == 1 ? 1 : edge == 3 ? -1 : 0;
        int oy = edge == 2 ? 1 : edge == 0 ? -1 : 0;
        for (int i = 0; i < len; i++) {
            int x = edgeCellX(x0, width, edge, along + i) + ox;
            int y = edgeCellY(y0, height, edge, along + i) + oy;
            if (!grid.inBounds(x, y) || x < 1 || y < 1 || x >= grid.width - 1 || y >= grid.height - 1) {
                continue;
            }
            int index = grid.index(x, y);
            if (blocked != null && blocked[index]) {
                continue;
            }
            if (grid.get(index) != Tile.ROCK || yard[index]) {
                continue;
            }
            boolean touches = false;
            for (int dir = 0; dir < 4; dir++) {
                int nx = x + (dir == 0 ? 0 : dir == 1 ? 1 : dir == 2 ? 0 : -1);
                int ny = y + (dir == 0 ? -1 : dir == 1 ? 0 : dir == 2 ? 1 : 0);
                if (grid.inBounds(nx, ny) && yard[grid.index(nx, ny)] && grid.get(nx, ny) == Tile.GROUND) {
                    touches = true;
                }
            }
            if (touches) {
                grid.set(index, Tile.GROUND);
                yard[index] = true;
            }
        }
    }

    private static int edgeCellX(int x0, int width, int edge, int along) {
        if (edge == 1) {
            return x0 + width - 1;
        }
        if (edge == 3) {
            return x0;
        }
        return x0 + along;
    }

    private static int edgeCellY(int y0, int height, int edge, int along) {
        if (edge == 0) {
            return y0;
        }
        if (edge == 2) {
            return y0 + height - 1;
        }
        return y0 + along;
    }

    private static int pieceSize(TileGrid grid, boolean[] yard, int sx, int sy) {
        int start = -1;
        if (grid.inBounds(sx, sy) && yard[grid.index(sx, sy)] && grid.get(sx, sy) == Tile.GROUND) {
            start = grid.index(sx, sy);
        } else {
            for (int i = 0; i < yard.length; i++) {
                if (yard[i] && grid.get(i) == Tile.GROUND) {
                    int x = grid.x(i);
                    int y = grid.y(i);
                    if (Math.abs(x - sx) <= 2 && Math.abs(y - sy) <= 2) {
                        start = i;
                        break;
                    }
                }
            }
        }
        if (start < 0) {
            return 0;
        }
        boolean[] seen = new boolean[yard.length];
        int[] queue = new int[yard.length];
        int head = 0;
        int tail = 0;
        seen[start] = true;
        queue[tail++] = start;
        int count = 0;
        while (head < tail) {
            int cell = queue[head++];
            count++;
            int x = grid.x(cell);
            int y = grid.y(cell);
            for (int dir = 0; dir < 4; dir++) {
                int nx = x + (dir == 0 ? 0 : dir == 1 ? 1 : dir == 2 ? 0 : -1);
                int ny = y + (dir == 0 ? -1 : dir == 1 ? 0 : dir == 2 ? 1 : 0);
                if (!grid.inBounds(nx, ny)) {
                    continue;
                }
                int next = grid.index(nx, ny);
                if (seen[next] || !yard[next] || grid.get(next) != Tile.GROUND) {
                    continue;
                }
                seen[next] = true;
                queue[tail++] = next;
            }
        }
        return count;
    }

    public static void apply(TileGrid grid, SeededRandom rng) {
        int width = grid.width;
        int height = grid.height;
        boolean[] open = new boolean[width * height];
        boolean[] carved = new boolean[width * height];
        for (int y = 1; y < height - 1; y++) {
            for (int x = 1; x < width - 1; x++) {
                if (grid.get(x, y) == Tile.WALL && rng.nextDouble() < 0.45) {
                    open[grid.index(x, y)] = true;
                }
            }
        }
        for (int round = 0; round < 4; round++) {
            boolean[] next = new boolean[open.length];
            for (int y = 1; y < height - 1; y++) {
                for (int x = 1; x < width - 1; x++) {
                    if (grid.get(x, y) != Tile.WALL) {
                        continue;
                    }
                    int near = 0;
                    for (int dy = -1; dy <= 1; dy++) {
                        for (int dx = -1; dx <= 1; dx++) {
                            if (dx == 0 && dy == 0) {
                                continue;
                            }
                            if (open[grid.index(x + dx, y + dy)]) {
                                near++;
                            }
                        }
                    }
                    int index = grid.index(x, y);
                    next[index] = near >= 5 || (open[index] && near >= 4);
                }
            }
            open = next;
        }
        for (int i = 0; i < open.length; i++) {
            if (open[i] && grid.get(i) == Tile.WALL) {
                grid.set(i, Tile.CORRIDOR_FLOOR);
                carved[i] = true;
            }
        }
        for (int y = 1; y < height - 1; y++) {
            for (int x = 1; x < width - 1; x++) {
                if (grid.get(x, y) != Tile.WALL) {
                    continue;
                }
                boolean northRoom = grid.get(x, y - 1) == Tile.ROOM_FLOOR;
                boolean southRoom = grid.get(x, y + 1) == Tile.ROOM_FLOOR;
                boolean westRoom = grid.get(x - 1, y) == Tile.ROOM_FLOOR;
                boolean eastRoom = grid.get(x + 1, y) == Tile.ROOM_FLOOR;
                boolean northCave = carved[grid.index(x, y - 1)];
                boolean southCave = carved[grid.index(x, y + 1)];
                boolean westCave = carved[grid.index(x - 1, y)];
                boolean eastCave = carved[grid.index(x + 1, y)];
                boolean bridge = (northRoom && southCave) || (southRoom && northCave)
                        || (westRoom && eastCave) || (eastRoom && westCave);
                if (bridge) {
                    grid.set(x, y, Tile.DOOR);
                    return;
                }
            }
        }
    }
}
