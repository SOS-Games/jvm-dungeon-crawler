package io.github.jvmdc.dungeongen.common;

import java.util.function.Predicate;

/** 4-connected walk over cells the predicate accepts. */
public final class FloodFill {
    private static final int[] DX = {0, 0, -1, 1};
    private static final int[] DY = {-1, 1, 0, 0};

    private FloodFill() {
    }

    public static boolean[] region(TileGrid grid, int start, Predicate<Tile> pass) {
        boolean[] seen = new boolean[grid.width * grid.height];
        if (start < 0 || start >= seen.length || !pass.test(grid.get(start))) {
            return seen;
        }
        int[] queue = new int[seen.length];
        int head = 0;
        int tail = 0;
        seen[start] = true;
        queue[tail++] = start;
        while (head < tail) {
            int cell = queue[head++];
            int x = grid.x(cell);
            int y = grid.y(cell);
            for (int dir = 0; dir < DX.length; dir++) {
                int nx = x + DX[dir];
                int ny = y + DY[dir];
                if (!grid.inBounds(nx, ny)) {
                    continue;
                }
                int next = grid.index(nx, ny);
                if (seen[next] || !pass.test(grid.get(next))) {
                    continue;
                }
                seen[next] = true;
                queue[tail++] = next;
            }
        }
        return seen;
    }
}
