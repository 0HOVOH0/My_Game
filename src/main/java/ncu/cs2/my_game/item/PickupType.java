package ncu.cs2.my_game.item;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;

/**
 * 可撿取道具種類。
 */
public enum PickupType {
    SMALL_POTION("小藥水", "Potion(S)", "/assets/items/potion_small.png") {
        @Override
        public PickupItem create(double x, double y, int quantity) {
            return new SmallPotionItem(x, y, quantity);
        }
    },
    LARGE_POTION("大藥水", "Potion(L)", "/assets/items/potion_large.png") {
        @Override
        public PickupItem create(double x, double y, int quantity) {
            return new LargePotionItem(x, y, quantity);
        }
    },
    FIRE_SCROLL("火焰卷軸", "Fire Scroll", "/assets/items/fire_scroll.png") {
        @Override
        public PickupItem create(double x, double y, int quantity) {
            return new FireScrollItem(x, y, quantity);
        }
    },
    BOMB("炸彈", "Bomb", "/assets/items/bomb.png") {
        @Override
        public PickupItem create(double x, double y, int quantity) {
            return new BombItem(x, y, quantity);
        }
    },
    ICE_SCROLL("冰凍卷軸", "Ice Scroll", "/assets/items/ice_scroll.png") {
        @Override
        public PickupItem create(double x, double y, int quantity) {
            return new IceScrollItem(x, y, quantity);
        }
    };

    private final String displayName;
    private final String hudLabel;
    private final String iconPath;
    private Image icon;

    PickupType(String displayName, String hudLabel, String iconPath) {
        this.displayName = displayName;
        this.hudLabel = hudLabel;
        this.iconPath = iconPath;
    }

    public String getDisplayName() { return displayName; }

    public String getHudLabel() { return hudLabel; }

    public PickupItem create(double x, double y) {
        return create(x, y, 1);
    }

    public abstract PickupItem create(double x, double y, int quantity);

    public void drawIcon(GraphicsContext gc, double x, double y, double size) {
        Image image = getIcon();
        if (image != null) {
            gc.drawImage(image, x, y, size, size);
            return;
        }

        gc.setFill(Color.web("#222222"));
        gc.fillRect(x, y, size, size);
        gc.setFill(Color.WHITE);
        gc.fillText(hudLabel.substring(0, 1), x + size * 0.38, y + size * 0.65);
    }

    private Image getIcon() {
        if (icon != null) return icon;
        try {
            var stream = PickupType.class.getResourceAsStream(iconPath);
            if (stream != null) icon = new Image(stream);
        } catch (RuntimeException ignored) {
            icon = null;
        }
        return icon;
    }
}
