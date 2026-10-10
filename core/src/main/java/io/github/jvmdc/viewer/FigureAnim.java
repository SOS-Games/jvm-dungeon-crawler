package io.github.jvmdc.viewer;

import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.math.Matrix4;
import io.github.jvmdc.render.ForwardRenderer;

/** A creature that moves in the large view. The tile keeps the still mesh. */
public interface FigureAnim {
    void tick(float dt);

    void draw(ForwardRenderer renderer, PerspectiveCamera camera, Matrix4 view);

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
