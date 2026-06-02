package ncu.cs2.my_game.map;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Random;

/**
 * Pre-generates validated normal-stage maps once per new game.
 * Enemy, item, gold, and hazard gameplay objects are still spawned by scenes.
 */
public class MapPoolManager {
    public static final int DEFAULT_POOL_SIZE = 12;
    private static final int MAX_POOL_ATTEMPTS = 120;
    private static final int RECENT_HISTORY_SIZE = 3;
    private static final int MIN_INTERIOR_WALL_TILES = 18;

    private final Random random = new Random();
    private final List<TileMap> normalMaps = new ArrayList<>();
    private final Deque<Integer> recentNormalMapIndexes = new ArrayDeque<>();

    public void generateNormalMapPool() {
        generateNormalMapPool(DEFAULT_POOL_SIZE);
    }

    public void generateNormalMapPool(int targetSize) {
        normalMaps.clear();
        recentNormalMapIndexes.clear();

        int attempts = 0;
        while (normalMaps.size() < targetSize && attempts < MAX_POOL_ATTEMPTS) {
            attempts++;
            long seed = System.nanoTime() + attempts * 1_013L;
            TileMap map = new PlatformDungeonGenerator(seed).generateLevel(2);
            if (isLegalPoolMap(map)) {
                normalMaps.add(map.copy());
            }
        }

        int fallbackAttempts = 0;
        while (normalMaps.size() < Math.min(3, targetSize) && fallbackAttempts < 60) {
            fallbackAttempts++;
            TileMap fallback = new PlatformDungeonGenerator(System.nanoTime()).generateLevel(2);
            if (isLegalPoolMap(fallback)) {
                normalMaps.add(fallback.copy());
            }
        }

        System.out.println("[MapPool] generated normal maps=" + normalMaps.size());
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

    private boolean isLegalPoolMap(TileMap map) {
        MapValidationResult mapResult = map.getMapValidationResult();
        PlatformValidationResult platformResult = map.getValidationResult();
        return mapResult != null && mapResult.isValid()
            && platformResult != null && platformResult.spawnToExitReachable()
            && countInteriorWallTiles(map) >= MIN_INTERIOR_WALL_TILES;
    }

    private int countInteriorWallTiles(TileMap map) {
        int count = 0;
        for (int y = 1; y < map.getHeightTiles() - 1; y++) {
            for (int x = 1; x < map.getWidthTiles() - 1; x++) {
                if (map.getTile(x, y) == TileType.WALL) count++;
            }
        }
        return count;
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
}
