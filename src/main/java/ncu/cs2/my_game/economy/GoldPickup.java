package ncu.cs2.my_game.economy;

import javafx.geometry.Rectangle2D;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import ncu.cs2.my_game.Config;

/**
 * 地板金幣掉落物。玩家碰到後直接加入 Gold，不進背包。
 */
public class GoldPickup {

    public static final double SIZE = 18.0;

    private static Image icon;

    private double x;
    private double y;
    private final int amount;
    private boolean pickedUp;
    private boolean falling;
    private double velocityY;

    public GoldPickup(double x, double y, int amount) {
        this.x = x;
        this.y = y;
        this.amount = Math.max(1, amount);
        this.pickedUp = false;
        this.falling = false;
        this.velocityY = 0;
    }

    public void draw(GraphicsContext gc) {
        if (pickedUp) return;

        gc.save();
        Image image = getIcon();
        if (image != null) {
            gc.drawImage(image, x, y, SIZE, SIZE);
        } else {
            gc.setFill(Color.GOLD);
            gc.fillOval(x, y, SIZE, SIZE);
            gc.setStroke(Color.ORANGE);
            gc.strokeOval(x, y, SIZE, SIZE);
        }
        gc.setFill(Color.WHITE);
        gc.fillText(String.valueOf(amount), x + SIZE - 3, y + SIZE + 9);
        gc.restore();
    }

    public Rectangle2D getHitbox() {
        return new Rectangle2D(x, y, SIZE, SIZE);
    }

    public void beginFall() {
        falling = true;
        velocityY = 0;
    }

    public void updateFalling(double dt, Rectangle2D ground, Rectangle2D[] platforms,
                              Rectangle2D[] solidObstacles) {
        if (!falling || pickedUp) return;
        double oldBottom = y + SIZE;
        velocityY += Config.GRAVITY * dt;
        y += velocityY * dt;
        if (landOn(ground, oldBottom)) return;
        if (platforms != null) {
            for (Rectangle2D platform : platforms) {
                if (landOn(platform, oldBottom)) return;
            }
        }
        if (solidObstacles != null) {
            for (Rectangle2D obstacle : solidObstacles) {
                if (landOn(obstacle, oldBottom)) return;
            }
        }
    }

    private boolean landOn(Rectangle2D surface, double oldBottom) {
        if (surface == null || velocityY < 0) return false;
        boolean overSurface = x + SIZE > surface.getMinX() && x < surface.getMaxX();
        if (!overSurface || oldBottom > surface.getMinY() + 1.0
                || y + SIZE < surface.getMinY()) return false;
        y = surface.getMinY() - SIZE;
        velocityY = 0;
        falling = false;
        return true;
    }

    public int getAmount() {
        return amount;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public boolean isPickedUp() {
        return pickedUp;
    }

    public void markPickedUp() {
        pickedUp = true;
    }

    private Image getIcon() {
        if (icon != null) return icon;
        try {
            var stream = GoldPickup.class.getResourceAsStream("/assets/items/coin.png");
            if (stream != null) icon = new Image(stream);
        } catch (RuntimeException ignored) {
            icon = null;
        }
        return icon;
    }
}
