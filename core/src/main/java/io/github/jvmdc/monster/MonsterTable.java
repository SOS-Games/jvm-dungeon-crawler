package io.github.jvmdc.monster;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import io.github.jvmdc.assets.AssetRoots;
import io.github.jvmdc.dungeongen.common.SeededRandom;
import io.github.jvmdc.world.BlockChunks;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Monster ids, placeholder stats, and which ids a floor depth may roll. */
public final class MonsterTable {
    public final int voxelsPerTile;
    public final int stairsClearance;
    private final Map<String, Kind> kinds;
    private final List<Floor> floors;

    private MonsterTable(int voxelsPerTile, int stairsClearance, Map<String, Kind> kinds, List<Floor> floors) {
        this.voxelsPerTile = voxelsPerTile;
        this.stairsClearance = stairsClearance;
        this.kinds = kinds;
        this.floors = floors;
    }

    public static MonsterTable load() throws IOException {
        Path file = AssetRoots.locate().custom.resolve("manifest").resolve("monsters.json");
        try (InputStream in = Files.newInputStream(file)) {
            return parse(new JsonReader().parse(in));
        }
    }

    /** Eleven figure voxels span one map tile before the body is shrunk to fit. */
    public float worldScale() {
        return BlockChunks.VOXELS_PER_TILE / (float) Math.max(1, voxelsPerTile);
    }

    public Kind kind(String id) {
        return kinds.get(id);
    }

    public Kind roll(SeededRandom rng, int depth) {
        Floor floor = floorFor(depth);
        int total = 0;
        List<Kind> pool = new ArrayList<>();
        List<Integer> weights = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : floor.weights.entrySet()) {
            Kind kind = kinds.get(entry.getKey());
            if (kind == null || entry.getValue() <= 0 || !kind.fits(floor.level)) {
                continue;
            }
            pool.add(kind);
            weights.add(entry.getValue());
            total += entry.getValue();
        }
        if (pool.isEmpty()) {
            for (Map.Entry<String, Integer> entry : floor.weights.entrySet()) {
                Kind kind = kinds.get(entry.getKey());
                if (kind == null || entry.getValue() <= 0) {
                    continue;
                }
                pool.add(kind);
                weights.add(entry.getValue());
                total += entry.getValue();
            }
        }
        if (pool.isEmpty()) {
            return null;
        }
        int pick = rng.nextInt(total);
        int cursor = 0;
        for (int i = 0; i < pool.size(); i++) {
            cursor += weights.get(i);
            if (pick < cursor) {
                return pool.get(i);
            }
        }
        return pool.get(pool.size() - 1);
    }

    private Floor floorFor(int depth) {
        Floor best = floors.get(0);
        for (Floor floor : floors) {
            if (floor.depth <= depth && floor.depth >= best.depth) {
                best = floor;
            }
        }
        return best;
    }

    private static MonsterTable parse(JsonValue root) {
        int voxels = root.getInt("voxelsPerTile", 11);
        int clearance = root.getInt("stairsClearance", 6);
        Map<String, Kind> kinds = new LinkedHashMap<>();
        JsonValue monsters = root.get("monsters");
        if (monsters != null) {
            for (JsonValue node = monsters.child; node != null; node = node.next) {
                kinds.put(node.name, new Kind(
                        node.name,
                        node.getString("species", ""),
                        node.getString("body", ""),
                        node.getInt("levelMin", 1),
                        node.getInt("levelMax", 1),
                        node.getInt("groupMin", 1),
                        node.getInt("groupMax", 1),
                        node.getInt("hp_placeholder", 1),
                        node.getInt("damage_placeholder", 1),
                        node.getInt("speed_placeholder", 1)));
            }
        }
        List<Floor> floors = new ArrayList<>();
        JsonValue rows = root.get("floors");
        if (rows != null) {
            for (JsonValue row = rows.child; row != null; row = row.next) {
                Map<String, Integer> weights = new LinkedHashMap<>();
                JsonValue table = row.get("weights");
                if (table != null) {
                    for (JsonValue weight = table.child; weight != null; weight = weight.next) {
                        weights.put(weight.name, weight.asInt());
                    }
                }
                floors.add(new Floor(row.getInt("depth", 0), row.getInt("level", 1), weights));
            }
        }
        if (floors.isEmpty()) {
            floors.add(new Floor(0, 1, Map.of()));
        }
        return new MonsterTable(voxels, clearance, kinds, floors);
    }

    public static final class Kind {
        public final String id;
        public final String species;
        public final String body;
        public final int levelMin;
        public final int levelMax;
        public final int groupMin;
        public final int groupMax;
        public final int hp;
        public final int damage;
        public final int speed;

        Kind(String id, String species, String body, int levelMin, int levelMax,
                int groupMin, int groupMax, int hp, int damage, int speed) {
            this.id = id;
            this.species = species;
            this.body = body;
            this.levelMin = levelMin;
            this.levelMax = Math.max(levelMin, levelMax);
            this.groupMin = Math.max(1, groupMin);
            this.groupMax = Math.max(this.groupMin, groupMax);
            this.hp = hp;
            this.damage = damage;
            this.speed = speed;
        }

        boolean fits(int level) {
            return level >= levelMin && level <= levelMax;
        }

        public int groupCount(SeededRandom rng) {
            if (groupMin == groupMax) {
                return groupMin;
            }
            return rng.nextInt(groupMin, groupMax + 1);
        }
    }

    private static final class Floor {
        final int depth;
        final int level;
        final Map<String, Integer> weights;

        Floor(int depth, int level, Map<String, Integer> weights) {
            this.depth = depth;
            this.level = level;
            this.weights = weights;
        }
    }
}
