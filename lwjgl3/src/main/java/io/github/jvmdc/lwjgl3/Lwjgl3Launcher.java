package io.github.jvmdc.lwjgl3;

import com.badlogic.gdx.Graphics;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3WindowAdapter;
import io.github.jvmdc.JvmDcApp;

/**
 * Desktop entry: open a GL 3.3 window, then the viewer.
 * gradlew.bat lwjgl3:run
 */
public final class Lwjgl3Launcher {
    public static void main(String[] args) {
        if (StartupHelper.startNewJvmIfRequired()) {
            return;
        }
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("JVM-Dungeon-Crawler");
        config.useVsync(true);
        Graphics.DisplayMode fullscreen = null;
        for (Graphics.DisplayMode mode : Lwjgl3ApplicationConfiguration.getDisplayModes()) {
            if (mode.width == 1920 && mode.height == 1080
                    && (fullscreen == null || mode.refreshRate > fullscreen.refreshRate)) {
                fullscreen = mode;
            }
        }
        if (fullscreen == null) {
            fullscreen = Lwjgl3ApplicationConfiguration.getDisplayMode();
        }
        config.setFullscreenMode(fullscreen);
        config.setBackBufferConfig(8, 8, 8, 8, 24, 0, 0);
        config.setOpenGLEmulation(Lwjgl3ApplicationConfiguration.GLEmulation.GL30, 3, 3);
        JvmDcApp app = new JvmDcApp();

        // later, copy the contents from jvmmw here
        new Lwjgl3Application(app, config);
    }
}
