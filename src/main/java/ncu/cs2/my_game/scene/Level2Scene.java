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
import ncu.cs2.my_game.entity.Enemy;
import ncu.cs2.my_game.entity.Player;
import ncu.cs2.my_game.physics.Collision;

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

    /** 終點門碰撞框；碰到後切換至 Boss 關 */
    private final Rectangle2D goalDoor;

    /**
     * 三個巡邏敵人，分別放置在 P2、P5、P7。
     * 血量歸零後 isAlive() 回傳 false，不再更新或繪製。
     */
    private final Enemy[] enemies;

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

    // ─────────────────────────────────────────────────────────────────────────

    /**
     * 建構子：初始化所有場景物件並立即啟動 AnimationTimer。
     *
     * @param stage 主視窗，用於切換 JavaFX Scene
     */
    public Level2Scene(Stage stage) {
        this.stage = stage;

        // ── 建立 Canvas 與 JavaFX Scene ──────────────────────────────────────
        Canvas canvas = new Canvas(Config.WINDOW_WIDTH, Config.WINDOW_HEIGHT);
        gc = canvas.getGraphicsContext2D();

        Pane  root        = new Pane(canvas);
        Scene javafxScene = new Scene(root, Config.WINDOW_WIDTH, Config.WINDOW_HEIGHT);

        // ── 初始化玩家（帶入 Level1 結束時的血量） ────────────────────────────
        player = new Player(50, 440);
        player.setHp(Main.getPersistedHp());

        // ── 地板 ──────────────────────────────────────────────────────────────
        ground = new Rectangle2D(0, GROUND_Y, Config.WINDOW_WIDTH, Config.GROUND_THICKNESS);

        // ── 平台（與第一關相同配置） ───────────────────────────────────────────
        // 正確路線：P1→P2→P3→(往左)→P5→P6→(往右遠跳)→P7→P8→BOSS
        platforms = new Rectangle2D[] {
            // P1：起點，左側
            new Rectangle2D( 20, 500, 130, PLAT_H),

            // P2：向右跳，有敵人巡邏
            new Rectangle2D(230, 430, 100, PLAT_H),

            // P3：向右跳，上有加血道具
            new Rectangle2D(420, 355, 100, PLAT_H),

            // P4：陷阱死路，從 P3 向右很容易到達但無法抵達終點
            new Rectangle2D(570, 440, 110, PLAT_H),

            // P5：關鍵轉折，必須從 P3 往左跳，有敵人巡邏
            new Rectangle2D(240, 275, 110, PLAT_H),

            // P6：從 P5 往左跳，向上攀登
            new Rectangle2D( 50, 200, 100, PLAT_H),

            // P7：從 P6 往右遠跳（最難），有敵人巡邏
            new Rectangle2D(280, 165, 110, PLAT_H),

            // P8：終點平台，較寬以利停腳
            new Rectangle2D(520, 150, 220, PLAT_H),
        };

        // ── 終點門 ────────────────────────────────────────────────────────────
        // 貼近右牆，門底部對齊 P8 頂面（y=150）
        goalDoor = new Rectangle2D(
            Config.WINDOW_WIDTH - GOAL_W - 10,  // x：貼近右牆
            150 - GOAL_H + PLAT_H,              // y：門底部對齊 P8 頂面
            GOAL_W, GOAL_H
        );

        // ── 敵人（每個敵人的巡邏範圍對應所在平台邊界） ───────────────────────
        // 巡邏右邊界 = 平台右端 - 敵人寬度，確保不會走出平台
        enemies = new Enemy[] {
            // 敵人1：在 P2（x=230..330）上巡邏，右邊界=330-28=302
            new Enemy(260, 430 - Enemy.ENEMY_H, 230, 302),

            // 敵人2：在 P5（x=240..350）上巡邏，右邊界=350-28=322
            new Enemy(270, 275 - Enemy.ENEMY_H, 240, 322),

            // 敵人3：在 P7（x=280..390）上巡邏，右邊界=390-28=362
            new Enemy(310, 165 - Enemy.ENEMY_H, 280, 362),
        };

        // ── 加血道具（放在 P3 中央偏右，玩家進到 P3 後容易看到）───────────────
        // P3 頂面 y=355，道具底部對齊平台：item.y = 355 - ITEM_SIZE = 335
        healthItem = new Rectangle2D(460, 355 - ITEM_SIZE, ITEM_SIZE, ITEM_SIZE);

        // ── 綁定鍵盤事件 ──────────────────────────────────────────────────────
        javafxScene.setOnKeyPressed(e -> {
            player.handleKeyPressed(e.getCode());
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
                transitioning = true;
                this.stop();
                Main.setPersistedHp(Config.PLAYER_MAX_HP);
                Main.startLevel2();
            }
            return;
        }

        // 1. 玩家物理更新
        player.update(dt);

        // 2. 存活敵人的物理更新
        for (Enemy enemy : enemies) {
            if (enemy.isAlive()) enemy.update(dt);
        }

        // 3. 玩家平台碰撞解析
        resolvePlayerPlatformCollisions();

        // 4. 每個存活敵人的平台碰撞解析
        resolveEnemyPlatformCollisions();

        // 5. 玩家左右邊界：不讓玩家走出畫面
        if (player.getX() < 0)
            player.setX(0);
        if (player.getX() + player.getWidth() > Config.WINDOW_WIDTH)
            player.setX(Config.WINDOW_WIDTH - player.getWidth());

        // 6. 玩家攻擊命中敵人
        checkPlayerAttackVsEnemies();

        // 7. 敵人接觸傷害玩家
        checkEnemyContactVsPlayer();

        // 8. 加血道具拾取
        checkHealthItem();

        // 9. 終點門判定
        checkGoalDoor();
    }

    /**
     * 解析玩家與地板、平台的碰撞。
     * 找到第一個碰撞面即停止，避免多平台邊緣衝突。
     */
    private void resolvePlayerPlatformCollisions() {
        // 先檢查地板
        if (Collision.checkPlatform(player, ground)) {
            player.setY(ground.getMinY() - player.getHeight());
            player.setOnGround(true);
            return;
        }

        // 再逐一檢查平台
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
     * 解析所有存活敵人與地板、平台的碰撞。
     * 每個敵人獨立解析，互不干擾。
     */
    private void resolveEnemyPlatformCollisions() {
        for (Enemy enemy : enemies) {
            if (!enemy.isAlive()) continue;

            // 先檢查地板
            if (Collision.checkPlatform(enemy, ground)) {
                enemy.setY(ground.getMinY() - enemy.getHeight());
                enemy.setOnGround(true);
                continue;   // 此敵人已解析，處理下一個
            }

            // 再逐一檢查平台
            boolean landed = false;
            for (Rectangle2D platform : platforms) {
                if (Collision.checkPlatform(enemy, platform)) {
                    enemy.setY(platform.getMinY() - enemy.getHeight());
                    enemy.setOnGround(true);
                    landed = true;
                    break;  // 每個敵人每幀只解析一個碰撞面
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
        // canHit()：本次揮擊尚未命中過任何目標，確保每次 J 只造成一次傷害
        if (!player.canHit()) return;

        Rectangle2D atkBox = player.getAttackBox();
        if (atkBox == null) return;

        for (Enemy enemy : enemies) {
            if (!enemy.isAlive()) continue;
            if (Collision.checkAABB(atkBox, enemy.getHitbox())) {
                enemy.takeDamage(Player.ATTACK_DAMAGE);
                // 命中第一個敵人後標記，本次揮擊不再傷害其他目標
                // 若需「貫穿」可移除 markHit() 改為多目標命中
                player.markHit();
                break;
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
            Main.setPersistedHp(player.getHp());   // 帶入血量到 Boss 關
            this.stop();
            Main.startBoss();
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
        gc.setFill(Color.web("#5a3a1a"));
        gc.fillRect(ground.getMinX(), ground.getMinY(),
                    ground.getWidth(), ground.getHeight());

        // 3. 所有平台（TODO: 換成 Tileset 圖塊）
        drawPlatforms(gc);

        // 4. 加血道具（TODO: 換成精靈圖）
        drawHealthItem(gc);

        // 5. 終點門（TODO: 換成門的精靈圖與開門動畫）
        drawGoalDoor(gc);

        // 6. 所有存活的敵人
        for (Enemy enemy : enemies) {
            enemy.draw(gc);
        }

        // 7. 玩家（最後繪製，顯示在最上層）
        player.draw(gc);

        // 8. HUD：左上角血量條
        drawHUD(gc);

        // 9. 玩家死亡後疊加 GAME OVER 畫面
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
     * 繪製終點門（金色框 + 半透明填充，標籤改為 "BOSS"）。
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

        // 門上方標籤
        gc.setFill(Color.YELLOW);
        gc.setFont(Font.font(13));
        gc.fillText("BOSS", goalDoor.getMinX() + 2, goalDoor.getMinY() - 6);
    }

    /**
     * 繪製左上角 HUD：血量條 + 數值文字 + 關卡標題。
     *
     * @param gc 畫布繪圖上下文
     */
    private void drawHUD(GraphicsContext gc) {
        final double barX = 12;
        final double barY = 12;
        final double barW = 160;
        final double barH = 14;

        // 標題文字
        gc.setFill(Color.LIGHTGRAY);
        gc.setFont(Font.font(11));
        gc.fillText("HP", barX, barY - 2);

        // 血量條背景（深紅底）
        gc.setFill(Color.web("#5a0000"));
        gc.fillRect(barX, barY, barW, barH);

        // 血量條前景（依比例變色）
        double ratio    = (double) player.getHp() / player.getMaxHp();
        Color  barColor = ratio > 0.6 ? Color.LIMEGREEN
                        : ratio > 0.3 ? Color.ORANGE
                                      : Color.RED;
        gc.setFill(barColor);
        gc.fillRect(barX, barY, barW * ratio, barH);

        // 血量數值文字
        gc.setFill(Color.WHITE);
        gc.setFont(Font.font(11));
        gc.fillText(player.getHp() + " / " + player.getMaxHp(),
                    barX + barW + 6, barY + 11);

        // 關卡標題
        gc.setFill(Color.LIGHTGRAY);
        gc.setFont(Font.font(12));
        gc.fillText("LEVEL 2", Config.WINDOW_WIDTH / 2.0 - 27, 20);
    }
}
