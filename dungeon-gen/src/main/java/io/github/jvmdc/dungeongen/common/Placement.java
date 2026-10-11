package io.github.jvmdc.dungeongen.common;

import java.util.List;

/**
 * One thing standing on a finished map: a trap, a chest, a spawn, a light, or a room marker.
 * Coordinates are tile coordinates. roomId and regionId are -1 when that side does not apply.
 * dangerTier is 0 through 3 for spawns, and -1 for everything else.
 */
public final class Placement {
    public final PlacementType type;
    public final int x;
    public final int y;
    public final Facing facing;
    public final int roomId;
    public final int regionId;
    public final int dangerTier;
    public final TrapKind trap;
    public final List<String> tags;

    public Placement(PlacementType type, int x, int y, Facing facing, int roomId, int regionId,
            int dangerTier, TrapKind trap, List<String> tags) {
        this.type = type;
        this.x = x;
        this.y = y;
        this.facing = facing == null ? Facing.NONE : facing;
        this.roomId = roomId;
        this.regionId = regionId;
        this.dangerTier = dangerTier;
        this.trap = trap;
        this.tags = tags == null || tags.isEmpty() ? List.of() : List.copyOf(tags);
    }
}
