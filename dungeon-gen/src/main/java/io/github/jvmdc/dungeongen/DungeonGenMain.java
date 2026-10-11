package io.github.jvmdc.dungeongen;

import io.github.jvmdc.dungeongen.common.AsciiMap;
import io.github.jvmdc.dungeongen.common.Dungeon;

import java.util.ArrayList;
import java.util.List;

/**
 * Prints one dungeon.
 * {@code --style corridor|accretion} and {@code --seed N}. Accretion is the default.
 * A bare number is still accepted as the seed. Optional width, height, and room count follow it.
 */
public final class DungeonGenMain {
    private DungeonGenMain() {
    }

    public static void main(String[] args) {
        String style = Layouts.DEFAULT;
        Long seed = null;
        List<String> rest = new ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if ("--style".equals(arg) && i + 1 < args.length) {
                style = args[++i];
            } else if (arg.startsWith("--style=")) {
                style = arg.substring("--style=".length());
            } else if ("--seed".equals(arg) && i + 1 < args.length) {
                seed = Long.parseLong(args[++i]);
            } else if (arg.startsWith("--seed=")) {
                seed = Long.parseLong(arg.substring("--seed=".length()));
            } else {
                rest.add(arg);
            }
        }
        if (seed == null) {
            seed = rest.isEmpty() ? 1L : Long.parseLong(rest.remove(0));
        }
        LayoutConfig config = "accretion".equals(style) ? LayoutConfig.attached() : LayoutConfig.standard();
        if (rest.size() >= 3) {
            int width = Integer.parseInt(rest.get(0));
            int height = Integer.parseInt(rest.get(1));
            int rooms = Integer.parseInt(rest.get(2));
            if ("accretion".equals(style)) {
                config = LayoutConfig.attached(width, height);
            } else if (rest.size() >= 9) {
                config = new LayoutConfig(
                        width, height, rooms,
                        Integer.parseInt(rest.get(3)),
                        Integer.parseInt(rest.get(4)),
                        Integer.parseInt(rest.get(5)),
                        Integer.parseInt(rest.get(6)),
                        Integer.parseInt(rest.get(7)),
                        Double.parseDouble(rest.get(8)));
            } else {
                config = new LayoutConfig(width, height, rooms, 3, 3, 3, 3, 5, 0.2);
            }
        }
        Dungeon dungeon = Layouts.of(style).generate(config, seed);
        System.out.println("style " + style
                + " seed " + dungeon.seed
                + " rooms " + dungeon.rooms.size()
                + " corridors " + dungeon.corridors.size());
        System.out.print(AsciiMap.render(dungeon));
    }
}
