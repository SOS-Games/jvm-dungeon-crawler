package io.github.jvmdc.dungeongen.common;

import io.github.jvmdc.dungeongen.LayoutConfig;

import java.util.ArrayList;
import java.util.List;

/** One finished dungeon. Rooms and the graph are fixed after generate returns. Placements are filled by the later pass. */
public final class Dungeon {
    public final long seed;
    public final LayoutConfig config;
    public final TileGrid grid;
    public final List<Room> rooms;
    public final List<Corridor> corridors;
    /** Rooms that actually share open floor. Secret doors are not walked. */
    public final RoomGraph graph;
    /** Cell index, or -1 when this layout does not place stairs. */
    public final int stairsUp;
    public final int stairsDown;
    /** Traps, chests, spawns, lights, and room markers. Filled after the rooms exist. */
    public final List<Placement> placements = new ArrayList<>();

    public Dungeon(long seed, LayoutConfig config, TileGrid grid,
            List<Room> rooms, List<Corridor> corridors, RoomGraph graph,
            int stairsUp, int stairsDown) {
        this.seed = seed;
        this.config = config;
        this.grid = grid;
        this.rooms = rooms;
        this.corridors = corridors;
        this.graph = graph;
        this.stairsUp = stairsUp;
        this.stairsDown = stairsDown;
    }
}
