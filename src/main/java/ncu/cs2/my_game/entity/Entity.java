package ncu.cs2.my_game.entity;

import javafx.geometry.Rectangle2D;
import javafx.scene.canvas.GraphicsContext;

/**
 * 所有遊戲實體的抽象基底類別。
 * 玩家、Boss、投射物等皆應繼承此類別。
 */
public abstract class Entity {

    // ── 位置 ─────────────────────────────────────────────────────────────────

    /** 實體左上角的 X 座標（像素） */
    protected double x;

    /** 實體左上角的 Y 座標（像素） */
    protected double y;

    // ── 速度 ─────────────────────────────────────────────────────────────────

    /** 水平速度（像素 / 秒），正值向右 */
    protected double velocityX;

    /** 垂直速度（像素 / 秒），正值向下 */
    protected double velocityY;

    // ── 尺寸 ─────────────────────────────────────────────────────────────────

    /** 碰撞框寬度（像素） */
    protected double width;

    /** 碰撞框高度（像素） */
    protected double height;

    // ── 血量 ─────────────────────────────────────────────────────────────────

    /** 目前血量 */
    protected int hp;

    /** 最大血量 */
    protected int maxHp;

    // ─────────────────────────────────────────────────────────────────────────

    /**
     * 建構子：設定初始位置、尺寸與血量。
     *
     * @param x     初始 X 座標
     * @param y     初始 Y 座標
     * @param width  碰撞框寬度
     * @param height 碰撞框高度
     * @param maxHp  最大血量（初始血量同此值）
     */
    protected Entity(double x, double y, double width, double height, int maxHp) {
        this.x       = x;
        this.y       = y;
        this.width   = width;
        this.height  = height;
        this.maxHp   = maxHp;
        this.hp      = maxHp;
        this.velocityX = 0;
        this.velocityY = 0;
    }

    // ── 抽象方法（子類別必須實作） ────────────────────────────────────────────

    /**
     * 每幀更新實體狀態（移動、物理、AI 等）。
     *
     * @param deltaTime 距上一幀的時間差（秒）
     */
    public abstract void update(double deltaTime);

    /**
     * 將實體繪製到畫布上。
     *
     * @param gc 畫布的 GraphicsContext，用於 2D 繪圖
     */
    public abstract void draw(GraphicsContext gc);

    // ── 一般方法 ──────────────────────────────────────────────────────────────

    /**
     * 取得實體的軸對齊碰撞框（AABB）。
     *
     * @return 以 (x, y, width, height) 定義的矩形碰撞框
     */
    public Rectangle2D getHitbox() {
        return new Rectangle2D(x, y, width, height);
    }

    /**
     * 判斷實體是否仍存活。
     *
     * @return hp > 0 時回傳 {@code true}
     */
    public boolean isAlive() {
        return hp > 0;
    }

    // ── Getter ────────────────────────────────────────────────────────────────

    /** 回傳目前 X 座標 */
    public double getX() { return x; }

    /** 回傳目前 Y 座標 */
    public double getY() { return y; }

    /** 回傳水平速度 */
    public double getVelocityX() { return velocityX; }

    /** 回傳垂直速度 */
    public double getVelocityY() { return velocityY; }

    /** 回傳碰撞框寬度 */
    public double getWidth() { return width; }

    /** 回傳碰撞框高度 */
    public double getHeight() { return height; }

    /** 回傳目前血量 */
    public int getHp() { return hp; }

    /** 回傳最大血量 */
    public int getMaxHp() { return maxHp; }

    // ── Setter ────────────────────────────────────────────────────────────────

    /** 設定 X 座標 */
    public void setX(double x) { this.x = x; }

    /** 設定 Y 座標 */
    public void setY(double y) { this.y = y; }

    /** 設定水平速度 */
    public void setVelocityX(double velocityX) { this.velocityX = velocityX; }

    /** 設定垂直速度 */
    public void setVelocityY(double velocityY) { this.velocityY = velocityY; }

    /**
     * 設定血量，自動限制在 [0, maxHp] 範圍內。
     *
     * @param hp 新的血量值
     */
    public void setHp(int hp) {
        this.hp = Math.max(0, Math.min(hp, maxHp));
    }

    // TODO: 加入 takeDamage(int amount)、heal(int amount) 等便利方法
}
