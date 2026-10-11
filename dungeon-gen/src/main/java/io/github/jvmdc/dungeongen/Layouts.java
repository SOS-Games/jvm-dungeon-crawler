package io.github.jvmdc.dungeongen;

import io.github.jvmdc.dungeongen.accretion.AccretionGenerator;
import io.github.jvmdc.dungeongen.corridor.CorridorNetworkGenerator;

public final class Layouts {
    public static final String DEFAULT = "accretion";

    private Layouts() {
    }

    public static LayoutGenerator of(String style) {
        if (style == null) {
            throw new IllegalArgumentException("style");
        }
        return switch (style) {
            case "corridor" -> new CorridorNetworkGenerator();
            case "accretion" -> new AccretionGenerator();
            default -> throw new IllegalArgumentException("style " + style);
        };
    }
}
