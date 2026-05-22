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
import ncu.cs2.my_game.effect.EffectManager;
import ncu.cs2.my_game.entity.Fireball;
import ncu.cs2.my_game.entity.Player;
import ncu.cs2.my_game.map.DungeonMapRenderer;
import ncu.cs2.my_game.map.MapValidationResult;
import ncu.cs2.my_game.map.PlatformValidationResult;
import ncu.cs2.my_game.map.PlatformDungeonGenerator;
import ncu.cs2.my_game.map.TileMap;
import ncu.cs2.my_game.physics.Collision;

import java.util.ArrayList;
import java.util.List;

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
    private final EffectManager effectManager;
    private final GameFlowController flowController;

    private long lastNano = 0;
    private boolean transitioning = false;
    private boolean portalEnterRequested = false;
    private double cameraX = 0;
    private double playerPlatformDropTimer = 0;
    private double fps = 0;

    public Level1Scene(Stage stage) {
        this.stage = stage;

        Canvas canvas = new Canvas(Config.WINDOW_WIDTH, Config.WINDOW_HEIGHT);
        gc = canvas.getGraphicsContext2D();
        Scene javafxScene = CanvasSceneSupport.createScaledCanvasScene(stage, canvas);

        tileMap = new PlatformDungeonGenerator().generateLevel(1);
        goalDoor = tileMap.getExitBounds();
        Fireball.setWorldBounds(tileMap.getWorldWidth(), Config.WINDOW_HEIGHT);

        player = new Player(tileMap.getSpawnX(), tileMap.getSpawnY());
        player.startInvincibility(Config.PLAYER_SPAWN_PROTECTION_SECONDS);
        effectManager = new EffectManager();
        flowController = GameFlowController.forScene(this::cleanup, () -> Main.startLevel1());
        Main.registerActiveScene("LEVEL_1", 1, GameState.PLAYING, this::cleanup);

        javafxScene.setOnKeyPressed(e -> {
            if (SceneTransitionManager.isTransitioning()) return;
            if (isPortalEnterKey(e.getCode()) && isNearGoalDoor()) {
                portalEnterRequested = true;
                e.consume();
                return;
            }
            if (flowController.handleKeyPressed(e)) return;
            player.handleKeyPressed(e.getCode());
            if (player.consumeAttackStarted()) {
                effectManager.playSlash(player);
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
        fps = dt > 0 ? 1.0 / dt : 0;
        SceneTransitionManager.tick(dt);

        if (!flowController.isPaused()) {
            update(dt);
        }
        render(gc);
    }

    private void update(double dt) {
        if (!player.isAlive()) {
            return;
        }

        if (player.consumeAttackStarted()) {
            effectManager.playSlash(player);
        }

        effectManager.update(dt);

        if (!player.isAlive()) {
            if (!transitioning) {
                transitioning = true;
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
        if (transitioning || SceneTransitionManager.isTransitioning()) return;
        boolean nearGoal = Collision.checkAABB(player.getHitbox(), goalDoor);
        if (nearGoal && portalEnterRequested && player.isOnGround()
            && SceneTransitionManager.tryBeginTransition("LEVEL_1_TO_LEVEL_2")) {
            transitioning = true;
            Main.setPersistedPlayerState(player.getHp(), player.getMana());
            this.stop();
            Main.startLevel2();
        }
        portalEnterRequested = false;
    }

    private void updateCamera() {
        double target = player.getX() + player.getWidth() / 2.0 - Config.WINDOW_WIDTH * 0.42;
        cameraX = Math.max(0, Math.min(target, tileMap.getWorldWidth() - Config.WINDOW_WIDTH));
    }

    private void render(GraphicsContext gc) {
        DungeonMapRenderer.draw(gc, tileMap, cameraX, "GOAL");
        if (flowController.isDebugVisible()) {
            DungeonMapRenderer.drawDebug(gc, tileMap, cameraX);
        }

        gc.save();
        gc.translate(-cameraX, 0);
        player.draw(gc);
        effectManager.draw(gc);
        drawGoalPrompt(gc);
        gc.restore();

        HudRenderer.drawPlayerStatus(gc, player, "LEVEL 1");
        drawProgress(gc);
        flowController.drawDebugOverlay(gc, debugLines());

        if (!player.isAlive()) {
            drawGameOverOverlay(gc);
        }
        flowController.drawPauseOverlay(gc);
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

    private List<String> debugLines() {
        List<String> lines = new ArrayList<>();
        PlatformValidationResult result = tileMap.getValidationResult();
        lines.add(String.format("FPS: %.0f", fps));
        lines.add(String.format("Player: %.1f, %.1f", player.getX(), player.getY()));
        lines.add(String.format("Velocity: %.1f, %.1f", player.getVelocityX(), player.getVelocityY()));
        lines.add("GameState: " + SceneTransitionManager.getCurrentGameState());
        lines.add("Transitioning: " + SceneTransitionManager.isTransitioning());
        lines.add("Stage: LEVEL 1");
        lines.add("Current level: " + SceneTransitionManager.getCurrentLevel());
        lines.add(String.format("Portal cooldown: %.2f", SceneTransitionManager.getPortalCooldown()));
        lines.add("Active loops: " + SceneTransitionManager.getActiveGameLoopCount());
        lines.add("Scene: " + SceneTransitionManager.getCurrentSceneName());
        lines.add("Enemy count: 0");
        lines.add("Boss alive: false");
        lines.add("Projectiles: " + player.getFireballs().size());
        lines.add("Effects: " + effectManager.getActiveEffectCount());
        lines.add("Paused: " + flowController.isPaused());
        if (result != null) {
            lines.add("Map seed: " + result.seed());
            lines.add("Map valid: " + result.valid() + " (" + result.reason() + ")");
            lines.add("Platforms: " + result.reachablePlatformCount() + "/" + result.platformCount());
            lines.add("Unreachable: " + result.unreachablePlatformCount());
        }
        MapValidationResult mapResult = tileMap.getMapValidationResult();
        if (mapResult != null) {
            lines.add("Spawn->Exit: " + mapResult.spawnToExitReachable());
            lines.add("Retry count: " + mapResult.retryCount());
            lines.add("Validation: " + mapResult.reason());
        }
        return lines;
    }

    private void drawGoalPrompt(GraphicsContext gc) {
        if (!isNearGoalDoor()) return;
        gc.setFill(Color.WHITE);
        gc.setFont(Font.font(14));
        gc.fillText("Press Enter", goalDoor.getMinX() - 24, goalDoor.getMinY() - 12);
    }

    private boolean isNearGoalDoor() {
        Rectangle2D area = inflate(goalDoor, 12);
        return Collision.checkAABB(player.getHitbox(), area);
    }

    private boolean isPortalEnterKey(KeyCode key) {
        return key == KeyCode.ENTER;
    }

    private void cleanup() {
        this.stop();
        effectManager.clear();
        player.getFireballs().clear();
        player.getIceProjectiles().clear();
    }
}
