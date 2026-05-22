package ncu.cs2.my_game.map;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Random;

import javax.imageio.ImageIO;

/**
 * Pre-generates validated normal-stage maps once per new game.
 * Enemy, item, gold, and hazard gameplay objects are still spawned by scenes.
 */
public class MapPoolManager {
    public static final int DEFAULT_POOL_SIZE = 12;
    private static final int MAX_POOL_ATTEMPTS = 120;
    private static final int RECENT_HISTORY_SIZE = 3;
    private static final int PREVIEW_SCALE = 8;
    private static final Path PREVIEW_DIR = Path.of("generated-map-pool");

    private final Random random = new Random();
    private final List<TileMap> normalMaps = new ArrayList<>();
    private final Deque<Integer> recentNormalMapIndexes = new ArrayDeque<>();

    public void generateNormalMapPool() {
        generateNormalMapPool(DEFAULT_POOL_SIZE);
    }

    public void generateNormalMapPool(int targetSize) {
        normalMaps.clear();
        recentNormalMapIndexes.clear();
        clearPreviewDirectory();

        int attempts = 0;
        while (normalMaps.size() < targetSize && attempts < MAX_POOL_ATTEMPTS) {
            attempts++;
            long seed = System.nanoTime() + attempts * 1_013L;
            TileMap map = new PlatformDungeonGenerator(seed).generateLevel(2);
            if (isLegalPoolMap(map)) {
                normalMaps.add(map.copy());
                writePreview(map, normalMaps.size(), seed);
            }
        }

        while (normalMaps.size() < Math.min(3, targetSize)) {
            TileMap fallback = new PlatformDungeonGenerator(System.nanoTime()).generateLevel(2);
            normalMaps.add(fallback.copy());
            writePreview(fallback, normalMaps.size(), System.nanoTime());
        }

        System.out.println("[MapPool] generated normal maps=" + normalMaps.size()
            + " previews=" + getPreviewDirectory().toAbsolutePath());
    }

    public TileMap pickNormalMap() {
        return pickNormalMap(random.nextLong());
    }

    public TileMap pickNormalMap(long requestSeed) {
        if (normalMaps.isEmpty()) {
            generateNormalMapPool();
        }
        int index = pickIndexAvoidingRecent(requestSeed);
        remember(index);
        return normalMaps.get(index).copy();
    }

    public int getNormalMapCount() {
        return normalMaps.size();
    }

    public Path getPreviewDirectory() {
        return PREVIEW_DIR;
    }

    private boolean isLegalPoolMap(TileMap map) {
        MapValidationResult mapResult = map.getMapValidationResult();
        PlatformValidationResult platformResult = map.getValidationResult();
        return mapResult != null && mapResult.isValid()
            && platformResult != null && platformResult.spawnToExitReachable();
    }

    private int pickIndexAvoidingRecent(long requestSeed) {
        Random picker = new Random(requestSeed);
        if (normalMaps.size() <= RECENT_HISTORY_SIZE) {
            return picker.nextInt(normalMaps.size());
        }
        for (int attempts = 0; attempts < 12; attempts++) {
            int candidate = picker.nextInt(normalMaps.size());
            if (!recentNormalMapIndexes.contains(candidate)) {
                return candidate;
            }
        }
        return picker.nextInt(normalMaps.size());
    }

    private void remember(int index) {
        recentNormalMapIndexes.addLast(index);
        while (recentNormalMapIndexes.size() > RECENT_HISTORY_SIZE) {
            recentNormalMapIndexes.removeFirst();
        }
    }

    private void clearPreviewDirectory() {
        try {
            Files.createDirectories(PREVIEW_DIR);
            try (var files = Files.list(PREVIEW_DIR)) {
                files.filter(path -> path.getFileName().toString().endsWith(".png"))
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException ignored) {
                            // Preview cleanup should never block the game.
                        }
                    });
            }
        } catch (IOException ignored) {
            // Missing previews are acceptable; maps still live in memory.
        }
    }

    private void writePreview(TileMap map, int index, long seed) {
        try {
            Files.createDirectories(PREVIEW_DIR);
            BufferedImage image = new BufferedImage(
                map.getWidthTiles() * PREVIEW_SCALE,
                map.getHeightTiles() * PREVIEW_SCALE,
                BufferedImage.TYPE_INT_ARGB
            );
            Graphics2D g = image.createGraphics();
            g.setColor(new Color(18, 19, 44));
            g.fillRect(0, 0, image.getWidth(), image.getHeight());

            for (int y = 0; y < map.getHeightTiles(); y++) {
                for (int x = 0; x < map.getWidthTiles(); x++) {
                    TileType type = map.getTile(x, y);
                    if (type == TileType.EMPTY || type == TileType.DECORATION) continue;
                    g.setColor(colorFor(type));
                    g.fillRect(x * PREVIEW_SCALE, y * PREVIEW_SCALE, PREVIEW_SCALE, PREVIEW_SCALE);
                }
            }

            g.setColor(Color.CYAN);
            g.fillOval((int) (map.getSpawnX() / TileMap.TILE_SIZE * PREVIEW_SCALE),
                (int) (map.getSpawnY() / TileMap.TILE_SIZE * PREVIEW_SCALE),
                PREVIEW_SCALE * 2, PREVIEW_SCALE * 2);

            if (map.getExitBounds() != null) {
                g.setColor(Color.MAGENTA);
                g.fillRect((int) (map.getExitBounds().getMinX() / TileMap.TILE_SIZE * PREVIEW_SCALE),
                    (int) (map.getExitBounds().getMinY() / TileMap.TILE_SIZE * PREVIEW_SCALE),
                    PREVIEW_SCALE * 2, PREVIEW_SCALE * 3);
            }

            g.setColor(Color.WHITE);
            g.drawString("map " + index + " seed " + seed, 8, 14);
            g.dispose();

            ImageIO.write(image, "png", PREVIEW_DIR.resolve(String.format("normal_map_%02d.png", index)).toFile());
        } catch (IOException ignored) {
            // Preview output is a debugging aid and should not break gameplay.
        }
    }

    private Color colorFor(TileType type) {
        return switch (type) {
            case FLOOR -> new Color(96, 58, 26);
            case WALL -> new Color(82, 80, 74);
            case PLATFORM -> new Color(32, 112, 200);
            case ONE_WAY_PLATFORM -> new Color(74, 128, 145);
            case LADDER -> new Color(155, 104, 42);
            case SPIKE -> new Color(160, 20, 24);
            case SPAWN -> Color.CYAN;
            case EXIT -> Color.MAGENTA;
            default -> Color.LIGHT_GRAY;
        };
    }
}
