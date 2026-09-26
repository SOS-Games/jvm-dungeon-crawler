package io.github.jvmdc.converter;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class VoxReader {

    public static class VoxData {
        public int sizeX, sizeY, sizeZ;
        public byte[][][] grid; // 0 = empty, 1-255 = palette index
        public int[] palette = new int[256]; // RGBA8888 packed
    }

    public static VoxData read(File file) throws IOException {
        byte[] bytes;
        try (FileInputStream fis = new FileInputStream(file)) {
            bytes = fis.readAllBytes();
        }

        ByteBuffer buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);

        // 1. Verify Magic
        int magic = buf.getInt();
        if (magic != 0x20584F56) { // "VOX " in little-endian
            throw new IllegalArgumentException("Not a valid VOX file: " + file.getName());
        }
        int version = buf.getInt();

        VoxData data = new VoxData();

        // 2. Read MAIN chunk
        String mainId = readChunkId(buf);
        int mainContentSize = buf.getInt();
        int mainChildrenSize = buf.getInt();

        // Skip MAIN payload (usually 0 bytes)
        buf.position(buf.position() + mainContentSize);

        int endPosition = buf.position() + mainChildrenSize;

        // 3. Process sub-chunks
        while (buf.position() < endPosition && buf.hasRemaining()) {
            String chunkId = readChunkId(buf);
            int contentSize = buf.getInt();
            int childrenSize = buf.getInt();

            int nextChunkPos = buf.position() + contentSize + childrenSize;

            switch (chunkId) {
                case "SIZE":
                    data.sizeX = buf.getInt();
                    data.sizeY = buf.getInt();
                    data.sizeZ = buf.getInt();
                    data.grid = new byte[data.sizeX][data.sizeY][data.sizeZ];
                    break;

                case "XYZI":
                    int numVoxels = buf.getInt();
                    for (int i = 0; i < numVoxels; i++) {
                        int x = buf.get() & 0xFF;
                        int y = buf.get() & 0xFF;
                        int z = buf.get() & 0xFF;
                        byte colorIndex = buf.get();
                        if (data.grid != null && x < data.sizeX && y < data.sizeY && z < data.sizeZ) {
                            data.grid[x][y][z] = colorIndex;
                        }
                    }
                    break;

                case "RGBA":
                    // 256 colors * 4 bytes (RGBA)
                    for (int i = 0; i < 256; i++) {
                        int r = buf.get() & 0xFF;
                        int g = buf.get() & 0xFF;
                        int b = buf.get() & 0xFF;
                        int a = buf.get() & 0xFF;
                        // Pack into RGBA8888 integer
                        data.palette[i] = (r << 24) | (g << 16) | (b << 8) | a;
                    }
                    break;

                default:
                    // Skip unsupported chunks like nTRN, nGRP, MATL, etc.
                    break;
            }

            buf.position(nextChunkPos);
        }

        return data;
    }

    private static String readChunkId(ByteBuffer buf) {
        byte[] b = new byte[4];
        buf.get(b);
        return new String(b);
    }
}