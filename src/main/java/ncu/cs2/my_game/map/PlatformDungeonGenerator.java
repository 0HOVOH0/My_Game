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
 * Version C Dungeon Generator — "Maze Blocks" style.
 *
 * <p>Architecture: large solid WALL blocks fill most of the vertical space.
 * Narrow corridors (5–8 tiles wide) between blocks contain ONE_WAY_PLATFORM
 * stepping stones spaced 3 tiles apart, allowing the player to climb from
 * floor level up to each block top. Block tops are flat walking surfaces.
 * Heights generally rise left-to-right (toward exit), with occasional dips
 * for maze feel. The floor row is always walkable between blocks.
 *
 * <p>Physics constraints used throughout:
 * <ul>
 *   <li>Max jump height = 127.5 px ≈ 3.99 tiles (JUMP_FORCE²/2GRAVITY)</li>
 *   <li>BFS vertical-up limit = 127.5 × 0.85 = 108 px ≈ 3.38 tiles</li>
 *   <li>Safe corridor step = 3 tiles (96 px) — always within limit</li>
 *   <li>Max horizontal jump = 167 px ≈ 5.2 tiles</li>
 *   <li>Player height = 42 px — walls at tile y=16 trap player on floor</li>
 * </ul>
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

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

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

        // Fallback: guaranteed passable bridge
        TileMap fallback = buildCandidate(widthTiles, levelIndex, config);
        carveGuaranteedBridge(fallback);
        PlatformValidationResult result = validatePlatformMap(fallback, stats);
        MapValidationResult mapResult = validateGeneratedMap(fallback, result, MAX_ATTEMPTS);
        fallback.setValidationResult(result);
        fallback.setMapValidationResult(mapResult);
        printGenerationReport(levelIndex, result);
        return fallback;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Core builder
    // ─────────────────────────────────────────────────────────────────────────

    private TileMap buildCandidate(int widthTiles, int levelIndex, PlatformGenerationConfig config) {
        TileMap map = new TileMap(widthTiles, HEIGHT_TILES);

        // ── 1. Shell: ceiling row, floor row, left/right walls ────────────────
        for (int x = 0; x < widthTiles; x++) {
            map.setTile(x, 0, TileType.WALL);
            map.setTile(x, HEIGHT_TILES - 1, TileType.FLOOR);
        }
        for (int y = 0; y < HEIGHT_TILES; y++) {
            map.setTile(0, y, TileType.WALL);
            map.setTile(widthTiles - 1, y, TileType.WALL);
        }

        // ── 2. Generate dungeon block layout ──────────────────────────────────
        // Each block = [x1, topY, x2, bottomY].
        // topY = first WALL row; player walks ON TOP of this surface.
        // bottomY = HEIGHT_TILES-2 (y=17); block sits directly above floor tile y=18.
        List<int[]> blocks = generateDungeonBlocks(widthTiles, levelIndex);

        // ── 3. Fill blocks with WALL tiles ────────────────────────────────────
        for (int[] b : blocks) {
            for (int y = b[1]; y <= b[3]; y++) {
                for (int x = b[0]; x <= b[2]; x++) {
                    if (x > 0 && x < widthTiles - 1 && y > 0 && y < HEIGHT_TILES - 1) {
                        map.setTile(x, y, TileType.WALL);
                    }
                }
            }
        }

        // ── 4. Register block tops as BFS platforms ───────────────────────────
        // Player stands on the topmost WALL row of each block.
        // Use addMainPlatform (no tile change needed — WALL already solid).
        List<Rectangle2D> route = new ArrayList<>();
        for (int[] b : blocks) {
            Rectangle2D top = new Rectangle2D(
                b[0] * (double) TileMap.TILE_SIZE,
                b[1] * (double) TileMap.TILE_SIZE,
                (b[2] - b[0] + 1) * (double) TileMap.TILE_SIZE,
                TileMap.TILE_SIZE
            );
            route.add(top);
            map.addMainPlatform(top);
        }

        // ── 5. Corridor stepping platforms ────────────────────────────────────
        addCorridorClimbers(map, blocks, route, widthTiles);

        // ── 6. Spawn at floor level, left side of map ─────────────────────────
        // Spawning at floor level avoids clearSpawnSafeZone erasing block WALL tiles.
        // Player must climb corridor platforms to reach block tops.
        int[] firstBlock = blocks.get(0);
        double spawnPixX = 2.0 * TileMap.TILE_SIZE;          // x=2 tiles from left wall
        // Player stands on floor tile (y=HEIGHT_TILES-1): feet at pixel (HEIGHT_TILES-1)*32
        double spawnPixY = (HEIGHT_TILES - 1) * (double) TileMap.TILE_SIZE - Config.PLAYER_HEIGHT;
        map.setSpawn(spawnPixX, spawnPixY);
        // Spawn marker tile (visual only, no physics effect)
        int spawnMarkerX = 2;
        int spawnMarkerY = HEIGHT_TILES - 2;                  // row just above floor
        map.setTile(spawnMarkerX, spawnMarkerY, TileType.SPAWN);
        clearSpawnSafeZone(map);

        // Register a floor-level platform so BFS can locate the spawn position.
        // The floor tile itself is solid but not in mainPlatforms; this virtual
        // rectangle bridges spawn to the first corridor climbing platform.
        Rectangle2D spawnFloor = new Rectangle2D(
            TileMap.TILE_SIZE,                                // x=1 tile (next to left wall)
            (HEIGHT_TILES - 1) * (double) TileMap.TILE_SIZE, // y = floor row pixel
            (firstBlock[0] + 3) * (double) TileMap.TILE_SIZE,// extends into first corridor
            TileMap.TILE_SIZE
        );
        map.addMainPlatform(spawnFloor);
        route.add(0, spawnFloor);

        // ── 7. Exit above last block ──────────────────────────────────────────
        int[] lastBlock = blocks.get(blocks.size() - 1);
        double exitX = (lastBlock[2] - 2) * (double) TileMap.TILE_SIZE;
        // 70 px above the block's top surface
        double exitY = Math.max(TileMap.TILE_SIZE, lastBlock[1] * (double) TileMap.TILE_SIZE - 70.0);
        Rectangle2D exitRect = new Rectangle2D(exitX, exitY, TileMap.TILE_SIZE * 1.2, 70.0);
        map.setExitBounds(exitRect);
        int exitTileX = (int) (exitX / TileMap.TILE_SIZE);
        int exitTileY = (int) (exitY / TileMap.TILE_SIZE);
        if (exitTileX > 0 && exitTileX < widthTiles - 1) {
            map.setTile(exitTileX, exitTileY, TileType.EXIT);
        }

        // ── 8. Clear headroom & floor corridor ───────────────────────────────
        clearBlockAirspace(map, route, blocks);
        clearFloorCorridor(map);

        // ── 9. Floor escape platforms (player can always climb back up) ───────
        addFloorEscapePlatforms(map, route, blocks);
        clearBlockAirspace(map, route, blocks);
        clearFloorCorridor(map);

        // ── 10. Enemies, rewards, decorations ────────────────────────────────
        addEnemyAndRewardZones(map, route, levelIndex);
        addTrapsAndDecorations(map, route, levelIndex);

        return map;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Block generation
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Generates dungeon block descriptors: [x1, topY, x2, bottomY].
     *
     * <p>Blocks are solid WALL rectangles from topY down to floor-1 (y=17).
     * Heights generally decrease (ascend screen) left-to-right, with 25%
     * chance of a dip to create maze feel. The last block is forced high
     * (small topY) so the exit sits near the top of the map.
     */
    private List<int[]> generateDungeonBlocks(int widthTiles, int levelIndex) {
        List<int[]> blocks = new ArrayList<>();
        int maxBlocks = levelIndex == 1 ? 5 + random.nextInt(2) : 6 + random.nextInt(2);
        int x = 4 + random.nextInt(3);        // first block at x=4..6
        int topY = 10 + random.nextInt(3);    // first block top at y=10..12

        for (int i = 0; i < maxBlocks; i++) {
            int w = 8 + random.nextInt(4);     // block width 8–11 tiles
            int x2 = x + w - 1;
            if (x2 >= widthTiles - 5) break;  // no room for this block

            // Block fills from topY down to y=17 (one above floor tile y=18)
            blocks.add(new int[]{ x, topY, x2, HEIGHT_TILES - 2 });

            int gap = 5 + random.nextInt(4);   // corridor width 5–8 tiles
            x = x2 + 1 + gap;

            // Advance height for next block
            int dy;
            if (i == maxBlocks - 2) {
                // Second-to-last: definitely climb hard
                dy = -(3 + random.nextInt(2));
            } else if (random.nextDouble() < 0.25) {
                // 25%: dip down 2-3 tiles (maze feel)
                dy = 2 + random.nextInt(2);
            } else {
                // 75%: climb 2-3 tiles
                dy = -(2 + random.nextInt(2));
            }
            topY = clamp(topY + dy, 2, 13);
        }

        // Ensure last block is near the top (exit must be high)
        if (!blocks.isEmpty()) {
            int[] last = blocks.get(blocks.size() - 1);
            if (last[1] > 6) {
                last[1] = 2 + random.nextInt(5); // force topY = 2..6
            }
        }

        return blocks;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Corridor platforms
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Places ONE_WAY_PLATFORM stepping stones in every corridor between adjacent
     * blocks, spaced 3 tiles (96 px) apart vertically — safely within the
     * 108 px BFS jump limit. The corridor to the left of Block 0 also gets
     * escape platforms so the player can climb back after falling to the floor.
     */
    private void addCorridorClimbers(TileMap map, List<int[]> blocks,
                                     List<Rectangle2D> route, int widthTiles) {
        // Between each pair of adjacent blocks
        for (int i = 0; i < blocks.size() - 1; i++) {
            int[] bA = blocks.get(i);
            int[] bB = blocks.get(i + 1);
            placeCorridorPlatforms(map, route, bA[2] + 1, bB[0] - 1,
                Math.min(bA[1], bB[1]), widthTiles);
        }

        // Pre-spawn corridor: left of Block 0 (so player can climb back if they fall)
        int[] first = blocks.get(0);
        if (first[0] >= 6) {
            // At least 6 tiles before first block — room for a vertical ladder
            int preW = Math.min(4, first[0] - 2);
            int preX1 = Math.max(2, first[0] - preW - 1);
            int preX2 = preX1 + preW - 1;
            placeCorridorPlatforms(map, route, preX1, preX2, first[1], widthTiles);
        }
    }

    /**
     * Fills a single corridor (x1..x2 inclusive) with stepping platforms
     * from floor-escape level (y=15) up to {@code highestBlockTopY - 1}.
     * Platforms are 3 tiles wide, centered in the corridor.
     */
    private void placeCorridorPlatforms(TileMap map, List<Rectangle2D> route,
                                        int x1, int x2, int highestBlockTopY,
                                        int widthTiles) {
        int corrW = x2 - x1 + 1;
        if (corrW < 2) return;

        int platLen = Math.min(3, corrW);
        int platX = x1 + (corrW - platLen) / 2;

        // y=15: floor-escape level (3 tiles above floor, 96 px jump ✓)
        int startY = HEIGHT_TILES - 4; // y=15

        for (int platY = startY; platY >= highestBlockTopY + 1; platY -= 3) {
            if (platY <= 1) break;

            boolean clear = true;
            for (int xx = platX; xx < platX + platLen; xx++) {
                if (map.getTile(xx, platY) != TileType.EMPTY) { clear = false; break; }
            }
            if (clear && platX >= 1 && platX + platLen <= widthTiles - 1) {
                route.add(addRun(map, platX, platY, platLen, TileType.ONE_WAY_PLATFORM));
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Airspace clearance (block-safe)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Clears 3 tiles of headroom above every registered platform/block-top,
     * and clears the air in jump corridors between consecutive platforms.
     * Protects dungeon block interiors — only erases non-block WALL/DECORATION tiles.
     */
    private void clearBlockAirspace(TileMap map, List<Rectangle2D> route, List<int[]> blocks) {
        // Per-platform headroom (3 tiles above)
        for (Rectangle2D platform : route) {
            int startX = Math.max(1, (int) (platform.getMinX() / TileMap.TILE_SIZE) - 1);
            int endX = Math.min(map.getWidthTiles() - 2,
                (int) (platform.getMaxX() / TileMap.TILE_SIZE));
            int platformY = (int) (platform.getMinY() / TileMap.TILE_SIZE);
            for (int y = Math.max(1, platformY - 3); y < platformY; y++) {
                for (int x = startX; x <= endX; x++) {
                    if (!isInsideDungeonBlock(blocks, x, y)) {
                        TileType t = map.getTile(x, y);
                        if (t == TileType.WALL || t == TileType.DECORATION) {
                            map.setTile(x, y, TileType.EMPTY);
                        }
                    }
                }
            }
        }

        // Jump corridor between consecutive platforms
        for (int i = 0; i < route.size() - 1; i++) {
            Rectangle2D a = route.get(i);
            Rectangle2D b = route.get(i + 1);
            // Horizontal span of the gap
            int gx1 = Math.max(1, (int) (Math.min(a.getMaxX(), b.getMaxX()) / TileMap.TILE_SIZE) - 1);
            int gx2 = Math.min(map.getWidthTiles() - 2,
                (int) (Math.max(a.getMinX(), b.getMinX()) / TileMap.TILE_SIZE) + 1);
            if (gx1 > gx2) { int tmp = gx1; gx1 = gx2; gx2 = tmp; }
            int topY = Math.max(1, (int) (Math.min(a.getMinY(), b.getMinY()) / TileMap.TILE_SIZE) - 4);
            int botY = Math.min(map.getHeightTiles() - 2,
                (int) (Math.max(a.getMinY(), b.getMinY()) / TileMap.TILE_SIZE));
            for (int y = topY; y <= botY; y++) {
                for (int x = gx1; x <= gx2; x++) {
                    if (!isInsideDungeonBlock(blocks, x, y)) {
                        TileType t = map.getTile(x, y);
                        if (t == TileType.WALL || t == TileType.DECORATION) {
                            map.setTile(x, y, TileType.EMPTY);
                        }
                    }
                }
            }
        }
    }

    /** Returns true if tile (x, y) is inside the solid WALL body of any block. */
    private boolean isInsideDungeonBlock(List<int[]> blocks, int x, int y) {
        for (int[] b : blocks) {
            if (x >= b[0] && x <= b[2] && y >= b[1] && y <= b[3]) return true;
        }
        return false;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Floor clearance (must always run after block fill)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Unconditionally clears tile rows y=16 and y=17 everywhere.
     * PLAYER_HEIGHT=42px → player head is at pixel ~534 when on floor (y=18).
     * Tile y=16 spans px 512–543, which overlaps the head — a wall there traps the player.
     * Clearing y=16,17 ensures the floor corridor under all blocks is always passable.
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

    // ─────────────────────────────────────────────────────────────────────────
    // Floor escape platforms
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Ensures the player can always escape after falling to the floor.
     *
     * <p>Scans every 5 tiles. If no platform exists at y≥15 (3 tiles above floor,
     * 96 px — within 127.5 px jump limit) within ±5 tiles of the scan position,
     * and the tile at y=15 is not already a solid block wall, places a
     * ONE_WAY_PLATFORM there.  Skips positions under solid blocks (player walks
     * out laterally first, then climbs corridor platforms).
     *
     * <p>A second pass at y=12 bridges from the escape platform up to
     * Zone-1 heights (block tops at y=10–12).
     */
    private void addFloorEscapePlatforms(TileMap map, List<Rectangle2D> route, List<int[]> blocks) {
        PlacementValidator validator = new PlacementValidator(map);
        int escapeY = map.getHeightTiles() - 4; // y=15
        int relayY  = map.getHeightTiles() - 7; // y=12
        int scanStep = 5;

        // Pass 1 – escape at y=15
        for (int checkX = 3; checkX < map.getWidthTiles() - 6; checkX += scanStep) {
            // Skip if solid block occupies y=15 here (player walks out laterally)
            if (isInsideDungeonBlock(blocks, checkX, escapeY)) continue;
            if (validator.isInSpawnSafeZone(checkX, escapeY)) continue;
            if (validator.isInExitSafeZone(checkX, escapeY)) continue;

            boolean hasLow = false;
            for (Rectangle2D p : route) {
                int py    = (int) (p.getMinY() / TileMap.TILE_SIZE);
                int px    = (int) (p.getMinX() / TileMap.TILE_SIZE);
                int pxEnd = (int) (p.getMaxX() / TileMap.TILE_SIZE);
                if (px - 4 <= checkX && checkX <= pxEnd + 4 && py >= escapeY) {
                    hasLow = true;
                    break;
                }
            }
            if (hasLow) continue;

            boolean clear = true;
            for (int xx = checkX; xx < checkX + 3; xx++) {
                if (map.getTile(xx, escapeY) != TileType.EMPTY) { clear = false; break; }
            }
            if (clear) route.add(addRun(map, checkX, escapeY, 3, TileType.ONE_WAY_PLATFORM));
        }

        // Pass 2 – relay at y=12
        for (int checkX = 3; checkX < map.getWidthTiles() - 6; checkX += scanStep) {
            if (isInsideDungeonBlock(blocks, checkX, relayY)) continue;
            if (validator.isInSpawnSafeZone(checkX, relayY)) continue;
            if (validator.isInExitSafeZone(checkX, relayY)) continue;

            boolean hasMid = false;
            for (Rectangle2D p : route) {
                int py    = (int) (p.getMinY() / TileMap.TILE_SIZE);
                int px    = (int) (p.getMinX() / TileMap.TILE_SIZE);
                int pxEnd = (int) (p.getMaxX() / TileMap.TILE_SIZE);
                if (px - 4 <= checkX && checkX <= pxEnd + 4 && py >= 10 && py <= 14) {
                    hasMid = true;
                    break;
                }
            }
            if (hasMid) continue;

            boolean clear = true;
            for (int xx = checkX; xx < checkX + 3; xx++) {
                if (map.getTile(xx, relayY) != TileType.EMPTY) { clear = false; break; }
            }
            if (clear) route.add(addRun(map, checkX, relayY, 3, TileType.ONE_WAY_PLATFORM));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Spawn safe zone
    // ─────────────────────────────────────────────────────────────────────────

    private void clearSpawnSafeZone(TileMap map) {
        if (map.getSpawnSafeZone() == null) return;
        for (int y = 1; y < map.getHeightTiles() - 1; y++) {
            for (int x = 1; x < map.getWidthTiles() - 1; x++) {
                if (map.getSpawnSafeZone().containsTile(x, y)) {
                    TileType type = map.getTile(x, y);
                    // Do NOT clear WALL — every WALL is structural block material.
                    // Only remove hazards and cosmetic decorations near spawn.
                    if (type == TileType.SPIKE || type == TileType.DECORATION) {
                        map.setTile(x, y, TileType.EMPTY);
                    }
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Enemies, rewards, traps
    // ─────────────────────────────────────────────────────────────────────────

    private void addEnemyAndRewardZones(TileMap map, List<Rectangle2D> route, int levelIndex) {
        int enemies = levelIndex == 1 ? 2 : 5;
        PlacementValidator validator = new PlacementValidator(map);
        for (int i = 1; i < route.size() - 1 && enemies > 0;
                i += Math.max(2, route.size() / (levelIndex == 1 ? 3 : 6))) {
            Rectangle2D platform = route.get(i);
            if (platform.getWidth() < TileMap.TILE_SIZE * 4) continue;
            Rectangle2D enemyProbe = new Rectangle2D(platform.getMinX(),
                platform.getMinY() - 48, platform.getWidth(), 48);
            if (!validator.isValidRewardPlacement(enemyProbe)) continue;
            map.addEnemyZone(platform);
            enemies--;
        }
        for (int i = 1; i < route.size() - 1; i += 4) {
            Rectangle2D platform = route.get(i);
            Rectangle2D reward = new Rectangle2D(
                platform.getMinX() + platform.getWidth() / 2.0 - 12,
                platform.getMinY() - 28, 24, 24);
            if (validator.isValidRewardPlacement(reward)) {
                map.addRewardZone(reward);
            }
        }
    }

    private void addTrapsAndDecorations(TileMap map, List<Rectangle2D> route, int levelIndex) {
        int trapCount = levelIndex == 1 ? 4 : 8;
        PlacementValidator validator = new PlacementValidator(map);
        Set<Integer> mainCenters = new HashSet<>();
        for (Rectangle2D platform : route) {
            mainCenters.add((int) (platform.getMinX() / TileMap.TILE_SIZE));
        }
        int placed = 0;
        for (int i = 0; i < trapCount * 5 && placed < trapCount; i++) {
            Rectangle2D platform = route.get(random.nextInt(route.size()));
            int start = (int) (platform.getMinX() / TileMap.TILE_SIZE) + 1;
            int end   = (int) (platform.getMaxX() / TileMap.TILE_SIZE) - 2;
            if (end <= start) continue;
            int x = start + random.nextInt(end - start + 1);
            if (mainCenters.contains(x)) continue;
            int y = (int) (platform.getMinY() / TileMap.TILE_SIZE) - 1;
            if (y < 1) continue;
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

    // ─────────────────────────────────────────────────────────────────────────
    // Validation
    // ─────────────────────────────────────────────────────────────────────────

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
        int goal  = platformContainingX(platforms, exit.getMinX());
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
            if (visited[i]) reachable++;
            else unreachable.add(platforms.get(i));
        }
        boolean enoughUseful = reachable >= Math.ceil(platforms.size() * 0.70);
        boolean rewardsOk = areRewardsReachable(map, platforms, visited);
        boolean valid = spawnToExitReachable && enoughUseful && rewardsOk;
        String reason = valid ? "ok"
            : !spawnToExitReachable ? "spawn cannot reach exit"
            : !enoughUseful ? "less than 70% of platforms are reachable"
            : "reward platform unreachable";
        return new PlatformValidationResult(valid, spawnToExitReachable, platforms.size(),
            reachable, unreachable.size(), depth[goal],
            map.getRewardZones().size(), seed, reason, List.copyOf(unreachable));
    }

    private boolean areRewardsReachable(TileMap map, List<Rectangle2D> platforms,
                                         boolean[] visited) {
        for (Rectangle2D reward : map.getRewardZones()) {
            int index = platformContainingX(platforms,
                reward.getMinX() + reward.getWidth() / 2.0);
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
        boolean exitTooClose   = false;

        if (map.getSpawnSafeZone() == null)    errors.add("Spawn safe zone missing");
        if (map.getExitSafeZone() == null
                || map.getExitBounds() == null) errors.add("Exit safe zone missing");

        if (map.getExitBounds() != null && !validator.isValidExitPlacement(map.getExitBounds())) {
            errors.add("Invalid exit placement");
        }
        if (map.getExitBounds() != null) {
            double dx = Math.abs(map.getExitBounds().getMinX() - map.getSpawnX());
            exitTooClose = dx < TileMap.MIN_EXIT_DISTANCE_FROM_SPAWN_TILES * TileMap.TILE_SIZE;
            if (exitTooClose) errors.add("Exit inside minimum spawn distance");
        }

        int spawnTileX    = (int) ((map.getSpawnX() + Config.PLAYER_WIDTH / 2.0) / TileMap.TILE_SIZE);
        int spawnFootTileY = (int) ((map.getSpawnY() + Config.PLAYER_HEIGHT + 1) / TileMap.TILE_SIZE);
        TileType foot = map.getTile(spawnTileX, spawnFootTileY);
        if (!foot.isStandable() && !foot.isSolid()) errors.add("Spawn has no safe floor");

        Rectangle2D standing = new Rectangle2D(map.getSpawnX(), map.getSpawnY(),
            Config.PLAYER_WIDTH, Config.PLAYER_HEIGHT);
        for (Rectangle2D solid : map.getSolidTilesNear(standing)) {
            if (solid.intersects(standing)) { errors.add("Spawn overlaps wall"); break; }
        }

        for (int y = 0; y < map.getHeightTiles(); y++) {
            for (int x = 0; x < map.getWidthTiles(); x++) {
                TileType type = map.getTile(x, y);
                if (type == TileType.SPIKE && validator.isInSpawnSafeZone(x, y)) {
                    hasSpawnHazard = true; errors.add("Spike placed near spawn");
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
            if (map.getSpawnSafeZone() != null
                    && map.getSpawnSafeZone().intersects(enemyProbe)) {
                hasSpawnHazard = true; errors.add("Enemy placed near spawn");
            }
        }

        if (!platformResult.spawnToExitReachable()) errors.add("Spawn cannot reach exit");
        if (!platformResult.valid())                 errors.add(platformResult.reason());

        // Floor escape check: every 5 tiles must have a platform at y≥15
        // (3 tiles = 96 px above floor — within 127.5 px max jump).
        // Skip tiles that sit inside solid WALL blocks — player walks out
        // laterally and climbs the corridor platform to escape.
        int floorEscapeY = map.getHeightTiles() - 4; // y=15
        List<Rectangle2D> allPlatforms = map.getMainPlatforms();
        for (int checkX = 3; checkX < map.getWidthTiles() - 4; checkX += 5) {
            // Position is inside a solid block at y=15 → player escapes laterally
            if (map.getTile(checkX, floorEscapeY) == TileType.WALL) continue;
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

    // ─────────────────────────────────────────────────────────────────────────
    // Jump / platform utilities
    // ─────────────────────────────────────────────────────────────────────────

    private boolean canJumpBetween(Rectangle2D from, Rectangle2D to, PlayerStats stats) {
        double gap;
        if (to.getMinX() > from.getMaxX())       gap = to.getMinX() - from.getMaxX();
        else if (from.getMinX() > to.getMaxX())  gap = from.getMinX() - to.getMaxX();
        else                                      gap = 0;

        double verticalUp   = from.getMinY() - to.getMinY();
        double verticalDrop = to.getMinY() - from.getMinY();
        double hLimit = stats.maxHorizontalJumpDistance();
        if (stats.doubleJump()) hLimit *= 1.25;

        return gap <= hLimit
            && verticalUp   <= stats.maxVerticalJumpHeight() * 0.85
            && verticalDrop <= stats.maxVerticalJumpHeight() * 1.4;
    }

    private int platformContainingX(List<Rectangle2D> platforms, double x) {
        for (int i = 0; i < platforms.size(); i++) {
            Rectangle2D p = platforms.get(i);
            if (x >= p.getMinX() && x <= p.getMaxX()) return i;
        }
        return -1;
    }

    private Rectangle2D addRun(TileMap map, int startX, int y, int length, TileType type) {
        map.addPlatformRun(startX, y, length, type);
        return new Rectangle2D(startX * TileMap.TILE_SIZE, y * TileMap.TILE_SIZE,
            length * TileMap.TILE_SIZE, TileMap.TILE_SIZE);
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Generation report & fallback
    // ─────────────────────────────────────────────────────────────────────────

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

    private void carveGuaranteedBridge(TileMap map) {
        int y = 12;
        for (int x = 1; x < map.getWidthTiles() - 2; x += 5) {
            addRun(map, x, y, 4, TileType.ONE_WAY_PLATFORM);
        }
        map.setSpawn(TileMap.TILE_SIZE * 2, y * TileMap.TILE_SIZE - Config.PLAYER_HEIGHT);
        map.setExitBounds(new Rectangle2D((map.getWidthTiles() - 5) * TileMap.TILE_SIZE,
            y * TileMap.TILE_SIZE - 70, 40, 70));
    }
}
