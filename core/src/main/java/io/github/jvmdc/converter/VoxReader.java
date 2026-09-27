package io.github.jvmdc.converter;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads a MagicaVoxel file. One file can hold several models; the scene graph
 * places them (armor is a head, chest, and limbs in one .vox). Model index 0
 * is what {@link #read(File)} returns, which is the piece Veloren uses when a
 * manifest does not ask for another index.
 */
public class VoxReader {

    public static class VoxData {
        public int sizeX, sizeY, sizeZ;
        public byte[][][] grid; // 0 = empty, 1-255 = palette index
        public int[] palette = new int[256]; // RGBA8888 packed
    }

    /** One solid model inside a .vox file. Index 0 is the first SIZE chunk. */
    public static class VoxModel {
        public int sizeX, sizeY, sizeZ;
        public byte[][][] grid;
        /** Palette for this model. Parts from different files keep their own colors. */
        public int[] palette;
    }

    /**
     * A placed copy of a model. Rotation is a 3x3 row-major matrix in voxel
     * space (Z up). Translation is the model's center, in voxels.
     */
    public static class VoxInstance {
        public final int model;
        public final float[] rotation;
        public final float[] translation;

        public VoxInstance(int model, float[] rotation, float[] translation) {
            this.model = model;
            this.rotation = rotation;
            this.translation = translation;
        }
    }

    public static class VoxScene {
        public final List<VoxModel> models = new ArrayList<>();
        public final int[] palette = new int[256];
        public final List<VoxInstance> instances = new ArrayList<>();
    }

    public static VoxData read(File file) throws IOException {
        VoxScene scene = readScene(file);
        VoxData data = new VoxData();
        data.palette = scene.palette;
        if (!scene.models.isEmpty()) {
            VoxModel model = scene.models.get(0);
            data.sizeX = model.sizeX;
            data.sizeY = model.sizeY;
            data.sizeZ = model.sizeZ;
            data.grid = model.grid;
        }
        return data;
    }

    public static VoxScene readScene(File file) throws IOException {
        byte[] bytes;
        try (FileInputStream fis = new FileInputStream(file)) {
            bytes = fis.readAllBytes();
        }

        ByteBuffer buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        int magic = buf.getInt();
        if (magic != 0x20584F56) {
            throw new IllegalArgumentException("Not a valid VOX file: " + file.getName());
        }
        buf.getInt(); // version

        String mainId = readChunkId(buf);
        int mainContentSize = buf.getInt();
        int mainChildrenSize = buf.getInt();
        if (!"MAIN".equals(mainId)) {
            throw new IllegalArgumentException("Missing MAIN chunk: " + file.getName());
        }
        buf.position(buf.position() + mainContentSize);

        VoxScene scene = new VoxScene();
        Map<Integer, Node> nodes = new HashMap<>();
        int endPosition = Math.min(buf.limit(), buf.position() + mainChildrenSize);

        while (buf.position() + 12 <= endPosition) {
            String chunkId = readChunkId(buf);
            int contentSize = buf.getInt();
            int childrenSize = buf.getInt();
            int nextChunkPos = Math.min(buf.limit(), buf.position() + contentSize + childrenSize);
            try {
                switch (chunkId) {
                    case "SIZE" -> readSize(buf, scene);
                    case "XYZI" -> readVoxels(buf, scene);
                    case "RGBA" -> readPalette(buf, scene);
                    case "nTRN" -> readTransform(buf, nodes);
                    case "nGRP" -> readGroup(buf, nodes);
                    case "nSHP" -> readShape(buf, nodes);
                    default -> {
                    }
                }
            } finally {
                buf.position(nextChunkPos);
            }
        }

        if (nodes.isEmpty()) {
            layOutModels(scene);
        } else {
            float[] identity = identityRotation();
            float[] origin = new float[3];
            walk(0, identity, origin, nodes, scene);
            if (scene.instances.isEmpty()) {
                layOutModels(scene);
            }
        }
        for (VoxModel model : scene.models) {
            model.palette = scene.palette;
        }
        return scene;
    }

    private static void readSize(ByteBuffer buf, VoxScene scene) {
        VoxModel model = new VoxModel();
        model.sizeX = buf.getInt();
        model.sizeY = buf.getInt();
        model.sizeZ = buf.getInt();
        long cells = (long) model.sizeX * model.sizeY * model.sizeZ;
        if (model.sizeX > 0 && model.sizeY > 0 && model.sizeZ > 0
                && model.sizeX <= 512 && model.sizeY <= 512 && model.sizeZ <= 512
                && cells <= 16_000_000L) {
            model.grid = new byte[model.sizeX][model.sizeY][model.sizeZ];
        }
        scene.models.add(model);
    }

    private static void readVoxels(ByteBuffer buf, VoxScene scene) {
        if (scene.models.isEmpty()) {
            return;
        }
        VoxModel model = scene.models.get(scene.models.size() - 1);
        int numVoxels = buf.getInt();
        for (int i = 0; i < numVoxels; i++) {
            int x = buf.get() & 0xFF;
            int y = buf.get() & 0xFF;
            int z = buf.get() & 0xFF;
            byte colorIndex = buf.get();
            if (model.grid != null && x < model.sizeX && y < model.sizeY && z < model.sizeZ) {
                model.grid[x][y][z] = colorIndex;
            }
        }
    }

    private static void readPalette(ByteBuffer buf, VoxScene scene) {
        for (int i = 0; i < 256 && buf.remaining() >= 4; i++) {
            int r = buf.get() & 0xFF;
            int g = buf.get() & 0xFF;
            int b = buf.get() & 0xFF;
            int a = buf.get() & 0xFF;
            scene.palette[i] = (r << 24) | (g << 16) | (b << 8) | a;
        }
    }

    private static void readTransform(ByteBuffer buf, Map<Integer, Node> nodes) {
        Node node = new Node();
        node.kind = Kind.TRN;
        int id = buf.getInt();
        Map<String, String> attrs = readDict(buf);
        node.hidden = "1".equals(attrs.get("_hidden"));
        node.child = buf.getInt();
        buf.getInt(); // reserved, always -1
        buf.getInt(); // layer
        int frames = buf.getInt();
        node.rotation = identityRotation();
        node.translation = new float[3];
        if (frames > 0) {
            Map<String, String> frame = readDict(buf);
            String packed = frame.get("_r");
            if (packed != null) {
                node.rotation = decodeRotation(parseInt(packed));
            }
            parseTranslation(frame.get("_t"), node.translation);
        }
        nodes.put(id, node);
    }

    private static void readGroup(ByteBuffer buf, Map<Integer, Node> nodes) {
        Node node = new Node();
        node.kind = Kind.GRP;
        int id = buf.getInt();
        Map<String, String> attrs = readDict(buf);
        node.hidden = "1".equals(attrs.get("_hidden"));
        int count = buf.getInt();
        node.children = new int[Math.max(0, count)];
        for (int i = 0; i < node.children.length && buf.remaining() >= 4; i++) {
            node.children[i] = buf.getInt();
        }
        nodes.put(id, node);
    }

    private static void readShape(ByteBuffer buf, Map<Integer, Node> nodes) {
        Node node = new Node();
        node.kind = Kind.SHP;
        int id = buf.getInt();
        Map<String, String> attrs = readDict(buf);
        node.hidden = "1".equals(attrs.get("_hidden"));
        int count = buf.getInt();
        node.models = new int[Math.max(0, count)];
        for (int i = 0; i < node.models.length; i++) {
            node.models[i] = buf.getInt();
            readDict(buf);
        }
        nodes.put(id, node);
    }

    /** Files with no scene graph: one model stays centered, several sit in a row. */
    private static void layOutModels(VoxScene scene) {
        float cursor = 0;
        boolean row = scene.models.size() > 1;
        for (int i = 0; i < scene.models.size(); i++) {
            VoxModel model = scene.models.get(i);
            float[] translation = new float[3];
            if (row) {
                translation[0] = cursor + model.sizeX * 0.5f;
                cursor += model.sizeX + 2;
            }
            scene.instances.add(new VoxInstance(i, identityRotation(), translation));
        }
    }

    private static void walk(int id, float[] parentR, float[] parentT,
                             Map<Integer, Node> nodes, VoxScene scene) {
        Node node = nodes.get(id);
        if (node == null || node.hidden) {
            return;
        }
        switch (node.kind) {
            case TRN -> {
                float[] rotation = multiplyRotation(parentR, node.rotation);
                float[] translation = multiplyPoint(parentR, node.translation, parentT);
                if (node.child >= 0) {
                    walk(node.child, rotation, translation, nodes, scene);
                }
            }
            case GRP -> {
                if (node.children == null) {
                    return;
                }
                for (int child : node.children) {
                    walk(child, parentR, parentT, nodes, scene);
                }
            }
            case SHP -> {
                if (node.models == null) {
                    return;
                }
                for (int model : node.models) {
                    scene.instances.add(new VoxInstance(model, copy(parentR), copy(parentT)));
                }
            }
        }
    }

    /**
     * MagicaVoxel packs a rotation in one byte: which axis each row points
     * along, and a sign bit per row. Value 20 is an X mirror, used for the
     * left-hand copy of a limb.
     */
    static float[] decodeRotation(int packed) {
        int i0 = packed & 3;
        int i1 = (packed >> 2) & 3;
        if (i0 == i1 || i0 > 2 || i1 > 2) {
            return identityRotation();
        }
        int i2 = 3 - i0 - i1;
        if (i2 < 0 || i2 > 2) {
            return identityRotation();
        }
        float[] rotation = new float[9];
        rotation[i0] = (packed & 16) != 0 ? -1f : 1f;
        rotation[3 + i1] = (packed & 32) != 0 ? -1f : 1f;
        rotation[6 + i2] = (packed & 64) != 0 ? -1f : 1f;
        return rotation;
    }

    private static float[] identityRotation() {
        return new float[] {1, 0, 0, 0, 1, 0, 0, 0, 1};
    }

    private static float[] multiplyRotation(float[] a, float[] b) {
        float[] out = new float[9];
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                out[row * 3 + col] =
                        a[row * 3] * b[col]
                                + a[row * 3 + 1] * b[3 + col]
                                + a[row * 3 + 2] * b[6 + col];
            }
        }
        return out;
    }

    private static float[] multiplyPoint(float[] rotation, float[] point, float[] add) {
        float[] out = new float[3];
        out[0] = rotation[0] * point[0] + rotation[1] * point[1] + rotation[2] * point[2] + add[0];
        out[1] = rotation[3] * point[0] + rotation[4] * point[1] + rotation[5] * point[2] + add[1];
        out[2] = rotation[6] * point[0] + rotation[7] * point[1] + rotation[8] * point[2] + add[2];
        return out;
    }

    private static float[] copy(float[] values) {
        float[] out = new float[values.length];
        System.arraycopy(values, 0, out, 0, values.length);
        return out;
    }

    private static void parseTranslation(String text, float[] into) {
        if (text == null || text.isBlank()) {
            return;
        }
        String[] parts = text.trim().split("\\s+");
        if (parts.length < 3) {
            return;
        }
        into[0] = parseInt(parts[0]);
        into[1] = parseInt(parts[1]);
        into[2] = parseInt(parts[2]);
    }

    private static int parseInt(String text) {
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static Map<String, String> readDict(ByteBuffer buf) {
        int count = buf.getInt();
        Map<String, String> dict = new HashMap<>();
        if (count < 0 || count > 256) {
            throw new IllegalArgumentException("bad vox dictionary");
        }
        for (int i = 0; i < count; i++) {
            dict.put(readString(buf), readString(buf));
        }
        return dict;
    }

    private static String readString(ByteBuffer buf) {
        int length = buf.getInt();
        if (length < 0 || length > 4096 || buf.remaining() < length) {
            return "";
        }
        byte[] bytes = new byte[length];
        buf.get(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static String readChunkId(ByteBuffer buf) {
        byte[] b = new byte[4];
        buf.get(b);
        return new String(b, StandardCharsets.US_ASCII);
    }

    private enum Kind { TRN, GRP, SHP }

    private static final class Node {
        Kind kind;
        boolean hidden;
        int child = -1;
        int[] children;
        int[] models;
        float[] rotation;
        float[] translation;
    }
}
