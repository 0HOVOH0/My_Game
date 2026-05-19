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
import ncu.cs2.my_game.entity.Boss;
import ncu.cs2.my_game.entity.Fireball;
import ncu.cs2.my_game.entity.Player;
import ncu.cs2.my_game.fsm.BossState;
import ncu.cs2.my_game.item.Inventory;
import ncu.cs2.my_game.item.PickupItem;
import ncu.cs2.my_game.item.PickupType;
import ncu.cs2.my_game.item.UseContext;
import ncu.cs2.my_game.physics.Collision;
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
    private static final int BOSS_DASH_DAMAGE = 10;

    /** Boss 身體接觸玩家的傷害值（Phase 1 起生效） */
    private static final int BOSS_CONTACT_DAMAGE = 6;

    /** Boss 身體接觸傷害的冷卻時間（秒） */
    private static final double BOSS_CONTACT_COOLDOWN = 0.8;

    // ── 欄位 ─────────────────────────────────────────────────────────────────

    /** 主視窗參考 */
    private final Stage stage;

    /** 繪圖用 GraphicsContext */
    private final GraphicsContext gc;

    /** 玩家實體 */
    private final Player player;

    /** Boss 實體（AI 由 BossStateMachine 驅動） */
    private Boss boss;

    /** 跨關卡背包 */
    private final Inventory inventory;

    /** Boss 關可撿取道具 */
    private final List<PickupItem> pickupItems;

    /** 掉落用亂數 */
    private final Random random;

    /** Boss 關剛進入時的狀態快照 */
    private final StageSnapshot initialSnapshot;

    /** 地板碰撞框（橫跨整個畫面底部） */
    private final Rectangle2D ground;

    /**
     * 三個小平台的碰撞框陣列，供玩家跳躍閃躲用。
     * Boss 不與平台互動，只與地板碰撞。
     */
    private final Rectangle2D[] platforms;

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

        // ── 初始化玩家（帶入 Level2 結束時的血量） ────────────────────────────
        player = new Player(70, 460);
        player.setHp(Main.getPersistedHp());
        player.setMana(Main.getPersistedMana());

        // ── 初始化 Boss（右側登場，落到地面，FSM 起始 IDLE 1 秒） ─────────────
        boss = new Boss(700, 340, player);
        inventory = Main.getInventory();
        pickupItems = new ArrayList<>();
        random = new Random();

        // ── 地板 ──────────────────────────────────────────────────────────────
        ground = new Rectangle2D(0, GROUND_Y, Config.WINDOW_WIDTH, Config.GROUND_THICKNESS);

        // ── 三個閃躲用平台 ────────────────────────────────────────────────────
        platforms = new Rectangle2D[] {
            // P1：左側低台，玩家初期可站此處保持距離
            new Rectangle2D( 30, 420, 110, PLAT_H),

            // P2：中央高台，可讓玩家從上方攻擊 Boss
            new Rectangle2D(345, 340, 110, PLAT_H),

            // P3：右側低台，靠近 Boss 登場位置
            new Rectangle2D(620, 420, 110, PLAT_H),
        };

        initialSnapshot = new StageSnapshot(
            player.getX(), player.getY(), player.getHp(), player.getMana(),
            inventory, pickupItems, new ArrayList<>(),
            false, 0, 0, 0, 0
        );

        // ── 鍵盤事件 ──────────────────────────────────────────────────────────
        javafxScene.setOnKeyPressed(e -> {
            // 正常移動輸入轉發給玩家
            player.handleKeyPressed(e.getCode());
            handleInventoryKey(e.getCode());

            // 玩家死亡時按 R 重啟本關
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

        // 計算距上一幀的時間差（秒），上限 0.05s 防止大幀跳躍
        double dt = (now - lastNano) / 1_000_000_000.0;
        lastNano = now;
        if (dt > Config.MAX_DELTA_TIME) dt = Config.MAX_DELTA_TIME;

        update(dt);
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
            if (!bossDropHandled) {
                bossDropHandled = true;
                spawnBossDrops();
            }

            bossDeadTimer += dt;

            // 仍更新視覺計時器，讓閃光與文字正常淡出
            if (flashTimer    > 0) flashTimer    -= dt;
            if (phaseTextTimer > 0) phaseTextTimer -= dt;

            // 等待 WIN_DELAY 秒後切換至結算畫面
            if (bossDeadTimer >= WIN_DELAY && !transitioning) {
                transitioning = true;
                this.stop();
                Main.startEnd();
            }
            return;
        }

        // ── 分支 2：玩家死亡，等待重啟 ───────────────────────────────────────
        if (!player.isAlive()) {
            if (rKeyPressed) {
                rKeyPressed   = false;
                rollbackToInitialSnapshot();
            }
            return;
        }

        // ── 分支 3：正常遊戲邏輯 ─────────────────────────────────────────────

        // 1. 玩家物理更新（重力、輸入、攻擊計時）
        player.update(dt);

        // 2. Boss 物理更新（FSM 決定速度、重力、投射物）
        boss.update(dt);

        // 3. 玩家平台碰撞解析
        resolvePlayerPlatformCollisions();

        // 4. Boss 地板碰撞解析（Boss 只與地板互動，不踩平台）
        resolveBossGround();

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
        checkBossDashVsPlayer();
        checkBossProjectilesVsPlayer();
        checkBossBodyContactVsPlayer();
        checkPickupItems();

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
        // 先檢查地板
        if (Collision.checkPlatform(player, ground)) {
            player.setY(ground.getMinY() - player.getHeight());
            player.setOnGround(true);
            return;
        }

        // 再逐一檢查三個平台
        for (Rectangle2D platform : platforms) {
            if (Collision.checkPlatform(player, platform)) {
                player.setY(platform.getMinY() - player.getHeight());
                player.setOnGround(true);
                return;
            }
        }

        // 沒踩到任何表面：玩家在空中
        player.setOnGround(false);
    }

    /**
     * 解析 Boss 與地板的碰撞。
     * Boss 只與地板互動（不踩平台），落地後清除垂直速度。
     */
    private void resolveBossGround() {
        if (Collision.checkPlatform(boss, ground)) {
            boss.setY(ground.getMinY() - boss.getHeight());
            boss.setVelocityY(0);
        }
    }

    /**
     * 使用背包快捷鍵。
     */
    private void handleInventoryKey(KeyCode key) {
        PickupType type = switch (key) {
            case DIGIT1 -> PickupType.SMALL_POTION;
            case DIGIT2 -> PickupType.LARGE_POTION;
            case DIGIT3 -> PickupType.FIRE_SCROLL;
            case DIGIT4 -> PickupType.BOMB;
            case DIGIT5 -> PickupType.ICE_SCROLL;
            default -> null;
        };
        if (type != null) {
            inventory.use(type, new UseContext(player, null, boss));
        }
    }

    /**
     * 死亡重生時回滾到剛進入 Boss 關的狀態。
     */
    private void rollbackToInitialSnapshot() {
        player.resetForCheckpoint(initialSnapshot.getPlayerX(),
                                  initialSnapshot.getPlayerY(),
                                  initialSnapshot.getPlayerHp(),
                                  initialSnapshot.getPlayerMana());
        initialSnapshot.restoreInventory(inventory);
        pickupItems.clear();
        pickupItems.addAll(initialSnapshot.createPickupItems());
        boss = new Boss(700, 340, player);

        phase2Triggered = false;
        phase3Triggered = false;
        flashTimer = 0;
        phaseTextTimer = 0;
        phaseText = "";
        bossDeadTimer = 0;
        bossDropHandled = false;
        bossContactCooldown = 0;
        transitioning = false;
    }

    // ── 戰鬥判定 ─────────────────────────────────────────────────────────────

    /**
     * 判定玩家近戰攻擊是否命中 Boss。
     * 使用 player.getAttackBox() 取得攻擊框（不在攻擊狀態時為 null）。
     * 命中後 Boss 呼叫 takeDamage()，由 BossStateMachine.onHit() 處理扣血與狀態切換。
     */
    private void checkPlayerAttackVsBoss() {
        // canHit()：正在攻擊中且本次揮擊尚未命中過，確保每次 J 只造成一次傷害
        if (!player.canHit()) return;

        Rectangle2D atkBox = player.getAttackBox();
        if (atkBox == null) return;

        if (Collision.checkAABB(atkBox, boss.getHitbox())) {
            boss.takeDamage(Player.ATTACK_DAMAGE);
            player.markHit();   // 標記命中，本次揮擊結束前不再傷害 Boss
        }
    }

    /**
     * 判定玩家火球是否命中 Boss、地板或平台。
     * 火球命中 Boss 後造成傷害並消失；撞到地形也會消失。
     */
    private void checkPlayerFireballsVsBoss() {
        for (Fireball fireball : player.getFireballs()) {
            if (!fireball.isAlive()) continue;

            if (boss.isAlive() && Collision.checkAABB(fireball.getHitbox(), boss.getHitbox())) {
                boss.takeDamage(fireball.getDamage());
                fireball.destroy();
                continue;
            }

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

        for (Rectangle2D platform : platforms) {
            if (Collision.checkAABB(fireball.getHitbox(), platform)) {
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

        for (Rectangle2D platform : platforms) {
            if (Collision.checkAABB(fireball.getHitbox(), platform)) {
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
                inventory.add(item.getType());
                item.markPickedUp();
            }
        }
        pickupItems.removeIf(PickupItem::isPickedUp);
    }

    /**
     * Boss 死亡必掉至少一個稀有道具，並額外機率掉大藥水。
     */
    private void spawnBossDrops() {
        PickupType rare = random.nextBoolean() ? PickupType.FIRE_SCROLL : PickupType.ICE_SCROLL;
        spawnPickup(rare, boss.getX(), boss.getY() + boss.getHeight() - PickupItem.SIZE);

        if (random.nextDouble() < 0.65) {
            spawnPickup(PickupType.LARGE_POTION,
                        boss.getX() + PickupItem.SIZE + 6,
                        boss.getY() + boss.getHeight() - PickupItem.SIZE);
        }
    }

    private void spawnPickup(PickupType type, double x, double y) {
        double px = Math.max(0, Math.min(x, Config.WINDOW_WIDTH - PickupItem.SIZE));
        double py = Math.max(0, Math.min(y, Config.WINDOW_HEIGHT - Config.GROUND_THICKNESS - PickupItem.SIZE));
        pickupItems.add(type.create(px, py));
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

        // 4. 地板道具
        for (PickupItem item : pickupItems) {
            item.draw(gc);
        }

        // 5. Boss（含投射物、血量條頭頂版、狀態標籤）
        boss.draw(gc);

        // 6. 玩家（最後畫在最上層）
        player.draw(gc);

        // 7. HUD（玩家左上角 + Boss 頂部中央）
        drawHUD(gc);

        // 7. 紅色閃光疊加層（階段轉換時）
        if (flashTimer > 0) {
            drawFlashOverlay(gc);
        }

        // 8. 階段提示文字
        if (phaseTextTimer > 0) {
            drawPhaseText(gc);
        }

        // 9. 結果畫面（勝利 / GAME OVER）
        if (boss.getCurrentState() == BossState.DEAD) {
            drawVictoryOverlay(gc);
        } else if (!player.isAlive()) {
            drawGameOverOverlay(gc);
        }
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
        drawBossHpBar(gc);
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
        final double barX = (Config.WINDOW_WIDTH - BOSS_BAR_W) / 2.0;   // 水平置中
        final double barY = 82;

        // Boss 名稱（TODO: 換成設計好的字型與角色名稱）
        gc.setFill(Color.LIGHTGRAY);
        gc.setFont(Font.font(11));
        gc.fillText("DARK OVERLORD",
                    barX + BOSS_BAR_W / 2.0 - 44,
                    barY - 2);

        // 血量條背景（深紅底）
        gc.setFill(Color.web("#4a0000"));
        gc.fillRect(barX, barY, BOSS_BAR_W, BOSS_BAR_H);

        // 血量條前景（紅色系，以區別於玩家的綠色系）
        double ratio    = (double) boss.getHp() / boss.getMaxHp();
        Color  barColor = ratio > 0.6 ? Color.web("#e53935")    // 鮮紅
                        : ratio > 0.3 ? Color.web("#ff6d00")    // 橘紅
                                      : Color.web("#b71c1c");   // 暗紅（危險）
        gc.setFill(barColor);
        gc.fillRect(barX, barY, BOSS_BAR_W * ratio, BOSS_BAR_H);

        // 血量條外框
        gc.setStroke(Color.web("#7f0000"));
        gc.setLineWidth(1.5);
        gc.strokeRect(barX, barY, BOSS_BAR_W, BOSS_BAR_H);

        // 血量數值文字（置於血量條右側）
        gc.setFill(Color.LIGHTGRAY);
        gc.setFont(Font.font(11));
        gc.fillText(boss.getHp() + " / " + boss.getMaxHp(),
                    barX + BOSS_BAR_W + 6, barY + 13);

        drawInventoryHUD(gc);
    }

    /**
     * 依 Inventory/PickupType 自動繪製背包數量。
     */
    private void drawInventoryHUD(GraphicsContext gc) {
        gc.setFill(Color.WHITE);
        gc.setFont(Font.font(11));
        double x = 12;
        double y = Config.WINDOW_HEIGHT - 62;
        for (PickupType type : inventory.getDisplayTypes()) {
            gc.fillText(type.getHudLabel() + ": " + inventory.getCount(type), x, y);
            y += 12;
        }
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
     * 繪製 Boss 死亡後的勝利畫面（"YOU WIN!"）。
     * 在切換至 EndScene 前的 WIN_DELAY 秒內持續顯示。
     *
     * @param gc 畫布繪圖上下文
     */
    private void drawVictoryOverlay(GraphicsContext gc) {
        // 半透明金色光暈
        gc.save();
        gc.setGlobalAlpha(0.25);
        gc.setFill(Color.GOLD);
        gc.fillRect(0, 0, Config.WINDOW_WIDTH, Config.WINDOW_HEIGHT);
        gc.restore();

        // 主文字
        gc.setFill(Color.GOLD);
        gc.setFont(Font.font(64));
        gc.fillText("YOU WIN!",
                    Config.WINDOW_WIDTH / 2.0 - 165,
                    Config.WINDOW_HEIGHT / 2.0);
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
}
