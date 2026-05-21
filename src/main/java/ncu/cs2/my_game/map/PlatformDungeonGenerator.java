package ncu.cs2.my_game.map;

import javafx.geometry.Rectangle2D;
import ncu.cs2.my_game.Config;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Random;
import java.util.Set;

/**
 * Procedural underground platform dungeon generator.
 */
public class PlatformDungeonGenerator {
    private static final int HEIGHT_TILES = 19;
    private static final int MAX_ATTEMPTS = 40;

    private final Random random;
    private final long seed;

    public PlatformDungeonGenerator() {
        this(System.nanoTime());
    }

    public PlatformDungeonGenerator(long seed) {
        this(new Random(seed), seed);
    }

    public PlatformDungeonGenerator(Random random) {
        this(random, 0L);
    }

    private PlatformDungeonGenerator(Random random, long seed) {
        this.random = random;
        this.seed = seed;
    }

    public TileMap generateLevel(int levelIndex) {
        int widthTiles = levelIndex == 1 ? 84 : 96;
        PlayerStats stats = PlayerStats.fromConfig();
        PlatformGenerationConfig config = PlatformGenerationConfig.forLevel(levelIndex, stats);

        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            TileMap map = buildCandidate(widthTiles, levelIndex, config);
            PlatformValidationResult result = validatePlatformMap(map, stats);
            map.setValidationResult(result);
            if (result.valid()) {
                printGenerationReport(levelIndex, result);
                return map;
            }
        }

        TileMap fallback = buildCandidate(widthTiles, levelIndex, config);
        carveGuaranteedBridge(fallback);
        PlatformValidationResult result = validatePlatformMap(fallback, stats);
        fallback.setValidationResult(result);
        printGenerationReport(levelIndex, result);
        return fallback;
    }

    private TileMap buildCandidate(int widthTiles, int levelIndex, PlatformGenerationConfig config) {
        TileMap map = new TileMap(widthTiles, HEIGHT_TILES);
        buildShell(map);

        List<Rectangle2D> route = new ArrayList<>();
        int x = 1;
        int y = 15;
        int length = 8;
        route.add(addRun(map, x, y, length, TileType.FLOOR));

        while (x < widthTiles - 13) {
            int gap = config.minGapX() + random.nextInt(config.maxGapX() - config.minGapX() + 1);
            int nextLength = config.minPlatformWidth()
                + random.nextInt(config.maxPlatformWidth() - config.minPlatformWidth() + 1);
            int dy = config.minHeightDelta()
                + random.nextInt(config.maxHeightDelta() - config.minHeightDelta() + 1);
            if (random.nextDouble() < 0.25) dy += random.nextBoolean() ? 1 : -1;

            int nextY = clamp(y + dy, 5, 16);
            if (nextY < y - 2) nextY = y - 2;
            if (nextY > y + 3) nextY = y + 3;

            x += length + gap;
            if (x + nextLength >= widthTiles - 4) {
                nextLength = widthTiles - x - 4;
            }
            if (nextLength < 4) break;

            TileType type = random.nextDouble() < config.oneWayPlatformChance()
                ? TileType.ONE_WAY_PLATFORM : TileType.PLATFORM;
            Rectangle2D platform = addRun(map, x, nextY, nextLength, type);
            route.add(platform);
            maybeAddVerticalSupport(map, x, nextY);
            y = nextY;
            length = nextLength;
        }

        Rectangle2D spawnPlatform = route.get(0);
        clearMainRouteAirspace(map, route);
        map.setSpawn(spawnPlatform.getMinX() + TileMap.TILE_SIZE,
            spawnPlatform.getMinY() - Config.PLAYER_HEIGHT);
        map.setTile(2, 14, TileType.SPAWN);

        Rectangle2D exitPlatform = route.get(route.size() - 1);
        double exitX = exitPlatform.getMaxX() - TileMap.TILE_SIZE * 1.5;
        double exitY = exitPlatform.getMinY() - TileMap.TILE_SIZE * 2.2;
        map.setExitBounds(new Rectangle2D(exitX, exitY, TileMap.TILE_SIZE * 1.2, TileMap.TILE_SIZE * 2.2));
        map.setTile((int) (exitX / TileMap.TILE_SIZE), (int) (exitY / TileMap.TILE_SIZE), TileType.EXIT);

        addBranches(map, route, levelIndex, config);
        addTrapsAndDecorations(map, route, levelIndex);
        addEnemyAndRewardZones(map, route, levelIndex);
        return map;
    }

    private void clearMainRouteAirspace(TileMap map, List<Rectangle2D> route) {
        for (Rectangle2D platform : route) {
            int startX = Math.max(1, (int) (platform.getMinX() / TileMap.TILE_SIZE) - 1);
            int endX = Math.min(map.getWidthTiles() - 2, (int) (platform.getMaxX() / TileMap.TILE_SIZE) + 1);
            int platformY = (int) (platform.getMinY() / TileMap.TILE_SIZE);
            for (int y = Math.max(1, platformY - 3); y < platformY; y++) {
                for (int x = startX; x <= endX; x++) {
                    map.setTile(x, y, TileType.EMPTY);
                }
            }
        }

        for (int i = 0; i < route.size() - 1; i++) {
            Rectangle2D a = route.get(i);
            Rectangle2D b = route.get(i + 1);
            int startX = Math.max(1, (int) (Math.min(a.getMaxX(), b.getMaxX()) / TileMap.TILE_SIZE) - 1);
            int endX = Math.min(map.getWidthTiles() - 2, (int) (Math.max(a.getMinX(), b.getMinX()) / TileMap.TILE_SIZE) + 1);
            if (startX > endX) {
                int temp = startX;
                startX = endX;
                endX = temp;
            }
            int topY = Math.max(1, (int) (Math.min(a.getMinY(), b.getMinY()) / TileMap.TILE_SIZE) - 4);
            int bottomY = Math.min(map.getHeightTiles() - 2, (int) (Math.max(a.getMinY(), b.getMinY()) / TileMap.TILE_SIZE));
            for (int y = topY; y <= bottomY; y++) {
                for (int x = startX; x <= endX; x++) {
                    TileType type = map.getTile(x, y);
                    if (type == TileType.WALL || type == TileType.DECORATION) {
                        map.setTile(x, y, TileType.EMPTY);
                    }
                }
            }
        }
    }

    private void buildShell(TileMap map) {
        for (int x = 0; x < map.getWidthTiles(); x++) {
            map.setTile(x, map.getHeightTiles() - 1, TileType.FLOOR);
        }
        for (int y = 0; y < map.getHeightTiles(); y++) {
            map.setTile(0, y, TileType.WALL);
            map.setTile(map.getWidthTiles() - 1, y, TileType.WALL);
        }

        for (int x = 4; x < map.getWidthTiles() - 4; x += 7 + random.nextInt(6)) {
            int top = 3 + random.nextInt(7);
            int bottom = Math.min(map.getHeightTiles() - 2, top + 3 + random.nextInt(4));
            for (int y = top; y <= bottom; y++) {
                if (random.nextDouble() < 0.76) {
                    map.setTile(x, y, TileType.WALL);
                }
            }
        }
    }

    private Rectangle2D addRun(TileMap map, int startX, int y, int length, TileType type) {
        map.addPlatformRun(startX, y, length, type);
        return new Rectangle2D(startX * TileMap.TILE_SIZE, y * TileMap.TILE_SIZE,
            length * TileMap.TILE_SIZE, TileMap.TILE_SIZE);
    }

    private void maybeAddVerticalSupport(TileMap map, int x, int y) {
        if (random.nextDouble() > 0.45) return;
        int supportX = x + random.nextInt(2);
        for (int sy = y + 1; sy < Math.min(map.getHeightTiles() - 1, y + 4); sy++) {
            if (random.nextDouble() < 0.82) {
                map.setTile(supportX, sy, TileType.WALL);
            }
        }
    }

    private void addBranches(TileMap map, List<Rectangle2D> route, int levelIndex,
                             PlatformGenerationConfig config) {
        int branchCount = levelIndex == 1 ? 5 : 7;
        for (int i = 0; i < branchCount; i++) {
            if (random.nextDouble() > config.branchChance()) continue;
            Rectangle2D base = route.get(1 + random.nextInt(Math.max(1, route.size() - 2)));
            int direction = random.nextBoolean() ? 1 : -1;
            int length = config.minPlatformWidth()
                + random.nextInt(Math.max(1, config.branchPlatformCount()));
            int gap = Math.max(2, config.minGapX() - 1) + random.nextInt(2);
            int startX = (int) ((direction > 0 ? base.getMaxX() : base.getMinX()) / TileMap.TILE_SIZE)
                + direction * gap;
            if (direction < 0) startX -= length;
            int tileY = clamp((int) (base.getMinY() / TileMap.TILE_SIZE) + random.nextInt(5) - 2, 4, 16);
            if (startX < 2 || startX + length >= map.getWidthTiles() - 2) continue;

            TileType type = random.nextDouble() < config.oneWayPlatformChance()
                ? TileType.ONE_WAY_PLATFORM : TileType.PLATFORM;
            Rectangle2D branch = addRun(map, startX, tileY, length, type);
            map.addRewardZone(new Rectangle2D(branch.getMinX() + branch.getWidth() / 2.0 - 12,
                branch.getMinY() - 28, 24, 24));

            if (random.nextDouble() < 0.45) {
                int ladderX = startX + length / 2;
                int minY = Math.min(tileY, (int) (base.getMinY() / TileMap.TILE_SIZE));
                int maxY = Math.max(tileY, (int) (base.getMinY() / TileMap.TILE_SIZE));
                for (int y = minY + 1; y < maxY; y++) {
                    map.setTile(ladderX, y, TileType.LADDER);
                }
            }
        }
    }

    private void addTrapsAndDecorations(TileMap map, List<Rectangle2D> route, int levelIndex) {
        int trapCount = levelIndex == 1 ? 6 : 10;
        Set<Integer> mainCenters = new HashSet<>();
        for (Rectangle2D platform : route) {
            mainCenters.add((int) (platform.getMinX() / TileMap.TILE_SIZE));
        }

        for (int i = 0; i < trapCount; i++) {
            Rectangle2D platform = route.get(random.nextInt(route.size()));
            int start = (int) (platform.getMinX() / TileMap.TILE_SIZE) + 1;
            int end = (int) (platform.getMaxX() / TileMap.TILE_SIZE) - 2;
            if (end <= start) continue;
            int x = start + random.nextInt(end - start + 1);
            if (mainCenters.contains(x)) continue;
            map.setTile(x, (int) (platform.getMinY() / TileMap.TILE_SIZE) - 1, TileType.SPIKE);
        }

        for (int x = 3; x < map.getWidthTiles() - 3; x += 3 + random.nextInt(4)) {
            int y = 2 + random.nextInt(map.getHeightTiles() - 5);
            if (map.getTile(x, y) == TileType.EMPTY && random.nextDouble() < 0.5) {
                map.setTile(x, y, TileType.DECORATION);
            }
        }
    }

    private void addEnemyAndRewardZones(TileMap map, List<Rectangle2D> route, int levelIndex) {
        int enemies = levelIndex == 1 ? 2 : 5;
        for (int i = 1; i < route.size() - 1 && enemies > 0; i += Math.max(2, route.size() / (levelIndex == 1 ? 3 : 6))) {
            Rectangle2D platform = route.get(i);
            if (platform.getWidth() < TileMap.TILE_SIZE * 4) continue;
            map.addEnemyZone(platform);
            enemies--;
        }

        for (int i = 1; i < route.size() - 1; i += 4) {
            Rectangle2D platform = route.get(i);
            map.addRewardZone(new Rectangle2D(platform.getMinX() + platform.getWidth() / 2.0 - 12,
                platform.getMinY() - 28, 24, 24));
        }
    }

    private void carveGuaranteedBridge(TileMap map) {
        int y = 12;
        for (int x = 1; x < map.getWidthTiles() - 2; x += 5) {
            addRun(map, x, y, 4, TileType.ONE_WAY_PLATFORM);
        }
        map.setSpawn(TileMap.TILE_SIZE * 2, y * TileMap.TILE_SIZE - Config.PLAYER_HEIGHT);
        map.setExitBounds(new Rectangle2D((map.getWidthTiles() - 5) * TileMap.TILE_SIZE,
            y * TileMap.TILE_SIZE - 70, 40, 70));
    }

    public boolean isReachableFromSpawnToExit(TileMap map, PlayerStats stats) {
        return validatePlatformMap(map, stats).spawnToExitReachable();
    }

    public PlatformValidationResult validatePlatformMap(TileMap map, PlayerStats stats) {
        List<Rectangle2D> platforms = new ArrayList<>(map.getMainPlatforms());
        Rectangle2D exit = map.getExitBounds();
        if (platforms.isEmpty() || exit == null) {
            return new PlatformValidationResult(false, false, platforms.size(), 0,
                platforms.size(), 0, map.getRewardZones().size(), seed,
                "missing platforms or exit", List.copyOf(platforms));
        }

        int start = platformContainingX(platforms, map.getSpawnX());
        int goal = platformContainingX(platforms, exit.getMinX());
        if (start < 0 || goal < 0) {
            return new PlatformValidationResult(false, false, platforms.size(), 0,
                platforms.size(), 0, map.getRewardZones().size(), seed,
                "spawn or exit is not on a platform", List.copyOf(platforms));
        }

        boolean[] visited = new boolean[platforms.size()];
        int[] depth = new int[platforms.size()];
        Queue<Integer> queue = new ArrayDeque<>();
        visited[start] = true;
        queue.add(start);

        boolean spawnToExitReachable = false;
        while (!queue.isEmpty()) {
            int current = queue.remove();
            if (current == goal) spawnToExitReachable = true;
            Rectangle2D from = platforms.get(current);

            for (int next = 0; next < platforms.size(); next++) {
                if (visited[next] || next == current) continue;
                if (canJumpBetween(from, platforms.get(next), stats)) {
                    visited[next] = true;
                    depth[next] = depth[current] + 1;
                    queue.add(next);
                }
            }
        }

        List<Rectangle2D> unreachable = new ArrayList<>();
        int reachable = 0;
        for (int i = 0; i < platforms.size(); i++) {
            if (visited[i]) {
                reachable++;
            } else {
                unreachable.add(platforms.get(i));
            }
        }
        boolean enoughPlatformsUseful = reachable >= Math.ceil(platforms.size() * 0.70);
        boolean rewardsReachable = areRewardsReachable(map, platforms, visited);
        boolean valid = spawnToExitReachable && enoughPlatformsUseful && rewardsReachable;
        String reason = valid ? "ok"
            : !spawnToExitReachable ? "spawn cannot reach exit"
            : !enoughPlatformsUseful ? "less than 70% of platforms are reachable"
            : "reward platform unreachable";
        return new PlatformValidationResult(valid, spawnToExitReachable, platforms.size(), reachable,
            unreachable.size(), depth[goal], map.getRewardZones().size(), seed, reason,
            List.copyOf(unreachable));
    }

    private boolean areRewardsReachable(TileMap map, List<Rectangle2D> platforms, boolean[] visited) {
        for (Rectangle2D reward : map.getRewardZones()) {
            int index = platformContainingX(platforms, reward.getMinX() + reward.getWidth() / 2.0);
            if (index < 0 || !visited[index]) return false;
        }
        return true;
    }

    private void printGenerationReport(int levelIndex, PlatformValidationResult result) {
        System.out.println("[DungeonMap] level=" + levelIndex
            + " seed=" + result.seed()
            + " platforms=" + result.platformCount()
            + " mainPathLength=" + result.mainPathLength()
            + " branches=" + result.branchCount()
            + " unreachable=" + result.unreachablePlatformCount()
            + " valid=" + result.valid()
            + " reason=" + result.reason());
    }

    private boolean canJumpBetween(Rectangle2D from, Rectangle2D to, PlayerStats stats) {
        double gap;
        if (to.getMinX() > from.getMaxX()) {
            gap = to.getMinX() - from.getMaxX();
        } else if (from.getMinX() > to.getMaxX()) {
            gap = from.getMinX() - to.getMaxX();
        } else {
            gap = 0;
        }

        double verticalUp = from.getMinY() - to.getMinY();
        double verticalDrop = to.getMinY() - from.getMinY();
        double horizontalLimit = stats.maxHorizontalJumpDistance();
        if (stats.doubleJump()) horizontalLimit *= 1.25;

        return gap <= horizontalLimit
            && verticalUp <= stats.maxVerticalJumpHeight() * 0.85
            && verticalDrop <= stats.maxVerticalJumpHeight() * 1.4;
    }

    private int platformContainingX(List<Rectangle2D> platforms, double x) {
        for (int i = 0; i < platforms.size(); i++) {
            Rectangle2D platform = platforms.get(i);
            if (x >= platform.getMinX() && x <= platform.getMaxX()) {
                return i;
            }
        }
        return -1;
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
