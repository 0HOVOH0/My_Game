package ncu.cs2.my_game.scene;

import javafx.animation.AnimationTimer;
import javafx.geometry.Point2D;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import ncu.cs2.my_game.Config;
import ncu.cs2.my_game.Main;
import ncu.cs2.my_game.economy.GoldPickup;
import ncu.cs2.my_game.economy.GoldSpawnManager;
import ncu.cs2.my_game.effect.EffectManager;
import ncu.cs2.my_game.entity.BombEntity;
import ncu.cs2.my_game.entity.Enemy;
import ncu.cs2.my_game.entity.Fireball;
import ncu.cs2.my_game.entity.IceProjectile;
import ncu.cs2.my_game.entity.Player;
import ncu.cs2.my_game.entity.RangedEnemy;
import ncu.cs2.my_game.item.Inventory;
import ncu.cs2.my_game.item.InventorySlot;
import ncu.cs2.my_game.item.ItemSpawnResolver;
import ncu.cs2.my_game.item.ItemSpawnManager;
import ncu.cs2.my_game.item.PickupItem;
import ncu.cs2.my_game.item.PickupType;
import ncu.cs2.my_game.item.PotionInventory;
import ncu.cs2.my_game.item.UseContext;
import ncu.cs2.my_game.map.DungeonMapRenderer;
import ncu.cs2.my_game.map.MapValidationResult;
import ncu.cs2.my_game.map.PlatformValidationResult;
import ncu.cs2.my_game.map.TileMap;
import ncu.cs2.my_game.map.TileType;
import ncu.cs2.my_game.physics.Collision;
import ncu.cs2.my_game.stage.StageDefinition;
import ncu.cs2.my_game.stage.StageType;
import ncu.cs2.my_game.state.EnemySnapshot;
import ncu.cs2.my_game.state.StageSnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
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

    /** 普通關世界寬度，Canvas 只顯示其中一段。 */
    private static final double WORLD_WIDTH = TileMap.TILE_SIZE * 96.0;

    private static final double CULL_MARGIN = 96.0;

    /** 平台厚度（像素） */
    private static final double PLAT_H = 16.0;

    /** 終點門寬度 */
    private static final double GOAL_W = 50.0;

    /** 終點門高度 */
    private static final double GOAL_H = 150.0;

    /** 舊版補血生成點也沿用地板道具尺寸與圖示比例。 */
    private static final double ITEM_SIZE = PickupItem.SIZE;

    /** 加血道具回復的血量 */
    private static final int ITEM_HEAL = 30;

    /** 生成平台/牆壁時保留的最小通行寬度，避免玩家或小兵被夾在窄縫。 */
    private static final double MIN_PASSAGE_WIDTH = Config.PLAYER_WIDTH + 10.0;

    /** 小兵出生前必須具備的有效巡邏寬度。 */
    private static final double MIN_ENEMY_PATROL_WIDTH = Enemy.ENEMY_W + Config.PLAYER_WIDTH + 48.0;

    /** 同一平台多隻小兵出生時的最小水平間距。 */
    private static final double ENEMY_SPAWN_SPACING = Enemy.ENEMY_W + 18.0;

    // ── 欄位 ─────────────────────────────────────────────────────────────────

    /** 主視窗參考 */
    private final Stage stage;

    /** 繪圖用 GraphicsContext */
    private final GraphicsContext gc;

    /** 玩家實體 */
    private final Player player;
    private final EffectManager effectManager;
    private final GameFlowController flowController;

    /** 本關地下城 tile map，負責地形資料與視覺呈現。 */
    private final TileMap tileMap;

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

    /** 跨關卡藥水欄，與三格一般道具分開保存。 */
    private final PotionInventory potionInventory;

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
    private boolean portalEnterRequested = false;

    /** 玩家死亡後是否按下了 R 鍵，觸發重啟 Level2 */
    private boolean rKeyPressed = false;

    /** 目前鏡頭左側世界座標。 */
    private double cameraX = 0;

    private double playerPlatformDropTimer = 0;
    private double fps = 0;
    private String pickupNotice = "";
    private double pickupNoticeTimer = 0;

    // ─────────────────────────────────────────────────────────────────────────

    /**
     * 建構子：初始化所有場景物件並立即啟動 AnimationTimer。
     *
     * @param stage 主視窗，用於切換 JavaFX Scene
     */
    public Level2Scene(Stage stage, StageDefinition stageDefinition) {
        this.stage = stage;
        this.stageDefinition = stageDefinition;
        random = new Random();

        // ── 建立 Canvas 與 JavaFX Scene ──────────────────────────────────────
        Canvas canvas = new Canvas(Config.WINDOW_WIDTH, Config.WINDOW_HEIGHT);
        gc = canvas.getGraphicsContext2D();

        Scene javafxScene = CanvasSceneSupport.createScaledCanvasScene(stage, canvas);
        Fireball.setWorldBounds(WORLD_WIDTH, Config.WINDOW_HEIGHT);
        tileMap = Main.pickNormalStageMap();

        // ── 初始化玩家（帶入 Level1 結束時的血量） ────────────────────────────
        player = new Player(tileMap.getSpawnX(), tileMap.getSpawnY());
        Main.applyPlayerProgress(player);
        player.setHp(Main.getPersistedHp());
        player.setMana(Main.getPersistedMana());
        player.startInvincibility(Config.PLAYER_SPAWN_PROTECTION_SECONDS);
        effectManager = new EffectManager();
        // R 鍵回滾至初始快照（同一張地圖），Shift+R 才回到 Level1 重新開始
        flowController = new GameFlowController(
            this::rollbackToInitialSnapshot,
            () -> { cleanup(); Main.startLevel1(); },
            () -> { cleanup(); Main.exitToMainMenu(); },
            () -> { cleanup(); javafx.application.Platform.exit(); }
        );
        Main.registerActiveScene("LEVEL_2", 2, GameState.PLAYING, this::cleanup);

        // ── 地板 ──────────────────────────────────────────────────────────────
        double mapGroundY = (tileMap.getHeightTiles() - 1) * TileMap.TILE_SIZE;
        ground = new Rectangle2D(0, mapGroundY, WORLD_WIDTH, TileMap.TILE_SIZE);

        platforms = createProceduralPlatforms();
        covers = createCoverWalls();
        visionBlockers = mergeBlockers(platforms, covers);
        navigationSurfaces = mergeBlockers(new Rectangle2D[] { ground }, visionBlockers);

        // ── 終點門 ────────────────────────────────────────────────────────────
        goalDoor = tileMap.getExitBounds();

        // ── 敵人（每個敵人的巡邏範圍對應所在平台邊界） ───────────────────────
        // 巡邏右邊界 = 平台右端 - 敵人寬度，確保不會走出平台
        List<EnemySnapshot> enemySnapshots = createEnemySnapshots();
        enemies = createEnemies(enemySnapshots);

        pickupItems = new ArrayList<>();
        goldPickups = new ArrayList<>();
        bombs = new ArrayList<>();
        inventory = Main.getInventory();
        potionInventory = Main.getPotionInventory();
        enemyDropsHandled = new boolean[enemies.length];
        itemSpawnManager = new ItemSpawnManager(ground, platforms, covers);
        goldSpawnManager = new GoldSpawnManager(ground, platforms, covers);
        itemSpawnManager.setForbiddenZones(createHazardForbiddenZones());
        goldSpawnManager.setForbiddenZones(createHazardForbiddenZones());
        inventoryOpen = false;
        selectedInventorySlot = 0;

        // 示範地板道具：玩家碰到後進背包，按 U/I/O 使用
        double[] startPotion = randomPickupPoint(0, Math.min(3, platforms.length - 1));
        double[] fireScroll = randomPickupPoint(2, Math.min(7, platforms.length - 1));
        double[] bomb = randomPickupPoint(5, platforms.length - 1);
        addPickup(PickupType.SMALL_POTION, startPotion[0], startPotion[1]);
        addPickup(PickupType.FIRE_SCROLL, fireScroll[0], fireScroll[1]);
        addPickup(PickupType.BOMB, bomb[0], bomb[1]);
        addExplorationSupplies();

        // ── 加血道具（放在 P3 中央偏右，玩家進到 P3 後容易看到）───────────────
        // P3 頂面 y=355，道具底部對齊平台：item.y = 355 - ITEM_SIZE = 335
        double[] healthPoint = safeLegacyHealthPoint(randomPickupPoint(1, Math.min(6, platforms.length - 1)));
        healthItem = new Rectangle2D(healthPoint[0], healthPoint[1], ITEM_SIZE, ITEM_SIZE);

        initialSnapshot = new StageSnapshot(
            player.getX(), player.getY(), player.getHp(), player.getMana(),
            inventory, pickupItems, goldPickups, enemySnapshots,
            true, healthItem.getMinX(), healthItem.getMinY(),
            healthItem.getWidth(), healthItem.getHeight()
        );

        // ── 綁定鍵盤事件 ──────────────────────────────────────────────────────
        javafxScene.setOnKeyPressed(e -> {
            if (SceneTransitionManager.isTransitioning()) return;
            if (Main.hasPendingTalentChoice()) {
                if (handleTalentChoiceKey(e.getCode())) e.consume();
                return;
            }
            // 死亡狀態：只允許 R 鍵（重生），封鎖所有其他按鍵
            if (!player.isAlive()) {
                if (e.getCode() == KeyCode.R) {
                    rKeyPressed = true;
                    e.consume();
                }
                return;
            }
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
            handleInventoryKey(e.getCode());
            handlePotionKey(e.getCode());
            if (e.getCode() == KeyCode.B) {
                inventoryOpen = !inventoryOpen;
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
        SceneTransitionManager.tick(dt);
        fps = dt > 0 ? 1.0 / dt : 0;

        if (!flowController.isPaused() && !Main.hasPendingTalentChoice()) {
            update(dt);
        }
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
        if (player.consumeAttackStarted()) {
            effectManager.playSlash(player);
        }
        effectManager.update(dt);
        player.update(dt);
        updatePlayerPlatformDrop(dt);

        // 2. 敵人物理與視線記憶；死亡遠程兵仍會更新殘留子彈，避免停在空中
        for (Enemy enemy : enemies) {
            if (enemy.isAlive()) {
                enemy.setNavigationSurfaces(navigationSurfaces);
                enemy.setMovementBlockers(covers);
                enemy.updateAwareness(player, visionBlockers);
            }
            enemy.update(dt);
        }

        // 3. 玩家平台碰撞解析
        resolvePlayerPlatformCollisions();

        // 4. 每個存活敵人的平台碰撞解析
        resolveEnemyPlatformCollisions(dt);

        // 4.5 若玩家放開 S，且頭頂空間足夠，恢復站立
        tryResolvePlayerStandUp();
        damagePlayerOnTraps();

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
        updateFallingDrops(dt);

        // 10. 地板道具拾取
        checkPickupItems();

        // 11. 金幣拾取
        checkGoldPickups();
        updateBombs(dt);

        // 12. 加血道具拾取
        checkHealthItem();

        // 13. 終點門判定
        checkGoalDoor();

        if (pickupNoticeTimer > 0) pickupNoticeTimer -= dt;
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

    private void handlePotionKey(KeyCode key) {
        int slotIndex = keyToPotionSlotIndex(key);
        if (slotIndex < 0) return;
        if (trySwapGroundPotion(slotIndex)) return;
        InventorySlot slot = potionInventory.getSlot(slotIndex);
        boolean used = potionInventory.useSlot(slotIndex, new UseContext(player, enemies, null, bombs));
        if (!used && slot != null && !slot.isEmpty() && player.getHp() >= player.getMaxHp()) {
            showPickupNotice("HP Full");
        }
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

        List<Rectangle2D> candidates = collectEnemySpawnSurfaces();
        Collections.shuffle(candidates, random);

        int enemyCount = switch (stageDefinition.getType()) {
            case ELITE -> 8;
            case COMBAT -> 7;
            case PLATFORM -> 6;
            case EXPLORATION -> 5;
            case SHOP, BOSS -> 6;
        };
        enemyCount += Math.min(3, stageDefinition.getDifficulty() / 4);

        int spawned = 0;
        for (int i = 0; i < candidates.size() && spawned < enemyCount; i++) {
            int groupSize = 1 + random.nextInt(3);
            groupSize = Math.min(groupSize, enemyCount - spawned);
            spawned += addEnemyGroupOnSurface(snapshots, candidates.get(i), groupSize, spawned,
                hpScale, damageScale, speedScale, projectileScale);
        }
        return snapshots;
    }

    private List<Rectangle2D> collectEnemySpawnSurfaces() {
        List<Rectangle2D> surfaces = new ArrayList<>();
        for (Rectangle2D zone : tileMap.getEnemyZones()) {
            addEnemySurfaceIfUsable(surfaces, zone);
        }
        addGroundEnemySurfaces(surfaces);

        if (surfaces.isEmpty()) {
            for (Rectangle2D platform : platforms) {
                if (isValidatedReachablePlatform(platform)) {
                    addEnemySurfaceIfUsable(surfaces, platform);
                }
            }
        }

        surfaces.sort(Comparator.comparingDouble(Rectangle2D::getMinX));
        return surfaces;
    }

    private void addGroundEnemySurfaces(List<Rectangle2D> surfaces) {
        for (Rectangle2D segment : groundPatrolSegments()) {
            if (segment.getWidth() >= MIN_ENEMY_PATROL_WIDTH
                    && random.nextDouble() < 0.38 + Math.min(0.16, stageDefinition.getDifficulty() * 0.015)) {
                addEnemySurfaceIfUsable(surfaces, segment);
            }
        }
    }

    private List<Rectangle2D> groundPatrolSegments() {
        List<Rectangle2D> segments = new ArrayList<>();
        double start = 0.0;
        List<Rectangle2D> blockers = new ArrayList<>();
        Collections.addAll(blockers, covers);
        blockers.sort(Comparator.comparingDouble(Rectangle2D::getMinX));
        for (Rectangle2D cover : blockers) {
            if (Math.abs(cover.getMaxY() - ground.getMinY()) > 1.5) continue;
            if (cover.getMinX() - start >= MIN_ENEMY_PATROL_WIDTH) {
                segments.add(new Rectangle2D(start, ground.getMinY(),
                    cover.getMinX() - start, ground.getHeight()));
            }
            start = Math.max(start, cover.getMaxX());
        }
        if (ground.getMaxX() - start >= MIN_ENEMY_PATROL_WIDTH) {
            segments.add(new Rectangle2D(start, ground.getMinY(),
                ground.getMaxX() - start, ground.getHeight()));
        }
        return segments;
    }

    private void addEnemySurfaceIfUsable(List<Rectangle2D> surfaces, Rectangle2D surface) {
        Rectangle2D patrolSegment = safeEnemyPatrolSegment(surface);
        if (patrolSegment == null || patrolSegment.getWidth() < MIN_ENEMY_PATROL_WIDTH) return;
        if (isNearSpawnOrExit(patrolSegment)) return;
        for (Rectangle2D existing : surfaces) {
            boolean sameHeight = Math.abs(existing.getMinY() - patrolSegment.getMinY()) < 1.5;
            boolean overlaps = existing.getMaxX() > patrolSegment.getMinX()
                && existing.getMinX() < patrolSegment.getMaxX();
            if (sameHeight && overlaps) return;
        }
        surfaces.add(patrolSegment);
    }

    private boolean isNearSpawnOrExit(Rectangle2D segment) {
        double centerX = segment.getMinX() + segment.getWidth() / 2.0;
        if (Math.abs(centerX - player.getX()) < TileMap.TILE_SIZE * 7) return true;
        return goalDoor != null && Math.abs(centerX - (goalDoor.getMinX() + goalDoor.getWidth() / 2.0))
            < TileMap.TILE_SIZE * 5;
    }

    private double rangedChance(int enemyIndex) {
        double chance = switch (stageDefinition.getType()) {
            case ELITE -> 0.42;
            case COMBAT -> 0.30;
            case PLATFORM -> 0.26;
            case EXPLORATION -> 0.22;
            case SHOP, BOSS -> 0.25;
        };
        if (enemyIndex == 0) chance *= 0.5;
        return chance;
    }

    private int addEnemyGroupOnSurface(List<EnemySnapshot> snapshots, Rectangle2D surface,
                                       int requestedCount, int spawnedSoFar,
                                       double hpScale, double damageScale,
                                       double speedScale, double projectileScale) {
        Rectangle2D segment = safeEnemyPatrolSegment(surface);
        if (segment == null || segment.getWidth() < MIN_ENEMY_PATROL_WIDTH) return 0;
        double patrolLeft = segment.getMinX();
        double patrolRight = Math.max(patrolLeft + Enemy.ENEMY_W + 8, segment.getMaxX());
        int capacity = Math.max(1, (int) Math.floor(
            (segment.getWidth() - Enemy.ENEMY_W) / ENEMY_SPAWN_SPACING) + 1);
        int count = Math.min(requestedCount, Math.min(3, capacity));
        double usableLeft = patrolLeft + 8.0;
        double usableRight = patrolRight - Enemy.ENEMY_W - 8.0;
        if (usableRight < usableLeft) return 0;

        int placed = 0;
        double lane = (usableRight - usableLeft) / Math.max(1, count);
        for (int i = 0; i < count; i++) {
            double base = count == 1
                ? (usableLeft + usableRight) / 2.0
                : usableLeft + lane * (i + 0.5);
            double jitter = Math.min(10.0, Math.max(0.0, lane * 0.22));
            double x = Math.max(usableLeft, Math.min(usableRight,
                base + (random.nextDouble() * 2.0 - 1.0) * jitter));
            if (overlapsExistingEnemySpawn(snapshots, x, segment.getMinY())) continue;
            boolean ranged = random.nextDouble() < rangedChance(spawnedSoFar + placed);
            snapshots.add(new EnemySnapshot(x, segment.getMinY() - Enemy.ENEMY_H,
                patrolLeft, patrolRight, ranged, hpScale, damageScale, speedScale, projectileScale));
            placed++;
        }
        return placed;
    }

    private boolean overlapsExistingEnemySpawn(List<EnemySnapshot> snapshots, double x, double platformY) {
        double y = platformY - Enemy.ENEMY_H;
        Rectangle2D next = new Rectangle2D(x, y, Enemy.ENEMY_W, Enemy.ENEMY_H);
        for (EnemySnapshot snapshot : snapshots) {
            Rectangle2D existing = new Rectangle2D(snapshot.getX(), snapshot.getY(),
                Enemy.ENEMY_W, Enemy.ENEMY_H);
            if (existing.intersects(next)) {
                return true;
            }
        }
        return false;
    }

    private Rectangle2D safeEnemyPatrolSegment(Rectangle2D platform) {
        List<double[]> segments = new ArrayList<>();
        segments.add(new double[] { platform.getMinX() + 8.0, platform.getMaxX() - 8.0 });

        for (Rectangle2D cover : covers) {
            boolean restsOnPlatform = Math.abs(cover.getMaxY() - platform.getMinY()) <= 1.5;
            boolean overlapsX = cover.getMaxX() > platform.getMinX() && cover.getMinX() < platform.getMaxX();
            if (!restsOnPlatform || !overlapsX) continue;

            double blockLeft = Math.max(platform.getMinX(), cover.getMinX() - 18.0);
            double blockRight = Math.min(platform.getMaxX(), cover.getMaxX() + 18.0);
            List<double[]> split = new ArrayList<>();
            for (double[] segment : segments) {
                if (blockRight <= segment[0] || blockLeft >= segment[1]) {
                    split.add(segment);
                    continue;
                }
                if (blockLeft - segment[0] >= Enemy.ENEMY_W + 24.0) {
                    split.add(new double[] { segment[0], blockLeft });
                }
                if (segment[1] - blockRight >= Enemy.ENEMY_W + 24.0) {
                    split.add(new double[] { blockRight, segment[1] });
                }
            }
            segments = split;
        }

        double[] best = null;
        for (double[] segment : segments) {
            double width = segment[1] - segment[0];
            if (width < Enemy.ENEMY_W + 24.0) continue;
            if (best == null || width > best[1] - best[0]) {
                best = segment;
            }
        }
        if (best == null) {
            return null;
        }
        return new Rectangle2D(best[0], platform.getMinY(), best[1] - best[0], platform.getHeight());
    }

    private Rectangle2D[] createProceduralPlatforms() {
        List<Rectangle2D> actualPlatforms = new ArrayList<>();
        for (int y = 0; y < tileMap.getHeightTiles(); y++) {
            int runStart = -1;
            for (int x = 0; x <= tileMap.getWidthTiles(); x++) {
                boolean standablePlatform = x < tileMap.getWidthTiles()
                    && tileMap.getTile(x, y).isStandable()
                    && !tileMap.getTile(x, y).isSolid();
                if (standablePlatform && runStart < 0) {
                    runStart = x;
                } else if (!standablePlatform && runStart >= 0) {
                    int length = x - runStart;
                    actualPlatforms.add(new Rectangle2D(runStart * TileMap.TILE_SIZE,
                        y * TileMap.TILE_SIZE, length * TileMap.TILE_SIZE, PLAT_H));
                    runStart = -1;
                }
            }
        }
        addWallTopSpawnSurfaces(actualPlatforms);
        return actualPlatforms.toArray(Rectangle2D[]::new);
    }

    private void addWallTopSpawnSurfaces(List<Rectangle2D> actualPlatforms) {
        for (int y = 2; y < tileMap.getHeightTiles() - 2; y++) {
            int runStart = -1;
            for (int x = 1; x <= tileMap.getWidthTiles() - 1; x++) {
                boolean clearWallTop = x < tileMap.getWidthTiles() - 1
                    && tileMap.getTile(x, y) == TileType.WALL
                    && tileMap.getTile(x, y - 1) == TileType.EMPTY
                    && tileMap.getTile(x, y - 2) == TileType.EMPTY;
                if (clearWallTop && runStart < 0) {
                    runStart = x;
                } else if (!clearWallTop && runStart >= 0) {
                    int length = x - runStart;
                    if (length >= 3) {
                        actualPlatforms.add(new Rectangle2D(runStart * TileMap.TILE_SIZE,
                            y * TileMap.TILE_SIZE, length * TileMap.TILE_SIZE, PLAT_H));
                    }
                    runStart = -1;
                }
            }
        }
    }

    /**
     * Keep combat and collectible placement off optional ledges that did not
     * pass the map generator's player-reachability graph.
     */
    private boolean isValidatedReachablePlatform(Rectangle2D platform) {
        PlatformValidationResult validation = tileMap.getValidationResult();
        if (validation == null) return true;
        for (Rectangle2D unreachable : validation.unreachablePlatforms()) {
            boolean sameHeight = Math.abs(unreachable.getMinY() - platform.getMinY()) < 1.5;
            boolean overlaps = unreachable.getMaxX() > platform.getMinX()
                && unreachable.getMinX() < platform.getMaxX();
            if (sameHeight && overlaps) {
                return false;
            }
        }
        return true;
    }

    private double rand(double min, double max) {
        return min + random.nextDouble() * (max - min);
    }

    private Rectangle2D randomPlatform(double minX, double maxX, double minY, double maxY,
                                       int minTiles, int maxTiles) {
        double width = platformWidth(minTiles, maxTiles);
        double x = rand(minX, Math.max(minX, maxX - width));
        double y = rand(minY, maxY);
        return new Rectangle2D(x, y, width, PLAT_H);
    }

    private void addRandomPlatform(List<Rectangle2D> generated, double minX, double maxX,
                                   double minY, double maxY, int minTiles, int maxTiles) {
        Rectangle2D fallback = null;
        for (int attempt = 0; attempt < 12; attempt++) {
            Rectangle2D candidate = randomPlatform(minX, maxX, minY, maxY, minTiles, maxTiles);
            if (fallback == null) fallback = candidate;
            if (isPlatformPlacementValid(candidate, generated)) {
                generated.add(candidate);
                return;
            }
        }
        generated.add(fallback);
    }

    private boolean isPlatformPlacementValid(Rectangle2D candidate, List<Rectangle2D> existing) {
        for (Rectangle2D platform : existing) {
            double verticalGap = Math.abs(candidate.getMinY() - platform.getMinY());
            double horizontalGap = Math.max(candidate.getMinX(), platform.getMinX())
                - Math.min(candidate.getMaxX(), platform.getMaxX());
            if (horizontalGap > 0 && horizontalGap < MIN_PASSAGE_WIDTH && verticalGap < 170.0) {
                return false;
            }
            if (verticalGap < 34.0 && horizontalGap < 44.0) {
                return false;
            }
            Rectangle2D padded = new Rectangle2D(platform.getMinX() - 10, platform.getMinY() - 8,
                platform.getWidth() + 20, platform.getHeight() + 16);
            if (candidate.intersects(padded) && verticalGap < 70.0) {
                return false;
            }
        }
        return true;
    }

    private double platformWidth(int minTiles, int maxTiles) {
        int bonus = stageDefinition.getDifficulty() >= 6 ? 1 : 0;
        if (stageDefinition.getType() == StageType.PLATFORM) bonus += 1;
        if (stageDefinition.getType() == StageType.EXPLORATION) maxTiles += 1;

        int min = Math.max(2, minTiles);
        int max = Math.max(min, maxTiles + bonus);
        int tiles = min + random.nextInt(max - min + 1);
        return tiles * 24.0;
    }

    private Rectangle2D[] createCoverWalls() {
        List<Rectangle2D> generated = new ArrayList<>();
        for (int y = 0; y < tileMap.getHeightTiles(); y++) {
            for (int x = 0; x < tileMap.getWidthTiles(); x++) {
                if (tileMap.getTile(x, y).isSolid()) {
                    generated.add(tileMap.tileBounds(x, y));
                }
            }
        }
        return generated.toArray(Rectangle2D[]::new);
    }

    private Rectangle2D anchoredCover(int platformIndex, double ratio, double width, double height) {
        Rectangle2D p = platforms[Math.max(0, Math.min(platformIndex, platforms.length - 1))];
        double maxWidth = Math.max(28.0, p.getWidth() - MIN_PASSAGE_WIDTH * 2.0);
        maxWidth = Math.min(maxWidth, p.getWidth() * 0.46);
        double safeWidth = Math.min(width, maxWidth);
        double margin = Math.min(MIN_PASSAGE_WIDTH, Math.max(14.0, p.getWidth() * 0.18));
        double x = p.getMinX() + p.getWidth() * ratio - safeWidth / 2.0;
        double minX = p.getMinX() + margin;
        double maxX = p.getMaxX() - margin - safeWidth;
        if (maxX < minX) {
            minX = p.getMinX();
            maxX = p.getMaxX() - safeWidth;
        }
        x = Math.max(minX, Math.min(x, maxX));
        return new Rectangle2D(x, p.getMinY() - height, safeWidth, height);
    }

    private void addCoverIfValid(List<Rectangle2D> generated, Rectangle2D cover) {
        if (isCoverPlacementValid(cover, generated)) {
            generated.add(cover);
        }
    }

    private boolean isCoverPlacementValid(Rectangle2D cover, List<Rectangle2D> existingCovers) {
        for (Rectangle2D platform : platforms) {
            boolean restsOnPlatform = Math.abs(cover.getMaxY() - platform.getMinY()) <= 1.5;
            boolean intersectsPlatform = cover.intersects(platform);
            if (intersectsPlatform && !restsOnPlatform) {
                return false;
            }
            double gap = horizontalGap(cover, platform);
            if (gap > 0 && gap < MIN_PASSAGE_WIDTH && verticalRangesOverlap(cover, platform, Config.PLAYER_HEIGHT)) {
                return false;
            }
        }
        for (Rectangle2D existing : existingCovers) {
            double gap = horizontalGap(cover, existing);
            if (gap > 0 && gap < MIN_PASSAGE_WIDTH && verticalRangesOverlap(cover, existing, Config.PLAYER_HEIGHT)) {
                return false;
            }
        }
        return true;
    }

    private double horizontalGap(Rectangle2D a, Rectangle2D b) {
        if (a.getMaxX() <= b.getMinX()) return b.getMinX() - a.getMaxX();
        if (b.getMaxX() <= a.getMinX()) return a.getMinX() - b.getMaxX();
        return -1.0;
    }

    private boolean verticalRangesOverlap(Rectangle2D a, Rectangle2D b, double padding) {
        return a.getMaxY() + padding > b.getMinY() && b.getMaxY() + padding > a.getMinY();
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

    private void damagePlayerOnTraps() {
        for (Rectangle2D trap : tileMap.getHazardTilesNear(player.getHitbox())) {
            if (Collision.checkAABB(player.getHitbox(), trap)) {
                player.takeDamage(14);
                return;
            }
        }
    }

    /**
     * 死亡重生時回滾到剛進入 Level2 的狀態。
     */
    private void rollbackToInitialSnapshot() {
        player.resetForCheckpoint(initialSnapshot.getPlayerX(),
                                  initialSnapshot.getPlayerY(),
                                  initialSnapshot.getPlayerHp(),
                                  initialSnapshot.getPlayerMana());
        player.startInvincibility(Config.PLAYER_SPAWN_PROTECTION_SECONDS);
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
        for (Rectangle2D cover : covers) {
            Collision.resolveSolid(player, cover);
        }
        if (hasSolidSupportUnderPlayer()) {
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

    private boolean hasSolidSupportUnderPlayer() {
        Rectangle2D hitbox = player.getHitbox();
        double feetY = hitbox.getMaxY();
        for (Rectangle2D cover : covers) {
            boolean horizontalSupport = hitbox.getMaxX() > cover.getMinX() + 1
                && hitbox.getMinX() < cover.getMaxX() - 1;
            if (horizontalSupport && Math.abs(feetY - cover.getMinY()) <= 1.5) {
                return true;
            }
        }
        return false;
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
    private void resolveEnemyPlatformCollisions(double dt) {
        for (Enemy enemy : enemies) {
            if (!enemy.isAlive()) continue;

            boolean groundedOnSolid = false;
            for (Rectangle2D cover : covers) {
                int result = Collision.resolveSolid(enemy, cover);
                if (result == -1) {
                    groundedOnSolid = true;
                } else if (result == 1 || result == 2) {
                    enemy.recoverFromWall(result);
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
            enemy.updateStuckRecovery(dt);
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
                enemy.takeDamage(Main.getPlayerMeleeDamage());
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
                if (fireball.canHitTarget(enemy)
                    && Collision.checkAABB(fireball.getHitbox(), enemy.getHitbox())) {
                    enemy.takeDamage(fireball.getDamage());
                    fireball.markTargetHit(enemy);
                    if (!fireball.isPiercingEnemies()) fireball.destroy();
                    consumed = true;
                    if (!fireball.isPiercingEnemies()) break;
                }
            }

            if (!consumed) {
                destroyFireballOnWall(fireball);
            }
        }
    }

    /**
     * 火球只會被真正牆壁 / 掩體阻擋；跳板與單向平台不再攔截遠程攻擊。
     */
    private void destroyFireballOnWall(Fireball fireball) {
        if (Collision.checkAABB(fireball.getHitbox(), ground)) {
            fireball.destroy();
            return;
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
            enemy.tryDamagePlayer(player);
        }
    }

    /**
     * 普通敵人死亡時依機率產生道具掉落。
     */
    private void checkEnemyDrops() {
        for (int i = 0; i < enemies.length; i++) {
            Enemy enemy = enemies[i];
            if (enemy == null || enemyDropsHandled[i] || enemy.isAlive()) continue;

            enemyDropsHandled[i] = true;
            grantExperience(enemy instanceof RangedEnemy ? 26 : 18);
            addFallingGold(random.nextInt(10) + 1,
                enemy.getX() - 18, enemy.getY() + enemy.getHeight() - GoldPickup.SIZE);
            PickupType drop = rollEnemyDrop();
            if (drop != null) {
                addFallingPickup(drop, enemy.getX() + 24,
                    enemy.getY() + enemy.getHeight() - PickupItem.SIZE);
            }
        }
    }

    private void grantExperience(int amount) {
        if (Main.addExperience(amount)) {
            Main.applyPlayerProgress(player);
            showPickupNotice("LEVEL UP!");
        } else {
            showPickupNotice("+" + amount + " EXP");
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
        itemSpawnManager.setForbiddenZones(createHazardForbiddenZones());
        pickupItems.add(itemSpawnManager.spawn(type, x, y, quantity, pickupItems));
    }

    private void addGold(int amount, double x, double y) {
        goldSpawnManager.setForbiddenZones(createHazardForbiddenZones());
        goldPickups.add(goldSpawnManager.spawn(amount, x, y, pickupItems, goldPickups));
    }

    private void addFallingPickup(PickupType type, double x, double y) {
        pickupItems.add(itemSpawnManager.spawnDrop(type, x, y, 1));
    }

    private void addFallingGold(int amount, double x, double y) {
        goldPickups.add(goldSpawnManager.spawnDrop(amount, x, y));
    }

    private void updateFallingDrops(double dt) {
        for (PickupItem item : pickupItems) {
            item.updateFalling(dt, ground, platforms, covers);
        }
        for (GoldPickup gold : goldPickups) {
            gold.updateFalling(dt, ground, platforms, covers);
        }
    }

    private List<Rectangle2D> createHazardForbiddenZones() {
        List<Rectangle2D> zones = new ArrayList<>();
        for (int y = 0; y < tileMap.getHeightTiles(); y++) {
            for (int x = 0; x < tileMap.getWidthTiles(); x++) {
                if (tileMap.getTile(x, y).isHazardous()) {
                    zones.add(tileMap.tileBounds(x, y));
                }
            }
        }
        return zones;
    }

    private double[] safeLegacyHealthPoint(double[] preferred) {
        ItemSpawnResolver resolver = new ItemSpawnResolver(ground, platforms, covers);
        resolver.setForbiddenZones(createHazardForbiddenZones());
        Point2D point = resolver.findValidSpawnPosition(preferred[0], preferred[1], pickupItems);
        return new double[] { point.getX(), point.getY() };
    }

    private void addExplorationSupplies() {
        PickupType[] supplyTypes = {
            PickupType.SMALL_POTION,
            PickupType.BOMB,
            PickupType.FIRE_SCROLL,
            PickupType.ICE_SCROLL,
            PickupType.SMALL_POTION,
            PickupType.BOMB,
            PickupType.FIRE_SCROLL
        };

        List<Integer> supplyPlatforms = new ArrayList<>();
        for (int i = 0; i < platforms.length; i++) {
            if (isValidatedReachablePlatform(platforms[i])
                    && platforms[i].getWidth() >= PickupItem.SIZE + 24) {
                supplyPlatforms.add(i);
            }
        }
        Collections.shuffle(supplyPlatforms, random);

        int count = Math.min(stageDefinition.getExplorationSupplyCount() + 2, supplyPlatforms.size());
        for (int i = 0; i < count; i++) {
            double[] point = pickupPoint(supplyPlatforms.get(i),
                0.18 + random.nextDouble() * 0.64);
            PickupType type = supplyTypes[random.nextInt(supplyTypes.length)];
            if (i % 2 == 0 || stageDefinition.getType() == StageType.EXPLORATION) {
                addPickup(type, point[0], point[1]);
            } else {
                addGold(3 + random.nextInt(8), point[0], point[1]);
            }
        }
    }

    private double[] randomPickupPoint(int minPlatformIndex, int maxPlatformIndex) {
        int min = Math.max(0, Math.min(minPlatformIndex, platforms.length - 1));
        int max = Math.max(min, Math.min(maxPlatformIndex, platforms.length - 1));
        List<Integer> candidates = new ArrayList<>();
        for (int i = min; i <= max; i++) {
            if (isValidatedReachablePlatform(platforms[i])) {
                candidates.add(i);
            }
        }
        if (candidates.isEmpty()) {
            for (int i = 0; i < platforms.length; i++) {
                if (isValidatedReachablePlatform(platforms[i])) {
                    candidates.add(i);
                }
            }
        }
        int platformIndex = candidates.isEmpty() ? min : candidates.get(random.nextInt(candidates.size()));
        return pickupPoint(platformIndex, 0.18 + random.nextDouble() * 0.64);
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
                if (projectile.canHitTarget(enemy)
                    && Collision.checkAABB(projectile.getHitbox(), enemy.getHitbox())) {
                    enemy.applySlow(IceProjectile.SLOW_DURATION, IceProjectile.SLOW_MULTIPLIER);
                    projectile.markTargetHit(enemy);
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
                boolean accepted = item.getType().isPotion()
                    ? potionInventory.add(item.getType(), item.getQuantity())
                    : inventory.add(item.getType(), item.getQuantity());
                if (accepted) {
                    item.markPickedUp();
                    showPickupNotice("+" + item.getType().getHudLabel());
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
                showPickupNotice("+" + gold.getAmount() + " Gold");
            }
        }
        goldPickups.removeIf(GoldPickup::isPickedUp);
    }

    private void updateBombs(double dt) {
        for (BombEntity bomb : bombs) {
            bomb.update(dt, ground, platforms, covers);
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

    private boolean trySwapGroundPotion(int slotIndex) {
        PickupItem groundItem = findBlockedPotionUnderPlayer();
        if (groundItem == null) return false;

        InventorySlot dropped = potionInventory.replaceSlot(slotIndex,
            groundItem.getType(), groundItem.getQuantity());
        if (dropped == null) return false;

        groundItem.markPickedUp();
        addPickup(dropped.getType(), player.getX(),
            player.getY() + player.getHeight() - PickupItem.SIZE, dropped.getCount());
        pickupItems.removeIf(PickupItem::isPickedUp);
        showPickupNotice("Potion swapped");
        return true;
    }

    private PickupItem findBlockedPickupUnderPlayer() {
        if (!inventory.isFull()) return null;
        for (PickupItem item : pickupItems) {
            if (item.isPickedUp()) continue;
            if (item.getType().isPotion()) continue;
            if (inventory.contains(item.getType())) continue;
            if (Collision.checkAABB(player.getHitbox(), item.getHitbox())) {
                return item;
            }
        }
        return null;
    }

    private PickupItem findBlockedPotionUnderPlayer() {
        if (!potionInventory.isFull()) return null;
        for (PickupItem item : pickupItems) {
            if (item.isPickedUp() || !item.getType().isPotion()) continue;
            if (potionInventory.contains(item.getType())) continue;
            if (Collision.checkAABB(player.getHitbox(), item.getHitbox())) return item;
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

    private int keyToPotionSlotIndex(KeyCode key) {
        return switch (key) {
            case N -> 0;
            case M -> 1;
            default -> -1;
        };
    }

    private boolean handleTalentChoiceKey(KeyCode key) {
        int index = switch (key) {
            case DIGIT1, NUMPAD1 -> 0;
            case DIGIT2, NUMPAD2 -> 1;
            case DIGIT3, NUMPAD3 -> 2;
            default -> -1;
        };
        if (index < 0) return false;
        if (Main.chooseTalent(index) != null) {
            Main.applyPlayerProgress(player);
            showPickupNotice("Talent learned");
            return true;
        }
        return false;
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
            showPickupNotice("+" + ITEM_HEAL + " HP");
        }
    }

    /**
     * 檢查玩家是否碰到終點門。
     * 碰到後停止 AnimationTimer 並切換至 Boss 關。
     * transitioning 旗標防止重複觸發。
     */
    private void checkGoalDoor() {
        if (transitioning || SceneTransitionManager.isTransitioning()) return;

        boolean nearGoal = Collision.checkAABB(player.getHitbox(), goalDoor);
        if (nearGoal && portalEnterRequested && player.isOnGround()
            && SceneTransitionManager.tryBeginTransition("LEVEL_2_TO_NEXT")) {
            transitioning = true;
            Main.setPersistedPlayerState(player.getHp(), player.getMana());   // 帶入狀態到 Boss 關
            this.stop();
            Main.completeNormalStage();
        }
        portalEnterRequested = false;
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
        DungeonMapRenderer.draw(gc, tileMap, cameraX, Main.peekNextSceneLabel());
        if (flowController.isDebugVisible()) {
            DungeonMapRenderer.drawDebug(gc, tileMap, cameraX);
        }

        gc.save();
        gc.translate(-cameraX, 0);

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

        // 7. 所有存活的敵人
        for (Enemy enemy : enemies) {
            if (!isVisible(enemy.getHitbox())) continue;
            enemy.draw(gc);
        }

        // 8. 玩家（最後繪製，顯示在最上層）
        player.draw(gc);
        effectManager.draw(gc);
        gc.restore();

        if (isNearGoalDoor()) {
            HudRenderer.drawGoalPrompt(gc,
                goalDoor.getMinX() + goalDoor.getWidth() / 2.0 - cameraX,
                goalDoor.getMinY() - 32);
        }

        // 9. HUD：左上角血量條
        drawHUD(gc);
        flowController.drawDebugOverlay(gc, debugLines());

        // 10. 玩家死亡後疊加 GAME OVER 畫面
        if (!player.isAlive()) {
            drawGameOverOverlay(gc);
        }
        flowController.drawPauseOverlay(gc);
    }

    private void drawGameOverOverlay(GraphicsContext gc) {
        HudRenderer.drawGameOverOverlay(gc, "GAME OVER", "按 R 重新開始本關", "（將重置至本關開始）");
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

    /** 繪製舊版補血生成點，沿用目前小藥水圖示以保持道具風格一致。 */
    private void drawHealthItem(GraphicsContext gc) {
        if (healthItem == null) return;
        if (!isVisible(healthItem)) return;

        double ix = healthItem.getMinX();
        double iy = healthItem.getMinY();
        double s  = ITEM_SIZE;
        PickupType.SMALL_POTION.drawIcon(gc, ix, iy, s);
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
        HudRenderer.drawPlayerStatus(gc, player,
            Main.getStageLabel() + " - " + stageDefinition.getType().getLabel().toUpperCase());
        HudRenderer.drawStageProgress(gc, player.getX(), tileMap.getSpawnX(),
            goalDoor.getMinX() + goalDoor.getWidth() / 2.0);
        HudRenderer.drawGold(gc, Main.getGold());
        HudRenderer.drawInventorySlots(gc, inventory, selectedInventorySlot);
        HudRenderer.drawPotionSlots(gc, potionInventory);
        PickupItem blockedItem = findBlockedPickupUnderPlayer();
        if (blockedItem != null) {
            HudRenderer.drawBackpackFullPrompt(gc, inventory, blockedItem);
        }
        PickupItem blockedPotion = findBlockedPotionUnderPlayer();
        if (blockedPotion != null) {
            HudRenderer.drawPotionFullPrompt(gc, potionInventory, blockedPotion);
        }
        if (inventoryOpen) {
            HudRenderer.drawInventoryOverlay(gc, inventory, selectedInventorySlot);
        }
        HudRenderer.drawControlsHint(gc);
        HudRenderer.drawPickupNotice(gc, pickupNotice, pickupNoticeTimer);
        HudRenderer.drawTalentSelection(gc, Main.getPlayerProgress());
    }

    private List<String> debugLines() {
        List<String> lines = new ArrayList<>();
        PlatformValidationResult result = tileMap.getValidationResult();
        int aliveEnemies = 0;
        for (Enemy enemy : enemies) {
            if (enemy.isAlive()) aliveEnemies++;
        }

        lines.add(String.format("FPS: %.0f", fps));
        lines.add(String.format("Player: %.1f, %.1f", player.getX(), player.getY()));
        lines.add(String.format("Velocity: %.1f, %.1f", player.getVelocityX(), player.getVelocityY()));
        lines.add("GameState: " + SceneTransitionManager.getCurrentGameState());
        lines.add("Transitioning: " + SceneTransitionManager.isTransitioning());
        lines.add("Stage: " + stageDefinition.getHudTitle());
        lines.add("Current level: " + SceneTransitionManager.getCurrentLevel());
        lines.add("Boss type: " + SceneTransitionManager.getCurrentBossType());
        lines.add(String.format("Portal cooldown: %.2f", SceneTransitionManager.getPortalCooldown()));
        lines.add("Active loops: " + SceneTransitionManager.getActiveGameLoopCount());
        lines.add("Scene: " + SceneTransitionManager.getCurrentSceneName());
        lines.add("Enemy count: " + aliveEnemies + "/" + enemies.length);
        lines.add("Boss alive: false");
        lines.add("Projectiles: " + countProjectiles());
        lines.add("Effects: " + effectManager.getActiveEffectCount());
        lines.add("Inventory: " + debugInventorySlots());
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

    private boolean isNearGoalDoor() {
        return Collision.checkAABB(player.getHitbox(), inflate(goalDoor, 12));
    }

    private Rectangle2D inflate(Rectangle2D rect, double amount) {
        return new Rectangle2D(rect.getMinX() - amount, rect.getMinY() - amount,
            rect.getWidth() + amount * 2, rect.getHeight() + amount * 2);
    }

    private boolean isPortalEnterKey(KeyCode key) {
        return key == KeyCode.ENTER;
    }

    private void showPickupNotice(String text) {
        pickupNotice = text;
        pickupNoticeTimer = 1.4;
    }

    private String debugInventorySlots() {
        List<String> slots = new ArrayList<>();
        for (int i = 0; i < inventory.getCapacity(); i++) {
            InventorySlot slot = inventory.getSlot(i);
            slots.add(slot == null || slot.isEmpty()
                ? "-"
                : slot.getType().name() + "x" + slot.getCount());
        }
        return String.join(",", slots);
    }

    private int countProjectiles() {
        int count = player.getFireballs().size() + player.getIceProjectiles().size() + bombs.size();
        for (Enemy enemy : enemies) {
            if (enemy instanceof RangedEnemy ranged) {
                count += ranged.getProjectiles().size();
            }
        }
        return count;
    }

    private void cleanup() {
        this.stop();
        effectManager.clear();
        player.getFireballs().clear();
        player.getIceProjectiles().clear();
        bombs.clear();
        pickupItems.clear();
        goldPickups.clear();
        for (Enemy enemy : enemies) {
            if (enemy instanceof RangedEnemy ranged) {
                ranged.clearProjectiles();
            }
        }
    }
}
