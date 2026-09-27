package io.github.jvmdc;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.Align;
import io.github.jvmdc.render.ForwardRenderer;
import io.github.jvmdc.viewer.VoxCatalog;

// gradlew.bat lwjgl3:run
public class JvmDcApp extends ApplicationAdapter {
    private static final int TILE_W = 196;
    private static final int TILE_H = 188;
    private static final int LABEL_H = 28;
    private static final int GAP = 12;
    private static final int HEADER = 28;
    private static final long LOAD_BUDGET_NS = 8_000_000L;

    private enum Mode { BROWSER, VIEW }

    private ForwardRenderer renderer;
    private SpriteBatch batch;
    private BitmapFont font;
    private GlyphLayout layout;
    private OrthographicCamera uiCamera;
    private PerspectiveCamera camera;
    private final Matrix4 model = new Matrix4();
    private VoxCatalog catalog;
    private Mode mode = Mode.BROWSER;
    private VoxCatalog.Entry selected;
    private float scroll;
    private float yaw;
    private float pitch;
    private int cols = 1;

    private final InputAdapter input = new InputAdapter() {
        private int pressX;
        private int pressY;
        private int lastX;
        private int lastY;
        private int button = -1;

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
            return true;
        }

        @Override
        public boolean touchDragged(int screenX, int screenY, int pointer) {
            if (mode == Mode.VIEW && button == Input.Buttons.LEFT) {
                yaw += (screenX - lastX) * 0.45f;
                pitch += (screenY - lastY) * 0.45f;
                pitch = MathUtils.clamp(pitch, -89f, 89f);
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
            if (button == Input.Buttons.LEFT && mode == Mode.BROWSER && dx * dx + dy * dy < 36) {
                int index = tileAt(screenX, screenY);
                if (index >= 0) {
                    open(catalog.entries.get(index));
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
            return false;
        }
    };

    @Override
    public void create() {
        catalog = VoxCatalog.scan();
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
    }

    private void open(VoxCatalog.Entry entry) {
        selected = entry;
        yaw = 0f;
        pitch = 0f;
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
        int count = catalog.entries.size();
        for (int row = firstRow; row <= lastRow; row++) {
            for (int col = 0; col < cols; col++) {
                int index = row * cols + col;
                if (index >= count || System.nanoTime() >= deadline) {
                    return;
                }
                catalog.load(catalog.entries.get(index));
            }
        }
    }

    private void renderBrowser(int width, int height) {
        renderer.begin();
        int strideX = TILE_W + GAP;
        int strideY = TILE_H + GAP;
        int previewH = TILE_H - LABEL_H;
        int count = catalog.entries.size();
        if (catalog.error == null) {
            for (int i = 0; i < count; i++) {
                int col = i % cols;
                int row = i / cols;
                int tileX = GAP + col * strideX;
                int tileTop = HEADER + GAP + row * strideY - (int) scroll;
                if (tileTop + TILE_H < 0 || tileTop > height) {
                    continue;
                }
                VoxCatalog.Entry entry = catalog.entries.get(i);
                int glY = height - (tileTop + previewH);
                renderer.tile(tileX, glY, TILE_W, previewH);
                if (entry.mesh != null && entry.mesh.parts.length > 0) {
                    frame(entry.mesh.span, TILE_W, previewH);
                    model.idt();
                    renderer.draw(camera, entry.mesh.parts, model);
                }
            }
        }
        renderer.end();
        beginText(width, height);
        font.setColor(0.1f, 0.1f, 0.12f, 1f);
        if (catalog.error != null) {
            font.draw(batch, catalog.error, 16, height - 16);
        } else {
            font.draw(batch, "Scroll to browse. Click a model.", 12, height - 8);
            for (int i = 0; i < count; i++) {
                int col = i % cols;
                int row = i / cols;
                int tileX = GAP + col * strideX;
                int tileTop = HEADER + GAP + row * strideY - (int) scroll;
                if (tileTop + TILE_H < -8 || tileTop > height) {
                    continue;
                }
                String name = fit(catalog.entries.get(i).name, TILE_W - 8);
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
        layout.setText(font, path, Color.BLACK, width - 24, Align.left, true);
        float pathHeight = layout.height;
        int header = (int) (16 + pathHeight + 22);
        int viewH = Math.max(1, height - header);
        renderer.begin();
        renderer.tile(0, 0, width, viewH);
        if (selected != null && selected.mesh != null && selected.mesh.parts.length > 0) {
            frame(selected.mesh.span, width, viewH);
            model.idt();
            model.rotate(0f, 1f, 0f, yaw);
            model.rotate(1f, 0f, 0f, pitch);
            renderer.draw(camera, selected.mesh.parts, model);
        }
        renderer.end();
        beginText(width, height);
        font.setColor(0.08f, 0.08f, 0.1f, 1f);
        font.draw(batch, layout, 12, height - 8);
        font.setColor(0.22f, 0.24f, 0.28f, 1f);
        String hint = selected != null && selected.failed
                ? "Could not read this file. Esc to go back."
                : "Drag to rotate. Esc or right-click to go back.";
        font.draw(batch, hint, 12, height - 12 - pathHeight);
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
        camera.position.set(dist * 0.72f, span * 0.18f, dist);
        camera.lookAt(0f, 0f, 0f);
        camera.up.set(0f, 1f, 0f);
        camera.near = Math.max(0.05f, span * 0.01f);
        camera.far = Math.max(20f, dist * 8f);
        camera.update();
    }

    private void clampScroll(int height) {
        if (catalog.error != null || catalog.entries.isEmpty()) {
            scroll = 0f;
            return;
        }
        int rows = (catalog.entries.size() + cols - 1) / cols;
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
        if (index < 0 || index >= catalog.entries.size()) {
            return -1;
        }
        return index;
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
