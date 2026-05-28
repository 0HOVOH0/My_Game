package ncu.cs2.my_game.item;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.image.PixelReader;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;

import java.util.ArrayDeque;

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

    public boolean isPotion() {
        return this == SMALL_POTION || this == LARGE_POTION;
    }

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
            if (stream != null) icon = removeLightEdgeFrame(new Image(stream));
        } catch (RuntimeException ignored) {
            icon = null;
        }
        return icon;
    }

    /**
     * Older item sprites include a white tile-card around the object. Remove only
     * pale pixels connected to the image boundary, preserving white pixels inside
     * the potion cross, fuse highlights, or scroll art.
     */
    private static Image removeLightEdgeFrame(Image source) {
        int width = (int) source.getWidth();
        int height = (int) source.getHeight();
        if (width <= 0 || height <= 0) return source;

        PixelReader reader = source.getPixelReader();
        WritableImage cleaned = new WritableImage(reader, width, height);
        PixelWriter writer = cleaned.getPixelWriter();
        boolean[][] removed = new boolean[height][width];
        ArrayDeque<int[]> queue = new ArrayDeque<>();

        for (int x = 0; x < width; x++) {
            enqueueLightEdge(reader, removed, queue, x, 0);
            enqueueLightEdge(reader, removed, queue, x, height - 1);
        }
        for (int y = 0; y < height; y++) {
            enqueueLightEdge(reader, removed, queue, 0, y);
            enqueueLightEdge(reader, removed, queue, width - 1, y);
        }
        while (!queue.isEmpty()) {
            int[] pixel = queue.remove();
            int x = pixel[0];
            int y = pixel[1];
            writer.setColor(x, y, Color.TRANSPARENT);
            if (x > 0) enqueueLightEdge(reader, removed, queue, x - 1, y);
            if (x + 1 < width) enqueueLightEdge(reader, removed, queue, x + 1, y);
            if (y > 0) enqueueLightEdge(reader, removed, queue, x, y - 1);
            if (y + 1 < height) enqueueLightEdge(reader, removed, queue, x, y + 1);
        }
        return cleaned;
    }

    private static void enqueueLightEdge(PixelReader reader, boolean[][] removed,
                                         ArrayDeque<int[]> queue, int x, int y) {
        if (removed[y][x]) return;
        Color color = reader.getColor(x, y);
        double spread = Math.max(color.getRed(), Math.max(color.getGreen(), color.getBlue()))
            - Math.min(color.getRed(), Math.min(color.getGreen(), color.getBlue()));
        boolean paleNeutral = color.getOpacity() > 0.01
            && color.getBrightness() > 0.72 && spread < 0.16;
        if (paleNeutral) {
            removed[y][x] = true;
            queue.add(new int[] { x, y });
        }
    }
}
