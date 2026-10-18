package io.github.jvmdc.prop;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import io.github.jvmdc.debug.LoadTrace;
import io.github.jvmdc.dungeongen.common.Dungeon;
import io.github.jvmdc.dungeongen.common.Facing;
import io.github.jvmdc.dungeongen.common.Placement;
import io.github.jvmdc.dungeongen.common.PlacementType;
import io.github.jvmdc.dungeongen.common.Tile;
import io.github.jvmdc.dungeongen.common.TileGrid;
import io.github.jvmdc.dungeongen.common.TrapKind;
import io.github.jvmdc.prop.PropCatalog.ModelDef;
import io.github.jvmdc.prop.PropCatalog.PlaceableDef;
import io.github.jvmdc.prop.PropCatalog.RigDef;
import io.github.jvmdc.prop.PropCatalog.RigPart;
import io.github.jvmdc.world.BlockChunks;

/**
 * One entity per door, stair, chest, torch, trap, and fixture on a floor.
 * Parts are posed at their resting pivot. They do not animate yet.
 */
public final class PropSpawner {
    private static final float TILE = BlockChunks.VOXELS_PER_TILE;
    /** Rig parts are laid out on one block, then stood in the middle of the map tile. */
    private static final float BLOCK = BlockChunks.VOXELS_PER_BLOCK;

    private PropSpawner() {
    }

    public static Engine populate(Dungeon dungeon, PropCatalog catalog) {
        return populate(dungeon, catalog, null);
    }

    public static Engine populate(Dungeon dungeon, PropCatalog catalog, LoadTrace trace) {
        Engine engine = new Engine();
        TileGrid grid = dungeon.grid;
        for (Placement placement : dungeon.placements) {
            String id = placeableId(placement);
            if (id == null) {
                continue;
            }
            PlaceableDef def = catalog.placeables.get(id);
            if (def == null) {
                continue;
            }
            float yaw = yawOf(placement.facing);
            boolean wall = def.wall || placement.facing != Facing.NONE && placement.type == PlacementType.LIGHT;
            spawn(engine, catalog, def, placement.x, placement.y, yaw, wall, placement.facing, trace);
        }
        for (int y = 0; y < grid.height; y++) {
            for (int x = 0; x < grid.width; x++) {
                Tile tile = grid.get(x, y);
                if (tile.isDoor()) {
                    String id = tile == Tile.LOCKED_DOOR ? "door_locked"
                            : tile == Tile.SECRET_DOOR ? "door_secret" : "door";
                    float yaw = doorYaw(grid, x, y);
                    spawn(engine, catalog, catalog.placeables.get(id), x, y, yaw, false, Facing.NONE, trace);
                } else if (tile == Tile.STAIRS_UP) {
                    spawn(engine, catalog, catalog.placeables.get("stairs_up"), x, y, 0f, false, Facing.NONE, trace);
                } else if (tile == Tile.STAIRS_DOWN) {
                    spawn(engine, catalog, catalog.placeables.get("stairs_down"), x, y, 0f, false, Facing.NONE, trace);
                }
            }
        }
        return engine;
    }

    static String placeableId(Placement placement) {
        return switch (placement.type) {
            case TRAP -> switch (placement.trap == null ? TrapKind.SPIKE_FLOOR : placement.trap) {
                case SPIKE_FLOOR -> "spike_trap";
                case ARROW_WALL -> "arrow_trap";
                case PIT_LID -> "floor_hatch";
                case MAGIC_WALL -> "beam_trap";
                case BEAR_TRAP -> "bear_trap";
            };
            case CHEST -> "chest";
            case LIGHT -> "torch";
            case DECOR -> decorId(placement);
            case FOUNTAIN -> "sink";
            default -> null;
        };
    }

    private static String decorId(Placement placement) {
        if (placement.tags.contains("altar")) {
            return "altar";
        }
        if (placement.tags.contains("sink")) {
            return "sink";
        }
        if (placement.tags.contains("pedestal")) {
            return "pedestal";
        }
        return switch ((placement.x * 17 + placement.y) & 3) {
            case 0 -> "sink";
            case 1 -> "altar";
            case 2 -> "pedestal";
            default -> "power_crystal";
        };
    }

    private static void spawn(Engine engine, PropCatalog catalog, PlaceableDef def,
                              int tileX, int tileY, float yaw, boolean wall, Facing facing, LoadTrace trace) {
        if (def == null) {
            return;
        }
        Entity entity = engine.createEntity();
        PropTransform transform = new PropTransform();
        transform.tileX = tileX;
        transform.tileY = tileY;
        transform.yaw = yaw;
        int depth = 0;
        if (wall) {
            if (trace == null) {
                depth = catalog.mountDepth(def.model);
            } else {
                trace.begin("wall depth");
                try {
                    depth = catalog.mountDepth(def.model);
                } finally {
                    trace.end();
                }
            }
        }
        float[] point = mountPoint(tileX, tileY, facing, wall, depth);
        transform.x = point[0];
        transform.y = point[1];
        transform.z = point[2];
        PropModel model = new PropModel();
        model.id = def.id;
        RigDef rig = catalog.rigs.get(def.model);
        float top = 11f;
        if (rig != null) {
            top = addRig(catalog, model, rig, def.state);
        } else {
            PropModel.Part part = new PropModel.Part();
            part.mesh = catalog.vox(def.model);
            part.sit = !wall;
            model.parts.add(part);
            ModelDef solid = catalog.models.get(def.model);
            if (solid != null) {
                top = solid.sz;
            }
        }
        PropCollider collider = new PropCollider();
        collider.halfX = BLOCK * 0.45f;
        collider.halfZ = BLOCK * 0.45f;
        collider.halfY = Math.max(4f, top * 0.5f);
        collider.blocksMovement = def.blocksMovement;
        collider.blocksSight = def.blocksSight;
        PropInteractable interactable = new PropInteractable();
        interactable.verb = def.verb;
        interactable.state = def.state;
        entity.add(transform);
        entity.add(model);
        entity.add(collider);
        entity.add(interactable);
        if (def.light) {
            PropLight light = new PropLight();
            light.x = transform.x;
            light.y = transform.y + (wall ? 4f : Math.min(top, 12f));
            light.z = transform.z;
            light.r = def.lightR;
            light.g = def.lightG;
            light.b = def.lightB;
            light.radius = def.lightRadius;
            entity.add(light);
        }
        engine.addEntity(entity);
    }

    private static float addRig(PropCatalog catalog, PropModel model, RigDef rig, String state) {
        float top = 1f;
        for (RigPart source : rig.parts) {
            if (state != null && source.hiddenIn.contains(state.toLowerCase())) {
                continue;
            }
            ModelDef solid = catalog.models.get(source.model);
            float sz = solid == null ? 1f : solid.sz;
            float ox = solid == null ? 0f : solid.ox;
            float oy = solid == null ? 0f : solid.oy;
            float oz = solid == null ? 0f : solid.oz;
            float jx = 0f;
            float jy = 0f;
            float jz = 0f;
            float angle = 0f;
            float[] pose = source.states.get(state);
            if (pose == null && !source.states.isEmpty()) {
                pose = source.states.values().iterator().next();
            }
            if (pose != null && "translate".equals(source.joint)) {
                jx = pose[0];
                jy = pose.length > 1 ? pose[1] : 0f;
                jz = pose.length > 2 ? pose[2] : 0f;
            } else if (pose != null && "rotate".equals(source.joint)) {
                angle = pose[0];
            }
            PropModel.Part part = new PropModel.Part();
            part.mesh = catalog.vox(source.model);
            part.local = true;
            part.vx = Math.round(source.ax + ox + jx);
            part.vy = Math.round(source.ay + oy + jy);
            part.vz = Math.round(source.az + oz + jz);
            if (solid != null && solid.hasPivot) {
                part.pivotX = solid.px;
                part.pivotY = solid.pz;
                part.pivotZ = -solid.py;
            }
            if ("rotate".equals(source.joint) || "spin".equals(source.joint)) {
                part.rotates = true;
                part.angle = angle;
                part.axisX = source.axisX;
                part.axisY = source.axisZ;
                part.axisZ = -source.axisY;
            }
            model.parts.add(part);
            top = Math.max(top, part.vz + sz);
        }
        return top;
    }

    /**
     * Floor props stand on the floor top. Wall props sit one voxel out from the
     * wall face, along the facing from placeables (wall or wall_face).
     */
    static float[] mountPoint(int tileX, int tileY, Facing facing, boolean wall, int depth) {
        float x = tileX * TILE + TILE * 0.5f;
        float z = -(tileY * TILE + TILE * 0.5f);
        float y = BlockChunks.FLOOR;
        if (!wall || facing == Facing.NONE) {
            return new float[] {x, y, z};
        }
        float ox = facing.dx;
        float oz = -facing.dy;
        float out = TILE * 0.5f + 1f + depth * 0.5f;
        return new float[] {x + ox * out, y + 14f, z + oz * out};
    }

    static float yawOf(Facing facing) {
        return switch (facing) {
            case N -> 180f;
            case E -> 90f;
            case S -> 0f;
            case W -> -90f;
            case NONE -> 0f;
        };
    }

    private static float doorYaw(TileGrid grid, int x, int y) {
        boolean north = open(grid, x, y - 1);
        boolean south = open(grid, x, y + 1);
        boolean east = open(grid, x + 1, y);
        boolean west = open(grid, x - 1, y);
        if (north && south) {
            return 0f;
        }
        if (east && west) {
            return 90f;
        }
        return 0f;
    }

    private static boolean open(TileGrid grid, int x, int y) {
        if (!grid.inBounds(x, y)) {
            return false;
        }
        Tile tile = grid.get(x, y);
        return tile.isOpen() || tile.isDoor();
    }
}
