package ncu.cs2.my_game.scene;

import javafx.animation.AnimationTimer;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import ncu.cs2.my_game.Config;
import ncu.cs2.my_game.Main;
import ncu.cs2.my_game.economy.GoldPickup;
import ncu.cs2.my_game.economy.GoldSpawnManager;
import ncu.cs2.my_game.entity.BombEntity;
import ncu.cs2.my_game.entity.Enemy;
import ncu.cs2.my_game.entity.Fireball;
import ncu.cs2.my_game.entity.IceProjectile;
import ncu.cs2.my_game.entity.Player;
import ncu.cs2.my_game.entity.RangedEnemy;
import ncu.cs2.my_game.item.Inventory;
import ncu.cs2.my_game.item.InventorySlot;
import ncu.cs2.my_game.item.ItemSpawnManager;
import ncu.cs2.my_game.item.PickupItem;
import ncu.cs2.my_game.item.PickupType;
import ncu.cs2.my_game.item.UseContext;
import ncu.cs2.my_game.physics.Collision;
import ncu.cs2.my_game.stage.StageDefinition;
import ncu.cs2.my_game.stage.StageType;
import ncu.cs2.my_game.state.EnemySnapshot;
import ncu.cs2.my_game.state.StageSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 第二關場景，繼承 AnimationTimer 充當遊戲迴圈。
 *
 * <p>地圖與第一關相同，但加入三名巡邏敵人與一個加血道具。
 * 抵達右側終點門後切換至 Boss 場景。</p>
 *
 * <pre>
 * 平台與物件配置（y 軸向下）：
 *
 *  y=150  ──────────────────[P8]──[BOSS]      ← 終點門
 *  y=165          [P7 敵人3]
 *  y=200  [P6]
 *  y=275          [P5 敵人2]
 *  y=355               [P3 加血道具]  [P4 死路]
 *  y=430          [P2 敵人1]
 *  y=500  [P1]
 *  y=550  ====== GROUND ======
 * </pre>
 */
public class Level2Scene extends AnimationTimer {

    // ── 地圖常數 ─────────────────────────────────────────────────────────────

    /** 地板 Y 座標 */
    private static final double GROUND_Y = Config.WINDOW_HEIGHT - Config.GROUND_THICKNESS;

    /** 普通關世界寬度，Canvas 只顯示其中一段。 */
    private static final double WORLD_WIDTH = Config.WINDOW_WIDTH * 2.0;

    private static final double CULL_MARGIN = 96.0;

    /** 平台厚度（像素） */
    private static final double PLAT_H = 16.0;

    /** 終點門寬度 */
    private static final double GOAL_W = 50.0;

    /** 終點門高度 */
    private static final double GOAL_H = 150.0;

    /** 加血道具尺寸（正方形，像素） */
    private static final double ITEM_SIZE = 20.0;

    /** 加血道具回復的血量 */
    private static final int ITEM_HEAL = 30;

    // ── 欄位 ─────────────────────────────────────────────────────────────────

    /** 主視窗參考 */
    private final Stage stage;

    /** 繪圖用 GraphicsContext */
    private final GraphicsContext gc;

    /** 玩家實體 */
    private final Player player;

    /** 地板碰撞框 */
    private final Rectangle2D ground;

    /** 8 個平台的碰撞框陣列（配置與第一關相同） */
    private final Rectangle2D[] platforms;

    /** 掩體/牆壁，具備完整實體碰撞並阻擋投射物。 */
    private final Rectangle2D[] covers;

    /** 小兵視線與射擊會被平台和牆壁共同阻擋。 */
    private final Rectangle2D[] visionBlockers;

    private final Rectangle2D[] navigationSurfaces;

    /** 終點門碰撞框；碰到後切換至 Boss 關 */
    private final Rectangle2D goalDoor;

    /**
     * 三個巡邏敵人，分別放置在 P2、P5、P7。
     * 血量歸零後 isAlive() 回傳 false，不再更新或繪製。
     */
    private final Enemy[] enemies;

    /** 可撿取道具列表 */
    private final List<PickupItem> pickupItems;

    /** 可撿取金幣列表 */
    private final List<GoldPickup> goldPickups;

    /** 玩家放置的炸彈 */
    private final List<BombEntity> bombs;

    /** 跨關卡背包 */
    private final Inventory inventory;

    /** 敵人掉落是否已處理 */
    private final boolean[] enemyDropsHandled;

    /** 掉落用亂數 */
    private final Random random;

    /** 道具安全生成器 */
    private final ItemSpawnManager itemSpawnManager;

    /** 金幣安全生成器 */
    private final GoldSpawnManager goldSpawnManager;

    /** 本次隨機生成關卡的設定。 */
    private final StageDefinition stageDefinition;

    /** 是否顯示背包 overlay。 */
    private boolean inventoryOpen;

    /** 目前 HUD 高亮的背包 slot。 */
    private int selectedInventorySlot;

    /** 關卡剛進入時的狀態快照 */
    private final StageSnapshot initialSnapshot;

    /**
     * 加血道具碰撞框。
     * 被玩家碰觸後設為 null，表示已拾取。
     */
    private Rectangle2D healthItem;

    /** 上一幀時間戳記（奈秒）；0 表示尚未初始化 */
    private long lastNano = 0;

    /** 是否已觸發場景切換，防止 handle() 重複呼叫 startBoss */
    private boolean transitioning = false;

    /** 玩家死亡後是否按下了 R 鍵，觸發重啟 Level2 */
    private boolean rKeyPressed = false;

    /** 目前鏡頭左側世界座標。 */
    private double cameraX = 0;

    private double playerPlatformDropTimer = 0;

    // ─────────────────────────────────────────────────────────────────────────

    /**
     * 建構子：初始化所有場景物件並立即啟動 AnimationTimer。
     *
     * @param stage 主視窗，用於切換 JavaFX Scene
     */
    public Level2Scene(Stage stage, StageDefinition stageDefinition) {
        this.stage = stage;
        this.stageDefinition = stageDefinition;

        // ── 建立 Canvas 與 JavaFX Scene ──────────────────────────────────────
        Canvas canvas = new Canvas(Config.WINDOW_WIDTH, Config.WINDOW_HEIGHT);
        gc = canvas.getGraphicsContext2D();

        Scene javafxScene = CanvasSceneSupport.createScaledCanvasScene(stage, canvas);
        Fireball.setWorldBounds(WORLD_WIDTH, Config.WINDOW_HEIGHT);

        // ── 初始化玩家（帶入 Level1 結束時的血量） ────────────────────────────
        player = new Player(50, 440);
        player.setHp(Main.getPersistedHp());
        player.setMana(Main.getPersistedMana());

        // ── 地板 ──────────────────────────────────────────────────────────────
        ground = new Rectangle2D(0, GROUND_Y, WORLD_WIDTH, Config.GROUND_THICKNESS);

        platforms = createProceduralPlatforms();
        covers = createCoverWalls();
        visionBlockers = mergeBlockers(platforms, covers);
        navigationSurfaces = mergeBlockers(new Rectangle2D[] { ground }, visionBlockers);

        // ── 終點門 ────────────────────────────────────────────────────────────
        boolean lowerGoal = Math.random() < 0.45;
        goalDoor = lowerGoal
            ? new Rectangle2D(WORLD_WIDTH - GOAL_W - 42, GROUND_Y - GOAL_H, GOAL_W, GOAL_H)
            : new Rectangle2D(WORLD_WIDTH - GOAL_W - 20, 150 - GOAL_H + PLAT_H, GOAL_W, GOAL_H);

        // ── 敵人（每個敵人的巡邏範圍對應所在平台邊界） ───────────────────────
        // 巡邏右邊界 = 平台右端 - 敵人寬度，確保不會走出平台
        List<EnemySnapshot> enemySnapshots = createEnemySnapshots();
        enemies = createEnemies(enemySnapshots);

        pickupItems = new ArrayList<>();
        goldPickups = new ArrayList<>();
        bombs = new ArrayList<>();
        inventory = Main.getInventory();
        enemyDropsHandled = new boolean[enemies.length];
        random = new Random();
        itemSpawnManager = new ItemSpawnManager(ground, platforms);
        goldSpawnManager = new GoldSpawnManager(ground, platforms);
        inventoryOpen = false;
        selectedInventorySlot = 0;

        // 示範地板道具：玩家碰到後進背包，按 U/I/O 使用
        double[] startPotion = pickupPoint(0, 0.55);
        double[] fireScroll = pickupPoint(2, 0.45);
        double[] bomb = pickupPoint(7, 0.28);
        addPickup(PickupType.SMALL_POTION, startPotion[0], startPotion[1]);
        addPickup(PickupType.FIRE_SCROLL, fireScroll[0], fireScroll[1]);
        addPickup(PickupType.BOMB, bomb[0], bomb[1]);
        addExplorationSupplies();

        // ── 加血道具（放在 P3 中央偏右，玩家進到 P3 後容易看到）───────────────
        // P3 頂面 y=355，道具底部對齊平台：item.y = 355 - ITEM_SIZE = 335
        double[] healthPoint = pickupPoint(2, 0.72);
        healthItem = new Rectangle2D(healthPoint[0], healthPoint[1], ITEM_SIZE, ITEM_SIZE);

        initialSnapshot = new StageSnapshot(
            player.getX(), player.getY(), player.getHp(), player.getMana(),
            inventory, pickupItems, goldPickups, enemySnapshots,
            true, healthItem.getMinX(), healthItem.getMinY(),
            healthItem.getWidth(), healthItem.getHeight()
        );

        // ── 綁定鍵盤事件 ──────────────────────────────────────────────────────
        javafxScene.setOnKeyPressed(e -> {
            player.handleKeyPressed(e.getCode());
            handleInventoryKey(e.getCode());
            if (e.getCode() == KeyCode.B) {
                inventoryOpen = !inventoryOpen;
            }
            if (e.getCode() == KeyCode.R && !player.isAlive()) {
                rKeyPressed = true;
            }
        });
        javafxScene.setOnKeyReleased(e -> player.handleKeyReleased(e.getCode()));

        // ── 切換視窗並啟動迴圈 ────────────────────────────────────────────────
        stage.setScene(javafxScene);
        this.start();   // 呼叫 AnimationTimer.start()
    }

    // ── AnimationTimer 主迴圈 ─────────────────────────────────────────────────

    /**
     * 每幀由 JavaFX 呼叫；計算 deltaTime 後依序執行更新與渲染。
     * 第一幀（lastNano == 0）只記錄時間戳記，避免 dt 異常大。
     *
     * @param now 目前時間戳記（奈秒）
     */
    @Override
    public void handle(long now) {
        // 第一幀：初始化時間基準，跳過本幀邏輯
        if (lastNano == 0) {
            lastNano = now;
            return;
        }

        // 計算時間差（秒），上限 0.05s 防止大幀跳躍
        double dt = (now - lastNano) / 1_000_000_000.0;
        lastNano = now;
        if (dt > Config.MAX_DELTA_TIME) dt = Config.MAX_DELTA_TIME;

        update(dt);
        render(gc);
    }

    // ── update ────────────────────────────────────────────────────────────────

    /**
     * 每幀更新邏輯，執行順序：
     * <ol>
     *   <li>玩家物理（重力、輸入、位移）</li>
     *   <li>每個存活敵人的物理</li>
     *   <li>玩家平台碰撞解析</li>
     *   <li>每個敵人的平台碰撞解析</li>
     *   <li>玩家左右邊界限制</li>
     *   <li>玩家攻擊 vs 敵人</li>
     *   <li>敵人接觸 vs 玩家</li>
     *   <li>加血道具拾取判定</li>
     *   <li>終點門判定</li>
     * </ol>
     *
     * @param dt 時間差（秒）
     */
    private void update(double dt) {
        // 玩家死亡後等待 R 鍵，重啟 Level2（血量重置為滿血）
        if (!player.isAlive()) {
            if (rKeyPressed && !transitioning) {
                rKeyPressed   = false;
                rollbackToInitialSnapshot();
            }
            return;
        }

        // 1. 玩家物理更新
        player.update(dt);
        updatePlayerPlatformDrop(dt);

        // 2. 敵人物理與視線記憶；死亡遠程兵仍會更新殘留子彈，避免停在空中
        for (Enemy enemy : enemies) {
            if (enemy.isAlive()) {
                enemy.setNavigationSurfaces(navigationSurfaces);
                enemy.updateAwareness(player, visionBlockers);
            }
            enemy.update(dt);
        }

        // 3. 玩家平台碰撞解析
        resolvePlayerPlatformCollisions();

        // 4. 每個存活敵人的平台碰撞解析
        resolveEnemyPlatformCollisions();

        // 4.5 若玩家放開 S，且頭頂空間足夠，恢復站立
        tryResolvePlayerStandUp();

        // 5. 玩家左右邊界：不讓玩家走出世界
        if (player.getX() < 0)
            player.setX(0);
        if (player.getX() + player.getWidth() > WORLD_WIDTH)
            player.setX(WORLD_WIDTH - player.getWidth());
        updateCamera();

        // 6. 玩家攻擊命中敵人
        checkPlayerAttackVsEnemies();

        // 7. 玩家火球命中敵人或撞到地形
        checkPlayerFireballs();
        checkPlayerIceProjectiles();

        // 8. 敵人接觸傷害玩家
        checkEnemyContactVsPlayer();
        checkRangedEnemyProjectiles();

        // 9. 敵人死亡掉落
        checkEnemyDrops();

        // 10. 地板道具拾取
        checkPickupItems();

        // 11. 金幣拾取
        checkGoldPickups();
        updateBombs(dt);

        // 12. 加血道具拾取
        checkHealthItem();

        // 13. 終點門判定
        checkGoalDoor();
    }

    /**
     * 使用背包快捷鍵。
     */
    private void handleInventoryKey(KeyCode key) {
        int slotIndex = keyToSlotIndex(key);
        if (slotIndex < 0) return;

        selectedInventorySlot = slotIndex;
        if (trySwapGroundItem(slotIndex)) {
            return;
        }
        inventory.useSlot(slotIndex, new UseContext(player, enemies, null, bombs));
    }

    private void tryResolvePlayerStandUp() {
        if (!player.wantsToStandUp()) return;

        Rectangle2D standingHitbox = player.getStandingHitbox();
        for (Rectangle2D platform : platforms) {
            if (Collision.checkAABB(standingHitbox, platform)) {
                return;
            }
        }
        for (Rectangle2D cover : covers) {
            if (Collision.checkAABB(standingHitbox, cover)) {
                return;
            }
        }
        player.standUp();
    }

    /**
     * 依快照建立敵人陣列。
     */
    private Enemy[] createEnemies(List<EnemySnapshot> snapshots) {
        Enemy[] result = new Enemy[snapshots.size()];
        for (int i = 0; i < snapshots.size(); i++) {
            result[i] = snapshots.get(i).createEnemy(player);
        }
        return result;
    }

    private List<EnemySnapshot> createEnemySnapshots() {
        List<EnemySnapshot> snapshots = new ArrayList<>();
        double hpScale = 1.0 + stageDefinition.getDifficulty() * 0.08;
        double damageScale = 1.0 + stageDefinition.getDifficulty() * 0.05;
        double speedScale = Math.min(1.35, 1.0 + stageDefinition.getDifficulty() * 0.025);
        double projectileScale = Math.min(1.35, 1.0 + stageDefinition.getDifficulty() * 0.025);

        addEnemyOnPlatform(snapshots, 1, 0.45, false, hpScale, damageScale, speedScale, projectileScale);
        addEnemyOnPlatform(snapshots, 4, 0.45, true, hpScale, damageScale, speedScale, projectileScale);
        addEnemyOnPlatform(snapshots, 6, 0.45, false, hpScale, damageScale, speedScale, projectileScale);
        addEnemyOnPlatform(snapshots, 8, 0.42, false, hpScale, damageScale, speedScale, projectileScale);
        addEnemyOnPlatform(snapshots, 9, 0.45, true, hpScale, damageScale, speedScale, projectileScale);
        addEnemyOnPlatform(snapshots, 10, 0.45, false, hpScale, damageScale, speedScale, projectileScale);
        addEnemyOnPlatform(snapshots, 11, 0.48,
            stageDefinition.getDifficulty() % 2 == 0, hpScale, damageScale, speedScale, projectileScale);

        if (stageDefinition.getDifficulty() >= 4) {
            addEnemyOnPlatform(snapshots, 12, 0.5, true, hpScale, damageScale, speedScale, projectileScale);
        }
        if (stageDefinition.getDifficulty() >= 8) {
            snapshots.add(new EnemySnapshot(900, GROUND_Y - Enemy.ENEMY_H, 780, 980,
                false, hpScale, damageScale, speedScale, projectileScale));
            snapshots.add(new EnemySnapshot(1280, GROUND_Y - Enemy.ENEMY_H, 1160, 1420,
                true, hpScale, damageScale, speedScale, projectileScale));
        }
        return snapshots;
    }

    private void addEnemyOnPlatform(List<EnemySnapshot> snapshots, int platformIndex, double ratio,
                                    boolean ranged, double hpScale, double damageScale,
                                    double speedScale, double projectileScale) {
        if (platformIndex < 0 || platformIndex >= platforms.length) return;
        Rectangle2D p = platforms[platformIndex];
        double patrolLeft = p.getMinX();
        double patrolRight = Math.max(patrolLeft + Enemy.ENEMY_W + 8, p.getMaxX());
        double x = patrolLeft + Math.max(6.0, (p.getWidth() - Enemy.ENEMY_W) * ratio);
        x = Math.min(x, patrolRight - Enemy.ENEMY_W);
        snapshots.add(new EnemySnapshot(x, p.getMinY() - Enemy.ENEMY_H,
            patrolLeft, patrolRight, ranged, hpScale, damageScale, speedScale, projectileScale));
    }

    private Rectangle2D[] createProceduralPlatforms() {
        boolean complex = stageDefinition.getDifficulty() >= 5
            || stageDefinition.getType() == StageType.PLATFORM;
        double upperJitter = complex ? 22.0 : 12.0;
        return new Rectangle2D[] {
            new Rectangle2D(20 + rand(-8, 18), 500 + rand(-8, 8), platformWidth(5, 7), PLAT_H),
            new Rectangle2D(210 + rand(-25, 28), 430 + rand(-12, 14), platformWidth(4, 7), PLAT_H),
            new Rectangle2D(410 + rand(-28, 34), 355 + rand(-14, 14), platformWidth(4, 7), PLAT_H),
            new Rectangle2D(570 + rand(-18, 32), 440 + rand(-12, 14), platformWidth(4, 8), PLAT_H),
            new Rectangle2D(225 + rand(-28, 28), 275 + rand(-upperJitter, upperJitter), platformWidth(4, 8), PLAT_H),
            new Rectangle2D(50 + rand(-20, 26), 200 + rand(-upperJitter, upperJitter), platformWidth(4, 7), PLAT_H),
            new Rectangle2D(275 + rand(-28, 34), 165 + rand(-16, 18), platformWidth(4, 8), PLAT_H),
            new Rectangle2D(520 + rand(-20, 30), 150 + rand(-10, 16), platformWidth(7, 10), PLAT_H),
            new Rectangle2D(810 + rand(-32, 36), 480 + rand(-12, 12), platformWidth(5, 9), PLAT_H),
            new Rectangle2D(1000 + rand(-35, 38), 395 + rand(-18, 18), platformWidth(4, 8), PLAT_H),
            new Rectangle2D(1185 + rand(-35, 42), 310 + rand(-20, 20), platformWidth(5, 9), PLAT_H),
            new Rectangle2D(1360 + rand(-38, 35), 225 + rand(-22, 22), platformWidth(5, 9), PLAT_H),
            new Rectangle2D(1420 + rand(-28, 30), 150 + rand(-12, 18), platformWidth(5, 8), PLAT_H),
        };
    }

    private double rand(double min, double max) {
        return min + Math.random() * (max - min);
    }

    private double platformWidth(int minTiles, int maxTiles) {
        int bonus = stageDefinition.getDifficulty() >= 6 ? 1 : 0;
        if (stageDefinition.getType() == StageType.PLATFORM) bonus += 1;
        if (stageDefinition.getType() == StageType.EXPLORATION) maxTiles += 1;

        int min = Math.max(2, minTiles);
        int max = Math.max(min, maxTiles + bonus);
        int tiles = min + (int) (Math.random() * (max - min + 1));
        return tiles * 24.0;
    }

    private Rectangle2D[] createCoverWalls() {
        double heightJitter = Math.random() * 18.0;
        return new Rectangle2D[] {
            new Rectangle2D(315 + rand(-35, 38), GROUND_Y - 108 - heightJitter, 72 + rand(0, 50), 108 + heightJitter),
            new Rectangle2D(660 + rand(-42, 42), GROUND_Y - 78, 88 + rand(0, 58), 78),
            new Rectangle2D(900 + rand(-38, 45), GROUND_Y - 128, 68 + rand(0, 54), 128),
            anchoredCover(9, 0.66, 64 + rand(0, 42), 58 + rand(0, 28)),
            new Rectangle2D(1250 + rand(-35, 42), GROUND_Y - 96, 112 + rand(0, 58), 96),
            new Rectangle2D(1460 + rand(-30, 34), GROUND_Y - 112, 84 + rand(0, 50), 112)
        };
    }

    private Rectangle2D anchoredCover(int platformIndex, double ratio, double width, double height) {
        Rectangle2D p = platforms[Math.max(0, Math.min(platformIndex, platforms.length - 1))];
        double x = p.getMinX() + p.getWidth() * ratio - width / 2.0;
        x = Math.max(p.getMinX(), Math.min(x, p.getMaxX() - width));
        return new Rectangle2D(x, p.getMinY() - height, width, height);
    }

    private Rectangle2D[] mergeBlockers(Rectangle2D[] first, Rectangle2D[] second) {
        Rectangle2D[] merged = new Rectangle2D[first.length + second.length];
        System.arraycopy(first, 0, merged, 0, first.length);
        System.arraycopy(second, 0, merged, first.length, second.length);
        return merged;
    }

    private void updateCamera() {
        double target = player.getX() + player.getWidth() / 2.0 - Config.WINDOW_WIDTH / 2.0;
        cameraX = Math.max(0, Math.min(target, WORLD_WIDTH - Config.WINDOW_WIDTH));
    }

    /**
     * 死亡重生時回滾到剛進入 Level2 的狀態。
     */
    private void rollbackToInitialSnapshot() {
        player.resetForCheckpoint(initialSnapshot.getPlayerX(),
                                  initialSnapshot.getPlayerY(),
                                  initialSnapshot.getPlayerHp(),
                                  initialSnapshot.getPlayerMana());
        initialSnapshot.restoreInventory(inventory);

        EnemySnapshot[] snapshots = initialSnapshot.getEnemySnapshots();
        for (int i = 0; i < snapshots.length; i++) {
            enemies[i] = snapshots[i].createEnemy(player);
            enemyDropsHandled[i] = false;
        }

        pickupItems.clear();
        pickupItems.addAll(initialSnapshot.createPickupItems());
        goldPickups.clear();
        goldPickups.addAll(initialSnapshot.createGoldPickups());
        bombs.clear();
        healthItem = initialSnapshot.hasHealthItem()
            ? new Rectangle2D(initialSnapshot.getHealthItemX(),
                              initialSnapshot.getHealthItemY(),
                              initialSnapshot.getHealthItemWidth(),
                              initialSnapshot.getHealthItemHeight())
            : null;
    }

    /**
     * 解析玩家與地板、平台的碰撞。
     * 找到第一個碰撞面即停止，避免多平台邊緣衝突。
     */
    private void resolvePlayerPlatformCollisions() {
        boolean groundedOnSolid = false;
        for (Rectangle2D cover : covers) {
            int result = Collision.resolveSolid(player, cover);
            if (result == -1) {
                groundedOnSolid = true;
            }
        }
        if (groundedOnSolid) {
            player.setOnGround(true);
            return;
        }

        // 先檢查地板
        if (Collision.checkPlatform(player, ground)) {
            player.setY(ground.getMinY() - player.getHeight());
            player.setOnGround(true);
            return;
        }

        // 再逐一檢查平台
        if (playerPlatformDropTimer <= 0) {
            for (Rectangle2D platform : platforms) {
                if (Collision.checkPlatform(player, platform)) {
                    player.setY(platform.getMinY() - player.getHeight());
                    player.setOnGround(true);
                    return;
                }
            }
        }

        // 沒踩到任何表面：玩家在空中
        player.setOnGround(false);
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

    /**
     * 解析所有存活敵人與地板、平台的碰撞。
     * 每個敵人獨立解析，互不干擾。
     */
    private void resolveEnemyPlatformCollisions() {
        for (Enemy enemy : enemies) {
            if (!enemy.isAlive()) continue;

            boolean groundedOnSolid = false;
            for (Rectangle2D cover : covers) {
                int result = Collision.resolveSolid(enemy, cover);
                if (result == -1) {
                    groundedOnSolid = true;
                }
            }
            if (groundedOnSolid) {
                enemy.setOnGround(true);
                continue;
            }

            // 先檢查地板
            if (Collision.checkPlatform(enemy, ground)) {
                enemy.setY(ground.getMinY() - enemy.getHeight());
                enemy.setOnGround(true);
                continue;   // 此敵人已解析，處理下一個
            }

            // 再逐一檢查平台
            boolean landed = false;
            if (!enemy.shouldDropFromPlatform()) {
                for (Rectangle2D platform : platforms) {
                    if (Collision.checkPlatform(enemy, platform)) {
                        enemy.setY(platform.getMinY() - enemy.getHeight());
                        enemy.setOnGround(true);
                        landed = true;
                        break;  // 每個敵人每幀只解析一個碰撞面
                    }
                }
            }

            // 沒踩到任何表面
            if (!landed) enemy.setOnGround(false);
        }
    }

    /**
     * 檢查玩家的攻擊判定框是否命中存活的敵人。
     * 命中後敵人扣 ATTACK_DAMAGE 血；同幀可同時命中多個敵人。
     */
    private void checkPlayerAttackVsEnemies() {
        destroyEnemyProjectilesInAttackRange();

        // canHit()：本次揮擊尚未命中過任何目標，確保每次 J 只造成一次傷害
        if (!player.canHit()) return;

        Rectangle2D atkBox = player.getAttackBox();
        if (atkBox == null) return;

        for (Enemy enemy : enemies) {
            if (!enemy.isAlive()) continue;
            if (Collision.checkAABB(atkBox, enemy.getHitbox()) && player.isAttackHitting(enemy.getHitbox())) {
                enemy.takeDamage(Player.ATTACK_DAMAGE);
                // 命中第一個敵人後標記，本次揮擊不再傷害其他目標
                // 若需「貫穿」可移除 markHit() 改為多目標命中
                player.markHit();
                break;
            }
        }
    }

    private void destroyEnemyProjectilesInAttackRange() {
        Rectangle2D atkBox = player.getAttackBox();
        if (atkBox == null) return;

        for (Enemy enemy : enemies) {
            if (!(enemy instanceof RangedEnemy ranged)) continue;
            for (Fireball projectile : ranged.getProjectiles()) {
                if (!projectile.isAlive()) continue;
                if (Collision.checkAABB(atkBox, projectile.getHitbox())
                    && player.isAttackHitting(projectile.getHitbox())) {
                    projectile.destroy();
                }
            }
        }
    }

    /**
     * 檢查玩家火球是否命中敵人、地板或平台。
     * 火球命中第一個敵人後造成傷害並消失；撞到地形也會消失。
     */
    private void checkPlayerFireballs() {
        for (Fireball fireball : player.getFireballs()) {
            if (!fireball.isAlive()) continue;

            boolean consumed = false;
            for (Enemy enemy : enemies) {
                if (!enemy.isAlive()) continue;
                if (Collision.checkAABB(fireball.getHitbox(), enemy.getHitbox())) {
                    enemy.takeDamage(fireball.getDamage());
                    fireball.destroy();
                    consumed = true;
                    break;
                }
            }

            if (!consumed) {
                destroyFireballOnWall(fireball);
            }
        }
    }

    /**
     * 火球撞到地板或任一平台時消失。
     *
     * @param fireball 要檢查的火球
     */
    private void destroyFireballOnWall(Fireball fireball) {
        if (Collision.checkAABB(fireball.getHitbox(), ground)) {
            fireball.destroy();
            return;
        }

        for (Rectangle2D platform : platforms) {
            if (Collision.checkAABB(fireball.getHitbox(), platform)) {
                fireball.destroy();
                return;
            }
        }

        for (Rectangle2D cover : covers) {
            if (Collision.checkAABB(fireball.getHitbox(), cover)) {
                fireball.destroy();
                return;
            }
        }
    }

    /**
     * 檢查存活敵人與玩家的碰撞框重疊，嘗試對玩家施加接觸傷害。
     * 每個敵人各自維護傷害冷卻，互不影響。
     * 玩家側由 Player.takeDamage() 的無敵機制防止重複扣血。
     */
    private void checkEnemyContactVsPlayer() {
        for (Enemy enemy : enemies) {
            if (!enemy.isAlive()) continue;
            if (Collision.checkAABB(enemy.getHitbox(), player.getHitbox())) {
                // 嘗試接觸傷害（冷卻中則無效果）
                enemy.tryDamagePlayer(player);
            }
        }
    }

    /**
     * 普通敵人死亡時依機率產生道具掉落。
     */
    private void checkEnemyDrops() {
        for (int i = 0; i < enemies.length; i++) {
            Enemy enemy = enemies[i];
            if (enemyDropsHandled[i] || enemy.isAlive()) continue;

            enemyDropsHandled[i] = true;
            addGold(random.nextInt(10) + 1,
                enemy.getX(), enemy.getY() + enemy.getHeight() - GoldPickup.SIZE);
            PickupType drop = rollEnemyDrop();
            if (drop != null) {
                addPickup(drop, enemy.getX(), enemy.getY() + enemy.getHeight() - PickupItem.SIZE);
            }
        }
    }

    /**
     * 普通敵人掉落規則：小藥水較常見，卷軸低機率。
     */
    private PickupType rollEnemyDrop() {
        double value = random.nextDouble();
        double bonus = stageDefinition.getEnemyDropBonus();
        if (value < 0.30 + bonus) return PickupType.SMALL_POTION;
        if (value < 0.38 + bonus) return PickupType.FIRE_SCROLL;
        if (value < 0.43 + bonus) return PickupType.ICE_SCROLL;
        if (value < 0.48 + bonus) return PickupType.BOMB;
        return null;
    }

    private void addPickup(PickupType type, double x, double y) {
        addPickup(type, x, y, 1);
    }

    private void addPickup(PickupType type, double x, double y, int quantity) {
        pickupItems.add(itemSpawnManager.spawn(type, x, y, quantity, pickupItems));
    }

    private void addGold(int amount, double x, double y) {
        goldPickups.add(goldSpawnManager.spawn(amount, x, y, pickupItems, goldPickups));
    }

    private void addExplorationSupplies() {
        double[][] supplyPoints = {
            pickupPoint(0, 0.72),
            pickupPoint(3, 0.45),
            pickupPoint(5, 0.35),
            pickupPoint(7, 0.65),
            pickupPoint(9, 0.48),
            pickupPoint(10, 0.55),
            pickupPoint(11, 0.58)
        };
        PickupType[] supplyTypes = {
            PickupType.SMALL_POTION,
            PickupType.BOMB,
            PickupType.FIRE_SCROLL,
            PickupType.ICE_SCROLL,
            PickupType.SMALL_POTION,
            PickupType.BOMB,
            PickupType.FIRE_SCROLL
        };

        int count = Math.min(stageDefinition.getExplorationSupplyCount() + 2, supplyPoints.length);
        for (int i = 0; i < count; i++) {
            if (i % 2 == 0 || stageDefinition.getType() == StageType.EXPLORATION) {
                addPickup(supplyTypes[i], supplyPoints[i][0], supplyPoints[i][1]);
            } else {
                addGold(3 + random.nextInt(8), supplyPoints[i][0], supplyPoints[i][1]);
            }
        }
    }

    private double[] pickupPoint(int platformIndex, double ratio) {
        Rectangle2D p = platforms[Math.max(0, Math.min(platformIndex, platforms.length - 1))];
        double x = p.getMinX() + Math.max(4.0, (p.getWidth() - PickupItem.SIZE) * ratio);
        x = Math.min(x, p.getMaxX() - PickupItem.SIZE - 4.0);
        return new double[] { x, p.getMinY() - PickupItem.SIZE };
    }

    private void checkPlayerIceProjectiles() {
        for (IceProjectile projectile : player.getIceProjectiles()) {
            if (!projectile.isAlive()) continue;
            for (Enemy enemy : enemies) {
                if (!enemy.isAlive()) continue;
                if (Collision.checkAABB(projectile.getHitbox(), enemy.getHitbox())) {
                    enemy.applySlow(IceProjectile.SLOW_DURATION, IceProjectile.SLOW_MULTIPLIER);
                    projectile.destroy();
                    break;
                }
            }
            if (projectile.isAlive()) destroyFireballOnWall(projectile);
        }
    }

    private void checkRangedEnemyProjectiles() {
        for (Enemy enemy : enemies) {
            if (!(enemy instanceof RangedEnemy ranged)) continue;
            for (Fireball projectile : ranged.getProjectiles()) {
                if (!projectile.isAlive()) continue;
                if (Collision.checkAABB(projectile.getHitbox(), player.getHitbox())) {
                    player.takeDamage(projectile.getDamage());
                    projectile.destroy();
                    continue;
                }
                destroyFireballOnWall(projectile);
            }
        }
    }

    /**
     * 玩家碰到地板道具時撿進背包。
     */
    private void checkPickupItems() {
        for (PickupItem item : pickupItems) {
            if (item.isPickedUp()) continue;
            if (Collision.checkAABB(player.getHitbox(), item.getHitbox())) {
                if (inventory.add(item.getType(), item.getQuantity())) {
                    item.markPickedUp();
                }
            }
        }
        pickupItems.removeIf(PickupItem::isPickedUp);
    }

    private void checkGoldPickups() {
        for (GoldPickup gold : goldPickups) {
            if (gold.isPickedUp()) continue;
            if (Collision.checkAABB(player.getHitbox(), gold.getHitbox())) {
                Main.addGold(gold.getAmount());
                gold.markPickedUp();
            }
        }
        goldPickups.removeIf(GoldPickup::isPickedUp);
    }

    private void updateBombs(double dt) {
        for (BombEntity bomb : bombs) {
            bomb.update(dt);
            if (bomb.shouldApplyDamage()) {
                applyBombDamage(bomb);
                bomb.markDamageApplied();
            }
        }
        bombs.removeIf(bomb -> !bomb.isAlive());
    }

    private void applyBombDamage(BombEntity bomb) {
        for (Enemy enemy : enemies) {
            if (!enemy.isAlive()) continue;
            double dx = enemy.getX() + enemy.getWidth() / 2.0 - bomb.getX();
            double dy = enemy.getY() + enemy.getHeight() / 2.0 - bomb.getY();
            if (Math.sqrt(dx * dx + dy * dy) <= BombEntity.RADIUS) {
                enemy.takeDamage(BombEntity.DAMAGE);
            }
        }
    }

    private boolean trySwapGroundItem(int slotIndex) {
        PickupItem groundItem = findBlockedPickupUnderPlayer();
        if (groundItem == null) return false;

        InventorySlot dropped = inventory.replaceSlot(slotIndex,
            groundItem.getType(), groundItem.getQuantity());
        if (dropped == null) return false;

        groundItem.markPickedUp();
        addPickup(dropped.getType(),
            player.getX(),
            player.getY() + player.getHeight() - PickupItem.SIZE,
            dropped.getCount());
        pickupItems.removeIf(PickupItem::isPickedUp);
        return true;
    }

    private PickupItem findBlockedPickupUnderPlayer() {
        if (!inventory.isFull()) return null;
        for (PickupItem item : pickupItems) {
            if (item.isPickedUp()) continue;
            if (inventory.contains(item.getType())) continue;
            if (Collision.checkAABB(player.getHitbox(), item.getHitbox())) {
                return item;
            }
        }
        return null;
    }

    private int keyToSlotIndex(KeyCode key) {
        return switch (key) {
            case U -> 0;
            case I -> 1;
            case O -> 2;
            default -> -1;
        };
    }

    /**
     * 檢查玩家是否碰到加血道具。
     * 碰到後立即回復 ITEM_HEAL 點血量（上限為最大血量），並移除道具。
     */
    private void checkHealthItem() {
        // 已被拾取則跳過
        if (healthItem == null) return;

        if (Collision.checkAABB(player.getHitbox(), healthItem)) {
            // 回復血量，setHp 內部自動限制不超過 maxHp
            player.setHp(player.getHp() + ITEM_HEAL);
            healthItem = null;   // 標記為已拾取
        }
    }

    /**
     * 檢查玩家是否碰到終點門。
     * 碰到後停止 AnimationTimer 並切換至 Boss 關。
     * transitioning 旗標防止重複觸發。
     */
    private void checkGoalDoor() {
        if (transitioning) return;

        if (Collision.checkAABB(player.getHitbox(), goalDoor)) {
            transitioning = true;
            Main.setPersistedPlayerState(player.getHp(), player.getMana());   // 帶入狀態到 Boss 關
            this.stop();
            Main.completeNormalStage();
        }
    }

    // ── render ────────────────────────────────────────────────────────────────

    /**
     * 每幀渲染，繪製順序：
     * <ol>
     *   <li>背景（深藍色，與第一關深灰色作區別）</li>
     *   <li>地板</li>
     *   <li>所有平台</li>
     *   <li>加血道具（已拾取則跳過）</li>
     *   <li>終點門</li>
     *   <li>所有存活的敵人</li>
     *   <li>玩家</li>
     *   <li>HUD（左上角血量條）</li>
     * </ol>
     *
     * @param gc 畫布繪圖上下文
     */
    private void render(GraphicsContext gc) {
        // 1. 背景（深海藍，TODO: 換成視差捲動背景圖片）
        gc.setFill(Color.web("#1a1a3e"));
        gc.fillRect(0, 0, Config.WINDOW_WIDTH, Config.WINDOW_HEIGHT);

        // 2. 地板（TODO: 換成地面圖塊）
        gc.save();
        gc.translate(-cameraX, 0);
        gc.setFill(Color.web("#5a3a1a"));
        gc.fillRect(cameraX - CULL_MARGIN, ground.getMinY(),
                    Config.WINDOW_WIDTH + CULL_MARGIN * 2, ground.getHeight());

        // 3. 所有平台（TODO: 換成 Tileset 圖塊）
        drawPlatforms(gc);
        drawCovers(gc);

        // 4. 加血道具（TODO: 換成精靈圖）
        drawHealthItem(gc);

        // 5. 可撿取地板道具
        for (PickupItem item : pickupItems) {
            if (!isVisible(item.getHitbox())) continue;
            item.draw(gc);
        }

        for (GoldPickup gold : goldPickups) {
            if (!isVisible(gold.getHitbox())) continue;
            gold.draw(gc);
        }

        for (BombEntity bomb : bombs) {
            bomb.draw(gc);
        }

        // 6. 終點門（TODO: 換成門的精靈圖與開門動畫）
        drawGoalDoor(gc);

        // 7. 所有存活的敵人
        for (Enemy enemy : enemies) {
            if (!isVisible(enemy.getHitbox())) continue;
            enemy.draw(gc);
        }

        // 8. 玩家（最後繪製，顯示在最上層）
        player.draw(gc);
        gc.restore();

        // 9. HUD：左上角血量條
        drawHUD(gc);

        // 10. 玩家死亡後疊加 GAME OVER 畫面
        if (!player.isAlive()) {
            drawGameOverOverlay(gc);
        }
    }

    /**
     * 繪製半透明黑色遮罩 + 紅色大字「GAME OVER」+ R 鍵重啟提示。
     *
     * @param gc 畫布繪圖上下文
     */
    private void drawGameOverOverlay(GraphicsContext gc) {
        gc.save();
        gc.setGlobalAlpha(0.65);
        gc.setFill(Color.BLACK);
        gc.fillRect(0, 0, Config.WINDOW_WIDTH, Config.WINDOW_HEIGHT);
        gc.restore();

        gc.setFill(Color.RED);
        gc.setFont(Font.font(60));
        gc.fillText("GAME OVER",
                    Config.WINDOW_WIDTH / 2.0 - 173,
                    Config.WINDOW_HEIGHT / 2.0 - 20);

        gc.setFill(Color.WHITE);
        gc.setFont(Font.font(20));
        gc.fillText("按 R 重新開始",
                    Config.WINDOW_WIDTH / 2.0 - 65,
                    Config.WINDOW_HEIGHT / 2.0 + 40);
    }

    /**
     * 繪製所有平台（藍色石磚調，與第一關的綠色草地作區別）。
     * TODO: 換成 Tileset 圖塊後移除此方法。
     *
     * @param gc 畫布繪圖上下文
     */
    private void drawPlatforms(GraphicsContext gc) {
        for (Rectangle2D p : platforms) {
            if (!isVisible(p)) continue;
            // 平台本體（石磚藍）
            gc.setFill(Color.web("#1565c0"));
            gc.fillRect(p.getMinX(), p.getMinY(), p.getWidth(), p.getHeight());

            // 平台上緣深色線條，增加立體感
            gc.setFill(Color.web("#0d47a1"));
            gc.fillRect(p.getMinX(), p.getMinY(), p.getWidth(), 3);
        }
    }

    /**
     * 繪製加血道具（綠色十字符號）。
     * 已拾取（healthItem == null）時不繪製。
     * TODO: 換成旋轉發光的道具精靈圖。
     *
     * @param gc 畫布繪圖上下文
     */
    private void drawHealthItem(GraphicsContext gc) {
        if (healthItem == null) return;
        if (!isVisible(healthItem)) return;

        double ix = healthItem.getMinX();
        double iy = healthItem.getMinY();
        double s  = ITEM_SIZE;

        // 外框（白色）增加辨識度
        gc.setFill(Color.web("#ffffff"));
        gc.fillRect(ix - 1, iy - 1, s + 2, s + 2);

        // 十字背景（深綠底）
        gc.setFill(Color.web("#1b5e20"));
        gc.fillRect(ix, iy, s, s);

        // 十字橫槓
        gc.setFill(Color.LIMEGREEN);
        gc.fillRect(ix, iy + s / 2 - 3, s, 6);

        // 十字直槓
        gc.fillRect(ix + s / 2 - 3, iy, 6, s);
    }

    /**
     * 繪製終點門（金色框 + 半透明填充），不顯示下一關提示。
     * TODO: 換成有骷髏頭圖示的威脅感門框精靈圖。
     *
     * @param gc 畫布繪圖上下文
     */
    private void drawGoalDoor(GraphicsContext gc) {
        // 半透明填充
        gc.save();
        gc.setGlobalAlpha(0.35);
        gc.setFill(Color.GOLD);
        gc.fillRect(goalDoor.getMinX(), goalDoor.getMinY(),
                    goalDoor.getWidth(), goalDoor.getHeight());
        gc.restore();

        // 金色外框
        gc.setStroke(Color.GOLD);
        gc.setLineWidth(3);
        gc.strokeRect(goalDoor.getMinX(), goalDoor.getMinY(),
                      goalDoor.getWidth(), goalDoor.getHeight());

    }

    private void drawCovers(GraphicsContext gc) {
        for (Rectangle2D cover : covers) {
            if (!isVisible(cover)) continue;
            gc.setFill(Color.web("#4e4a46"));
            gc.fillRect(cover.getMinX(), cover.getMinY(), cover.getWidth(), cover.getHeight());
            gc.setFill(Color.web("#74706a"));
            gc.fillRect(cover.getMinX(), cover.getMinY(), cover.getWidth(), 5);
        }
    }

    private boolean isVisible(Rectangle2D rect) {
        return rect.getMaxX() >= cameraX - CULL_MARGIN
            && rect.getMinX() <= cameraX + Config.WINDOW_WIDTH + CULL_MARGIN;
    }

    /**
     * 繪製左上角 HUD：血量條 + 數值文字 + 關卡標題。
     *
     * @param gc 畫布繪圖上下文
     */
    private void drawHUD(GraphicsContext gc) {
        HudRenderer.drawPlayerStatus(gc, player, stageDefinition.getHudTitle());
        HudRenderer.drawGold(gc, Main.getGold());
        HudRenderer.drawInventorySlots(gc, inventory, selectedInventorySlot);
        PickupItem blockedItem = findBlockedPickupUnderPlayer();
        if (blockedItem != null) {
            HudRenderer.drawBackpackFullPrompt(gc, inventory, blockedItem);
        }
        if (inventoryOpen) {
            HudRenderer.drawInventoryOverlay(gc, inventory, selectedInventorySlot);
        }
    }
}
