package io.github.jvmdc.viewer;

import com.badlogic.gdx.Gdx;
import io.github.jvmdc.assets.AssetRoots;
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
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Every .vox file under the asset folder, plus the custom voxel folder.
 * A small biped whose parts live in one folder becomes one tile. Those part
 * files stay in the list until that body builds, then they drop out of All parts.
 * Weapons stay listed.
 */
public final class VoxCatalog {
    public static final class Entry {
        public final String name;
        public final String path;
        public final File file;
        public final boolean npc;
        final NpcFigure.Assembly figure;
        public VoxMesher.VoxMesh mesh;
        /** Animals idle in the large view. Null for a still figure. */
        public FigureAnim rig;
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
        Scan scan = new Scan();
        while (!scan.pump(-1)) {
            // Runs the whole scan on this thread. The viewer pumps it across frames instead.
        }
        return scan.catalog();
    }

    /**
     * Asset scan that can stop and resume. Mesh uploads stay on the calling thread.
     * pump returns true when the catalog is finished.
     */
    public static final class Scan {
        private enum Phase { LIST, FIGURES, BUILD, DONE }

        private final VoxCatalog catalog = new VoxCatalog();
        private Phase phase = Phase.LIST;
        private Path root;
        private Stream<Path> walk;
        private Iterator<Path> files;
        private List<NpcFigure.Assembly> figures = List.of();
        private int figureIndex;
        private Set<String> builtParts = new HashSet<>();
        private int armed;
        private boolean customListed;

        public VoxCatalog catalog() {
            return catalog;
        }

        /** Does a slice of work. A negative budget runs until the scan is finished. */
        public boolean pump(long budgetNs) {
            long start = System.nanoTime();
            try {
                while (phase != Phase.DONE && (budgetNs < 0 || System.nanoTime() - start < budgetNs)) {
                    step();
                }
            } catch (IOException e) {
                catalog.error = phase == Phase.FIGURES
                        ? "could not read npc json: " + e.getMessage()
                        : "could not list assets: " + e.getMessage();
                closeWalk();
                phase = Phase.DONE;
            }
            return phase == Phase.DONE;
        }

        /** Drops a scan that was stopped early, including any meshes already built. */
        public void dispose() {
            closeWalk();
            catalog.dispose();
            phase = Phase.DONE;
        }

        private void step() throws IOException {
            switch (phase) {
                case LIST -> listOne();
                case FIGURES -> {
                    figures = NpcFigure.find(root);
                    phase = Phase.BUILD;
                }
                case BUILD -> buildOne();
                case DONE -> {
                }
            }
        }

        private void listOne() throws IOException {
            if (root == null) {
                Properties properties = Converter.getLocalPropeties();
                String velorenPath = properties.getProperty("velorenPath");
                if (velorenPath == null || velorenPath.isBlank()) {
                    catalog.error = "velorenPath is missing from local.properties";
                    phase = Phase.DONE;
                    return;
                }
                root = Path.of(velorenPath, "assets");
                if (!Files.isDirectory(root)) {
                    catalog.error = "assets folder not found: " + root;
                    phase = Phase.DONE;
                    return;
                }
                walk = Files.walk(root);
                files = walk.filter(path -> path.getFileName().toString().toLowerCase().endsWith(".vox"))
                        .iterator();
                return;
            }
            if (!files.hasNext()) {
                closeWalk();
                if (!customListed) {
                    customListed = true;
                    catalog.entries.addAll(listFolder(AssetRoots.locate().custom, "assets/custom/"));
                }
                phase = Phase.FIGURES;
                return;
            }
            Path file = files.next();
            String path = relative(root, file);
            String filename = file.getFileName().toString();
            String name = filename.toLowerCase().endsWith(".vox")
                    ? filename.substring(0, filename.length() - 4)
                    : filename;
            catalog.entries.add(new Entry(name, path, file.toFile()));
        }

        private void buildOne() {
            if (figureIndex >= figures.size()) {
                catalog.entries.removeIf(entry -> !entry.npc && builtParts.contains(entry.path));
                catalog.entries.sort(Comparator.comparing(entry -> entry.path));
                Gdx.app.log("JvmDc", catalog.entries.size() + " tiles, " + figures.size()
                        + " assembled npcs, " + armed + " weapons");
                phase = Phase.DONE;
                return;
            }
            NpcFigure.Assembly figure = figures.get(figureIndex++);
            Entry entry = new Entry(figure.name, figure.path, null, figure);
            try {
                entry.mesh = NpcFigure.build(figure);
                entry.rig = loadRig(figure);
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

        private void closeWalk() {
            if (walk != null) {
                walk.close();
                walk = null;
                files = null;
            }
        }
    }

    public void load(Entry entry) {
        if (entry.mesh != null || entry.failed) {
            return;
        }
        try {
            if (entry.figure != null) {
                entry.mesh = NpcFigure.build(entry.figure);
                entry.rig = loadRig(entry.figure);
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

    /** Idle is extra. A bad pose leaves the still mesh. */
    private static FigureAnim loadRig(NpcFigure.Assembly figure) {
        if (figure == null || figure.body == null) {
            return null;
        }
        try {
            if ("bird_medium".equals(figure.body)) {
                return BirdIdle.load(figure);
            }
            return AnimalIdle.load(figure);
        } catch (RuntimeException | IOException e) {
            Gdx.app.error("JvmDc", "idle " + figure.path, e);
            return null;
        }
    }

    public void dispose() {
        for (Entry entry : entries) {
            if (entry.mesh != null) {
                entry.mesh.dispose();
                entry.mesh = null;
            }
            if (entry.rig != null) {
                entry.rig.dispose();
                entry.rig = null;
            }
        }
    }

    /** .vox files under a folder. The prefix is put in front of each relative path. */
    static List<Entry> listFolder(Path folder, String prefix) throws IOException {
        List<Entry> found = new ArrayList<>();
        if (folder == null || !Files.isDirectory(folder)) {
            return found;
        }
        try (Stream<Path> walk = Files.walk(folder)) {
            for (Path file : walk.filter(path -> path.getFileName().toString().toLowerCase().endsWith(".vox")).toList()) {
                String filename = file.getFileName().toString();
                String name = filename.substring(0, filename.length() - 4);
                String rel = folder.relativize(file).toString().replace('\\', '/');
                found.add(new Entry(name, prefix + rel, file.toFile()));
            }
        }
        return found;
    }

    private static String relative(Path root, Path file) {
        return "assets/" + root.relativize(file).toString().replace('\\', '/');
    }
}
