package ncu.cs2.my_game.map;

import javafx.geometry.Rectangle2D;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import ncu.cs2.my_game.Config;

/**
 * Lightweight procedural dungeon tile renderer.
 */
public final class DungeonMapRenderer {
    private DungeonMapRenderer() {}

    public static void draw(GraphicsContext gc, TileMap map, double cameraX, String exitLabel) {
        drawBackground(gc, cameraX);

        int firstTileX = Math.max(0, (int) (cameraX / TileMap.TILE_SIZE) - 1);
        int lastTileX = Math.min(map.getWidthTiles() - 1,
            (int) ((cameraX + Config.WINDOW_WIDTH) / TileMap.TILE_SIZE) + 2);

        for (int y = 0; y < map.getHeightTiles(); y++) {
            for (int x = firstTileX; x <= lastTileX; x++) {
                TileType type = map.getTile(x, y);
                if (type == TileType.EMPTY || type == TileType.SPAWN || type == TileType.EXIT) continue;
                drawTile(gc, type, x * TileMap.TILE_SIZE - cameraX, y * TileMap.TILE_SIZE);
            }
        }

        drawExit(gc, map.getExitBounds(), cameraX, exitLabel);
    }

    private static void drawBackground(GraphicsContext gc, double cameraX) {
        gc.setFill(Color.web("#121219"));
        gc.fillRect(0, 0, Config.WINDOW_WIDTH, Config.WINDOW_HEIGHT);

        gc.setFill(Color.web("#171823"));
        for (int i = 0; i < 18; i++) {
            double x = (i * 73 - (cameraX * 0.18) % 73);
            gc.fillRect(x, 80 + (i % 5) * 55, 38, 6);
        }
    }

    private static void drawTile(GraphicsContext gc, TileType type, double x, double y) {
        double s = TileMap.TILE_SIZE;
        switch (type) {
            case FLOOR, WALL -> drawStoneBlock(gc, x, y, s, type == TileType.WALL);
            case PLATFORM -> drawPlatform(gc, x, y, s, Color.web("#65707d"), Color.web("#303741"));
            case ONE_WAY_PLATFORM -> drawPlatform(gc, x, y, s, Color.web("#7d6d42"), Color.web("#3f361c"));
            case LADDER -> drawLadder(gc, x, y, s);
            case SPIKE -> drawSpike(gc, x, y, s);
            case DECORATION -> drawDecoration(gc, x, y, s);
            default -> {
            }
        }
    }

    private static void drawStoneBlock(GraphicsContext gc, double x, double y, double s, boolean wall) {
        gc.setFill(wall ? Color.web("#30333c") : Color.web("#454a54"));
        gc.fillRect(x, y, s, s);
        gc.setStroke(Color.web("#1a1c22"));
        gc.setLineWidth(1);
        gc.strokeRect(x + 0.5, y + 0.5, s - 1, s - 1);
        gc.setStroke(Color.web("#5e6570"));
        gc.strokeLine(x + 4, y + 8, x + s - 5, y + 8);
        gc.strokeLine(x + s / 2, y + 8, x + s / 2, y + s - 4);
    }

    private static void drawPlatform(GraphicsContext gc, double x, double y, double s,
                                     Color body, Color edge) {
        gc.setFill(body);
        gc.fillRect(x, y, s, 16);
        gc.setFill(edge);
        gc.fillRect(x, y, s, 4);
        gc.setStroke(Color.web("#17191f"));
        gc.strokeRect(x + 0.5, y + 0.5, s - 1, 15);
    }

    private static void drawLadder(GraphicsContext gc, double x, double y, double s) {
        gc.setStroke(Color.web("#b88445"));
        gc.setLineWidth(3);
        gc.strokeLine(x + 9, y + 2, x + 9, y + s - 2);
        gc.strokeLine(x + s - 9, y + 2, x + s - 9, y + s - 2);
        gc.setLineWidth(2);
        gc.strokeLine(x + 9, y + 8, x + s - 9, y + 8);
        gc.strokeLine(x + 9, y + 20, x + s - 9, y + 20);
    }

    private static void drawSpike(GraphicsContext gc, double x, double y, double s) {
        gc.setFill(Color.web("#8e1b1b"));
        gc.fillPolygon(new double[]{x + 3, x + s / 2, x + s - 3},
            new double[]{y + s, y + 7, y + s}, 3);
        gc.setStroke(Color.web("#d7d0c6"));
        gc.strokePolygon(new double[]{x + 3, x + s / 2, x + s - 3},
            new double[]{y + s, y + 7, y + s}, 3);
    }

    private static void drawDecoration(GraphicsContext gc, double x, double y, double s) {
        gc.setFill(Color.web("#f0a64a"));
        gc.fillOval(x + 12, y + 8, 8, 13);
        gc.setFill(Color.web("#7a4b24"));
        gc.fillRect(x + 14, y + 20, 4, 10);
        gc.setFill(Color.web("#262a32"));
        gc.fillRect(x + 10, y + 29, 12, 3);
    }

    private static void drawExit(GraphicsContext gc, Rectangle2D exit, double cameraX, String exitLabel) {
        double x = exit.getMinX() - cameraX;
        double y = exit.getMinY();
        gc.save();
        gc.setGlobalAlpha(0.28);
        gc.setFill(Color.web("#63d8ff"));
        gc.fillOval(x - 8, y - 6, exit.getWidth() + 16, exit.getHeight() + 12);
        gc.restore();

        gc.setStroke(Color.web("#8af0ff"));
        gc.setLineWidth(3);
        gc.strokeRoundRect(x, y, exit.getWidth(), exit.getHeight(), 16, 16);
        gc.setFill(Color.web("#152d38"));
        gc.fillRoundRect(x + 6, y + 8, exit.getWidth() - 12, exit.getHeight() - 10, 12, 12);
        gc.setFill(Color.web("#bff9ff"));
        gc.fillText(exitLabel, x - 3, y - 7);
    }

    public static void drawDebug(GraphicsContext gc, TileMap map, double cameraX) {
        PlatformValidationResult result = map.getValidationResult();
        if (result == null) return;

        gc.save();
        gc.setLineWidth(2);
        gc.setStroke(Color.web("#36d66b"));
        for (Rectangle2D platform : map.getMainPlatforms()) {
            gc.strokeRect(platform.getMinX() - cameraX, platform.getMinY(),
                platform.getWidth(), platform.getHeight());
        }

        gc.setStroke(Color.web("#ff334e"));
        for (Rectangle2D platform : result.unreachablePlatforms()) {
            gc.strokeRect(platform.getMinX() - cameraX, platform.getMinY() - 3,
                platform.getWidth(), platform.getHeight() + 6);
        }

        gc.setStroke(Color.web("#ffd84d"));
        for (Rectangle2D reward : map.getRewardZones()) {
            gc.strokeRect(reward.getMinX() - cameraX - 6, reward.getMinY() - 6,
                reward.getWidth() + 12, reward.getHeight() + 12);
        }
        gc.restore();
    }
}
