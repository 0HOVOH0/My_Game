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
 *
 * <p>Route shape: four vertical zones that naturally rise left-to-right.
 * Within each zone the path walks up AND down for organic feel; zone transitions
 * provide the overall upward trend.  Zone-1 guiding walls and an upper-route layer
 * encourage vertical exploration and reduce clustering in the lower half.
 */
public class PlatformDungeonGenerator {
    private static final int HEIGHT_TILES = 20;
    private static final int MAX_ATTEMPTS = 60;
    private static final double SAFE_JUMP_SPEED_FACTOR = 0.70;
    private static final double SAFE_JUMP_HEIGHT_FACTOR = 0.70;
    private static final double RECOVERY_JUMP_HEIGHT_FACTOR = 0.80;
    private static final double SAFE_DROP_HEIGHT_FACTOR = 1.20;
    private static final double JUMP_EDGE_MARGIN = 10.0;
    private static final int MIN_WALL_GAP_TILES = 3;
    private static final double WALL_CONNECTION_CHANCE = 0.24;

    private record OptionalPlatform(Rectangle2D bounds, TileType type) { }

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
            TileMap map = buildCandidate(widthTiles, levelIndex, config, stats);
            PlatformValidationResult result = validatePlatformMap(map, stats);
            MapValidationResult mapResult = validateGeneratedMap(map, result, attempt);
            map.setValidationResult(result);
            map.setMapValidationResult(mapResult);
            if (mapResult.isValid()) {
                printGenerationReport(levelIndex, result);
                return map;
            }
            System.out.println("Map generation failed: " + mapResult.reason());
        }

        TileMap fallback = new TileMap(widthTiles, HEIGHT_TILES);
        buildShell(fallback);
        carveGuaranteedBridge(fallback);
        PlatformValidationResult result = validatePlatformMap(fallback, stats);
        MapValidationResult mapResult = validateGeneratedMap(fallback, result, MAX_ATTEMPTS);
        fallback.setValidationResult(result);
        fallback.setMapValidationResult(mapResult);
        printGenerationReport(levelIndex, result);
        return fallback;
    }

    private TileMap buildCandidate(int widthTiles, int levelIndex, PlatformGenerationConfig config,
                                   PlayerStats stats) {
        TileMap map = new TileMap(widthTiles, HEIGHT_TILES);
        buildShell(map);

        // Establish endpoint reserves before building the maze so the wall pass cannot
        // consume the spawn or right-hand exit region.
        map.setSpawn(TileMap.TILE_SIZE * 2, 12 * TileMap.TILE_SIZE - Config.PLAYER_HEIGHT);
        map.setExitBounds(new Rectangle2D((widthTiles - 7) * TileMap.TILE_SIZE,
            3 * TileMap.TILE_SIZE, TileMap.TILE_SIZE * 1.2, TileMap.TILE_SIZE * 2.2));

        // Phase 1: the maze is fixed first. Platforms added later must route around it.
        addLShapedWalls(map, List.of(), levelIndex);
        addGuidingWalls(map, List.of());
        clearFloorCorridor(map);

        // Phase 2: plan a real jumpable route through the finished wall geometry.
        List<Rectangle2D> route = List.of();
        for (int routeAttempt = 0; routeAttempt < 12 && route.isEmpty(); routeAttempt++) {
            route = planMainRoute(map, widthTiles, config, stats);
        }
        if (route.isEmpty()) {
            return map;
        }
        placeMainRoute(map, route, config);

        Rectangle2D spawnPlatform = route.get(0);
        map.setSpawn(spawnPlatform.getMinX() + TileMap.TILE_SIZE,
            spawnPlatform.getMinY() - Config.PLAYER_HEIGHT);
        map.setTile(2, 11, TileType.SPAWN);

        // Exit must land on the planned main route to guarantee BFS reachability.
        List<Rectangle2D> mainRoute = new ArrayList<>(route);
        Rectangle2D exitPlatform = chooseExitPlatform(map, mainRoute);
        double exitX = Math.max(exitPlatform.getMinX() + TileMap.TILE_SIZE,
            exitPlatform.getMaxX() - TileMap.TILE_SIZE * 1.2);
        double exitY = exitPlatform.getMinY() - TileMap.TILE_SIZE * 2.2;
        map.setExitBounds(new Rectangle2D(exitX, exitY, TileMap.TILE_SIZE * 1.2, TileMap.TILE_SIZE * 2.2));
        map.setTile((int) (exitX / TileMap.TILE_SIZE), (int) (exitY / TileMap.TILE_SIZE), TileType.EXIT);

        // Phase 3: optional exploration paths and relay platforms only after the main path.
        List<OptionalPlatform> optionalPlatforms = new ArrayList<>();
        addUpperRoute(map, route, levelIndex, stats);
        optionalPlatforms.addAll(addConnectorPlatforms(map, route, levelIndex, stats));
        addBranches(map, route, levelIndex, config, stats, optionalPlatforms);
        addRouteArchitecture(map, route, levelIndex);
        addUpperFloatingWalls(map, route, levelIndex);
        addFloorPocketRecoveryPlatforms(map, route, stats);
        pruneRedundantOptionalPlatforms(map, route, optionalPlatforms, stats);

        // Phase 4: dynamic content is allowed only once terrain is genuinely traversable.
        PlatformValidationResult terrainValidation = validatePlatformMap(map, stats);
        if (!terrainValidation.spawnToExitReachable()) {
            return map;
        }
        addEnemyAndRewardZones(map, route, levelIndex, terrainValidation.unreachablePlatforms());
        addTrapsAndDecorations(map, route, levelIndex);
        return map;
    }

    /**
     * Adds stepping-stone platforms in the upper half of the map (y=4–8) starting from
     * the highest main-route platform in the right portion.  Each stone is 2–3 tiles
     * higher than the previous, keeping vertical gaps within the player's jump limit.
     * These platforms give the exit a high position and create an explorable upper layer.
     */
    private void addUpperRoute(TileMap map, List<Rectangle2D> route, int levelIndex,
                               PlayerStats stats) {
        // Start from the highest existing platform in the right 60% of the map.
        int mapRightStartTile = (int) (map.getWidthTiles() * 0.60);
        Rectangle2D base = null;
        for (Rectangle2D p : route) {
            int tileX = (int) (p.getMinX() / TileMap.TILE_SIZE);
            if (tileX >= mapRightStartTile && (base == null || p.getMinY() < base.getMinY())) {
                base = p;
            }
        }
        if (base == null) return;

        PlacementValidator validator = new PlacementValidator(map);
        Rectangle2D current = base;
        int stoneCount = 2 + random.nextInt(2);  // 2-3 deliberate high ledges

        for (int attempt = 0; attempt < stoneCount * 6 && stoneCount > 0; attempt++) {
            int baseX = (int) (current.getMinX() / TileMap.TILE_SIZE);
            int baseY = (int) (current.getMinY() / TileMap.TILE_SIZE);
            int newY = baseY - (2 + random.nextInt(2));  // 2–3 tiles higher (within jump limit)
            if (newY < 4) continue;

            int dx = (random.nextBoolean() ? 1 : -1) * (2 + random.nextInt(5));
            int startX = clamp(baseX + dx, 3, map.getWidthTiles() - 10);
            int pLength = 3 + random.nextInt(4);
            if (startX + pLength >= map.getWidthTiles() - 2) continue;
            if (!canPlaceConnector(map, startX, newY, pLength)) continue;
            Rectangle2D candidate = platformBounds(startX, newY, pLength);
            if (!canJumpBetween(map, current, candidate, stats)
                    || !canJumpBetween(map, candidate, current, stats)) {
                continue;
            }

            TileType type = random.nextDouble() < 0.5 ? TileType.ONE_WAY_PLATFORM : TileType.PLATFORM;
            Rectangle2D newPlat = addRun(map, startX, newY, pLength, type);
            route.add(newPlat);

            Rectangle2D reward = new Rectangle2D(
                newPlat.getMinX() + newPlat.getWidth() / 2.0 - 12,
                newPlat.getMinY() - 28, 24, 24);
            if (validator.isValidRewardPlacement(reward)) {
                map.addRewardZone(reward);
            }
            current = newPlat;
            stoneCount--;
        }
    }

    /**
     * Selects the rightmost valid main-route platform. The goal door should represent
     * the end of exploration, rather than appearing early on a decorative high ledge.
     */
    private Rectangle2D chooseExitPlatform(TileMap map, List<Rectangle2D> route) {
        PlacementValidator validator = new PlacementValidator(map);

        List<Rectangle2D> valid = new ArrayList<>();
        for (Rectangle2D platform : route) {
            double exitX = Math.max(platform.getMinX() + TileMap.TILE_SIZE,
                platform.getMaxX() - TileMap.TILE_SIZE * 1.2);
            double exitY = platform.getMinY() - TileMap.TILE_SIZE * 2.2;
            if (validator.isValidExitPlacement(
                    new Rectangle2D(exitX, exitY, TileMap.TILE_SIZE * 1.2, TileMap.TILE_SIZE * 2.2))) {
                valid.add(platform);
            }
        }
        if (!valid.isEmpty()) {
            valid.sort((a, b) -> Double.compare(b.getMaxX(), a.getMaxX()));
            return valid.get(0);
        }

        // Fallback: rightmost valid (original behaviour)
        for (int i = route.size() - 1; i >= 0; i--) {
            Rectangle2D platform = route.get(i);
            double exitX = Math.max(platform.getMinX() + TileMap.TILE_SIZE,
                platform.getMaxX() - TileMap.TILE_SIZE * 1.2);
            double exitY = platform.getMinY() - TileMap.TILE_SIZE * 2.2;
            if (validator.isValidExitPlacement(
                    new Rectangle2D(exitX, exitY, TileMap.TILE_SIZE * 1.2, TileMap.TILE_SIZE * 2.2))) {
                return platform;
            }
        }
        return route.get(route.size() - 1);
    }

    /**
     * Returns [yMin, yMax, yCenter] for the zone that owns this horizontal progress (0–1).
     * Zone 1 (0–30%): low, near ground. Zone 4 (80–100%): upper, near exit.
     */
    /**
     * [yMin, yMax, yCenter] for each horizontal zone.
     * Zones are narrower so the climb distributes evenly rather than bunching at the end.
     * Within each zone the random walk goes both up AND down, giving organic movement.
     */
    private int[] getZoneRange(double progress) {
        // yMax kept ≤13 so no route platform spawns close enough to the floor
        // to create a low-ceiling trap (PLAYER_HEIGHT=42px: wall at y=16 blocks standing).
        if (progress < 0.22) return new int[]{ 9, 13, 11}; // Zone 1: mid, never below y=13
        if (progress < 0.47) return new int[]{ 5, 11,  8}; // Zone 2: transition climb
        if (progress < 0.72) return new int[]{ 4,  9,  6}; // Zone 3: upper-mid
        return new int[]{ 3,  6,  4};                       // Zone 4: near top
    }

    /**
     * Plans the guaranteed path only after all maze walls have been fixed in place.
     * Rectangle instances are geometry proposals here; no platform tile is written until
     * every consecutive jump has passed wall clearance and player-reach validation.
     */
    private List<Rectangle2D> planMainRoute(TileMap map, int widthTiles,
                                            PlatformGenerationConfig config, PlayerStats stats) {
        List<Rectangle2D> route = new ArrayList<>();
        int x = 1;
        int y = 12;
        // Keep the first landing fully inside the reserved spawn zone.  Maze walls are
        // free to begin after it, but can never consume the player's first jump surface.
        int length = 6;
        Rectangle2D start = platformBounds(x, y, length);
        if (!canPlaceRoutePlatform(map, start)) {
            return List.of();
        }
        route.add(start);

        int platformsPlaced = 0;
        while (x < widthTiles - 5) {
            int gap = config.minGapX() + random.nextInt(config.maxGapX() - config.minGapX() + 1);
            int nextLength = config.minPlatformWidth()
                + random.nextInt(config.maxPlatformWidth() - config.minPlatformWidth() + 1);
            double progress = (double) x / Math.max(1, widthTiles - 5);
            int[] zone = getZoneRange(progress);
            int diff = zone[2] - y;
            int dy;
            if (platformsPlaced < 3) {
                dy = -(2 + random.nextInt(2));
            } else if (Math.abs(diff) >= 4) {
                dy = Integer.signum(diff) * (2 + random.nextInt(2));
            } else {
                dy = random.nextBoolean() ? -2 : 2;
            }
            int targetY = clamp(y + dy, zone[0], zone[1]);

            x += length + gap;
            if (x + nextLength > widthTiles - 1) {
                nextLength = widthTiles - x - 1;
            }
            if (nextLength < 3) break;

            Rectangle2D previous = route.get(route.size() - 1);
            Rectangle2D next = findReachableRoutePlatform(map, previous, x, targetY,
                nextLength, 3, map.getHeightTiles() - 4, stats);
            if (next == null) {
                return List.of();
            }
            route.add(next);
            x = (int) (next.getMinX() / TileMap.TILE_SIZE);
            y = (int) (next.getMinY() / TileMap.TILE_SIZE);
            length = nextLength;
            platformsPlaced++;
        }

        if (route.size() < 5
                || route.get(route.size() - 1).getMaxX() < map.getWorldWidth() * 0.987) {
            return List.of();
        }
        return route;
    }

    private Rectangle2D findReachableRoutePlatform(TileMap map, Rectangle2D previous,
                                                    int startX, int targetY, int length,
                                                    int zoneMin, int zoneMax, PlayerStats stats) {
        // Start from the preferred zone height, then widen into alternate passages when
        // a maze wall closes that lane. This makes the route genuinely follow the maze.
        int[] offsets = {0, -1, 1, -2, 2, -3, 3, -4, 4, -5, 5, -6, 6, -7, 7};
        int[] horizontalOffsets = {0, -2, 2, -4, 4, -6, 6};
        int previousY = (int) (previous.getMinY() / TileMap.TILE_SIZE);
        int requiredSeparation = Math.max(1,
            (int) Math.ceil((double) Config.PLAYER_HEIGHT / TileMap.TILE_SIZE));
        for (int horizontalOffset : horizontalOffsets) {
            int candidateX = startX + horizontalOffset;
            double gap = candidateX * TileMap.TILE_SIZE - previous.getMaxX();
            if (candidateX <= 2 || candidateX + length > map.getWidthTiles() - 1
                    || gap < requiredSeparation * TileMap.TILE_SIZE) {
                continue;
            }
            for (int offset : offsets) {
                int tileY = clamp(targetY + offset, zoneMin, zoneMax);
                if (Math.abs(tileY - previousY) < requiredSeparation) continue;
                Rectangle2D candidate = platformBounds(candidateX, tileY, length);
                if (canPlaceRoutePlatform(map, candidate)
                        && canJumpBetween(map, previous, candidate, stats)) {
                    return candidate;
                }
            }
        }
        return null;
    }

    private Rectangle2D platformBounds(int startX, int tileY, int length) {
        return new Rectangle2D(startX * TileMap.TILE_SIZE, tileY * TileMap.TILE_SIZE,
            length * TileMap.TILE_SIZE, TileMap.TILE_SIZE);
    }

    private boolean canPlaceRoutePlatform(TileMap map, Rectangle2D platform) {
        int startX = (int) (platform.getMinX() / TileMap.TILE_SIZE);
        int endX = (int) (platform.getMaxX() / TileMap.TILE_SIZE) - 1;
        int platformY = (int) (platform.getMinY() / TileMap.TILE_SIZE);
        for (int x = startX; x <= endX; x++) {
            if (map.getTile(x, platformY) != TileType.EMPTY) return false;
            for (int y = Math.max(1, platformY - 3); y < platformY; y++) {
                if (map.getTile(x, y).isSolid()) return false;
            }
        }
        return true;
    }

    private void placeMainRoute(TileMap map, List<Rectangle2D> route,
                                PlatformGenerationConfig config) {
        for (int i = 0; i < route.size(); i++) {
            Rectangle2D platform = route.get(i);
            int startX = (int) (platform.getMinX() / TileMap.TILE_SIZE);
            int tileY = (int) (platform.getMinY() / TileMap.TILE_SIZE);
            int length = (int) (platform.getWidth() / TileMap.TILE_SIZE);
            TileType type = i == 0 ? TileType.FLOOR
                : random.nextDouble() < config.oneWayPlatformChance()
                    ? TileType.ONE_WAY_PLATFORM : TileType.PLATFORM;
            addRun(map, startX, tileY, length, type);
        }
    }

    /**
     * Places 4–6 floor-rising guiding walls spread evenly across the first 70% of the map.
     * Wall height is calibrated to the zone at each position: the wall top sits 1–2 tiles
     * BELOW the zone's platform center, so the player can always jump over the wall from an
     * adjacent route platform.  This creates traversable barriers — not impassable blockades.
     */
    private void addGuidingWalls(TileMap map, List<Rectangle2D> route) {
        int coverEnd = (int) (map.getWidthTiles() * 0.92);
        PlacementValidator validator = new PlacementValidator(map);
        int wallCount = 6 + random.nextInt(3); // 6-8 separated walls, including the final zone
        int spacing = Math.max(7, coverEnd / (wallCount + 1));

        int spawnClearEnd = (int) (map.getWidthTiles() * 0.20); // don't place near spawn

        for (int w = 0; w < wallCount; w++) {
            int wallX = spacing * (w + 1) + random.nextInt(5) - 2;
            wallX = clamp(wallX, Math.max(6, spawnClearEnd), coverEnd - 3);

            // Most columns anchor to the floor; a smaller set remains suspended
            // to preserve overhead cover and crouch routes.
            boolean groundedWall = random.nextDouble() < 0.66;
            int wallFloorLimit = groundedWall
                ? map.getHeightTiles() - 2
                : map.getHeightTiles() - 4;
            if (validator.isInSpawnSafeZone(wallX, wallFloorLimit)) continue;
            if (validator.isInExitSafeZone(wallX, wallFloorLimit)) continue;

            // Wall top = zone center + 1–2 tiles downward (higher y = lower on screen).
            // Route platforms in this zone sit at or above the wall top, so a normal
            // platform-to-platform jump easily clears the wall.
            double progress = (double) wallX / Math.max(1, map.getWidthTiles() - 4);
            int[] zone = getZoneRange(progress);
            int zoneCenter = zone[2];
            int wallTopY = clamp(zoneCenter + 1 + random.nextInt(2), 7, wallFloorLimit - 3);

            int thickness = 2 + random.nextInt(3);
            List<int[]> tiles = new ArrayList<>();
            for (int tileY = wallTopY; tileY <= wallFloorLimit; tileY++) {
                for (int thickX = wallX; thickX < wallX + thickness; thickX++) {
                    tiles.add(new int[] { thickX, tileY });
                }
            }
            boolean allowConnection = random.nextDouble() < WALL_CONNECTION_CHANCE;
            if (!canPlaceWallShape(map, route, validator, tiles, allowConnection)) continue;
            for (int[] tile : tiles) {
                map.setTile(tile[0], tile[1], TileType.WALL);
            }
        }
    }

    /**
     * Adds 5–7 thick horizontal wall slabs (wall-as-floor sections) in the first 65%
     * of the map.  Each slab is 5–8 tiles wide and 2 tiles thick — a solid stone ledge
     * the player must jump onto rather than walk around.  Placed after airspace clearing
     * so they are not erased, and validated against the route corridor.
     */
    private void addWallFloorSections(TileMap map, List<Rectangle2D> route) {
        int xLimit = (int) (map.getWidthTiles() * 0.65);
        PlacementValidator validator = new PlacementValidator(map);
        int toPlace = 6 + random.nextInt(3); // 6–8 slabs

        for (int attempt = 0; attempt < toPlace * 14 && toPlace > 0; attempt++) {
            int slabW = 5 + random.nextInt(4);
            int slabX = 4 + random.nextInt(Math.max(1, xLimit - slabW - 4));
            int slabTopY = 9 + random.nextInt(6); // top of slab at y=9–14
            int slabThick = 2;

            if (slabX + slabW >= map.getWidthTiles() - 2) continue;
            if (slabTopY + slabThick >= map.getHeightTiles() - 1) continue;
            if (validator.isInSpawnSafeZone(slabX, slabTopY)) continue;
            if (validator.isInExitSafeZone(slabX, slabTopY)) continue;

            // Reject only if the slab sits in the 2-tile direct headroom above a platform
            // (player needs 2 tiles clearance to stand; slab farther above is fine as a ledge).
            boolean conflict = false;
            for (Rectangle2D p : route) {
                int py = (int) (p.getMinY() / TileMap.TILE_SIZE);
                int px = (int) (p.getMinX() / TileMap.TILE_SIZE);
                int pxEnd = (int) (p.getMaxX() / TileMap.TILE_SIZE);
                boolean xOvlp = slabX <= pxEnd + 1 && slabX + slabW >= px - 1;
                boolean yConflict = slabTopY >= py - 2 && slabTopY < py;
                if (xOvlp && yConflict) { conflict = true; break; }
            }
            if (conflict) continue;

            // Reject if the slab blocks the jump corridor between consecutive platforms
            boolean blocksRoute = false;
            for (int i = 0; i < route.size() - 1; i++) {
                Rectangle2D a = route.get(i), b = route.get(i + 1);
                int cMinX = (int) (Math.min(a.getMaxX(), b.getMinX()) / TileMap.TILE_SIZE) - 1;
                int cMaxX = (int) (Math.max(a.getMinX(), b.getMaxX()) / TileMap.TILE_SIZE) + 1;
                int cTopY  = (int) (Math.min(a.getMinY(), b.getMinY()) / TileMap.TILE_SIZE) - 3;
                int cBotY  = (int) (Math.max(a.getMinY(), b.getMinY()) / TileMap.TILE_SIZE);
                if (slabX <= cMaxX && slabX + slabW >= cMinX
                        && slabTopY >= cTopY && slabTopY <= cBotY) {
                    blocksRoute = true; break;
                }
            }
            if (blocksRoute) continue;

            // Place slab tiles
            boolean placed = false;
            for (int yt = slabTopY; yt < slabTopY + slabThick; yt++) {
                for (int xt = slabX; xt < slabX + slabW; xt++) {
                    if (!isProtectedRouteTile(route, xt, yt)) {
                        TileType existing = map.getTile(xt, yt);
                        if (existing == TileType.EMPTY || existing == TileType.WALL) {
                            map.setTile(xt, yt, TileType.WALL);
                            placed = true;
                        }
                    }
                }
            }
            if (!placed) continue;

            // Vertical wall support hanging from one side of the slab
            int wallX = random.nextBoolean() ? slabX : slabX + slabW - 1;
            int wallH = 2 + random.nextInt(4);
            for (int yt = slabTopY + slabThick;
                    yt < Math.min(map.getHeightTiles() - 1, slabTopY + slabThick + wallH); yt++) {
                if (!isProtectedRouteTile(route, wallX, yt)) {
                    TileType existing = map.getTile(wallX, yt);
                    if (existing == TileType.EMPTY || existing == TileType.WALL) {
                        map.setTile(wallX, yt, TileType.WALL);
                    }
                }
            }
            toPlace--;
        }
    }

    /**
     * Adds 2 rows of WALL tiles directly below a route platform to give it a
     * dungeon-ledge appearance (thick stone shelf instead of a thin floating plank).
     * These tiles are below the platform so they never block the player's headroom.
     */
    private void addPlatformLedge(TileMap map, int startX, int platformY, int len) {
        for (int sy = platformY + 1; sy <= Math.min(platformY + 2, map.getHeightTiles() - 2); sy++) {
            for (int sx = startX; sx < startX + len; sx++) {
                if (map.getTile(sx, sy) == TileType.EMPTY) {
                    map.setTile(sx, sy, TileType.WALL);
                }
            }
        }
    }

    /**
     * Guarantees the player can always escape the floor after a fall.
     *
     * <p>PLAYER_HEIGHT=42px, so a wall at tile-y=16 (pixels 512–543) overlaps the player's
     * head when standing on the floor (feet at y=18, head ≈pixel 534). Any platform ledge
     * wall at y≤16 creates a low-ceiling trap. The only safe escape height is y=15 (3 tiles
     * above the floor = 96px, well within the 127.5px jump limit).
     *
     * <p>Two passes:
     *  1. First-level escape at y=15 — directly reachable from the floor in one jump.
     *  2. Relay platform at y=12  — bridges from y=15 up to the main route (Zone 1 starts
     *     at y=11–13). Without this, a player on the escape platform at y=15 might have
     *     no nearby platform at the right height to continue upward.
     */
    private void addFloorEscapePlatforms(TileMap map, List<Rectangle2D> route) {
        PlacementValidator validator = new PlacementValidator(map);
        // y=15: 3 tiles above floor (y=18). Jump from floor = 96px < 127.5px max. Safe.
        int escapeY   = map.getHeightTiles() - 4; // y=15 for 19-tile map
        // y=12: relay step between escape (y=15) and Zone-1 route (y=9–13). 3 tiles up. ✓
        int relayY    = map.getHeightTiles() - 7; // y=12 for 19-tile map
        int scanStep  = 5;

        // Pass 1: first-level escape platforms at y=15
        for (int checkX = 3; checkX < map.getWidthTiles() - 6; checkX += scanStep) {
            boolean hasLowPlatform = false;
            for (Rectangle2D p : route) {
                int py    = (int) (p.getMinY() / TileMap.TILE_SIZE);
                int px    = (int) (p.getMinX() / TileMap.TILE_SIZE);
                int pxEnd = (int) (p.getMaxX() / TileMap.TILE_SIZE);
                // Platform at y≥escapeY means it is within one jump of the floor
                if (px - 4 <= checkX && checkX <= pxEnd + 4 && py >= escapeY) {
                    hasLowPlatform = true;
                    break;
                }
            }
            if (hasLowPlatform) continue;
            if (validator.isInSpawnSafeZone(checkX, escapeY)) continue;
            if (validator.isInExitSafeZone(checkX, escapeY)) continue;
            if (canPlaceConnector(map, checkX, escapeY, 3)) {
                route.add(addRun(map, checkX, escapeY, 3, TileType.ONE_WAY_PLATFORM));
            }
        }

        // Pass 2: relay platforms at y=12 wherever mid-level (y=10–14) coverage is missing
        for (int checkX = 3; checkX < map.getWidthTiles() - 6; checkX += scanStep) {
            boolean hasMidPlatform = false;
            for (Rectangle2D p : route) {
                int py    = (int) (p.getMinY() / TileMap.TILE_SIZE);
                int px    = (int) (p.getMinX() / TileMap.TILE_SIZE);
                int pxEnd = (int) (p.getMaxX() / TileMap.TILE_SIZE);
                if (px - 4 <= checkX && checkX <= pxEnd + 4 && py >= 10 && py <= 14) {
                    hasMidPlatform = true;
                    break;
                }
            }
            if (hasMidPlatform) continue;
            if (validator.isInSpawnSafeZone(checkX, relayY)) continue;
            if (validator.isInExitSafeZone(checkX, relayY)) continue;
            if (canPlaceConnector(map, checkX, relayY, 3)) {
                route.add(addRun(map, checkX, relayY, 3, TileType.ONE_WAY_PLATFORM));
            }
        }
    }

    /**
     * Turns selected route platforms into stone rooms and ledges after the
     * guaranteed route exists. Added masonry is below validated standing
     * surfaces, so the finished map reads as architecture rather than a cloud
     * of floating boards while final reachability still rejects blocked maps.
     */
    private void addRouteArchitecture(TileMap map, List<Rectangle2D> route, int levelIndex) {
        PlacementValidator validator = new PlacementValidator(map);
        int target = Math.min(8, 4 + levelIndex / 2);
        int placed = 0;
        for (int i = 1; i < route.size() - 1 && placed < target; i++) {
            Rectangle2D platform = route.get(i);
            int platformX = (int) (platform.getMinX() / TileMap.TILE_SIZE);
            int platformY = (int) (platform.getMinY() / TileMap.TILE_SIZE);
            int length = (int) (platform.getWidth() / TileMap.TILE_SIZE);
            if (length < 4 || random.nextDouble() > 0.62) continue;

            int supportWidth = Math.max(2, length - 1 - random.nextInt(Math.min(2, length - 2)));
            int supportOffset = random.nextBoolean() ? 0 : length - supportWidth;
            int supportHeight = 2 + random.nextInt(Math.min(4, Math.max(2, map.getHeightTiles() - platformY - 3)));
            List<int[]> masonry = new ArrayList<>();
            for (int y = platformY + 1;
                    y <= Math.min(map.getHeightTiles() - 2, platformY + supportHeight); y++) {
                for (int x = platformX + supportOffset; x < platformX + supportOffset + supportWidth; x++) {
                    masonry.add(new int[] { x, y });
                }
            }
            if (!canPlaceArchitecture(map, route, platform, validator, masonry)) continue;
            for (int[] tile : masonry) {
                map.setTile(tile[0], tile[1], TileType.WALL);
            }
            placed++;
        }
    }

    private boolean canPlaceArchitecture(TileMap map, List<Rectangle2D> route, Rectangle2D ownPlatform,
                                         PlacementValidator validator, List<int[]> masonry) {
        for (int[] tile : masonry) {
            int x = tile[0];
            int y = tile[1];
            if (validator.isInSpawnSafeZone(x, y) || validator.isInExitSafeZone(x, y)) return false;
            if (map.getTile(x, y) != TileType.EMPTY) return false;
            for (Rectangle2D platform : route) {
                if (platform == ownPlatform) continue;
                int otherY = (int) (platform.getMinY() / TileMap.TILE_SIZE);
                boolean overlaps = x * TileMap.TILE_SIZE < platform.getMaxX()
                    && (x + 1) * TileMap.TILE_SIZE > platform.getMinX();
                if (overlaps && y >= otherY - 3 && y <= otherY) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Adds ceiling masonry in the upper half so validated maps still have
     * overhead cover and separated chambers. The jump corridor around every
     * planned route platform remains protected.
     */
    private void addUpperFloatingWalls(TileMap map, List<Rectangle2D> route, int levelIndex) {
        PlacementValidator validator = new PlacementValidator(map);
        int target = 4 + Math.min(2, levelIndex / 3);
        int placed = 0;
        for (int attempt = 0; attempt < target * 18 && placed < target; attempt++) {
            int length = 4 + random.nextInt(5);
            int topY = 1 + random.nextInt(3);
            int capThickness = 1 + random.nextInt(2);
            int dropHeight = 2 + random.nextInt(4);
            int stemWidth = 1 + random.nextInt(2);
            int x = 9 + random.nextInt(Math.max(1, map.getWidthTiles() - length - 18));
            int stemX = random.nextBoolean() ? x : x + length - stemWidth;
            List<int[]> masonry = new ArrayList<>();
            for (int y = topY; y < topY + capThickness; y++) {
                for (int tileX = x; tileX < x + length; tileX++) {
                    masonry.add(new int[] { tileX, y });
                }
            }
            for (int y = topY + capThickness; y < topY + capThickness + dropHeight; y++) {
                for (int tileX = stemX; tileX < stemX + stemWidth; tileX++) {
                    masonry.add(new int[] { tileX, y });
                }
            }
            if (!canPlaceUpperFloatingWall(map, route, validator, masonry)) continue;
            for (int[] tile : masonry) {
                map.setTile(tile[0], tile[1], TileType.WALL);
            }
            placed++;
        }
    }

    private boolean canPlaceUpperFloatingWall(TileMap map, List<Rectangle2D> route,
                                              PlacementValidator validator, List<int[]> masonry) {
        Set<String> candidateTiles = new HashSet<>();
        for (int[] tile : masonry) {
            candidateTiles.add(tile[0] + ":" + tile[1]);
        }
        for (int[] tile : masonry) {
            int x = tile[0];
            int y = tile[1];
            if (x <= 1 || x >= map.getWidthTiles() - 2 || y <= 0 || y >= map.getHeightTiles() / 2) {
                return false;
            }
            if (validator.isInSpawnSafeZone(x, y) || validator.isInExitSafeZone(x, y)
                    || map.getTile(x, y) != TileType.EMPTY || isProtectedRouteTile(route, x, y)) {
                return false;
            }
            for (int nearbyY = y - 1; nearbyY <= y + 1; nearbyY++) {
                for (int nearbyX = x - 1; nearbyX <= x + 1; nearbyX++) {
                    if (candidateTiles.contains(nearbyX + ":" + nearbyY)) continue;
                    if (map.isInside(nearbyX, nearbyY)
                            && map.getTile(nearbyX, nearbyY) == TileType.WALL) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /**
     * Adds a low rescue step and a relay inside each isolated walkable floor pocket.
     * Ground-connected maze columns may form pits; every pit wide enough for the
     * player must provide a climb back toward the generated route.
     */
    private void addFloorPocketRecoveryPlatforms(TileMap map, List<Rectangle2D> route,
                                                 PlayerStats stats) {
        int walkY = map.getHeightTiles() - 2;
        // The first rescue ledge must stay inside the conservative 70% jump limit.
        // Two tiles above the floor remains easy to clear without demanding a max jump.
        int escapeY = map.getHeightTiles() - 3;
        List<Rectangle2D> safeReturnRoute = collectPlatformsLeadingToGoal(map, route, stats);
        int x = 1;
        while (x < map.getWidthTiles() - 1) {
            while (x < map.getWidthTiles() - 1 && map.getTile(x, walkY).isSolid()) x++;
            int start = x;
            while (x < map.getWidthTiles() - 1 && !map.getTile(x, walkY).isSolid()) x++;
            int end = x - 1;
            int width = end - start + 1;
            if (width < 2 || canEscapePocketToPlatforms(map, route, start, end, escapeY,
                    safeReturnRoute, stats)) {
                continue;
            }

            carveRecoveryShaft(map, start, end, escapeY);
            List<Rectangle2D> wallTopTargets = collectNearbyWallTopsLeadingToGoal(map, start, end,
                safeReturnRoute, stats);
            Rectangle2D previous = findLowEscapeInPocket(route, start, end, escapeY);
            Rectangle2D floorSurface = previous == null
                ? centeredFloorPocketSurface(map, start, end)
                : floorLaunchSurfaceForPlatform(map, start, end, previous);
            if (previous != null && !canRecoveryJumpBetween(map, floorSurface, previous, stats)) {
                previous = null;
                floorSurface = centeredFloorPocketSurface(map, start, end);
            }
            Rectangle2D jumpOrigin = previous == null ? floorSurface : previous;
            double anchorCenter = previous == null
                ? (start + end + 1) / 2.0
                : previous.getMinX() / TileMap.TILE_SIZE
                    + previous.getWidth() / TileMap.TILE_SIZE / 2.0;
            int stepY = previous == null ? escapeY
                : (int) (previous.getMinY() / TileMap.TILE_SIZE) - 2;
            for (int step = 0; step < 8; step++, stepY -= 3) {
                if (stepY <= 2 || (previous != null
                        && canReturnToGoalRoute(map, previous, wallTopTargets, safeReturnRoute, stats))) {
                    break;
                }
                int length = randomRecoveryLength(width);
                int stepX = clamp((int) Math.round(anchorCenter - length / 2.0),
                    start, end - length + 1);
                Rectangle2D placed = addOffsetRecoveryStep(map, route, stepX, stepY, length,
                    start, end, jumpOrigin, stats);
                if (placed == null) break;
                previous = placed;
                jumpOrigin = placed;
                anchorCenter = placed.getMinX() / TileMap.TILE_SIZE
                    + placed.getWidth() / TileMap.TILE_SIZE / 2.0;
            }
        }
    }

    /**
     * Opens a narrow vertical route inside a sealed floor pocket before placing steps.
     * L-shaped caps may otherwise leave a visible platform below a solid ceiling,
     * producing a fall-in trap that cannot be escaped through normal movement.
     */
    private void carveRecoveryShaft(TileMap map, int pocketStart, int pocketEnd, int escapeY) {
        int width = pocketEnd - pocketStart + 1;
        int shaftWidth = Math.min(7, Math.max(1, width / 2));
        int shaftX = pocketStart + Math.max(0, (width - shaftWidth) / 2);
        for (int y = 2; y <= escapeY; y++) {
            for (int x = shaftX; x < shaftX + shaftWidth; x++) {
                TileType type = map.getTile(x, y);
                if (type == TileType.WALL || type == TileType.DECORATION
                        || type == TileType.SPIKE) {
                    map.setTile(x, y, TileType.EMPTY);
                }
            }
        }
    }

    private int randomRecoveryLength(int pocketWidth) {
        int maxLength = Math.min(7, Math.max(1, pocketWidth / 2));
        return maxLength <= 3 ? maxLength : 3 + random.nextInt(maxLength - 2);
    }

    private Rectangle2D addOffsetRecoveryStep(TileMap map, List<Rectangle2D> route, int baseX,
                                              int tileY, int length, int pocketStart, int pocketEnd,
                                              Rectangle2D previousStep, PlayerStats stats) {
        int direction = random.nextBoolean() ? 1 : -1;
        // Try a visible offset on one side, then immediately fall back to the
        // opposite open side if the preferred side is blocked by masonry.
        int[] offsets = { direction * 2, -direction * 2, direction * 3, -direction * 3,
            direction, -direction, 0 };
        int maxX = pocketEnd - length + 1;
        for (int offset : offsets) {
            int candidateX = clamp(baseX + offset, pocketStart, maxX);
            Rectangle2D candidate = platformBounds(candidateX, tileY, length);
            if (canPlaceConnector(map, candidateX, tileY, length)
                    && (previousStep == null || canRecoveryJumpBetween(map, previousStep, candidate, stats))) {
                Rectangle2D step = addRun(map, candidateX, tileY, length, TileType.ONE_WAY_PLATFORM);
                route.add(step);
                return step;
            }
        }
        return null;
    }

    private boolean canConnectToRoute(TileMap map, Rectangle2D step,
                                      List<Rectangle2D> existingRoute, PlayerStats stats) {
        for (Rectangle2D platform : existingRoute) {
            if (platform == step || platform.equals(step)) {
                return true;
            }
            if (canJumpBetween(map, step, platform, stats)) {
                return true;
            }
        }
        return false;
    }

    private boolean canReturnToGoalRoute(TileMap map, Rectangle2D step,
                                         List<Rectangle2D> wallTops,
                                         List<Rectangle2D> safeRoute, PlayerStats stats) {
        return canRecoveryConnectToRoute(map, step, wallTops, stats)
            || canRecoveryConnectToRoute(map, step, safeRoute, stats);
    }

    private boolean canRecoveryConnectToRoute(TileMap map, Rectangle2D step,
                                              List<Rectangle2D> route, PlayerStats stats) {
        for (Rectangle2D platform : route) {
            if (platform == step || platform.equals(step)
                    || canRecoveryJumpBetween(map, step, platform, stats)) {
                return true;
            }
        }
        return false;
    }

    private Rectangle2D centeredFloorPocketSurface(TileMap map, int startX, int endX) {
        int floorY = map.getHeightTiles() - 1;
        int length = Math.min(3, endX - startX + 1);
        int tileX = startX + Math.max(0, (endX - startX + 1 - length) / 2);
        return platformBounds(tileX, floorY, length);
    }

    private Rectangle2D floorLaunchSurfaceForPlatform(TileMap map, int startX, int endX,
                                                       Rectangle2D destination) {
        int length = Math.min(3, endX - startX + 1);
        double destinationCenter = destination.getMinX() / TileMap.TILE_SIZE
            + destination.getWidth() / TileMap.TILE_SIZE / 2.0;
        int tileX = clamp((int) Math.round(destinationCenter - length / 2.0),
            startX, endX - length + 1);
        return platformBounds(tileX, map.getHeightTiles() - 1, length);
    }

    private boolean hasLowEscapeInPocket(List<Rectangle2D> platforms, int startX, int endX,
                                         int minimumTileY) {
        return findLowEscapeInPocket(platforms, startX, endX, minimumTileY) != null;
    }

    private Rectangle2D findLowEscapeInPocket(List<Rectangle2D> platforms, int startX, int endX,
                                              int minimumTileY) {
        for (Rectangle2D platform : platforms) {
            int tileY = (int) (platform.getMinY() / TileMap.TILE_SIZE);
            int platformStart = (int) (platform.getMinX() / TileMap.TILE_SIZE);
            int platformEnd = (int) (platform.getMaxX() / TileMap.TILE_SIZE) - 1;
            if (tileY >= minimumTileY && platformEnd >= startX && platformStart <= endX) {
                return platform;
            }
        }
        return null;
    }

    private boolean canEscapePocketToPlatforms(TileMap map, List<Rectangle2D> platforms,
                                               int start, int end, int minimumTileY,
                                               List<Rectangle2D> safeRoute, PlayerStats stats) {
        for (Rectangle2D platform : platforms) {
            int tileY = (int) (platform.getMinY() / TileMap.TILE_SIZE);
            int left = (int) (platform.getMinX() / TileMap.TILE_SIZE);
            int right = (int) (platform.getMaxX() / TileMap.TILE_SIZE) - 1;
            if (tileY >= minimumTileY && right >= start && left <= end
                    && canConnectToRoute(map, platform, safeRoute, stats)) {
                return true;
            }
        }
        return false;
    }

    private List<Rectangle2D> collectPlatformsLeadingToGoal(TileMap map,
                                                              List<Rectangle2D> platforms,
                                                              PlayerStats stats) {
        List<Rectangle2D> safe = new ArrayList<>();
        int goal = platformContainingX(platforms,
            map.getExitBounds().getMinX() + map.getExitBounds().getWidth() / 2.0);
        if (goal < 0) return safe;

        Queue<Integer> queue = new ArrayDeque<>();
        boolean[] visited = new boolean[platforms.size()];
        visited[goal] = true;
        queue.add(goal);
        while (!queue.isEmpty()) {
            int destination = queue.remove();
            for (int candidate = 0; candidate < platforms.size(); candidate++) {
                if (!visited[candidate] && canJumpBetween(map, platforms.get(candidate),
                        platforms.get(destination), stats)) {
                    visited[candidate] = true;
                    queue.add(candidate);
                }
            }
        }
        for (int i = 0; i < platforms.size(); i++) {
            if (visited[i]) safe.add(platforms.get(i));
        }
        return safe;
    }

    private List<Rectangle2D> collectNearbyWallTopsLeadingToGoal(TileMap map, int pocketStart,
                                                                  int pocketEnd,
                                                                  List<Rectangle2D> safeRoute,
                                                                  PlayerStats stats) {
        List<Rectangle2D> wallTops = new ArrayList<>();
        double pocketCenter = (pocketStart + pocketEnd + 1) * TileMap.TILE_SIZE / 2.0;
        for (int y = 2; y < map.getHeightTiles() - 2; y++) {
            int x = 1;
            while (x < map.getWidthTiles() - 1) {
                if (!isClearWallTopTile(map, x, y)) {
                    x++;
                    continue;
                }
                int start = x;
                while (x < map.getWidthTiles() - 1 && isClearWallTopTile(map, x, y)) x++;
                int length = x - start;
                if (length < 2) continue;
                Rectangle2D wallTop = platformBounds(start, y, length);
                double horizontalDistance = Math.abs(
                    wallTop.getMinX() + wallTop.getWidth() / 2.0 - pocketCenter);
                if (horizontalDistance > TileMap.TILE_SIZE * 14) continue;
                if (canConnectToRoute(map, wallTop, safeRoute, stats)) {
                    wallTops.add(wallTop);
                }
            }
        }
        wallTops.sort((a, b) -> Double.compare(
            Math.abs(a.getMinX() + a.getWidth() / 2.0 - pocketCenter),
            Math.abs(b.getMinX() + b.getWidth() / 2.0 - pocketCenter)));
        return wallTops;
    }

    private boolean isClearWallTopTile(TileMap map, int x, int y) {
        if (map.getTile(x, y) != TileType.WALL || map.getTile(x, y - 1) != TileType.EMPTY) {
            return false;
        }
        return map.getTile(x, y - 2) == TileType.EMPTY;
    }

    /**
     * Clears only floor-level hazards. Grounded wall columns are intentionally
     * preserved so the route planner must bridge or route around real maze walls.
     */
    private void clearFloorCorridor(TileMap map) {
        int y16 = map.getHeightTiles() - 3; // tile y=16
        int y17 = map.getHeightTiles() - 2; // tile y=17
        for (int x = 1; x < map.getWidthTiles() - 1; x++) {
            TileType t = map.getTile(x, y16);
            if (t == TileType.DECORATION || t == TileType.SPIKE) {
                map.setTile(x, y16, TileType.EMPTY);
            }
            t = map.getTile(x, y17);
            if (t == TileType.DECORATION || t == TileType.SPIKE) {
                map.setTile(x, y17, TileType.EMPTY);
            }
        }
    }

    private void clearSpawnSafeZone(TileMap map) {
        if (map.getSpawnSafeZone() == null) return;
        for (int y = 1; y < map.getHeightTiles() - 1; y++) {
            for (int x = 1; x < map.getWidthTiles() - 1; x++) {
                if (map.getSpawnSafeZone().containsTile(x, y)) {
                    TileType type = map.getTile(x, y);
                    if (type == TileType.SPIKE || type == TileType.DECORATION || type == TileType.WALL) {
                        map.setTile(x, y, TileType.EMPTY);
                    }
                }
            }
        }
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

    private void addLShapedWalls(TileMap map, List<Rectangle2D> route, int levelIndex) {
        // The route is planned after walls, so keep enough open pockets for jump landings.
        // Guiding columns below add the remaining maze density without sealing every lane.
        int count = 4 + Math.min(5, levelIndex);
        PlacementValidator validator = new PlacementValidator(map);
        for (int i = 0; i < count * 5 && count > 0; i++) {
            int arm = 3 + random.nextInt(5 + Math.min(3, levelIndex / 2));
            int height = 3 + random.nextInt(5 + Math.min(4, levelIndex / 2));
            int thickness = 2 + random.nextInt(3);
            boolean fromFloor = random.nextDouble() < 0.66;
            boolean mirror = random.nextBoolean();
            boolean inverted = random.nextBoolean();
            int x = 4 + random.nextInt(Math.max(1, map.getWidthTiles() - arm - 8));
            // Grounded L walls touch the walkable floor; inverted variants keep
            // their horizontal cap at the top while the stem reaches the ground.
            int anchorY = fromFloor
                ? map.getHeightTiles() - 2
                : 1 + random.nextInt(Math.max(1, map.getHeightTiles() / 3));

            List<int[]> tiles = lWallTiles(x, anchorY, arm, height, thickness,
                fromFloor, mirror, inverted);
            boolean allowConnection = random.nextDouble() < WALL_CONNECTION_CHANCE;
            if (canPlaceWallShape(map, route, validator, tiles, allowConnection)) {
                for (int[] tile : tiles) {
                    map.setTile(tile[0], tile[1], TileType.WALL);
                }
                count--;
            }
        }
    }

    private List<OptionalPlatform> addConnectorPlatforms(TileMap map, List<Rectangle2D> route,
                                                          int levelIndex, PlayerStats stats) {
        List<OptionalPlatform> optionalPlatforms = new ArrayList<>();
        int extra = Math.min(3, 1 + levelIndex / 2);
        for (int i = 0; i < route.size() - 1 && extra > 0; i++) {
            Rectangle2D from = route.get(i);
            Rectangle2D to = route.get(i + 1);
            double gap = to.getMinX() - from.getMaxX();
            if (gap < TileMap.TILE_SIZE * 2.2) continue;

            int length = 2 + random.nextInt(3);
            int startX = (int) ((from.getMaxX() + gap * (0.35 + random.nextDouble() * 0.25)) / TileMap.TILE_SIZE);
            int fromY = (int) (from.getMinY() / TileMap.TILE_SIZE);
            int toY = (int) (to.getMinY() / TileMap.TILE_SIZE);
            int midY = clamp((fromY + toY) / 2 + (random.nextBoolean() ? 1 : -1), 4, 16);
            if (Math.abs(midY - fromY) * TileMap.TILE_SIZE < Config.PLAYER_HEIGHT) {
                midY = clamp(fromY + (toY >= fromY ? 2 : -2), 4, 16);
            }
            if (startX <= 2 || startX + length >= map.getWidthTiles() - 2) continue;
            Rectangle2D connector = platformBounds(startX, midY, length);
            boolean linksRoute = canJumpBetween(map, from, connector, stats)
                && canJumpBetween(map, connector, to, stats);
            boolean returnsFromDetour = canJumpBetween(map, to, connector, stats)
                && canJumpBetween(map, connector, from, stats);
            if (canPlaceConnector(map, startX, midY, length)
                    && (linksRoute || returnsFromDetour)) {
                Rectangle2D platform = addRun(map, startX, midY, length, TileType.ONE_WAY_PLATFORM);
                optionalPlatforms.add(new OptionalPlatform(platform, TileType.ONE_WAY_PLATFORM));
                extra--;
            }
        }
        return optionalPlatforms;
    }

    private boolean canPlaceConnector(TileMap map, int startX, int tileY, int length) {
        for (int x = startX; x < startX + length; x++) {
            if (map.getTile(x, tileY) != TileType.EMPTY) return false;
            for (int y = Math.max(1, tileY - 3); y <= tileY; y++) {
                if (map.getTile(x, y).isSolid()) return false;
            }
        }
        return true;
    }

    private List<int[]> lWallTiles(int x, int anchorY, int arm, int height, int thickness,
                                   boolean fromFloor, boolean mirror, boolean inverted) {
        List<int[]> tiles = new ArrayList<>();
        int stemStartX = mirror ? x + arm - thickness : x;
        int verticalStart = fromFloor ? anchorY - height + 1 : anchorY;
        int verticalEnd = fromFloor ? anchorY : anchorY + height - 1;
        for (int y = verticalStart; y <= verticalEnd; y++) {
            for (int dx = 0; dx < thickness; dx++) {
                tiles.add(new int[] { stemStartX + dx, y });
            }
        }

        int armY;
        if (fromFloor) {
            armY = inverted ? verticalStart : anchorY;
        } else {
            armY = inverted ? verticalEnd : anchorY;
        }
        for (int dx = 0; dx < arm; dx++) {
            for (int dy = 0; dy < thickness; dy++) {
                tiles.add(new int[] { x + dx, fromFloor ? armY - dy : armY + dy });
            }
        }
        return tiles;
    }

    private boolean canPlaceWallShape(TileMap map, List<Rectangle2D> route,
                                      PlacementValidator validator, List<int[]> tiles,
                                      boolean allowConnection) {
        Set<String> candidateTiles = new HashSet<>();
        for (int[] tile : tiles) {
            candidateTiles.add(tile[0] + ":" + tile[1]);
        }
        Set<String> nearbyWalls = new HashSet<>();
        for (int[] tile : tiles) {
            int x = tile[0];
            int y = tile[1];
            if (x <= 1 || x >= map.getWidthTiles() - 2 || y <= 0 || y >= map.getHeightTiles() - 1) {
                return false;
            }
            if (validator.isInSpawnSafeZone(x, y) || validator.isInExitSafeZone(x, y)) {
                return false;
            }
            TileType type = map.getTile(x, y);
            if (type != TileType.EMPTY) {
                return false;
            }
            if (isProtectedRouteTile(route, x, y)) {
                return false;
            }
            for (int nearbyY = y - MIN_WALL_GAP_TILES; nearbyY <= y + MIN_WALL_GAP_TILES; nearbyY++) {
                for (int nearbyX = x - MIN_WALL_GAP_TILES; nearbyX <= x + MIN_WALL_GAP_TILES; nearbyX++) {
                    if (!map.isInside(nearbyX, nearbyY)
                            || candidateTiles.contains(nearbyX + ":" + nearbyY)
                            || nearbyX == 0 || nearbyX == map.getWidthTiles() - 1) {
                        continue;
                    }
                    if (map.getTile(nearbyX, nearbyY) == TileType.WALL) {
                        nearbyWalls.add(nearbyX + ":" + nearbyY);
                    }
                }
            }
        }
        if (nearbyWalls.isEmpty()) return true;
        return allowConnection && nearbyWalls.size() <= Math.max(4, tiles.size() / 4);
    }

    private boolean isProtectedRouteTile(List<Rectangle2D> route, int tileX, int tileY) {
        double x = tileX * TileMap.TILE_SIZE;
        for (Rectangle2D platform : route) {
            int platformY = (int) (platform.getMinY() / TileMap.TILE_SIZE);
            boolean overlapsX = x + TileMap.TILE_SIZE > platform.getMinX() - Config.PLAYER_WIDTH
                && x < platform.getMaxX() + Config.PLAYER_WIDTH;
            boolean inHeadroom = tileY >= platformY - 3 && tileY <= platformY;
            if (overlapsX && inHeadroom) return true;
        }
        for (int i = 0; i < route.size() - 1; i++) {
            Rectangle2D a = route.get(i);
            Rectangle2D b = route.get(i + 1);
            double minX = Math.min(a.getMaxX(), b.getMaxX()) - Config.PLAYER_WIDTH;
            double maxX = Math.max(a.getMinX(), b.getMinX()) + Config.PLAYER_WIDTH;
            int topY = (int) (Math.min(a.getMinY(), b.getMinY()) / TileMap.TILE_SIZE) - 4;
            int bottomY = (int) (Math.max(a.getMinY(), b.getMinY()) / TileMap.TILE_SIZE) + 1;
            boolean inGapX = x + TileMap.TILE_SIZE > minX && x < maxX;
            boolean inGapY = tileY >= topY && tileY <= bottomY;
            if (inGapX && inGapY) return true;
        }
        return false;
    }

    private void addBranches(TileMap map, List<Rectangle2D> route, int levelIndex,
                             PlatformGenerationConfig config, PlayerStats stats,
                             List<OptionalPlatform> optionalPlatforms) {
        if (route.size() < 3) return;
        int targetBranches = levelIndex == 1 ? 7 : 11;
        int placed = 0;
        for (int attempt = 0; attempt < targetBranches * 5 && placed < targetBranches; attempt++) {
            if (random.nextDouble() > Math.min(0.94, config.branchChance() + 0.18)) continue;
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
            if (!canPlaceConnector(map, startX, tileY, length)) continue;
            Rectangle2D candidate = platformBounds(startX, tileY, length);
            if (!canJumpBetween(map, base, candidate, stats)
                    || !canJumpBetween(map, candidate, base, stats)) {
                continue;
            }

            TileType type = random.nextDouble() < config.oneWayPlatformChance()
                ? TileType.ONE_WAY_PLATFORM : TileType.PLATFORM;
            Rectangle2D branch = addRun(map, startX, tileY, length, type);
            placed++;
            Rectangle2D reward = new Rectangle2D(branch.getMinX() + branch.getWidth() / 2.0 - 12,
                branch.getMinY() - 28, 24, 24);
            boolean hasReward = false;
            if (new PlacementValidator(map).isValidRewardPlacement(reward)) {
                map.addRewardZone(reward);
                hasReward = true;
            }
            if (!hasReward) {
                optionalPlatforms.add(new OptionalPlatform(branch, type));
            }

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

    /**
     * Removes empty platforms which do not contribute to a route toward the exit or rewards.
     * Each removal is still validated against reachability and pit-recovery rules before it sticks.
     */
    private void pruneRedundantOptionalPlatforms(TileMap map, List<Rectangle2D> route,
                                                 List<OptionalPlatform> earlyCandidates,
                                                 PlayerStats stats) {
        int floorEscapeY = map.getHeightTiles() - 3;
        boolean changed;
        do {
            changed = false;
            List<OptionalPlatform> candidates = collectRedundantPlatformCandidates(map, stats);
            for (OptionalPlatform early : earlyCandidates) {
                if (containsPlatform(map.getMainPlatforms(), early.bounds())
                        && candidates.stream().noneMatch(candidate ->
                            samePlatform(candidate.bounds(), early.bounds()))) {
                    candidates.add(early);
                }
            }
            for (Rectangle2D platform : map.getMainPlatforms()) {
                if (isPhysicalPlatform(map, platform)
                        && !isRequiredPlatform(map, platform)
                        && candidates.stream().noneMatch(candidate ->
                            samePlatform(candidate.bounds(), platform))) {
                    candidates.add(new OptionalPlatform(platform, platformTileType(map, platform)));
                }
            }
            candidates.sort((a, b) -> Double.compare(a.bounds().getMinY(), b.bounds().getMinY()));

            for (OptionalPlatform candidate : candidates) {
                if (isRequiredPlatform(map, candidate.bounds())) continue;
                List<OptionalPlatform> removed = new ArrayList<>();
                if (!removeOptionalPlatform(map, candidate, removed)) continue;
                cascadeRedundantOptionalPlatforms(map, stats, removed);

                PlatformValidationResult result = validatePlatformMap(map, stats);
                boolean stillPlayable = result.valid()
                    && hasEscapableFloorPockets(map, floorEscapeY, stats);
                Rectangle2D wallTopReplacement = null;
                if (!stillPlayable) {
                    wallTopReplacement = findWallTopReplacement(map, candidate.bounds());
                    if (wallTopReplacement != null) {
                        map.addMainPlatform(wallTopReplacement);
                        result = validatePlatformMap(map, stats);
                        stillPlayable = result.valid()
                            && hasEscapableFloorPockets(map, floorEscapeY, stats);
                    }
                }
                if (stillPlayable) {
                    for (OptionalPlatform removedPlatform : removed) {
                        removePlatformReference(route, removedPlatform.bounds());
                    }
                    changed = true;
                } else {
                    if (wallTopReplacement != null) {
                        map.removeMainPlatform(wallTopReplacement);
                    }
                    restoreOptionalPlatforms(map, removed);
                }
            }
        } while (changed);
    }

    private boolean removeOptionalPlatform(TileMap map, OptionalPlatform candidate,
                                           List<OptionalPlatform> removed) {
        if (!map.removePlatformRun(candidate.bounds())) return false;
        removed.add(candidate);
        return true;
    }

    private void cascadeRedundantOptionalPlatforms(TileMap map, PlayerStats stats,
                                                   List<OptionalPlatform> removed) {
        boolean changed;
        do {
            changed = false;
            List<OptionalPlatform> cascade = collectRedundantPlatformCandidates(map, stats);
            for (OptionalPlatform candidate : cascade) {
                if (isRequiredPlatform(map, candidate.bounds())) continue;
                if (removeOptionalPlatform(map, candidate, removed)) {
                    changed = true;
                }
            }
        } while (changed);
    }

    private List<OptionalPlatform> collectRedundantPlatformCandidates(TileMap map, PlayerStats stats) {
        List<Rectangle2D> platforms = new ArrayList<>(map.getMainPlatforms());
        Set<Integer> useful = collectUsefulPlatformIndexes(map, platforms, stats);
        List<OptionalPlatform> candidates = new ArrayList<>();
        for (int index = 0; index < platforms.size(); index++) {
            Rectangle2D platform = platforms.get(index);
            if (!isPhysicalPlatform(map, platform)
                    || useful.contains(index)
                    || isRequiredPlatform(map, platform)) continue;
            candidates.add(new OptionalPlatform(platform, platformTileType(map, platform)));
        }
        return candidates;
    }

    private Set<Integer> collectUsefulPlatformIndexes(TileMap map, List<Rectangle2D> platforms,
                                                      PlayerStats stats) {
        Set<Integer> useful = new HashSet<>();
        Rectangle2D exit = map.getExitBounds();
        int start = platformContainingX(platforms, map.getSpawnX());
        if (start < 0 || exit == null) return useful;

        Set<Integer> targets = new HashSet<>();
        int goal = platformContainingX(platforms, exit.getMinX());
        if (goal >= 0) targets.add(goal);
        for (Rectangle2D reward : map.getRewardZones()) {
            int rewardIndex = platformContainingX(platforms, reward.getMinX() + reward.getWidth() / 2.0);
            if (rewardIndex >= 0) targets.add(rewardIndex);
        }
        if (targets.isEmpty()) return useful;

        boolean[] fromStart = new boolean[platforms.size()];
        Queue<Integer> queue = new ArrayDeque<>();
        fromStart[start] = true;
        queue.add(start);
        while (!queue.isEmpty()) {
            int current = queue.remove();
            for (int next = 0; next < platforms.size(); next++) {
                if (fromStart[next] || next == current) continue;
                if (canJumpBetween(map, platforms.get(current), platforms.get(next), stats)) {
                    fromStart[next] = true;
                    queue.add(next);
                }
            }
        }

        boolean[] reachesTarget = new boolean[platforms.size()];
        queue.clear();
        for (int target : targets) {
            reachesTarget[target] = true;
            queue.add(target);
        }
        while (!queue.isEmpty()) {
            int current = queue.remove();
            for (int previous = 0; previous < platforms.size(); previous++) {
                if (reachesTarget[previous] || previous == current) continue;
                if (canJumpBetween(map, platforms.get(previous), platforms.get(current), stats)) {
                    reachesTarget[previous] = true;
                    queue.add(previous);
                }
            }
        }

        for (int i = 0; i < platforms.size(); i++) {
            if (fromStart[i] && reachesTarget[i]) {
                useful.add(i);
            }
        }
        return useful;
    }

    private boolean isRequiredPlatform(TileMap map, Rectangle2D platform) {
        Rectangle2D exit = map.getExitBounds();
        if (platformContainsX(platform, map.getSpawnX())) return true;
        if (exit != null && platformContainsX(platform, exit.getMinX())) return true;
        for (Rectangle2D reward : map.getRewardZones()) {
            if (platformContainsX(platform, reward.getMinX() + reward.getWidth() / 2.0)) {
                return true;
            }
        }
        return false;
    }

    private TileType platformTileType(TileMap map, Rectangle2D platform) {
        int tileX = (int) Math.round(platform.getMinX() / TileMap.TILE_SIZE);
        int tileY = (int) Math.round(platform.getMinY() / TileMap.TILE_SIZE);
        TileType type = map.getTile(tileX, tileY);
        return type == TileType.PLATFORM ? TileType.PLATFORM : TileType.ONE_WAY_PLATFORM;
    }

    private boolean isPhysicalPlatform(TileMap map, Rectangle2D platform) {
        int tileX = (int) Math.round(platform.getMinX() / TileMap.TILE_SIZE);
        int tileY = (int) Math.round(platform.getMinY() / TileMap.TILE_SIZE);
        TileType type = map.getTile(tileX, tileY);
        return type == TileType.PLATFORM || type == TileType.ONE_WAY_PLATFORM;
    }

    private boolean containsPlatform(List<Rectangle2D> platforms, Rectangle2D target) {
        for (Rectangle2D platform : platforms) {
            if (samePlatform(platform, target)) return true;
        }
        return false;
    }

    private void removePlatformReference(List<Rectangle2D> platforms, Rectangle2D target) {
        platforms.removeIf(platform -> samePlatform(platform, target));
    }

    private boolean samePlatform(Rectangle2D a, Rectangle2D b) {
        return a.getMinX() == b.getMinX()
            && a.getMinY() == b.getMinY()
            && a.getWidth() == b.getWidth();
    }

    private boolean platformContainsX(Rectangle2D platform, double x) {
        return x >= platform.getMinX() - JUMP_EDGE_MARGIN
            && x <= platform.getMaxX() + JUMP_EDGE_MARGIN;
    }

    private Rectangle2D findWallTopReplacement(TileMap map, Rectangle2D removedPlatform) {
        int platformY = (int) Math.round(removedPlatform.getMinY() / TileMap.TILE_SIZE);
        int left = Math.max(1, (int) Math.round(removedPlatform.getMinX() / TileMap.TILE_SIZE) - 1);
        int right = Math.min(map.getWidthTiles() - 2,
            (int) Math.round(removedPlatform.getMaxX() / TileMap.TILE_SIZE));

        Rectangle2D best = null;
        for (int y = platformY; y <= Math.min(map.getHeightTiles() - 2, platformY + 3); y++) {
            int x = left;
            while (x <= right) {
                while (x <= right && !isClearWallTopTile(map, x, y)) x++;
                int start = x;
                while (x <= right && isClearWallTopTile(map, x, y)) x++;
                int length = x - start;
                if (length >= 2) {
                    Rectangle2D wallTop = platformBounds(start, y, length);
                    if (best == null || wallTop.getWidth() > best.getWidth()) {
                        best = wallTop;
                    }
                }
            }
            if (best != null) return best;
        }
        return null;
    }

    private void restoreOptionalPlatform(TileMap map, OptionalPlatform candidate) {
        Rectangle2D bounds = candidate.bounds();
        int startX = (int) Math.round(bounds.getMinX() / TileMap.TILE_SIZE);
        int tileY = (int) Math.round(bounds.getMinY() / TileMap.TILE_SIZE);
        int length = (int) Math.round(bounds.getWidth() / TileMap.TILE_SIZE);
        map.addPlatformRun(startX, tileY, length, candidate.type());
    }

    private void restoreOptionalPlatforms(TileMap map, List<OptionalPlatform> removed) {
        for (int i = removed.size() - 1; i >= 0; i--) {
            restoreOptionalPlatform(map, removed.get(i));
        }
    }

    private void addTrapsAndDecorations(TileMap map, List<Rectangle2D> route, int levelIndex) {
        int trapCount = levelIndex == 1 ? 6 : 10;
        PlacementValidator validator = new PlacementValidator(map);
        Set<Integer> mainCenters = new HashSet<>();
        for (Rectangle2D platform : route) {
            mainCenters.add((int) (platform.getMinX() / TileMap.TILE_SIZE));
        }

        int placed = 0;
        for (int i = 0; i < trapCount * 5 && placed < trapCount; i++) {
            Rectangle2D platform = route.get(random.nextInt(route.size()));
            int start = (int) (platform.getMinX() / TileMap.TILE_SIZE) + 1;
            int end = (int) (platform.getMaxX() / TileMap.TILE_SIZE) - 2;
            if (end <= start) continue;
            int x = start + random.nextInt(end - start + 1);
            if (mainCenters.contains(x)) continue;
            int y = (int) (platform.getMinY() / TileMap.TILE_SIZE) - 1;
            if (validator.canPlaceSpikeAt(x, y)) {
                map.setTile(x, y, TileType.SPIKE);
                placed++;
            }
        }

        for (int x = 3; x < map.getWidthTiles() - 3; x += 3 + random.nextInt(4)) {
            int y = 2 + random.nextInt(map.getHeightTiles() - 5);
            if (map.getTile(x, y) == TileType.EMPTY && random.nextDouble() < 0.5) {
                if (validator.isInSpawnSafeZone(x, y) || validator.isInExitSafeZone(x, y)) continue;
                map.setTile(x, y, TileType.DECORATION);
            }
        }
    }

    private void addEnemyAndRewardZones(TileMap map, List<Rectangle2D> route, int levelIndex,
                                        List<Rectangle2D> unreachablePlatforms) {
        int enemies = levelIndex == 1 ? 2 : 5;
        PlacementValidator validator = new PlacementValidator(map);
        for (int i = 1; i < route.size() - 1 && enemies > 0; i += Math.max(2, route.size() / (levelIndex == 1 ? 3 : 6))) {
            Rectangle2D platform = route.get(i);
            if (unreachablePlatforms.contains(platform)) continue;
            if (platform.getWidth() < TileMap.TILE_SIZE * 4) continue;
            Rectangle2D enemyProbe = new Rectangle2D(platform.getMinX(), platform.getMinY() - 48,
                platform.getWidth(), 48);
            if (!validator.isValidRewardPlacement(enemyProbe)) continue;
            map.addEnemyZone(platform);
            enemies--;
        }

        for (int i = 1; i < route.size() - 1; i += 4) {
            Rectangle2D platform = route.get(i);
            if (unreachablePlatforms.contains(platform)) continue;
            Rectangle2D reward = new Rectangle2D(platform.getMinX() + platform.getWidth() / 2.0 - 12,
                platform.getMinY() - 28, 24, 24);
            if (validator.isValidRewardPlacement(reward)) {
                map.addRewardZone(reward);
            }
        }
    }

    private void carveGuaranteedBridge(TileMap map) {
        int y = 12;
        for (int x = 1; x < map.getWidthTiles() - 2; x += 6) {
            addRun(map, x, y, 4, TileType.ONE_WAY_PLATFORM);
        }
        // Keep the safety route intact while giving the fallback map masonry rooms
        // rather than a bare sequence of floating boards.
        for (int x = 10; x < map.getWidthTiles() - 10; x += 18) {
            int towerWidth = 2 + random.nextInt(2);
            int towerTop = y + 2 + random.nextInt(2);
            for (int tileX = x; tileX < x + towerWidth; tileX++) {
                for (int tileY = towerTop; tileY < map.getHeightTiles() - 1; tileY++) {
                    map.setTile(tileX, tileY, TileType.WALL);
                }
            }
        }
        // The fallback map still needs a guaranteed return from a floor fall.
        for (int x = 3; x < map.getWidthTiles() - 4; x += 12) {
            addRun(map, x, map.getHeightTiles() - 4, 4, TileType.ONE_WAY_PLATFORM);
            addRun(map, x, map.getHeightTiles() - 6, 4, TileType.ONE_WAY_PLATFORM);
        }
        map.setSpawn(TileMap.TILE_SIZE * 2, y * TileMap.TILE_SIZE - Config.PLAYER_HEIGHT);
        map.setExitBounds(new Rectangle2D((map.getWidthTiles() - 2.25) * TileMap.TILE_SIZE,
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
                if (canJumpBetween(map, from, platforms.get(next), stats)) {
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
        boolean enoughPlatformsUseful = reachable == platforms.size();
        boolean rewardsReachable = areRewardsReachable(map, platforms, visited);
        boolean valid = spawnToExitReachable && enoughPlatformsUseful && rewardsReachable;
        String reason = valid ? "ok"
            : !spawnToExitReachable ? "spawn cannot reach exit"
            : !enoughPlatformsUseful ? "contains unreachable optional platform"
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

    public MapValidationResult validateGeneratedMap(TileMap map,
                                                    PlatformValidationResult platformResult,
                                                    int retryCount) {
        List<String> errors = new ArrayList<>();
        PlacementValidator validator = new PlacementValidator(map);
        boolean hasSpawnHazard = false;
        boolean exitTooClose = false;

        if (map.getSpawnSafeZone() == null) {
            errors.add("Spawn safe zone missing");
        }
        if (map.getExitSafeZone() == null || map.getExitBounds() == null) {
            errors.add("Exit safe zone missing");
        }
        if (map.getExitBounds() != null && !validator.isValidExitPlacement(map.getExitBounds())) {
            errors.add("Invalid exit placement");
        }
        if (map.getExitBounds() != null) {
            double dx = Math.abs(map.getExitBounds().getMinX() - map.getSpawnX());
            exitTooClose = dx < TileMap.MIN_EXIT_DISTANCE_FROM_SPAWN_TILES * TileMap.TILE_SIZE;
            if (exitTooClose) {
                errors.add("Exit inside minimum spawn distance");
            }
        }

        int spawnTileX = (int) ((map.getSpawnX() + Config.PLAYER_WIDTH / 2.0) / TileMap.TILE_SIZE);
        int spawnFootTileY = (int) ((map.getSpawnY() + Config.PLAYER_HEIGHT + 1) / TileMap.TILE_SIZE);
        TileType foot = map.getTile(spawnTileX, spawnFootTileY);
        if (!foot.isStandable() && !foot.isSolid()) {
            errors.add("Spawn has no safe floor");
        }

        Rectangle2D standing = new Rectangle2D(map.getSpawnX(), map.getSpawnY(),
            Config.PLAYER_WIDTH, Config.PLAYER_HEIGHT);
        for (Rectangle2D solid : map.getSolidTilesNear(standing)) {
            if (solid.intersects(standing)) {
                errors.add("Spawn overlaps wall");
                break;
            }
        }

        for (int y = 0; y < map.getHeightTiles(); y++) {
            for (int x = 0; x < map.getWidthTiles(); x++) {
                TileType type = map.getTile(x, y);
                if (type == TileType.SPIKE && validator.isInSpawnSafeZone(x, y)) {
                    hasSpawnHazard = true;
                    errors.add("Spike placed near spawn");
                }
                if (type == TileType.SPIKE && validator.isInExitSafeZone(x, y)) {
                    errors.add("Spike placed near exit");
                }
                if (type == TileType.WALL && validator.isInExitSafeZone(x, y)) {
                    Rectangle2D wall = map.tileBounds(x, y);
                    if (map.getExitBounds() != null && wall.intersects(map.getExitBounds())) {
                        errors.add("Wall blocks exit");
                    }
                }
            }
        }

        for (Rectangle2D zone : map.getEnemyZones()) {
            Rectangle2D enemyProbe = new Rectangle2D(zone.getMinX(), zone.getMinY() - 48,
                zone.getWidth(), 48);
            if (map.getSpawnSafeZone() != null && map.getSpawnSafeZone().intersects(enemyProbe)) {
                hasSpawnHazard = true;
                errors.add("Enemy placed near spawn");
            }
        }

        if (!platformResult.spawnToExitReachable()) {
            errors.add("Spawn cannot reach exit");
        }
        if (!platformResult.valid()) {
            errors.add(platformResult.reason());
        }

        // Validate escape per actual open floor pocket. The old fixed-interval rule
        // incorrectly rejected x positions occupied by intentional grounded maze walls.
        int floorEscapeY = map.getHeightTiles() - 3; // two tiles above the floor is a safe first jump
        if (!hasEscapableFloorPockets(map, floorEscapeY, PlayerStats.fromConfig())) {
            errors.add("Trapped floor pocket has no recovery platform");
        }

        if (errors.isEmpty()) {
            return MapValidationResult.valid(platformResult.unreachablePlatformCount(),
                platformResult.spawnToExitReachable(), retryCount);
        }
        return MapValidationResult.invalid(errors, platformResult.unreachablePlatformCount(),
            platformResult.spawnToExitReachable(), hasSpawnHazard, exitTooClose, retryCount);
    }

    private boolean hasEscapableFloorPockets(TileMap map, int minimumTileY, PlayerStats stats) {
        int walkY = map.getHeightTiles() - 2;
        List<Rectangle2D> platforms = map.getMainPlatforms();
        List<Rectangle2D> safeRoute = collectPlatformsLeadingToGoal(map, platforms, stats);
        int x = 1;
        for (; x < map.getWidthTiles() - 1; x++) {
            if (map.getTile(x, walkY).isSolid()) continue;
            int start = x;
            while (x < map.getWidthTiles() - 1 && !map.getTile(x, walkY).isSolid()) x++;
            int end = x - 1;
            if (end - start + 1 < 2) return false;
            if (!hasRecoveryChainToGoal(map, platforms, start, end, minimumTileY,
                    safeRoute, stats)) {
                return false;
            }
        }
        return true;
    }

    private boolean hasRecoveryChainToGoal(TileMap map, List<Rectangle2D> platforms,
                                           int start, int end, int minimumTileY,
                                           List<Rectangle2D> safeRoute, PlayerStats stats) {
        List<Rectangle2D> wallTops = collectNearbyWallTopsLeadingToGoal(map, start, end,
            safeRoute, stats);
        Queue<Integer> queue = new ArrayDeque<>();
        boolean[] visited = new boolean[platforms.size()];
        for (int index = 0; index < platforms.size(); index++) {
            Rectangle2D platform = platforms.get(index);
            int tileY = (int) (platform.getMinY() / TileMap.TILE_SIZE);
            int left = (int) (platform.getMinX() / TileMap.TILE_SIZE);
            int right = (int) (platform.getMaxX() / TileMap.TILE_SIZE) - 1;
            if (tileY < minimumTileY || right < start || left > end) continue;
            Rectangle2D floorSurface = floorLaunchSurfaceForPlatform(map, start, end, platform);
            if (canRecoveryJumpBetween(map, floorSurface, platform, stats)) {
                visited[index] = true;
                queue.add(index);
            }
        }
        while (!queue.isEmpty()) {
            int current = queue.remove();
            Rectangle2D platform = platforms.get(current);
            if (canReturnToGoalRoute(map, platform, wallTops, safeRoute, stats)) {
                return true;
            }
            for (int next = 0; next < platforms.size(); next++) {
                if (!visited[next] && canRecoveryJumpBetween(map, platform, platforms.get(next), stats)) {
                    visited[next] = true;
                    queue.add(next);
                }
            }
        }
        return false;
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

    private boolean canJumpBetween(TileMap map, Rectangle2D from, Rectangle2D to, PlayerStats stats) {
        return canJumpBetween(map, from, to, stats, SAFE_JUMP_HEIGHT_FACTOR);
    }

    private boolean canRecoveryJumpBetween(TileMap map, Rectangle2D from, Rectangle2D to,
                                           PlayerStats stats) {
        return canJumpBetween(map, from, to, stats, RECOVERY_JUMP_HEIGHT_FACTOR);
    }

    private boolean canJumpBetween(TileMap map, Rectangle2D from, Rectangle2D to, PlayerStats stats,
                                   double heightFactor) {
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
        if (verticalUp > stats.maxVerticalJumpHeight() * heightFactor
                || verticalDrop > stats.maxVerticalJumpHeight() * SAFE_DROP_HEIGHT_FACTOR) {
            return false;
        }

        double jumpVelocity = Math.abs(Config.JUMP_FORCE);
        double discriminant = jumpVelocity * jumpVelocity + 2.0 * stats.gravity() * verticalDrop;
        if (discriminant < 0) return false;
        double flightTime = (jumpVelocity + Math.sqrt(discriminant)) / stats.gravity();
        double horizontalLimit = stats.moveSpeed() * flightTime * SAFE_JUMP_SPEED_FACTOR;
        if (stats.doubleJump()) horizontalLimit *= 1.18;
        double requiredTravel = gap == 0
            ? 0
            : gap + stats.playerWidth() + JUMP_EDGE_MARGIN * 2.0;
        if (requiredTravel > horizontalLimit) return false;

        return hasClearJumpArc(map, from, to, stats, flightTime);
    }

    /**
     * Samples the same parabolic jump shape used by player physics. A route is not
     * accepted merely because two ledges are near each other; the player's body
     * must clear every solid maze tile between take-off and landing.
     */
    private boolean hasClearJumpArc(TileMap map, Rectangle2D from, Rectangle2D to,
                                    PlayerStats stats, double flightTime) {
        boolean movingRight = to.getMinX() >= from.getMinX();
        double startX = movingRight
            ? from.getMaxX() - stats.playerWidth() - JUMP_EDGE_MARGIN
            : from.getMinX() + JUMP_EDGE_MARGIN;
        double endX = movingRight
            ? to.getMinX() + JUMP_EDGE_MARGIN
            : to.getMaxX() - stats.playerWidth() - JUMP_EDGE_MARGIN;
        double startFeetY = from.getMinY();
        int samples = Math.max(12, (int) Math.ceil(flightTime * 24.0));

        if (!hasStandingClearance(map, startX, startFeetY, stats)
                || !hasStandingClearance(map, endX, to.getMinY(), stats)) {
            return false;
        }

        for (int sample = 1; sample < samples; sample++) {
            double ratio = (double) sample / samples;
            double time = flightTime * ratio;
            double x = startX + (endX - startX) * ratio;
            double feetY = startFeetY + Config.JUMP_FORCE * time
                + 0.5 * stats.gravity() * time * time;
            Rectangle2D playerBody = new Rectangle2D(x, feetY - stats.playerHeight(),
                stats.playerWidth(), stats.playerHeight());
            for (Rectangle2D solid : map.getSolidTilesNear(playerBody)) {
                if (solid.intersects(playerBody)) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean hasStandingClearance(TileMap map, double x, double feetY, PlayerStats stats) {
        Rectangle2D playerBody = new Rectangle2D(x, feetY - stats.playerHeight() - 0.5,
            stats.playerWidth(), stats.playerHeight());
        for (Rectangle2D solid : map.getSolidTilesNear(playerBody)) {
            if (solid.intersects(playerBody)) {
                return false;
            }
        }
        return true;
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
