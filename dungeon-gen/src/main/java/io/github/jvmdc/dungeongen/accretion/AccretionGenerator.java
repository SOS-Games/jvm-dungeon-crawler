package io.github.jvmdc.dungeongen.accretion;

import io.github.jvmdc.dungeongen.FeatureHooks;
import io.github.jvmdc.dungeongen.LayoutConfig;
import io.github.jvmdc.dungeongen.LayoutGenerator;
import io.github.jvmdc.dungeongen.common.Dungeon;
import io.github.jvmdc.dungeongen.common.Room;
import io.github.jvmdc.dungeongen.common.RoomGraph;
import io.github.jvmdc.dungeongen.common.SeededRandom;
import io.github.jvmdc.dungeongen.common.Tile;
import io.github.jvmdc.dungeongen.common.TileGrid;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds clusters of walled rooms beside open courtyards, with solid rock left in between.
 * Courtyards are irregular ground, not plain rectangles, and rooms open onto them.
 * Each room is a floor shape wrapped in a one-tile wall. Doors are cut into that wall.
 * Rooms sometimes share a wall, and otherwise sit a tile or two of ground apart.
 * Short tunnels are used only when two areas would otherwise stay separate.
 * Placement stops at the room cap or the walkable target, whichever comes first.
 */
public final class AccretionGenerator implements LayoutGenerator {
    static final int[] DX = {0, 1, 0, -1};
    static final int[] DY = {-1, 0, 1, 0};

    @Override
    public Dungeon generate(LayoutConfig config, long seed) {
        SeededRandom rng = new SeededRandom(seed);
        TileGrid grid = new TileGrid(config.width, config.height);
        for (int i = 0; i < grid.width * grid.height; i++) {
            grid.set(i, Tile.ROCK);
        }
        Plot plot = placeOpenRegions(config, grid, rng);
        List<Node> nodes = new ArrayList<>();
        Scratch scratch = new Scratch();
        placeStart(config, grid, rng, nodes, scratch, plot);
        int attempts = 0;
        int limit = Math.max(80, config.roomCount * 40);
        int softCap = config.width >= 56 && config.height >= 56 ? 30 : config.roomCount;
        while (attempts < limit && live(nodes) < config.roomCount) {
            if (config.floorRatio > 0 && live(nodes) >= 8 && coverage(grid) >= config.floorRatio) {
                break;
            }
            if (live(nodes) >= softCap
                    && (config.floorRatio <= 0 || coverage(grid) >= config.floorRatio - 0.03)) {
                break;
            }
            attempts++;
            boolean preferYard = plot.regionCount > 0 && live(nodes) <= plot.regionCount * 3;
            boolean placed = (preferYard || attempts % 2 == 0) && attachToCourtyard(config, grid, rng, nodes, scratch, plot);
            if (!placed) {
                attach(config, grid, rng, nodes, scratch, plot);
            }
        }
        addLoops(config, grid, rng, nodes, plot);
        connectRegions(grid, config.rockBorder, plot);
        dropDeadEnds(config, grid, rng, nodes, plot);
        markSecrets(config, grid, rng, nodes);
        decorate(config, grid, rng, nodes, plot);
        VaultLibrary.stamp(grid, nodes, rng, config.vaultCount);
        for (int pass = 0; pass < 3; pass++) {
            connectRegions(grid, config.rockBorder, plot);
            repairDoors(grid, nodes, plot);
        }
        for (int pass = 0; pass < 4; pass++) {
            connectRegions(grid, config.rockBorder, plot);
            closeBentDoors(grid, nodes, plot);
        }
        connectRegions(grid, config.rockBorder, plot);
        linkOrDropGround(grid, nodes, plot);
        closeBentDoors(grid, nodes, plot);
        connectRegions(grid, config.rockBorder, plot);
        closeBentDoors(grid, nodes, plot);
        linkOrDropGround(grid, nodes, plot);
        polish(config, grid, nodes, plot, rng);
        splitRockIfNeeded(grid, plot, config.rockBorder);
        List<Room> rooms = toRooms(nodes);
        RoomGraph graph = RoomGraph.derive(grid, rooms);
        int[] stairs = StairPlacer.place(grid, rooms, graph);
        Dungeon dungeon = new Dungeon(seed, config, grid, List.copyOf(rooms), List.of(),
                graph, stairs[0], stairs[1]);
        FeatureHooks.apply(dungeon, config);
        return dungeon;
    }

    private static Plot placeOpenRegions(LayoutConfig config, TileGrid grid, SeededRandom rng) {
        Plot plot = new Plot(grid.width * grid.height);
        int border = config.rockBorder;
        int left = Math.max(border, (int) Math.ceil(grid.width * 0.2));
        int right = Math.min(grid.width - border, (int) Math.floor(grid.width * 0.8));
        int top = Math.max(border, (int) Math.ceil(grid.height * 0.2));
        int bottom = Math.min(grid.height - border, (int) Math.floor(grid.height * 0.8));
        int availW = right - left;
        int availH = bottom - top;
        if (availW >= 4 && availH >= 4) {
            for (int i = 0; i < config.openRegionCount && plot.regionCount < plot.regions.length; i++) {
                int zoneLeft = left;
                int zoneRight = right;
                int zoneTop = top;
                int zoneBottom = bottom;
                if (config.openRegionCount > 1 && availW >= availH) {
                    int mid = left + availW / 2;
                    if (i % 2 == 0) {
                        zoneRight = mid;
                    } else {
                        zoneLeft = mid;
                    }
                } else if (config.openRegionCount > 1) {
                    int mid = top + availH / 2;
                    if (i % 2 == 0) {
                        zoneBottom = mid;
                    } else {
                        zoneTop = mid;
                    }
                }
                int zoneW = zoneRight - zoneLeft;
                int zoneH = zoneBottom - zoneTop;
                int maxW = Math.min(config.openRegionMaxSize, zoneW);
                int maxH = Math.min(config.openRegionMaxSize, zoneH);
                int minW = Math.min(config.openRegionMinSize, maxW);
                int minH = Math.min(config.openRegionMinSize, maxH);
                if (minW < 4 || minH < 4) {
                    continue;
                }
                for (int attempt = 0; attempt < 24; attempt++) {
                    int w = minW >= maxW ? minW : rng.nextInt(minW, maxW + 1);
                    int h = minH >= maxH ? minH : rng.nextInt(minH, maxH + 1);
                    int xSpan = zoneRight - w - zoneLeft;
                    int ySpan = zoneBottom - h - zoneTop;
                    int x = zoneLeft + (xSpan > 0 ? rng.nextInt(xSpan + 1) : 0);
                    int y = zoneTop + (ySpan > 0 ? rng.nextInt(ySpan + 1) : 0);
                    if (overlapsRegion(plot, x, y, w, h)) {
                        continue;
                    }
                    fillCourtyard(grid, plot, x, y, w, h);
                    CaveOverlay.roughen(grid, rng, x, y, w, h, plot.courtyard, plot.keep);
                    dressCourtyard(grid, rng, plot, x, y, w, h);
                    plot.regions[plot.regionCount][0] = x;
                    plot.regions[plot.regionCount][1] = y;
                    plot.regions[plot.regionCount][2] = w;
                    plot.regions[plot.regionCount][3] = h;
                    plot.regionCount++;
                    break;
                }
            }
        }
        joinNearbyYards(grid, plot, rng);
        if (grid.width >= 56 && grid.height >= 56) {
            placeKeeps(config, grid, plot);
        }
        return plot;
    }

    private static boolean overlapsRegion(Plot plot, int x, int y, int w, int h) {
        for (int i = 0; i < plot.regionCount; i++) {
            int[] region = plot.regions[i];
            if (x < region[0] + region[2] && x + w > region[0] && y < region[1] + region[3] && y + h > region[1]) {
                return true;
            }
        }
        return false;
    }

    /** A solid block of ground. The courtyard itself has no wall. */
    private static void fillCourtyard(TileGrid grid, Plot plot, int x, int y, int w, int h) {
        for (int yy = y; yy < y + h; yy++) {
            for (int xx = x; xx < x + w; xx++) {
                if (grid.get(xx, yy) == Tile.ROCK) {
                    grid.set(xx, yy, Tile.GROUND);
                }
                plot.courtyard[grid.index(xx, yy)] = true;
            }
        }
    }

    /**
     * Reserves two corner pockets of rock. They stay plain rock unless the finished map
     * would otherwise be a single rock mass, in which case one pocket is walled off.
     */
    private static void placeKeeps(LayoutConfig config, TileGrid grid, Plot plot) {
        int b = config.rockBorder;
        reservePocket(grid, plot, b, b, 8, 6, 1, 1);
        reservePocket(grid, plot, grid.width - b - 8, grid.height - b - 6, 8, 6, -1, -1);
    }

    private static void reservePocket(TileGrid grid, Plot plot, int x, int y, int w, int h, int sx, int sy) {
        if (!pocketClear(grid, plot, x, y, w, h, sx, sy)) {
            return;
        }
        for (int yy = y; yy < y + h; yy++) {
            for (int xx = x; xx < x + w; xx++) {
                plot.keep[grid.index(xx, yy)] = true;
            }
        }
        markSeal(grid, plot, x, y, w, h, sx, sy);
    }

    private static void splitRockIfNeeded(TileGrid grid, Plot plot, int border) {
        if (grid.width < 56 || grid.height < 56 || rockMasses(grid, border, 40) >= 2) {
            return;
        }
        int w = 8;
        int h = 6;
        int[][] corners = {
                {border, border, 1, 1},
                {grid.width - border - w, border, -1, 1},
                {border, grid.height - border - h, 1, -1},
                {grid.width - border - w, grid.height - border - h, -1, -1}
        };
        for (int[] corner : corners) {
            if (wallOffPocket(grid, plot, corner[0], corner[1], w, h, corner[2], corner[3])) {
                return;
            }
        }
    }

    private static boolean wallOffPocket(TileGrid grid, Plot plot, int x, int y, int w, int h, int sx, int sy) {
        if (!pocketClear(grid, plot, x, y, w, h, sx, sy)) {
            return false;
        }
        int x0 = sx > 0 ? x + w : x - 1;
        int y0 = sy > 0 ? y + h : y - 1;
        for (int i = 0; i < h; i++) {
            grid.set(x0, y + i, Tile.WALL);
        }
        for (int i = 0; i < w; i++) {
            grid.set(x + i, y0, Tile.WALL);
        }
        grid.set(x0, y0, Tile.WALL);
        return true;
    }

    private static boolean pocketClear(TileGrid grid, Plot plot, int x, int y, int w, int h, int sx, int sy) {
        if (x < 1 || y < 1 || x + w >= grid.width || y + h >= grid.height) {
            return false;
        }
        for (int yy = y; yy < y + h; yy++) {
            for (int xx = x; xx < x + w; xx++) {
                if (grid.get(xx, yy) != Tile.ROCK || plot.courtyard[grid.index(xx, yy)]) {
                    return false;
                }
            }
        }
        int x0 = sx > 0 ? x + w : x - 1;
        int y0 = sy > 0 ? y + h : y - 1;
        if (!grid.inBounds(x0, y0)) {
            return false;
        }
        for (int i = 0; i < h; i++) {
            if (!sealClear(grid, plot, x0, y + i)) {
                return false;
            }
        }
        for (int i = 0; i < w; i++) {
            if (!sealClear(grid, plot, x + i, y0)) {
                return false;
            }
        }
        return sealClear(grid, plot, x0, y0);
    }

    private static boolean sealClear(TileGrid grid, Plot plot, int x, int y) {
        return grid.inBounds(x, y) && grid.get(x, y) == Tile.ROCK && !plot.courtyard[grid.index(x, y)];
    }

    private static void markSeal(TileGrid grid, Plot plot, int x, int y, int w, int h, int sx, int sy) {
        int x0 = sx > 0 ? x + w : x - 1;
        int y0 = sy > 0 ? y + h : y - 1;
        for (int i = 0; i < h; i++) {
            plot.keep[grid.index(x0, y + i)] = true;
        }
        for (int i = 0; i < w; i++) {
            plot.keep[grid.index(x + i, y0)] = true;
        }
        plot.keep[grid.index(x0, y0)] = true;
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
                        int nx = cx + DX[dir];
                        int ny = cy + DY[dir];
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

    private static void placeStart(LayoutConfig config, TileGrid grid, SeededRandom rng,
            List<Node> nodes, Scratch scratch, Plot plot) {
        int[][] local = null;
        for (int attempt = 0; attempt < 8; attempt++) {
            int[][] rolled = normalize(shape(config, rng, 0));
            int[] size = bounds(rolled);
            local = rolled;
            if (size[2] >= 5 && size[3] >= 5) {
                break;
            }
        }
        int[] size = bounds(local);
        int[][] parts = null;
        int[][] tries = {
                {(grid.width - size[2]) / 2, (grid.height - size[3]) / 2},
                {grid.width / 3, grid.height / 2},
                {grid.width / 2, grid.height / 3},
                {config.rockBorder + 2, grid.height / 2},
                {grid.width / 2, config.rockBorder + 2}
        };
        for (int[] spot : tries) {
            int[][] candidate = translate(local, spot[0], spot[1]);
            if (fits(config, grid, candidate, plot)) {
                parts = candidate;
                break;
            }
        }
        if (parts == null) {
            parts = findClearing(config, grid, plot, Math.min(5, size[2]), Math.min(5, size[3]));
        }
        if (parts == null) {
            return;
        }
        Node start = new Node(parts);
        paint(grid, start, scratch, plot);
        scratch.reset();
        start.door = cutOutsideDoor(grid, List.of(start), start, false);
        doorOntoCourtyard(grid, start);
        nodes.add(start);
    }

    private static int[][] findClearing(LayoutConfig config, TileGrid grid, Plot plot, int w, int h) {
        for (int y = config.rockBorder + 1; y < grid.height - config.rockBorder - h; y += 2) {
            for (int x = config.rockBorder + 1; x < grid.width - config.rockBorder - w; x += 2) {
                int[][] parts = {{x, y, w, h}};
                if (fits(config, grid, parts, plot)) {
                    return parts;
                }
            }
        }
        return null;
    }

    private static void attach(LayoutConfig config, TileGrid grid, SeededRandom rng,
            List<Node> nodes, Scratch scratch, Plot plot) {
        Node parent = pickParent(config, grid, nodes, rng, plot);
        int dir = pickDir(config, grid, parent, rng, plot);
        if (dir < 0) {
            return;
        }
        int[][] local = normalize(shape(config, rng, dir));
        int[] size = bounds(local);
        int gap = chooseGap(config, rng);
        int face = DX[dir] != 0 ? parent.h : parent.w;
        int cross = DX[dir] != 0 ? size[3] : size[2];
        if (face < 1 || cross < 1) {
            return;
        }
        int along = rng.nextInt(face);
        int shift = rng.nextInt(cross);
        int ox;
        int oy;
        if (DX[dir] > 0) {
            ox = parent.x + parent.w + 1 + gap;
            oy = parent.y + along - shift;
        } else if (DX[dir] < 0) {
            ox = parent.x - gap - 1 - size[2];
            oy = parent.y + along - shift;
        } else if (DY[dir] > 0) {
            ox = parent.x + along - shift;
            oy = parent.y + parent.h + 1 + gap;
        } else {
            ox = parent.x + along - shift;
            oy = parent.y - gap - 1 - size[3];
        }
        int[][] world = translate(local, ox, oy);
        if (fits(config, grid, world, plot)) {
            Node child = new Node(world);
            paint(grid, child, scratch, plot);
            if (gap > 0) {
                fillGap(grid, parent, child, dir, gap, scratch);
            }
            int door = cutJoin(grid, parent, child, dir, gap, scratch);
            if (door >= 0) {
                scratch.reset();
                child.parent = parent;
                child.door = door;
                doorOntoCourtyard(grid, child);
                nodes.add(child);
                return;
            }
            scratch.restore(grid);
        }
        placeFallback(config, grid, parent, dir, nodes, scratch, plot);
    }

    /** Puts a room against a courtyard edge so its door opens onto that ground. */
    private static boolean attachToCourtyard(LayoutConfig config, TileGrid grid, SeededRandom rng,
            List<Node> nodes, Scratch scratch, Plot plot) {
        if (plot.regionCount == 0) {
            return false;
        }
        for (int attempt = 0; attempt < 6; attempt++) {
            int[] region = plot.regions[rng.nextInt(plot.regionCount)];
            int away = rng.nextInt(4);
            int[][] local = normalize(shape(config, rng, away));
            int[] size = bounds(local);
            int face = DX[away] != 0 ? region[3] : region[2];
            int cross = DX[away] != 0 ? size[3] : size[2];
            if (face < 1 || cross < 1) {
                continue;
            }
            int along = rng.nextInt(face);
            int shift = rng.nextInt(cross);
            int ox;
            int oy;
            if (DX[away] > 0) {
                ox = region[0] + region[2] + 1;
                oy = region[1] + along - shift;
            } else if (DX[away] < 0) {
                ox = region[0] - 1 - size[2];
                oy = region[1] + along - shift;
            } else if (DY[away] > 0) {
                ox = region[0] + along - shift;
                oy = region[1] + region[3] + 1;
            } else {
                ox = region[0] + along - shift;
                oy = region[1] - 1 - size[3];
            }
            int[][] world = translate(local, ox, oy);
            if (!fits(config, grid, world, plot)) {
                continue;
            }
            Node child = new Node(world);
            paint(grid, child, scratch, plot);
            int door = cutCourtyardDoor(grid, child, (away + 2) % 4);
            if (door < 0) {
                scratch.restore(grid);
                continue;
            }
            scratch.reset();
            child.door = door;
            nodes.add(child);
            return true;
        }
        return false;
    }

    private static int cutCourtyardDoor(TileGrid grid, Node node, int toward) {
        int face = DX[toward] != 0 ? node.h : node.w;
        for (int along = 0; along < face; along++) {
            int wx = outsideX(node, toward, along);
            int wy = outsideY(node, toward, along);
            int gx = wx + DX[toward];
            int gy = wy + DY[toward];
            int ix = wx - DX[toward];
            int iy = wy - DY[toward];
            if (!grid.inBounds(wx, wy) || !grid.inBounds(gx, gy)) {
                continue;
            }
            if (grid.get(wx, wy) == Tile.WALL && grid.get(gx, gy) == Tile.GROUND && node.contains(ix, iy)
                    && grid.get(ix, iy).isOpen()) {
                grid.set(wx, wy, Tile.DOOR);
                return grid.index(wx, wy);
            }
        }
        return -1;
    }

    /** Shared wall about 30% of the time; otherwise a gap of 1, 2, or 3, weighted toward the shorter gaps. */
    private static int chooseGap(LayoutConfig config, SeededRandom rng) {
        if (rng.nextDouble() < config.wallSharing) {
            return 0;
        }
        if (config.roomGapMax <= config.roomGapMin) {
            return config.roomGapMin;
        }
        double roll = rng.nextDouble();
        if (roll < 0.55) {
            return config.roomGapMin;
        }
        if (roll < 0.85) {
            return Math.min(config.roomGapMax, config.roomGapMin + 1);
        }
        return config.roomGapMax;
    }

    /** A short rectangle on an open side, used when the rolled shape does not fit. */
    private static void placeFallback(LayoutConfig config, TileGrid grid, Node parent, int dir,
            List<Node> nodes, Scratch scratch, Plot plot) {
        if (placeOnSide(config, grid, parent, dir, nodes, scratch, plot)) {
            return;
        }
        for (int other = 0; other < 4; other++) {
            if (other != dir && placeOnSide(config, grid, parent, other, nodes, scratch, plot)) {
                return;
            }
        }
    }

    private static boolean placeOnSide(LayoutConfig config, TileGrid grid, Node parent, int dir,
            List<Node> nodes, Scratch scratch, Plot plot) {
        int face = DX[dir] != 0 ? parent.h : parent.w;
        int bestAlong = -1;
        int bestDepth = 1;
        for (int along = 0; along < face; along++) {
            int depth = freeDepth(config, grid, parent, dir, along);
            if (depth > bestDepth) {
                bestDepth = depth;
                bestAlong = along;
            }
        }
        if (bestAlong < 0 || bestDepth < 3) {
            return false;
        }
        int depth = Math.min(6, bestDepth - 1);
        int span = Math.min(6, face);
        int along0 = Math.min(bestAlong, Math.max(0, face - span));
        int ox;
        int oy;
        int w;
        int h;
        if (DX[dir] > 0) {
            ox = parent.x + parent.w + 1;
            oy = parent.y + along0;
            w = depth;
            h = span;
        } else if (DX[dir] < 0) {
            ox = parent.x - 1 - depth;
            oy = parent.y + along0;
            w = depth;
            h = span;
        } else if (DY[dir] > 0) {
            ox = parent.x + along0;
            oy = parent.y + parent.h + 1;
            w = span;
            h = depth;
        } else {
            ox = parent.x + along0;
            oy = parent.y - 1 - depth;
            w = span;
            h = depth;
        }
        int[][] world = {{ox, oy, w, h}};
        if (!fits(config, grid, world, plot)) {
            return false;
        }
        Node child = new Node(world);
        paint(grid, child, scratch, plot);
        int door = cutJoin(grid, parent, child, dir, 0, scratch);
        if (door < 0) {
            scratch.restore(grid);
            return false;
        }
        scratch.reset();
        child.parent = parent;
        child.door = door;
        doorOntoCourtyard(grid, child);
        nodes.add(child);
        return true;
    }

    private static int freeDepth(LayoutConfig config, TileGrid grid, Node parent, int dir, int along) {
        int x = DX[dir] != 0
                ? (DX[dir] > 0 ? parent.x + parent.w + 1 : parent.x - 2)
                : parent.x + Math.min(along, Math.max(0, parent.w - 1));
        int y = DY[dir] != 0
                ? (DY[dir] > 0 ? parent.y + parent.h + 1 : parent.y - 2)
                : parent.y + Math.min(along, Math.max(0, parent.h - 1));
        int depth = 0;
        for (int step = 0; step < 8; step++) {
            int cx = x + DX[dir] * step;
            int cy = y + DY[dir] * step;
            if (!inside(config, grid, cx, cy)) {
                break;
            }
            Tile tile = grid.get(cx, cy);
            if (tile != Tile.ROCK && tile != Tile.GROUND) {
                break;
            }
            depth++;
        }
        return depth;
    }

    private static int[][] shape(LayoutConfig config, SeededRandom rng, int dir) {
        int limit = gridLimit(config);
        double sizeRoll = rng.nextDouble();
        if (sizeRoll < 0.15) {
            int length = Math.min(16, Math.max(8, limit));
            if (limit >= 8) {
                length = rng.nextInt(8, Math.min(16, limit) + 1);
            }
            int thin = 2 + rng.nextInt(2);
            if (DX[dir] != 0) {
                return new int[][] {{0, 0, length, thin}};
            }
            return new int[][] {{0, 0, thin, length}};
        }
        int w;
        int h;
        if (sizeRoll < 0.25) {
            w = rng.nextInt(3, 5);
            h = rng.nextInt(3, 5);
        } else if (sizeRoll < 0.75) {
            w = rng.nextInt(5, 9);
            h = rng.nextInt(5, 9);
        } else {
            w = rng.nextInt(9, 13);
            h = rng.nextInt(9, 13);
        }
        w = Math.max(2, Math.min(w, Math.min(config.maxRoomWidth, limit)));
        h = Math.max(2, Math.min(h, Math.min(config.maxRoomHeight, limit)));
        double shape = rng.nextDouble();
        if (shape < 0.60 || w < 4 || h < 4) {
            return new int[][] {{0, 0, w, h}};
        }
        int thick = Math.max(2, Math.min(w, h) / 2);
        if (shape < 0.85) {
            if (rng.nextInt(2) == 0) {
                int side = rng.nextInt(2) == 0 ? -thick : h;
                return new int[][] {{0, 0, w, h}, {0, side, Math.max(2, w / 2), thick}};
            }
            return new int[][] {
                    {0, 0, w, h},
                    {w / 2 - thick / 2, h, Math.max(2, thick), Math.max(2, h / 2)}
            };
        }
        return new int[][] {
                {0, thick, w, Math.max(2, h - thick)},
                {w / 2 - thick / 2, 0, Math.max(2, thick), h}
        };
    }

    private static int gridLimit(LayoutConfig config) {
        return Math.max(4, Math.min(config.width, config.height) - 2 * config.rockBorder - 2);
    }

    private static boolean fits(LayoutConfig config, TileGrid grid, int[][] parts, Plot plot) {
        Node node = new Node(parts);
        for (int[] part : parts) {
            for (int y = part[1]; y < part[1] + part[3]; y++) {
                for (int x = part[0]; x < part[0] + part[2]; x++) {
                    if (!inside(config, grid, x, y)) {
                        return false;
                    }
                    int index = grid.index(x, y);
                    if (grid.get(x, y) != Tile.ROCK || plot.keep[index] || plot.courtyard[index]) {
                        return false;
                    }
                    for (int dir = 0; dir < 4; dir++) {
                        int nx = x + DX[dir];
                        int ny = y + DY[dir];
                        if (node.contains(nx, ny)) {
                            continue;
                        }
                        int ringIndex = grid.index(nx, ny);
                        if (!inside(config, grid, nx, ny) || plot.keep[ringIndex] || plot.courtyard[ringIndex]) {
                            return false;
                        }
                        Tile ring = grid.get(nx, ny);
                        if (ring != Tile.ROCK && ring != Tile.GROUND && ring != Tile.WALL) {
                            return false;
                        }
                    }
                }
            }
        }
        return true;
    }

    private static boolean inside(LayoutConfig config, TileGrid grid, int x, int y) {
        return x >= config.rockBorder && y >= config.rockBorder
                && x < grid.width - config.rockBorder && y < grid.height - config.rockBorder;
    }

    private static void paint(TileGrid grid, Node node, Scratch scratch, Plot plot) {
        for (int[] part : node.parts) {
            for (int y = part[1]; y < part[1] + part[3]; y++) {
                for (int x = part[0]; x < part[0] + part[2]; x++) {
                    for (int dir = 0; dir < 4; dir++) {
                        int nx = x + DX[dir];
                        int ny = y + DY[dir];
                        if (node.contains(nx, ny) || !grid.inBounds(nx, ny)) {
                            continue;
                        }
                        int index = grid.index(nx, ny);
                        if (plot.courtyard[index] || plot.keep[index]) {
                            continue;
                        }
                        Tile tile = grid.get(nx, ny);
                        if (tile == Tile.ROCK || tile == Tile.GROUND) {
                            scratch.set(grid, nx, ny, Tile.WALL);
                        }
                    }
                }
            }
        }
        for (int[] part : node.parts) {
            for (int y = part[1]; y < part[1] + part[3]; y++) {
                for (int x = part[0]; x < part[0] + part[2]; x++) {
                    scratch.set(grid, x, y, Tile.ROOM_FLOOR);
                }
            }
        }
    }

    private static void fillGap(TileGrid grid, Node parent, Node child, int dir, int gap, Scratch scratch) {
        int cross0 = DX[dir] != 0
                ? Math.max(parent.y, child.y)
                : Math.max(parent.x, child.x);
        int cross1 = DX[dir] != 0
                ? Math.min(parent.y + parent.h, child.y + child.h)
                : Math.min(parent.x + parent.w, child.x + child.w);
        for (int cross = cross0; cross < cross1; cross++) {
            for (int step = 1; step <= gap; step++) {
                int x;
                int y;
                if (DX[dir] > 0) {
                    x = parent.x + parent.w + step;
                    y = cross;
                } else if (DX[dir] < 0) {
                    x = parent.x - step;
                    y = cross;
                } else if (DY[dir] > 0) {
                    x = cross;
                    y = parent.y + parent.h + step;
                } else {
                    x = cross;
                    y = parent.y - step;
                }
                if (grid.inBounds(x, y) && grid.get(x, y) == Tile.ROCK) {
                    scratch.set(grid, x, y, Tile.GROUND);
                }
            }
        }
    }

    /** Door in the wall between two rooms, or onto the ground that separates them. */
    private static int cutJoin(TileGrid grid, Node parent, Node child, int dir, int gap, Scratch scratch) {
        int door = -1;
        for (int[] part : child.parts) {
            for (int y = part[1]; y < part[1] + part[3]; y++) {
                for (int x = part[0]; x < part[0] + part[2]; x++) {
                    int wx = x - DX[dir];
                    int wy = y - DY[dir];
                    if (!grid.inBounds(wx, wy) || child.contains(wx, wy)) {
                        continue;
                    }
                    boolean towardParent = gap == 0
                            ? parent.contains(wx - DX[dir], wy - DY[dir])
                            : parent.contains(wx - DX[dir] * (gap + 1), wy - DY[dir] * (gap + 1));
                    if (!towardParent || grid.get(wx, wy) != Tile.WALL) {
                        continue;
                    }
                    scratch.set(grid, wx, wy, Tile.DOOR);
                    if (gap > 0) {
                        int px = wx - DX[dir] * gap;
                        int py = wy - DY[dir] * gap;
                        if (grid.inBounds(px, py) && grid.get(px, py) == Tile.WALL
                                && parent.contains(px - DX[dir], py - DY[dir])) {
                            scratch.set(grid, px, py, Tile.DOOR);
                        }
                    }
                    door = grid.index(wx, wy);
                    return door;
                }
            }
        }
        return door;
    }

    private static int cutOutsideDoor(TileGrid grid, List<Node> nodes, Node node, boolean secret) {
        int best = -1;
        int bestSpace = -1;
        for (int dir = 0; dir < 4; dir++) {
            int space = dirSpace(grid, node, dir, 1);
            if (space <= bestSpace) {
                continue;
            }
            int face = DX[dir] != 0 ? node.h : node.w;
            for (int along = 0; along < face; along++) {
                int x = outsideX(node, dir, along);
                int y = outsideY(node, dir, along);
                if (!grid.inBounds(x, y) || grid.get(x, y) != Tile.WALL) {
                    continue;
                }
                int ix = x - DX[dir];
                int iy = y - DY[dir];
                if (!node.contains(ix, iy)) {
                    continue;
                }
                int inwardX = x - DX[dir];
                int inwardY = y - DY[dir];
                if (!node.contains(inwardX, inwardY) || !grid.get(inwardX, inwardY).isOpen()) {
                    continue;
                }
                int ox = x + DX[dir];
                int oy = y + DY[dir];
                Tile outside = grid.inBounds(ox, oy) ? grid.get(ox, oy) : Tile.ROCK;
                if (!outside.isOpen() && outside != Tile.ROCK && outside != Tile.WALL) {
                    continue;
                }
                grid.set(x, y, secret ? Tile.SECRET_DOOR : Tile.DOOR);
                if (!secret && grid.inBounds(ox, oy) && grid.get(ox, oy) == Tile.ROCK) {
                    grid.set(ox, oy, Tile.GROUND);
                } else if (!secret && grid.inBounds(ox, oy) && grid.get(ox, oy) == Tile.WALL
                        && !besideFloor(grid, ox, oy) && !touchesInterior(nodes, ox, oy)) {
                    grid.set(ox, oy, Tile.GROUND);
                }
                if (spans(grid, x, y)) {
                    return grid.index(x, y);
                }
                grid.set(x, y, Tile.WALL);
                if (grid.inBounds(ox, oy) && grid.get(ox, oy) == Tile.GROUND) {
                    grid.set(ox, oy, outside);
                }
            }
        }
        return best;
    }

    private static void addLoops(LayoutConfig config, TileGrid grid, SeededRandom rng, List<Node> nodes, Plot plot) {
        int wanted = config.loopCount > 0 ? config.loopCount : (int) (live(nodes) * config.loopRatio);
        int added = 0;
        List<int[]> pairs = new ArrayList<>();
        for (int i = 0; i < nodes.size(); i++) {
            for (int j = i + 1; j < nodes.size(); j++) {
                Node a = nodes.get(i);
                Node b = nodes.get(j);
                if (a.removed || b.removed || joined(a, b)) {
                    continue;
                }
                int dist = Math.abs((a.x + a.w / 2) - (b.x + b.w / 2)) + Math.abs((a.y + a.h / 2) - (b.y + b.h / 2));
                if (dist > config.loopMaxDistance) {
                    continue;
                }
                int hops = hops(nodes, a, b);
                if (hops < config.minLoopDistance) {
                    continue;
                }
                pairs.add(new int[] {dist, hops, i, j});
            }
        }
        pairs.sort((p, q) -> {
            int byDist = Integer.compare(p[0], q[0]);
            if (byDist != 0) {
                return byDist;
            }
            return Integer.compare(q[1], p[1]);
        });
        for (int[] pair : pairs) {
            if (added >= wanted) {
                break;
            }
            Node a = nodes.get(pair[2]);
            Node b = nodes.get(pair[3]);
            boolean locked = false;
            if (cutShared(grid, a, b, locked) || digTunnel(grid, a, b, config.rockBorder, 6, plot)) {
                a.links.add(b);
                b.links.add(a);
                added++;
            }
        }
    }

    private static boolean cutShared(TileGrid grid, Node a, Node b, boolean locked) {
        for (int y = 1; y < grid.height - 1; y++) {
            for (int x = 1; x < grid.width - 1; x++) {
                if (grid.get(x, y) != Tile.WALL) {
                    continue;
                }
                boolean touchA = touches(a, x, y);
                boolean touchB = touches(b, x, y);
                if (touchA && touchB && straight(a, b, x, y)) {
                    grid.set(x, y, locked ? Tile.LOCKED_DOOR : Tile.DOOR);
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean straight(Node a, Node b, int x, int y) {
        boolean ns = (a.contains(x, y - 1) && b.contains(x, y + 1))
                || (b.contains(x, y - 1) && a.contains(x, y + 1));
        boolean ew = (a.contains(x - 1, y) && b.contains(x + 1, y))
                || (b.contains(x - 1, y) && a.contains(x + 1, y));
        return ns || ew;
    }

    private static boolean touches(Node node, int x, int y) {
        return node.contains(x - 1, y) || node.contains(x + 1, y)
                || node.contains(x, y - 1) || node.contains(x, y + 1);
    }

    private static void connectRegions(TileGrid grid, int border, Plot plot) {
        for (int pass = 0; pass < 8; pass++) {
            int[] comp = components(grid);
            int rooms = distinctRooms(grid, comp);
            if (rooms <= 1) {
                return;
            }
            if (punchDoor(grid, comp, border)) {
                continue;
            }
            if (!digBetween(grid, comp, border, plot)) {
                return;
            }
        }
    }

    private static int[] components(TileGrid grid) {
        int[] comp = new int[grid.width * grid.height];
        for (int i = 0; i < comp.length; i++) {
            comp[i] = -1;
        }
        int id = 0;
        int[] queue = new int[comp.length];
        for (int start = 0; start < comp.length; start++) {
            if (comp[start] >= 0 || !grid.get(start).isOpen()) {
                continue;
            }
            int head = 0;
            int tail = 0;
            comp[start] = id;
            queue[tail++] = start;
            while (head < tail) {
                int cell = queue[head++];
                int x = grid.x(cell);
                int y = grid.y(cell);
                for (int dir = 0; dir < 4; dir++) {
                    int nx = x + DX[dir];
                    int ny = y + DY[dir];
                    if (!grid.inBounds(nx, ny)) {
                        continue;
                    }
                    int next = grid.index(nx, ny);
                    if (comp[next] >= 0 || !grid.get(next).isOpen()) {
                        continue;
                    }
                    comp[next] = id;
                    queue[tail++] = next;
                }
            }
            id++;
        }
        return comp;
    }

    private static int distinctRooms(TileGrid grid, int[] comp) {
        boolean[] seen = new boolean[comp.length];
        int count = 0;
        for (int i = 0; i < comp.length; i++) {
            if (grid.get(i) == Tile.ROOM_FLOOR && comp[i] >= 0 && !seen[comp[i]]) {
                seen[comp[i]] = true;
                count++;
            }
        }
        return count;
    }

    private static boolean punchDoor(TileGrid grid, int[] comp, int border) {
        for (int y = border; y < grid.height - border; y++) {
            for (int x = border; x < grid.width - border; x++) {
                if (grid.get(x, y) != Tile.WALL) {
                    continue;
                }
                int north = comp[grid.index(x, y - 1)];
                int south = comp[grid.index(x, y + 1)];
                int west = comp[grid.index(x - 1, y)];
                int east = comp[grid.index(x + 1, y)];
                if (north >= 0 && south >= 0 && north != south) {
                    grid.set(x, y, Tile.DOOR);
                    return true;
                }
                if (west >= 0 && east >= 0 && west != east) {
                    grid.set(x, y, Tile.DOOR);
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean digBetween(TileGrid grid, int[] comp, int border, Plot plot) {
        int from = -1;
        int target = -1;
        for (int i = 0; i < comp.length; i++) {
            if (grid.get(i) != Tile.ROOM_FLOOR || comp[i] < 0) {
                continue;
            }
            if (from < 0) {
                from = comp[i];
            } else if (comp[i] != from) {
                target = comp[i];
                break;
            }
        }
        if (from < 0 || target < 0) {
            return false;
        }
        boolean[] tried = new boolean[comp.length];
        tried[from] = true;
        while (target >= 0) {
            if (digOne(grid, comp, border, plot, from, target)) {
                return true;
            }
            tried[target] = true;
            target = -1;
            for (int i = 0; i < comp.length; i++) {
                if (grid.get(i) == Tile.ROOM_FLOOR && comp[i] >= 0 && !tried[comp[i]]) {
                    target = comp[i];
                    break;
                }
            }
        }
        return false;
    }

    private static boolean digOne(TileGrid grid, int[] comp, int border, Plot plot, int from, int target) {
        int[] prev = new int[comp.length];
        for (int i = 0; i < prev.length; i++) {
            prev[i] = -2;
        }
        int[] queue = new int[comp.length];
        int head = 0;
        int tail = 0;
        for (int i = 0; i < comp.length; i++) {
            if (comp[i] == from) {
                prev[i] = -1;
                queue[tail++] = i;
            }
        }
        int hit = -1;
        while (head < tail) {
            int cell = queue[head++];
            int x = grid.x(cell);
            int y = grid.y(cell);
            for (int dir = 0; dir < 4; dir++) {
                int nx = x + DX[dir];
                int ny = y + DY[dir];
                if (nx < border || ny < border || nx >= grid.width - border || ny >= grid.height - border) {
                    continue;
                }
                int next = grid.index(nx, ny);
                if (prev[next] != -2) {
                    continue;
                }
                Tile tile = grid.get(next);
                if (comp[next] == target && aligned(grid, prev, cell, nx, ny)) {
                    hit = cell;
                    break;
                }
                if (plot.keep[next] || (tile != Tile.ROCK && tile != Tile.WALL)) {
                    continue;
                }
                prev[next] = cell;
                queue[tail++] = next;
            }
            if (hit >= 0) {
                break;
            }
        }
        if (hit < 0) {
            return false;
        }
        int guard = 0;
        int limit = grid.width + grid.height;
        while (hit >= 0 && guard++ < limit) {
            Tile tile = grid.get(hit);
            if (tile != Tile.ROCK && tile != Tile.WALL) {
                break;
            }
            int x = grid.x(hit);
            int y = grid.y(hit);
            grid.set(hit, tile == Tile.WALL || besideFloor(grid, x, y) ? Tile.DOOR : Tile.CORRIDOR_FLOOR);
            hit = prev[hit];
        }
        return guard > 0;
    }

    private static boolean digTunnel(TileGrid grid, Node a, Node b, int border, int max, Plot plot) {
        int[] prev = new int[grid.width * grid.height];
        for (int i = 0; i < prev.length; i++) {
            prev[i] = -2;
        }
        int[] queue = new int[prev.length];
        int head = 0;
        int tail = 0;
        for (int[] part : a.parts) {
            for (int y = part[1]; y < part[1] + part[3]; y++) {
                for (int x = part[0]; x < part[0] + part[2]; x++) {
                    int index = grid.index(x, y);
                    prev[index] = -1;
                    queue[tail++] = index;
                }
            }
        }
        int hit = -1;
        while (head < tail) {
            int cell = queue[head++];
            int x = grid.x(cell);
            int y = grid.y(cell);
            for (int dir = 0; dir < 4; dir++) {
                int nx = x + DX[dir];
                int ny = y + DY[dir];
                if (nx < border || ny < border || nx >= grid.width - border || ny >= grid.height - border) {
                    continue;
                }
                int next = grid.index(nx, ny);
                if (prev[next] != -2) {
                    continue;
                }
                if (b.contains(nx, ny)) {
                    hit = cell;
                    break;
                }
                Tile tile = grid.get(next);
                if (plot.keep[next] || plot.courtyard[next]
                        || (tile != Tile.ROCK && tile != Tile.GROUND) || besideFloor(grid, nx, ny)) {
                    continue;
                }
                prev[next] = cell;
                queue[tail++] = next;
            }
            if (hit >= 0) {
                break;
            }
        }
        if (hit < 0) {
            return false;
        }
        int length = 0;
        int cell = hit;
        int[] path = new int[max + 2];
        while (cell >= 0 && !a.contains(grid.x(cell), grid.y(cell)) && length <= max) {
            path[length++] = cell;
            cell = prev[cell];
        }
        if (length == 0 || length > max) {
            return false;
        }
        grid.set(path[0], Tile.DOOR);
        for (int i = 1; i < length; i++) {
            if (grid.get(path[i]) == Tile.WALL && i == length - 1) {
                grid.set(path[i], Tile.DOOR);
            } else if (grid.get(path[i]) != Tile.ROOM_FLOOR) {
                grid.set(path[i], i == length - 1 ? Tile.DOOR : Tile.CORRIDOR_FLOOR);
            }
        }
        return true;
    }

    private static void dropDeadEnds(LayoutConfig config, TileGrid grid, SeededRandom rng, List<Node> nodes, Plot plot) {
        List<Node> leaves = leaves(nodes);
        shuffle(leaves, rng);
        int keep = (int) (leaves.size() * config.deadEndKeep);
        int remove = leaves.size() - keep;
        int floorRooms = config.width >= 56 && config.height >= 56 ? 25 : 4;
        int area = grid.width * grid.height;
        for (int i = 0; i < remove; i++) {
            if (live(nodes) <= floorRooms) {
                break;
            }
            if (config.floorRatio > 0 && coverage(grid) <= config.floorRatio - 0.03) {
                break;
            }
            Node leaf = leaves.get(i);
            if (config.floorRatio > 0) {
                double after = (coverage(grid) * area - openLoss(grid, nodes, leaf, plot)) / area;
                if (after <= config.floorRatio - 0.03) {
                    continue;
                }
            }
            erase(grid, nodes, leaf, plot);
        }
    }

    /** Open tiles an erase would turn into rock or wall. Ground left beside a keep still counts as open. */
    private static int openLoss(TileGrid grid, List<Node> nodes, Node node, Plot plot) {
        int loss = 0;
        for (int[] part : node.parts) {
            for (int y = part[1]; y < part[1] + part[3]; y++) {
                for (int x = part[0]; x < part[0] + part[2]; x++) {
                    if (grid.get(x, y).isOpen() && !erasedTile(grid, plot, x, y).isOpen()) {
                        loss++;
                    }
                }
            }
        }
        for (int y = node.y - 1; y <= node.y + node.h; y++) {
            for (int x = node.x - 1; x <= node.x + node.w; x++) {
                if (!grid.inBounds(x, y) || node.contains(x, y)) {
                    continue;
                }
                Tile tile = grid.get(x, y);
                if ((tile != Tile.WALL && !tile.isDoor()) || otherRoom(nodes, node, x, y)) {
                    continue;
                }
                if (tile.isOpen() && !erasedTile(grid, plot, x, y).isOpen()) {
                    loss++;
                }
            }
        }
        return loss;
    }

    private static void erase(TileGrid grid, List<Node> nodes, Node node, Plot plot) {
        node.removed = true;
        for (int[] part : node.parts) {
            for (int y = part[1]; y < part[1] + part[3]; y++) {
                for (int x = part[0]; x < part[0] + part[2]; x++) {
                    grid.set(x, y, erasedTile(grid, plot, x, y));
                }
            }
        }
        for (int y = node.y - 1; y <= node.y + node.h; y++) {
            for (int x = node.x - 1; x <= node.x + node.w; x++) {
                if (!grid.inBounds(x, y)) {
                    continue;
                }
                Tile tile = grid.get(x, y);
                if (tile != Tile.WALL && !tile.isDoor()) {
                    continue;
                }
                if (otherRoom(nodes, node, x, y)) {
                    if (tile.isDoor()) {
                        grid.set(x, y, Tile.WALL);
                    }
                    continue;
                }
                grid.set(x, y, erasedTile(grid, plot, x, y));
            }
        }
        for (Node other : node.links) {
            other.links.remove(node);
        }
        node.links.clear();
    }

    private static boolean otherRoom(List<Node> nodes, Node self, int x, int y) {
        for (Node node : nodes) {
            if (node == self || node.removed) {
                continue;
            }
            if (node.contains(x, y) || touches(node, x, y)) {
                return true;
            }
        }
        return false;
    }

    private static void markSecrets(LayoutConfig config, TileGrid grid, SeededRandom rng, List<Node> nodes) {
        List<Node> leaves = leaves(nodes);
        shuffle(leaves, rng);
        int wanted = (int) (live(nodes) * config.secretRoomChance);
        int marked = 0;
        for (Node leaf : leaves) {
            if (marked >= wanted) {
                break;
            }
            if (leaf.door < 0 || grid.get(leaf.door) != Tile.DOOR) {
                continue;
            }
            leaf.secret = true;
            grid.set(leaf.door, Tile.SECRET_DOOR);
            marked++;
        }
    }

    private static Tile erasedTile(TileGrid grid, Plot plot, int x, int y) {
        int index = grid.index(x, y);
        if (plot.keep[index] || plot.courtyard[index] || besideKeep(grid, plot, x, y)) {
            return Tile.GROUND;
        }
        return Tile.ROCK;
    }

    private static boolean besideKeep(TileGrid grid, Plot plot, int x, int y) {
        for (int dir = 0; dir < 4; dir++) {
            int nx = x + DX[dir];
            int ny = y + DY[dir];
            if (grid.inBounds(nx, ny) && plot.keep[grid.index(nx, ny)]) {
                return true;
            }
        }
        return false;
    }

    private static void decorate(LayoutConfig config, TileGrid grid, SeededRandom rng, List<Node> nodes, Plot plot) {
        for (Node node : nodes) {
            if (node.removed) {
                continue;
            }
            boolean roomy = node.w >= 8 && node.h >= 8;
            if (roomy && rng.nextDouble() < config.pillarChance) {
                pillars(grid, node);
            }
            if (roomy && rng.nextDouble() < config.partitionChance) {
                partition(grid, rng, node);
            }
            if (rng.nextDouble() < config.alcoveChance) {
                int count = 1 + rng.nextInt(2);
                for (int i = 0; i < count; i++) {
                    alcove(config, grid, node, plot);
                }
            }
            if (rng.nextDouble() < config.hazardChance) {
                hazard(grid, rng, node);
            }
        }
    }

    private static void pillars(TileGrid grid, Node node) {
        if (node.w < 8 || node.h < 8) {
            return;
        }
        for (int[] part : node.parts) {
            if (part[2] < 8 || part[3] < 8) {
                continue;
            }
            for (int y = part[1] + 2; y < part[1] + part[3] - 2; y += 3) {
                for (int x = part[0] + 2; x < part[0] + part[2] - 2; x += 3) {
                    if (x < part[0] + 3 && y < part[1] + 3) {
                        continue;
                    }
                    if (grid.get(x, y) == Tile.ROOM_FLOOR) {
                        grid.set(x, y, Tile.WALL);
                    }
                }
            }
        }
    }

    private static void partition(TileGrid grid, SeededRandom rng, Node node) {
        int[] part = node.parts[0];
        if (part[2] < 8 || part[3] < 8) {
            return;
        }
        boolean vertical = rng.nextInt(2) == 0;
        int gap = rng.nextInt(vertical ? part[3] - 2 : part[2] - 2) + 1;
        if (vertical) {
            int x = part[0] + part[2] / 2;
            for (int y = part[1] + 1; y < part[1] + part[3] - 1; y++) {
                grid.set(x, y, y == part[1] + gap ? Tile.DOOR : Tile.WALL);
            }
        } else {
            int y = part[1] + part[3] / 2;
            for (int x = part[0] + 1; x < part[0] + part[2] - 1; x++) {
                grid.set(x, y, x == part[0] + gap ? Tile.DOOR : Tile.WALL);
            }
        }
    }

    private static void alcove(LayoutConfig config, TileGrid grid, Node node, Plot plot) {
        for (int[] part : node.parts) {
            for (int y = part[1]; y < part[1] + part[3]; y++) {
                for (int x = part[0]; x < part[0] + part[2]; x++) {
                    for (int dir = 0; dir < 4; dir++) {
                        int wx = x + DX[dir];
                        int wy = y + DY[dir];
                        if (!grid.inBounds(wx, wy) || grid.get(wx, wy) != Tile.WALL || node.contains(wx, wy)) {
                            continue;
                        }
                        int ox = wx + DX[dir];
                        int oy = wy + DY[dir];
                        if (!inside(config, grid, ox, oy) || node.contains(ox, oy)) {
                            continue;
                        }
                        int outside = grid.index(ox, oy);
                        if (plot.keep[outside] || plot.courtyard[outside]
                                || (grid.get(ox, oy) != Tile.ROCK && grid.get(ox, oy) != Tile.GROUND)) {
                            continue;
                        }
                        boolean wrapped = true;
                        for (int side = 0; side < 4; side++) {
                            int sx = wx + DX[side];
                            int sy = wy + DY[side];
                            if (node.contains(sx, sy)) {
                                continue;
                            }
                            if (!grid.inBounds(sx, sy)) {
                                wrapped = false;
                                break;
                            }
                            Tile around = grid.get(sx, sy);
                            if (around == Tile.ROCK || around == Tile.GROUND) {
                                grid.set(sx, sy, Tile.WALL);
                            } else if (around != Tile.WALL && !around.isDoor()) {
                                wrapped = false;
                                break;
                            }
                        }
                        if (!wrapped) {
                            grid.set(wx, wy, Tile.WALL);
                            return;
                        }
                        grid.set(wx, wy, Tile.ROOM_FLOOR);
                        node.addPart(wx, wy, 1, 1);
                        return;
                    }
                }
            }
        }
    }

    private static void hazard(TileGrid grid, SeededRandom rng, Node node) {
        Tile tile = switch (rng.nextInt(3)) {
            case 0 -> Tile.WATER;
            case 1 -> Tile.PIT;
            default -> Tile.LAVA;
        };
        int[] part = node.parts[rng.nextInt(node.parts.length)];
        if (part[2] < 5 || part[3] < 5) {
            return;
        }
        int x = part[0] + 2 + rng.nextInt(Math.max(1, part[2] - 4));
        int y = part[1] + 2 + rng.nextInt(Math.max(1, part[3] - 4));
        if (x < part[0] + 3 && y < part[1] + 3) {
            return;
        }
        for (int dy = 0; dy < 2; dy++) {
            for (int dx = 0; dx < 2; dx++) {
                if (node.contains(x + dx, y + dy) && grid.get(x + dx, y + dy) == Tile.ROOM_FLOOR) {
                    grid.set(x + dx, y + dy, tile);
                }
            }
        }
    }

    /** Every door keeps a walkable tile on both sides, and each open room can be entered. */
    private static void repairDoors(TileGrid grid, List<Node> nodes, Plot plot) {
        sealRings(grid, nodes, plot);
        for (int y = 1; y < grid.height - 1; y++) {
            for (int x = 1; x < grid.width - 1; x++) {
                if (!grid.get(x, y).isDoor()) {
                    continue;
                }
                if (spans(grid, x, y)) {
                    continue;
                }
                clearSide(grid, nodes, plot, x, y - 1);
                clearSide(grid, nodes, plot, x, y + 1);
                if (!spans(grid, x, y)) {
                    clearSide(grid, nodes, plot, x - 1, y);
                    clearSide(grid, nodes, plot, x + 1, y);
                }
                if (!spans(grid, x, y)) {
                    openFacing(grid, nodes, plot, x, y);
                }
                if (!spans(grid, x, y)) {
                    grid.set(x, y, Tile.WALL);
                    for (Node node : nodes) {
                        if (node.door == grid.index(x, y)) {
                            node.door = -1;
                        }
                    }
                }
            }
        }
        for (Node node : nodes) {
            if (node.removed || node.door >= 0) {
                continue;
            }
            node.door = cutOutsideDoor(grid, nodes, node, node.secret);
        }
        for (Node node : nodes) {
            if (node.removed || node.secret || node.door < 0) {
                continue;
            }
            int x = grid.x(node.door);
            int y = grid.y(node.door);
            for (int dir = 0; dir < 4; dir++) {
                int nx = x + DX[dir];
                int ny = y + DY[dir];
                if (!node.contains(nx, ny)) {
                    continue;
                }
                int guard = 0;
                while (guard++ < 8 && grid.inBounds(nx, ny) && node.contains(nx, ny)
                        && !grid.get(nx, ny).isOpen()) {
                    grid.set(nx, ny, Tile.ROOM_FLOOR);
                    nx += DX[dir];
                    ny += DY[dir];
                }
            }
        }
    }

    /** A room's floor only meets a wall or a door. */
    private static void sealRings(TileGrid grid, List<Node> nodes, Plot plot) {
        for (Node node : nodes) {
            if (node.removed) {
                continue;
            }
            for (int[] part : node.parts) {
                for (int y = part[1]; y < part[1] + part[3]; y++) {
                    for (int x = part[0]; x < part[0] + part[2]; x++) {
                        if (!grid.get(x, y).isOpen()) {
                            continue;
                        }
                        for (int dir = 0; dir < 4; dir++) {
                            int nx = x + DX[dir];
                            int ny = y + DY[dir];
                            if (!grid.inBounds(nx, ny) || node.contains(nx, ny) || owned(nodes, nx, ny)) {
                                continue;
                            }
                            Tile outside = grid.get(nx, ny);
                            if (outside == Tile.WALL || outside.isDoor() || plot.keep[grid.index(nx, ny)]
                                    || plot.courtyard[grid.index(nx, ny)]) {
                                continue;
                            }
                            grid.set(nx, ny, Tile.WALL);
                        }
                    }
                }
            }
        }
    }

    private static boolean owned(List<Node> nodes, int x, int y) {
        for (Node node : nodes) {
            if (!node.removed && node.contains(x, y)) {
                return true;
            }
        }
        return false;
    }

    private static boolean touchesInterior(List<Node> nodes, int x, int y) {
        for (int dir = 0; dir < 4; dir++) {
            int nx = x + DX[dir];
            int ny = y + DY[dir];
            for (Node node : nodes) {
                if (!node.removed && node.contains(nx, ny)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean besideFloor(TileGrid grid, int x, int y) {
        for (int dir = 0; dir < 4; dir++) {
            int nx = x + DX[dir];
            int ny = y + DY[dir];
            if (grid.inBounds(nx, ny) && grid.get(nx, ny) == Tile.ROOM_FLOOR) {
                return true;
            }
        }
        return false;
    }

    private static void closeBentDoors(TileGrid grid, List<Node> nodes, Plot plot) {
        for (int y = 1; y < grid.height - 1; y++) {
            for (int x = 1; x < grid.width - 1; x++) {
                if (grid.get(x, y).isDoor()) {
                    slideDoorOut(grid, nodes, plot, x, y);
                }
            }
        }
        for (int y = 1; y < grid.height - 1; y++) {
            for (int x = 1; x < grid.width - 1; x++) {
                if (!grid.get(x, y).isDoor() || doorOk(grid, x, y)) {
                    continue;
                }
                widenAroundDoor(grid, plot, x, y);
                forceSpan(grid, plot, x, y);
                if (!doorOk(grid, x, y)) {
                    boolean spanned = openCell(grid, x - 1, y) && openCell(grid, x + 1, y)
                            || openCell(grid, x, y - 1) && openCell(grid, x, y + 1);
                    if (spanned && !besideFloor(grid, x, y)) {
                        grid.set(x, y, Tile.CORRIDOR_FLOOR);
                    } else {
                        grid.set(x, y, Tile.WALL);
                    }
                }
            }
        }
    }

    /** Both sides are walkable, and at least one of them has two walkable neighbors. */
    private static boolean doorOk(TileGrid grid, int x, int y) {
        return axisOk(grid, x - 1, y, x + 1, y) || axisOk(grid, x, y - 1, x, y + 1);
    }

    private static boolean axisOk(TileGrid grid, int ax, int ay, int bx, int by) {
        if (!openCell(grid, ax, ay) || !openCell(grid, bx, by)) {
            return false;
        }
        return openNeighbors(grid, ax, ay) >= 2 || openNeighbors(grid, bx, by) >= 2;
    }

    private static boolean openCell(TileGrid grid, int x, int y) {
        return grid.inBounds(x, y) && grid.get(x, y).isOpen();
    }

    private static boolean touchesRoom(TileGrid grid, int x, int y) {
        for (int dir = 0; dir < 4; dir++) {
            int nx = x + DX[dir];
            int ny = y + DY[dir];
            if (grid.inBounds(nx, ny) && grid.get(nx, ny) == Tile.ROOM_FLOOR) {
                return true;
            }
        }
        return false;
    }

    private static int openNeighbors(TileGrid grid, int x, int y) {
        int count = 0;
        for (int dir = 0; dir < 4; dir++) {
            if (openCell(grid, x + DX[dir], y + DY[dir])) {
                count++;
            }
        }
        return count;
    }

    /** Moves a door that sits on a room floor out onto the wall in front of it. */
    private static void slideDoorOut(TileGrid grid, List<Node> nodes, Plot plot, int x, int y) {
        if (!owned(nodes, x, y)) {
            return;
        }
        Tile door = grid.get(x, y);
        for (int dir = 0; dir < 4; dir++) {
            int floorX = x + DX[dir];
            int floorY = y + DY[dir];
            int wallX = x - DX[dir];
            int wallY = y - DY[dir];
            int beyondX = wallX - DX[dir];
            int beyondY = wallY - DY[dir];
            if (!grid.inBounds(floorX, floorY) || !grid.inBounds(wallX, wallY) || !grid.inBounds(beyondX, beyondY)) {
                continue;
            }
            if (grid.get(floorX, floorY) != Tile.ROOM_FLOOR || plot.keep[grid.index(wallX, wallY)]) {
                continue;
            }
            Tile shell = grid.get(wallX, wallY);
            if (shell != Tile.WALL && shell != Tile.GROUND && shell != Tile.CORRIDOR_FLOOR) {
                continue;
            }
            if (!grid.get(beyondX, beyondY).isOpen()) {
                continue;
            }
            grid.set(x, y, Tile.ROOM_FLOOR);
            grid.set(wallX, wallY, door);
            if (doorOk(grid, wallX, wallY)) {
                return;
            }
            grid.set(wallX, wallY, Tile.WALL);
            grid.set(x, y, Tile.ROOM_FLOOR);
            return;
        }
    }

    /** Opens the wall across from a walkable side so the door lines up. */
    private static void forceSpan(TileGrid grid, Plot plot, int x, int y) {
        int[][] axes = {{-1, 0, 1, 0}, {0, -1, 0, 1}};
        for (int[] axis : axes) {
            int ax = x + axis[0];
            int ay = y + axis[1];
            int bx = x + axis[2];
            int by = y + axis[3];
            if (openCell(grid, ax, ay) && !openCell(grid, bx, by)) {
                openAcross(grid, plot, x, y, bx, by);
            } else if (openCell(grid, bx, by) && !openCell(grid, ax, ay)) {
                openAcross(grid, plot, x, y, ax, ay);
            }
            if (doorOk(grid, x, y)) {
                return;
            }
        }
    }

    private static void openAcross(TileGrid grid, Plot plot, int doorX, int doorY, int x, int y) {
        if (!grid.inBounds(x, y) || plot.keep[grid.index(x, y)]) {
            return;
        }
        Tile tile = grid.get(x, y);
        if (tile != Tile.ROCK && tile != Tile.WALL) {
            return;
        }
        for (int dir = 0; dir < 4; dir++) {
            int nx = x + DX[dir];
            int ny = y + DY[dir];
            if (nx == doorX && ny == doorY) {
                continue;
            }
            if (!grid.inBounds(nx, ny)) {
                continue;
            }
            Tile beside = grid.get(nx, ny);
            if (beside == Tile.ROOM_FLOOR || beside.isDoor() && touchesRoom(grid, nx, ny)) {
                return;
            }
        }
        grid.set(x, y, Tile.GROUND);
        if (openNeighbors(grid, x, y) >= 2) {
            return;
        }
        for (int dir = 0; dir < 4; dir++) {
            int nx = x + DX[dir];
            int ny = y + DY[dir];
            if ((nx == doorX && ny == doorY) || !grid.inBounds(nx, ny) || plot.keep[grid.index(nx, ny)]) {
                continue;
            }
            if (grid.get(nx, ny) == Tile.ROCK && !touchesRoom(grid, nx, ny)) {
                grid.set(nx, ny, Tile.GROUND);
                return;
            }
        }
    }

    private static void widenAroundDoor(TileGrid grid, Plot plot, int x, int y) {
        int[][] sides = {{x - 1, y}, {x + 1, y}, {x, y - 1}, {x, y + 1}};
        for (int[] side : sides) {
            if (!openCell(grid, side[0], side[1]) || openNeighbors(grid, side[0], side[1]) >= 2) {
                continue;
            }
            for (int dir = 0; dir < 4; dir++) {
                int nx = side[0] + DX[dir];
                int ny = side[1] + DY[dir];
                if ((nx == x && ny == y) || !grid.inBounds(nx, ny)) {
                    continue;
                }
                int index = grid.index(nx, ny);
                if (plot.keep[index] || grid.get(index) != Tile.ROCK || touchesRoom(grid, nx, ny)) {
                    continue;
                }
                grid.set(index, grid.get(side[0], side[1]) == Tile.GROUND ? Tile.GROUND : Tile.CORRIDOR_FLOOR);
                if (openNeighbors(grid, side[0], side[1]) >= 2) {
                    return;
                }
            }
        }
    }

    private static boolean aligned(TileGrid grid, int[] prev, int cell, int tx, int ty) {
        int from = prev[cell];
        if (from < 0) {
            return false;
        }
        int x = grid.x(cell);
        int y = grid.y(cell);
        return grid.x(from) - x == x - tx && grid.y(from) - y == y - ty;
    }

    private static boolean spans(TileGrid grid, int x, int y) {
        return grid.get(x, y - 1).isOpen() && grid.get(x, y + 1).isOpen()
                || grid.get(x - 1, y).isOpen() && grid.get(x + 1, y).isOpen();
    }

    private static void clearSide(TileGrid grid, List<Node> nodes, Plot plot, int x, int y) {
        if (!grid.inBounds(x, y) || grid.get(x, y).isOpen() || plot.keep[grid.index(x, y)]) {
            return;
        }
        Tile tile = grid.get(x, y);
        if (tile != Tile.WALL && tile != Tile.ROCK) {
            return;
        }
        if (tile == Tile.WALL) {
            for (Node node : nodes) {
                if (!node.removed && node.contains(x, y)) {
                    grid.set(x, y, Tile.ROOM_FLOOR);
                    return;
                }
            }
            return;
        }
        grid.set(x, y, Tile.GROUND);
    }

    private static void openFacing(TileGrid grid, List<Node> nodes, Plot plot, int x, int y) {
        int[][] axis = {{0, -1, 0, 1}, {-1, 0, 1, 0}};
        for (int[] side : axis) {
            int ax = x + side[0];
            int ay = y + side[1];
            int bx = x + side[2];
            int by = y + side[3];
            if (!grid.inBounds(ax, ay) || !grid.inBounds(bx, by)) {
                continue;
            }
            boolean aOpen = grid.get(ax, ay).isOpen();
            boolean bOpen = grid.get(bx, by).isOpen();
            if (aOpen && !bOpen && canOpen(grid, nodes, plot, bx, by)) {
                forceOpen(grid, nodes, bx, by);
                return;
            }
            if (bOpen && !aOpen && canOpen(grid, nodes, plot, ax, ay)) {
                forceOpen(grid, nodes, ax, ay);
                return;
            }
        }
    }

    private static boolean canOpen(TileGrid grid, List<Node> nodes, Plot plot, int x, int y) {
        if (plot.keep[grid.index(x, y)]) {
            return false;
        }
        Tile tile = grid.get(x, y);
        if (tile != Tile.WALL && tile != Tile.ROCK) {
            return false;
        }
        for (Node node : nodes) {
            if (!node.removed && node.contains(x, y)) {
                return true;
            }
        }
        return !besideFloor(grid, x, y) && !touchesInterior(nodes, x, y);
    }

    private static void forceOpen(TileGrid grid, List<Node> nodes, int x, int y) {
        for (Node node : nodes) {
            if (!node.removed && node.contains(x, y)) {
                grid.set(x, y, Tile.ROOM_FLOOR);
                return;
            }
        }
        grid.set(x, y, Tile.GROUND);
    }

    private static boolean isRoomDoor(List<Node> nodes, int index) {
        for (Node node : nodes) {
            if (!node.removed && node.door == index) {
                return true;
            }
        }
        return false;
    }

    private static List<Room> toRooms(List<Node> nodes) {
        List<Room> rooms = new ArrayList<>();
        for (Node node : nodes) {
            if (node.removed) {
                continue;
            }
            rooms.add(new Room(rooms.size(), node.parts, node.secret, false));
        }
        return rooms;
    }

    private static Node pickParent(LayoutConfig config, TileGrid grid, List<Node> nodes, SeededRandom rng, Plot plot) {
        int[] weight = new int[nodes.size()];
        int total = 0;
        for (int i = 0; i < nodes.size(); i++) {
            Node node = nodes.get(i);
            if (node.removed) {
                continue;
            }
            int kids = children(nodes, node);
            int cluster = kids >= 5 ? 1 : 6 - kids;
            weight[i] = cluster * (1 + outwardSpace(config, grid, node, plot) / 4);
            total += weight[i];
        }
        if (total <= 0) {
            return nodes.get(rng.nextInt(nodes.size()));
        }
        return nodes.get(weighted(weight, total, rng));
    }

    private static int children(List<Node> nodes, Node parent) {
        int kids = 0;
        for (Node node : nodes) {
            if (!node.removed && node.parent == parent) {
                kids++;
            }
        }
        return kids;
    }

    private static int pickDir(LayoutConfig config, TileGrid grid, Node node, SeededRandom rng, Plot plot) {
        int[] weight = new int[4];
        int total = 0;
        for (int dir = 0; dir < 4; dir++) {
            weight[dir] = dirSpace(grid, node, dir, config.rockBorder, plot);
            total += weight[dir];
        }
        if (total <= 0) {
            return -1;
        }
        return weighted(weight, total, rng);
    }

    private static int weighted(int[] weight, int total, SeededRandom rng) {
        int roll = rng.nextInt(Math.max(1, total));
        int cursor = 0;
        for (int i = 0; i < weight.length; i++) {
            cursor += weight[i];
            if (roll < cursor) {
                return i;
            }
        }
        return weight.length - 1;
    }

    private static int outwardSpace(LayoutConfig config, TileGrid grid, Node node, Plot plot) {
        int space = 0;
        for (int dir = 0; dir < 4; dir++) {
            space += dirSpace(grid, node, dir, config.rockBorder, plot);
        }
        return space;
    }

    private static int dirSpace(TileGrid grid, Node node, int dir, int border) {
        return dirSpace(grid, node, dir, border, null);
    }

    private static int dirSpace(TileGrid grid, Node node, int dir, int border, Plot plot) {
        int face = DX[dir] != 0 ? node.h : node.w;
        int space = 0;
        int stepAlong = Math.max(1, face / 4);
        for (int along = 0; along < face; along += stepAlong) {
            int x = outsideX(node, dir, along);
            int y = outsideY(node, dir, along);
            for (int step = 1; step <= 6; step++) {
                int cx = x + DX[dir] * step;
                int cy = y + DY[dir] * step;
                if (cx < border || cy < border || cx >= grid.width - border || cy >= grid.height - border) {
                    break;
                }
                int index = grid.index(cx, cy);
                if (plot != null && (plot.keep[index] || plot.courtyard[index])) {
                    break;
                }
                Tile tile = grid.get(cx, cy);
                if (tile != Tile.ROCK) {
                    break;
                }
                space++;
            }
        }
        return space;
    }

    private static int outsideX(Node node, int dir, int along) {
        if (DX[dir] > 0) {
            return node.x + node.w;
        }
        if (DX[dir] < 0) {
            return node.x - 1;
        }
        return node.x + Math.min(along, Math.max(0, node.w - 1));
    }

    private static int outsideY(Node node, int dir, int along) {
        if (DY[dir] > 0) {
            return node.y + node.h;
        }
        if (DY[dir] < 0) {
            return node.y - 1;
        }
        return node.y + Math.min(along, Math.max(0, node.h - 1));
    }

    private static double coverage(TileGrid grid) {
        int open = 0;
        int area = grid.width * grid.height;
        for (int i = 0; i < area; i++) {
            if (grid.get(i).isOpen()) {
                open++;
            }
        }
        return open / (double) area;
    }

    private static List<Node> leaves(List<Node> nodes) {
        List<Node> leaves = new ArrayList<>();
        for (Node node : nodes) {
            if (!node.removed && node.parent != null && degree(node, nodes) == 1) {
                leaves.add(node);
            }
        }
        return leaves;
    }

    private static int degree(Node node, List<Node> nodes) {
        int count = node.links.size();
        if (node.parent != null && !node.parent.removed) {
            count++;
        }
        for (Node other : nodes) {
            if (!other.removed && other.parent == node) {
                count++;
            }
        }
        return count;
    }

    private static boolean joined(Node a, Node b) {
        return a.parent == b || b.parent == a || a.links.contains(b);
    }

    private static int hops(List<Node> nodes, Node start, Node goal) {
        int[] dist = new int[nodes.size()];
        for (int i = 0; i < dist.length; i++) {
            dist[i] = -1;
        }
        int startIndex = nodes.indexOf(start);
        int[] queue = new int[nodes.size()];
        int head = 0;
        int tail = 0;
        dist[startIndex] = 0;
        queue[tail++] = startIndex;
        while (head < tail) {
            int current = queue[head++];
            if (nodes.get(current) == goal) {
                return dist[current];
            }
            for (Node next : neighbors(nodes, nodes.get(current))) {
                int index = nodes.indexOf(next);
                if (dist[index] < 0) {
                    dist[index] = dist[current] + 1;
                    queue[tail++] = index;
                }
            }
        }
        return -1;
    }

    private static List<Node> neighbors(List<Node> nodes, Node node) {
        List<Node> next = new ArrayList<>(node.links);
        if (node.parent != null && !node.parent.removed) {
            next.add(node.parent);
        }
        for (Node other : nodes) {
            if (!other.removed && other.parent == node) {
                next.add(other);
            }
        }
        return next;
    }

    private static void shuffle(List<Node> nodes, SeededRandom rng) {
        for (int i = nodes.size() - 1; i > 0; i--) {
            int j = rng.nextInt(i + 1);
            Node swap = nodes.get(i);
            nodes.set(i, nodes.get(j));
            nodes.set(j, swap);
        }
    }

    private static int live(List<Node> nodes) {
        int count = 0;
        for (Node node : nodes) {
            if (!node.removed) {
                count++;
            }
        }
        return count;
    }

    private static int[][] normalize(int[][] parts) {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        for (int[] part : parts) {
            minX = Math.min(minX, part[0]);
            minY = Math.min(minY, part[1]);
        }
        return translate(parts, -minX, -minY);
    }

    private static int[][] translate(int[][] parts, int dx, int dy) {
        int[][] moved = new int[parts.length][4];
        for (int i = 0; i < parts.length; i++) {
            moved[i][0] = parts[i][0] + dx;
            moved[i][1] = parts[i][1] + dy;
            moved[i][2] = parts[i][2];
            moved[i][3] = parts[i][3];
        }
        return moved;
    }

    private static int[] bounds(int[][] parts) {
        int maxX = 0;
        int maxY = 0;
        for (int[] part : parts) {
            maxX = Math.max(maxX, part[0] + part[2]);
            maxY = Math.max(maxY, part[1] + part[3]);
        }
        return new int[] {0, 0, maxX, maxY};
    }

    static final class Node {
        int[][] parts;
        final int x;
        final int y;
        final int w;
        final int h;
        Node parent;
        int door = -1;
        boolean secret;
        boolean removed;
        final List<Node> links = new ArrayList<>();

        Node(int[][] parts) {
            this.parts = new int[parts.length][4];
            int minX = Integer.MAX_VALUE;
            int minY = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE;
            int maxY = Integer.MIN_VALUE;
            for (int i = 0; i < parts.length; i++) {
                this.parts[i] = parts[i].clone();
                minX = Math.min(minX, parts[i][0]);
                minY = Math.min(minY, parts[i][1]);
                maxX = Math.max(maxX, parts[i][0] + parts[i][2]);
                maxY = Math.max(maxY, parts[i][1] + parts[i][3]);
            }
            this.x = minX;
            this.y = minY;
            this.w = maxX - minX;
            this.h = maxY - minY;
        }

        boolean contains(int cellX, int cellY) {
            for (int[] part : parts) {
                if (cellX >= part[0] && cellY >= part[1]
                        && cellX < part[0] + part[2] && cellY < part[1] + part[3]) {
                    return true;
                }
            }
            return false;
        }

        void addPart(int px, int py, int pw, int ph) {
            int[][] next = new int[parts.length + 1][4];
            for (int i = 0; i < parts.length; i++) {
                next[i] = parts[i];
            }
            next[parts.length] = new int[] {px, py, pw, ph};
            parts = next;
        }
    }

    /** If a room sits against a courtyard, one door opens onto that ground. */
    private static void doorOntoCourtyard(TileGrid grid, Node node) {
        if (node.door >= 0) {
            int x = grid.x(node.door);
            int y = grid.y(node.door);
            for (int dir = 0; dir < 4; dir++) {
                int nx = x + DX[dir];
                int ny = y + DY[dir];
                if (grid.inBounds(nx, ny) && grid.get(nx, ny) == Tile.GROUND) {
                    return;
                }
            }
        }
        for (int[] part : node.parts) {
            for (int y = part[1]; y < part[1] + part[3]; y++) {
                for (int x = part[0]; x < part[0] + part[2]; x++) {
                    if (!grid.get(x, y).isOpen()) {
                        continue;
                    }
                    for (int dir = 0; dir < 4; dir++) {
                        int wx = x + DX[dir];
                        int wy = y + DY[dir];
                        int ox = wx + DX[dir];
                        int oy = wy + DY[dir];
                        if (!grid.inBounds(wx, wy) || !grid.inBounds(ox, oy) || node.contains(wx, wy)) {
                            continue;
                        }
                        if (grid.get(wx, wy) == Tile.WALL && grid.get(ox, oy) == Tile.GROUND) {
                            grid.set(wx, wy, Tile.DOOR);
                            return;
                        }
                    }
                }
            }
        }
    }

    /**
     * Joins stray ground to the room network with a short tunnel, or turns that ground back into rock.
     */
    private static void linkOrDropGround(TileGrid grid, List<Node> nodes, Plot plot) {
        int origin = stairSite(grid, nodes);
        for (Node node : nodes) {
            if (origin >= 0) {
                break;
            }
            if (node.removed || node.secret) {
                continue;
            }
            for (int[] part : node.parts) {
                for (int y = part[1]; y < part[1] + part[3] && origin < 0; y++) {
                    for (int x = part[0]; x < part[0] + part[2]; x++) {
                        if (grid.get(x, y).isOpen()) {
                            origin = grid.index(x, y);
                            break;
                        }
                    }
                }
            }
            if (origin >= 0) {
                break;
            }
        }
        if (origin < 0) {
            reclaimAllGround(grid, plot);
            return;
        }
        for (int pass = 0; pass < 8; pass++) {
            boolean[] reached = floodOpen(grid, origin);
            int stray = -1;
            for (int i = 0; i < reached.length; i++) {
                if (grid.get(i) == Tile.GROUND && !reached[i]) {
                    stray = i;
                    break;
                }
            }
            if (stray < 0) {
                return;
            }
            if (!digToReached(grid, plot, stray, reached, 16)) {
                reclaimGround(grid, plot, stray);
            }
        }
        boolean[] reached = floodOpen(grid, origin);
        for (int i = 0; i < reached.length; i++) {
            if (grid.get(i) == Tile.GROUND && !reached[i]) {
                reclaimGround(grid, plot, i);
            }
        }
    }

    /** Same spot the up stairs will use: first open room with a 3×3 floor. */
    private static int stairSite(TileGrid grid, List<Node> nodes) {
        for (Node node : nodes) {
            if (node.removed || node.secret || node.w < 3 || node.h < 3) {
                continue;
            }
            for (int y = node.y + 1; y < node.y + node.h - 1; y++) {
                for (int x = node.x + 1; x < node.x + node.w - 1; x++) {
                    if (!node.contains(x, y)) {
                        continue;
                    }
                    boolean open = true;
                    for (int dy = -1; dy <= 1 && open; dy++) {
                        for (int dx = -1; dx <= 1; dx++) {
                            int nx = x + dx;
                            int ny = y + dy;
                            if (!node.contains(nx, ny) || !grid.get(nx, ny).isOpen()) {
                                open = false;
                                break;
                            }
                        }
                    }
                    if (open) {
                        return grid.index(x, y);
                    }
                }
            }
        }
        return -1;
    }

    private static boolean[] floodOpen(TileGrid grid, int origin) {
        boolean[] reached = new boolean[grid.width * grid.height];
        int[] queue = new int[reached.length];
        int head = 0;
        int tail = 0;
        reached[origin] = true;
        queue[tail++] = origin;
        while (head < tail) {
            int cell = queue[head++];
            int x = grid.x(cell);
            int y = grid.y(cell);
            for (int dir = 0; dir < 4; dir++) {
                int nx = x + DX[dir];
                int ny = y + DY[dir];
                if (!grid.inBounds(nx, ny)) {
                    continue;
                }
                int next = grid.index(nx, ny);
                if (reached[next] || !grid.get(next).isOpen()) {
                    continue;
                }
                reached[next] = true;
                queue[tail++] = next;
            }
        }
        return reached;
    }

    private static boolean digToReached(TileGrid grid, Plot plot, int stray, boolean[] reached, int max) {
        int[] prev = new int[reached.length];
        int[] dist = new int[reached.length];
        for (int i = 0; i < prev.length; i++) {
            prev[i] = -2;
        }
        int[] queue = new int[prev.length];
        int head = 0;
        int tail = 0;
        int[] stack = new int[prev.length];
        int stackN = 0;
        stack[stackN++] = stray;
        while (stackN > 0) {
            int cell = stack[--stackN];
            if (prev[cell] != -2) {
                continue;
            }
            prev[cell] = -1;
            dist[cell] = 0;
            queue[tail++] = cell;
            int x = grid.x(cell);
            int y = grid.y(cell);
            for (int dir = 0; dir < 4; dir++) {
                int nx = x + DX[dir];
                int ny = y + DY[dir];
                if (!grid.inBounds(nx, ny)) {
                    continue;
                }
                int next = grid.index(nx, ny);
                if (grid.get(next) == Tile.GROUND) {
                    stack[stackN++] = next;
                }
            }
        }
        int hit = -1;
        int exit = -1;
        while (head < tail && hit < 0) {
            int cell = queue[head++];
            if (dist[cell] >= max) {
                continue;
            }
            int x = grid.x(cell);
            int y = grid.y(cell);
            for (int dir = 0; dir < 4; dir++) {
                int nx = x + DX[dir];
                int ny = y + DY[dir];
                if (!grid.inBounds(nx, ny)) {
                    continue;
                }
                int next = grid.index(nx, ny);
                if (prev[next] != -2) {
                    continue;
                }
                if (reached[next] && grid.get(next).isOpen() && aligned(grid, prev, cell, nx, ny)
                        && tunnelFits(grid, prev, cell, next)) {
                    hit = cell;
                    exit = next;
                    break;
                }
                Tile tile = grid.get(next);
                if (plot.keep[next] || plot.courtyard[next] || (tile != Tile.ROCK && tile != Tile.WALL)) {
                    continue;
                }
                prev[next] = cell;
                dist[next] = dist[cell] + 1;
                queue[tail++] = next;
            }
        }
        if (hit < 0) {
            return false;
        }
        int forward = exit;
        int guard = 0;
        while (hit >= 0 && guard++ < max + 2) {
            Tile tile = grid.get(hit);
            if (tile != Tile.ROCK && tile != Tile.WALL) {
                break;
            }
            int before = prev[hit];
            grid.set(hit, entrance(grid, hit, before, forward) ? Tile.DOOR : Tile.CORRIDOR_FLOOR);
            forward = hit;
            hit = before;
        }
        return guard > 0;
    }

    /** A wall or a cell beside a room becomes a door only when the tunnel runs straight through it. */
    private static boolean tunnelFits(TileGrid grid, int[] prev, int cell, int forward) {
        int guard = 0;
        while (cell >= 0 && guard++ < 64) {
            Tile tile = grid.get(cell);
            if (tile != Tile.ROCK && tile != Tile.WALL) {
                return true;
            }
            int before = prev[cell];
            if (before < 0) {
                return false;
            }
            if ((tile == Tile.WALL || besideFloor(grid, grid.x(cell), grid.y(cell)))
                    && !straight(grid, before, cell, forward)) {
                return false;
            }
            forward = cell;
            cell = before;
        }
        return false;
    }

    private static boolean entrance(TileGrid grid, int cell, int before, int after) {
        Tile tile = grid.get(cell);
        if (tile != Tile.WALL && !besideFloor(grid, grid.x(cell), grid.y(cell))) {
            return false;
        }
        return straight(grid, before, cell, after);
    }

    private static boolean straight(TileGrid grid, int a, int b, int c) {
        if (a < 0 || c < 0) {
            return false;
        }
        int bx = grid.x(b);
        int by = grid.y(b);
        return grid.x(a) - bx == bx - grid.x(c) && grid.y(a) - by == by - grid.y(c);
    }

    private static void reclaimGround(TileGrid grid, Plot plot, int start) {
        int[] stack = new int[grid.width * grid.height];
        boolean[] seen = new boolean[stack.length];
        int n = 0;
        stack[n++] = start;
        seen[start] = true;
        while (n > 0) {
            int cell = stack[--n];
            grid.set(cell, Tile.ROCK);
            plot.courtyard[cell] = false;
            int x = grid.x(cell);
            int y = grid.y(cell);
            for (int dir = 0; dir < 4; dir++) {
                int nx = x + DX[dir];
                int ny = y + DY[dir];
                if (!grid.inBounds(nx, ny)) {
                    continue;
                }
                int next = grid.index(nx, ny);
                if (!seen[next] && grid.get(next) == Tile.GROUND) {
                    seen[next] = true;
                    stack[n++] = next;
                }
            }
        }
    }

    private static void reclaimAllGround(TileGrid grid, Plot plot) {
        for (int i = 0; i < plot.courtyard.length; i++) {
            if (grid.get(i) == Tile.GROUND) {
                grid.set(i, Tile.ROCK);
                plot.courtyard[i] = false;
            }
        }
    }

    /** A few pillars or rubble piles inside a courtyard, skipped when they would split the ground. */
    private static void dressCourtyard(TileGrid grid, SeededRandom rng, Plot plot, int x0, int y0, int w, int h) {
        int features = rng.nextInt(5);
        for (int n = 0; n < features; n++) {
            int placed = 0;
            for (int attempt = 0; attempt < 8 && placed == 0; attempt++) {
                int x = x0 + 2 + rng.nextInt(Math.max(1, w - 4));
                int y = y0 + 2 + rng.nextInt(Math.max(1, h - 4));
                if (!interiorGround(grid, plot, x, y)) {
                    continue;
                }
                boolean pillar = rng.nextInt(2) == 0;
                int x2 = x + (rng.nextInt(2) == 0 ? 1 : 0);
                int y2 = y + (x2 == x ? 1 : 0);
                boolean pair = !pillar && interiorGround(grid, plot, x2, y2);
                int before = yardPiece(grid, plot, x, y);
                grid.set(x, y, pillar ? Tile.WALL : Tile.ROCK);
                if (pair) {
                    grid.set(x2, y2, Tile.ROCK);
                }
                int after = yardPiece(grid, plot, x, y);
                int removed = 1 + (pair ? 1 : 0);
                if (after < 8 || after != before - removed) {
                    grid.set(x, y, Tile.GROUND);
                    if (pair) {
                        grid.set(x2, y2, Tile.GROUND);
                    }
                } else {
                    placed = 1;
                }
            }
        }
    }

    private static boolean interiorGround(TileGrid grid, Plot plot, int x, int y) {
        if (!grid.inBounds(x, y) || !plot.courtyard[grid.index(x, y)] || grid.get(x, y) != Tile.GROUND) {
            return false;
        }
        for (int dir = 0; dir < 4; dir++) {
            int nx = x + DX[dir];
            int ny = y + DY[dir];
            if (!grid.inBounds(nx, ny) || grid.get(nx, ny) != Tile.GROUND) {
                return false;
            }
        }
        return true;
    }

    private static int yardPiece(TileGrid grid, Plot plot, int sx, int sy) {
        int start = -1;
        for (int i = 0; i < plot.courtyard.length && start < 0; i++) {
            if (plot.courtyard[i] && grid.get(i) == Tile.GROUND) {
                if (Math.abs(grid.x(i) - sx) + Math.abs(grid.y(i) - sy) <= 3) {
                    start = i;
                }
            }
        }
        if (start < 0) {
            return 0;
        }
        boolean[] seen = new boolean[plot.courtyard.length];
        int[] queue = new int[seen.length];
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
                int nx = x + DX[dir];
                int ny = y + DY[dir];
                if (!grid.inBounds(nx, ny)) {
                    continue;
                }
                int next = grid.index(nx, ny);
                if (seen[next] || !plot.courtyard[next] || grid.get(next) != Tile.GROUND) {
                    continue;
                }
                seen[next] = true;
                queue[tail++] = next;
            }
        }
        return count;
    }

    /** Yards a few tiles apart become one plaza through a 2- or 3-tile opening. */
    private static void joinNearbyYards(TileGrid grid, Plot plot, SeededRandom rng) {
        if (plot.regionCount < 2) {
            return;
        }
        int[] label = new int[plot.courtyard.length];
        for (int i = 0; i < label.length; i++) {
            label[i] = -1;
        }
        int components = labelYard(grid, plot, label);
        if (components < 2) {
            return;
        }
        List<Integer>[] cells = groups(components);
        for (int i = 0; i < label.length; i++) {
            if (label[i] >= 0 && grid.get(i) == Tile.GROUND) {
                cells[label[i]].add(i);
            }
        }
        for (int a = 0; a < components; a++) {
            for (int b = a + 1; b < components; b++) {
                int ax = 0;
                int ay = 0;
                int bx = 0;
                int by = 0;
                int best = 5;
                for (int left : cells[a]) {
                    int x = grid.x(left);
                    int y = grid.y(left);
                    for (int right : cells[b]) {
                        int gap = Math.max(Math.abs(x - grid.x(right)), Math.abs(y - grid.y(right))) - 1;
                        if (gap >= 1 && gap < best) {
                            best = gap;
                            ax = x;
                            ay = y;
                            bx = grid.x(right);
                            by = grid.y(right);
                        }
                    }
                }
                if (best <= 3) {
                    carvePlaza(grid, plot, ax, ay, bx, by, 2 + rng.nextInt(2));
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Integer>[] groups(int components) {
        List<Integer>[] cells = new List[components];
        for (int i = 0; i < components; i++) {
            cells[i] = new ArrayList<>();
        }
        return cells;
    }

    private static int labelYard(TileGrid grid, Plot plot, int[] label) {
        int id = 0;
        int[] queue = new int[label.length];
        for (int i = 0; i < label.length; i++) {
            if (label[i] >= 0 || !plot.courtyard[i] || grid.get(i) != Tile.GROUND) {
                continue;
            }
            int head = 0;
            int tail = 0;
            label[i] = id;
            queue[tail++] = i;
            while (head < tail) {
                int cell = queue[head++];
                int x = grid.x(cell);
                int y = grid.y(cell);
                for (int dir = 0; dir < 4; dir++) {
                    int nx = x + DX[dir];
                    int ny = y + DY[dir];
                    if (!grid.inBounds(nx, ny)) {
                        continue;
                    }
                    int next = grid.index(nx, ny);
                    if (label[next] >= 0 || !plot.courtyard[next] || grid.get(next) != Tile.GROUND) {
                        continue;
                    }
                    label[next] = id;
                    queue[tail++] = next;
                }
            }
            id++;
        }
        return id;
    }

    private static void carvePlaza(TileGrid grid, Plot plot, int x0, int y0, int x1, int y1, int wide) {
        int x = x0;
        int y = y0;
        int guard = 0;
        while ((x != x1 || y != y1) && guard++ < grid.width + grid.height) {
            if (x != x1) {
                x += Integer.compare(x1, x);
            } else {
                y += Integer.compare(y1, y);
            }
            for (int t = 0; t < wide; t++) {
                int px = x;
                int py = y + t;
                if (y0 == y1) {
                    px = x;
                    py = y + t - wide / 2;
                } else if (x0 == x1) {
                    px = x + t - wide / 2;
                    py = y;
                }
                if (!grid.inBounds(px, py)) {
                    continue;
                }
                int index = grid.index(px, py);
                if (plot.keep[index] || grid.get(index) != Tile.ROCK) {
                    continue;
                }
                grid.set(index, Tile.GROUND);
                plot.courtyard[index] = true;
            }
        }
    }

    /**
     * Gives each courtyard two to four openings, drops halls that die in rock,
     * and closes doors that face a wall or a one-tile pinch.
     */
    private static void polish(LayoutConfig config, TileGrid grid, List<Node> nodes, Plot plot, SeededRandom rng) {
        ensureOpenings(config, grid, nodes, plot);
        finishDeadCorridors(config, grid, nodes, plot, rng);
        closeBentDoors(grid, nodes, plot);
        connectRegions(grid, config.rockBorder, plot);
        linkOrDropGround(grid, nodes, plot);
        finishDeadCorridors(config, grid, nodes, plot, rng);
        closeBentDoors(grid, nodes, plot);
        linkOrDropGround(grid, nodes, plot);
        ensureOpenings(config, grid, nodes, plot);
        closeBentDoors(grid, nodes, plot);
        finishDeadCorridors(config, grid, nodes, plot, rng);
        closeBentDoors(grid, nodes, plot);
        connectRegions(grid, config.rockBorder, plot);
        linkOrDropGround(grid, nodes, plot);
        closeBentDoors(grid, nodes, plot);
        linkOrDropGround(grid, nodes, plot);
        closeBentDoors(grid, nodes, plot);
        sealRoomEdges(grid, nodes);
    }

    /** Room interiors stay floor, and they only meet a wall or a door. */
    private static void sealRoomEdges(TileGrid grid, List<Node> nodes) {
        for (Node node : nodes) {
            if (node.removed) {
                continue;
            }
            for (int[] part : node.parts) {
                for (int y = part[1]; y < part[1] + part[3]; y++) {
                    for (int x = part[0]; x < part[0] + part[2]; x++) {
                        Tile tile = grid.get(x, y);
                        if (tile == Tile.GROUND || tile == Tile.CORRIDOR_FLOOR) {
                            grid.set(x, y, Tile.ROOM_FLOOR);
                        }
                        if (!grid.get(x, y).isOpen()) {
                            continue;
                        }
                        for (int dir = 0; dir < 4; dir++) {
                            int nx = x + DX[dir];
                            int ny = y + DY[dir];
                            if (!grid.inBounds(nx, ny) || node.contains(nx, ny) || owned(nodes, nx, ny)) {
                                continue;
                            }
                            Tile outside = grid.get(nx, ny);
                            if (outside == Tile.WALL || outside.isDoor()) {
                                continue;
                            }
                            if (outside.isOpen()) {
                                grid.set(nx, ny, Tile.DOOR);
                                if (!doorOk(grid, nx, ny)) {
                                    grid.set(nx, ny, Tile.WALL);
                                }
                            } else {
                                grid.set(nx, ny, Tile.WALL);
                            }
                        }
                    }
                }
            }
        }
        for (int y = 1; y < grid.height - 1; y++) {
            for (int x = 1; x < grid.width - 1; x++) {
                if (grid.get(x, y).isDoor() && !doorOk(grid, x, y)) {
                    grid.set(x, y, Tile.WALL);
                }
            }
        }
    }

    private static void ensureOpenings(LayoutConfig config, TileGrid grid, List<Node> nodes, Plot plot) {
        int area = grid.width * grid.height;
        int[] label = new int[area];
        for (int i = 0; i < area; i++) {
            label[i] = -1;
        }
        int[] queue = new int[area];
        int id = 0;
        for (int i = 0; i < area; i++) {
            if (label[i] >= 0 || grid.get(i) != Tile.GROUND) {
                continue;
            }
            int head = 0;
            int tail = 0;
            label[i] = id;
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
                    int nx = x + DX[dir];
                    int ny = y + DY[dir];
                    if (!grid.inBounds(nx, ny)) {
                        continue;
                    }
                    int next = grid.index(nx, ny);
                    if (label[next] >= 0 || grid.get(next) != Tile.GROUND) {
                        continue;
                    }
                    label[next] = id;
                    queue[tail++] = next;
                }
            }
            if (size >= 12 && maxX - minX >= 3 && maxY - minY >= 3) {
                balanceOpenings(config, grid, nodes, plot, label, id);
            }
            id++;
        }
    }

    private static void balanceOpenings(LayoutConfig config, TileGrid grid, List<Node> nodes, Plot plot,
            int[] label, int id) {
        for (int pass = 0; pass < 6; pass++) {
            boolean[] edge = new boolean[4];
            int openings = countOpenings(grid, label, id, edge);
            int edges = 0;
            for (boolean used : edge) {
                if (used) {
                    edges++;
                }
            }
            if (openings >= 2 && openings <= 4 && edges >= 2) {
                return;
            }
            if (openings > 4) {
                if (!closeExtraOpening(grid, label, id)) {
                    return;
                }
                continue;
            }
            boolean added = false;
            for (int dir = 0; dir < 4 && !added; dir++) {
                if (edge[dir] && edges >= 2 && openings >= 2) {
                    continue;
                }
                added = addOpening(config, grid, nodes, plot, label, id, dir);
            }
            if (!added) {
                return;
            }
        }
    }

    private static int countOpenings(TileGrid grid, int[] label, int id, boolean[] edge) {
        int area = label.length;
        boolean[] touch = new boolean[area];
        boolean[] seen = new boolean[area];
        for (int i = 0; i < area; i++) {
            if (label[i] != id) {
                continue;
            }
            int x = grid.x(i);
            int y = grid.y(i);
            for (int dir = 0; dir < 4; dir++) {
                int nx = x + DX[dir];
                int ny = y + DY[dir];
                if (!grid.inBounds(nx, ny)) {
                    continue;
                }
                int next = grid.index(nx, ny);
                if (opensOnto(grid.get(next))) {
                    touch[next] = true;
                }
            }
        }
        int openings = 0;
        int[] queue = new int[area];
        for (int i = 0; i < area; i++) {
            if (!touch[i] || seen[i]) {
                continue;
            }
            if (grid.get(i) == Tile.DOOR) {
                seen[i] = true;
                openings++;
                markEdge(grid, label, id, i, edge);
                continue;
            }
            int head = 0;
            int tail = 0;
            seen[i] = true;
            queue[tail++] = i;
            markEdge(grid, label, id, i, edge);
            while (head < tail) {
                int cell = queue[head++];
                int x = grid.x(cell);
                int y = grid.y(cell);
                for (int dir = 0; dir < 4; dir++) {
                    int nx = x + DX[dir];
                    int ny = y + DY[dir];
                    if (!grid.inBounds(nx, ny)) {
                        continue;
                    }
                    int next = grid.index(nx, ny);
                    if (!seen[next] && touch[next] && grid.get(next) != Tile.DOOR) {
                        seen[next] = true;
                        queue[tail++] = next;
                    }
                }
            }
            openings++;
        }
        return openings;
    }

    private static void markEdge(TileGrid grid, int[] label, int id, int cell, boolean[] edge) {
        int x = grid.x(cell);
        int y = grid.y(cell);
        for (int dir = 0; dir < 4; dir++) {
            int nx = x + DX[dir];
            int ny = y + DY[dir];
            if (grid.inBounds(nx, ny) && label[grid.index(nx, ny)] == id) {
                edge[(dir + 2) % 4] = true;
            }
        }
    }

    private static boolean opensOnto(Tile tile) {
        return tile == Tile.DOOR || tile == Tile.ROOM_FLOOR || tile == Tile.CORRIDOR_FLOOR
                || tile == Tile.STAIRS_UP || tile == Tile.STAIRS_DOWN;
    }

    private static boolean closeExtraOpening(TileGrid grid, int[] label, int id) {
        for (int i = 0; i < label.length; i++) {
            if (label[i] != id || grid.get(i) != Tile.GROUND) {
                continue;
            }
            int x = grid.x(i);
            int y = grid.y(i);
            for (int dir = 0; dir < 4; dir++) {
                int nx = x + DX[dir];
                int ny = y + DY[dir];
                if (!grid.inBounds(nx, ny) || grid.get(nx, ny) != Tile.DOOR) {
                    continue;
                }
                grid.set(nx, ny, Tile.WALL);
                return true;
            }
        }
        return false;
    }

    private static boolean addOpening(LayoutConfig config, TileGrid grid, List<Node> nodes, Plot plot,
            int[] label, int id, int dir) {
        int roomCap = grid.width >= 56 && grid.height >= 56 ? 35 : config.roomCount + 6;
        for (int i = 0; i < label.length; i++) {
            if (label[i] != id) {
                continue;
            }
            int x = grid.x(i);
            int y = grid.y(i);
            int wx = x + DX[dir];
            int wy = y + DY[dir];
            if (!grid.inBounds(wx, wy)) {
                continue;
            }
            Tile wall = grid.get(wx, wy);
            if (wall == Tile.WALL) {
                int fx = wx + DX[dir];
                int fy = wy + DY[dir];
                if (openCell(grid, fx, fy) && !plot.keep[grid.index(wx, wy)]) {
                    grid.set(wx, wy, Tile.DOOR);
                    if (doorOk(grid, wx, wy)) {
                        return true;
                    }
                    grid.set(wx, wy, Tile.WALL);
                }
            }
            if ((wall == Tile.ROCK || wall == Tile.WALL) && live(nodes) < roomCap
                    && paintCloset(grid, nodes, plot, wx, wy, dir)) {
                return true;
            }
        }
        return false;
    }

    private static boolean finishDeadCorridors(LayoutConfig config, TileGrid grid, List<Node> nodes, Plot plot,
            SeededRandom rng) {
        boolean erased = false;
        int roomCap = grid.width >= 56 && grid.height >= 56 ? 35 : config.roomCount + 6;
        for (int guard = 0; guard < grid.width * grid.height; guard++) {
            int end = corridorDeadEnd(grid);
            if (end < 0) {
                return erased;
            }
            int back = -1;
            int x = grid.x(end);
            int y = grid.y(end);
            for (int dir = 0; dir < 4; dir++) {
                int nx = x + DX[dir];
                int ny = y + DY[dir];
                if (openCell(grid, nx, ny)) {
                    back = dir;
                }
            }
            int forward = back < 0 ? 0 : (back + 2) % 4;
            if (rng.nextDouble() < config.deadEndClosetChance && live(nodes) < roomCap
                    && paintCloset(grid, nodes, plot, x, y, forward)) {
                continue;
            }
            eraseStub(grid, end);
            erased = true;
        }
        return erased;
    }

    private static int corridorDeadEnd(TileGrid grid) {
        for (int i = 0; i < grid.width * grid.height; i++) {
            if (grid.get(i) != Tile.CORRIDOR_FLOOR) {
                continue;
            }
            if (openNeighbors(grid, grid.x(i), grid.y(i)) < 2) {
                return i;
            }
        }
        return -1;
    }

    private static void eraseStub(TileGrid grid, int end) {
        int cell = end;
        int from = -1;
        for (int guard = 0; guard < grid.width + grid.height; guard++) {
            if (grid.get(cell) != Tile.CORRIDOR_FLOOR) {
                return;
            }
            int open = 0;
            int next = -1;
            int x = grid.x(cell);
            int y = grid.y(cell);
            for (int dir = 0; dir < 4; dir++) {
                int nx = x + DX[dir];
                int ny = y + DY[dir];
                if (!openCell(grid, nx, ny)) {
                    continue;
                }
                open++;
                int index = grid.index(nx, ny);
                if (index != from && grid.get(index) == Tile.CORRIDOR_FLOOR) {
                    next = index;
                } else if (index != from && next < 0) {
                    next = index;
                }
            }
            if (open >= 3) {
                return;
            }
            grid.set(cell, Tile.ROCK);
            if (next < 0 || grid.get(next) != Tile.CORRIDOR_FLOOR) {
                return;
            }
            from = cell;
            cell = next;
        }
    }

    /** A 2×2 room just past a door cell. The door faces {@code dir}. */
    private static boolean paintCloset(TileGrid grid, List<Node> nodes, Plot plot, int doorX, int doorY, int dir) {
        if (!grid.inBounds(doorX, doorY) || plot.keep[grid.index(doorX, doorY)]) {
            return false;
        }
        Tile doorTile = grid.get(doorX, doorY);
        if (doorTile != Tile.ROCK && doorTile != Tile.WALL && doorTile != Tile.CORRIDOR_FLOOR) {
            return false;
        }
        int backX = doorX - DX[dir];
        int backY = doorY - DY[dir];
        if (!openCell(grid, backX, backY)) {
            return false;
        }
        for (int side = -1; side <= 1; side += 2) {
            if (commitCloset(grid, nodes, plot, doorX, doorY, dir, side, doorTile)) {
                return true;
            }
        }
        return false;
    }

    private static boolean commitCloset(TileGrid grid, List<Node> nodes, Plot plot, int doorX, int doorY, int dir,
            int side, Tile doorTile) {
        int perpX = -DY[dir] * side;
        int perpY = DX[dir] * side;
        int x0 = doorX + DX[dir];
        int y0 = doorY + DY[dir];
        int[] xs = {x0, x0 + DX[dir], x0 + perpX, x0 + DX[dir] + perpX};
        int[] ys = {y0, y0 + DY[dir], y0 + perpY, y0 + DY[dir] + perpY};
        int minX = xs[0];
        int minY = ys[0];
        for (int i = 0; i < 4; i++) {
            if (!grid.inBounds(xs[i], ys[i])) {
                return false;
            }
            int index = grid.index(xs[i], ys[i]);
            if (plot.keep[index] || plot.courtyard[index] || grid.get(index) != Tile.ROCK || owned(nodes, xs[i], ys[i])) {
                return false;
            }
            minX = Math.min(minX, xs[i]);
            minY = Math.min(minY, ys[i]);
        }
        for (int i = 0; i < 4; i++) {
            for (int step = 0; step < 4; step++) {
                int nx = xs[i] + DX[step];
                int ny = ys[i] + DY[step];
                if (closetCell(xs, ys, nx, ny) || (nx == doorX && ny == doorY)) {
                    continue;
                }
                if (!grid.inBounds(nx, ny) || plot.keep[grid.index(nx, ny)] || owned(nodes, nx, ny)) {
                    return false;
                }
                Tile around = grid.get(nx, ny);
                if (around != Tile.ROCK && around != Tile.WALL) {
                    return false;
                }
            }
        }
        int[] undo = new int[24];
        int undoN = 0;
        for (int i = 0; i < 4; i++) {
            for (int step = 0; step < 4; step++) {
                int nx = xs[i] + DX[step];
                int ny = ys[i] + DY[step];
                if (closetCell(xs, ys, nx, ny) || (nx == doorX && ny == doorY)) {
                    continue;
                }
                if (grid.get(nx, ny) == Tile.ROCK) {
                    undo[undoN++] = (grid.index(nx, ny) << 8) | Tile.ROCK.code;
                    grid.set(nx, ny, Tile.WALL);
                }
            }
        }
        for (int i = 0; i < 4; i++) {
            undo[undoN++] = (grid.index(xs[i], ys[i]) << 8) | Tile.ROCK.code;
            grid.set(xs[i], ys[i], Tile.ROOM_FLOOR);
        }
        undo[undoN++] = (grid.index(doorX, doorY) << 8) | (doorTile.code & 0xff);
        grid.set(doorX, doorY, Tile.DOOR);
        if (!doorOk(grid, doorX, doorY)) {
            for (int i = undoN - 1; i >= 0; i--) {
                grid.set(undo[i] >> 8, Tile.from((byte) (undo[i] & 0xff)));
            }
            return false;
        }
        Node closet = new Node(new int[][] {{minX, minY, 2, 2}});
        closet.door = grid.index(doorX, doorY);
        nodes.add(closet);
        return true;
    }

    private static boolean closetCell(int[] xs, int[] ys, int x, int y) {
        for (int i = 0; i < xs.length; i++) {
            if (xs[i] == x && ys[i] == y) {
                return true;
            }
        }
        return false;
    }

    /** Courtyards stay free of rooms. Keep marks rock that placement must not erase. */
    private static final class Plot {
        final boolean[] courtyard;
        final boolean[] keep;
        final int[][] regions = new int[4][4];
        int regionCount;

        Plot(int cells) {
            courtyard = new boolean[cells];
            keep = new boolean[cells];
        }
    }

    private static final class Scratch {
        private int[] cells = new int[1024];
        private int count;

        void reset() {
            count = 0;
        }

        void set(TileGrid grid, int x, int y, Tile tile) {
            if (count == cells.length) {
                int[] grown = new int[cells.length * 2];
                System.arraycopy(cells, 0, grown, 0, cells.length);
                cells = grown;
            }
            int index = grid.index(x, y);
            cells[count++] = (index << 8) | (grid.get(index).code & 0xff);
            grid.set(index, tile);
        }

        void restore(TileGrid grid) {
            for (int i = count - 1; i >= 0; i--) {
                grid.set(cells[i] >> 8, Tile.from((byte) (cells[i] & 0xff)));
            }
            count = 0;
        }
    }
}
