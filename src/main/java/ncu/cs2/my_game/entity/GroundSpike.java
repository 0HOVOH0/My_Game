package ncu.cs2.my_game.entity;

import javafx.geometry.Rectangle2D;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

/**
 * 地刺 Boss 的延遲攻擊。先顯示紅色警告，再產生尖刺傷害。
 */
public class GroundSpike {

    private static final double WARNING_TIME = 0.8;
    private static final double ACTIVE_TIME = 0.35;
    public static final double WIDTH = 42.0;
    private static final double HEIGHT = 50.0;
    private static final int DAMAGE = 18;

    private final double x;
    private final double groundY;
    private double timer;
    private boolean hit;
    private boolean alive = true;

    public GroundSpike(double x, double groundY) {
        this.x = x;
        this.groundY = groundY;
    }

    public void update(double dt) {
        timer += dt;
        if (timer > WARNING_TIME + ACTIVE_TIME) alive = false;
    }

    public void draw(GraphicsContext gc) {
        if (!alive) return;
        if (timer < WARNING_TIME) {
            double alpha = 0.25 + 0.45 * Math.abs(Math.sin(timer * 16));
            gc.save();
            gc.setGlobalAlpha(alpha);
            gc.setFill(Color.RED);
            gc.fillOval(x, groundY - 10, WIDTH, 12);
            gc.restore();
            return;
        }

        gc.setFill(Color.DARKRED);
        double[] xs = {x, x + WIDTH / 2.0, x + WIDTH};
        double[] ys = {groundY, groundY - HEIGHT, groundY};
        gc.fillPolygon(xs, ys, 3);
        gc.setStroke(Color.ORANGE);
        gc.strokePolygon(xs, ys, 3);
    }

    public boolean tryHit(Player player) {
        if (hit || timer < WARNING_TIME) return false;
        if (getHitbox().intersects(player.getHitbox())) {
            hit = true;
            player.takeDamage(DAMAGE);
            return true;
        }
        return false;
    }

    public boolean isAlive() { return alive; }

    public Rectangle2D getHitbox() {
        return new Rectangle2D(x, groundY - HEIGHT, WIDTH, HEIGHT);
    }
}
