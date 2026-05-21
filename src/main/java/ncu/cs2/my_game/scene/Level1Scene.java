package ncu.cs2.my_game.scene;

import javafx.animation.AnimationTimer;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.KeyCode;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import ncu.cs2.my_game.Config;
import ncu.cs2.my_game.Main;
import ncu.cs2.my_game.entity.Fireball;
import ncu.cs2.my_game.entity.Player;
import ncu.cs2.my_game.map.DungeonMapRenderer;
import ncu.cs2.my_game.map.PlatformDungeonGenerator;
import ncu.cs2.my_game.map.TileMap;
import ncu.cs2.my_game.physics.Collision;

/**
 * First stage: procedural underground platform dungeon.
 */
public class Level1Scene extends AnimationTimer {
    private static final int TRAP_DAMAGE = 12;

    private final Stage stage;
    private final GraphicsContext gc;
    private final TileMap tileMap;
    private final Rectangle2D goalDoor;
    private final Player player;

    private long lastNano = 0;
    private boolean transitioning = false;
    private boolean rKeyPressed = false;
    private double cameraX = 0;
    private double playerPlatformDropTimer = 0;

    public Level1Scene(Stage stage) {
        this.stage = stage;

        Canvas canvas = new Canvas(Config.WINDOW_WIDTH, Config.WINDOW_HEIGHT);
        gc = canvas.getGraphicsContext2D();
        Scene javafxScene = CanvasSceneSupport.createScaledCanvasScene(stage, canvas);

        tileMap = new PlatformDungeonGenerator().generateLevel(1);
        goalDoor = tileMap.getExitBounds();
        Fireball.setWorldBounds(tileMap.getWorldWidth(), Config.WINDOW_HEIGHT);

        player = new Player(tileMap.getSpawnX(), tileMap.getSpawnY());

        javafxScene.setOnKeyPressed(e -> {
            player.handleKeyPressed(e.getCode());
            if (e.getCode() == KeyCode.R && !player.isAlive()) {
                rKeyPressed = true;
            }
        });
        javafxScene.setOnKeyReleased(e -> player.handleKeyReleased(e.getCode()));

        stage.setScene(javafxScene);
        this.start();
    }

    @Override
    public void handle(long now) {
        if (lastNano == 0) {
            lastNano = now;
            return;
        }

        double dt = (now - lastNano) / 1_000_000_000.0;
        lastNano = now;
        if (dt > Config.MAX_DELTA_TIME) dt = Config.MAX_DELTA_TIME;

        update(dt);
        render(gc);
    }

    private void update(double dt) {
        if (!player.isAlive()) {
            if (rKeyPressed && !transitioning) {
                rKeyPressed = false;
                transitioning = true;
                this.stop();
                Main.startLevel1();
            }
            return;
        }

        player.update(dt);
        updatePlayerPlatformDrop(dt);
        resolvePlayerTerrainCollisions();
        tryResolvePlayerStandUp();
        clampPlayerToWorld();
        damagePlayerOnTraps();
        checkPlayerFireballsVsTerrain();
        checkGoalDoor();
        updateCamera();
    }

    private void resolvePlayerTerrainCollisions() {
        boolean landed = false;
        if (playerPlatformDropTimer <= 0) {
            for (Rectangle2D surface : tileMap.getStandableTilesNear(inflate(player.getHitbox(), 10))) {
                if (Collision.checkPlatform(player, surface)) {
                    player.setY(surface.getMinY() - player.getHeight());
                    player.setOnGround(true);
                    landed = true;
                    break;
                }
            }
        }
        if (!landed) player.setOnGround(false);

        for (Rectangle2D solid : tileMap.getSolidTilesNear(inflate(player.getHitbox(), 2))) {
            int result = Collision.resolveSolid(player, solid);
            if (result == -1) {
                player.setOnGround(true);
            }
        }
    }

    private void updatePlayerPlatformDrop(double dt) {
        if (playerPlatformDropTimer > 0) {
            playerPlatformDropTimer -= dt;
            if (playerPlatformDropTimer < 0) playerPlatformDropTimer = 0;
        }
        if (player.consumePlatformDropRequest()) {
            playerPlatformDropTimer = 0.22;
            player.setY(player.getY() + 5);
            player.setOnGround(false);
        }
    }

    private void tryResolvePlayerStandUp() {
        if (!player.wantsToStandUp()) return;

        Rectangle2D standingHitbox = player.getStandingHitbox();
        for (Rectangle2D solid : tileMap.getSolidTilesNear(inflate(standingHitbox, 2))) {
            if (Collision.checkAABB(standingHitbox, solid)) return;
        }
        for (Rectangle2D surface : tileMap.getStandableTilesNear(inflate(standingHitbox, 2))) {
            if (Collision.checkAABB(standingHitbox, surface)) return;
        }
        player.standUp();
    }

    private void clampPlayerToWorld() {
        if (player.getX() < TileMap.TILE_SIZE) player.setX(TileMap.TILE_SIZE);
        double maxX = tileMap.getWorldWidth() - TileMap.TILE_SIZE - player.getWidth();
        if (player.getX() > maxX) player.setX(maxX);
        if (player.getY() > tileMap.getWorldHeight()) {
            player.takeDamage(20);
            player.setX(tileMap.getSpawnX());
            player.setY(tileMap.getSpawnY());
            player.setVelocityY(0);
        }
    }

    private void damagePlayerOnTraps() {
        for (Rectangle2D trap : tileMap.getHazardTilesNear(inflate(player.getHitbox(), 2))) {
            if (Collision.checkAABB(player.getHitbox(), trap)) {
                player.takeDamage(TRAP_DAMAGE);
                return;
            }
        }
    }

    private void checkPlayerFireballsVsTerrain() {
        for (Fireball fireball : player.getFireballs()) {
            if (!fireball.isAlive() || fireball.isPiercingEnemies()) continue;
            for (Rectangle2D tile : tileMap.getStandableTilesNear(inflate(fireball.getHitbox(), 2))) {
                if (Collision.checkAABB(fireball.getHitbox(), tile)) {
                    fireball.destroy();
                    break;
                }
            }
        }
    }

    private void checkGoalDoor() {
        if (transitioning) return;
        if (Collision.checkAABB(player.getHitbox(), goalDoor)) {
            transitioning = true;
            Main.setPersistedPlayerState(player.getHp(), player.getMana());
            this.stop();
            Main.startLevel2();
        }
    }

    private void updateCamera() {
        double target = player.getX() + player.getWidth() / 2.0 - Config.WINDOW_WIDTH * 0.42;
        cameraX = Math.max(0, Math.min(target, tileMap.getWorldWidth() - Config.WINDOW_WIDTH));
    }

    private void render(GraphicsContext gc) {
        DungeonMapRenderer.draw(gc, tileMap, cameraX, "GOAL");

        gc.save();
        gc.translate(-cameraX, 0);
        player.draw(gc);
        gc.restore();

        HudRenderer.drawPlayerStatus(gc, player, "LEVEL 1");
        drawProgress(gc);

        if (!player.isAlive()) {
            drawGameOverOverlay(gc);
        }
    }

    private void drawProgress(GraphicsContext gc) {
        double ratio = player.getX() / Math.max(1.0, tileMap.getWorldWidth() - Config.WINDOW_WIDTH);
        gc.setFill(Color.web("#232733"));
        gc.fillRect(12, 84, 150, 5);
        gc.setFill(Color.web("#8af0ff"));
        gc.fillRect(12, 84, 150 * Math.max(0, Math.min(1, ratio)), 5);
    }

    private void drawGameOverOverlay(GraphicsContext gc) {
        gc.save();
        gc.setGlobalAlpha(0.65);
        gc.setFill(Color.BLACK);
        gc.fillRect(0, 0, Config.WINDOW_WIDTH, Config.WINDOW_HEIGHT);
        gc.restore();

        gc.setFill(Color.RED);
        gc.setFont(Font.font(60));
        gc.fillText("GAME OVER", Config.WINDOW_WIDTH / 2.0 - 173, Config.WINDOW_HEIGHT / 2.0 - 20);
        gc.setFill(Color.WHITE);
        gc.setFont(Font.font(20));
        gc.fillText("按 R 重新開始", Config.WINDOW_WIDTH / 2.0 - 65, Config.WINDOW_HEIGHT / 2.0 + 40);
    }

    private Rectangle2D inflate(Rectangle2D rect, double amount) {
        return new Rectangle2D(rect.getMinX() - amount, rect.getMinY() - amount,
            rect.getWidth() + amount * 2, rect.getHeight() + amount * 2);
    }
}
