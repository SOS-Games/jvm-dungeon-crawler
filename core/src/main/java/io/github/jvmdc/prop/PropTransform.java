package io.github.jvmdc.prop;

import com.badlogic.ashley.core.Component;

/** World position of a prop. Y is up. Yaw turns it on the floor. */
public final class PropTransform implements Component {
    public float x;
    public float y;
    public float z;
    public float yaw;
    public int tileX;
    public int tileY;
}
