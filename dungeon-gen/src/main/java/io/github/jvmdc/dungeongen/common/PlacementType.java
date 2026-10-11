package io.github.jvmdc.dungeongen.common;

/** What a placement marks. The tile underneath stays walkable except for floor traps. */
public enum PlacementType {
    TRAP,
    CHEST,
    MONSTER_SPAWN,
    ITEM_SPAWN,
    GOLD,
    LIGHT,
    DECOR,
    FOUNTAIN,
    /** Marks a whole room. The tile is just where the label sits. */
    SHOP,
    BOSS_SPAWN
}
