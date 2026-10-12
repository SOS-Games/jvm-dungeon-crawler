package io.github.jvmdc.prop;

import io.github.jvmdc.assets.AssetRoots;
import io.github.jvmdc.assets.ModelPaths;
import io.github.jvmdc.converter.VoxReader;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RigOverlapTest {
    @Test
    void spikeShaftsLeaveTheBaseAndTheFloor() throws Exception {
        List<RigParts.Piece> pieces = cut("trap.spikes");
        assertTrue(count(pieces.get(0)) > 0);
        assertTrue(count(pieces.get(1)) > 0);
        assertNoSharedCells(pieces.get(0), pieces.get(1));
        assertTrue(minZ(pieces.get(1)) >= 0);
    }

    @Test
    void hatchDoorLeavesTheFrameAndTheLadderStays() throws Exception {
        List<RigParts.Piece> pieces = cut("placement.floor_hatch");
        assertTrue(count(pieces.get(1)) > 0, "door");
        assertNoSharedCells(pieces.get(0), pieces.get(1));
        assertTrue(minZ(pieces.get(2)) < 0, "ladder");
        assertTrue(count(pieces.get(2)) > 0);
    }

    private static List<RigParts.Piece> cut(String rigId) throws Exception {
        PropCatalog catalog = PropCatalog.load(AssetRoots.locate());
        PropCatalog.RigDef rig = catalog.rigs.get(rigId);
        ModelPaths paths = ModelPaths.locate();
        List<VoxReader.VoxModel> models = new ArrayList<>();
        int[][] corner = new int[rig.parts.size()][3];
        for (int i = 0; i < rig.parts.size(); i++) {
            PropCatalog.RigPart source = rig.parts.get(i);
            PropCatalog.ModelDef solid = catalog.models.get(source.model);
            String specifier = catalog.vox(source.model);
            VoxReader.VoxScene scene = VoxReader.readScene(paths.find(specifier).orElseThrow().toFile());
            VoxReader.VoxModel model = scene.models.get(0);
            model.palette = scene.palette;
            models.add(model);
            float jx = 0f;
            float jy = 0f;
            float jz = 0f;
            float[] pose = source.states.get("retracted");
            if (pose == null) {
                pose = source.states.get("closed");
            }
            if (pose != null && pose.length >= 3 && "translate".equals(source.joint)) {
                jx = pose[0];
                jy = pose[1];
                jz = pose[2];
            }
            corner[i][0] = Math.round(source.ax + solid.ox + jx);
            corner[i][1] = Math.round(source.ay + solid.oy + jy);
            corner[i][2] = Math.round(source.az + solid.oz + jz);
        }
        return RigParts.cut(models, corner);
    }

    private static void assertNoSharedCells(RigParts.Piece earlier, RigParts.Piece later) {
        VoxReader.VoxModel model = later.model;
        for (int x = 0; x < model.sizeX; x++) {
            for (int y = 0; y < model.sizeY; y++) {
                for (int z = 0; z < model.sizeZ; z++) {
                    if (model.grid[x][y][z] == 0) {
                        continue;
                    }
                    assertTrue(!filled(earlier, later.x + x, later.y + y, later.z + z));
                }
            }
        }
    }

    private static boolean filled(RigParts.Piece piece, int px, int py, int pz) {
        int x = px - piece.x;
        int y = py - piece.y;
        int z = pz - piece.z;
        VoxReader.VoxModel model = piece.model;
        if (x < 0 || y < 0 || z < 0 || x >= model.sizeX || y >= model.sizeY || z >= model.sizeZ) {
            return false;
        }
        return model.grid[x][y][z] != 0;
    }

    private static int count(RigParts.Piece piece) {
        int n = 0;
        VoxReader.VoxModel model = piece.model;
        for (int x = 0; x < model.sizeX; x++) {
            for (int y = 0; y < model.sizeY; y++) {
                for (int z = 0; z < model.sizeZ; z++) {
                    if (model.grid[x][y][z] != 0) {
                        n++;
                    }
                }
            }
        }
        return n;
    }

    private static int minZ(RigParts.Piece piece) {
        int min = Integer.MAX_VALUE;
        VoxReader.VoxModel model = piece.model;
        for (int x = 0; x < model.sizeX; x++) {
            for (int y = 0; y < model.sizeY; y++) {
                for (int z = 0; z < model.sizeZ; z++) {
                    if (model.grid[x][y][z] != 0) {
                        min = Math.min(min, piece.z + z);
                    }
                }
            }
        }
        return min;
    }
}
