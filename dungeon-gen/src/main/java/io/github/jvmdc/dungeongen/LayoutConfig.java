package io.github.jvmdc.dungeongen;

import java.util.List;

/**
 * Knobs shared by every layout. A corridor network uses the room size, buffer, and loop ratio.
 * Attached rooms also use the loop distance, how many dead ends to keep, secret rooms, vaults,
 * and the optional cave pass. Unused fields are left at defaults and ignored.
 */
public final class LayoutConfig {
    public final int width;
    public final int height;
    public final int roomCount;
    public final int minRoomWidth;
    public final int maxRoomWidth;
    public final int minRoomHeight;
    public final int maxRoomHeight;
    public final int roomBuffer;
    public final double loopRatio;
    /** Shortest graph distance, in room hops, before an extra loop is allowed. */
    public final int minLoopDistance;
    /** Fraction of dead-end rooms to leave in place. 1 keeps them all. */
    public final double deadEndKeep;
    /** Chance a dead-end hall becomes a 2×2 closet instead of being filled back in. */
    public final double deadEndClosetChance;
    public final double secretRoomChance;
    public final boolean caves;
    public final int vaultCount;
    /** Stop adding attached rooms once this fraction of the map is walkable. 0 uses roomCount only. */
    public final double floorRatio;
    public final int rockBorder;
    public final int openRegionCount;
    public final int openRegionMinSize;
    public final int openRegionMaxSize;
    /** Chance that two rooms share one wall instead of a strip of open ground. */
    public final double wallSharing;
    public final int roomGapMin;
    public final int roomGapMax;
    public final int loopMaxDistance;
    /** Extra loops to attempt. 0 falls back to {@code roomCount * loopRatio}. */
    public final int loopCount;
    public final double pillarChance;
    public final double alcoveChance;
    public final double partitionChance;
    public final double hazardChance;
    public final List<FeaturePlacer> placers;

    public LayoutConfig(int width, int height, int roomCount,
            int minRoomWidth, int maxRoomWidth,
            int minRoomHeight, int maxRoomHeight,
            int roomBuffer, double loopRatio) {
        this(width, height, roomCount, minRoomWidth, maxRoomWidth, minRoomHeight, maxRoomHeight,
                roomBuffer, loopRatio, 3, 1.0, 0.0, false, 0);
    }

    public LayoutConfig(int width, int height, int roomCount,
            int minRoomWidth, int maxRoomWidth,
            int minRoomHeight, int maxRoomHeight,
            int roomBuffer, double loopRatio,
            int minLoopDistance, double deadEndKeep, double secretRoomChance,
            boolean caves, int vaultCount) {
        this(width, height, roomCount, minRoomWidth, maxRoomWidth, minRoomHeight, maxRoomHeight,
                roomBuffer, loopRatio, minLoopDistance, deadEndKeep, secretRoomChance,
                caves, vaultCount, 0.0);
    }

    public LayoutConfig(int width, int height, int roomCount,
            int minRoomWidth, int maxRoomWidth,
            int minRoomHeight, int maxRoomHeight,
            int roomBuffer, double loopRatio,
            int minLoopDistance, double deadEndKeep, double secretRoomChance,
            boolean caves, int vaultCount, double floorRatio) {
        this(width, height, roomCount, minRoomWidth, maxRoomWidth, minRoomHeight, maxRoomHeight,
                roomBuffer, loopRatio, minLoopDistance, deadEndKeep, secretRoomChance,
                caves, vaultCount, floorRatio, List.of(),
                2, 2, 8, 16, 0.5, 1, 3, 14, 4, 0.30, 0.22, 0.18, 0.12);
    }

    public LayoutConfig(int width, int height, int roomCount,
            int minRoomWidth, int maxRoomWidth,
            int minRoomHeight, int maxRoomHeight,
            int roomBuffer, double loopRatio,
            int minLoopDistance, double deadEndKeep, double secretRoomChance,
            boolean caves, int vaultCount, double floorRatio,
            int rockBorder, int openRegionCount, int openRegionMinSize, int openRegionMaxSize,
            double wallSharing, int roomGapMin, int roomGapMax, int loopMaxDistance, int loopCount,
            double pillarChance, double alcoveChance, double partitionChance, double hazardChance) {
        this(width, height, roomCount, minRoomWidth, maxRoomWidth, minRoomHeight, maxRoomHeight,
                roomBuffer, loopRatio, minLoopDistance, deadEndKeep, secretRoomChance,
                caves, vaultCount, floorRatio, List.of(),
                rockBorder, openRegionCount, openRegionMinSize, openRegionMaxSize,
                wallSharing, roomGapMin, roomGapMax, loopMaxDistance, loopCount,
                pillarChance, alcoveChance, partitionChance, hazardChance);
    }

    public LayoutConfig(int width, int height, int roomCount,
            int minRoomWidth, int maxRoomWidth,
            int minRoomHeight, int maxRoomHeight,
            int roomBuffer, double loopRatio,
            int minLoopDistance, double deadEndKeep, double secretRoomChance,
            boolean caves, int vaultCount, double floorRatio, List<FeaturePlacer> placers,
            int rockBorder, int openRegionCount, int openRegionMinSize, int openRegionMaxSize,
            double wallSharing, int roomGapMin, int roomGapMax, int loopMaxDistance, int loopCount,
            double pillarChance, double alcoveChance, double partitionChance, double hazardChance) {
        if (roomCount < 0) {
            throw new IllegalArgumentException("roomCount");
        }
        if (minRoomWidth < 1 || minRoomHeight < 1
                || maxRoomWidth < minRoomWidth || maxRoomHeight < minRoomHeight) {
            throw new IllegalArgumentException("room size");
        }
        if (roomBuffer < 1) {
            throw new IllegalArgumentException("roomBuffer");
        }
        if (Double.isNaN(loopRatio) || loopRatio < 0) {
            throw new IllegalArgumentException("loopRatio");
        }
        if (minLoopDistance < 1) {
            throw new IllegalArgumentException("minLoopDistance");
        }
        if (Double.isNaN(deadEndKeep) || deadEndKeep < 0 || deadEndKeep > 1
                || Double.isNaN(secretRoomChance) || secretRoomChance < 0 || secretRoomChance > 1) {
            throw new IllegalArgumentException("room chances");
        }
        if (vaultCount < 0) {
            throw new IllegalArgumentException("vaultCount");
        }
        if (Double.isNaN(floorRatio) || floorRatio < 0 || floorRatio > 0.8) {
            throw new IllegalArgumentException("floorRatio");
        }
        if (rockBorder < 1 || openRegionCount < 0 || openRegionMinSize < 2 || openRegionMaxSize < openRegionMinSize
                || roomGapMin < 0 || roomGapMax < roomGapMin || loopMaxDistance < 1 || loopCount < 0) {
            throw new IllegalArgumentException("open regions");
        }
        if (Double.isNaN(wallSharing) || wallSharing < 0 || wallSharing > 1
                || Double.isNaN(pillarChance) || pillarChance < 0 || pillarChance > 1
                || Double.isNaN(alcoveChance) || alcoveChance < 0 || alcoveChance > 1
                || Double.isNaN(partitionChance) || partitionChance < 0 || partitionChance > 1
                || Double.isNaN(hazardChance) || hazardChance < 0 || hazardChance > 1) {
            throw new IllegalArgumentException("feature chances");
        }
        if (width < maxRoomWidth + 2 * roomBuffer + 1 || height < maxRoomHeight + 2 * roomBuffer + 1) {
            throw new IllegalArgumentException("map is too small for the largest room and the buffer");
        }
        this.width = width;
        this.height = height;
        this.roomCount = roomCount;
        this.minRoomWidth = minRoomWidth;
        this.maxRoomWidth = maxRoomWidth;
        this.minRoomHeight = minRoomHeight;
        this.maxRoomHeight = maxRoomHeight;
        this.roomBuffer = roomBuffer;
        this.loopRatio = loopRatio;
        this.minLoopDistance = minLoopDistance;
        this.deadEndKeep = deadEndKeep;
        this.deadEndClosetChance = 0.2;
        this.secretRoomChance = secretRoomChance;
        this.caves = caves;
        this.vaultCount = vaultCount;
        this.floorRatio = floorRatio;
        this.rockBorder = rockBorder;
        this.openRegionCount = openRegionCount;
        this.openRegionMinSize = openRegionMinSize;
        this.openRegionMaxSize = openRegionMaxSize;
        this.wallSharing = wallSharing;
        this.roomGapMin = roomGapMin;
        this.roomGapMax = roomGapMax;
        this.loopMaxDistance = loopMaxDistance;
        this.loopCount = loopCount;
        this.pillarChance = pillarChance;
        this.alcoveChance = alcoveChance;
        this.partitionChance = partitionChance;
        this.hazardChance = hazardChance;
        this.placers = placers == null ? List.of() : List.copyOf(placers);
    }

    /** Corridor defaults: 50×30, 15 rooms, every room 3×3, a 5-tile gap. */
    public static LayoutConfig standard() {
        return new LayoutConfig(50, 30, 15, 3, 3, 3, 3, 5, 0.2);
    }

    /**
     * Walled rooms on a 64×64 map. Stops near 30 rooms or when about 39% of the map is walkable,
     * and never places more than 35. Two open courtyards stay clear, and pockets of rock remain.
     */
    public static LayoutConfig attached() {
        return attached(64, 64);
    }

    /** Same attached-room settings, scaled down when the map is smaller than 64×64. */
    public static LayoutConfig attached(int width, int height) {
        int side = Math.min(width, height);
        int maxRoom = Math.max(4, Math.min(12, side - 6));
        int minRoom = Math.min(3, maxRoom);
        boolean full = width >= 56 && height >= 56;
        int cap = full ? 35 : Math.max(8, Math.min(24, width * height / 70));
        int openMin = full ? 12 : Math.max(5, side / 6);
        int openMax = full ? 18 : Math.max(openMin, Math.min(side / 3, 10));
        double floor = full ? 0.39 : 0.0;
        int loops = full ? 4 : 2;
        int loopDist = full ? 4 : 3;
        return new LayoutConfig(width, height, cap, minRoom, maxRoom, minRoom, maxRoom,
                1, 0.25, loopDist, 0.30, 0.10, false, 2, floor,
                2, 2, openMin, openMax, 0.30, 1, 3, 14, loops,
                0.40, 0.20, 0.25, 0.12);
    }
}
