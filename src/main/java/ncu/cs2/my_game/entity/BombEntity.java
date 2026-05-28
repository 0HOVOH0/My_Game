package ncu.cs2.my_game.entity;

import javafx.geometry.Rectangle2D;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import ncu.cs2.my_game.Config;

public class BombEntity {

    public static final double FUSE_TIME = 2.0;
    public static final double EXPLOSION_TIME = 0.35;
    public static final double RADIUS = 150.0;
    public static final int DAMAGE = 85;

    private static final double SIZE = 22.0;
    private static final double AIR_DRAG = 0.94;

    private double x;
    private double y;
    private double velocityX;
    private double velocityY;
    private double timer;
    private boolean landed;
    private boolean damageApplied;
    private boolean alive = true;

    public BombEntity(double x, double y) {
        this(x, y, 0, 0);
    }

    public BombEntity(double x, double y, double velocityX, double velocityY) {
        this.x = x;
        this.y = y;
        this.velocityX = velocityX;
        this.velocityY = velocityY;
    }

    public void update(double dt) {
        if (!landed) {
            velocityY += Config.GRAVITY * dt;
            velocityX *= Math.pow(AIR_DRAG, dt * 60.0);
            x += velocityX * dt;
            y += velocityY * dt;
            return;
        }

        timer += dt;
        if (timer >= FUSE_TIME + EXPLOSION_TIME) alive = false;
    }

    public void update(double dt, Rectangle2D ground, Rectangle2D[] platforms, Rectangle2D[] covers) {
        double oldX = x;
        double oldY = y;
        update(dt);
        if (landed || hasExploded()) return;

        if (tryLandOn(ground)) return;
        if (platforms != null) {
            for (Rectangle2D platform : platforms) {
                if (platform != null && tryLandOn(platform)) return;
            }
        }
        if (covers != null) {
            for (Rectangle2D cover : covers) {
                if (cover != null && tryLandOn(cover)) return;
                if (cover != null && resolveWallBounce(cover, oldX, oldY)) return;
            }
        }
    }

    private boolean tryLandOn(Rectangle2D surface) {
        if (velocityY < 0) return false;
        Rectangle2D hitbox = getHitbox();
        boolean overlapX = hitbox.getMaxX() > surface.getMinX()
            && hitbox.getMinX() < surface.getMaxX();
        boolean touchingTop = hitbox.getMaxY() >= surface.getMinY()
            && hitbox.getMaxY() <= surface.getMinY() + Math.max(10.0, surface.getHeight());
        if (!overlapX || !touchingTop) return false;

        y = surface.getMinY() - SIZE / 2.0;
        velocityX = 0;
        velocityY = 0;
        landed = true;
        timer = 0;
        return true;
    }

    private boolean resolveWallBounce(Rectangle2D wall, double oldX, double oldY) {
        Rectangle2D hitbox = getHitbox();
        if (!hitbox.intersects(wall)) return false;

        Rectangle2D oldHitbox = new Rectangle2D(oldX - SIZE / 2.0, oldY - SIZE / 2.0, SIZE, SIZE);
        if (oldHitbox.getMaxX() <= wall.getMinX()) {
            x = wall.getMinX() - SIZE / 2.0 - 0.5;
            velocityX = -Math.abs(velocityX) * 0.25;
        } else if (oldHitbox.getMinX() >= wall.getMaxX()) {
            x = wall.getMaxX() + SIZE / 2.0 + 0.5;
            velocityX = Math.abs(velocityX) * 0.25;
        } else if (oldHitbox.getMinY() >= wall.getMaxY()) {
            y = wall.getMaxY() + SIZE / 2.0 + 0.5;
            velocityY = Math.max(40.0, Math.abs(velocityY) * 0.2);
        } else {
            y = wall.getMinY() - SIZE / 2.0;
            velocityX = 0;
            velocityY = 0;
            landed = true;
            timer = 0;
        }
        return true;
    }

    public void draw(GraphicsContext gc) {
        if (!alive) return;
        gc.save();
        if (!hasExploded()) {
            double pulse = landed ? 1.0 + 0.18 * Math.abs(Math.sin(timer * 10.0)) : 1.0;
            double size = SIZE * pulse;
            gc.setFill(landed && timer % 0.35 < 0.18 ? Color.web("#151515") : Color.web("#050505"));
            gc.fillOval(x - size / 2.0, y - size / 2.0, size, size);
            gc.setFill(Color.web("#3b3b3b"));
            gc.fillOval(x - size * 0.22, y - size * 0.34, size * 0.25, size * 0.22);
            gc.setStroke(Color.web("#d6c49a"));
            gc.setLineWidth(2);
            gc.strokeLine(x + size * 0.20, y - size * 0.35, x + size * 0.42, y - size * 0.62);
            gc.setFill(Color.ORANGE);
            gc.fillOval(x + size * 0.36, y - size * 0.72, size * 0.22, size * 0.22);
            gc.setStroke(Color.web("#dddddd"));
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

    private Rectangle2D getHitbox() {
        return new Rectangle2D(x - SIZE / 2.0, y - SIZE / 2.0, SIZE, SIZE);
    }

    private boolean hasExploded() {
        return landed && timer >= FUSE_TIME;
    }
}
