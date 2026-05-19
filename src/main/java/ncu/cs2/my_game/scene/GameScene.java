package ncu.cs2.my_game.scene;

import javafx.animation.AnimationTimer;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import ncu.cs2.my_game.Config;
import ncu.cs2.my_game.entity.Boss;
import ncu.cs2.my_game.entity.Player;
import ncu.cs2.my_game.fsm.BossState;
import ncu.cs2.my_game.physics.Collision;

/**
 * 遊戲主場景：Canvas 渲染、遊戲迴圈、玩家 / Boss / 投射物的碰撞判定。
 */
public class GameScene {

    /** 主視窗參考 */
    private final Stage stage;

    /** 遊戲迴圈 */
    private AnimationTimer gameLoop;

    /** 玩家實體 */
    private Player player;

    /** Boss 實體 */
    private Boss boss;

    /** 地板碰撞框（靜態） */
    private Rectangle2D ground;

    // ─────────────────────────────────────────────────────────────────────────

    public GameScene(Stage stage) {
        this.stage = stage;
    }

    /**
     * 初始化所有物件並啟動遊戲迴圈。
     */
    public void start() {
        Canvas canvas = new Canvas(Config.WINDOW_WIDTH, Config.WINDOW_HEIGHT);
        Pane   root   = new Pane(canvas);
        Scene  scene  = new Scene(root, Config.WINDOW_WIDTH, Config.WINDOW_HEIGHT);

        // 初始化玩家與 Boss（Boss 需要 Player 參照供 FSM 追蹤）
        player = new Player(100, 300);
        boss   = new Boss(580, 300, player);

        // 地板：畫面底部 50px
        ground = new Rectangle2D(0, Config.WINDOW_HEIGHT - 50,
                                 Config.WINDOW_WIDTH, 50);

        // 鍵盤事件轉發給玩家
        scene.setOnKeyPressed(e  -> player.handleKeyPressed(e.getCode()));
        scene.setOnKeyReleased(e -> player.handleKeyReleased(e.getCode()));

        stage.setScene(scene);

        // 啟動遊戲迴圈
        GraphicsContext gc       = canvas.getGraphicsContext2D();
        long[]          lastNano = {System.nanoTime()};

        gameLoop = new AnimationTimer() {
            @Override
            public void handle(long now) {
                double dt = (now - lastNano[0]) / 1_000_000_000.0;
                lastNano[0] = now;
                if (dt > 0.05) dt = 0.05;   // 防止大幀跳躍

                update(dt);
                render(gc);
            }
        };
        gameLoop.start();
    }

    // ── update ────────────────────────────────────────────────────────────────

    /**
     * 每幀更新順序：
     * 1. 玩家 / Boss 物理
     * 2. 地板碰撞
     * 3. 玩家邊界限制
     * 4. 玩家攻擊 → 命中 Boss
     * 5. Boss DASH 攻擊 → 命中玩家
     * 6. Boss 投射物 → 命中玩家
     */
    private void update(double dt) {
        // ── 1. 物理更新 ────────────────────────────────────────────────────────
        player.update(dt);
        if (boss.isAlive()) boss.update(dt);

        // ── 2. 地板碰撞 ────────────────────────────────────────────────────────
        resolveGround(player);
        if (boss.isAlive()) resolveGroundBoss();

        // ── 3. 玩家左右邊界 ────────────────────────────────────────────────────
        if (player.getX() < 0)
            player.setX(0);
        if (player.getX() + player.getWidth() > Config.WINDOW_WIDTH)
            player.setX(Config.WINDOW_WIDTH - player.getWidth());

        if (!boss.isAlive()) return;   // Boss 已死則跳過後續戰鬥判定

        // ── 4. 玩家近戰攻擊命中 Boss ───────────────────────────────────────────
        Rectangle2D playerAtk = player.getAttackBox();
        if (playerAtk != null &&
                Collision.checkAABB(playerAtk, boss.getHitbox())) {
            boss.takeDamage(Player.ATTACK_DAMAGE);
        }

        // ── 5. Boss DASH 衝刺命中玩家 ─────────────────────────────────────────
        Rectangle2D dashBox = boss.getAttackBox();
        if (dashBox != null &&
                Collision.checkAABB(dashBox, player.getHitbox())) {
            player.takeDamage(30);   // 衝刺傷害固定 30
        }

        // ── 6. Boss 投射物命中玩家 ─────────────────────────────────────────────
        for (Boss.Projectile p : boss.getProjectiles()) {
            if (!p.isAlive()) continue;
            if (Collision.checkAABB(p.getHitbox(), player.getHitbox())) {
                player.takeDamage(p.getDamage());
                p.destroy();   // 命中後消滅，下幀由 Boss.update() 清除
            }
        }
    }

    /**
     * 解析玩家與地板的碰撞：落地時固定 Y 座標並通知玩家站地狀態。
     *
     * @param p 要判定的玩家
     */
    private void resolveGround(Player p) {
        if (Collision.checkPlatform(p, ground)) {
            p.setY(ground.getMinY() - p.getHeight());
            p.setOnGround(true);
        } else {
            p.setOnGround(false);
        }
    }

    /**
     * 解析 Boss 與地板的碰撞：落地時固定 Y 並清除垂直速度。
     * Boss 不需要 isOnGround 狀態，只需停在地板上不繼續下落。
     */
    private void resolveGroundBoss() {
        if (Collision.checkPlatform(boss, ground)) {
            boss.setY(ground.getMinY() - boss.getHeight());
            boss.setVelocityY(0);
        }
    }

    // ── render ────────────────────────────────────────────────────────────────

    /**
     * 每幀渲染：背景 → 地板 → Boss → 玩家 → 勝負畫面。
     *
     * @param gc 畫布繪圖上下文
     */
    private void render(GraphicsContext gc) {
        // 清空背景
        gc.setFill(Color.SKYBLUE);
        gc.fillRect(0, 0, Config.WINDOW_WIDTH, Config.WINDOW_HEIGHT);

        // 地板
        gc.setFill(Color.SADDLEBROWN);
        gc.fillRect(ground.getMinX(), ground.getMinY(),
                    ground.getWidth(), ground.getHeight());

        // Boss（含投射物）
        boss.draw(gc);

        // 玩家（最後畫，顯示在最上層）
        player.draw(gc);

        // 勝負提示
        drawOutcome(gc);
    }

    /**
     * 玩家或 Boss 死亡時顯示結果文字。
     *
     * @param gc 畫布繪圖上下文
     */
    private void drawOutcome(GraphicsContext gc) {
        if (boss.getCurrentState() == BossState.DEAD) {
            drawCenteredText(gc, "YOU WIN!", Color.GOLD, 64);
        } else if (!player.isAlive()) {
            drawCenteredText(gc, "GAME OVER", Color.RED, 64);
        }
    }

    /**
     * 在畫面中央繪製大字文字。
     *
     * @param gc    畫布繪圖上下文
     * @param text  要顯示的文字
     * @param color 文字顏色
     * @param size  字體大小（px）
     */
    private void drawCenteredText(GraphicsContext gc, String text,
                                  Color color, double size) {
        gc.setFill(color);
        gc.setFont(javafx.scene.text.Font.font(size));
        // 粗估字寬置中（實際寬度需 TextLayout，這裡用近似值）
        double approxW = text.length() * size * 0.55;
        gc.fillText(text,
                    (Config.WINDOW_WIDTH  - approxW) / 2.0,
                    (Config.WINDOW_HEIGHT) / 2.0);
    }

    /**
     * 停止遊戲迴圈（切換回主選單時呼叫）。
     */
    public void stop() {
        if (gameLoop != null) gameLoop.stop();
    }
}
