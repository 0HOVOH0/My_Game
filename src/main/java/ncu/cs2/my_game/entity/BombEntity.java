package ncu.cs2.my_game.entity;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

public class BombEntity {

    public static final double FUSE_TIME = 2.0;
    public static final double EXPLOSION_TIME = 0.35;
    public static final double RADIUS = 150.0;
    public static final int DAMAGE = 85;

    private final double x;
    private final double y;
    private double timer;
    private boolean damageApplied;
    private boolean alive = true;

    public BombEntity(double x, double y) {
        this.x = x;
        this.y = y;
    }

    public void update(double dt) {
        timer += dt;
        if (timer >= FUSE_TIME + EXPLOSION_TIME) alive = false;
    }

    public void draw(GraphicsContext gc) {
        if (!alive) return;
        gc.save();
        if (!hasExploded()) {
            double pulse = 1.0 + 0.18 * Math.abs(Math.sin(timer * 10.0));
            double size = 18.0 * pulse;
            gc.setFill(timer % 0.35 < 0.18 ? Color.DIMGRAY : Color.ORANGERED);
            gc.fillOval(x - size / 2.0, y - size / 2.0, size, size);
            gc.setStroke(Color.WHITE);
            gc.strokeOval(x - size / 2.0, y - size / 2.0, size, size);
        } else {
            double progress = Math.min(1.0, (timer - FUSE_TIME) / EXPLOSION_TIME);
            gc.setGlobalAlpha(0.45 * (1.0 - progress));
            gc.setFill(Color.ORANGE);
            gc.fillOval(x - RADIUS, y - RADIUS, RADIUS * 2, RADIUS * 2);
            gc.setStroke(Color.YELLOW);
            gc.setLineWidth(3);
            gc.strokeOval(x - RADIUS, y - RADIUS, RADIUS * 2, RADIUS * 2);
        }
        gc.restore();
    }

    public boolean shouldApplyDamage() {
        return hasExploded() && !damageApplied;
    }

    public void markDamageApplied() {
        damageApplied = true;
    }

    public boolean isAlive() { return alive; }

    public double getX() { return x; }

    public double getY() { return y; }

    private boolean hasExploded() {
        return timer >= FUSE_TIME;
    }
}
