package io.github.jvmdc.assets;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Named entries from converted manifests, then from assets/custom/manifest.
 * A custom id replaces a converted id with the same name. Only JSON is read.
 */
public final class ManifestLibrary {
    private final Map<String, JsonValue> entries = new LinkedHashMap<>();

    private ManifestLibrary() {
    }

    public static ManifestLibrary load(AssetRoots roots) throws IOException {
        ManifestLibrary library = new ManifestLibrary();
        if (Files.isDirectory(roots.assets)) {
            List<Path> manifests = new ArrayList<>();
            try (var walk = Files.walk(roots.assets)) {
                walk.filter(path -> {
                    String name = path.getFileName().toString();
                    return name.endsWith("manifest.json") && !path.startsWith(roots.custom);
                }).sorted().forEach(manifests::add);
            }
            for (Path file : manifests) {
                library.absorb(read(file));
            }
        }
        Path manifestDir = roots.custom.resolve("manifest");
        if (Files.isDirectory(manifestDir)) {
            try (var list = Files.list(manifestDir)) {
                List<Path> files = list.filter(path -> path.getFileName().toString().endsWith(".json"))
                        .sorted()
                        .toList();
                for (Path file : files) {
                    library.absorb(read(file));
                }
            }
        }
        CustomAssetRegistry registry = CustomAssetRegistry.load(roots);
        for (AssetDef asset : registry.all().values()) {
            library.entries.put(asset.id, asset.rawJson);
        }
        return library;
    }

    public JsonValue get(String id) {
        return entries.get(id);
    }

    public boolean has(String id) {
        return entries.containsKey(id);
    }

    /**
     * Dotted model name on this entry, when the JSON carries one.
     * Custom models use {@code vox}. Converted entries use {@code model}.
     */
    public String specifier(String id) {
        return firstSpecifier(entries.get(id));
    }

    private static String firstSpecifier(JsonValue node) {
        if (node == null) {
            return null;
        }
        if (node.isString()) {
            String text = node.asString();
            if (text.indexOf('.') > 0 && text.indexOf(' ') < 0 && text.indexOf('(') < 0) {
                return text;
            }
            return null;
        }
        if (node.isObject()) {
            JsonValue vox = node.get("vox");
            if (vox != null && vox.isString()) {
                return vox.asString();
            }
            JsonValue model = node.get("model");
            if (model != null && model.isString()) {
                return model.asString();
            }
        }
        for (JsonValue child = node.child; child != null; child = child.next) {
            String found = firstSpecifier(child);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private void absorb(JsonValue node) {
        if (node == null) {
            return;
        }
        if (node.isArray()) {
            for (JsonValue child = node.child; child != null; child = child.next) {
                absorb(child);
            }
            return;
        }
        if (!node.isObject()) {
            return;
        }
        JsonValue map = node.get("map");
        if (map != null && map.isObject()) {
            absorb(map);
            return;
        }
        if (isCatalog(node)) {
            if (hasNestedCatalog(node)) {
                for (JsonValue child = node.child; child != null; child = child.next) {
                    if (child.name != null && child.name.startsWith("_")) {
                        continue;
                    }
                    if (isCatalog(child)) {
                        putAll(child);
                    } else if (child.name != null) {
                        entries.put(child.name, child);
                    }
                }
            } else {
                putAll(node);
            }
            return;
        }
        for (JsonValue child = node.child; child != null; child = child.next) {
            if (child.name == null || child.name.startsWith("_")) {
                continue;
            }
            if (isCatalog(child)) {
                putAll(child);
            } else if (child.isObject() || child.isArray()) {
                entries.put(child.name, child);
            }
        }
    }

    private void putAll(JsonValue catalog) {
        for (JsonValue child = catalog.child; child != null; child = child.next) {
            if (child.name != null && !child.name.startsWith("_")) {
                entries.put(child.name, child);
            }
        }
    }

    private static boolean hasNestedCatalog(JsonValue node) {
        for (JsonValue child = node.child; child != null; child = child.next) {
            if (isCatalog(child)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isCatalog(JsonValue node) {
        if (node == null || !node.isObject() || node.child == null) {
            return false;
        }
        for (JsonValue child = node.child; child != null; child = child.next) {
            if (!child.isObject()) {
                return false;
            }
        }
        return true;
    }

    private static JsonValue read(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            return new JsonReader().parse(in);
        }
    }
}
