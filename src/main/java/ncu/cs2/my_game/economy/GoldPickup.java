package ncu.cs2.my_game.economy;

import javafx.geometry.Rectangle2D;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;

/**
 * 地板金幣掉落物。玩家碰到後直接加入 Gold，不進背包。
 */
public class GoldPickup {

    public static final double SIZE = 18.0;

    private static Image icon;

    private final double x;
    private final double y;
    private final int amount;
    private boolean pickedUp;

    public GoldPickup(double x, double y, int amount) {
        this.x = x;
        this.y = y;
        this.amount = Math.max(1, amount);
        this.pickedUp = false;
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
