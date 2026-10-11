package io.github.jvmdc.prop;

import com.badlogic.ashley.core.Component;

/** A point light in world voxels. Colour is bright, not a 0–1 paint colour. */
public final class PropLight implements Component {
    public float x;
    public float y;
    public float z;
    public float r;
    public float g;
    public float b;
    public float radius;
}
