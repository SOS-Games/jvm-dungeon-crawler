package io.github.jvmdc.prop;

import com.badlogic.ashley.core.Component;

/** Box around a prop, in voxels. The physics world is not built yet. */
public final class PropCollider implements Component {
    public float halfX = 5.5f;
    public float halfY = 8f;
    public float halfZ = 5.5f;
    public boolean blocksMovement;
    public boolean blocksSight;
}
