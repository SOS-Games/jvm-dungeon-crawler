package io.github.jvmdc;

import com.badlogic.gdx.ApplicationAdapter;

// gradlew.bat lwjgl3:run
public class JvmDcApp extends ApplicationAdapter {
    @Override
    public void create() {
        System.out.println("Hello, World!");
    }

    @Override
    public void render() {
    }
    
    @Override
    public void dispose() {
        System.out.println("Hello, World!");
    }
}
