package io.github.jvmdc.dungeongen.accretion;

import io.github.jvmdc.dungeongen.common.Room;
import io.github.jvmdc.dungeongen.common.RoomGraph;
import io.github.jvmdc.dungeongen.common.Tile;
import io.github.jvmdc.dungeongen.common.TileGrid;

import java.util.List;

/**
 * Up stairs go in the first open room. Down stairs go in the open room furthest from them
 * along the room graph. Locked and secret rooms are skipped. The stair cell needs a 3×3 of
 * open floor so an arrival is not jammed against a wall.
 */
public final class StairPlacer {
    private StairPlacer() {
    }

    public static int[] place(TileGrid grid, List<Room> rooms, RoomGraph graph) {
        int upRoom = -1;
        for (Room room : rooms) {
            if (site(grid, room) >= 0) {
                upRoom = room.id;
                break;
            }
        }
        if (upRoom < 0) {
            return new int[] {-1, -1};
        }
        int up = site(grid, rooms.get(upRoom));
        int downRoom = -1;
        int best = -1;
        for (Room room : rooms) {
            if (room.id == upRoom || site(grid, room) < 0) {
                continue;
            }
            int hops = graph.hops(upRoom, room.id);
            if (hops > best) {
                best = hops;
                downRoom = room.id;
            }
        }
        int down = downRoom < 0 ? otherSite(grid, rooms.get(upRoom), up) : site(grid, rooms.get(downRoom));
        grid.set(up, Tile.STAIRS_UP);
        if (down >= 0 && down != up) {
            grid.set(down, Tile.STAIRS_DOWN);
        } else {
            down = -1;
        }
        return new int[] {up, down};
    }

    private static int site(TileGrid grid, Room room) {
        if (room.secret || room.locked || room.width < 3 || room.height < 3) {
            return -1;
        }
        for (int y = room.y + 1; y < room.y + room.height - 1; y++) {
            for (int x = room.x + 1; x < room.x + room.width - 1; x++) {
                if (openBlock(grid, room, x, y)) {
                    return grid.index(x, y);
                }
            }
        }
        return -1;
    }

    private static int otherSite(TileGrid grid, Room room, int used) {
        for (int y = room.y + 1; y < room.y + room.height - 1; y++) {
            for (int x = room.x + 1; x < room.x + room.width - 1; x++) {
                int index = grid.index(x, y);
                if (index != used && openBlock(grid, room, x, y)) {
                    return index;
                }
            }
        }
        return -1;
    }

    private static boolean openBlock(TileGrid grid, Room room, int x, int y) {
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                int nx = x + dx;
                int ny = y + dy;
                if (!room.contains(nx, ny)) {
                    return false;
                }
                Tile tile = grid.get(nx, ny);
                if (!tile.isOpen()) {
                    return false;
                }
            }
        }
        return true;
    }
}
