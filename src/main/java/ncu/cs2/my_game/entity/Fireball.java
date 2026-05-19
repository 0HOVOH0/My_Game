package ncu.cs2.my_game.entity;

import javafx.geometry.Rectangle2D;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import ncu.cs2.my_game.Config;

/**
 * 玩家施放的火球投射物。
 * 依產生時的方向直線飛行，命中敵人、牆壁或離開地圖後由場景標記為消滅。
 */
public class Fireball {

    /** 火球尺寸（像素） */
    public static final double SIZE = Config.FIREBALL_SIZE;

    /** 火球飛行速度（像素 / 秒） */
    public static final double SPEED = Config.FIREBALL_SPEED;

    /** 命中敵人或 Boss 的傷害 */
    public static final int DAMAGE = Config.FIREBALL_DAMAGE;

    private double x;
    private double y;
    private final double dirX;
    private final double dirY;
    private final double size;
    private final double speed;
    private final int damage;
    private final Color fillColor;
    private final Color strokeColor;
    private boolean alive;

    /**
     * 建立火球。
     *
     * @param x    初始 X 座標
     * @param y    初始 Y 座標
     * @param dirX X 方向（-1、0、1）
     * @param dirY Y 方向（-1、0、1）
     */
    public Fireball(double x, double y, double dirX, double dirY) {
        this(x, y, dirX, dirY, SIZE, SPEED, DAMAGE, Color.ORANGERED, Color.YELLOW);
    }

    /**
     * 建立可調整外觀與傷害的火球，供 Boss 與道具共用。
     *
     * @param x           初始 X 座標
     * @param y           初始 Y 座標
     * @param dirX        X 方向
     * @param dirY        Y 方向
     * @param size        尺寸
     * @param speed       速度
     * @param damage      傷害
     * @param fillColor   主色
     * @param strokeColor 外框色
     */
    public Fireball(double x, double y, double dirX, double dirY,
                    double size, double speed, int damage,
                    Color fillColor, Color strokeColor) {
        this.x = x;
        this.y = y;
        double length = Math.sqrt(dirX * dirX + dirY * dirY);
        if (length == 0) {
            this.dirX = 1.0;
            this.dirY = 0.0;
        } else {
            this.dirX = dirX / length;
            this.dirY = dirY / length;
        }
        this.size = size;
        this.speed = speed;
        this.damage = damage;
        this.fillColor = fillColor;
        this.strokeColor = strokeColor;
        this.alive = true;
    }

    /**
     * 每幀沿指定方向移動；超出視窗範圍後自動消失。
     *
     * @param deltaTime 時間差（秒）
     */
    public void update(double deltaTime) {
        if (!alive) return;

        x += dirX * speed * deltaTime;
        y += dirY * speed * deltaTime;

        if (x + size < 0 || x > Config.WINDOW_WIDTH ||
            y + size < 0 || y > Config.WINDOW_HEIGHT) {
            alive = false;
        }
    }

    /**
     * 繪製火球，使用橘紅色圓形與亮色外框表示移動中的魔法彈。
     *
     * @param gc 畫布繪圖上下文
     */
    public void draw(GraphicsContext gc) {
        if (!alive) return;

        gc.save();
        gc.setFill(fillColor);
        gc.fillOval(x, y, size, size);
        gc.setStroke(strokeColor);
        gc.setLineWidth(2.0);
        gc.strokeOval(x, y, size, size);
        gc.restore();
    }

    /** 取得火球碰撞框 */
    public Rectangle2D getHitbox() {
        return new Rectangle2D(x, y, size, size);
    }

    /** 標記火球消失 */
    public void destroy() { alive = false; }

    /** 回傳火球是否仍存在 */
    public boolean isAlive() { return alive; }

    /** 回傳火球傷害 */
    public int getDamage() { return damage; }

    /** 回傳 X 座標 */
    public double getX() { return x; }

    /** 回傳 Y 座標 */
    public double getY() { return y; }

    /** 回傳尺寸 */
    public double getSize() { return size; }
}
