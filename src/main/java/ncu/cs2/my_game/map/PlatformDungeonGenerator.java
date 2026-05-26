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
            MapValidationResult mapResult = validateGeneratedMap(map, result, attempt);
            map.setValidationResult(result);
            map.setMapValidationResult(mapResult);
            if (mapResult.isValid()) {
                printGenerationReport(levelIndex, result);
                return map;
            }
            System.out.println("Map generation failed: " + mapResult.reason());
        }

        TileMap fallback = buildCandidate(widthTiles, levelIndex, config);
        carveGuaranteedBridge(fallback);
        PlatformValidationResult result = validatePlatformMap(fallback, stats);
        MapValidationResult mapResult = validateGeneratedMap(fallback, result, MAX_ATTEMPTS);
        fallback.setValidationResult(result);
        fallback.setMapValidationResult(mapResult);
        printGenerationReport(levelIndex, result);
        return fallback;
    }

    private TileMap buildCandidate(int widthTiles, int levelIndex, PlatformGenerationConfig config) {
        TileMap map = new TileMap(widthTiles, HEIGHT_TILES);
        buildShell(map);

        List<Rectangle2D> route = new ArrayList<>();
        int x = 1;
        int y = 12; // = Zone 1 center: equal chance of going up or down initially
        int length = 8;
        route.add(addRun(map, x, y, length, TileType.FLOOR));

        int platformsPlaced = 0;
        while (x < widthTiles - 13) {
            int gap = config.minGapX() + random.nextInt(config.maxGapX() - config.minGapX() + 1);
            int nextLength = config.minPlatformWidth()
                + random.nextInt(config.maxPlatformWidth() - config.minPlatformWidth() + 1);

            double progress = (double) x / Math.max(1, widthTiles - 13);
            int[] zone = getZoneRange(progress);
            int zoneMin = zone[0], zoneMax = zone[1], zoneCenter = zone[2];

            int diff = zoneCenter - y;
            int dy;
            if (platformsPlaced < 3) {
                // Force the first three platforms UPWARD so the player immediately
                // has a clear path toward the upper half of the map.
                dy = -(1 + random.nextInt(2));
            } else if (Math.abs(diff) >= 4) {
                // Far from zone center: pull toward it with 1–2 tile steps
                dy = Integer.signum(diff) * (1 + random.nextInt(2));
            } else {
                // Near zone center: free random walk within zone
                dy = config.minHeightDelta()
                    + random.nextInt(config.maxHeightDelta() - config.minHeightDelta() + 1);
                if (random.nextDouble() < 0.30) dy += random.nextBoolean() ? 1 : -1;
            }

            int nextY = y + dy;
            // Up: max 3 tiles (player can jump back). Down: max 2 tiles (player can jump back up).
            if (nextY < y - 3) nextY = y - 3;
            if (nextY > y + 2) nextY = y + 2;
            nextY = clamp(nextY, zoneMin, zoneMax);

            x += length + gap;
            if (x + nextLength >= widthTiles - 4) {
                nextLength = widthTiles - x - 4;
            }
            if (nextLength < 4) break;

            TileType type = random.nextDouble() < config.oneWayPlatformChance()
                ? TileType.ONE_WAY_PLATFORM : TileType.PLATFORM;
            Rectangle2D platform = addRun(map, x, nextY, nextLength, type);
            route.add(platform);
            y = nextY;
            length = nextLength;
            platformsPlaced++;
        }

        Rectangle2D spawnPlatform = route.get(0);
        clearMainRouteAirspace(map, route);
        map.setSpawn(spawnPlatform.getMinX() + TileMap.TILE_SIZE,
            spawnPlatform.getMinY() - Config.PLAYER_HEIGHT);
        map.setTile(2, 11, TileType.SPAWN);
        clearSpawnSafeZone(map);

        // Snapshot the main route BEFORE adding upper-route stones.
        // Exit must land on a main-route platform to guarantee BFS reachability.
        List<Rectangle2D> mainRoute = new ArrayList<>(route);

        addUpperRoute(map, route, levelIndex);

        // Choose exit from main route only (upper-route platforms may be isolated islands).
        Rectangle2D exitPlatform = chooseExitPlatform(map, mainRoute);
        double exitX = Math.max(exitPlatform.getMinX() + TileMap.TILE_SIZE,
            exitPlatform.getMaxX() - TileMap.TILE_SIZE * 1.5);
        double exitY = exitPlatform.getMinY() - TileMap.TILE_SIZE * 2.2;
        map.setExitBounds(new Rectangle2D(exitX, exitY, TileMap.TILE_SIZE * 1.2, TileMap.TILE_SIZE * 2.2));
        map.setTile((int) (exitX / TileMap.TILE_SIZE), (int) (exitY / TileMap.TILE_SIZE), TileType.EXIT);

        addLShapedWalls(map, route, levelIndex);
        addGuidingWalls(map, route);
        clearMainRouteAirspace(map, route);
        addConnectorPlatforms(map, route, levelIndex);
        addBranches(map, route, levelIndex, config);
        addFloorEscapePlatforms(map, route); // ensure player can always climb off the floor
        // Final clearance: escape/relay platforms added above haven't had their headroom
        // cleared yet. Run once more so newly-added platforms also get clear airspace.
        clearMainRouteAirspace(map, route);
        // Unconditionally clear the two tile rows just above the floor (y=16,17).
        // Any wall reaching this height creates a low-ceiling trap the player can't
        // stand in (PLAYER_HEIGHT=42px → head at tile y≈16.7 when on floor).
        clearFloorCorridor(map);
        addEnemyAndRewardZones(map, route, levelIndex);
        addTrapsAndDecorations(map, route, levelIndex);
        return map;
    }

    /**
     * Adds stepping-stone platforms in the upper half of the map (y=4–8) starting from
     * the highest main-route platform in the right portion.  Each stone is 2–3 tiles
     * higher than the previous, keeping vertical gaps within the player's jump limit.
     * These platforms give the exit a high position and create an explorable upper layer.
     */
    private void addUpperRoute(TileMap map, List<Rectangle2D> route, int levelIndex) {
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
        int stoneCount = 3 + random.nextInt(2);  // 3–4 upper platforms

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
     * Selects the exit platform: prefers the highest platform with a valid exit placement
     * in the right portion of the map.  Falls back to the original rightmost-valid logic.
     */
    private Rectangle2D chooseExitPlatform(TileMap map, List<Rectangle2D> route) {
        PlacementValidator validator = new PlacementValidator(map);

        List<Rectangle2D> valid = new ArrayList<>();
        for (Rectangle2D platform : route) {
            double exitX = Math.max(platform.getMinX() + TileMap.TILE_SIZE,
                platform.getMaxX() - TileMap.TILE_SIZE * 1.5);
            double exitY = platform.getMinY() - TileMap.TILE_SIZE * 2.2;
            if (validator.isValidExitPlacement(
                    new Rectangle2D(exitX, exitY, TileMap.TILE_SIZE * 1.2, TileMap.TILE_SIZE * 2.2))) {
                valid.add(platform);
            }
        }
        if (!valid.isEmpty()) {
            // Sort by ascending minY (highest on screen first), break ties by rightmost.
            valid.sort((a, b) -> {
                int yComp = Double.compare(a.getMinY(), b.getMinY());
                return yComp != 0 ? yComp : Double.compare(b.getMinX(), a.getMinX());
            });
            // Pick randomly from the top 40% so the exit isn't always the single
            // absolute-highest platform (which can be hard to reach).
            int topCount = Math.max(1, valid.size() * 2 / 5);
            return valid.get(random.nextInt(topCount));
        }

        // Fallback: rightmost valid (original behaviour)
        for (int i = route.size() - 1; i >= 0; i--) {
            Rectangle2D platform = route.get(i);
            double exitX = Math.max(platform.getMinX() + TileMap.TILE_SIZE,
                platform.getMaxX() - TileMap.TILE_SIZE * 1.5);
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
     * Places 4–6 floor-rising guiding walls spread evenly across the first 70% of the map.
     * Wall height is calibrated to the zone at each position: the wall top sits 1–2 tiles
     * BELOW the zone's platform center, so the player can always jump over the wall from an
     * adjacent route platform.  This creates traversable barriers — not impassable blockades.
     */
    private void addGuidingWalls(TileMap map, List<Rectangle2D> route) {
        int coverEnd = (int) (map.getWidthTiles() * 0.70);
        PlacementValidator validator = new PlacementValidator(map);
        int wallCount = 4 + random.nextInt(3); // 4–6 walls
        int spacing = Math.max(7, coverEnd / (wallCount + 1));

        int spawnClearEnd = (int) (map.getWidthTiles() * 0.20); // don't place near spawn

        for (int w = 0; w < wallCount; w++) {
            int wallX = spacing * (w + 1) + random.nextInt(5) - 2;
            wallX = clamp(wallX, Math.max(6, spawnClearEnd), coverEnd - 3);

            // Walls stop 3 tiles above the floor so floor level stays walkable —
            // the player can always duck under and reach the next escape platform.
            int wallFloorLimit = map.getHeightTiles() - 4;
            if (validator.isInSpawnSafeZone(wallX, wallFloorLimit)) continue;
            if (validator.isInExitSafeZone(wallX, wallFloorLimit)) continue;

            // Wall top = zone center + 1–2 tiles downward (higher y = lower on screen).
            // Route platforms in this zone sit at or above the wall top, so a normal
            // platform-to-platform jump easily clears the wall.
            double progress = (double) wallX / Math.max(1, map.getWidthTiles() - 4);
            int[] zone = getZoneRange(progress);
            int zoneCenter = zone[2];
            int wallTopY = clamp(zoneCenter + 1 + random.nextInt(2), 7, wallFloorLimit - 3);

            for (int tileY = wallTopY; tileY <= wallFloorLimit; tileY++) {
                if (validator.isInSpawnSafeZone(wallX, tileY)) break;
                if (!isProtectedRouteTile(route, wallX, tileY)) {
                    TileType existing = map.getTile(wallX, tileY);
                    if (existing == TileType.EMPTY || existing == TileType.WALL) {
                        map.setTile(wallX, tileY, TileType.WALL);
                    }
                }
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
            boolean clear = true;
            for (int xx = checkX; xx < checkX + 3; xx++) {
                if (map.getTile(xx, escapeY) != TileType.EMPTY) { clear = false; break; }
            }
            if (clear) {
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
            boolean clear = true;
            for (int xx = checkX; xx < checkX + 3; xx++) {
                if (map.getTile(xx, relayY) != TileType.EMPTY) { clear = false; break; }
            }
            if (clear) {
                route.add(addRun(map, checkX, relayY, 3, TileType.ONE_WAY_PLATFORM));
            }
        }
    }

    /**
     * Clears tile rows y=16 and y=17 (the two tiles directly above the floor).
     * A wall at tile y=16 (pixels 512–543) overlaps the player's head (pixel ≈534)
     * when they stand on the floor (y=18), making those areas impassable.
     * This runs unconditionally after all wall generation as a safety net.
     */
    private void clearFloorCorridor(TileMap map) {
        int y16 = map.getHeightTiles() - 3; // tile y=16
        int y17 = map.getHeightTiles() - 2; // tile y=17
        for (int x = 1; x < map.getWidthTiles() - 1; x++) {
            TileType t = map.getTile(x, y16);
            if (t == TileType.WALL || t == TileType.DECORATION || t == TileType.SPIKE) {
                map.setTile(x, y16, TileType.EMPTY);
            }
            t = map.getTile(x, y17);
            if (t == TileType.WALL || t == TileType.DECORATION || t == TileType.SPIKE) {
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

        // Sparse background columns. Wide spacing (10–16 tiles) so jump corridors
        // between route platforms are rarely intersected; short height so fewer
        // tiles end up in route headroom. clearMainRouteAirspace clears anything
        // that does fall in a corridor, but sparser columns reduce reliance on it.
        for (int x = 6; x < map.getWidthTiles() - 4; x += 10 + random.nextInt(7)) {
            int top = 2 + random.nextInt(5);
            int bottom = Math.min(map.getHeightTiles() - 5, top + 3 + random.nextInt(5));
            for (int y = top; y <= bottom; y++) {
                if (random.nextDouble() < 0.60) {
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

    private void addLShapedWalls(TileMap map, List<Rectangle2D> route, int levelIndex) {
        int count = 8 + Math.min(8, levelIndex * 2);
        PlacementValidator validator = new PlacementValidator(map);
        for (int i = 0; i < count * 5 && count > 0; i++) {
            int arm = 3 + random.nextInt(5 + Math.min(3, levelIndex / 2));
            int height = 3 + random.nextInt(5 + Math.min(4, levelIndex / 2));
            boolean fromFloor = random.nextDouble() < 0.40;
            boolean mirror = random.nextBoolean();
            boolean inverted = !fromFloor && random.nextBoolean();
            int x = 4 + random.nextInt(Math.max(1, map.getWidthTiles() - arm - 8));
            // Floor-anchored walls stop at y=getHeightTiles()-6 (=13) so the arm
            // never lands at y=15 (escape platform level) and never creates low
            // ceilings near the floor (PLAYER_HEIGHT=42 → head at tile y≈16.7).
            int anchorY = fromFloor
                ? map.getHeightTiles() - 6
                : 1 + random.nextInt(Math.max(1, map.getHeightTiles() / 3));

            List<int[]> tiles = lWallTiles(x, anchorY, arm, height, fromFloor, mirror, inverted);
            if (canPlaceWallShape(map, route, validator, tiles)) {
                for (int[] tile : tiles) {
                    map.setTile(tile[0], tile[1], TileType.WALL);
                }
                count--;
            }
        }
    }

    private void addConnectorPlatforms(TileMap map, List<Rectangle2D> route, int levelIndex) {
        int extra = Math.min(6, 2 + levelIndex);
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
            if (canPlaceConnector(map, startX, midY, length)) {
                addRun(map, startX, midY, length, TileType.ONE_WAY_PLATFORM);
                extra--;
            }
        }
    }

    private boolean canPlaceConnector(TileMap map, int startX, int tileY, int length) {
        for (int x = startX; x < startX + length; x++) {
            if (map.getTile(x, tileY) != TileType.EMPTY) return false;
            for (int y = Math.max(1, tileY - 3); y <= tileY; y++) {
                if (map.getTile(x, y) == TileType.WALL) return false;
            }
        }
        return true;
    }

    private List<int[]> lWallTiles(int x, int anchorY, int arm, int height,
                                   boolean fromFloor, boolean mirror, boolean inverted) {
        List<int[]> tiles = new ArrayList<>();
        int stemX = mirror ? x + arm - 1 : x;
        int verticalStart = fromFloor ? anchorY - height + 1 : anchorY;
        int verticalEnd = fromFloor ? anchorY : anchorY + height - 1;
        for (int y = verticalStart; y <= verticalEnd; y++) {
            tiles.add(new int[] { stemX, y });
        }

        int armY;
        if (fromFloor) {
            armY = inverted ? verticalStart : anchorY;
        } else {
            armY = inverted ? verticalEnd : anchorY;
        }
        for (int dx = 0; dx < arm; dx++) {
            tiles.add(new int[] { x + dx, armY });
        }
        return tiles;
    }

    private boolean canPlaceWallShape(TileMap map, List<Rectangle2D> route,
                                      PlacementValidator validator, List<int[]> tiles) {
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
            if (type != TileType.EMPTY && type != TileType.WALL) {
                return false;
            }
            if (isProtectedRouteTile(route, x, y)) {
                return false;
            }
        }
        return true;
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
                             PlatformGenerationConfig config) {
        if (route.size() < 3) return;
        int branchCount = levelIndex == 1 ? 8 : 12;
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
            Rectangle2D reward = new Rectangle2D(branch.getMinX() + branch.getWidth() / 2.0 - 12,
                branch.getMinY() - 28, 24, 24);
            if (new PlacementValidator(map).isValidRewardPlacement(reward)) {
                map.addRewardZone(reward);
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

    private void addEnemyAndRewardZones(TileMap map, List<Rectangle2D> route, int levelIndex) {
        int enemies = levelIndex == 1 ? 2 : 5;
        PlacementValidator validator = new PlacementValidator(map);
        for (int i = 1; i < route.size() - 1 && enemies > 0; i += Math.max(2, route.size() / (levelIndex == 1 ? 3 : 6))) {
            Rectangle2D platform = route.get(i);
            if (platform.getWidth() < TileMap.TILE_SIZE * 4) continue;
            Rectangle2D enemyProbe = new Rectangle2D(platform.getMinX(), platform.getMinY() - 48,
                platform.getWidth(), 48);
            if (!validator.isValidRewardPlacement(enemyProbe)) continue;
            map.addEnemyZone(platform);
            enemies--;
        }

        for (int i = 1; i < route.size() - 1; i += 4) {
            Rectangle2D platform = route.get(i);
            Rectangle2D reward = new Rectangle2D(platform.getMinX() + platform.getWidth() / 2.0 - 12,
                platform.getMinY() - 28, 24, 24);
            if (validator.isValidRewardPlacement(reward)) {
                map.addRewardZone(reward);
            }
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

        // Validate floor escape: every 5 tiles must have a platform at y≥15
        // (3 tiles above floor = 96px, within the 127.5px max jump from the ground).
        // y=13 or y=14 platforms are 4–5 tiles above floor and unreachable in one jump.
        int floorEscapeY = map.getHeightTiles() - 4; // y=15 for 19-tile map
        List<Rectangle2D> allPlatforms = map.getMainPlatforms();
        for (int checkX = 3; checkX < map.getWidthTiles() - 4; checkX += 5) {
            boolean hasEscape = false;
            for (Rectangle2D p : allPlatforms) {
                int py    = (int) (p.getMinY() / TileMap.TILE_SIZE);
                int px    = (int) (p.getMinX() / TileMap.TILE_SIZE);
                int pxEnd = (int) (p.getMaxX() / TileMap.TILE_SIZE);
                if (px - 5 <= checkX && checkX <= pxEnd + 5 && py >= floorEscapeY) {
                    hasEscape = true;
                    break;
                }
            }
            if (!hasEscape) {
                errors.add("No floor escape near x=" + checkX);
                break;
            }
        }

        if (errors.isEmpty()) {
            return MapValidationResult.valid(platformResult.unreachablePlatformCount(),
                platformResult.spawnToExitReachable(), retryCount);
        }
        return MapValidationResult.invalid(errors, platformResult.unreachablePlatformCount(),
            platformResult.spawnToExitReachable(), hasSpawnHazard, exitTooClose, retryCount);
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
