package io.github.jvmdc.prop;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import io.github.jvmdc.assets.AssetRoots;
import io.github.jvmdc.dungeongen.LayoutConfig;
import io.github.jvmdc.dungeongen.Layouts;
import io.github.jvmdc.dungeongen.common.Dungeon;
import io.github.jvmdc.dungeongen.common.Placement;
import io.github.jvmdc.dungeongen.common.PlacementType;
import io.github.jvmdc.dungeongen.common.Tile;
import io.github.jvmdc.dungeongen.common.TileGrid;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PropSpawnerTest {
    private static final ComponentMapper<PropModel> MODEL = ComponentMapper.getFor(PropModel.class);
    private static final ComponentMapper<PropTransform> TRANSFORM = ComponentMapper.getFor(PropTransform.class);
    private static final ComponentMapper<PropInteractable> USE = ComponentMapper.getFor(PropInteractable.class);
    private static final ComponentMapper<PropLight> LIGHT = ComponentMapper.getFor(PropLight.class);

    @Test
    void rigsKeepPivots() throws Exception {
        PropCatalog catalog = PropCatalog.load(AssetRoots.locate());
        assertTrue(catalog.models.get("trap.lever_handle").hasPivot);
        assertEquals(5, catalog.rigs.get("placement.altar").parts.size());
        assertTrue(catalog.vox("Door").startsWith("voxygen.voxel."));
        assertTrue(catalog.vox("trap.lever_base").startsWith("custom.voxel."));
        assertTrue(catalog.placeables.get("altar").light);
        assertTrue(catalog.placeables.get("torch").light);
        assertTrue(catalog.placeables.get("power_crystal").light);
    }

    @Test
    void seedsOneAndEightSpawnProps() throws Exception {
        PropCatalog catalog = PropCatalog.load(AssetRoots.locate());
        spawn(catalog, 1);
        spawn(catalog, 8);
    }

    private static void spawn(PropCatalog catalog, long seed) {
        Dungeon dungeon = Layouts.of(Layouts.DEFAULT).generate(LayoutConfig.attached(), seed);
        Engine engine = PropSpawner.populate(dungeon, catalog);
        var entities = engine.getEntitiesFor(Family.all(
                PropTransform.class, PropModel.class, PropCollider.class, PropInteractable.class).get());
        assertEquals(engine.getEntities().size(), entities.size(), "seed " + seed);
        int doors = 0;
        int stairs = 0;
        int lights = 0;
        int chests = 0;
        boolean rig = false;
        TileGrid grid = dungeon.grid;
        for (Entity entity : entities) {
            PropModel model = MODEL.get(entity);
            if (model.id.startsWith("door")) {
                doors++;
            }
            if ("stairs_up".equals(model.id) || "stairs_down".equals(model.id)) {
                stairs++;
            }
            if ("chest".equals(model.id)) {
                chests++;
                assertEquals("open", USE.get(entity).verb);
            }
            if (LIGHT.has(entity)) {
                lights++;
                assertTrue(LIGHT.get(entity).radius > 0f);
            }
            if (model.parts.size() > 1) {
                rig = true;
                PropModel.Part first = model.parts.get(0);
                boolean separated = false;
                for (PropModel.Part part : model.parts) {
                    if (part.rotates || part.x != first.x || part.y != first.y || part.z != first.z) {
                        separated = true;
                    }
                    assertFalse(part.mesh.isEmpty(), model.id);
                }
                assertTrue(separated, model.id);
            }
            PropTransform at = TRANSFORM.get(entity);
            assertTrue(grid.inBounds(at.tileX, at.tileY));
        }
        int doorTiles = 0;
        int stairTiles = 0;
        for (int y = 0; y < grid.height; y++) {
            for (int x = 0; x < grid.width; x++) {
                Tile tile = grid.get(x, y);
                if (tile.isDoor()) {
                    doorTiles++;
                }
                if (tile == Tile.STAIRS_UP || tile == Tile.STAIRS_DOWN) {
                    stairTiles++;
                }
            }
        }
        assertEquals(doorTiles, doors, "seed " + seed);
        assertEquals(stairTiles, stairs, "seed " + seed);
        int placedLights = 0;
        for (Placement placement : dungeon.placements) {
            if (placement.type == PlacementType.LIGHT) {
                placedLights++;
            }
            if (placement.type == PlacementType.CHEST) {
                chests--;
            }
        }
        assertEquals(0, chests, "seed " + seed);
        assertTrue(lights >= placedLights, "seed " + seed);
        assertTrue(rig, "seed " + seed);
    }
}
