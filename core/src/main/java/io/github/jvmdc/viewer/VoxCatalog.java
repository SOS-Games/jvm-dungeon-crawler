package io.github.jvmdc.viewer;

import com.badlogic.gdx.Gdx;
import io.github.jvmdc.converter.Converter;
import io.github.jvmdc.converter.VoxMesher;
import io.github.jvmdc.converter.VoxReader;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Every .vox file under the Veloren assets folder. A small biped whose parts
 * live in one folder becomes one tile. Those part files stay in the list until
 * that body builds, then they drop out of All parts. Weapons stay listed.
 */
public final class VoxCatalog {
    public static final class Entry {
        public final String name;
        public final String path;
        public final File file;
        public final boolean npc;
        final NpcFigure.Assembly figure;
        public VoxMesher.VoxMesh mesh;
        public boolean failed;
        public String error;

        Entry(String name, String path, File file) {
            this(name, path, file, null);
        }

        Entry(String name, String path, File file, NpcFigure.Assembly figure) {
            this.name = name;
            this.path = path;
            this.file = file;
            this.figure = figure;
            this.npc = figure != null;
        }
    }

    public final List<Entry> entries = new ArrayList<>();
    public String error;

    public static VoxCatalog scan() {
        VoxCatalog catalog = new VoxCatalog();
        Properties properties = Converter.getLocalPropeties();
        String velorenPath = properties.getProperty("velorenPath");
        if (velorenPath == null || velorenPath.isBlank()) {
            catalog.error = "velorenPath is missing from local.properties";
            return catalog;
        }
        Path root = Path.of(velorenPath, "assets");
        if (!Files.isDirectory(root)) {
            catalog.error = "assets folder not found: " + root;
            return catalog;
        }
        List<Path> files = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(root)) {
            walk.filter(path -> path.getFileName().toString().toLowerCase().endsWith(".vox"))
                    .forEach(files::add);
        } catch (IOException e) {
            catalog.error = "could not list assets: " + e.getMessage();
            return catalog;
        }
        List<NpcFigure.Assembly> figures;
        try {
            figures = NpcFigure.find(root);
        } catch (IOException e) {
            catalog.error = "could not read npc json: " + e.getMessage();
            return catalog;
        }
        for (Path file : files) {
            String path = relative(root, file);
            String filename = file.getFileName().toString();
            String name = filename.toLowerCase().endsWith(".vox")
                    ? filename.substring(0, filename.length() - 4)
                    : filename;
            catalog.entries.add(new Entry(name, path, file.toFile()));
        }
        Set<String> builtParts = new HashSet<>();
        int armed = 0;
        for (NpcFigure.Assembly figure : figures) {
            Entry entry = new Entry(figure.name, figure.path, null, figure);
            try {
                entry.mesh = NpcFigure.build(figure);
                for (NpcFigure.Part part : figure.parts) {
                if (part.tool != null) {
                    armed++;
                    continue;
                }
                    builtParts.add(relative(root, part.file));
                }
            } catch (RuntimeException | IOException e) {
                entry.failed = true;
                entry.error = e.getMessage() == null ? e.toString() : e.getMessage();
                Gdx.app.error("JvmDc", "failed " + entry.path, e);
            }
            catalog.entries.add(entry);
        }
        catalog.entries.removeIf(entry -> !entry.npc && builtParts.contains(entry.path));
        catalog.entries.sort(Comparator.comparing(entry -> entry.path));
        Gdx.app.log("JvmDc", catalog.entries.size() + " tiles, " + figures.size()
                + " assembled npcs, " + armed + " weapons");
        return catalog;
    }

    public void load(Entry entry) {
        if (entry.mesh != null || entry.failed) {
            return;
        }
        try {
            if (entry.figure != null) {
                entry.mesh = NpcFigure.build(entry.figure);
            } else {
                VoxReader.VoxScene scene = VoxReader.readScene(entry.file);
                entry.mesh = VoxMesher.build(scene);
            }
        } catch (RuntimeException | IOException e) {
            entry.failed = true;
            entry.error = e.getMessage() == null ? e.toString() : e.getMessage();
            Gdx.app.error("JvmDc", "failed " + entry.path, e);
        }
    }

    public void dispose() {
        for (Entry entry : entries) {
            if (entry.mesh != null) {
                entry.mesh.dispose();
                entry.mesh = null;
            }
        }
    }

    private static String relative(Path root, Path file) {
        return "assets/" + root.relativize(file).toString().replace('\\', '/');
    }
}
