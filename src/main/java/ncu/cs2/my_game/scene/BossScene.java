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
import ncu.cs2.my_game.effect.EffectManager;
import ncu.cs2.my_game.entity.BombEntity;
import ncu.cs2.my_game.entity.Boss;
import ncu.cs2.my_game.entity.BossType;
import ncu.cs2.my_game.entity.Enemy;
import ncu.cs2.my_game.entity.Fireball;
import ncu.cs2.my_game.entity.GroundSpike;
import ncu.cs2.my_game.entity.GroundSpikeBoss;
import ncu.cs2.my_game.entity.IceProjectile;
import ncu.cs2.my_game.entity.Player;
import ncu.cs2.my_game.entity.RangedEnemy;
import ncu.cs2.my_game.entity.SummonerBoss;
import ncu.cs2.my_game.fsm.BossState;
import ncu.cs2.my_game.item.Inventory;
import ncu.cs2.my_game.item.InventorySlot;
import ncu.cs2.my_game.item.ItemSpawnManager;
import ncu.cs2.my_game.item.PickupItem;
import ncu.cs2.my_game.item.PickupType;
import ncu.cs2.my_game.item.UseContext;
import ncu.cs2.my_game.physics.Collision;
import ncu.cs2.my_game.state.BossFightSnapshot;
import ncu.cs2.my_game.state.StageSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Boss 戰場景，繼承 AnimationTimer 充當遊戲迴圈。
 *
 * <p>單一寬闊房間，三個小平台供玩家閃躲。
 * Boss 從畫面右側出現，依 BossStateMachine 的 FSM 驅動行為：
 * IDLE → CHASE → DASH（HP &lt; 60%）→ RAGE（HP &lt; 30%）。</p>
 *
 * <pre>
 * 房間配置（y 軸向下）：
 *
 *  y=340  [P2 中央高台]
 *  y=420  [P1 左低台]               [P3 右低台]   ← Boss 從右側登場
 *  y=550  =================== GROUND ===================
 * </pre>
 *
 * <p>階段提示：</p>
 * <ul>
 *   <li>HP 跌破 60% → 畫面閃紅 + 顯示「PHASE 2」1 秒</li>
 *   <li>HP 跌破 30% → 畫面閃紅 + 顯示「PHASE 3 - RAGE」1 秒</li>
 * </ul>
 */
public class BossScene extends AnimationTimer {

    // ── 房間常數 ─────────────────────────────────────────────────────────────

    /** 地板 Y 座標 */
    private static final double GROUND_Y = Config.WINDOW_HEIGHT - Config.GROUND_THICKNESS;

    /** 平台厚度（像素） */
    private static final double PLAT_H = 16.0;

    /** Boss 血量條寬度（像素） */
    private static final double BOSS_BAR_W = 500.0;

    /** Boss 血量條高度（像素） */
    private static final double BOSS_BAR_H = 18.0;

    /** Boss 死亡後等待幾秒再切換場景 */
    private static final double WIN_DELAY = 1.0;

    /** 階段文字顯示時間（秒） */
    private static final double PHASE_TEXT_DURATION = 1.0;

    /** 紅色閃光持續時間（秒） */
    private static final double FLASH_DURATION = 0.25;

    /** DASH 衝刺命中玩家的傷害值 */
    private static final int BOSS_DASH_DAMAGE = 7;

    /** Boss 身體接觸玩家的傷害值（Phase 1 起生效） */
    private static final int BOSS_CONTACT_DAMAGE = 4;

    /** Boss 身體接觸傷害的冷卻時間（秒） */
    private static final double BOSS_CONTACT_COOLDOWN = 0.8;

    /** Boss 房生成牆與平台時保留至少一個玩家寬度的通行空間。 */
    private static final double MIN_PASSAGE_WIDTH = Config.PLAYER_WIDTH + 10.0;

    // ── 欄位 ─────────────────────────────────────────────────────────────────

    /** 主視窗參考 */
    private final Stage stage;

    /** 繪圖用 GraphicsContext */
    private final GraphicsContext gc;

    private final EffectManager effectManager;
    private final GameFlowController flowController;

    /** 玩家實體 */
    private final Player player;

    /** Boss 實體（AI 由 BossStateMachine 驅動） */
    private Boss boss;

    /** 跨關卡背包 */
    private final Inventory inventory;

    /** Boss 關可撿取道具 */
    private final List<PickupItem> pickupItems;

    /** Boss 關可撿取金幣 */
    private final List<GoldPickup> goldPickups;

    private final List<BombEntity> bombs;

    /** 掉落用亂數 */
    private final Random random;

    /** 道具安全生成器 */
    private final ItemSpawnManager itemSpawnManager;

    /** 金幣安全生成器 */
    private final GoldSpawnManager goldSpawnManager;

    /** 是否顯示背包 overlay。 */
    private boolean inventoryOpen;

    /** 目前 HUD 高亮的背包 slot。 */
    private int selectedInventorySlot;

    /** Boss 關剛進入時的狀態快照 */
    private final StageSnapshot initialSnapshot;

    private final BossFightSnapshot bossFightSnapshot;

    /** 地板碰撞框（橫跨整個畫面底部） */
    private final Rectangle2D ground;

    /**
     * 三個小平台的碰撞框陣列，供玩家跳躍閃躲用。
     * Boss 不與平台互動，只與地板碰撞。
     */
    private final Rectangle2D[] platforms;

    /** Boss 房實體掩體/牆壁。 */
    private final Rectangle2D[] covers;

    /** 小兵視線與投射物會被平台和掩體共同阻擋。 */
    private final Rectangle2D[] visionBlockers;

    /** Boss dash 落點檢查用地面集合。 */
    private final Rectangle2D[] dashSurfaces;

    private final Rectangle2D[] navigationSurfaces;

    /** 本次 Boss 關固定的 Boss 類型；死亡 rollback 不重新抽，避免復活後換 Boss。 */
    private final BossType bossType;

    // ── 階段與視覺效果 ────────────────────────────────────────────────────────

    /** 是否已觸發第二階段提示（HP 跌破 60%）；避免重複觸發 */
    private boolean phase2Triggered = false;

    /** 是否已觸發第三階段提示（HP 跌破 30%）；避免重複觸發 */
    private boolean phase3Triggered = false;

    /** 紅色閃光剩餘時間（秒）；> 0 時繪製半透明紅色全螢幕疊加層 */
    private double flashTimer = 0;

    /** 階段提示文字剩餘顯示時間（秒）；> 0 時在畫面中央顯示 phaseText */
    private double phaseTextTimer = 0;

    /** 目前要顯示的階段提示文字（"PHASE 2" 或 "PHASE 3 - RAGE"） */
    private String phaseText = "";

    // ── 勝負計時 ─────────────────────────────────────────────────────────────

    /**
     * Boss 死亡後的延遲計時器（秒）。
     * 累計達到 WIN_DELAY 後才切換至 EndScene，讓玩家看到死亡動畫。
     */
    private double bossDeadTimer = 0;

    // ── 流程控制 ─────────────────────────────────────────────────────────────

    /** 是否已觸發場景切換，防止 handle() 重複觸發 Main.startEnd() */
    private boolean transitioning = false;
    private boolean portalEnterRequested = false;

    /**
     * 玩家死亡後是否按下了 R 鍵。
     * 在 handle() 內檢查後重置並重啟場景。
     */
    private boolean rKeyPressed = false;

    /** Boss 死亡掉落是否已產生 */
    private boolean bossDropHandled = false;

    /** Boss 身體接觸傷害的冷卻計時器（秒）；> 0 時不會再次造成接觸傷害 */
    private double bossContactCooldown = 0;

    /** 上一幀的時間戳記（奈秒）；0 表示尚未初始化 */
    private long lastNano = 0;

    private double playerPlatformDropTimer = 0;
    private double bossPlatformDropTimer = 0;
    private Rectangle2D bossExitDoor = null;
    private double fps = 0;

    // ─────────────────────────────────────────────────────────────────────────

    /**
     * 建構子：初始化所有場景物件並立即啟動 AnimationTimer。
     * Boss 從畫面右側（x=700）出現，Player 從左側（x=70）出發。
     *
     * @param stage 主視窗，用於切換 JavaFX Scene
     */
    public BossScene(Stage stage) {
        this.stage = stage;

        // ── 建立 Canvas 與 JavaFX Scene ──────────────────────────────────────
        Canvas canvas = new Canvas(Config.WINDOW_WIDTH, Config.WINDOW_HEIGHT);
        gc = canvas.getGraphicsContext2D();

        Scene javafxScene = CanvasSceneSupport.createScaledCanvasScene(stage, canvas);
        Fireball.setWorldBounds(Config.WINDOW_WIDTH, Config.WINDOW_HEIGHT);
        effectManager = new EffectManager();
        flowController = GameFlowController.forScene(this::cleanup, () -> Main.startBoss());
        Main.registerActiveScene("BOSS", Main.getStageNumber(), GameState.BOSS_FIGHT, this::cleanup);

        // ── 初始化玩家（帶入 Level2 結束時的血量） ────────────────────────────
        player = new Player(70, 460);
        player.setHp(Main.getPersistedHp());
        player.setMana(Main.getPersistedMana());
        player.startInvincibility(Config.PLAYER_SPAWN_PROTECTION_SECONDS);

        inventory = Main.getInventory();
        pickupItems = new ArrayList<>();
        goldPickups = new ArrayList<>();
        bombs = new ArrayList<>();
        random = new Random();

        // ── 地板 ──────────────────────────────────────────────────────────────
        ground = new Rectangle2D(0, GROUND_Y, Config.WINDOW_WIDTH, Config.GROUND_THICKNESS);

        // ── 三個閃躲用平台 ────────────────────────────────────────────────────
        platforms = new Rectangle2D[] {
            new Rectangle2D( 36 + bossRand(-8, 14), 452 + bossRand(-6, 8), bossPlatformWidth(7, 9), PLAT_H),
            new Rectangle2D(330 + bossRand(-14, 16), 452 + bossRand(-6, 8), bossPlatformWidth(7, 9), PLAT_H),
            new Rectangle2D(615 + bossRand(-12, 10), 452 + bossRand(-6, 8), bossPlatformWidth(6, 8), PLAT_H),
            new Rectangle2D(150 + bossRand(-12, 18), 326 + bossRand(-8, 10), bossPlatformWidth(6, 8), PLAT_H),
            new Rectangle2D(515 + bossRand(-16, 12), 326 + bossRand(-8, 10), bossPlatformWidth(6, 8), PLAT_H),
            new Rectangle2D(335 + bossRand(-14, 14), 202 + bossRand(-6, 8), bossPlatformWidth(5, 7), PLAT_H),
        };
        covers = new Rectangle2D[0];
        visionBlockers = platforms;
        dashSurfaces = mergeBlockers(new Rectangle2D[] { ground }, visionBlockers);
        navigationSurfaces = dashSurfaces;
        bossType = Main.getOrCreateBossType();
        SceneTransitionManager.setBossFightState(BossFightState.FIGHTING);
        boss = createBoss();
        itemSpawnManager = new ItemSpawnManager(ground, platforms);
        goldSpawnManager = new GoldSpawnManager(ground, platforms);
        inventoryOpen = false;
        selectedInventorySlot = 0;

        initialSnapshot = new StageSnapshot(
            player.getX(), player.getY(), player.getHp(), player.getMana(),
            inventory, pickupItems, goldPickups, new ArrayList<>(),
            false, 0, 0, 0, 0
        );
        bossFightSnapshot = new BossFightSnapshot(initialSnapshot, bossType, selectedInventorySlot);
        SceneTransitionManager.setBossFightSnapshotActive(true);

        // ── 鍵盤事件 ──────────────────────────────────────────────────────────
        javafxScene.setOnKeyPressed(e -> {
            if (SceneTransitionManager.isTransitioning()) return;
            if (!player.isAlive() && e.getCode() == KeyCode.R) {
                rKeyPressed = true;
                e.consume();
                return;
            }
            if (isPortalEnterKey(e.getCode()) && bossExitDoor != null
                && Collision.checkAABB(player.getHitbox(), inflate(bossExitDoor, 14))) {
                portalEnterRequested = true;
                e.consume();
                return;
            }
            if (flowController.handleKeyPressed(e)) return;
            // 正常移動輸入轉發給玩家
            player.handleKeyPressed(e.getCode());
            if (player.consumeAttackStarted()) {
                effectManager.playSlash(player);
            }
            handleInventoryKey(e.getCode());
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

        // 計算距上一幀的時間差（秒），上限 0.05s 防止大幀跳躍
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

    // ── update ────────────────────────────────────────────────────────────────

    /**
     * 每幀更新邏輯，三條主要分支：
     * <ol>
     *   <li>Boss 已死：倒計時後切換至 EndScene（仍渲染，讓玩家看到死亡畫面）</li>
     *   <li>玩家已死：等待 R 鍵重啟（不更新任何物理或戰鬥）</li>
     *   <li>正常遊戲：完整物理→碰撞→戰鬥→階段判定流程</li>
     * </ol>
     *
     * @param dt 時間差（秒）
     */
    private void update(double dt) {

        // ── 分支 1：Boss 死亡倒計時 ───────────────────────────────────────────
        if (boss.getCurrentState() == BossState.DEAD) {
            SceneTransitionManager.setBossFightState(BossFightState.BOSS_DEFEATED);
            if (!bossDropHandled) {
                bossDropHandled = true;
                spawnBossDrops();
                bossExitDoor = createBossExitDoor();
                bombs.clear();
                effectManager.clear();
                boss.handleDeathCleanup();
                BossUIManager.clearBossUI();
            }

            if (player.consumeAttackStarted()) {
                effectManager.playSlash(player);
            }
            effectManager.update(dt);
            player.update(dt);
            updatePlayerPlatformDrop(dt);
            resolvePlayerPlatformCollisions();
            tryResolvePlayerStandUp();
            checkPickupItems();
            checkGoldPickups();
            checkBossExitDoor();

            // 仍更新視覺計時器，讓閃光與文字正常淡出
            if (flashTimer    > 0) flashTimer    -= dt;
            if (phaseTextTimer > 0) phaseTextTimer -= dt;
            return;
        }

        // ── 分支 2：玩家死亡，等待重啟 ───────────────────────────────────────
        if (!player.isAlive()) {
            SceneTransitionManager.setBossFightState(BossFightState.PLAYER_DIED);
            if (rKeyPressed) {
                rKeyPressed   = false;
                SceneTransitionManager.setBossFightState(BossFightState.RETRYING);
                rollbackToInitialSnapshot();
                SceneTransitionManager.setBossFightState(BossFightState.FIGHTING);
            }
            return;
        }

        // ── 分支 3：正常遊戲邏輯 ─────────────────────────────────────────────

        // 1. 玩家物理更新（重力、輸入、攻擊計時）
        if (player.consumeAttackStarted()) {
            effectManager.playSlash(player);
        }
        effectManager.update(dt);
        player.update(dt);
        updatePlayerPlatformDrop(dt);

        // 2. Boss 物理更新（FSM 決定速度、重力、投射物）
        updateBossMinionAwareness();
        boss.configureDashNavigation(covers, dashSurfaces, Config.WINDOW_WIDTH);
        boss.update(dt);

        // 3. 玩家平台碰撞解析
        resolvePlayerPlatformCollisions();

        // 4. Boss 地板碰撞解析（Boss 只與地板互動，不踩平台）
        resolveBossGround(dt);

        // 4.5 若玩家放開 S，且頭頂空間足夠，恢復站立
        tryResolvePlayerStandUp();

        // 5. 玩家左右邊界
        if (player.getX() < 0)
            player.setX(0);
        if (player.getX() + player.getWidth() > Config.WINDOW_WIDTH)
            player.setX(Config.WINDOW_WIDTH - player.getWidth());

        // 6. Boss 左右邊界（防止 DASH 衝出畫面）
        if (boss.getX() < 0)
            boss.setX(0);
        if (boss.getX() + boss.getWidth() > Config.WINDOW_WIDTH)
            boss.setX(Config.WINDOW_WIDTH - boss.getWidth());

        // 7. 戰鬥碰撞判定
        checkPlayerAttackVsBoss();
        checkPlayerFireballsVsBoss();
        checkPlayerIceProjectilesVsBoss();
        checkBossDashVsPlayer();
        checkBossProjectilesVsPlayer();
        checkBossBodyContactVsPlayer();
        checkBossMinionsVsPlayer();
        checkBossMinionProjectilesVsPlayer();
        checkGroundSpikesVsPlayer();
        checkPickupItems();
        checkGoldPickups();
        updateBombs(dt);

        // 8. 階段轉換檢查（首次跌破閾值時觸發視覺效果）
        checkPhaseTransitions();

        // 9. 視覺計時器遞減
        if (flashTimer    > 0) flashTimer    -= dt;
        if (phaseTextTimer > 0) phaseTextTimer -= dt;
        if (bossContactCooldown > 0) bossContactCooldown -= dt;
    }

    // ── 碰撞解析 ─────────────────────────────────────────────────────────────

    /**
     * 解析玩家與地板及三個平台的碰撞。
     * 找到第一個命中的表面即停止，設定 Y 座標並標記站地。
     */
    private void resolvePlayerPlatformCollisions() {
        boolean groundedOnSolid = false;
        for (Rectangle2D cover : covers) {
            int result = Collision.resolveSolid(player, cover);
            if (result == -1) groundedOnSolid = true;
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

        // 再逐一檢查三個平台
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
     * 解析 Boss 與地板的碰撞。
     * Boss 只與地板互動（不踩平台），落地後清除垂直速度。
     */
    private void resolveBossGround(double dt) {
        if (bossPlatformDropTimer > 0) {
            bossPlatformDropTimer -= dt;
            if (bossPlatformDropTimer < 0) bossPlatformDropTimer = 0;
        }

        boolean bossOnSolid = false;
        for (Rectangle2D cover : covers) {
            int result = Collision.resolveSolid(boss, cover);
            if (result == -1) bossOnSolid = true;
        }
        if (bossOnSolid) {
            boss.setOnGround(true);
        } else if (Collision.checkPlatform(boss, ground)) {
            boss.setY(ground.getMinY() - boss.getHeight());
            boss.setOnGround(true);
        } else {
            boolean bossOnPlatform = false;
            if (bossPlatformDropTimer <= 0 && boss.shouldDropFromPlatform()) {
                bossPlatformDropTimer = 0.28;
                boss.setY(boss.getY() + 8);
                boss.setOnGround(false);
            }
            if (bossPlatformDropTimer <= 0) {
                for (Rectangle2D platform : platforms) {
                    if (Collision.checkPlatform(boss, platform)) {
                        boss.setY(platform.getMinY() - boss.getHeight());
                        boss.setOnGround(true);
                        bossOnPlatform = true;
                        break;
                    }
                }
                if (!bossOnPlatform) {
                    boss.setOnGround(false);
                }
            } else {
                boss.setOnGround(false);
            }
        }

        for (Enemy minion : boss.getMinions()) {
            boolean minionOnSolid = false;
            for (Rectangle2D cover : covers) {
                int result = Collision.resolveSolid(minion, cover);
                if (result == -1) {
                    minionOnSolid = true;
                } else if (result == 1 || result == 2) {
                    minion.recoverFromWall(result);
                }
            }
            if (minionOnSolid) {
                minion.setOnGround(true);
                continue;
            }
            if (Collision.checkPlatform(minion, ground)) {
                minion.setY(ground.getMinY() - minion.getHeight());
                minion.setOnGround(true);
                continue;
            }
            boolean minionOnPlatform = false;
            if (!minion.shouldDropFromPlatform()) {
                for (Rectangle2D platform : platforms) {
                    if (Collision.checkPlatform(minion, platform)) {
                        minion.setY(platform.getMinY() - minion.getHeight());
                        minion.setOnGround(true);
                        minionOnPlatform = true;
                        break;
                    }
                }
            }
            if (!minionOnPlatform) {
                minion.setOnGround(false);
            }
            minion.updateStuckRecovery(dt);
        }
    }

    private void updateBossMinionAwareness() {
        for (Enemy minion : boss.getMinions()) {
            if (minion.isAlive()) {
                minion.setNavigationSurfaces(navigationSurfaces);
                minion.setMovementBlockers(covers);
                minion.updateAwareness(player, visionBlockers);
            }
        }
    }

    private Rectangle2D[] mergeBlockers(Rectangle2D[] first, Rectangle2D[] second) {
        Rectangle2D[] merged = new Rectangle2D[first.length + second.length];
        System.arraycopy(first, 0, merged, 0, first.length);
        System.arraycopy(second, 0, merged, first.length, second.length);
        return merged;
    }

    private Boss createBoss() {
        int stage = Math.max(1, Main.getStageNumber());
        int maxHp = (int) Math.round(Config.BOSS_MAX_HP * 1.05 * (1.0 + stage * 0.08));
        return switch (bossType) {
            case GROUND_SPIKE -> new GroundSpikeBoss(700, 340, player, maxHp, GROUND_Y, dashSurfaces);
            case SUMMONER -> new SummonerBoss(700, 340, player, maxHp, GROUND_Y, dashSurfaces);
            case FIREBALL -> new Boss(700, 340, player, maxHp);
        };
    }

    private double bossPlatformWidth(int minTiles, int maxTiles) {
        int tiles = minTiles + (int) (Math.random() * (maxTiles - minTiles + 1));
        return tiles * 24.0;
    }

    private double bossRand(double min, double max) {
        return min + Math.random() * (max - min);
    }

    private Rectangle2D[] createBossCovers(Rectangle2D[] candidates) {
        List<Rectangle2D> accepted = new ArrayList<>();
        for (Rectangle2D cover : candidates) {
            if (isBossCoverPlacementValid(cover, accepted)) {
                accepted.add(cover);
            }
        }
        return accepted.toArray(Rectangle2D[]::new);
    }

    private boolean isBossCoverPlacementValid(Rectangle2D cover, List<Rectangle2D> accepted) {
        for (Rectangle2D platform : platforms) {
            double gap = horizontalGap(cover, platform);
            if (gap > 0 && gap < MIN_PASSAGE_WIDTH && verticalRangesOverlap(cover, platform, Config.PLAYER_HEIGHT)) {
                return false;
            }
            if (cover.intersects(platform)) {
                return false;
            }
        }
        for (Rectangle2D other : accepted) {
            double gap = horizontalGap(cover, other);
            if (gap > 0 && gap < MIN_PASSAGE_WIDTH && verticalRangesOverlap(cover, other, Config.PLAYER_HEIGHT)) {
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

    private Rectangle2D createBossExitDoor() {
        Rectangle2D best = null;
        for (Rectangle2D platform : platforms) {
            if (platform.getWidth() < 70) continue;
            if (best == null || platform.getMinY() < best.getMinY()) {
                best = platform;
            }
        }
        if (best == null) {
            best = ground;
        }
        double doorW = 46.0;
        double doorH = 92.0;
        double x = best.getMinX() + best.getWidth() / 2.0 - doorW / 2.0;
        x = Math.max(best.getMinX() + 6, Math.min(x, best.getMaxX() - doorW - 6));
        return new Rectangle2D(x, best.getMinY() - doorH, doorW, doorH);
    }

    private void checkBossExitDoor() {
        if (bossExitDoor == null || transitioning || SceneTransitionManager.isTransitioning()) return;
        boolean nearDoor = Collision.checkAABB(player.getHitbox(), bossExitDoor);
        if (nearDoor && portalEnterRequested && player.isOnGround()
            && SceneTransitionManager.tryBeginTransition("BOSS_TO_NEXT_LEVEL")) {
            transitioning = true;
            this.stop();
            SceneTransitionManager.setBossFightState(BossFightState.COMPLETED);
            SceneTransitionManager.setBossFightSnapshotActive(false);
            Main.advanceAfterBoss();
            Main.startLevel2();
        }
        portalEnterRequested = false;
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
        inventory.useSlot(slotIndex, new UseContext(player, null, boss, bombs));
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
     * 死亡重生時回滾到剛進入 Boss 關的狀態。
     */
    private void rollbackToInitialSnapshot() {
        StageSnapshot snapshot = bossFightSnapshot.getStageSnapshot();
        player.resetForCheckpoint(snapshot.getPlayerX(),
                                  snapshot.getPlayerY(),
                                  snapshot.getPlayerHp(),
                                  snapshot.getPlayerMana());
        player.startInvincibility(Config.PLAYER_SPAWN_PROTECTION_SECONDS);
        snapshot.restoreInventory(inventory);
        selectedInventorySlot = bossFightSnapshot.getSelectedInventorySlot();
        pickupItems.clear();
        pickupItems.addAll(snapshot.createPickupItems());
        goldPickups.clear();
        goldPickups.addAll(snapshot.createGoldPickups());
        bombs.clear();
        effectManager.clear();
        boss = createBoss();
        bossPlatformDropTimer = 0;
        SceneTransitionManager.setCurrentBossType(bossFightSnapshot.getBossType());
        SceneTransitionManager.setBossFightSnapshotActive(true);

        phase2Triggered = false;
        phase3Triggered = false;
        flashTimer = 0;
        phaseTextTimer = 0;
        phaseText = "";
        bossDeadTimer = 0;
        bossDropHandled = false;
        bossContactCooldown = 0;
        bossExitDoor = null;
        transitioning = false;
    }

    // ── 戰鬥判定 ─────────────────────────────────────────────────────────────

    /**
     * 判定玩家近戰攻擊是否命中 Boss。
     * 使用 player.getAttackBox() 取得攻擊框（不在攻擊狀態時為 null）。
     * 命中後 Boss 呼叫 takeDamage()，由 BossStateMachine.onHit() 處理扣血與狀態切換。
     */
    private void checkPlayerAttackVsBoss() {
        destroyHostileProjectilesInAttackRange();

        // canHit()：正在攻擊中且本次揮擊尚未命中過，確保每次 J 只造成一次傷害
        if (!player.canHit()) return;

        Rectangle2D atkBox = player.getAttackBox();
        if (atkBox == null) return;

        if (Collision.checkAABB(atkBox, boss.getHitbox()) && player.isAttackHitting(boss.getHitbox())) {
            boss.takeDamage(Player.ATTACK_DAMAGE);
            player.markHit();   // 標記命中，本次揮擊結束前不再傷害 Boss
        }
        for (Enemy minion : boss.getMinions()) {
            if (!minion.isAlive()) continue;
            if (Collision.checkAABB(atkBox, minion.getHitbox()) && player.isAttackHitting(minion.getHitbox())) {
                minion.takeDamage(Player.ATTACK_DAMAGE);
                player.markHit();
                break;
            }
        }
    }

    private void destroyHostileProjectilesInAttackRange() {
        Rectangle2D atkBox = player.getAttackBox();
        if (atkBox == null) return;

        for (Fireball projectile : boss.getProjectiles()) {
            destroyProjectileIfSlashed(atkBox, projectile);
        }
        for (Enemy minion : boss.getMinions()) {
            if (!(minion instanceof RangedEnemy ranged)) continue;
            for (Fireball projectile : ranged.getProjectiles()) {
                destroyProjectileIfSlashed(atkBox, projectile);
            }
        }
    }

    private void destroyProjectileIfSlashed(Rectangle2D atkBox, Fireball projectile) {
        if (!projectile.isAlive()) return;
        if (Collision.checkAABB(atkBox, projectile.getHitbox())
            && player.isAttackHitting(projectile.getHitbox())) {
            projectile.destroy();
        }
    }

    /**
     * 判定玩家火球是否命中 Boss、地板或平台。
     * 火球命中 Boss 後造成傷害並消失；撞到地形也會消失。
     */
    private void checkPlayerFireballsVsBoss() {
        for (Fireball fireball : player.getFireballs()) {
            if (!fireball.isAlive()) continue;

            if (boss.isAlive() && fireball.canHitTarget(boss)
                && Collision.checkAABB(fireball.getHitbox(), boss.getHitbox())) {
                boss.takeDamage(fireball.getDamage());
                fireball.markTargetHit(boss);
                if (!fireball.isPiercingEnemies()) {
                    fireball.destroy();
                    continue;
                }
            }

            for (Enemy minion : boss.getMinions()) {
                if (minion.isAlive() && fireball.canHitTarget(minion)
                    && Collision.checkAABB(fireball.getHitbox(), minion.getHitbox())) {
                    minion.takeDamage(fireball.getDamage());
                    fireball.markTargetHit(minion);
                    if (!fireball.isPiercingEnemies()) {
                        fireball.destroy();
                        break;
                    }
                }
            }
            if (!fireball.isAlive()) continue;

            destroyFireballOnWall(fireball);
        }
    }

    /**
     * 火球撞到 Boss 場景的地板或平台時消失。
     *
     * @param fireball 要檢查的火球
     */
    private void destroyFireballOnWall(Fireball fireball) {
        if (Collision.checkAABB(fireball.getHitbox(), ground)) {
            fireball.destroy();
            return;
        }

        if (!fireball.isPiercingEnemies()) {
            for (Rectangle2D platform : platforms) {
                if (Collision.checkAABB(fireball.getHitbox(), platform)) {
                    fireball.destroy();
                    return;
                }
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
     * 判定 Boss DASH 衝刺的攻擊框是否命中玩家。
     * Boss.getAttackBox() 在非 DASH 狀態回傳 null，因此不需要額外狀態判斷。
     * 衝刺命中造成 {@value #BOSS_DASH_DAMAGE} 點傷害。
     */
    private void checkBossDashVsPlayer() {
        Rectangle2D dashBox = boss.getAttackBox();
        if (dashBox == null) return;

        if (Collision.checkAABB(dashBox, player.getHitbox())) {
            player.takeDamage(BOSS_DASH_DAMAGE);
        }
    }

    /**
     * 逐一檢查 Boss 所有存活的投射物是否命中玩家。
     * 命中後呼叫 p.destroy() 標記消滅，Boss.update() 的 removeIf 會在下幀清除。
     * 傷害量由 Fireball.getDamage() 決定（目前 Boss 火球 15 點）。
     */
    private void checkBossProjectilesVsPlayer() {
        for (Fireball p : boss.getProjectiles()) {
            if (!p.isAlive()) continue;
            if (isFireballTouchingWall(p)) {
                p.destroy();
                continue;
            }
            if (Collision.checkAABB(p.getHitbox(), player.getHitbox())) {
                player.takeDamage(p.getDamage());
                p.destroy();   // 命中後消滅，下幀由 Boss.update() 清除
            }
        }
    }

    /**
     * 檢查火球是否撞到 Boss 場景地形。
     */
    private boolean isFireballTouchingWall(Fireball fireball) {
        if (Collision.checkAABB(fireball.getHitbox(), ground)) return true;

        if (!fireball.isPiercingEnemies()) {
            for (Rectangle2D platform : platforms) {
                if (Collision.checkAABB(fireball.getHitbox(), platform)) {
                    return true;
                }
            }
        }
        for (Rectangle2D cover : covers) {
            if (Collision.checkAABB(fireball.getHitbox(), cover)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判定 Boss 身體是否與玩家重疊並造成接觸傷害。
     * 適用於任何非 DEAD 狀態（Phase 1 的 CHASE / IDLE 都會觸發）。
     * 每次傷害有 0.8 秒冷卻，避免持續重疊時每幀連續扣血。
     */
    private void checkBossBodyContactVsPlayer() {
        if (boss.getCurrentState() == BossState.DEAD) return;
        if (bossContactCooldown > 0) return;
        if (Collision.checkAABB(boss.getHitbox(), player.getHitbox())) {
            player.takeDamage(BOSS_CONTACT_DAMAGE);
            bossContactCooldown = BOSS_CONTACT_COOLDOWN;
        }
    }

    /**
     * 玩家碰到 Boss 關道具時撿進背包。
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

    /**
     * Boss 死亡必掉至少一個稀有道具，並額外機率掉大藥水。
     */
    private void spawnBossDrops() {
        PickupType rare = random.nextBoolean() ? PickupType.FIRE_SCROLL : PickupType.ICE_SCROLL;
        addPickup(rare, boss.getX(), boss.getY() + boss.getHeight() - PickupItem.SIZE);

        if (random.nextDouble() < 0.65) {
            addPickup(PickupType.LARGE_POTION,
                        boss.getX() + PickupItem.SIZE + 6,
                        boss.getY() + boss.getHeight() - PickupItem.SIZE);
        }
        addGold(30 + random.nextInt(71),
                boss.getX() + PickupItem.SIZE * 2,
                boss.getY() + boss.getHeight() - GoldPickup.SIZE);
    }

    private void checkBossMinionsVsPlayer() {
        for (Enemy minion : boss.getMinions()) {
            if (!minion.isAlive()) continue;
            minion.tryDamagePlayer(player);
        }
    }

    private void checkBossMinionProjectilesVsPlayer() {
        for (Enemy minion : boss.getMinions()) {
            if (!(minion instanceof RangedEnemy ranged)) continue;
            for (Fireball projectile : ranged.getProjectiles()) {
                if (!projectile.isAlive()) continue;
                if (isFireballTouchingWall(projectile)) {
                    projectile.destroy();
                    continue;
                }
                if (Collision.checkAABB(projectile.getHitbox(), player.getHitbox())) {
                    player.takeDamage(projectile.getDamage());
                    projectile.destroy();
                }
            }
        }
    }

    private void checkPlayerIceProjectilesVsBoss() {
        for (IceProjectile projectile : player.getIceProjectiles()) {
            if (!projectile.isAlive()) continue;
            if (boss.isAlive() && projectile.canHitTarget(boss)
                && Collision.checkAABB(projectile.getHitbox(), boss.getHitbox())) {
                boss.applySlow(IceProjectile.BOSS_SLOW_DURATION, IceProjectile.BOSS_SLOW_MULTIPLIER);
                projectile.markTargetHit(boss);
            }

            for (Enemy minion : boss.getMinions()) {
                if (minion.isAlive() && projectile.canHitTarget(minion)
                    && Collision.checkAABB(projectile.getHitbox(), minion.getHitbox())) {
                    minion.applySlow(IceProjectile.SLOW_DURATION, IceProjectile.SLOW_MULTIPLIER);
                    projectile.markTargetHit(minion);
                }
            }
            if (projectile.isAlive()) destroyFireballOnWall(projectile);
        }
    }

    private void checkGroundSpikesVsPlayer() {
        for (GroundSpike spike : boss.getGroundSpikes()) {
            spike.tryHit(player);
        }
    }

    private void addPickup(PickupType type, double x, double y) {
        addPickup(type, x, y, 1);
    }

    private void addPickup(PickupType type, double x, double y, int quantity) {
        itemSpawnManager.setForbiddenZones(currentSpikeHazards());
        pickupItems.add(itemSpawnManager.spawn(type, x, y, quantity, pickupItems));
    }

    private void addGold(int amount, double x, double y) {
        goldSpawnManager.setForbiddenZones(currentSpikeHazards());
        goldPickups.add(goldSpawnManager.spawn(amount, x, y, pickupItems, goldPickups));
    }

    private List<Rectangle2D> currentSpikeHazards() {
        List<Rectangle2D> hazards = new ArrayList<>();
        for (GroundSpike spike : boss.getGroundSpikes()) {
            if (spike.isAlive()) {
                hazards.add(spike.getHitbox());
            }
        }
        return hazards;
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
            bomb.update(dt, ground, platforms, covers);
            if (bomb.shouldApplyDamage()) {
                applyBombDamage(bomb);
                bomb.markDamageApplied();
            }
        }
        if (boss.getCurrentState() == BossState.DEAD) {
            bombs.clear();
            return;
        }
        bombs.removeIf(bomb -> !bomb.isAlive());
    }

    private void applyBombDamage(BombEntity bomb) {
        if (boss.isAlive()) {
            double dx = boss.getX() + boss.getWidth() / 2.0 - bomb.getX();
            double dy = boss.getY() + boss.getHeight() / 2.0 - bomb.getY();
            if (Math.sqrt(dx * dx + dy * dy) <= BombEntity.RADIUS) {
                boss.takeDamage(BombEntity.DAMAGE);
            }
        }
        for (Enemy minion : boss.getMinions()) {
            if (!minion.isAlive()) continue;
            double dx = minion.getX() + minion.getWidth() / 2.0 - bomb.getX();
            double dy = minion.getY() + minion.getHeight() / 2.0 - bomb.getY();
            if (Math.sqrt(dx * dx + dy * dy) <= BombEntity.RADIUS) {
                minion.takeDamage(BombEntity.DAMAGE);
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

    // ── 階段管理 ─────────────────────────────────────────────────────────────

    /**
     * 檢查 Boss 血量是否首次跌破 60% 或 30%，觸發對應的階段視覺效果。
     * 每個閾值只觸發一次（phase2Triggered / phase3Triggered 旗標保護）。
     * 觸發效果：全螢幕紅色閃光 + 中央文字顯示 1 秒。
     */
    private void checkPhaseTransitions() {
        if (!boss.isAlive()) return;

        double ratio = (double) boss.getHp() / boss.getMaxHp();

        // Phase 2：血量首次跌破 60%
        if (!phase2Triggered && ratio < 0.60) {
            phase2Triggered = true;
            triggerPhaseAnnounce("PHASE 2");
        }

        // Phase 3：血量首次跌破 30%
        if (!phase3Triggered && ratio < 0.30) {
            phase3Triggered = true;
            triggerPhaseAnnounce("PHASE 3 - RAGE");
        }
    }

    /**
     * 啟動階段轉換的視覺效果：重置紅色閃光計時器與文字顯示計時器。
     *
     * @param text 要顯示在畫面中央的提示文字
     */
    private void triggerPhaseAnnounce(String text) {
        flashTimer     = FLASH_DURATION;
        phaseText      = text;
        phaseTextTimer = PHASE_TEXT_DURATION;
    }

    // ── render ────────────────────────────────────────────────────────────────

    /**
     * 每幀渲染，繪製順序：
     * <ol>
     *   <li>背景（暗紫色 Boss 氛圍）</li>
     *   <li>地板</li>
     *   <li>三個閃躲平台</li>
     *   <li>Boss（含投射物）</li>
     *   <li>玩家</li>
     *   <li>HUD（玩家血量條左上角 + Boss 血量條頂部中央）</li>
     *   <li>階段閃光疊加層（flashTimer > 0）</li>
     *   <li>階段提示文字（phaseTextTimer > 0）</li>
     *   <li>Boss 死亡勝利畫面 / 玩家死亡 GAME OVER 畫面</li>
     * </ol>
     *
     * @param gc 畫布繪圖上下文
     */
    private void render(GraphicsContext gc) {
        // 1. 背景（暗紫色，營造 Boss 戰氛圍）
        // TODO: 換成帶有光效的 Boss 戰背景圖片
        gc.setFill(Color.web("#1a0025"));
        gc.fillRect(0, 0, Config.WINDOW_WIDTH, Config.WINDOW_HEIGHT);

        // 2. 地板（TODO: 換成地面圖塊）
        gc.setFill(Color.web("#5a3a1a"));
        gc.fillRect(ground.getMinX(), ground.getMinY(),
                    ground.getWidth(), ground.getHeight());

        // 3. 閃躲平台（TODO: 換成石板 Tileset）
        drawPlatforms(gc);
        drawCovers(gc);

        // 4. 地板道具
        for (PickupItem item : pickupItems) {
            item.draw(gc);
        }

        for (GoldPickup gold : goldPickups) {
            gold.draw(gc);
        }

        for (BombEntity bomb : bombs) {
            bomb.draw(gc);
        }

        drawBossExitDoor(gc);

        // 5. Boss（含投射物、血量條頭頂版、狀態標籤）
        boss.draw(gc);

        // 6. 玩家（最後畫在最上層）
        player.draw(gc);
        effectManager.draw(gc);

        // 7. HUD（玩家左上角 + Boss 頂部中央）
        drawHUD(gc);
        flowController.drawDebugOverlay(gc, debugLines());

        // 7. 紅色閃光疊加層（階段轉換時）
        if (flashTimer > 0) {
            drawFlashOverlay(gc);
        }

        // 8. 階段提示文字
        if (phaseTextTimer > 0) {
            drawPhaseText(gc);
        }

        // 9. 結果畫面（勝利 / GAME OVER）
        if (!player.isAlive() && boss.getCurrentState() != BossState.DEAD) {
            drawGameOverOverlay(gc);
        }
        flowController.drawPauseOverlay(gc);
    }

    /**
     * 繪製三個閃躲平台（深灰石板色）。
     * TODO: 換成 Tileset 圖塊後移除此方法。
     *
     * @param gc 畫布繪圖上下文
     */
    private void drawPlatforms(GraphicsContext gc) {
        for (Rectangle2D p : platforms) {
            // 平台本體（深灰石板）
            gc.setFill(Color.web("#37474f"));
            gc.fillRect(p.getMinX(), p.getMinY(), p.getWidth(), p.getHeight());

            // 平台上緣較亮線條，增加立體感
            gc.setFill(Color.web("#546e7a"));
            gc.fillRect(p.getMinX(), p.getMinY(), p.getWidth(), 3);
        }
    }

    private void drawCovers(GraphicsContext gc) {
        for (Rectangle2D cover : covers) {
            gc.setFill(Color.web("#4e4a46"));
            gc.fillRect(cover.getMinX(), cover.getMinY(), cover.getWidth(), cover.getHeight());
            gc.setFill(Color.web("#74706a"));
            gc.fillRect(cover.getMinX(), cover.getMinY(), cover.getWidth(), 5);
        }
    }

    private void drawBossExitDoor(GraphicsContext gc) {
        if (bossExitDoor == null) return;
        gc.save();
        gc.setGlobalAlpha(0.35);
        gc.setFill(Color.GOLD);
        gc.fillRect(bossExitDoor.getMinX(), bossExitDoor.getMinY(),
                    bossExitDoor.getWidth(), bossExitDoor.getHeight());
        gc.restore();
        gc.setStroke(Color.GOLD);
        gc.setLineWidth(3);
        gc.strokeRect(bossExitDoor.getMinX(), bossExitDoor.getMinY(),
                      bossExitDoor.getWidth(), bossExitDoor.getHeight());
        if (Collision.checkAABB(player.getHitbox(), inflate(bossExitDoor, 14))) {
            gc.setFill(Color.WHITE);
            gc.setFont(Font.font(14));
            gc.fillText("Press Enter", bossExitDoor.getMinX() - 24, bossExitDoor.getMinY() - 12);
        }
    }

    /**
     * 繪製 HUD：
     * <ul>
     *   <li>左上角：玩家血量條（綠色）</li>
     *   <li>頂部中央：Boss 血量條（紅色）+ Boss 名稱</li>
     * </ul>
     *
     * @param gc 畫布繪圖上下文
     */
    private void drawHUD(GraphicsContext gc) {
        HudRenderer.drawPlayerStatus(gc, player, "BOSS");
        HudRenderer.drawGold(gc, Main.getGold());
        drawBossHpBar(gc);
        HudRenderer.drawInventorySlots(gc, inventory, selectedInventorySlot);
        PickupItem blockedItem = findBlockedPickupUnderPlayer();
        if (blockedItem != null) {
            HudRenderer.drawBackpackFullPrompt(gc, inventory, blockedItem);
        }
        if (inventoryOpen) {
            HudRenderer.drawInventoryOverlay(gc, inventory, selectedInventorySlot);
        }
    }

    /**
     * 在左上角繪製玩家血量條（寬 160px，高 14px）。
     * 顏色隨比例變化：60% 以上綠色、30-60% 橘色、30% 以下紅色。
     *
     * @param gc 畫布繪圖上下文
     */
    private void drawPlayerHpBar(GraphicsContext gc) {
        HudRenderer.drawPlayerStatus(gc, player, "BOSS");
    }

    /**
     * 在畫面頂部中央繪製 Boss 血量條（寬 500px，高 18px）。
     * 顏色隨比例變化：紅色（60%+）→ 橘紅（30-60%）→ 深紅（30%-），
     * 與玩家血量條顏色系統作區分。
     * Boss 名稱顯示於血量條正上方中央。
     *
     * @param gc 畫布繪圖上下文
     */
    private void drawBossHpBar(GraphicsContext gc) {
        BossUIManager.drawBossHealthBar(gc, boss);
    }

    /**
     * 繪製紅色全螢幕閃光疊加層（階段轉換時的視覺衝擊）。
     * Alpha 值隨 flashTimer 線性淡出：計時器越小則越透明。
     *
     * @param gc 畫布繪圖上下文
     */
    private void drawFlashOverlay(GraphicsContext gc) {
        gc.save();
        // 線性淡出：flashTimer 從 FLASH_DURATION 降至 0，alpha 從 0.5 降至 0
        gc.setGlobalAlpha(0.5 * (flashTimer / FLASH_DURATION));
        gc.setFill(Color.RED);
        gc.fillRect(0, 0, Config.WINDOW_WIDTH, Config.WINDOW_HEIGHT);
        gc.restore();
    }

    /**
     * 在畫面中央繪製階段提示文字（"PHASE 2" 或 "PHASE 3 - RAGE"）。
     * 顯示期間最後 0.3 秒會逐漸淡出，讓過渡更自然。
     *
     * @param gc 畫布繪圖上下文
     */
    private void drawPhaseText(GraphicsContext gc) {
        // 最後 0.3 秒線性淡出；其餘時間完全不透明
        double alpha = phaseTextTimer < 0.3 ? phaseTextTimer / 0.3 : 1.0;

        gc.save();
        gc.setGlobalAlpha(alpha);

        // 文字陰影（增強可讀性）
        gc.setFill(Color.BLACK);
        gc.setFont(Font.font(42));
        double approxW = phaseText.length() * 23.0;   // 粗估字寬
        double textX   = Config.WINDOW_WIDTH  / 2.0 - approxW / 2.0;
        double textY   = Config.WINDOW_HEIGHT / 2.0 - 20;
        gc.fillText(phaseText, textX + 2, textY + 2);   // 偏移 2px 作為陰影

        // 主文字（黃色）
        gc.setFill(Color.YELLOW);
        gc.fillText(phaseText, textX, textY);

        gc.restore();
    }

    /**
     * 繪製玩家死亡後的 GAME OVER 畫面。
     * 半透明黑色遮罩上顯示紅色大字，底部提示按 R 重試。
     *
     * @param gc 畫布繪圖上下文
     */
    private void drawGameOverOverlay(GraphicsContext gc) {
        // 半透明黑色遮罩
        gc.save();
        gc.setGlobalAlpha(0.65);
        gc.setFill(Color.BLACK);
        gc.fillRect(0, 0, Config.WINDOW_WIDTH, Config.WINDOW_HEIGHT);
        gc.restore();

        // 「GAME OVER」大字（紅色）
        gc.setFill(Color.RED);
        gc.setFont(Font.font(60));
        gc.fillText("GAME OVER",
                    Config.WINDOW_WIDTH / 2.0 - 173,
                    Config.WINDOW_HEIGHT / 2.0 - 20);

        // 重啟提示（白色小字）
        gc.setFill(Color.WHITE);
        gc.setFont(Font.font(20));
        gc.fillText("按 R 重新開始",
                    Config.WINDOW_WIDTH / 2.0 - 65,
                    Config.WINDOW_HEIGHT / 2.0 + 40);
    }

    private List<String> debugLines() {
        List<String> lines = new ArrayList<>();
        int aliveMinions = 0;
        for (Enemy minion : boss.getMinions()) {
            if (minion.isAlive()) aliveMinions++;
        }
        lines.add(String.format("FPS: %.0f", fps));
        lines.add(String.format("Player: %.1f, %.1f", player.getX(), player.getY()));
        lines.add(String.format("Velocity: %.1f, %.1f", player.getVelocityX(), player.getVelocityY()));
        lines.add("GameState: " + SceneTransitionManager.getCurrentGameState());
        lines.add("Transitioning: " + SceneTransitionManager.isTransitioning());
        lines.add("Stage: BOSS - " + boss.getDisplayName());
        lines.add("Current level: " + SceneTransitionManager.getCurrentLevel());
        lines.add("Boss type: " + SceneTransitionManager.getCurrentBossType());
        lines.add("Boss fight: " + SceneTransitionManager.getBossFightState());
        lines.add("Boss snapshot: " + SceneTransitionManager.hasBossFightSnapshot());
        lines.add(String.format("Portal cooldown: %.2f", SceneTransitionManager.getPortalCooldown()));
        lines.add("Active loops: " + SceneTransitionManager.getActiveGameLoopCount());
        lines.add("Scene: " + SceneTransitionManager.getCurrentSceneName());
        lines.add("Enemy count: " + aliveMinions);
        lines.add("Boss alive: " + (boss.isAlive() && !boss.isDeathHandled()));
        lines.add("Boss death handled: " + boss.isDeathHandled());
        lines.add("Projectiles: " + countProjectiles());
        lines.add("Effects: " + effectManager.getActiveEffectCount());
        lines.add("Inventory: " + debugInventorySlots());
        lines.add("Paused: " + flowController.isPaused());
        lines.add("Object count: " + (pickupItems.size() + goldPickups.size() + bombs.size()
            + aliveMinions + effectManager.getActiveEffectCount()));
        return lines;
    }

    private Rectangle2D inflate(Rectangle2D rect, double amount) {
        return new Rectangle2D(rect.getMinX() - amount, rect.getMinY() - amount,
            rect.getWidth() + amount * 2, rect.getHeight() + amount * 2);
    }

    private boolean isPortalEnterKey(KeyCode key) {
        return key == KeyCode.ENTER;
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
        int count = player.getFireballs().size() + player.getIceProjectiles().size()
            + boss.getProjectiles().size() + bombs.size();
        for (Enemy minion : boss.getMinions()) {
            if (minion instanceof RangedEnemy ranged) {
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
        if (boss != null) {
            boss.handleDeathCleanup();
        }
        BossUIManager.clearBossUI();
        bossExitDoor = null;
    }
}
