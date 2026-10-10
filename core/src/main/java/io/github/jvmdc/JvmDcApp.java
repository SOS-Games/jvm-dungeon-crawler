package io.github.jvmdc;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Align;
import io.github.jvmdc.render.ForwardRenderer;
import io.github.jvmdc.viewer.VoxCatalog;

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
    private static final String HINT = "Scroll to browse. Click a model.";
    private static final int BTN_W = 96;
    private static final int BTN_H = 20;
    private static final long LOAD_BUDGET_NS = 8_000_000L;

    private enum Mode { BROWSER, VIEW }

    /** How a drag in the large view is used. */
    private enum Drag { SPIN, SLIDE, ORBIT }

    // Front-right camera, in units of the model's size. Orbit starts on this same view.
    private static final float CAM_X = 0.72f * 1.7f;
    private static final float CAM_Y = 0.18f;
    private static final float CAM_Z = -1.7f;
    private static final float ORBIT_RADIUS = (float) Math.sqrt(CAM_X * CAM_X + CAM_Y * CAM_Y + CAM_Z * CAM_Z);
    private static final float ORBIT_YAW0 = MathUtils.atan2(CAM_X, CAM_Z) * MathUtils.radiansToDegrees;
    private static final float ORBIT_PITCH0 = MathUtils.asin(CAM_Y / ORBIT_RADIUS) * MathUtils.radiansToDegrees;

    private ForwardRenderer renderer;
    private SpriteBatch batch;
    private BitmapFont font;
    private GlyphLayout layout;
    private OrthographicCamera uiCamera;
    private PerspectiveCamera camera;
    private final Matrix4 model = new Matrix4();
    private VoxCatalog catalog;
    private final List<VoxCatalog.Entry> shown = new ArrayList<>();
    private boolean npcsOnly;
    private final StringBuilder query = new StringBuilder();
    private boolean searchFocus;
    private Texture pixel;
    private Mode mode = Mode.BROWSER;
    private VoxCatalog.Entry selected;
    private float scroll;
    private float yaw;
    private float pitch;
    private Drag drag = Drag.SPIN;
    /** Sky backdrop and the nearby lamp. Off until the Scene button is turned on. */
    private boolean showScene;
    private float slideX;
    private float slideZ;
    private float orbitYaw = ORBIT_YAW0;
    private float orbitPitch = ORBIT_PITCH0;
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
            pressOnModeChip = mode == Mode.VIEW && viewModeAt(screenX, screenY) >= 0;
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
                    orbitYaw += dx * 0.45f;
                    orbitPitch += dy * 0.45f;
                    orbitPitch = MathUtils.clamp(orbitPitch, -89f, 89f);
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
            int dx = screenX - pressX;
            int dy = screenY - pressY;
            if (button == Input.Buttons.LEFT && mode == Mode.VIEW && dx * dx + dy * dy < 36) {
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
            if (keycode == Input.Keys.ESCAPE && searchFocus) {
                searchFocus = false;
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
        catalog = VoxCatalog.scan();
        applyFilter();
        Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pixmap.setColor(Color.WHITE);
        pixmap.fill();
        pixel = new Texture(pixmap);
        pixmap.dispose();
        renderer = new ForwardRenderer();
        batch = new SpriteBatch();
        font = new BitmapFont();
        font.setColor(0.1f, 0.1f, 0.12f, 1f);
        layout = new GlyphLayout();
        uiCamera = new OrthographicCamera();
        camera = new PerspectiveCamera(50f, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        camera.near = 0.1f;
        camera.far = 500f;
        camera.up.set(0f, 1f, 0f);
        Gdx.input.setInputProcessor(input);
        if (catalog.error == null) {
            Gdx.graphics.setTitle("JVM-Dungeon-Crawler — " + catalog.entries.size() + " models");
        }
    }

    @Override
    public void render() {
        int width = Gdx.graphics.getWidth();
        int height = Gdx.graphics.getHeight();
        cols = Math.max(1, (width - GAP) / (TILE_W + GAP));
        clampScroll(height);
        pumpLoads(height);
        if (mode == Mode.VIEW) {
            renderView(width, height);
        } else {
            renderBrowser(width, height);
        }
    }

    @Override
    public void dispose() {
        if (catalog != null) {
            catalog.dispose();
        }
        if (renderer != null) {
            renderer.dispose();
        }
        if (batch != null) {
            batch.dispose();
        }
        if (font != null) {
            font.dispose();
        }
        if (pixel != null) {
            pixel.dispose();
        }
    }

    private void open(VoxCatalog.Entry entry) {
        selected = entry;
        yaw = 0f;
        pitch = 0f;
        slideX = 0f;
        slideZ = 0f;
        orbitYaw = ORBIT_YAW0;
        orbitPitch = ORBIT_PITCH0;
        mode = Mode.VIEW;
        catalog.load(entry);
    }

    private void pumpLoads(int height) {
        if (catalog.error != null) {
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
        int chips = BTN_W * 4 + 18 + 12;
        layout.setText(font, path, Color.BLACK, width - 24 - chips, Align.left, true);
        float pathHeight = layout.height;
        int header = (int) (16 + pathHeight + 22);
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
            renderer.draw(camera, selected.mesh.parts, model);
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
        batch.end();
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
        // Positive X and negative Z is the front-right. Orbit swings around the model.
        float lookX = 0f;
        float lookZ = 0f;
        if (mode == Mode.VIEW && drag == Drag.ORBIT) {
            lookX = slideX;
            lookZ = slideZ;
            float radius = Math.max(span, 1f) * ORBIT_RADIUS;
            float cp = MathUtils.cosDeg(orbitPitch);
            camera.position.set(
                    lookX + radius * cp * MathUtils.sinDeg(orbitYaw),
                    radius * MathUtils.sinDeg(orbitPitch),
                    lookZ + radius * cp * MathUtils.cosDeg(orbitYaw));
        } else {
            camera.position.set(dist * 0.72f, span * 0.18f, -dist);
        }
        camera.lookAt(lookX, 0f, lookZ);
        camera.up.set(0f, 1f, 0f);
        camera.near = Math.max(0.05f, span * 0.01f);
        camera.far = Math.max(20f, dist * 8f);
        camera.update();
    }

    private void clampScroll(int height) {
        if (catalog.error != null || shown.isEmpty()) {
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
}
