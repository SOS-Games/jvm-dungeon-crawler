package io.github.jvmdc.dungeongen.common;

import java.util.Random;

/** One random stream for a whole dungeon. The seed is chosen by the caller and never replaced. */
public final class SeededRandom {
    private final Random random;

    public SeededRandom(long seed) {
        this.random = new Random(seed);
    }

    public int nextInt(int bound) {
        return random.nextInt(bound);
    }

    public int nextInt(int origin, int bound) {
        return random.nextInt(origin, bound);
    }

    public double nextDouble() {
        return random.nextDouble();
    }
}
