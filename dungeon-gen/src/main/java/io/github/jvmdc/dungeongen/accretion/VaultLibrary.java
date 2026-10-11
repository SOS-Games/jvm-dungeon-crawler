package io.github.jvmdc.dungeongen.accretion;

import io.github.jvmdc.dungeongen.common.SeededRandom;
import io.github.jvmdc.dungeongen.common.Tile;
import io.github.jvmdc.dungeongen.common.TileGrid;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Hand-authored rooms loaded from {@code /vaults/*.json}. A vault is stamped into a room
 * that is large enough to hold it. Cells may be floor, wall, rock, water, or a door.
 */
public final class VaultLibrary {
    private static final String[] NAMES = {"alcove", "pillar", "pool"};

    private VaultLibrary() {
    }

    public static void stamp(TileGrid grid, List<AccretionGenerator.Node> nodes, SeededRandom rng, int count) {
        if (count <= 0 || nodes.isEmpty()) {
            return;
        }
        List<Vault> vaults = load();
        if (vaults.isEmpty()) {
            return;
        }
        int placed = 0;
        int tries = 0;
        while (placed < count && tries < count * 12) {
            tries++;
            Vault vault = vaults.get(rng.nextInt(vaults.size()));
            AccretionGenerator.Node node = nodes.get(rng.nextInt(nodes.size()));
            if (node.removed) {
                continue;
            }
            if (blit(grid, node, vault)) {
                placed++;
            }
        }
    }

    private static boolean blit(TileGrid grid, AccretionGenerator.Node node, Vault vault) {
        int vw = vault.rows[0].length;
        int vh = vault.rows.length;
        for (int[] part : node.parts) {
            if (part[2] < vw || part[3] < vh) {
                continue;
            }
            int ox = part[0];
            int oy = part[1];
            for (int y = 0; y < vh; y++) {
                for (int x = 0; x < vw; x++) {
                    int gx = ox + x;
                    int gy = oy + y;
                    if (!node.contains(gx, gy)) {
                        continue;
                    }
                    Tile tile = switch (vault.rows[y][x]) {
                        case '#' -> Tile.WALL;
                        case '.' -> Tile.ROOM_FLOOR;
                        case '~' -> Tile.WATER;
                        case 'R' -> Tile.ROCK;
                        case 'D' -> Tile.DOOR;
                        case 'S' -> Tile.SECRET_DOOR;
                        default -> null;
                    };
                    if (tile != null) {
                        grid.set(gx, gy, tile);
                    }
                }
            }
            if (vault.secret) {
                node.secret = true;
                if (node.door >= 0 && grid.get(node.door) == Tile.DOOR) {
                    grid.set(node.door, Tile.SECRET_DOOR);
                }
            }
            return true;
        }
        return false;
    }

    static List<Vault> load() {
        List<Vault> vaults = new ArrayList<>();
        for (String name : NAMES) {
            try (InputStream in = VaultLibrary.class.getResourceAsStream("/vaults/" + name + ".json")) {
                if (in == null) {
                    continue;
                }
                vaults.add(parse(name, new String(in.readAllBytes(), StandardCharsets.UTF_8)));
            } catch (IOException ignored) {
                // a missing vault is skipped; the rest of the layout still generates
            }
        }
        vaults.sort(Comparator.comparing(vault -> vault.id));
        return vaults;
    }

    private static Vault parse(String id, String text) {
        boolean secret = text.contains("\"secret\"") && text.replace(" ", "").contains("\"secret\":true");
        int gridAt = text.indexOf("\"grid\"");
        int bracket = text.indexOf('[', gridAt);
        int end = text.indexOf(']', bracket);
        String body = text.substring(bracket + 1, end);
        List<String> lines = new ArrayList<>();
        int cursor = 0;
        while (cursor < body.length()) {
            int quote = body.indexOf('"', cursor);
            if (quote < 0) {
                break;
            }
            int close = body.indexOf('"', quote + 1);
            lines.add(body.substring(quote + 1, close));
            cursor = close + 1;
        }
        char[][] rows = new char[lines.size()][];
        for (int i = 0; i < lines.size(); i++) {
            rows[i] = lines.get(i).toCharArray();
        }
        return new Vault(id, secret, rows);
    }

    record Vault(String id, boolean secret, char[][] rows) {
    }
}
