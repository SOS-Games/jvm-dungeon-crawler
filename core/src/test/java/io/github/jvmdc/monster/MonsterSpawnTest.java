package io.github.jvmdc.monster;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import io.github.jvmdc.dungeongen.LayoutConfig;
import io.github.jvmdc.dungeongen.Layouts;
import io.github.jvmdc.dungeongen.common.Dungeon;
import io.github.jvmdc.dungeongen.common.Placement;
import io.github.jvmdc.dungeongen.common.PlacementType;
import io.github.jvmdc.dungeongen.common.SeededRandom;
import io.github.jvmdc.dungeongen.common.Tile;
import io.github.jvmdc.dungeongen.common.TileGrid;
import io.github.jvmdc.monster.MonsterTable.Kind;
import io.github.jvmdc.prop.PropCollider;
import io.github.jvmdc.prop.PropTransform;
import io.github.jvmdc.viewer.FigureLibrary;
import io.github.jvmdc.world.BlockChunks;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MonsterSpawnTest {
    private static final int[] RING_X = {0, 1, -1, 0, 0, 1, 1, -1, -1, 2, -2, 0, 0, 2, 2, -2, -2, 1, -1, 1, -1};
    private static final int[] RING_Y = {0, 0, 0, 1, -1, 1, -1, 1, -1, 0, 0, 2, -2, 1, -1, 1, -1, 2, 2, -2, -2};
    private static final ComponentMapper<PropTransform> TRANSFORM = ComponentMapper.getFor(PropTransform.class);
    private static final ComponentMapper<MonsterBody> BODY = ComponentMapper.getFor(MonsterBody.class);
    private static final ComponentMapper<MonsterHealth> HEALTH = ComponentMapper.getFor(MonsterHealth.class);
    private static final ComponentMapper<MonsterFaction> FACTION = ComponentMapper.getFor(MonsterFaction.class);
    private static final ComponentMapper<MonsterAi> AI = ComponentMapper.getFor(MonsterAi.class);
    private static final ComponentMapper<PropCollider> COLLIDER = ComponentMapper.getFor(PropCollider.class);

    @Test
    void tableMapsBodiesAndFloors() throws Exception {
        MonsterTable table = MonsterTable.load();
        assertEquals(11, table.voxelsPerTile);
        assertEquals(6, table.stairsClearance);
        assertEquals(3f, table.worldScale(), 0.001f);
        Kind boar = table.kind("boar");
        assertEquals("boar/male", boar.species);
        assertEquals("quadruped_small", boar.body);
        assertEquals(14, boar.hp);
        assertEquals(4, boar.damage);
        assertEquals(4, boar.speed);
        assertEquals("cavetroll", table.kind("troll").species);
        assertEquals("biped_large", table.kind("troll").body);
        assertEquals("arthropod", table.kind("cavespider").body);
        for (String id : List.of("batfox", "frog", "boar", "cavespider", "wolf", "bear", "troll")) {
            Kind kind = table.kind(id);
            assertTrue(FigureLibrary.available(kind.species, kind.body), id);
        }
        SeededRandom early = new SeededRandom(1);
        SeededRandom late = new SeededRandom(1);
        Set<String> floor0 = new HashSet<>();
        Set<String> floor2 = new HashSet<>();
        for (int i = 0; i < 40; i++) {
            floor0.add(table.roll(early, 0).id);
            floor2.add(table.roll(late, 2).id);
        }
        assertTrue(floor0.contains("boar") || floor0.contains("batfox") || floor0.contains("frog"));
        assertTrue(Set.of("batfox", "frog", "boar", "cavespider").containsAll(floor0), floor0.toString());
        assertTrue(Set.of("wolf", "bear", "troll").containsAll(floor2), floor2.toString());
    }

    @Test
    void fitKeepsTheBodyInsideTheTileAndOnTheFloor() {
        float requested = BlockChunks.VOXELS_PER_TILE / 11f;
        float[] wide = {-20f, -4f, -8f, 20f, 12f, 8f};
        float scale = MonsterFit.scaleToTile(requested, wide);
        float reach = (float) Math.hypot(20f, 8f);
        float limit = BlockChunks.VOXELS_PER_TILE * 0.5f - 1f;
        assertTrue(scale < requested);
        assertTrue(reach * scale <= limit + 0.01f);
        assertEquals(BlockChunks.FLOOR - wide[1] * scale, MonsterFit.footY(scale, wide[1]), 0.001f);
        float[] tall = {-2f, 0f, -2f, 2f, 80f, 2f};
        float tallScale = MonsterFit.scaleToTile(requested, tall);
        assertTrue(80f * tallScale <= BlockChunks.STORY - 1f + 0.01f);
    }

    @Test
    void seedsOneAndEightStandClearOfStairsAndDoors() throws Exception {
        MonsterTable table = MonsterTable.load();
        stand(table, 1);
        stand(table, 8);
    }

    private static void stand(MonsterTable table, long seed) {
        Dungeon dungeon = Layouts.of(Layouts.DEFAULT).generate(LayoutConfig.attached(), seed);
        List<MonsterSpawner.MonsterSpot> spots = MonsterSpawner.plan(dungeon, table, 0);
        assertTrue(spots.size() >= 1, "seed " + seed);
        TileGrid grid = dungeon.grid;
        int stairsX = grid.x(dungeon.stairsUp);
        int stairsY = grid.y(dungeon.stairsUp);
        Set<Long> tiles = new HashSet<>();
        Map<Long, Long> owner = new HashMap<>();
        Map<Long, Integer> groups = new HashMap<>();
        Map<Long, Kind> kinds = new HashMap<>();
        for (MonsterSpawner.MonsterSpot spot : spots) {
            assertTrue(tiles.add(key(spot.tileX, spot.tileY)), "seed " + seed + " overlap");
            owner.put(key(spot.tileX, spot.tileY), key(spot.sourceX, spot.sourceY));
            Tile tile = grid.get(spot.tileX, spot.tileY);
            assertTrue(tile == Tile.ROOM_FLOOR || tile == Tile.CORRIDOR_FLOOR || tile == Tile.GROUND
                    || tile == Tile.TRAP_PLATE, "seed " + seed);
            assertTrue(Math.max(Math.abs(spot.tileX - stairsX), Math.abs(spot.tileY - stairsY))
                    > table.stairsClearance, "seed " + seed + " stairs");
            assertTrue(!besideDoor(grid, spot.tileX, spot.tileY), "seed " + seed + " door");
            assertEquals(BlockChunks.FLOOR, spot.y, 0.001f);
            assertTrue(Math.max(Math.abs(spot.tileX - spot.sourceX), Math.abs(spot.tileY - spot.sourceY)) <= 2);
            long source = key(spot.sourceX, spot.sourceY);
            groups.merge(source, 1, Integer::sum);
            kinds.put(source, spot.kind);
        }
        for (Placement placement : dungeon.placements) {
            if (placement.type != PlacementType.MONSTER_SPAWN) {
                continue;
            }
            Integer count = groups.get(key(placement.x, placement.y));
            if (count == null) {
                continue;
            }
            Kind kind = kinds.get(key(placement.x, placement.y));
            if (!placement.tags.contains("group")) {
                assertEquals(1, count, "seed " + seed);
                continue;
            }
            assertTrue(count >= 1 && count <= kind.groupMax, "seed " + seed);
            int room = openAround(grid, placement.x, placement.y, stairsX, stairsY,
                    table.stairsClearance, owner, key(placement.x, placement.y));
            if (room >= kind.groupMax) {
                assertTrue(count >= kind.groupMin, "seed " + seed + " group " + count);
            }
        }
        Engine engine = new Engine();
        MonsterSpawner.add(engine, dungeon, table, 0);
        var entities = engine.getEntitiesFor(Family.all(
                PropTransform.class, MonsterBody.class, MonsterHealth.class,
                MonsterFaction.class, PropCollider.class, MonsterAi.class).get());
        assertEquals(spots.size(), entities.size());
        for (Entity entity : entities) {
            assertEquals(BlockChunks.FLOOR, TRANSFORM.get(entity).y, 0.001f);
            assertEquals("idle", AI.get(entity).state);
            assertEquals("wild", FACTION.get(entity).name);
            MonsterHealth health = HEALTH.get(entity);
            assertTrue(health.hp > 0 && health.hp == health.maxHp);
            assertTrue(COLLIDER.get(entity).blocksMovement);
            assertEquals(table.worldScale(), BODY.get(entity).scale, 0.001f);
        }
        MonsterSpawner.clear(engine);
        assertEquals(0, MonsterSpawner.count(engine));
    }

    /** Ring cells this mark could use: empty, or already taken by this same mark. */
    private static int openAround(TileGrid grid, int ox, int oy, int stairsX, int stairsY, int clearance,
            Map<Long, Long> owner, long source) {
        int legal = 0;
        Set<Long> seen = new HashSet<>();
        for (int i = 0; i < RING_X.length; i++) {
            int x = ox + RING_X[i];
            int y = oy + RING_Y[i];
            if (!grid.inBounds(x, y) || !seen.add(key(x, y))) {
                continue;
            }
            Long held = owner.get(key(x, y));
            if (held != null && held != source) {
                continue;
            }
            Tile tile = grid.get(x, y);
            if (tile != Tile.ROOM_FLOOR && tile != Tile.CORRIDOR_FLOOR && tile != Tile.GROUND
                    && tile != Tile.TRAP_PLATE) {
                continue;
            }
            if (besideDoor(grid, x, y)) {
                continue;
            }
            if (Math.max(Math.abs(x - stairsX), Math.abs(y - stairsY)) <= clearance) {
                continue;
            }
            legal++;
        }
        return legal;
    }

    private static boolean besideDoor(TileGrid grid, int x, int y) {
        int[] dx = {0, 1, 0, -1};
        int[] dy = {-1, 0, 1, 0};
        for (int dir = 0; dir < 4; dir++) {
            int nx = x + dx[dir];
            int ny = y + dy[dir];
            if (grid.inBounds(nx, ny) && grid.get(nx, ny).isDoor()) {
                return true;
            }
        }
        return false;
    }

    private static long key(int x, int y) {
        return ((long) x << 32) | (y & 0xffffffffL);
    }
}
