package io.github.jvmdc.dungeongen.corridor;

import io.github.jvmdc.dungeongen.common.Tile;
import io.github.jvmdc.dungeongen.common.TileGrid;

import java.util.Arrays;
import java.util.PriorityQueue;

/**
 * 4-neighbour A* with the original corridor costs. Steps are up, down, left, right.
 * Room floor blocks the search, except the goal tile. Walls and existing corridors can be walked.
 * A step costs 1, times 0.95 when it follows the dominant axis, plus 5 on a wall that touches a
 * room (not the start or end), plus 2 when stepping onto a corridor. The original turn penalty
 * is 0, so it is not applied. The heuristic is Manhattan, which is slightly high once
 * the 0.95 multiplier is in play, same as the original.
 */
final class CorridorRouter {
    static final int[] DX = {0, 0, -1, 1};
    static final int[] DY = {-1, 1, 0, 0};

    static final double DOMINANT_AXIS_COST = 0.95;
    static final double ROOM_TOUCH_PENALTY = 5.0;
    static final double CORRIDOR_CROSS_PENALTY = 2.0;
    static final double AXIS_THRESHOLD = 1.5;

    enum Axis {
        NONE, HORIZONTAL, VERTICAL
    }

    private final int width;
    private final int cellCount;
    private final double[] g;
    private final int[] parent;
    private final int[] seen;
    private final int[] closed;
    private int generation;

    CorridorRouter(int width, int height) {
        this.width = width;
        this.cellCount = width * height;
        this.g = new double[cellCount];
        this.parent = new int[cellCount];
        this.seen = new int[cellCount];
        this.closed = new int[cellCount];
    }

    static Axis axis(int x0, int y0, int x1, int y1) {
        int dx = Math.abs(x0 - x1);
        int dy = Math.abs(y0 - y1);
        if (dx > dy * AXIS_THRESHOLD) {
            return Axis.HORIZONTAL;
        }
        if (dy > dx * AXIS_THRESHOLD) {
            return Axis.VERTICAL;
        }
        return Axis.NONE;
    }

    /**
     * Path of cell indices from start to end, or null when there is no route of at least
     * {@link CorridorNetworkGenerator#MIN_CORRIDOR_TILES} tiles. A short path is rejected
     * instead of returning a sentinel cell.
     */
    int[] find(TileGrid grid, int start, int end, Axis axis) {
        int gen = nextGeneration();
        seen[start] = gen;
        g[start] = 0;
        parent[start] = -1;
        PriorityQueue<Open> open = new PriorityQueue<>();
        int seq = 0;
        open.add(new Open(heuristic(start, end), seq++, start));

        while (!open.isEmpty()) {
            Open current = open.poll();
            int cell = current.cell;
            if (closed[cell] == gen) {
                continue;
            }
            if (cell == end) {
                return reconstruct(start, end);
            }
            closed[cell] = gen;
            int x = cell % width;
            int y = cell / width;
            for (int dir = 0; dir < DX.length; dir++) {
                int nx = x + DX[dir];
                int ny = y + DY[dir];
                if (nx < 0 || ny < 0 || nx >= width || ny >= grid.height) {
                    continue;
                }
                int neighbor = ny * width + nx;
                if (closed[neighbor] == gen) {
                    continue;
                }
                Tile tile = grid.get(neighbor);
                if (neighbor != end && tile == Tile.ROOM_FLOOR) {
                    continue;
                }
                double step = 1.0;
                if (axis == Axis.HORIZONTAL && DX[dir] != 0) {
                    step *= DOMINANT_AXIS_COST;
                } else if (axis == Axis.VERTICAL && DY[dir] != 0) {
                    step *= DOMINANT_AXIS_COST;
                }
                if (tile == Tile.WALL && neighbor != start && neighbor != end && touchesRoom(grid, nx, ny)) {
                    step += ROOM_TOUCH_PENALTY;
                }
                if (tile == Tile.CORRIDOR_FLOOR) {
                    step += CORRIDOR_CROSS_PENALTY;
                }
                double nextG = g[cell] + step;
                if (seen[neighbor] == gen && nextG >= g[neighbor]) {
                    continue;
                }
                seen[neighbor] = gen;
                g[neighbor] = nextG;
                parent[neighbor] = cell;
                // Equal f keeps the earlier push. PriorityQueue alone does not promise that.
                open.add(new Open(nextG + heuristic(neighbor, end), seq++, neighbor));
            }
        }
        return null;
    }

    private int nextGeneration() {
        generation++;
        if (generation == Integer.MAX_VALUE) {
            Arrays.fill(seen, 0);
            Arrays.fill(closed, 0);
            generation = 1;
        }
        return generation;
    }

    private int[] reconstruct(int start, int end) {
        int length = 0;
        for (int cell = end; cell != -1; cell = parent[cell]) {
            length++;
            if (cell == start) {
                break;
            }
        }
        if (length < CorridorNetworkGenerator.MIN_CORRIDOR_TILES) {
            return null;
        }
        int[] path = new int[length];
        int cell = end;
        for (int i = length - 1; i >= 0; i--) {
            path[i] = cell;
            cell = parent[cell];
        }
        return path;
    }

    private int heuristic(int a, int b) {
        int ax = a % width;
        int ay = a / width;
        int bx = b % width;
        int by = b / width;
        return Math.abs(ax - bx) + Math.abs(ay - by);
    }

    private boolean touchesRoom(TileGrid grid, int x, int y) {
        for (int dir = 0; dir < DX.length; dir++) {
            int nx = x + DX[dir];
            int ny = y + DY[dir];
            if (nx >= 0 && ny >= 0 && nx < width && ny < grid.height
                    && grid.get(nx, ny) == Tile.ROOM_FLOOR) {
                return true;
            }
        }
        return false;
    }

    /** f, then insertion order, so a tie does not depend on heap layout. */
    private record Open(double f, int seq, int cell) implements Comparable<Open> {
        @Override
        public int compareTo(Open other) {
            int byCost = Double.compare(f, other.f);
            if (byCost != 0) {
                return byCost;
            }
            return Integer.compare(seq, other.seq);
        }
    }
}
