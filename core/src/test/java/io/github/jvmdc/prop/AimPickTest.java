package io.github.jvmdc.prop;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector3;
import io.github.jvmdc.dungeongen.common.Dungeon;
import io.github.jvmdc.dungeongen.common.Tile;
import io.github.jvmdc.dungeongen.common.TileGrid;
import io.github.jvmdc.monster.MonsterBody;
import io.github.jvmdc.world.BlockChunks;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AimPickTest {
    @Test
    void namesTheSurfaceAndTheThingInFront() {
        TileGrid grid = new TileGrid(4, 2);
        for (int y = 0; y < grid.height; y++) {
            for (int x = 0; x < grid.width; x++) {
                grid.set(x, y, Tile.ROOM_FLOOR);
            }
        }
        grid.set(3, 0, Tile.WALL);
        grid.set(0, 1, Tile.WATER);
        Dungeon dungeon = new Dungeon(1L, null, grid, List.of(), List.of(), null, -1, -1);
        float tile = BlockChunks.VOXELS_PER_TILE;
        Vector3 down = new Vector3(0f, -1f, 0f);
        Vector3 floor = new Vector3(tile * 0.5f, 20f, -tile * 0.5f);
        assertEquals("Floor", AimPick.find(dungeon, null, floor, down, true));
        assertEquals("Ceiling", AimPick.find(dungeon, null, floor, new Vector3(0f, 1f, 0f), true));
        assertEquals("", AimPick.find(dungeon, null, floor, new Vector3(0f, 1f, 0f), false));

        Vector3 water = new Vector3(tile * 0.5f, 20f, -(tile + tile * 0.5f));
        assertEquals("Water", AimPick.find(dungeon, null, water, down, false));

        Vector3 eye = new Vector3(tile * 0.5f, 12f, -tile * 0.5f);
        assertEquals("Wall", AimPick.find(dungeon, null, eye, new Vector3(1f, 0f, 0f), false));

        Engine engine = new Engine();
        Entity chest = engine.createEntity();
        PropTransform transform = new PropTransform();
        transform.x = tile * 1.5f;
        transform.y = BlockChunks.FLOOR;
        transform.z = -tile * 0.5f;
        PropCollider box = new PropCollider();
        box.halfX = 6f;
        box.halfY = 5f;
        box.halfZ = 6f;
        PropModel model = new PropModel();
        model.id = "chest";
        chest.add(transform);
        chest.add(box);
        chest.add(model);
        engine.addEntity(chest);
        Vector3 atChest = new Vector3(transform.x, 20f, transform.z);
        assertEquals("Chest", AimPick.find(dungeon, engine, atChest, down, false));

        Entity boar = engine.createEntity();
        PropTransform body = new PropTransform();
        body.x = tile * 0.5f;
        body.y = BlockChunks.FLOOR;
        body.z = -(tile + tile * 0.5f);
        body.yaw = 90f;
        PropCollider hide = new PropCollider();
        hide.halfX = 8f;
        hide.halfY = 6f;
        hide.halfZ = 4f;
        MonsterBody monster = new MonsterBody();
        monster.id = "boar";
        boar.add(body);
        boar.add(hide);
        boar.add(monster);
        engine.addEntity(boar);
        Vector3 side = new Vector3(body.x - 20f, body.y + 6f, body.z);
        assertEquals("Boar", AimPick.find(dungeon, engine, side, new Vector3(1f, 0f, 0f), false));
    }
}
