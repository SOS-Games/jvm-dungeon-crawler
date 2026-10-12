package io.github.jvmdc.monster;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import io.github.jvmdc.dungeongen.common.Dungeon;
import io.github.jvmdc.dungeongen.common.Placement;
import io.github.jvmdc.dungeongen.common.PlacementType;
import io.github.jvmdc.dungeongen.common.SeededRandom;
import io.github.jvmdc.dungeongen.common.Tile;
import io.github.jvmdc.dungeongen.common.TileGrid;
import io.github.jvmdc.monster.MonsterTable.Kind;
import io.github.jvmdc.prop.PropCollider;
import io.github.jvmdc.prop.PropTransform;
import io.github.jvmdc.world.BlockChunks;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Turns monster marks into entities. A group mark spreads two to four bodies
 * around its tile. Nothing is placed in a doorway or beside the up-stairs.
 */
public final class MonsterSpawner {
    private static final long STREAM = 0x6D6F6E7374657273L;
    private static final int[] RING_X = {0, 1, -1, 0, 0, 1, 1, -1, -1, 2, -2, 0, 0, 2, 2, -2, -2, 1, -1, 1, -1};
    private static final int[] RING_Y = {0, 0, 0, 1, -1, 1, -1, 1, -1, 0, 0, 2, -2, 1, -1, 1, -1, 2, 2, -2, -2};
    private static final ComponentMapper<MonsterBody> BODY = ComponentMapper.getFor(MonsterBody.class);

    private MonsterSpawner() {
    }

    public static List<MonsterSpot> plan(Dungeon dungeon, MonsterTable table, int depth) {
        List<MonsterSpot> spots = new ArrayList<>();
        if (dungeon == null || table == null) {
            return spots;
        }
        SeededRandom rng = new SeededRandom(dungeon.seed ^ STREAM ^ depth);
        TileGrid grid = dungeon.grid;
        int stairsX = dungeon.stairsUp >= 0 ? grid.x(dungeon.stairsUp) : Integer.MIN_VALUE;
        int stairsY = dungeon.stairsUp >= 0 ? grid.y(dungeon.stairsUp) : Integer.MIN_VALUE;
        Set<Long> used = new HashSet<>();
        for (Placement placement : dungeon.placements) {
            if (placement.type != PlacementType.MONSTER_SPAWN) {
                continue;
            }
            if (!legal(grid, placement.x, placement.y, stairsX, stairsY, table.stairsClearance, used)) {
                continue;
            }
            Kind kind = table.roll(rng, depth);
            if (kind == null) {
                continue;
            }
            int count = placement.tags.contains("group") ? kind.groupCount(rng) : 1;
            int placed = 0;
            for (int i = 0; i < RING_X.length && placed < count; i++) {
                int x = placement.x + RING_X[i];
                int y = placement.y + RING_Y[i];
                if (!legal(grid, x, y, stairsX, stairsY, table.stairsClearance, used)) {
                    continue;
                }
                float yaw = rng.nextInt(360);
                spots.add(new MonsterSpot(x, y, placement.x, placement.y, kind, yaw));
                used.add(key(x, y));
                placed++;
            }
        }
        return spots;
    }

    public static void add(Engine engine, Dungeon dungeon, MonsterTable table, int depth) {
        if (engine == null) {
            return;
        }
        for (MonsterSpot spot : plan(dungeon, table, depth)) {
            Entity entity = engine.createEntity();
            PropTransform transform = new PropTransform();
            transform.tileX = spot.tileX;
            transform.tileY = spot.tileY;
            transform.x = spot.x;
            transform.y = spot.y;
            transform.z = spot.z;
            transform.yaw = spot.yaw;
            MonsterBody body = new MonsterBody();
            body.id = spot.kind.id;
            body.species = spot.kind.species;
            body.body = spot.kind.body;
            body.scale = table.worldScale();
            body.sourceX = spot.sourceX;
            body.sourceY = spot.sourceY;
            MonsterHealth health = new MonsterHealth();
            health.maxHp = spot.kind.hp;
            health.hp = spot.kind.hp;
            MonsterFaction faction = new MonsterFaction();
            PropCollider collider = new PropCollider();
            collider.halfX = BlockChunks.VOXELS_PER_TILE * 0.25f;
            collider.halfY = 8f;
            collider.halfZ = BlockChunks.VOXELS_PER_TILE * 0.25f;
            collider.blocksMovement = true;
            collider.blocksSight = false;
            MonsterAi ai = new MonsterAi();
            entity.add(transform);
            entity.add(body);
            entity.add(health);
            entity.add(faction);
            entity.add(collider);
            entity.add(ai);
            engine.addEntity(entity);
        }
    }

    public static void clear(Engine engine) {
        if (engine == null) {
            return;
        }
        ImmutableArray<Entity> entities = engine.getEntitiesFor(Family.all(MonsterBody.class).get());
        List<Entity> doomed = new ArrayList<>();
        for (Entity entity : entities) {
            doomed.add(entity);
        }
        for (Entity entity : doomed) {
            engine.removeEntity(entity);
        }
    }

    public static int count(Engine engine) {
        if (engine == null) {
            return 0;
        }
        return engine.getEntitiesFor(Family.all(MonsterBody.class).get()).size();
    }

    public static String idAt(Engine engine, int index) {
        ImmutableArray<Entity> entities = engine.getEntitiesFor(Family.all(MonsterBody.class).get());
        if (index < 0 || index >= entities.size()) {
            return "";
        }
        return BODY.get(entities.get(index)).id;
    }

    private static boolean legal(TileGrid grid, int x, int y, int stairsX, int stairsY, int clearance,
            Set<Long> used) {
        if (!grid.inBounds(x, y) || used.contains(key(x, y))) {
            return false;
        }
        Tile tile = grid.get(x, y);
        if (tile != Tile.ROOM_FLOOR && tile != Tile.CORRIDOR_FLOOR && tile != Tile.GROUND
                && tile != Tile.TRAP_PLATE) {
            return false;
        }
        if (besideDoor(grid, x, y)) {
            return false;
        }
        if (stairsX != Integer.MIN_VALUE
                && Math.max(Math.abs(x - stairsX), Math.abs(y - stairsY)) <= clearance) {
            return false;
        }
        return true;
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

    public static final class MonsterSpot {
        public final int tileX;
        public final int tileY;
        public final int sourceX;
        public final int sourceY;
        public final Kind kind;
        public final float yaw;
        public final float x;
        public final float y;
        public final float z;

        MonsterSpot(int tileX, int tileY, int sourceX, int sourceY, Kind kind, float yaw) {
            this.tileX = tileX;
            this.tileY = tileY;
            this.sourceX = sourceX;
            this.sourceY = sourceY;
            this.kind = kind;
            this.yaw = yaw;
            float center = BlockChunks.VOXELS_PER_TILE * 0.5f;
            this.x = tileX * BlockChunks.VOXELS_PER_TILE + center;
            this.y = BlockChunks.FLOOR;
            this.z = -(tileY * BlockChunks.VOXELS_PER_TILE + center);
        }
    }
}
