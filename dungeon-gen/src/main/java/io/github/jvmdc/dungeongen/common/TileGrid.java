package io.github.jvmdc.dungeongen.common;

/** Row-major grid. Index of (x, y) is y * width + x. y grows downward. */
public final class TileGrid {
    public final int width;
    public final int height;
    private final byte[] cells;

    public TileGrid(int width, int height) {
        this.width = width;
        this.height = height;
        this.cells = new byte[width * height];
    }

    public int index(int x, int y) {
        return y * width + x;
    }

    public int x(int index) {
        return index % width;
    }

    public int y(int index) {
        return index / width;
    }

    public boolean inBounds(int x, int y) {
        return x >= 0 && y >= 0 && x < width && y < height;
    }

    public Tile get(int x, int y) {
        return Tile.from(cells[index(x, y)]);
    }

    public Tile get(int index) {
        return Tile.from(cells[index]);
    }

    public void set(int x, int y, Tile tile) {
        cells[index(x, y)] = tile.code;
    }

    public void set(int index, Tile tile) {
        cells[index] = tile.code;
    }
}
