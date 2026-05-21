package ncu.cs2.my_game.map;

import javafx.geometry.Rectangle2D;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Tile-based world data in absolute game coordinates.
 */
public class TileMap {
    public static final int TILE_SIZE = 32;

    private final int widthTiles;
    private final int heightTiles;
    private final TileType[][] tiles;
    private final List<Rectangle2D> mainPlatforms = new ArrayList<>();
    private final List<Rectangle2D> enemyZones = new ArrayList<>();
    private final List<Rectangle2D> rewardZones = new ArrayList<>();

    private double spawnX;
    private double spawnY;
    private Rectangle2D exitBounds;

    public TileMap(int widthTiles, int heightTiles) {
        this.widthTiles = widthTiles;
        this.heightTiles = heightTiles;
        this.tiles = new TileType[heightTiles][widthTiles];
        for (int y = 0; y < heightTiles; y++) {
            for (int x = 0; x < widthTiles; x++) {
                tiles[y][x] = TileType.EMPTY;
            }
        }
    }

    public int getWidthTiles() { return widthTiles; }

    public int getHeightTiles() { return heightTiles; }

    public double getWorldWidth() { return widthTiles * TILE_SIZE; }

    public double getWorldHeight() { return heightTiles * TILE_SIZE; }

    public TileType getTile(int tileX, int tileY) {
        if (!isInside(tileX, tileY)) return TileType.WALL;
        return tiles[tileY][tileX];
    }

    public void setTile(int tileX, int tileY, TileType type) {
        if (isInside(tileX, tileY)) {
            tiles[tileY][tileX] = type;
        }
    }

    public boolean isInside(int tileX, int tileY) {
        return tileX >= 0 && tileX < widthTiles && tileY >= 0 && tileY < heightTiles;
    }

    public Rectangle2D tileBounds(int tileX, int tileY) {
        return new Rectangle2D(tileX * TILE_SIZE, tileY * TILE_SIZE, TILE_SIZE, TILE_SIZE);
    }

    public List<Rectangle2D> getSolidTilesNear(Rectangle2D area) {
        return collectTiles(area, true, false, false);
    }

    public List<Rectangle2D> getStandableTilesNear(Rectangle2D area) {
        return collectTiles(area, false, true, false);
    }

    public List<Rectangle2D> getHazardTilesNear(Rectangle2D area) {
        return collectTiles(area, false, false, true);
    }

    private List<Rectangle2D> collectTiles(Rectangle2D area, boolean solid, boolean standable, boolean hazard) {
        int minX = Math.max(0, (int) Math.floor(area.getMinX() / TILE_SIZE) - 1);
        int maxX = Math.min(widthTiles - 1, (int) Math.floor(area.getMaxX() / TILE_SIZE) + 1);
        int minY = Math.max(0, (int) Math.floor(area.getMinY() / TILE_SIZE) - 1);
        int maxY = Math.min(heightTiles - 1, (int) Math.floor(area.getMaxY() / TILE_SIZE) + 1);

        List<Rectangle2D> result = new ArrayList<>();
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                TileType type = tiles[y][x];
                if ((solid && type.isSolid())
                    || (standable && type.isStandable())
                    || (hazard && type.isHazardous())) {
                    result.add(tileBounds(x, y));
                }
            }
        }
        return result;
    }

    public void addPlatformRun(int startTileX, int tileY, int length, TileType type) {
        int x0 = Math.max(0, startTileX);
        int x1 = Math.min(widthTiles - 1, startTileX + length - 1);
        for (int x = x0; x <= x1; x++) {
            setTile(x, tileY, type);
        }
        mainPlatforms.add(new Rectangle2D(x0 * TILE_SIZE, tileY * TILE_SIZE,
            (x1 - x0 + 1) * TILE_SIZE, TILE_SIZE));
    }

    public void addMainPlatform(Rectangle2D platform) {
        mainPlatforms.add(platform);
    }

    public List<Rectangle2D> getMainPlatforms() {
        return Collections.unmodifiableList(mainPlatforms);
    }

    public void addEnemyZone(Rectangle2D zone) {
        enemyZones.add(zone);
    }

    public List<Rectangle2D> getEnemyZones() {
        return Collections.unmodifiableList(enemyZones);
    }

    public void addRewardZone(Rectangle2D zone) {
        rewardZones.add(zone);
    }

    public List<Rectangle2D> getRewardZones() {
        return Collections.unmodifiableList(rewardZones);
    }

    public double getSpawnX() { return spawnX; }

    public double getSpawnY() { return spawnY; }

    public void setSpawn(double spawnX, double spawnY) {
        this.spawnX = spawnX;
        this.spawnY = spawnY;
    }

    public Rectangle2D getExitBounds() { return exitBounds; }

    public void setExitBounds(Rectangle2D exitBounds) {
        this.exitBounds = exitBounds;
    }
}
