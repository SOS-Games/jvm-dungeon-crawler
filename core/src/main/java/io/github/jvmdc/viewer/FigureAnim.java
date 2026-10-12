package io.github.jvmdc.viewer;

import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.math.Matrix4;
import io.github.jvmdc.render.ForwardRenderer;

/** A creature that moves in the large view. The tile keeps the still mesh. */
public interface FigureAnim {
    void tick(float dt);

    void draw(ForwardRenderer renderer, Camera camera, Matrix4 view);

    /**
     * Stood-up bounds in figure voxels: minX, minY, minZ, maxX, maxY, maxZ.
     * Y is up. The body is centered on X and Z.
     */
    default float[] stoodBounds() {
        return new float[] {0f, 0f, 0f, 0f, 0f, 0f};
    }

    void dispose();

    /** How many clips this figure can play. Idle is always the first. */
    default int clipCount() {
        return 1;
    }

    /** Name shown on the clip button. */
    default String clipName(int index) {
        return "Idle";
    }

    default void setClip(int index) {
    }

    default int clip() {
        return 0;
    }
}
