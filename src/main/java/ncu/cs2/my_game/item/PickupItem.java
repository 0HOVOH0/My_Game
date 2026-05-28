package ncu.cs2.my_game.item;

import javafx.geometry.Rectangle2D;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import ncu.cs2.my_game.Config;

/**
 * 地板道具基底類別。
 * 子類別只負責定義外觀、名稱與使用效果。
 */
public abstract class PickupItem {

    public static final double SIZE = 28.0;

    private double x;
    private double y;
    private final PickupType type;
    private final int quantity;
    private boolean pickedUp;
    private boolean falling;
    private double velocityY;

    protected PickupItem(double x, double y, PickupType type) {
        this(x, y, type, 1);
    }

    protected PickupItem(double x, double y, PickupType type, int quantity) {
        this.x = x;
        this.y = y;
        this.type = type;
        this.quantity = Math.max(1, quantity);
        this.pickedUp = false;
        this.falling = false;
        this.velocityY = 0;
    }

    public abstract void use(UseContext context);

    public boolean canUse(UseContext context) {
        return true;
    }

    protected abstract Color getFillColor();

    protected abstract String getSymbol();

    public void draw(GraphicsContext gc) {
        if (pickedUp) return;

        gc.save();
        type.drawIcon(gc, x, y, SIZE);
        if (quantity > 1) {
            gc.setFill(Color.web("#111111", 0.78));
            gc.fillRoundRect(x + SIZE - 13, y + SIZE - 10, 14, 10, 4, 4);
            gc.setFill(Color.WHITE);
            gc.fillText(String.valueOf(quantity), x + SIZE - 10, y + SIZE - 2);
        }
        gc.restore();
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

    public Rectangle2D getHitbox() {
        return new Rectangle2D(x, y, SIZE, SIZE);
    }

    public PickupType getType() { return type; }

    public int getQuantity() { return quantity; }

    public boolean isPickedUp() { return pickedUp; }

    public void markPickedUp() { pickedUp = true; }

    public double getX() { return x; }

    public double getY() { return y; }
}
