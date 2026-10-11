package io.github.jvmdc.world;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Block colours for a generated floor. Each material has a base colour, a
 * small spread from a 2×2 brick hash, and a darker value on voxels that touch
 * open air. Slots are assigned once so a chunk can greedy-mesh equal colours.
 */
public final class BlockPalette {
    private final int[] rgbaBySlot = new int[256];
    private final int[][] slots = new int[Kind.values().length][];
    private final int[] ranges = new int[Kind.values().length];

    private BlockPalette() {
    }

    public static BlockPalette load(Path file) throws IOException {
        JsonValue root;
        try (InputStream in = Files.newInputStream(file)) {
            root = new JsonReader().parse(in);
        }
        BlockPalette palette = new BlockPalette();
        int next = 1;
        for (Kind kind : Kind.values()) {
            JsonValue node = root.get(kind.key);
            if (node == null) {
                throw new IOException("missing block colour " + kind.key);
            }
            JsonValue base = node.get("base");
            int range = node.getInt("range");
            int edge = node.getInt("edge");
            if (range < 1 || next + range * 2 > 256) {
                throw new IOException("block colour " + kind.key + " does not fit the palette");
            }
            int[] table = new int[range * 2];
            for (int shade = 0; shade < range; shade++) {
                table[shade] = next;
                palette.rgbaBySlot[next++] = pack(base, shade);
                table[shade + range] = next;
                palette.rgbaBySlot[next++] = pack(base, shade - edge);
            }
            palette.slots[kind.ordinal()] = table;
            palette.ranges[kind.ordinal()] = range;
        }
        return palette;
    }

    public int[] rgbaBySlot() {
        return rgbaBySlot;
    }

    /** Palette slot for this voxel. 0 is empty and is never returned. */
    public int paint(Kind kind, int x, int y, int z, boolean edge) {
        int range = ranges[kind.ordinal()];
        int shade = brick(x, y, z, range);
        return slots[kind.ordinal()][shade + (edge ? range : 0)];
    }

    public int red(int slot) {
        return (rgbaBySlot[slot] >> 24) & 0xFF;
    }

    /** 2×2×1 bricks, staggered by one each layer. */
    static int brick(int x, int y, int z, int range) {
        int px = (x + z) >> 1;
        int py = (y + z) >> 1;
        int h = reverseBits((px ^ py ^ z) & 0xFF);
        return (h * range) >> 8;
    }

    private static int pack(JsonValue base, int delta) {
        int r = clamp(base.getInt(0) + delta);
        int g = clamp(base.getInt(1) + delta);
        int b = clamp(base.getInt(2) + delta);
        return (r << 24) | (g << 16) | (b << 8);
    }

    private static int clamp(int channel) {
        return Math.max(0, Math.min(255, channel));
    }

    private static int reverseBits(int bits) {
        int b = bits & 0xFF;
        b = ((b & 0xF0) >> 4) | ((b & 0x0F) << 4);
        b = ((b & 0xCC) >> 2) | ((b & 0x33) << 2);
        b = ((b & 0xAA) >> 1) | ((b & 0x55) << 1);
        return b;
    }

    /** Which block colour a voxel uses. Null means that height is open air. */
    public enum Kind {
        WALL("wall"),
        ROOM_FLOOR("room_floor"),
        CORRIDOR("corridor"),
        GROUND("ground"),
        ROCK("rock"),
        CEILING("ceiling"),
        WATER("water"),
        DEEP_WATER("deep_water"),
        LAVA("lava"),
        PIT("pit");

        final String key;

        Kind(String key) {
            this.key = key;
        }
    }
}
