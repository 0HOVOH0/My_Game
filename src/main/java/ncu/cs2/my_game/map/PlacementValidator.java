package ncu.cs2.my_game.map;

import javafx.geometry.Rectangle2D;

/**
 * Shared object placement checks for procedural maps.
 */
public class PlacementValidator {
    private final TileMap map;

    public PlacementValidator(TileMap map) {
        this.map = map;
    }

    public boolean isInsideSpawnSafeZone(double x, double y) {
        SpawnSafeZone zone = map.getSpawnSafeZone();
        return zone != null && zone.contains(x, y);
    }

    public boolean isInSpawnSafeZone(int tileX, int tileY) {
        SpawnSafeZone zone = map.getSpawnSafeZone();
        return zone != null && zone.containsTile(tileX, tileY);
    }

    public boolean isInsideExitSafeZone(double x, double y) {
        SpawnSafeZone zone = map.getExitSafeZone();
        return zone != null && zone.contains(x, y);
    }

    public boolean isInExitSafeZone(int tileX, int tileY) {
        SpawnSafeZone zone = map.getExitSafeZone();
        return zone != null && zone.containsTile(tileX, tileY);
    }

    public boolean canPlaceSpikeAt(int tileX, int tileY) {
        if (!map.isInside(tileX, tileY)) return false;
        if (isInSpawnSafeZone(tileX, tileY) || isInExitSafeZone(tileX, tileY)) return false;
        if (map.getTile(tileX, tileY) != TileType.EMPTY) return false;
        if (tileY <= 0 || map.getTile(tileX, tileY - 1) == TileType.WALL) return false;
        TileType below = map.getTile(tileX, tileY + 1);
        if (!below.isStandable() && !below.isSolid()) return false;

        boolean leftEscape = tileX > 1 && !map.getTile(tileX - 1, tileY).isHazardous()
            && map.getTile(tileX - 1, tileY) != TileType.WALL;
        boolean rightEscape = tileX < map.getWidthTiles() - 2 && !map.getTile(tileX + 1, tileY).isHazardous()
            && map.getTile(tileX + 1, tileY) != TileType.WALL;
        return leftEscape || rightEscape;
    }

    public boolean isValidExitPlacement(Rectangle2D exitBounds) {
        if (exitBounds == null) return false;
        if (map.getSpawnSafeZone() != null && map.getSpawnSafeZone().intersects(exitBounds)) return false;
        double exitCenterProgress = (exitBounds.getMinX() + exitBounds.getWidth() / 2.0)
            / map.getWorldWidth();
        if (exitCenterProgress < 0.98 || exitCenterProgress > 1.0) return false;
        double minDistance = TileMap.MIN_EXIT_DISTANCE_FROM_SPAWN_TILES * TileMap.TILE_SIZE;
        double dx = exitBounds.getMinX() - map.getSpawnX();
        if (Math.abs(dx) < minDistance) return false;
        int supportTileX = (int) ((exitBounds.getMinX() + exitBounds.getWidth() / 2.0) / TileMap.TILE_SIZE);
        int supportTileY = (int) (exitBounds.getMaxY() / TileMap.TILE_SIZE);
        TileType support = map.getTile(supportTileX, supportTileY);
        if (!support.isStandable() && !support.isSolid()) return false;

        int left = Math.max(1, (int) (exitBounds.getMinX() / TileMap.TILE_SIZE) - 1);
        int right = Math.min(map.getWidthTiles() - 2, (int) (exitBounds.getMaxX() / TileMap.TILE_SIZE) + 1);
        int y = Math.max(1, (int) (exitBounds.getMinY() / TileMap.TILE_SIZE));
        return map.getTile(left, y) != TileType.WALL || map.getTile(right, y) != TileType.WALL;
    }

    public boolean isValidRewardPlacement(Rectangle2D bounds) {
        if (map.getSpawnSafeZone() != null && map.getSpawnSafeZone().intersects(bounds)) return false;
        if (map.getExitSafeZone() != null && map.getExitSafeZone().intersects(bounds)) return false;
        return true;
    }
}
