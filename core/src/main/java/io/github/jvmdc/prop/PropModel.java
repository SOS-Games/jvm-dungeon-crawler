package io.github.jvmdc.prop;

import com.badlogic.ashley.core.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Meshes for one prop. A single model is one part. A rig lists each piece at
 * its pivot so a joint can turn later. Nothing here plays an animation.
 */
public final class PropModel implements Component {
    public String id = "";
    public final List<Part> parts = new ArrayList<>();

    public static final class Part {
        /** Dotted voxel name, or a custom model id that resolves to one. */
        public String mesh = "";
        public float x;
        public float y;
        public float z;
        public float pivotX;
        public float pivotY;
        public float pivotZ;
        public float axisX;
        public float axisY = 1f;
        public float axisZ;
        /** Resting joint angle, in degrees. */
        public float angle;
        public boolean rotates;
        /** Raise a centred mesh so its bottom sits on the prop origin. */
        public boolean sit;
    }
}
