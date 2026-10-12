package io.github.jvmdc;

import com.badlogic.ashley.core.Engine;
import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Align;
import io.github.jvmdc.debug.LoadTrace;
import io.github.jvmdc.dungeongen.LayoutConfig;
import io.github.jvmdc.dungeongen.place.PlacementBudget;
import io.github.jvmdc.monster.MonsterSpawner;
import io.github.jvmdc.monster.MonsterTable;
import io.github.jvmdc.monster.MonsterView;
import io.github.jvmdc.dungeongen.Layouts;
import io.github.jvmdc.dungeongen.common.Dungeon;
import io.github.jvmdc.dungeongen.common.Facing;
import io.github.jvmdc.dungeongen.common.Placement;
import io.github.jvmdc.dungeongen.common.PlacementType;
import io.github.jvmdc.dungeongen.common.Room;
import io.github.jvmdc.dungeongen.common.Tile;
import io.github.jvmdc.dungeongen.common.TileGrid;
import io.github.jvmdc.assets.AssetRoots;
import io.github.jvmdc.assets.ModelPaths;
import io.github.jvmdc.converter.VoxMesher;
import io.github.jvmdc.prop.AimPick;
import io.github.jvmdc.prop.PropCatalog;
import io.github.jvmdc.prop.PropSpawner;
import io.github.jvmdc.prop.PropView;
import io.github.jvmdc.render.ForwardRenderer;
import io.github.jvmdc.viewer.VoxCatalog;
import io.github.jvmdc.world.BlockChunks;
import io.github.jvmdc.world.ViewRange;
import io.github.jvmdc.world.BlockPalette;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

// gradlew.bat lwjgl3:run
public class JvmDcApp extends ApplicationAdapter {
    private static final int TILE_W = 196;
    private static final int TILE_H = 188;
    private static final int LABEL_H = 28;
    private static final int GAP = 12;
    private static final int HEADER = 28;
    private static final int CLEAR_W = 64;
    private static final String HINT = "Scroll to browse. Click a model. Esc for the menu.";
    private static final int MENU_W = 280;
    private static final int MENU_H = 44;
    private static final int BTN_W = 96;
    private static final int CLIP_W = 112;
    private static final int BTN_H = 20;
    private static final long LOAD_BUDGET_NS = 8_000_000L;

    private enum Mode { MENU, LOADING, BROWSER, VIEW, DUNGEON, FLY }

    /** How a drag in the large view is used. */
    private enum Drag { SPIN, SLIDE, ORBIT }

    private ForwardRenderer renderer;
    private SpriteBatch batch;
    private BitmapFont font;
    private GlyphLayout layout;
    private OrthographicCamera uiCamera;
    private PerspectiveCamera camera;
    private final Matrix4 model = new Matrix4();
    private VoxCatalog catalog;
    private VoxCatalog.Scan catalogScan;
    private float spin;
    private final List<VoxCatalog.Entry> shown = new ArrayList<>();
    private boolean npcsOnly;
    private final StringBuilder query = new StringBuilder();
    private boolean searchFocus;
    private Texture pixel;
    private Mode mode = Mode.MENU;
    private Dungeon dungeon;
    private long dungeonSeed = 1L;
    /** Placement fullness. Half, normal, one and a half, or double. */
    private double placeDensity = 1.0;
    private static final double[] DENSITY_STEPS = {0.5, 1.0, 1.5, 2.0};
    /** Placement icons and room roles. P toggles them. */
    private boolean showPlacements = true;
    /** Last tile copied from a click, shown in the status line. */
    private String copiedTile = "";
    /** Top-down view with ceiling slabs hidden. */
    private boolean cutaway;
    private float cutawayZoom = 1f;
    /** H hides the ceiling slabs while flying. */
    private boolean showCeiling = true;
    private ShapeRenderer shapes;
    private final Vector3 arrowPoint = new Vector3();
    private final float[] arrowScreen = new float[6];
    private final Vector3 aimOrigin = new Vector3();
    private final Vector3 aimDir = new Vector3();
    private long flySeed = Long.MIN_VALUE;
    private final Vector3 flyPos = new Vector3();
    private float flyYaw;
    private float flyPitch;
    private OrthographicCamera topCamera;
    private final List<DrawnChunk> flyShell = new ArrayList<>();
    private final List<DrawnChunk> flyCeiling = new ArrayList<>();
    private String flyNote = "";
    private FlyLoad flyBuild;
    /** Last fly-load timings, kept on screen after the spinner goes. */
    private String flyLoadNote = "";
    private PropCatalog propCatalog;
    private Engine props;
    private PropView propView;
    private MonsterView monsterView;
    private MonsterTable monsterTable;
    /** M hides or shows the bodies rolled from monster marks. */
    private boolean showMonsters = true;
    private int monsterCount;
    private VoxCatalog.Entry selected;
    private float scroll;
    private float yaw;
    private float pitch;
    private Drag drag = Drag.SPIN;
    /** Sky backdrop and the nearby lamp. Off until the Scene button is turned on. */
    private boolean showScene;
    private float slideX;
    private float slideZ;
    /** Large-view camera. Buttons do not write this. Orbit drag turns it in place. */
    private final Vector3 viewPos = new Vector3();
    private final Vector3 viewLook = new Vector3();
    private final Vector3 orbitOffset = new Vector3();
    private final Vector3 groundForward = new Vector3();
    private final Vector3 groundRight = new Vector3();
    private int cols = 1;

    private final InputAdapter input = new InputAdapter() {
        private int pressX;
        private int pressY;
        private int lastX;
        private int lastY;
        private int button = -1;
        private boolean pressOnModeChip;

        @Override
        public boolean touchDown(int screenX, int screenY, int pointer, int button) {
            if (pointer != 0) {
                return false;
            }
            pressX = screenX;
            pressY = screenY;
            lastX = screenX;
            lastY = screenY;
            this.button = button;
            pressOnModeChip = mode == Mode.VIEW
                    && (viewModeAt(screenX, screenY) >= 0 || clipAt(screenX, screenY) >= 0);
            return true;
        }

        @Override
        public boolean touchDragged(int screenX, int screenY, int pointer) {
            if (mode == Mode.VIEW && button == Input.Buttons.LEFT && !pressOnModeChip) {
                int dx = screenX - lastX;
                int dy = lastY - screenY;
                if (drag == Drag.SLIDE && shiftDown()) {
                    pitch += dy * 0.45f;
                    pitch = MathUtils.clamp(pitch, -89f, 89f);
                } else if (drag == Drag.SLIDE) {
                    slideOnGround(dx, dy);
                } else if (drag == Drag.ORBIT) {
                    orbitDrag(dx * 0.45f, dy * 0.45f);
                } else {
                    yaw += dx * 0.45f;
                    pitch += dy * 0.45f;
                    pitch = MathUtils.clamp(pitch, -89f, 89f);
                }
            }
            lastX = screenX;
            lastY = screenY;
            return true;
        }

        @Override
        public boolean touchUp(int screenX, int screenY, int pointer, int button) {
            if (button == Input.Buttons.RIGHT && mode == Mode.VIEW) {
                mode = Mode.BROWSER;
                this.button = -1;
                return true;
            }
            if (button == Input.Buttons.RIGHT && mode == Mode.DUNGEON) {
                mode = Mode.MENU;
                this.button = -1;
                return true;
            }
            int dx = screenX - pressX;
            int dy = screenY - pressY;
            if (button == Input.Buttons.LEFT && mode == Mode.VIEW && dx * dx + dy * dy < 36) {
                int clipPick = clipAt(screenX, screenY);
                if (clipPick >= 0 && selected != null && selected.rig != null) {
                    selected.rig.setClip(clipPick);
                }
                int picked = viewModeAt(screenX, screenY);
                if (picked == 0) {
                    drag = Drag.SPIN;
                } else if (picked == 1) {
                    drag = Drag.SLIDE;
                } else if (picked == 2) {
                    drag = Drag.ORBIT;
                } else if (picked == 3) {
                    showScene = !showScene;
                }
            }
            if (button == Input.Buttons.LEFT && mode == Mode.MENU && dx * dx + dy * dy < 36) {
                int picked = menuAt(screenX, screenY);
                if (picked == 0) {
                    openDungeon();
                } else if (picked == 1) {
                    openBrowser();
                }
            }
            if (button == Input.Buttons.LEFT && mode == Mode.DUNGEON && dx * dx + dy * dy < 36) {
                int picked = dungeonButtonAt(screenX, screenY);
                if (picked == 0) {
                    mode = Mode.MENU;
                } else if (picked == 1) {
                    nextDungeon();
                } else if (picked >= 10) {
                    placeDensity = DENSITY_STEPS[picked - 10];
                    openDungeon();
                } else {
                    copyDungeonTile(screenX, screenY);
                }
            }
            if (button == Input.Buttons.LEFT && mode == Mode.BROWSER && dx * dx + dy * dy < 36) {
                int search = searchAt(screenX, screenY);
                if (search == 2) {
                    query.setLength(0);
                    searchFocus = true;
                    applyFilter();
                } else if (search == 1) {
                    searchFocus = true;
                } else {
                    searchFocus = false;
                    int filter = filterAt(screenX, screenY);
                    if (filter >= 0) {
                        boolean next = filter == 1;
                        if (next != npcsOnly) {
                            npcsOnly = next;
                            applyFilter();
                        }
                    } else {
                        int index = tileAt(screenX, screenY);
                        if (index >= 0) {
                            open(shown.get(index));
                        }
                    }
                }
            }
            this.button = -1;
            return true;
        }

        @Override
        public boolean scrolled(float amountX, float amountY) {
            if (mode == Mode.FLY && cutaway) {
                cutawayZoom = MathUtils.clamp(cutawayZoom * (amountY > 0f ? 0.9f : 1.1f), 0.35f, 8f);
                return true;
            }
            if (mode != Mode.BROWSER) {
                return false;
            }
            scroll += amountY * 72f;
            return true;
        }

        @Override
        public boolean keyDown(int keycode) {
            if (keycode == Input.Keys.ESCAPE && mode == Mode.VIEW) {
                mode = Mode.BROWSER;
                return true;
            }
            if (keycode == Input.Keys.ESCAPE && flyBuild != null) {
                cancelFlyLoad();
                return true;
            }
            if (keycode == Input.Keys.ESCAPE && mode == Mode.LOADING) {
                cancelLoad();
                return true;
            }
            if (keycode == Input.Keys.ESCAPE && mode == Mode.FLY) {
                Gdx.input.setCursorCatched(false);
                mode = Mode.DUNGEON;
                return true;
            }
            if (keycode == Input.Keys.C && mode == Mode.FLY) {
                cutaway = !cutaway;
                if (cutaway) {
                    cutawayZoom = 1f;
                }
                Gdx.input.setCursorCatched(!cutaway);
                Gdx.input.getDeltaX();
                Gdx.input.getDeltaY();
                return true;
            }
            if (keycode == Input.Keys.H && mode == Mode.FLY) {
                showCeiling = !showCeiling;
                return true;
            }
            if (keycode == Input.Keys.F && mode == Mode.DUNGEON) {
                enterFly();
                return true;
            }
            if (keycode == Input.Keys.ESCAPE && (mode == Mode.DUNGEON || mode == Mode.BROWSER)) {
                if (mode == Mode.BROWSER && searchFocus) {
                    searchFocus = false;
                } else {
                    mode = Mode.MENU;
                }
                return true;
            }
            if (keycode == Input.Keys.N && (mode == Mode.DUNGEON || mode == Mode.FLY)) {
                nextDungeon();
                return true;
            }
            if (keycode == Input.Keys.P && mode == Mode.DUNGEON) {
                showPlacements = !showPlacements;
                return true;
            }
            if (keycode == Input.Keys.M && (mode == Mode.DUNGEON || mode == Mode.FLY)) {
                showMonsters = !showMonsters;
                applyMonsterToggle();
                return true;
            }
            if (keycode == Input.Keys.BACKSPACE && mode == Mode.BROWSER && searchFocus && query.length() > 0) {
                query.setLength(query.length() - 1);
                applyFilter();
                return true;
            }
            return false;
        }

        @Override
        public boolean keyTyped(char character) {
            if (mode != Mode.BROWSER || !searchFocus || character < 32 || character == 127) {
                return false;
            }
            query.append(character);
            applyFilter();
            return true;
        }
    };

    @Override
    public void create() {
        Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pixmap.setColor(Color.WHITE);
        pixmap.fill();
        pixel = new Texture(pixmap);
        pixmap.dispose();
        renderer = new ForwardRenderer();
        batch = new SpriteBatch();
        shapes = new ShapeRenderer();
        font = new BitmapFont();
        font.setColor(0.1f, 0.1f, 0.12f, 1f);
        layout = new GlyphLayout();
        uiCamera = new OrthographicCamera();
        camera = new PerspectiveCamera(50f, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        camera.near = 0.1f;
        camera.far = 500f;
        camera.up.set(0f, 1f, 0f);
        topCamera = new OrthographicCamera();
        Gdx.input.setInputProcessor(input);
        Gdx.graphics.setTitle("JVM-Dungeon-Crawler");
    }

    @Override
    public void render() {
        int width = Gdx.graphics.getWidth();
        int height = Gdx.graphics.getHeight();
        if (renderer != null) {
            renderer.resetClear();
        }
        cols = Math.max(1, (width - GAP) / (TILE_W + GAP));
        clampScroll(height);
        pumpLoads(height);
        if (mode == Mode.LOADING) {
            renderLoading(width, height);
            return;
        }
        if (mode == Mode.VIEW) {
            renderView(width, height);
        } else if (mode == Mode.BROWSER) {
            renderBrowser(width, height);
        } else if (mode == Mode.DUNGEON) {
            renderDungeon(width, height);
        } else if (mode == Mode.FLY) {
            renderFly(width, height);
        } else {
            renderMenu(width, height);
        }
    }

    @Override
    public void dispose() {
        if (catalogScan != null) {
            catalogScan.dispose();
            catalogScan = null;
        }
        if (catalog != null) {
            catalog.dispose();
        }
        if (renderer != null) {
            renderer.dispose();
        }
        if (batch != null) {
            batch.dispose();
        }
        if (shapes != null) {
            shapes.dispose();
        }
        if (font != null) {
            font.dispose();
        }
        if (pixel != null) {
            pixel.dispose();
        }
        disposeFly();
        if (propView != null) {
            propView.dispose();
            propView = null;
        }
    }

    private void open(VoxCatalog.Entry entry) {
        selected = entry;
        yaw = 0f;
        pitch = 0f;
        slideX = 0f;
        slideZ = 0f;
        mode = Mode.VIEW;
        catalog.load(entry);
        if (entry.rig != null) {
            int start = 0;
            for (int i = 0; i < entry.rig.clipCount(); i++) {
                if ("Idle".equals(entry.rig.clipName(i))) {
                    start = i;
                    break;
                }
            }
            entry.rig.setClip(start);
        }
        placeDefaultView(entry.mesh != null ? entry.mesh.span : 8f);
    }

    /** Front-right view of a newly opened model. */
    private void placeDefaultView(float span) {
        float dist = Math.max(span, 1f) * 1.7f;
        viewPos.set(dist * 0.72f, span * 0.18f, -dist);
        viewLook.set(0f, 0f, 0f);
    }

    /**
     * Turn the large-view camera around the point it is already looking at.
     * Distance stays as it was, so the model does not jump closer or farther.
     */
    private void orbitDrag(float yawDeg, float pitchDeg) {
        orbitOffset.set(viewPos).sub(viewLook);
        float dist = orbitOffset.len();
        if (dist < 1e-4f) {
            return;
        }
        float yaw = MathUtils.atan2(orbitOffset.x, orbitOffset.z) * MathUtils.radiansToDegrees;
        float pitch = MathUtils.asin(MathUtils.clamp(orbitOffset.y / dist, -1f, 1f)) * MathUtils.radiansToDegrees;
        yaw += yawDeg;
        pitch = MathUtils.clamp(pitch + pitchDeg, -89f, 89f);
        float cp = MathUtils.cosDeg(pitch);
        viewPos.set(
                viewLook.x + dist * cp * MathUtils.sinDeg(yaw),
                viewLook.y + dist * MathUtils.sinDeg(pitch),
                viewLook.z + dist * cp * MathUtils.cosDeg(yaw));
    }

    private void openBrowser() {
        searchFocus = false;
        if (catalog != null) {
            mode = Mode.BROWSER;
            return;
        }
        catalogScan = null;
        mode = Mode.LOADING;
    }

    private void cancelLoad() {
        if (catalogScan != null) {
            catalogScan.dispose();
            catalogScan = null;
        }
        mode = Mode.MENU;
    }

    private void openDungeon() {
        boolean flying = mode == Mode.FLY || flyBuild != null;
        flyBuild = null;
        disposeFly();
        PlacementBudget.density = placeDensity;
        dungeon = Layouts.of(Layouts.DEFAULT).generate(LayoutConfig.attached(), dungeonSeed);
        refreshMonsterCount();
        copiedTile = "";
        flyNote = "";
        cutaway = false;
        mode = flying ? Mode.FLY : Mode.DUNGEON;
        if (flying) {
            enterFly();
        }
    }

    private MonsterTable loadMonsters() {
        if (monsterTable == null) {
            try {
                monsterTable = MonsterTable.load();
            } catch (IOException e) {
                monsterTable = null;
            }
        }
        return monsterTable;
    }

    private void refreshMonsterCount() {
        if (dungeon == null || !showMonsters || loadMonsters() == null) {
            monsterCount = 0;
            return;
        }
        monsterCount = MonsterSpawner.plan(dungeon, monsterTable, 0).size();
    }

    private void applyMonsterToggle() {
        if (props == null || loadMonsters() == null) {
            refreshMonsterCount();
            return;
        }
        MonsterSpawner.clear(props);
        if (showMonsters) {
            MonsterSpawner.add(props, dungeon, monsterTable, 0);
            if (monsterView != null) {
                monsterView.setScale(monsterTable.worldScale());
                monsterView.attach(props);
            }
        }
        monsterCount = MonsterSpawner.count(props);
    }

    private String monsterLabel() {
        return showMonsters ? "monsters " + monsterCount : "monsters off";
    }

    private void nextDungeon() {
        dungeonSeed++;
        openDungeon();
    }

    /** First-person fly through the meshed floor. C switches to a top-down cutaway. */
    private void enterFly() {
        if (dungeon == null) {
            return;
        }
        if (flySeed == dungeon.seed && !flyShell.isEmpty()) {
            showFly();
            return;
        }
        disposeFly();
        flyBuild = new FlyLoad();
        flyBuild.trace = new LoadTrace();
        flyBuild.trace.total = chunkCount();
        mode = Mode.LOADING;
    }

    private void showFly() {
        mode = Mode.FLY;
        Gdx.input.setCursorCatched(!cutaway);
        Gdx.input.getDeltaX();
        Gdx.input.getDeltaY();
    }

    private void cancelFlyLoad() {
        flyBuild = null;
        disposeFly();
        mode = Mode.DUNGEON;
    }

    /** One slice of the floor build. True when the fly view is ready or the build failed. */
    private boolean pumpFly() {
        long start = System.nanoTime();
        LoadTrace trace = flyBuild.trace;
        try {
            if (flyBuild.palette == null) {
                trace.begin("palette");
                try {
                    flyBuild.palette = BlockPalette.load(
                            AssetRoots.locate().custom.resolve("manifest").resolve("block_palette.json"));
                } finally {
                    trace.end();
                }
                if (System.nanoTime() - start >= LOAD_BUDGET_NS) {
                    return false;
                }
            }
            TileGrid grid = dungeon.grid;
            int across = (grid.width + BlockChunks.CHUNK_TILES - 1) / BlockChunks.CHUNK_TILES;
            while (flyBuild.tileY < grid.height && System.nanoTime() - start < LOAD_BUDGET_NS) {
                long chunkStart = System.nanoTime();
                BlockChunks.partAt(dungeon, flyBuild.palette, flyBuild.tileX, flyBuild.tileY, (shell, ceiling) -> {
                    trace.begin("upload");
                    try {
                        uploadFly(List.of(shell), flyShell);
                        uploadFly(List.of(ceiling), flyCeiling);
                    } finally {
                        trace.end();
                    }
                }, trace);
                trace.noteChunk(System.nanoTime() - chunkStart);
                flyBuild.tileX += BlockChunks.CHUNK_TILES;
                if (flyBuild.tileX >= grid.width) {
                    flyBuild.tileX = 0;
                    flyBuild.tileY += BlockChunks.CHUNK_TILES;
                }
                trace.done = flyBuild.tileY / BlockChunks.CHUNK_TILES * across
                        + flyBuild.tileX / BlockChunks.CHUNK_TILES;
            }
            if (flyBuild.tileY < grid.height) {
                return false;
            }
            if (propCatalog == null) {
                trace.begin("catalog");
                try {
                    propCatalog = PropCatalog.load(AssetRoots.locate());
                } finally {
                    trace.end();
                }
            }
            if (propView == null) {
                propView = new PropView(ModelPaths.locate());
            }
            trace.begin("place props");
            try {
                props = PropSpawner.populate(dungeon, propCatalog, trace);
                MonsterTable monsters = loadMonsters();
                if (showMonsters && monsters != null) {
                    MonsterSpawner.add(props, dungeon, monsters, 0);
                }
            } finally {
                trace.end();
            }
            if (monsterView == null) {
                monsterView = new MonsterView();
            }
            MonsterTable monsters = loadMonsters();
            if (monsters != null) {
                monsterView.setScale(monsters.worldScale());
            }
            monsterView.attach(props);
            monsterCount = MonsterSpawner.count(props);
            placeFlySpawn();
            flySeed = dungeon.seed;
            cutaway = false;
            flyNote = "";
            finishFlyTrace(trace);
            flyBuild = null;
            showFly();
            return true;
        } catch (Exception e) {
            disposeFly();
            flyBuild = null;
            flyNote = "could not build the 3D floor";
            mode = Mode.DUNGEON;
            return true;
        }
    }

    private int chunkCount() {
        TileGrid grid = dungeon.grid;
        int across = (grid.width + BlockChunks.CHUNK_TILES - 1) / BlockChunks.CHUNK_TILES;
        int down = (grid.height + BlockChunks.CHUNK_TILES - 1) / BlockChunks.CHUNK_TILES;
        return across * down;
    }

    private void finishFlyTrace(LoadTrace trace) {
        flyLoadNote = trace.report();
        System.out.println(flyLoadNote);
        try {
            Path out = Path.of("build", "fly-load.txt");
            Files.createDirectories(out.getParent());
            Files.writeString(out, flyLoadNote);
        } catch (IOException ignored) {
            flyLoadNote = flyLoadNote + "\n(could not write build/fly-load.txt)";
        }
    }

    private void uploadFly(List<BlockChunks.ChunkMesh> chunks, List<DrawnChunk> into) {
        for (BlockChunks.ChunkMesh chunk : chunks) {
            if (chunk.mesh.quads <= 0) {
                continue;
            }
            into.add(new DrawnChunk(chunk.originX, chunk.originY, chunk.mesh.upload()));
        }
    }

    private void placeFlySpawn() {
        TileGrid grid = dungeon.grid;
        int tileX = grid.width / 2;
        int tileY = grid.height / 2;
        if (dungeon.stairsUp >= 0) {
            tileX = grid.x(dungeon.stairsUp);
            tileY = grid.y(dungeon.stairsUp);
        }
        float center = BlockChunks.VOXELS_PER_TILE * 0.5f;
        flyPos.set(
                tileX * BlockChunks.VOXELS_PER_TILE + center,
                BlockChunks.FLOOR + 20f,
                -(tileY * BlockChunks.VOXELS_PER_TILE + center));
        flyYaw = 0f;
        flyPitch = 0f;
    }

    private void disposeFly() {
        for (DrawnChunk chunk : flyShell) {
            chunk.mesh.dispose();
        }
        for (DrawnChunk chunk : flyCeiling) {
            chunk.mesh.dispose();
        }
        flyShell.clear();
        flyCeiling.clear();
        flySeed = Long.MIN_VALUE;
        props = null;
        if (monsterView != null) {
            monsterView.dispose();
            monsterView = null;
        }
    }

    private void renderFly(int width, int height) {
        float dt = Math.min(Gdx.graphics.getDeltaTime(), 0.05f);
        if (!cutaway) {
            steerFly(dt);
        }
        renderer.setClear(0.012f, 0.014f, 0.018f);
        renderer.begin();
        renderer.setSunScale(0.05f);
        Camera view;
        if (cutaway) {
            frameCutaway(width, height);
            view = topCamera;
        } else {
            frameFly(width, height);
            view = camera;
        }
        if (propView != null) {
            propView.uploadLights(props, view, cutaway, renderer);
        }
        renderer.setFaceNoise(true);
        drawFlyChunks(view, showCeiling && !cutaway);
        renderer.setFaceNoise(false);
        if (propView != null) {
            propView.draw(props, renderer, view, model);
        }
        if (monsterView != null && showMonsters) {
            monsterView.tick(dt);
            monsterView.draw(props, renderer, view);
        }
        renderer.end(false);
        if (cutaway) {
            drawCameraArrow(width, height);
        }
        beginText(width, height);
        font.setColor(0.1f, 0.1f, 0.12f, 1f);
        String status = "seed " + dungeon.seed
                + (cutaway ? "    top-down" : (showCeiling ? "    fly" : "    fly, ceiling off"))
                + "    " + monsterLabel()
                + "    WASD mouse    space up    C cutaway    H ceiling    M monsters    Esc map";
        font.draw(batch, status, 12, height - 8);
        if (!flyLoadNote.isEmpty()) {
            String[] lines = flyLoadNote.split("\n");
            float blockH = lines.length * 16f + 8f;
            batch.setColor(0.04f, 0.05f, 0.06f, 0.72f);
            batch.draw(pixel, 8f, height - 28f - blockH, 420f, blockH);
            batch.setColor(Color.WHITE);
            font.setColor(0.86f, 0.87f, 0.9f, 1f);
            float y = height - 36f;
            for (String line : lines) {
                font.draw(batch, line, 16f, y);
                y -= 16f;
            }
        }
        font.setColor(0.1f, 0.1f, 0.12f, 1f);
        drawAimLabel(view, width, height);
        batch.end();
    }

    /** Small name under the middle of the view for whatever the look ray meets. */
    private void drawAimLabel(Camera view, int width, int height) {
        String name = aimedName(view, width, height);
        if (name.isEmpty()) {
            return;
        }
        layout.setText(font, name);
        float x = (width - layout.width) * 0.5f;
        float y = height * 0.5f - 18f;
        font.setColor(0f, 0f, 0f, 0.9f);
        font.draw(batch, name, x + 1f, y - 1f);
        font.setColor(0.97f, 0.95f, 0.9f, 1f);
        font.draw(batch, name, x, y);
    }

    private String aimedName(Camera view, int width, int height) {
        if (cutaway) {
            aimOrigin.set(Gdx.input.getX(), Gdx.input.getY(), 0f);
            aimDir.set(Gdx.input.getX(), Gdx.input.getY(), 1f);
            view.unproject(aimOrigin, 0, 0, width, height);
            view.unproject(aimDir, 0, 0, width, height);
            aimDir.sub(aimOrigin);
            return AimPick.find(dungeon, props, aimOrigin, aimDir, false);
        }
        return AimPick.find(dungeon, props, view.position, view.direction, showCeiling);
    }

    private void steerFly(float dt) {
        if (Gdx.input.isCursorCatched()) {
            flyYaw += Gdx.input.getDeltaX() * 0.15f;
            flyPitch = MathUtils.clamp(flyPitch - Gdx.input.getDeltaY() * 0.15f, -89f, 89f);
        }
        float yawRad = flyYaw * MathUtils.degreesToRadians;
        float pitchRad = flyPitch * MathUtils.degreesToRadians;
        float cp = MathUtils.cos(pitchRad);
        float fx = MathUtils.sin(yawRad) * cp;
        float fy = MathUtils.sin(pitchRad);
        float fz = -MathUtils.cos(yawRad) * cp;
        float rx = MathUtils.cos(yawRad);
        float rz = MathUtils.sin(yawRad);
        float mx = 0f;
        float my = 0f;
        float mz = 0f;
        if (Gdx.input.isKeyPressed(Input.Keys.W)) {
            mx += fx;
            my += fy;
            mz += fz;
        }
        if (Gdx.input.isKeyPressed(Input.Keys.S)) {
            mx -= fx;
            my -= fy;
            mz -= fz;
        }
        if (Gdx.input.isKeyPressed(Input.Keys.D)) {
            mx += rx;
            mz += rz;
        }
        if (Gdx.input.isKeyPressed(Input.Keys.A)) {
            mx -= rx;
            mz -= rz;
        }
        if (Gdx.input.isKeyPressed(Input.Keys.SPACE)) {
            my += 1f;
        }
        if (Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT) || Gdx.input.isKeyPressed(Input.Keys.CONTROL_RIGHT)) {
            my -= 1f;
        }
        float len2 = mx * mx + my * my + mz * mz;
        if (len2 < 1e-8f) {
            return;
        }
        float len = (float) Math.sqrt(len2);
        boolean fast = Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT) || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT);
        float speed = (fast ? 330f : 108f) * dt / len;
        flyPos.x += mx * speed;
        flyPos.y += my * speed;
        flyPos.z += mz * speed;
    }

    private void frameFly(int width, int height) {
        float yawRad = flyYaw * MathUtils.degreesToRadians;
        float pitchRad = flyPitch * MathUtils.degreesToRadians;
        float cp = MathUtils.cos(pitchRad);
        camera.viewportWidth = width;
        camera.viewportHeight = height;
        camera.position.set(flyPos);
        camera.direction.set(MathUtils.sin(yawRad) * cp, MathUtils.sin(pitchRad), -MathUtils.cos(yawRad) * cp);
        camera.up.set(0f, 1f, 0f);
        camera.near = ViewRange.near();
        camera.far = ViewRange.far(dungeon.grid.width, dungeon.grid.height);
        camera.update();
    }

    private void frameCutaway(int width, int height) {
        float mapW = dungeon.grid.width * BlockChunks.VOXELS_PER_TILE;
        float mapH = dungeon.grid.height * BlockChunks.VOXELS_PER_TILE;
        float aspect = width / (float) Math.max(1, height);
        float viewH = mapH * 1.06f / cutawayZoom;
        float viewW = viewH * aspect;
        topCamera.viewportWidth = viewW;
        topCamera.viewportHeight = viewH;
        float cx = mapW * 0.5f;
        float cz = -mapH * 0.5f;
        topCamera.position.set(cx, BlockChunks.HEIGHT + 80f, cz);
        topCamera.up.set(0f, 0f, 1f);
        topCamera.lookAt(cx, 0f, cz);
        topCamera.near = ViewRange.near();
        topCamera.far = BlockChunks.HEIGHT + 80f + BlockChunks.VOXELS_PER_TILE;
        topCamera.update();
    }

    /** Arrow on the top-down map: tail is the fly camera, tip is the way it faces. */
    private void drawCameraArrow(int width, int height) {
        float yawRad = flyYaw * MathUtils.degreesToRadians;
        float dx = MathUtils.sin(yawRad);
        float dz = -MathUtils.cos(yawRad);
        float px = -dz;
        float pz = dx;
        float tile = BlockChunks.VOXELS_PER_TILE;
        float len = tile * 2.4f;
        float head = len * 0.42f;
        float y = BlockChunks.FLOOR + 8f;
        float x = flyPos.x;
        float z = flyPos.z;
        float edge = tile * 0.1f;
        float lenOut = len + edge;
        uiCamera.setToOrtho(false, width, height);
        shapes.setProjectionMatrix(uiCamera.combined);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        arrow(width, height, x, y, z, x + dx * (lenOut - head), z + dz * (lenOut - head), dx, dz, px, pz,
                lenOut, tile * 0.18f, tile * 0.46f, 0.12f, 0.1f, 0.08f);
        arrow(width, height, x, y, z, x + dx * (len - head), z + dz * (len - head), dx, dz, px, pz,
                len, tile * 0.1f, tile * 0.3f, 1f, 0.86f, 0.15f);
        shapes.end();
    }

    private void arrow(int width, int height, float x, float y, float z, float hx, float hz,
            float dx, float dz, float px, float pz, float len, float shaft, float wing,
            float r, float g, float b) {
        shapes.setColor(r, g, b, 1f);
        tri(width, height,
                x + px * shaft, y, z + pz * shaft,
                hx + px * shaft, y, hz + pz * shaft,
                hx - px * shaft, y, hz - pz * shaft);
        tri(width, height,
                x + px * shaft, y, z + pz * shaft,
                hx - px * shaft, y, hz - pz * shaft,
                x - px * shaft, y, z - pz * shaft);
        tri(width, height,
                x + dx * len, y, z + dz * len,
                hx + px * wing, y, hz + pz * wing,
                hx - px * wing, y, hz - pz * wing);
    }

    private void tri(int width, int height,
            float x1, float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3) {
        project(width, height, x1, y1, z1);
        arrowScreen[0] = arrowPoint.x;
        arrowScreen[1] = arrowPoint.y;
        project(width, height, x2, y2, z2);
        arrowScreen[2] = arrowPoint.x;
        arrowScreen[3] = arrowPoint.y;
        project(width, height, x3, y3, z3);
        arrowScreen[4] = arrowPoint.x;
        arrowScreen[5] = arrowPoint.y;
        shapes.triangle(arrowScreen[0], arrowScreen[1], arrowScreen[2], arrowScreen[3],
                arrowScreen[4], arrowScreen[5]);
    }

    private void project(int width, int height, float x, float y, float z) {
        arrowPoint.set(x, y, z);
        topCamera.project(arrowPoint, 0, 0, width, height);
    }

    private void drawFlyChunks(Camera view, boolean ceilings) {
        drawChunkList(view, flyShell);
        if (ceilings) {
            drawChunkList(view, flyCeiling);
        }
    }

    private void drawChunkList(Camera view, List<DrawnChunk> chunks) {
        for (DrawnChunk chunk : chunks) {
            model.idt().translate(chunk.originX, 0f, -chunk.originY);
            renderer.draw(view, chunk.mesh.parts, model);
        }
    }

    private void pumpLoads(int height) {
        if (catalog == null || catalog.error != null) {
            return;
        }
        long deadline = System.nanoTime() + LOAD_BUDGET_NS;
        if (mode == Mode.VIEW && selected != null) {
            catalog.load(selected);
        }
        if (mode != Mode.BROWSER) {
            return;
        }
        int stride = TILE_H + GAP;
        int firstRow = Math.max(0, (int) (scroll / stride));
        int lastRow = (int) ((scroll + height) / stride) + 1;
        int count = shown.size();
        for (int row = firstRow; row <= lastRow; row++) {
            for (int col = 0; col < cols; col++) {
                int index = row * cols + col;
                if (index >= count || System.nanoTime() >= deadline) {
                    return;
                }
                catalog.load(shown.get(index));
            }
        }
    }

    private void renderBrowser(int width, int height) {
        renderer.begin();
        int strideX = TILE_W + GAP;
        int strideY = TILE_H + GAP;
        int previewH = TILE_H - LABEL_H;
        int count = shown.size();
        if (catalog.error == null) {
            for (int i = 0; i < count; i++) {
                int col = i % cols;
                int row = i / cols;
                int tileX = GAP + col * strideX;
                int tileTop = HEADER + GAP + row * strideY - (int) scroll;
                if (tileTop + TILE_H < 0 || tileTop > height) {
                    continue;
                }
                VoxCatalog.Entry entry = shown.get(i);
                int glY = height - (tileTop + previewH);
                renderer.tile(tileX, glY, TILE_W, previewH);
                if (entry.mesh != null && entry.mesh.parts.length > 0) {
                    frame(entry.mesh.span, TILE_W, previewH);
                    model.idt();
                    renderer.draw(camera, entry.mesh.parts, model);
                }
            }
        }
        renderer.end(false);
        beginText(width, height);
        font.setColor(0.1f, 0.1f, 0.12f, 1f);
        if (catalog.error != null) {
            font.draw(batch, catalog.error, 16, height - 16);
        } else {
            layoutHeader(width);
            font.draw(batch, HINT, 12, height - 8);
            drawFilter(height);
            drawSearch(height);
            for (int i = 0; i < count; i++) {
                int col = i % cols;
                int row = i / cols;
                int tileX = GAP + col * strideX;
                int tileTop = HEADER + GAP + row * strideY - (int) scroll;
                if (tileTop + TILE_H < -8 || tileTop > height) {
                    continue;
                }
                String name = fit(shown.get(i).name, TILE_W - 8);
                layout.setText(font, name);
                float textX = tileX + (TILE_W - layout.width) * 0.5f;
                float textY = height - (tileTop + previewH + 4);
                font.draw(batch, name, textX, textY);
            }
        }
        batch.end();
    }

    private void renderView(int width, int height) {
        String path = selected == null ? "" : selected.path;
        int chips = BTN_W * 4 + 6 * 3 + 12;
        layout.setText(font, path, Color.BLACK, width - 24 - chips, Align.left, true);
        float pathHeight = layout.height;
        int header = (int) (16 + pathHeight + 22);
        viewHeaderH = header;
        int viewH = Math.max(1, height - header);
        renderer.begin();
        renderer.tile(0, 0, width, viewH);
        float span = selected != null && selected.mesh != null ? selected.mesh.span : 8f;
        frame(span, width, viewH);
        if (showScene) {
            renderer.backdrop(camera);
        }
        if (selected != null && selected.mesh != null && selected.mesh.parts.length > 0) {
            model.idt();
            model.translate(slideX, 0f, slideZ);
            model.rotate(0f, 1f, 0f, yaw);
            model.rotate(1f, 0f, 0f, pitch);
            if (showScene) {
                renderer.placeLamp(selected.mesh.span);
            }
            if (selected.rig != null && !"No anim".equals(selected.rig.clipName(selected.rig.clip()))) {
                selected.rig.tick(Gdx.graphics.getDeltaTime());
                selected.rig.draw(renderer, camera, model);
            } else {
                renderer.draw(camera, selected.mesh.parts, model);
            }
            if (showScene) {
                renderer.drawLamp(camera, selected.mesh.span);
            }
        }
        renderer.end(true);
        beginText(width, height);
        font.setColor(0.08f, 0.08f, 0.1f, 1f);
        font.draw(batch, layout, 12, height - 8);
        font.setColor(0.22f, 0.24f, 0.28f, 1f);
        String hint = selected != null && selected.failed
                ? "Could not read this file. Esc to go back."
                : drag == Drag.SLIDE
                ? "Drag to slide the model. Shift-drag to tilt. Esc or right-click to go back."
                : drag == Drag.ORBIT
                ? "Drag to move the camera around the model. Esc or right-click to go back."
                : "Drag to turn the model. Esc or right-click to go back.";
        font.draw(batch, hint, 12, height - 12 - pathHeight);
        int bottom = height - 4 - BTN_H;
        int orbitBtn = width - 12 - BTN_W;
        int slideBtn = orbitBtn - 6 - BTN_W;
        int spinBtn = slideBtn - 6 - BTN_W;
        int sceneBtn = spinBtn - 6 - BTN_W;
        chip(sceneBtn, bottom, "Scene", showScene);
        chip(spinBtn, bottom, "Spin", drag == Drag.SPIN);
        chip(slideBtn, bottom, "Slide", drag == Drag.SLIDE);
        chip(orbitBtn, bottom, "Orbit", drag == Drag.ORBIT);
        if (selected != null && selected.rig != null) {
            int top = viewHeaderH;
            for (int i = 0; i < selected.rig.clipCount(); i++) {
                int row = height - top - BTN_H;
                chip(12, row, CLIP_W, BTN_H, selected.rig.clipName(i), selected.rig.clip() == i);
                top += BTN_H + 6;
            }
        }
        batch.end();
    }

    private void renderLoading(int width, int height) {
        // The first frame only draws. Work starts on the next frame, after this picture is up.
        if (flyBuild != null) {
            if (!flyBuild.armed) {
                flyBuild.armed = true;
            } else if (pumpFly()) {
                if (mode == Mode.FLY) {
                    renderFly(width, height);
                } else {
                    renderDungeon(width, height);
                }
                return;
            }
            String detail = flyBuild.trace == null ? "" : flyBuild.trace.report();
            drawSpinner(width, height, "Loading the floor", "Esc for the map", detail);
            return;
        }
        if (catalogScan == null) {
            catalogScan = new VoxCatalog.Scan();
        } else if (catalogScan.pump(LOAD_BUDGET_NS)) {
            catalog = catalogScan.catalog();
            catalogScan = null;
            applyFilter();
            if (catalog.error == null) {
                Gdx.graphics.setTitle("JVM-Dungeon-Crawler — " + catalog.entries.size() + " models");
            }
            mode = Mode.BROWSER;
            renderBrowser(width, height);
            return;
        }
        drawSpinner(width, height, "Loading assets", "Esc for the menu", "");
    }

    private void drawSpinner(int width, int height, String label, String hint, String detail) {
        renderer.begin();
        renderer.end(false);
        beginText(width, height);
        spin = (spin + Gdx.graphics.getDeltaTime() * 280f) % 360f;
        float cx = width * 0.5f;
        float cy = height * 0.5f + 16f;
        int marks = 12;
        for (int i = 0; i < marks; i++) {
            float age = i / (float) marks;
            float angle = spin - i * (360f / marks);
            batch.setColor(0.12f, 0.14f, 0.16f, 0.2f + 0.8f * (1f - age));
            float x = cx + MathUtils.cosDeg(angle) * 26f;
            float y = cy + MathUtils.sinDeg(angle) * 26f;
            batch.draw(pixel, x - 3.5f, y - 3.5f, 7f, 7f);
        }
        batch.setColor(Color.WHITE);
        font.setColor(0.15f, 0.16f, 0.18f, 1f);
        layout.setText(font, label);
        font.draw(batch, label, cx - layout.width * 0.5f, cy - 52f);
        font.setColor(0.35f, 0.37f, 0.4f, 1f);
        layout.setText(font, hint);
        font.draw(batch, hint, cx - layout.width * 0.5f, cy - 74f);
        if (detail != null && !detail.isEmpty()) {
            font.setColor(0.22f, 0.24f, 0.28f, 1f);
            float y = cy - 102f;
            for (String line : detail.split("\n")) {
                if (line.isEmpty()) {
                    continue;
                }
                layout.setText(font, line);
                font.draw(batch, line, cx - layout.width * 0.5f, y);
                y -= layout.height + 2f;
            }
        }
        font.setColor(0.1f, 0.1f, 0.12f, 1f);
        batch.end();
    }

    private void renderMenu(int width, int height) {
        renderer.begin();
        renderer.end(false);
        beginText(width, height);
        font.getData().setScale(2f);
        font.setColor(0.12f, 0.13f, 0.16f, 1f);
        String title = "JVM Dungeon Crawler";
        layout.setText(font, title);
        font.draw(batch, title, (width - layout.width) * 0.5f, height - 120);
        font.getData().setScale(1f);
        int x = menuX(width);
        chip(x, height - menuTop(0) - MENU_H, MENU_W, MENU_H, "Dungeon generator", false);
        chip(x, height - menuTop(1) - MENU_H, MENU_W, MENU_H, "Asset viewer", false);
        batch.end();
    }

    private void renderDungeon(int width, int height) {
        renderer.begin();
        renderer.end(false);
        beginText(width, height);
        int header = 64;
        if (dungeon != null) {
            TileGrid grid = dungeon.grid;
            int footer = showPlacements ? 36 : 16;
            int cell = Math.max(2, Math.min((width - 24) / grid.width, (height - header - footer) / grid.height));
            int mapW = cell * grid.width;
            int originX = (width - mapW) / 2;
            int originTop = header + 8;
            for (int y = 0; y < grid.height; y++) {
                for (int x = 0; x < grid.width; x++) {
                    paintCell(grid.get(x, y));
                    int draw = Math.max(1, cell - 1);
                    float left = originX + x * cell;
                    float bottom = height - (originTop + (y + 1) * cell);
                    batch.draw(pixel, left, bottom, draw, draw);
                    if (grid.get(x, y).isDoor()) {
                        drawDoorLine(grid, x, y, left, bottom, draw);
                    }
                }
            }
            if (showPlacements) {
                drawPlacements(dungeon, originX, originTop, cell, height);
                drawRoomRoles(dungeon, originX, originTop, cell, height);
                drawPlacementLegend(width, height);
            }
            batch.setColor(Color.WHITE);
            font.setColor(0.1f, 0.1f, 0.12f, 1f);
            String status = "seed " + dungeon.seed
                    + "    rooms " + dungeon.rooms.size()
                    + (copiedTile.isEmpty() ? "" : "    copied " + copiedTile)
                    + (flyNote.isEmpty() ? "" : "    " + flyNote)
                    + "    density " + densityLabel(placeDensity)
                    + "    " + monsterLabel()
                    + "    F fly    click a tile    N new    P marks    M monsters";
            int textLeft = 12 + BTN_W + 10;
            int textRight = width - 12 - BTN_W - 10;
            font.draw(batch, fit(status, Math.max(12, textRight - textLeft)), textLeft, height - 8);
            drawLegend(width, height);
        }
        int bottom = height - 4 - BTN_H;
        chip(12, bottom, "Menu", false);
        chip(width - 12 - BTN_W, bottom, "New", false);
        int densityW = 52;
        int densityGap = 6;
        int densityTotal = DENSITY_STEPS.length * densityW + (DENSITY_STEPS.length - 1) * densityGap;
        int densityLeft = (width - densityTotal) / 2;
        int densityBottom = height - 28 - BTN_H;
        for (int i = 0; i < DENSITY_STEPS.length; i++) {
            boolean on = Math.abs(placeDensity - DENSITY_STEPS[i]) < 0.01;
            chip(densityLeft + i * (densityW + densityGap), densityBottom, densityW, BTN_H,
                    densityLabel(DENSITY_STEPS[i]), on);
        }
        batch.end();
    }

    private void paintCell(Tile tile) {
        switch (tile) {
            case ROOM_FLOOR -> batch.setColor(0.76f, 0.64f, 0.42f, 1f);
            case CORRIDOR_FLOOR -> batch.setColor(0.45f, 0.55f, 0.48f, 1f);
            case GROUND -> batch.setColor(0.40f, 0.38f, 0.32f, 1f);
            case WALL -> batch.setColor(0.55f, 0.55f, 0.58f, 1f);
            case ROCK -> batch.setColor(0.08f, 0.08f, 0.09f, 1f);
            case DOOR -> batch.setColor(0.85f, 0.72f, 0.4f, 1f);
            case LOCKED_DOOR -> batch.setColor(0.55f, 0.28f, 0.22f, 1f);
            case SECRET_DOOR -> batch.setColor(0.72f, 0.48f, 0.9f, 1f);
            case STAIRS_UP -> batch.setColor(0.25f, 0.85f, 0.55f, 1f);
            case STAIRS_DOWN -> batch.setColor(0.95f, 0.42f, 0.12f, 1f);
            case WATER -> batch.setColor(0.28f, 0.45f, 0.62f, 1f);
            case DEEP_WATER -> batch.setColor(0.12f, 0.24f, 0.4f, 1f);
            case LAVA -> batch.setColor(0.8f, 0.32f, 0.12f, 1f);
            case PIT -> batch.setColor(0.05f, 0.05f, 0.06f, 1f);
            case TRAP_PLATE -> batch.setColor(0.7f, 0.25f, 0.25f, 1f);
        }
    }

    /** A line through a door, along the way it joins the two sides. */
    private void drawDoorLine(TileGrid grid, int x, int y, float left, float bottom, int draw) {
        if (draw < 3) {
            return;
        }
        boolean north = passable(grid, x, y - 1);
        boolean south = passable(grid, x, y + 1);
        boolean east = passable(grid, x + 1, y);
        boolean west = passable(grid, x - 1, y);
        boolean vertical = north && south;
        boolean horizontal = east && west;
        if (!vertical && !horizontal) {
            if (solid(grid, x - 1, y) && solid(grid, x + 1, y)) {
                vertical = true;
            }
            if (solid(grid, x, y - 1) && solid(grid, x, y + 1)) {
                horizontal = true;
            }
        }
        if (!vertical && !horizontal) {
            return;
        }
        int thick = Math.max(1, draw / 5);
        float half = thick * 0.5f;
        batch.setColor(0.15f, 0.1f, 0.05f, 1f);
        if (vertical) {
            batch.draw(pixel, left + draw * 0.5f - half, bottom, thick, draw);
        }
        if (horizontal) {
            batch.draw(pixel, left, bottom + draw * 0.5f - half, draw, thick);
        }
    }

    private boolean passable(TileGrid grid, int x, int y) {
        if (!grid.inBounds(x, y)) {
            return false;
        }
        Tile tile = grid.get(x, y);
        return tile.isOpen() || tile.isDoor();
    }

    private boolean solid(TileGrid grid, int x, int y) {
        if (!grid.inBounds(x, y)) {
            return true;
        }
        Tile tile = grid.get(x, y);
        return tile == Tile.WALL || tile == Tile.ROCK;
    }

    /** Colored swatches for every tile, under the seed line. */
    private void drawLegend(int width, int height) {
        Tile[] tiles = {
                Tile.ROCK, Tile.WALL, Tile.GROUND, Tile.ROOM_FLOOR, Tile.CORRIDOR_FLOOR,
                Tile.DOOR, Tile.LOCKED_DOOR, Tile.SECRET_DOOR, Tile.STAIRS_UP, Tile.STAIRS_DOWN,
                Tile.WATER, Tile.DEEP_WATER, Tile.LAVA, Tile.PIT, Tile.TRAP_PLATE
        };
        String[] names = {
                "rock", "wall", "ground", "room", "hall",
                "door", "locked", "secret", "stairs up", "stairs down",
                "water", "deep water", "lava", "pit", "trap"
        };
        int x = 12;
        int y = height - 28;
        font.setColor(0.1f, 0.1f, 0.12f, 1f);
        for (int i = 0; i < tiles.length; i++) {
            layout.setText(font, names[i]);
            int item = 14 + (int) layout.width + 14;
            if (x + item > width - 12) {
                x = 12;
                y -= 18;
            }
            paintCell(tiles[i]);
            batch.draw(pixel, x, y - 10, 10, 10);
            batch.setColor(Color.WHITE);
            font.setColor(0.1f, 0.1f, 0.12f, 1f);
            font.draw(batch, names[i], x + 14, y);
            x += item;
        }
    }

    private void drawPlacements(Dungeon dungeon, int originX, int originTop, int cell, int height) {
        int mark = Math.max(2, cell / 2);
        int pad = Math.max(0, (cell - mark) / 2);
        for (Placement placement : dungeon.placements) {
            int draw = Math.max(1, mark - 1);
            float left = originX + placement.x * cell + pad;
            float bottom = height - (originTop + (placement.y + 1) * cell) + pad;
            drawMark(placement.type, left, bottom, draw);
            if (placement.facing != Facing.NONE && draw >= 3) {
                float centerX = left + draw * 0.5f;
                float centerY = bottom + draw * 0.5f;
                int thick = Math.max(1, draw / 4);
                float half = thick * 0.5f;
                float reach = draw * 0.5f;
                batch.setColor(0.15f, 0.1f, 0.05f, 1f);
                if (placement.facing.dx != 0) {
                    float x0 = placement.facing.dx > 0 ? centerX : centerX - reach;
                    batch.draw(pixel, x0, centerY - half, reach, thick);
                } else {
                    float y0 = placement.facing.dy < 0 ? centerY : centerY - reach;
                    batch.draw(pixel, centerX - half, y0, thick, reach);
                }
            }
        }
        batch.setColor(Color.WHITE);
    }

    private void drawRoomRoles(Dungeon dungeon, int originX, int originTop, int cell, int height) {
        if (cell < 8) {
            return;
        }
        font.getData().setScale(cell >= 14 ? 0.7f : 0.5f);
        font.setColor(0.95f, 0.95f, 0.9f, 1f);
        for (Room room : dungeon.rooms) {
            String role = roomRole(dungeon, room);
            if (role.isEmpty()) {
                continue;
            }
            layout.setText(font, role);
            float x = originX + room.centerX * cell + cell * 0.5f - layout.width * 0.5f;
            float y = height - (originTop + room.centerY * cell) - 2f;
            font.draw(batch, role, x, y);
        }
        font.getData().setScale(1f);
        font.setColor(0.1f, 0.1f, 0.12f, 1f);
    }

    private static String roomRole(Dungeon dungeon, Room room) {
        TileGrid grid = dungeon.grid;
        String role = "";
        for (Placement placement : dungeon.placements) {
            if (placement.roomId != room.id) {
                continue;
            }
            if (placement.type == PlacementType.SHOP) {
                return "shop";
            }
            if (placement.type == PlacementType.BOSS_SPAWN) {
                role = "boss";
            } else if (role.isEmpty() && placement.tags.contains("treasure")) {
                role = "treasure";
            }
        }
        if (!role.isEmpty()) {
            return role;
        }
        if (dungeon.stairsUp >= 0 && room.contains(grid.x(dungeon.stairsUp), grid.y(dungeon.stairsUp))) {
            return "start";
        }
        if (dungeon.stairsDown >= 0 && room.contains(grid.x(dungeon.stairsDown), grid.y(dungeon.stairsDown))) {
            return "exit";
        }
        return "";
    }

    /** Colored marks for placements, along the bottom of the map view. */
    private void drawPlacementLegend(int width, int height) {
        PlacementType[] types = PlacementType.values();
        String[] names = {
                "trap", "chest", "monster", "item", "gold",
                "light", "decor", "fountain", "shop", "boss"
        };
        int x = 12;
        int y = 28;
        font.setColor(0.1f, 0.1f, 0.12f, 1f);
        for (int i = 0; i < types.length; i++) {
            layout.setText(font, names[i]);
            int item = 14 + (int) layout.width + 14;
            if (x + item > width - 12) {
                x = 12;
                y += 18;
            }
            drawMark(types[i], x, y, 10);
            batch.setColor(Color.WHITE);
            font.setColor(0.1f, 0.1f, 0.12f, 1f);
            font.draw(batch, names[i], x + 14, y + 10);
            x += item;
        }
    }

    /** Chest gets a cross. A monster gets an X. Gold, decor, and fountains are a square outline. Shop and boss are a Z. */
    private void drawMark(PlacementType type, float x, float y, int size) {
        if (type == PlacementType.GOLD || type == PlacementType.DECOR || type == PlacementType.FOUNTAIN) {
            paintPlacement(type);
            drawSquareOutline(x, y, size);
            return;
        }
        if (type == PlacementType.SHOP || type == PlacementType.BOSS_SPAWN) {
            paintPlacement(type);
            drawZ(x, y, size);
            return;
        }
        paintPlacement(type);
        batch.draw(pixel, x, y, size, size);
        if (type == PlacementType.CHEST && size >= 4) {
            batch.setColor(0.25f, 0.12f, 0.05f, 1f);
            int thick = Math.max(1, size / 5);
            int arm = Math.max(2, size * 3 / 5);
            batch.draw(pixel, x + (size - thick) / 2f, y + (size - arm) / 2f, thick, arm);
            batch.draw(pixel, x + (size - arm) / 2f, y + (size - thick) / 2f, arm, thick);
        }
        if (type == PlacementType.MONSTER_SPAWN && size >= 4) {
            batch.setColor(0.95f, 0.9f, 0.88f, 1f);
            drawX(x, y, size);
        }
    }

    private void drawSquareOutline(float x, float y, int size) {
        int edge = Math.max(1, size / 4);
        batch.draw(pixel, x, y, size, edge);
        batch.draw(pixel, x, y + size - edge, size, edge);
        batch.draw(pixel, x, y, edge, size);
        batch.draw(pixel, x + size - edge, y, edge, size);
    }

    private void drawX(float x, float y, int size) {
        int thick = Math.max(1, size / 5);
        int inset = Math.max(1, size / 5);
        float span = size - inset * 2f - thick;
        float left = x + inset;
        float bottom = y + inset;
        if (span < 1f) {
            span = Math.max(1f, size - thick);
            left = x;
            bottom = y;
        }
        int steps = Math.max(2, Math.round(span));
        for (int i = 0; i < steps; i++) {
            float t = i / (float) (steps - 1);
            float along = span * t;
            batch.draw(pixel, left + along, bottom + along, thick, thick);
            batch.draw(pixel, left + span - along, bottom + along, thick, thick);
        }
    }

    private void drawZ(float x, float y, int size) {
        int edge = Math.max(1, size / 4);
        batch.draw(pixel, x, y + size - edge, size, edge);
        batch.draw(pixel, x, y, size, edge);
        float span = Math.max(1f, size - edge);
        int steps = Math.max(2, Math.round(span));
        for (int i = 0; i < steps; i++) {
            float t = i / (float) (steps - 1);
            float along = span * (1f - t);
            batch.draw(pixel, x + along, y + along, edge, edge);
        }
    }

    private void paintPlacement(PlacementType type) {
        switch (type) {
            case TRAP -> batch.setColor(0.9f, 0.2f, 0.2f, 1f);
            case CHEST -> batch.setColor(0.95f, 0.75f, 0.2f, 1f);
            case MONSTER_SPAWN -> batch.setColor(0.75f, 0.15f, 0.2f, 1f);
            case ITEM_SPAWN -> batch.setColor(0.3f, 0.75f, 0.4f, 1f);
            case GOLD -> batch.setColor(1f, 0.85f, 0.3f, 1f);
            case LIGHT -> batch.setColor(1f, 0.95f, 0.55f, 1f);
            case DECOR -> batch.setColor(0.6f, 0.45f, 0.3f, 1f);
            case FOUNTAIN -> batch.setColor(0.3f, 0.7f, 0.85f, 1f);
            case SHOP -> batch.setColor(0.45f, 0.55f, 0.9f, 1f);
            case BOSS_SPAWN -> batch.setColor(0.85f, 0.15f, 0.45f, 1f);
        }
    }

    private int menuX(int width) {
        return (width - MENU_W) / 2;
    }

    /** Top of a menu button, measured from the top of the window. */
    private int menuTop(int index) {
        return 200 + index * (MENU_H + 16);
    }

    /** 0 is the dungeon, 1 is the asset viewer, -1 is neither. */
    private int menuAt(int x, int y) {
        int left = menuX(Gdx.graphics.getWidth());
        if (x < left || x >= left + MENU_W) {
            return -1;
        }
        for (int i = 0; i < 2; i++) {
            int top = menuTop(i);
            if (y >= top && y < top + MENU_H) {
                return i;
            }
        }
        return -1;
    }

    /** Copies the tile type and every mark on that square. */
    private void copyDungeonTile(int screenX, int screenY) {
        if (dungeon == null) {
            return;
        }
        int width = Gdx.graphics.getWidth();
        int height = Gdx.graphics.getHeight();
        TileGrid grid = dungeon.grid;
        int header = 64;
        int footer = showPlacements ? 36 : 16;
        int cell = Math.max(2, Math.min((width - 24) / grid.width, (height - header - footer) / grid.height));
        int originX = (width - cell * grid.width) / 2;
        int originTop = header + 8;
        int x = (screenX - originX) / cell;
        int y = (screenY - originTop) / cell;
        if (cell <= 0 || !grid.inBounds(x, y)) {
            return;
        }
        String text = describeTile(x, y);
        if (Gdx.app.getClipboard() != null) {
            Gdx.app.getClipboard().setContents(text);
        }
        copiedTile = x + "," + y;
    }

    private String describeTile(int x, int y) {
        TileGrid grid = dungeon.grid;
        StringBuilder out = new StringBuilder();
        out.append(x).append(',').append(y).append(' ').append(grid.get(x, y)).append('\n');
        Room room = null;
        for (Room candidate : dungeon.rooms) {
            if (candidate.contains(x, y)) {
                room = candidate;
                break;
            }
        }
        if (room != null) {
            String role = roomRole(dungeon, room);
            out.append("room ").append(room.id);
            if (!role.isEmpty()) {
                out.append(' ').append(role);
            }
            out.append('\n');
        }
        int marks = 0;
        for (Placement placement : dungeon.placements) {
            if (placement.x != x || placement.y != y) {
                continue;
            }
            marks++;
            out.append(placement.type);
            if (placement.facing != Facing.NONE) {
                out.append(" facing ").append(placement.facing);
            }
            if (placement.trap != null) {
                out.append(' ').append(placement.trap);
            }
            if (placement.roomId >= 0) {
                out.append(" room ").append(placement.roomId);
            }
            if (placement.regionId >= 0) {
                out.append(" region ").append(placement.regionId);
            }
            if (placement.dangerTier >= 0) {
                out.append(" tier ").append(placement.dangerTier);
            }
            if (!placement.tags.isEmpty()) {
                out.append(" tags");
                for (String tag : placement.tags) {
                    out.append(' ').append(tag);
                }
            }
            out.append('\n');
        }
        if (marks == 0) {
            out.append("nothing placed\n");
        }
        return out.toString();
    }

    /** 0 is Menu, 1 is New, 10–13 are density steps, -1 is neither. */
    private int dungeonButtonAt(int x, int y) {
        if (y >= 4 && y < 4 + BTN_H) {
            if (x >= 12 && x < 12 + BTN_W) {
                return 0;
            }
            int newer = Gdx.graphics.getWidth() - 12 - BTN_W;
            if (x >= newer && x < newer + BTN_W) {
                return 1;
            }
        }
        if (y >= 28 && y < 28 + BTN_H) {
            int densityW = 52;
            int densityGap = 6;
            int densityTotal = DENSITY_STEPS.length * densityW + (DENSITY_STEPS.length - 1) * densityGap;
            int densityLeft = (Gdx.graphics.getWidth() - densityTotal) / 2;
            for (int i = 0; i < DENSITY_STEPS.length; i++) {
                int left = densityLeft + i * (densityW + densityGap);
                if (x >= left && x < left + densityW) {
                    return 10 + i;
                }
            }
        }
        return -1;
    }

    private static String densityLabel(double density) {
        if (Math.abs(density - 0.5) < 0.01) {
            return "0.5x";
        }
        if (Math.abs(density - 1.5) < 0.01) {
            return "1.5x";
        }
        return (int) density + "x";
    }

    private void beginText(int width, int height) {
        uiCamera.setToOrtho(false, width, height);
        uiCamera.update();
        batch.setProjectionMatrix(uiCamera.combined);
        batch.begin();
    }

    private void frame(float span, float viewW, float viewH) {
        float dist = Math.max(span, 1f) * 1.7f;
        camera.viewportWidth = viewW;
        camera.viewportHeight = Math.max(1f, viewH);
        // Tiles use a fixed front-right camera. The large view uses the camera
        // left by the last orbit drag, so Spin, Slide, and Scene do not reframe it.
        if (mode == Mode.VIEW) {
            camera.position.set(viewPos);
            camera.lookAt(viewLook);
        } else {
            camera.position.set(dist * 0.72f, span * 0.18f, -dist);
            camera.lookAt(0f, 0f, 0f);
        }
        camera.up.set(0f, 1f, 0f);
        camera.near = Math.max(0.05f, span * 0.01f);
        camera.far = Math.max(20f, dist * 8f);
        camera.update();
    }

    private void clampScroll(int height) {
        if (mode != Mode.BROWSER) {
            return;
        }
        if (catalog == null || catalog.error != null || shown.isEmpty()) {
            scroll = 0f;
            return;
        }
        int rows = (shown.size() + cols - 1) / cols;
        int content = HEADER + rows * (TILE_H + GAP) + GAP;
        float maxScroll = Math.max(0, content - height);
        scroll = MathUtils.clamp(scroll, 0f, maxScroll);
    }

    private int tileAt(int x, int y) {
        if (catalog.error != null) {
            return -1;
        }
        int strideX = TILE_W + GAP;
        int strideY = TILE_H + GAP;
        if (y < HEADER || x < GAP) {
            return -1;
        }
        int col = (x - GAP) / strideX;
        if (col < 0 || col >= cols) {
            return -1;
        }
        int tileX = GAP + col * strideX;
        if (x < tileX || x >= tileX + TILE_W) {
            return -1;
        }
        int contentY = y - HEADER - GAP + (int) scroll;
        if (contentY < 0) {
            return -1;
        }
        int row = contentY / strideY;
        int into = contentY - row * strideY;
        if (into >= TILE_H) {
            return -1;
        }
        int index = row * cols + col;
        if (index < 0 || index >= shown.size()) {
            return -1;
        }
        return index;
    }

    /**
     * Move the model on the ground. Screen right follows the camera's right,
     * and screen up moves it away along the ground. Yaw stays as it was.
     */
    private void slideOnGround(int dx, int dy) {
        float span = selected != null && selected.mesh != null ? selected.mesh.span : 1f;
        float dist = Math.max(span, 1f) * 1.7f;
        float viewH = Math.max(1f, camera.viewportHeight);
        float worldPerPixel = (2f * dist * MathUtils.sinDeg(25f) / MathUtils.cosDeg(25f)) / viewH;
        groundForward.set(camera.direction.x, 0f, camera.direction.z);
        if (groundForward.len2() < 1e-8f) {
            groundForward.set(0f, 0f, 1f);
        }
        groundForward.nor();
        groundRight.set(0f, 1f, 0f).crs(groundForward).nor();
        slideX += (groundRight.x * dx + groundForward.x * dy) * worldPerPixel;
        slideZ += (groundRight.z * dx + groundForward.z * dy) * worldPerPixel;
    }

    private static boolean shiftDown() {
        return Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT)
                || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT);
    }

    /** 0 is Spin, 1 is Slide, 2 is Orbit, 3 is Scene, -1 is neither. Input y is measured from the top of the window. */
    private int viewModeAt(int x, int y) {
        if (y < 4 || y >= 4 + BTN_H) {
            return -1;
        }
        int width = Gdx.graphics.getWidth();
        int orbitBtn = width - 12 - BTN_W;
        int slideBtn = orbitBtn - 6 - BTN_W;
        int spinBtn = slideBtn - 6 - BTN_W;
        int sceneBtn = spinBtn - 6 - BTN_W;
        if (x >= sceneBtn && x < sceneBtn + BTN_W) {
            return 3;
        }
        if (x >= spinBtn && x < spinBtn + BTN_W) {
            return 0;
        }
        if (x >= slideBtn && x < slideBtn + BTN_W) {
            return 1;
        }
        if (x >= orbitBtn && x < orbitBtn + BTN_W) {
            return 2;
        }
        return -1;
    }

    /** Clip index under the pointer, or -1. The list sits on the left under the path. */
    private int clipAt(int x, int y) {
        if (selected == null || selected.rig == null) {
            return -1;
        }
        if (x < 12 || x >= 12 + CLIP_W) {
            return -1;
        }
        int top = viewHeaderH;
        int n = selected.rig.clipCount();
        for (int i = 0; i < n; i++) {
            if (y >= top && y < top + BTN_H) {
                return i;
            }
            top += BTN_H + 6;
        }
        return -1;
    }

    private int viewHeaderH = 80;
    private int fieldX;
    private int fieldW;
    private int clearX;
    private int allX;
    private int npcX;

    /** Places the hint, search field, Clear, and the two filter chips on one row. */
    private void layoutHeader(int width) {
        layout.setText(font, HINT);
        fieldX = 12 + (int) Math.ceil(layout.width) + 10;
        npcX = width - 12 - BTN_W;
        allX = npcX - 6 - BTN_W;
        clearX = allX - 6 - CLEAR_W;
        fieldW = Math.max(24, clearX - 8 - fieldX);
    }

    /** 0 is All parts, 1 is NPCs, -1 is neither. */
    private int filterAt(int x, int y) {
        if (y < 4 || y >= 4 + BTN_H) {
            return -1;
        }
        layoutHeader(Gdx.graphics.getWidth());
        if (x >= allX && x < allX + BTN_W) {
            return 0;
        }
        if (x >= npcX && x < npcX + BTN_W) {
            return 1;
        }
        return -1;
    }

    private void applyFilter() {
        shown.clear();
        for (VoxCatalog.Entry entry : catalog.entries) {
            if (npcsOnly && !entry.npc) {
                continue;
            }
            if (query.length() > 0) {
                String q = query.toString().toLowerCase();
                if (!entry.name.toLowerCase().contains(q) && !entry.path.toLowerCase().contains(q)) {
                    continue;
                }
            }
            shown.add(entry);
        }
        scroll = 0f;
    }

    private void drawFilter(int height) {
        int bottom = height - 4 - BTN_H;
        chip(allX, bottom, "All parts", !npcsOnly);
        chip(npcX, bottom, "NPCs", npcsOnly);
    }

    /** 1 is the text field, 2 is Clear, -1 is neither. Input y is from the top of the window. */
    private int searchAt(int x, int y) {
        if (y < 4 || y >= 4 + BTN_H) {
            return -1;
        }
        layoutHeader(Gdx.graphics.getWidth());
        if (x >= clearX && x < clearX + CLEAR_W) {
            return 2;
        }
        if (x >= fieldX && x < fieldX + fieldW) {
            return 1;
        }
        return -1;
    }

    private void drawSearch(int height) {
        int bottom = height - 4 - BTN_H;
        batch.setColor(searchFocus ? 0.95f : 0.82f, searchFocus ? 0.96f : 0.84f, searchFocus ? 0.97f : 0.86f, 1f);
        batch.draw(pixel, fieldX, bottom, fieldW, BTN_H);
        batch.setColor(Color.WHITE);
        chip(clearX, bottom, CLEAR_W, BTN_H, "Clear", false);
        String shownText = query.length() == 0 ? "Search" : query.toString();
        font.setColor(query.length() == 0 ? 0.45f : 0.1f, query.length() == 0 ? 0.48f : 0.1f, query.length() == 0 ? 0.5f : 0.12f, 1f);
        String fitted = fit(shownText, Math.max(12, fieldW - 12));
        layout.setText(font, fitted);
        font.draw(batch, fitted, fieldX + 6, bottom + BTN_H - 4);
        if (searchFocus) {
            float caretX = query.length() == 0 ? fieldX + 6 : fieldX + 8 + layout.width;
            batch.setColor(0.15f, 0.16f, 0.18f, 1f);
            batch.draw(pixel, caretX, bottom + 3, 1, BTN_H - 6);
            batch.setColor(Color.WHITE);
        }
        font.setColor(0.1f, 0.1f, 0.12f, 1f);
    }

    private void chip(int x, int bottom, String label, boolean on) {
        chip(x, bottom, BTN_W, BTN_H, label, on);
    }

    private void chip(int x, int bottom, int width, int height, String label, boolean on) {
        batch.setColor(on ? 0.22f : 0.45f, on ? 0.28f : 0.48f, on ? 0.36f : 0.52f, 1f);
        batch.draw(pixel, x, bottom, width, height);
        batch.setColor(Color.WHITE);
        font.setColor(on ? 0.95f : 0.15f, on ? 0.96f : 0.16f, on ? 0.97f : 0.18f, 1f);
        layout.setText(font, label);
        font.draw(batch, label, x + (width - layout.width) * 0.5f, bottom + height - 3);
        font.setColor(0.1f, 0.1f, 0.12f, 1f);
    }

    private String fit(String text, float maxWidth) {
        layout.setText(font, text);
        if (layout.width <= maxWidth) {
            return text;
        }
        int lo = 0;
        int hi = text.length();
        while (lo < hi) {
            int mid = (lo + hi + 1) / 2;
            layout.setText(font, text.substring(0, mid) + "...");
            if (layout.width <= maxWidth) {
                lo = mid;
            } else {
                hi = mid - 1;
            }
        }
        return text.substring(0, lo) + "...";
    }

    /** Floor build shown on the asset loader. The first frame only draws the spinner. */
    private static final class FlyLoad {
        boolean armed;
        BlockPalette palette;
        int tileX;
        int tileY;
        LoadTrace trace;
    }

    /** One uploaded chunk. originY is the map axis; the mesh is placed at z = -originY. */
    private static final class DrawnChunk {
        final int originX;
        final int originY;
        final VoxMesher.VoxMesh mesh;

        DrawnChunk(int originX, int originY, VoxMesher.VoxMesh mesh) {
            this.originX = originX;
            this.originY = originY;
            this.mesh = mesh;
        }
    }
}
