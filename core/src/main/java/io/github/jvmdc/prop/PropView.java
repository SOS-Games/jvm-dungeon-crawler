package io.github.jvmdc.prop;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.math.Matrix4;
import io.github.jvmdc.assets.ModelPaths;
import io.github.jvmdc.converter.VoxMesher;
import io.github.jvmdc.converter.VoxReader;
import io.github.jvmdc.render.ForwardRenderer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Loads each prop voxel once and draws the posed parts. Also uploads nearby point lights. */
public final class PropView {
    private static final int MAX_LIGHTS = 32;
    private static final ComponentMapper<PropTransform> TRANSFORM = ComponentMapper.getFor(PropTransform.class);
    private static final ComponentMapper<PropModel> MODEL = ComponentMapper.getFor(PropModel.class);
    private static final ComponentMapper<PropLight> LIGHT = ComponentMapper.getFor(PropLight.class);

    private final ModelPaths paths;
    private final Map<String, VoxMesher.VoxMesh> meshes = new HashMap<>();
    private final float[] lightPos = new float[MAX_LIGHTS * 3];
    private final float[] lightColor = new float[MAX_LIGHTS * 3];
    private final float[] lightRadius = new float[MAX_LIGHTS];
    private final List<PropLight> nearest = new ArrayList<>();

    public PropView(ModelPaths paths) {
        this.paths = paths;
    }

    public void draw(Engine engine, ForwardRenderer renderer, Camera camera, Matrix4 model) {
        if (engine == null) {
            return;
        }
        ImmutableArray<Entity> entities = engine.getEntitiesFor(Family.all(PropTransform.class, PropModel.class).get());
        for (Entity entity : entities) {
            PropTransform transform = TRANSFORM.get(entity);
            PropModel prop = MODEL.get(entity);
            for (PropModel.Part part : prop.parts) {
                VoxMesher.VoxMesh mesh = mesh(part.mesh);
                if (mesh == null || mesh.parts.length == 0) {
                    continue;
                }
                model.idt();
                model.translate(transform.x, transform.y, transform.z);
                model.rotate(0f, 1f, 0f, transform.yaw);
                model.translate(part.x, part.y + (part.sit ? lift(mesh) : 0f), part.z);
                if (part.rotates) {
                    model.translate(part.pivotX, part.pivotY, part.pivotZ);
                    model.rotate(part.axisX, part.axisY, part.axisZ, part.angle);
                    model.translate(-part.pivotX, -part.pivotY, -part.pivotZ);
                }
                renderer.draw(camera, mesh.parts, model);
            }
        }
    }

    /** The closest lights to the camera. A top-down view measures across the floor. */
    public void uploadLights(Engine engine, Camera camera, boolean overhead, ForwardRenderer renderer) {
        nearest.clear();
        if (engine == null) {
            renderer.setLights(lightPos, lightColor, lightRadius, 0);
            return;
        }
        float cx = camera.position.x;
        float cy = overhead ? 20f : camera.position.y;
        float cz = camera.position.z;
        ImmutableArray<Entity> entities = engine.getEntitiesFor(Family.all(PropLight.class).get());
        for (Entity entity : entities) {
            nearest.add(LIGHT.get(entity));
        }
        nearest.sort(Comparator.comparingDouble(light -> {
            float dx = light.x - cx;
            float dy = light.y - cy;
            float dz = light.z - cz;
            return dx * dx + dy * dy + dz * dz;
        }));
        int count = Math.min(MAX_LIGHTS, nearest.size());
        for (int i = 0; i < count; i++) {
            PropLight light = nearest.get(i);
            lightPos[i * 3] = light.x;
            lightPos[i * 3 + 1] = light.y;
            lightPos[i * 3 + 2] = light.z;
            lightColor[i * 3] = light.r;
            lightColor[i * 3 + 1] = light.g;
            lightColor[i * 3 + 2] = light.b;
            lightRadius[i] = light.radius;
        }
        renderer.setLights(lightPos, lightColor, lightRadius, count);
    }

    public void dispose() {
        for (VoxMesher.VoxMesh mesh : meshes.values()) {
            if (mesh != null) {
                mesh.dispose();
            }
        }
        meshes.clear();
    }

    private VoxMesher.VoxMesh mesh(String specifier) {
        if (specifier == null || specifier.isEmpty()) {
            return null;
        }
        if (meshes.containsKey(specifier)) {
            return meshes.get(specifier);
        }
        VoxMesher.VoxMesh mesh = null;
        try {
            var file = paths.find(specifier);
            if (file.isPresent()) {
                mesh = VoxMesher.build(VoxReader.readScene(file.get().toFile()));
            }
        } catch (RuntimeException | java.io.IOException ignored) {
            mesh = null;
        }
        meshes.put(specifier, mesh);
        return mesh;
    }

    /** Centred meshes sit with their bottom on the prop origin. */
    private static float lift(VoxMesher.VoxMesh mesh) {
        return (mesh.maxY - mesh.minY) * 0.5f;
    }
}
