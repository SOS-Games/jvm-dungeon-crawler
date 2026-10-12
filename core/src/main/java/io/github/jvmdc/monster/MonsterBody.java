package io.github.jvmdc.monster;

import com.badlogic.ashley.core.Component;

/** Which body this spawn rolled, and how big it is drawn. */
public final class MonsterBody implements Component {
    public String id = "";
    public String species = "";
    public String body = "";
    public float scale = 1f;
    public int sourceX;
    public int sourceY;
}
