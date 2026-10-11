package io.github.jvmdc.prop;

import com.badlogic.ashley.core.Component;

/** What the player can do with a prop. An empty verb means it only sits there. */
public final class PropInteractable implements Component {
    public String verb = "";
    public String state = "";
}
