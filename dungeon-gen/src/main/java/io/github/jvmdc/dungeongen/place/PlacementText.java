package io.github.jvmdc.dungeongen.place;

import io.github.jvmdc.dungeongen.common.Dungeon;
import io.github.jvmdc.dungeongen.common.Placement;

/** One line per placement, in the order the pass stored them. */
public final class PlacementText {
    private PlacementText() {
    }

    public static String render(Dungeon dungeon) {
        StringBuilder out = new StringBuilder();
        out.append("placements seed ").append(dungeon.seed).append('\n');
        for (Placement placement : dungeon.placements) {
            out.append(placement.type);
            out.append(' ').append(placement.x).append(',').append(placement.y);
            out.append(' ').append(placement.facing);
            out.append(" room=").append(placement.roomId);
            out.append(" region=").append(placement.regionId);
            out.append(" tier=").append(placement.dangerTier);
            out.append(" trap=").append(placement.trap == null ? "-" : placement.trap);
            if (!placement.tags.isEmpty()) {
                out.append(" tags=");
                for (int i = 0; i < placement.tags.size(); i++) {
                    if (i > 0) {
                        out.append(',');
                    }
                    out.append(placement.tags.get(i));
                }
            }
            out.append('\n');
        }
        return out.toString();
    }
}
