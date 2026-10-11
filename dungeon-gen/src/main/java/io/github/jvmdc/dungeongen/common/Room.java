package io.github.jvmdc.dungeongen.common;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** A carved rectangle. Center uses integer division, so a 3×3 room sits on its middle tile. */
public final class Room {
    public final int id;
    public final int x;
    public final int y;
    public final int width;
    public final int height;
    public final int centerX;
    public final int centerY;
    public final boolean secret;
    public final boolean locked;
    /** Interior rectangles. A plain room has one. An L, T, or plus has several. */
    private final int[][] parts;
    private final List<Integer> corridorIds = new ArrayList<>();

    public Room(int id, int x, int y, int width, int height) {
        this(id, x, y, width, height, false, false);
    }

    public Room(int id, int x, int y, int width, int height, boolean secret, boolean locked) {
        this.id = id;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.centerX = x + width / 2;
        this.centerY = y + height / 2;
        this.secret = secret;
        this.locked = locked;
        this.parts = new int[][] {{x, y, width, height}};
    }

    public Room(int id, int[][] parts, boolean secret, boolean locked) {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        this.parts = new int[parts.length][4];
        for (int i = 0; i < parts.length; i++) {
            this.parts[i][0] = parts[i][0];
            this.parts[i][1] = parts[i][1];
            this.parts[i][2] = parts[i][2];
            this.parts[i][3] = parts[i][3];
            minX = Math.min(minX, parts[i][0]);
            minY = Math.min(minY, parts[i][1]);
            maxX = Math.max(maxX, parts[i][0] + parts[i][2]);
            maxY = Math.max(maxY, parts[i][1] + parts[i][3]);
        }
        this.id = id;
        this.x = minX;
        this.y = minY;
        this.width = maxX - minX;
        this.height = maxY - minY;
        this.centerX = x + width / 2;
        this.centerY = y + height / 2;
        this.secret = secret;
        this.locked = locked;
    }

    public int[][] parts() {
        int[][] copy = new int[parts.length][4];
        for (int i = 0; i < parts.length; i++) {
            copy[i] = parts[i].clone();
        }
        return copy;
    }

    public List<Integer> corridorIds() {
        return Collections.unmodifiableList(corridorIds);
    }

    public void addCorridor(int corridorId) {
        corridorIds.add(corridorId);
    }

    public boolean contains(int cellX, int cellY) {
        for (int[] part : parts) {
            if (cellX >= part[0] && cellY >= part[1] && cellX < part[0] + part[2] && cellY < part[1] + part[3]) {
                return true;
            }
        }
        return false;
    }
}
