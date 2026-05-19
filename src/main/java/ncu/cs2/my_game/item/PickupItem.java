package ncu.cs2.my_game.item;

import javafx.geometry.Rectangle2D;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

/**
 * 地板道具基底類別。
 * 子類別只負責定義外觀、名稱與使用效果。
 */
public abstract class PickupItem {

    public static final double SIZE = 22.0;

    private final double x;
    private final double y;
    private final PickupType type;
    private boolean pickedUp;

    protected PickupItem(double x, double y, PickupType type) {
        this.x = x;
        this.y = y;
        this.type = type;
        this.pickedUp = false;
    }

    public abstract void use(UseContext context);

    protected abstract Color getFillColor();

    protected abstract String getSymbol();

    public void draw(GraphicsContext gc) {
        if (pickedUp) return;

        gc.save();
        gc.setFill(Color.WHITE);
        gc.fillRect(x - 1, y - 1, SIZE + 2, SIZE + 2);
        gc.setFill(getFillColor());
        gc.fillRect(x, y, SIZE, SIZE);
        gc.setFill(Color.WHITE);
        gc.fillText(getSymbol(), x + 6, y + 15);
        gc.restore();
    }

    public Rectangle2D getHitbox() {
        return new Rectangle2D(x, y, SIZE, SIZE);
    }

    public PickupType getType() { return type; }

    public boolean isPickedUp() { return pickedUp; }

    public void markPickedUp() { pickedUp = true; }

    public double getX() { return x; }

    public double getY() { return y; }
}
