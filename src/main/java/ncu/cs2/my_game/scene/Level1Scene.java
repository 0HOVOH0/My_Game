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
import ncu.cs2.my_game.entity.Fireball;
import ncu.cs2.my_game.entity.Player;
import ncu.cs2.my_game.physics.Collision;

/**
 * 第一關場景，繼承 AnimationTimer 充當遊戲迴圈。
 *
 * <p>地圖設計：路線非直線，玩家需在 P3 往左跳才能繼續前進。
 * P4 是陷阱死路（看似向右延伸的正確路線），實際無法從 P4 到達終點。
 * 正確路線：P1→P2→P3→(往左)→P5→P6→(往右遠跳)→P7→P8→GOAL</p>
 *
 * <pre>
 * 平台配置示意（y 軸向下，數值為像素）：
 *
 *  y=150  ────────────────────[P8 終點]──[GOAL]
 *  y=165            [P7 ]
 *  y=200  [P6]
 *  y=275            [P5  ]
 *  y=355                 [P3]         [P4 死路]
 *  y=430            [P2]
 *  y=440                              [P4 ←死路]
 *  y=500  [P1]
 *  y=550  ====== GROUND ======
 * </pre>
 */
public class Level1Scene extends AnimationTimer {

    // ── 地圖常數 ─────────────────────────────────────────────────────────────

    /** 地板 Y 座標 */
    private static final double GROUND_Y = Config.WINDOW_HEIGHT - Config.GROUND_THICKNESS;

    /** 平台厚度（像素） */
    private static final double PLAT_H = 16.0;

    /** 終點門寬度 */
    private static final double GOAL_W = 50.0;

    /** 終點門高度 */
    private static final double GOAL_H = 150.0;

    // ── 欄位 ─────────────────────────────────────────────────────────────────

    /** 主視窗參考 */
    private final Stage stage;

    /** 繪圖用 GraphicsContext */
    private final GraphicsContext gc;

    /** 玩家實體 */
    private final Player player;

    /**
     * 地板碰撞框（橫跨整個畫面底部）。
     * TODO: 之後換成可捲動地圖時改為多段地板
     */
    private final Rectangle2D ground;

    /**
     * 8 個平台的碰撞框陣列。
     * TODO: 換成 Tiled 地圖格式後從檔案讀取座標
     */
    private final Rectangle2D[] platforms;

    /**
     * 終點門碰撞框，玩家碰到時切換至第二關。
     * TODO: 之後換成有開門動畫的精靈圖
     */
    private final Rectangle2D goalDoor;

    /** 上一幀的時間戳記（奈秒），用於計算 deltaTime；0 表示尚未初始化 */
    private long lastNano = 0;

    /** 是否已觸發場景切換，防止 handle() 重複呼叫 startLevel2 */
    private boolean transitioning = false;

    /** 玩家死亡後是否按下了 R 鍵，觸發重啟 Level1 */
    private boolean rKeyPressed = false;

    // ─────────────────────────────────────────────────────────────────────────

    /**
     * 建構子：初始化所有場景物件並立即啟動 AnimationTimer。
     * 建構完成後遊戲迴圈即開始運行，外部不需要額外呼叫。
     *
     * @param stage 主視窗，用於切換 JavaFX Scene
     */
    public Level1Scene(Stage stage) {
        this.stage = stage;

        // ── 建立 Canvas 與 JavaFX Scene ──────────────────────────────────────
        Canvas canvas = new Canvas(Config.WINDOW_WIDTH, Config.WINDOW_HEIGHT);
        gc = canvas.getGraphicsContext2D();

        Pane  root      = new Pane(canvas);
        Scene javafxScene = new Scene(root, Config.WINDOW_WIDTH, Config.WINDOW_HEIGHT);

        // ── 初始化玩家 ────────────────────────────────────────────────────────
        // 玩家出生在 P1 上方，會立即落下並踩到 P1（P1 頂面 y=500，玩家高 42）
        player = new Player(50, 440);

        // ── 定義地板 ──────────────────────────────────────────────────────────
        ground = new Rectangle2D(0, GROUND_Y, Config.WINDOW_WIDTH, Config.GROUND_THICKNESS);

        // ── 定義 8 個平台 ──────────────────────────────────────────────────────
        // 物理限制：最大跳躍高度 ≈ 127px，最大水平跳躍距離 ≈ 200px
        // 正確路線：P1→P2→P3→(往左跳)→P5→P6→(往右遠跳)→P7→P8→GOAL
        platforms = new Rectangle2D[] {
            // P1：起點平台，左側近地面
            new Rectangle2D( 20, 500, 130, PLAT_H),

            // P2：向右跳 100px、向上 70px
            new Rectangle2D(230, 430, 100, PLAT_H),

            // P3：向右跳 90px、向上 75px；到這裡玩家可能直覺往右走向 P4
            new Rectangle2D(420, 355, 100, PLAT_H),

            // P4：陷阱死路──從 P3 向右跳 50px、落下 85px 很容易到達，
            //     但此平台無法繼續前往終點（距 P8 高度差 >290px，遠超跳躍上限）
            new Rectangle2D(570, 440, 110, PLAT_H),

            // P5：關鍵轉折──必須從 P3 往左跳約 70px、向上 80px 才能抵達
            new Rectangle2D(240, 275, 110, PLAT_H),

            // P6：從 P5 往左跳 90px、向上 75px
            new Rectangle2D( 50, 200, 100, PLAT_H),

            // P7：從 P6 往右遠跳 130px、向上 35px（本關最難跳躍）
            new Rectangle2D(280, 165, 110, PLAT_H),

            // P8：從 P7 向右跳 130px、向上 15px；較寬以利停腳，終點門在右端
            new Rectangle2D(520, 150, 220, PLAT_H),
        };

        // ── 定義終點門 ────────────────────────────────────────────────────────
        // 位於 P8 右端正上方，玩家走到平台右側自然碰到
        goalDoor = new Rectangle2D(
            Config.WINDOW_WIDTH - GOAL_W - 10,  // x：貼近右牆
            150 - GOAL_H + PLAT_H,              // y：門底部對齊 P8 頂面
            GOAL_W, GOAL_H
        );

        // ── 綁定鍵盤事件 ──────────────────────────────────────────────────────
        javafxScene.setOnKeyPressed(e -> {
            player.handleKeyPressed(e.getCode());
            if (e.getCode() == KeyCode.R && !player.isAlive()) {
                rKeyPressed = true;
            }
        });
        javafxScene.setOnKeyReleased(e -> player.handleKeyReleased(e.getCode()));

        // ── 切換視窗顯示的場景並啟動遊戲迴圈 ────────────────────────────────
        stage.setScene(javafxScene);
        this.start();   // 呼叫 AnimationTimer.start()，開始觸發 handle()
    }

    // ── AnimationTimer 主迴圈 ─────────────────────────────────────────────────

    /**
     * 每幀由 JavaFX 呼叫；計算 deltaTime 後依序執行更新與渲染。
     * 第一幀（lastNano == 0）只記錄時間戳記不執行邏輯，避免 dt 異常大。
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
     * 每幀更新邏輯，執行順序：
     * <ol>
     *   <li>玩家物理（重力、速度、位移）</li>
     *   <li>地板與平台碰撞判定</li>
     *   <li>左右邊界限制</li>
     *   <li>終點門判定</li>
     * </ol>
     *
     * @param dt 時間差（秒）
     */
    private void update(double dt) {
        // 玩家死亡後等待 R 鍵重啟 Level1
        if (!player.isAlive()) {
            if (rKeyPressed && !transitioning) {
                rKeyPressed   = false;
                transitioning = true;
                this.stop();
                Main.startLevel1();   // startLevel1() 會重置血量與計時器
            }
            return;
        }

        // 1. 玩家物理更新（內含重力、鍵盤輸入、攻擊計時）
        player.update(dt);

        // 2. 碰撞判定：地板與所有平台
        resolvePlatformCollisions();

        // 3. 玩家左右邊界：不讓玩家走出畫面
        if (player.getX() < 0)
            player.setX(0);
        if (player.getX() + player.getWidth() > Config.WINDOW_WIDTH)
            player.setX(Config.WINDOW_WIDTH - player.getWidth());

        // 4. 火球碰到地板或平台後消失
        checkPlayerFireballsVsWalls();

        // 5. 終點門：碰到後停止迴圈並切換場景
        checkGoalDoor();
    }

    /**
     * 依序對地板與每個平台執行 checkPlatform() 碰撞解析。
     * 找到第一個碰撞的表面即停止，設定玩家 Y 座標並標記站地；
     * 若全部都沒碰到則標記為空中（允許下落）。
     *
     * <p>每幀最多解析一個表面，可避免同時踩兩塊平台邊緣的衝突。</p>
     */
    private void resolvePlatformCollisions() {
        // 先檢查地板
        if (Collision.checkPlatform(player, ground)) {
            player.setY(ground.getMinY() - player.getHeight());
            player.setOnGround(true);
            return;
        }

        // 再逐一檢查平台（依陣列順序，找到第一個命中即停止）
        for (Rectangle2D platform : platforms) {
            if (Collision.checkPlatform(player, platform)) {
                player.setY(platform.getMinY() - player.getHeight());
                player.setOnGround(true);
                return;
            }
        }

        // 沒有踩到任何表面：玩家在空中
        player.setOnGround(false);
    }

    /**
     * 檢查玩家是否碰到終點門。
     * 使用 checkAABB 而非 checkPlatform，因為終點門是側向觸碰而非從上方踩踏。
     * 觸碰後停止 AnimationTimer 並切換至下一關。
     */
    private void checkGoalDoor() {
        if (transitioning) return;

        if (Collision.checkAABB(player.getHitbox(), goalDoor)) {
            transitioning = true;
            Main.setPersistedHp(player.getHp());   // 帶入血量到 Level2
            this.stop();
            Main.startLevel2();
        }
    }

    /**
     * 檢查玩家火球是否撞到地板或平台。
     * Level1 沒有敵人，因此火球只需要處理牆面/地形消失。
     */
    private void checkPlayerFireballsVsWalls() {
        for (Fireball fireball : player.getFireballs()) {
            if (!fireball.isAlive()) continue;

            if (Collision.checkAABB(fireball.getHitbox(), ground)) {
                fireball.destroy();
                continue;
            }

            for (Rectangle2D platform : platforms) {
                if (Collision.checkAABB(fireball.getHitbox(), platform)) {
                    fireball.destroy();
                    break;
                }
            }
        }
    }

    // ── render ────────────────────────────────────────────────────────────────

    /**
     * 每幀渲染，繪製順序：
     * <ol>
     *   <li>背景（深灰色）</li>
     *   <li>地板</li>
     *   <li>所有平台</li>
     *   <li>終點門</li>
     *   <li>玩家</li>
     *   <li>HUD（左上角血量條）</li>
     * </ol>
     *
     * @param gc 畫布繪圖上下文
     */
    private void render(GraphicsContext gc) {
        // 1. 背景（深灰，TODO：之後換成視差捲動背景圖片）
        gc.setFill(Color.web("#2b2b2b"));
        gc.fillRect(0, 0, Config.WINDOW_WIDTH, Config.WINDOW_HEIGHT);

        // 2. 地板（TODO：之後換成地面圖塊 Tileset）
        gc.setFill(Color.web("#5a3a1a"));
        gc.fillRect(ground.getMinX(), ground.getMinY(),
                    ground.getWidth(), ground.getHeight());

        // 3. 所有平台（TODO：之後換成平台圖塊 Tileset）
        drawPlatforms(gc);

        // 4. 終點門（TODO：之後換成門的精靈圖與開門動畫）
        drawGoalDoor(gc);

        // 5. 玩家（含頭頂血量條與攻擊框）
        player.draw(gc);

        // 6. HUD：左上角血量條與標籤
        drawHUD(gc);

        // 7. 玩家死亡後疊加 GAME OVER 畫面
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
     * 繪製所有平台（綠色矩形）。
     * TODO: 換成 Tileset 圖塊後移除此方法，改用 TileRenderer。
     *
     * @param gc 畫布繪圖上下文
     */
    private void drawPlatforms(GraphicsContext gc) {
        for (Rectangle2D p : platforms) {
            // 平台本體（草地綠）
            gc.setFill(Color.web("#4caf50"));
            gc.fillRect(p.getMinX(), p.getMinY(), p.getWidth(), p.getHeight());

            // 平台上緣深色線條，增加立體感
            gc.setFill(Color.web("#2e7d32"));
            gc.fillRect(p.getMinX(), p.getMinY(), p.getWidth(), 3);
        }
    }

    /**
     * 繪製終點門（金色框 + 半透明填充）。
     * TODO: 換成精靈圖後加入閃爍光暈特效。
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
        gc.fillText("GOAL", goalDoor.getMinX() + 4, goalDoor.getMinY() - 6);
    }

    /**
     * 繪製左上角 HUD：血量條 + 數值文字。
     * 位置固定在畫面左上角，不隨玩家移動。
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

        // 血量條背景（深紅）
        gc.setFill(Color.web("#5a0000"));
        gc.fillRect(barX, barY, barW, barH);

        // 血量條前景（依比例變色）
        double ratio = (double) player.getHp() / player.getMaxHp();
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
        gc.fillText("LEVEL 1", Config.WINDOW_WIDTH / 2.0 - 25, 20);

        drawFireballCooldownHUD(gc);
    }

    /**
     * 繪製火球術冷卻。
     */
    private void drawFireballCooldownHUD(GraphicsContext gc) {
        gc.setFill(Color.WHITE);
        gc.setFont(Font.font(12));
        String text = player.canCastFireball()
            ? "Fireball: Ready"
            : "Fireball: " + String.format("%.1fs", player.getFireballCooldownTimer());
        gc.fillText(text, 12, 48);
    }
}
