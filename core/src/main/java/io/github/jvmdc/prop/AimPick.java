package io.github.jvmdc.prop;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import io.github.jvmdc.dungeongen.common.Dungeon;
import io.github.jvmdc.dungeongen.common.Tile;
import io.github.jvmdc.dungeongen.common.TileGrid;
import io.github.jvmdc.monster.MonsterBody;
import io.github.jvmdc.world.BlockChunks;

/** Name of the first thing a look ray meets: a body, a prop, or the dungeon surface. */
public final class AimPick {
    private static final ComponentMapper<PropTransform> TRANSFORM = ComponentMapper.getFor(PropTransform.class);
    private static final ComponentMapper<PropCollider> COLLIDER = ComponentMapper.getFor(PropCollider.class);
    private static final ComponentMapper<PropModel> MODEL = ComponentMapper.getFor(PropModel.class);
    private static final ComponentMapper<MonsterBody> BODY = ComponentMapper.getFor(MonsterBody.class);
    private static final float[] SPAN = new float[2];
    private static final Vector3 DIR = new Vector3();

    private AimPick() {
    }

    public static String find(Dungeon dungeon, Engine engine, Vector3 origin, Vector3 direction, boolean ceiling) {
        if (dungeon == null || origin == null || direction == null || direction.len2() < 1e-8f) {
            return "";
        }
        DIR.set(direction).nor();
        float limit = (dungeon.grid.width + dungeon.grid.height) * (float) BlockChunks.VOXELS_PER_TILE
                + BlockChunks.HEIGHT;
        float propT = propDistance(engine, origin, limit);
        float tileT = tileDistance(dungeon.grid, origin, limit, ceiling);
        if (propT < tileT) {
            return propName(engine, origin, propT);
        }
        if (tileT < limit) {
            return surface(dungeon.grid, origin.x + DIR.x * tileT, origin.y + DIR.y * tileT,
                    origin.z + DIR.z * tileT, ceiling);
        }
        return "";
    }

    private static float propDistance(Engine engine, Vector3 origin, float limit) {
        float best = limit;
        if (engine == null) {
            return best;
        }
        ImmutableArray<Entity> entities = engine.getEntitiesFor(
                Family.all(PropTransform.class, PropCollider.class).get());
        for (Entity entity : entities) {
            if (label(entity).isEmpty()) {
                continue;
            }
            float t = hitBox(origin, TRANSFORM.get(entity), COLLIDER.get(entity), best);
            if (t >= 0f && t < best) {
                best = t;
            }
        }
        return best;
    }

    private static String propName(Engine engine, Vector3 origin, float at) {
        String name = "";
        float best = at + 0.51f;
        ImmutableArray<Entity> entities = engine.getEntitiesFor(
                Family.all(PropTransform.class, PropCollider.class).get());
        for (Entity entity : entities) {
            String label = label(entity);
            if (label.isEmpty()) {
                continue;
            }
            float t = hitBox(origin, TRANSFORM.get(entity), COLLIDER.get(entity), best);
            if (t >= 0f && t < best) {
                best = t;
                name = label;
            }
        }
        return name;
    }

    private static String label(Entity entity) {
        MonsterBody body = BODY.get(entity);
        if (body != null && !body.id.isEmpty()) {
            return pretty(body.id);
        }
        PropModel model = MODEL.get(entity);
        if (model != null && !model.id.isEmpty()) {
            return pretty(model.id);
        }
        return "";
    }

    private static String pretty(String id) {
        String spaced = id.replace('_', ' ');
        return Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }

    /** Ray entry time, or -1. The box hangs from half a height below the origin to a full height above it. */
    private static float hitBox(Vector3 origin, PropTransform transform, PropCollider box, float limit) {
        float cos = MathUtils.cosDeg(transform.yaw);
        float sin = MathUtils.sinDeg(transform.yaw);
        float wx = origin.x - transform.x;
        float wy = origin.y - transform.y;
        float wz = origin.z - transform.z;
        float lx = wx * cos - wz * sin;
        float lz = wx * sin + wz * cos;
        float ldx = DIR.x * cos - DIR.z * sin;
        float ldz = DIR.x * sin + DIR.z * cos;
        SPAN[0] = 0.05f;
        SPAN[1] = limit;
        if (!clip(lx, ldx, -box.halfX, box.halfX)) {
            return -1f;
        }
        if (!clip(wy, DIR.y, -box.halfY, box.halfY * 2f)) {
            return -1f;
        }
        if (!clip(lz, ldz, -box.halfZ, box.halfZ)) {
            return -1f;
        }
        return SPAN[0];
    }

    private static boolean clip(float origin, float dir, float min, float max) {
        if (Math.abs(dir) < 1e-8f) {
            return origin >= min && origin <= max;
        }
        float t1 = (min - origin) / dir;
        float t2 = (max - origin) / dir;
        if (t1 > t2) {
            float swap = t1;
            t1 = t2;
            t2 = swap;
        }
        SPAN[0] = Math.max(SPAN[0], t1);
        SPAN[1] = Math.min(SPAN[1], t2);
        return SPAN[0] <= SPAN[1];
    }

    private static float tileDistance(TileGrid grid, Vector3 origin, float limit, boolean ceiling) {
        float step = 1f;
        for (float t = 0.25f; t < limit; t += step) {
            String hit = surface(grid, origin.x + DIR.x * t, origin.y + DIR.y * t, origin.z + DIR.z * t, ceiling);
            if (!hit.isEmpty()) {
                return t;
            }
        }
        return limit;
    }

    private static String surface(TileGrid grid, float x, float y, float z, boolean ceiling) {
        if (y < 0f || y >= BlockChunks.HEIGHT) {
            return "";
        }
        int tx = (int) Math.floor(x / BlockChunks.VOXELS_PER_TILE);
        int ty = (int) Math.floor(-z / BlockChunks.VOXELS_PER_TILE);
        if (!grid.inBounds(tx, ty)) {
            return "";
        }
        Tile tile = grid.get(tx, ty);
        if (tile == Tile.WALL) {
            return "Wall";
        }
        if (tile == Tile.ROCK) {
            return "Rock";
        }
        if (ceiling && y >= BlockChunks.HEIGHT - BlockChunks.CEILING) {
            return "Ceiling";
        }
        if (tile != Tile.PIT && y < BlockChunks.FLOOR) {
            return switch (tile) {
                case CORRIDOR_FLOOR -> "Corridor";
                case GROUND -> "Ground";
                case WATER -> "Water";
                case DEEP_WATER -> "Deep water";
                case LAVA -> "Lava";
                case TRAP_PLATE -> "Trap";
                case DOOR -> "Door";
                case LOCKED_DOOR -> "Locked door";
                case SECRET_DOOR -> "Secret door";
                case STAIRS_UP -> "Stairs up";
                case STAIRS_DOWN -> "Stairs down";
                default -> "Floor";
            };
        }
        return "";
    }
}
