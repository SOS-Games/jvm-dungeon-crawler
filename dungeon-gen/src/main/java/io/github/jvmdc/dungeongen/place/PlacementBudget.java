package io.github.jvmdc.dungeongen.place;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * How full a floor should be. Every count comes from {@code placement.json}.
 * {@link #density} is the debug multiplier, from half to double.
 */
public final class PlacementBudget {
    /** Live tuning knob. 1 is the config as written. */
    public static double density = 1.0;

    public final double perTiles;
    public final double decor;
    public final double items;
    public final double monsters;
    public final double traps;
    public final int roomDecorMin;
    public final int roomOtherMin;
    public final double courtTiles;
    public final int courtGroupMin;
    public final int courtGroupMax;
    public final int courtClusterMin;
    public final int courtClusterMax;
    public final int clusterRadius;
    public final int fountainGap;
    public final int sinkGap;
    public final int sinkRoomTiles;
    public final double halls;
    public final int lightWallTiles;
    public final int lightMinGap;
    public final int shopMin;
    public final int shopMax;
    public final int fountainMin;
    public final int fountainMax;
    public final int sinkMin;
    public final int sinkMax;
    public final int altarMin;
    public final int altarMax;
    public final double poolChance;
    public final double lavaChance;
    public final double densityMin;
    public final double densityMax;
    public final int bossEveryFloors;
    public final double chestChance;
    public final Map<String, List<String>> themes;
    public final List<String> courtPieces;
    public final List<String> solidPieces;

    private static PlacementBudget loaded;

    private PlacementBudget(String text) {
        perTiles = num(text, "perTiles", 10);
        decor = num(text, "decor", 1);
        items = num(text, "items", 0.4);
        monsters = num(text, "monsters", 0.3);
        traps = num(text, "traps", 0.15);
        roomDecorMin = (int) num(text, "roomDecorMin", 2);
        roomOtherMin = (int) num(text, "roomOtherMin", 1);
        courtTiles = num(text, "courtTiles", 25);
        courtGroupMin = (int) num(text, "courtGroupMin", 2);
        courtGroupMax = (int) num(text, "courtGroupMax", 4);
        courtClusterMin = (int) num(text, "courtClusterMin", 3);
        courtClusterMax = (int) num(text, "courtClusterMax", 6);
        clusterRadius = (int) num(text, "clusterRadius", 2);
        fountainGap = (int) num(text, "fountainGap", 6);
        sinkGap = (int) num(text, "sinkGap", 4);
        sinkRoomTiles = (int) num(text, "sinkRoomTiles", 8);
        halls = num(text, "halls", 0.35);
        lightWallTiles = (int) num(text, "lightWallTiles", 12);
        lightMinGap = (int) num(text, "lightMinGap", 5);
        shopMin = (int) num(text, "shopMin", 0);
        shopMax = (int) num(text, "shopMax", 1);
        fountainMin = (int) num(text, "fountainMin", 0);
        fountainMax = (int) num(text, "fountainMax", 2);
        sinkMin = (int) num(text, "sinkMin", 0);
        sinkMax = (int) num(text, "sinkMax", 2);
        altarMin = (int) num(text, "altarMin", 0);
        altarMax = (int) num(text, "altarMax", 1);
        poolChance = num(text, "poolChance", 0.45);
        lavaChance = num(text, "lavaChance", 0.5);
        densityMin = num(text, "densityMin", 0.5);
        densityMax = num(text, "densityMax", 2);
        bossEveryFloors = (int) num(text, "bossEveryFloors", 3);
        chestChance = num(text, "chestChance", 0.4);
        themes = new LinkedHashMap<>();
        themes.put("storage", pieces(text, "theme.storage", "barrel,crate"));
        themes.put("barracks", pieces(text, "theme.barracks", "bunk,rack"));
        themes.put("library", pieces(text, "theme.library", "bookshelf"));
        themes.put("shrine", pieces(text, "theme.shrine", "candle"));
        themes.put("crypt", pieces(text, "theme.crypt", "bones,web"));
        themes.put("treasure", pieces(text, "theme.treasure", "coin,pedestal"));
        courtPieces = pieces(text, "courtPieces", "rubble,bones,pillar,stalagmite");
        solidPieces = pieces(text, "solidPieces", "barrel,crate,bookshelf,pedestal,pillar,stalagmite,altar,sink");
    }

    public static PlacementBudget load() {
        if (loaded != null) {
            return loaded;
        }
        String text = "";
        try (InputStream in = PlacementBudget.class.getResourceAsStream("/placement.json")) {
            if (in != null) {
                text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (IOException ignored) {
            text = "";
        }
        loaded = new PlacementBudget(text);
        return loaded;
    }

    /** Debug multiplier clamped to the config range. Lights ignore it. */
    public double factor() {
        return Math.max(densityMin, Math.min(densityMax, density));
    }

    /** How many of this kind a patch of floor should hold, after the debug multiplier. */
    public int scaled(int tiles, double rate) {
        if (tiles <= 0 || perTiles <= 0 || rate <= 0) {
            return 0;
        }
        return (int) Math.round(tiles / perTiles * rate * factor());
    }

    public boolean solid(String piece) {
        return solidPieces.contains(piece);
    }

    private static List<String> pieces(String text, String key, String fallback) {
        String raw = str(text, key, fallback);
        List<String> out = new ArrayList<>();
        for (String part : raw.split(",")) {
            String name = part.trim();
            if (!name.isEmpty()) {
                out.add(name);
            }
        }
        return List.copyOf(out);
    }

    private static double num(String text, String key, double fallback) {
        String raw = str(text, key, null);
        if (raw == null) {
            return fallback;
        }
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** Value after the key, either a number or a quoted string. */
    private static String str(String text, String key, String fallback) {
        String needle = "\"" + key + "\"";
        int at = text.indexOf(needle);
        if (at < 0) {
            return fallback;
        }
        int colon = text.indexOf(':', at + needle.length());
        if (colon < 0) {
            return fallback;
        }
        int i = colon + 1;
        while (i < text.length() && Character.isWhitespace(text.charAt(i))) {
            i++;
        }
        if (i >= text.length()) {
            return fallback;
        }
        if (text.charAt(i) == '"') {
            int end = text.indexOf('"', i + 1);
            return end < 0 ? fallback : text.substring(i + 1, end);
        }
        int end = i;
        while (end < text.length()) {
            char c = text.charAt(end);
            if ((c >= '0' && c <= '9') || c == '.' || c == '-') {
                end++;
            } else {
                break;
            }
        }
        return end == i ? fallback : text.substring(i, end);
    }
}
