package io.github.jvmdc.monster;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.math.Matrix4;
import io.github.jvmdc.prop.PropCollider;
import io.github.jvmdc.prop.PropTransform;
import io.github.jvmdc.render.ForwardRenderer;
import io.github.jvmdc.viewer.FigureAnim;
import io.github.jvmdc.viewer.FigureLibrary;

import java.util.HashMap;
import java.util.Map;

/** Draws spawned bodies with the same lights already uploaded for props. */
public final class MonsterView {
    private static final ComponentMapper<PropTransform> TRANSFORM = ComponentMapper.getFor(PropTransform.class);
    private static final ComponentMapper<MonsterBody> BODY = ComponentMapper.getFor(MonsterBody.class);
    private static final ComponentMapper<PropCollider> COLLIDER = ComponentMapper.getFor(PropCollider.class);

    private final Map<String, FigureAnim> rigs = new HashMap<>();
    private final Map<String, float[]> bounds = new HashMap<>();
    private final Matrix4 model = new Matrix4();
    private float requestedScale = 1f;

    public void setScale(float scale) {
        requestedScale = scale;
    }

    public void attach(Engine engine) {
        if (engine == null) {
            return;
        }
        ImmutableArray<Entity> entities = engine.getEntitiesFor(Family.all(PropTransform.class, MonsterBody.class).get());
        for (Entity entity : entities) {
            MonsterBody body = BODY.get(entity);
            FigureAnim rig = rig(body);
            if (rig == null) {
                continue;
            }
            float[] box = bounds.get(body.id);
            float scale = MonsterFit.scaleToTile(requestedScale, box);
            body.scale = scale;
            PropTransform transform = TRANSFORM.get(entity);
            transform.y = MonsterFit.footY(scale, box[1]);
            PropCollider collider = COLLIDER.get(entity);
            if (collider != null) {
                collider.halfX = Math.max(1f, (box[3] - box[0]) * 0.5f * scale);
                collider.halfY = Math.max(1f, (box[4] - box[1]) * 0.5f * scale);
                collider.halfZ = Math.max(1f, (box[5] - box[2]) * 0.5f * scale);
            }
        }
    }

    public void tick(float dt) {
        for (FigureAnim rig : rigs.values()) {
            rig.tick(dt);
        }
    }

    public void draw(Engine engine, ForwardRenderer renderer, Camera camera) {
        if (engine == null) {
            return;
        }
        ImmutableArray<Entity> entities = engine.getEntitiesFor(Family.all(PropTransform.class, MonsterBody.class).get());
        for (Entity entity : entities) {
            MonsterBody body = BODY.get(entity);
            FigureAnim rig = rigs.get(body.id);
            if (rig == null) {
                continue;
            }
            PropTransform transform = TRANSFORM.get(entity);
            model.idt();
            model.translate(transform.x, transform.y, transform.z);
            model.rotate(0f, 1f, 0f, transform.yaw);
            model.scale(body.scale, body.scale, body.scale);
            rig.draw(renderer, camera, model);
        }
    }

    public int clip(String id) {
        FigureAnim rig = rigs.get(id);
        return rig == null ? -1 : rig.clip();
    }

    public String clipName(String id) {
        FigureAnim rig = rigs.get(id);
        if (rig == null) {
            return "";
        }
        return rig.clipName(rig.clip());
    }

    public void dispose() {
        for (FigureAnim rig : rigs.values()) {
            rig.dispose();
        }
        rigs.clear();
        bounds.clear();
    }

    private FigureAnim rig(MonsterBody body) {
        if (rigs.containsKey(body.id)) {
            return rigs.get(body.id);
        }
        FigureAnim rig;
        try {
            rig = FigureLibrary.load(body.species, body.body);
        } catch (Exception e) {
            rig = null;
        }
        rigs.put(body.id, rig);
        if (rig != null) {
            bounds.put(body.id, rig.stoodBounds());
        }
        return rig;
    }
}
