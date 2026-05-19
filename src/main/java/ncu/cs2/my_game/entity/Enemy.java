package ncu.cs2.my_game.entity;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import ncu.cs2.my_game.physics.Gravity;

/**
 * 普通敵人實體，繼承 Entity。
 * AI 行為：在指定的巡邏範圍內來回走動，碰到邊界自動轉向。
 * 與玩家接觸時造成傷害（附帶冷卻），被玩家攻擊命中則扣血，血量歸零後消失。
 */
public class Enemy extends Entity {

    // ── 常數 ─────────────────────────────────────────────────────────────────

    /** 碰撞框寬度（像素） */
    public static final double ENEMY_W = 28.0;

    /** 碰撞框高度（像素） */
    public static final double ENEMY_H = 36.0;

    /** 最大血量 */
    public static final int ENEMY_MAX_HP = 40;

    /** 接觸玩家造成的傷害量 */
    public static final int CONTACT_DAMAGE = 10;

    /** 接觸傷害冷卻時間（秒）；避免每幀都觸發傷害 */
    public static final double DAMAGE_COOLDOWN = 0.5;

    /** 巡邏移動速度（像素 / 秒） */
    private static final double PATROL_SPEED = 80.0;

    // ── 巡邏邊界 ─────────────────────────────────────────────────────────────

    /**
     * 巡邏左邊界 X 座標（通常設為所在平台左端）。
     * 敵人 X 小於此值時轉為向右走。
     */
    private final double patrolLeft;

    /**
     * 巡邏右邊界 X 座標（通常設為平台右端減去敵人寬度）。
     * 敵人 X 大於此值時轉為向左走。
     */
    private final double patrolRight;

    // ── 狀態 ─────────────────────────────────────────────────────────────────

    /** 移動方向：+1.0 = 向右，-1.0 = 向左 */
    private double moveDir;

    /**
     * 接觸傷害冷卻計時器（秒）。
     * 大於 0 時不對玩家施加傷害；每幀遞減至 0。
     */
    private double damageCooldown;

    /** 冰凍/緩速剩餘時間（秒） */
    private double slowTimer;

    /** 目前速度倍率，1.0 表示正常速度 */
    private double speedMultiplier;

    // ─────────────────────────────────────────────────────────────────────────

    /**
     * 建構子：設定初始位置與巡邏範圍，初始向右走。
     *
     * @param x           初始 X 座標（左上角）
     * @param y           初始 Y 座標，建議設為平台頂面 - ENEMY_H
     * @param patrolLeft  巡邏左邊界 X（對應平台左端）
     * @param patrolRight 巡邏右邊界 X（對應平台右端 - ENEMY_W）
     */
    public Enemy(double x, double y, double patrolLeft, double patrolRight) {
        super(x, y, ENEMY_W, ENEMY_H, ENEMY_MAX_HP);
        this.patrolLeft  = patrolLeft;
        this.patrolRight = patrolRight;
        this.moveDir     = 1.0;   // 初始向右
        this.damageCooldown = 0;
        this.slowTimer = 0;
        this.speedMultiplier = 1.0;
    }

    // ── update ────────────────────────────────────────────────────────────────

    /**
     * 每幀更新：套用重力、水平巡邏移動、邊界轉向、傷害冷卻計時。
     * 死亡後不再更新任何狀態。
     *
     * @param deltaTime 時間差（秒）
     */
    @Override
    public void update(double deltaTime) {
        // 死亡後停止所有更新
        if (!isAlive()) return;

        // 1. 套用重力（累加 velocityY）
        Gravity.apply(this, deltaTime);

        // 2. 設定水平速度並更新位置
        velocityX = moveDir * PATROL_SPEED * speedMultiplier;
        x += velocityX * deltaTime;
        y += velocityY * deltaTime;

        // 3. 到達巡邏邊界時原地修正並反向
        if (x <= patrolLeft) {
            x       = patrolLeft;
            moveDir = 1.0;   // 轉向右
        } else if (x + width >= patrolRight) {
            x       = patrolRight - width;
            moveDir = -1.0;  // 轉向左
        }

        // 4. 傷害冷卻計時器遞減
        if (damageCooldown > 0) damageCooldown -= deltaTime;

        // 5. 緩速計時器遞減
        if (slowTimer > 0) {
            slowTimer -= deltaTime;
            if (slowTimer <= 0) {
                slowTimer = 0;
                speedMultiplier = 1.0;
            }
        }
    }

    // ── 受傷 ─────────────────────────────────────────────────────────────────

    /**
     * 承受玩家攻擊傷害；直接扣血，無無敵時間（敵人設計不需要）。
     *
     * @param amount 傷害量（正整數）
     */
    public void takeDamage(int amount) {
        setHp(hp - amount);
    }

    /**
     * 暫時降低敵人巡邏速度。
     *
     * @param duration   持續時間（秒）
     * @param multiplier 速度倍率，0.5 表示半速
     */
    public void applySlow(double duration, double multiplier) {
        slowTimer = Math.max(slowTimer, duration);
        speedMultiplier = Math.min(speedMultiplier, multiplier);
    }

    // ── 接觸傷害 ─────────────────────────────────────────────────────────────

    /**
     * 嘗試對玩家施加接觸傷害。
     * 冷卻計時器歸零前不重複傷害，防止每幀連續扣血。
     * 傷害實際是否生效由 Player.takeDamage() 的無敵機制決定。
     *
     * @param player 要攻擊的玩家
     * @return 本次是否觸發了傷害呼叫（冷卻中回傳 false）
     */
    public boolean tryDamagePlayer(Player player) {
        if (damageCooldown > 0) return false;

        player.takeDamage(CONTACT_DAMAGE);
        damageCooldown = DAMAGE_COOLDOWN;   // 重置冷卻
        return true;
    }

    // ── 地面狀態 ─────────────────────────────────────────────────────────────

    /**
     * 由平台碰撞解析呼叫：落地時清除垂直速度，防止持續累積下落速度。
     *
     * @param onGround true = 已踩到平台；false = 在空中（不做任何事）
     */
    public void setOnGround(boolean onGround) {
        if (onGround && velocityY > 0) velocityY = 0;
    }

    // ── draw ─────────────────────────────────────────────────────────────────

    /**
     * 繪製敵人本體（磚紅色矩形）、面向眼睛，以及頭頂血量條。
     * 死亡後不繪製任何內容，自然從畫面消失。
     *
     * @param gc 畫布繪圖上下文
     */
    @Override
    public void draw(GraphicsContext gc) {
        // 死亡後不渲染
        if (!isAlive()) return;

        // 本體（磚紅色）
        // TODO: 換成敵人精靈圖動畫
        gc.setFill(slowTimer > 0 ? Color.LIGHTBLUE : Color.FIREBRICK);
        gc.fillRect(x, y, width, height);

        // 眼睛指示移動方向（往右眼睛靠右，往左眼睛靠左）
        double eyeX = moveDir > 0 ? x + width - 11 : x + 4;
        gc.setFill(Color.WHITE);
        gc.fillOval(eyeX, y + 7, 7, 7);
        gc.setFill(Color.BLACK);
        gc.fillOval(eyeX + 1.5, y + 8.5, 4, 4);

        // 頭頂血量條
        drawHpBar(gc);
    }

    /**
     * 在敵人頭頂上方繪製小型血量條。
     *
     * @param gc 畫布繪圖上下文
     */
    private void drawHpBar(GraphicsContext gc) {
        final double barH = 4;
        final double barY = y - barH - 3;

        // 背景（深紅底）
        gc.setFill(Color.DARKRED);
        gc.fillRect(x, barY, width, barH);

        // 剩餘血量前景（綠色）
        gc.setFill(Color.LIMEGREEN);
        gc.fillRect(x, barY, width * ((double) hp / maxHp), barH);
    }
}
