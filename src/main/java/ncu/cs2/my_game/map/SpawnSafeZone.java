package ncu.cs2.my_game.map;

import javafx.geometry.Rectangle2D;

/**
 * Rectangular safety area around spawn or exit, expressed in world coordinates.
 */
public class SpawnSafeZone {
    private final Rectangle2D bounds;

    public SpawnSafeZone(double centerX, double centerY, int widthTiles, int heightTiles) {
        double width = widthTiles * TileMap.TILE_SIZE;
        double height = heightTiles * TileMap.TILE_SIZE;
        this.bounds = new Rectangle2D(centerX - width / 2.0, centerY - height / 2.0, width, height);
    }

    public Rectangle2D getBounds() {
        return bounds;
    }

    public boolean contains(double x, double y) {
        return bounds.contains(x, y);
    }

    public boolean containsTile(int tileX, int tileY) {
        double x = tileX * TileMap.TILE_SIZE + TileMap.TILE_SIZE / 2.0;
        double y = tileY * TileMap.TILE_SIZE + TileMap.TILE_SIZE / 2.0;
        return contains(x, y);
    }

    public boolean intersects(Rectangle2D rect) {
        return bounds.intersects(rect);
    }
}
