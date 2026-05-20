package ncu.cs2.my_game.scene;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import ncu.cs2.my_game.Config;
import ncu.cs2.my_game.entity.Player;
import ncu.cs2.my_game.item.Inventory;
import ncu.cs2.my_game.item.InventorySlot;
import ncu.cs2.my_game.item.PickupItem;
import ncu.cs2.my_game.item.PickupType;

/** 共用玩家 HUD 繪製工具。 */
public final class HudRenderer {

    private static final String[] SLOT_KEYS = {"U", "I", "O"};
    private static Image coinIcon;

    private HudRenderer() {}

    public static void drawPlayerStatus(GraphicsContext gc, Player player, String title) {
        drawBar(gc, "HP", 12, 12, 160, 14,
                (double) player.getHp() / player.getMaxHp(),
                player.getHp() + " / " + player.getMaxHp(),
                Color.LIMEGREEN, Color.ORANGE, Color.RED);

        drawBar(gc, "Mana", 12, 38, 160, 12,
                player.getMana() / player.getMaxMana(),
                String.format("%.0f / %.0f", player.getMana(), player.getMaxMana()),
                Color.DEEPSKYBLUE, Color.DODGERBLUE, Color.ROYALBLUE);

        drawFireballIcon(gc, player, 12, 62);

        gc.setFill(Color.LIGHTGRAY);
        gc.setFont(Font.font(12));
        gc.fillText(title, Config.WINDOW_WIDTH / 2.0 - title.length() * 3.6, 20);
    }

    public static void drawInventorySlots(GraphicsContext gc, Inventory inventory,
                                          int selectedSlot) {
        double x = Config.WINDOW_WIDTH - 178;
        double y = 42;
        double rowH = 42;

        gc.save();
        gc.setFont(Font.font(11));
        for (int i = 0; i < inventory.getCapacity(); i++) {
            InventorySlot slot = inventory.getSlot(i);
            boolean selected = i == selectedSlot;

            gc.setFill(Color.web("#11131d", 0.78));
            gc.fillRoundRect(x, y + i * rowH, 166, 34, 6, 6);
            gc.setStroke(selected ? Color.GOLD : Color.web("#4a4f61"));
            gc.setLineWidth(selected ? 2 : 1);
            gc.strokeRoundRect(x, y + i * rowH, 166, 34, 6, 6);

            gc.setFill(Color.LIGHTGRAY);
            gc.fillText("Slot" + (i + 1) + " [" + slotKey(i) + "]:",
                x + 8, y + i * rowH + 12);

            if (slot == null || slot.isEmpty()) {
                gc.setFill(Color.web("#b8bcc9"));
                gc.fillText("Empty", x + 38, y + i * rowH + 27);
            } else {
                slot.getType().drawIcon(gc, x + 8, y + i * rowH + 15, 16);
                gc.setFill(Color.WHITE);
                gc.fillText(slot.getType().getHudLabel() + " x" + slot.getCount(),
                    x + 30, y + i * rowH + 28);
            }
        }

        gc.setFill(Color.web("#dce4ff"));
        gc.fillText("Selected Item: Slot" + (selectedSlot + 1),
            x, y + inventory.getCapacity() * rowH + 4);
        gc.restore();
    }

    public static void drawGold(GraphicsContext gc, int gold) {
        double x = Config.WINDOW_WIDTH - 126;
        double y = 10;

        gc.save();
        gc.setFill(Color.web("#11131d", 0.78));
        gc.fillRoundRect(x, y, 112, 24, 6, 6);
        gc.setStroke(Color.GOLD);
        gc.strokeRoundRect(x, y, 112, 24, 6, 6);

        Image icon = getCoinIcon();
        if (icon != null) {
            gc.drawImage(icon, x + 7, y + 4, 16, 16);
        } else {
            gc.setFill(Color.GOLD);
            gc.fillOval(x + 7, y + 4, 16, 16);
        }
        gc.setFill(Color.WHITE);
        gc.setFont(Font.font(12));
        gc.fillText("Gold: " + gold, x + 29, y + 16);
        gc.restore();
    }

    public static void drawInventoryOverlay(GraphicsContext gc, Inventory inventory,
                                            int selectedSlot) {
        double w = 320;
        double h = 70 + inventory.getCapacity() * 42;
        double x = (Config.WINDOW_WIDTH - w) / 2.0;
        double y = (Config.WINDOW_HEIGHT - h) / 2.0;

        gc.save();
        gc.setGlobalAlpha(0.88);
        gc.setFill(Color.web("#080a12"));
        gc.fillRoundRect(x, y, w, h, 8, 8);
        gc.setGlobalAlpha(1.0);
        gc.setStroke(Color.web("#6f7cff"));
        gc.setLineWidth(2);
        gc.strokeRoundRect(x, y, w, h, 8, 8);

        gc.setFill(Color.WHITE);
        gc.setFont(Font.font(18));
        gc.fillText("Backpack", x + 18, y + 30);
        gc.setFont(Font.font(12));
        gc.setFill(Color.LIGHTGRAY);
        gc.fillText("B: close", x + w - 68, y + 30);

        for (int i = 0; i < inventory.getCapacity(); i++) {
            drawOverlaySlot(gc, inventory.getSlot(i), i, i == selectedSlot,
                x + 18, y + 52 + i * 42, w - 36);
        }
        gc.restore();
    }

    public static void drawBackpackFullPrompt(GraphicsContext gc, Inventory inventory,
                                              PickupItem groundItem) {
        double x = 18;
        double y = 116;
        double w = 230;
        double h = 112 + inventory.getCapacity() * 17;

        gc.save();
        gc.setFill(Color.web("#160b0b", 0.86));
        gc.fillRoundRect(x, y, w, h, 8, 8);
        gc.setStroke(Color.ORANGE);
        gc.setLineWidth(2);
        gc.strokeRoundRect(x, y, w, h, 8, 8);

        gc.setFill(Color.ORANGE);
        gc.setFont(Font.font(15));
        gc.fillText("Backpack Full", x + 12, y + 22);

        gc.setFill(Color.WHITE);
        gc.setFont(Font.font(12));
        gc.fillText("Ground Item:", x + 12, y + 43);
        groundItem.getType().drawIcon(gc, x + 104, y + 28, 20);
        gc.fillText(groundItem.getType().getHudLabel() + " x" + groundItem.getQuantity(),
            x + 130, y + 43);

        gc.setFill(Color.LIGHTGRAY);
        gc.fillText("Replace:", x + 12, y + 66);
        for (int i = 0; i < inventory.getCapacity(); i++) {
            InventorySlot slot = inventory.getSlot(i);
            String name = slot == null || slot.isEmpty()
                ? "Empty"
                : slot.getType().getHudLabel() + " x" + slot.getCount();
            gc.fillText(slotKey(i) + " -> Slot" + (i + 1) + ": " + name,
                x + 12, y + 87 + i * 17);
        }
        gc.restore();
    }

    private static void drawOverlaySlot(GraphicsContext gc, InventorySlot slot, int index,
                                        boolean selected, double x, double y, double w) {
        gc.setFill(selected ? Color.web("#2d2940") : Color.web("#151824"));
        gc.fillRoundRect(x, y, w, 34, 6, 6);
        gc.setStroke(selected ? Color.GOLD : Color.web("#3f4558"));
        gc.strokeRoundRect(x, y, w, 34, 6, 6);

        gc.setFill(Color.LIGHTGRAY);
        gc.setFont(Font.font(12));
        gc.fillText("[" + (index + 1) + "] " + slotKey(index), x + 10, y + 21);

        if (slot == null || slot.isEmpty()) {
            gc.setFill(Color.web("#aeb4c7"));
            gc.fillText("Empty", x + 72, y + 21);
            return;
        }

        PickupType type = slot.getType();
        type.drawIcon(gc, x + 72, y + 7, 20);
        gc.setFill(Color.WHITE);
        gc.fillText(type.getHudLabel() + " x" + slot.getCount(), x + 100, y + 21);
    }

    private static String slotKey(int index) {
        return index >= 0 && index < SLOT_KEYS.length ? SLOT_KEYS[index] : "-";
    }

    private static Image getCoinIcon() {
        if (coinIcon != null) return coinIcon;
        try {
            var stream = HudRenderer.class.getResourceAsStream("/assets/items/coin.png");
            if (stream != null) coinIcon = new Image(stream);
        } catch (RuntimeException ignored) {
            coinIcon = null;
        }
        return coinIcon;
    }

    private static void drawFireballIcon(GraphicsContext gc, Player player, double x, double y) {
        final double size = 34;
        boolean ready = player.canCastFireball();

        gc.save();
        gc.setFill(Color.web("#202028"));
        gc.fillRoundRect(x, y, size, size, 6, 6);
        gc.setStroke(ready ? Color.GOLD : Color.web("#555560"));
        gc.setLineWidth(ready ? 2.5 : 1.5);
        gc.strokeRoundRect(x, y, size, size, 6, 6);

        gc.setFill(ready ? Color.ORANGERED : Color.web("#8a4a2a"));
        gc.fillOval(x + 8, y + 7, 18, 18);
        gc.setStroke(ready ? Color.YELLOW : Color.web("#6f6f6f"));
        gc.setLineWidth(1.5);
        gc.strokeOval(x + 8, y + 7, 18, 18);

        double cooldownRatio = player.getFireballCooldownRatio();
        if (!ready) {
            gc.setGlobalAlpha(0.58);
            gc.setFill(Color.GRAY);
            gc.fillRoundRect(x, y, size, size * cooldownRatio, 6, 6);
            gc.setGlobalAlpha(1.0);
            gc.setFill(Color.WHITE);
            gc.setFont(Font.font(10));
            String status = player.getFireballStatusText();
            gc.fillText(status.contains("mana") ? "MP" : status, x + 5, y + 31);
        } else {
            gc.setGlobalAlpha(0.24);
            gc.setStroke(Color.YELLOW);
            gc.setLineWidth(5);
            gc.strokeRoundRect(x - 2, y - 2, size + 4, size + 4, 8, 8);
            gc.setGlobalAlpha(1.0);
        }

        gc.setFill(Color.WHITE);
        gc.setFont(Font.font(10));
        gc.fillText("K", x + size - 9, y + 10);
        gc.restore();
    }

    private static void drawBar(GraphicsContext gc, String label, double x, double y,
                                double width, double height, double ratio, String value,
                                Color high, Color mid, Color low) {
        ratio = Math.max(0, Math.min(1, ratio));

        gc.setFill(Color.LIGHTGRAY);
        gc.setFont(Font.font(11));
        gc.fillText(label, x, y - 2);

        gc.setFill(Color.web("#1d1d26"));
        gc.fillRect(x, y, width, height);

        Color color = ratio > 0.6 ? high : ratio > 0.3 ? mid : low;
        gc.setFill(color);
        gc.fillRect(x, y, width * ratio, height);

        gc.setStroke(Color.web("#050505"));
        gc.setLineWidth(1);
        gc.strokeRect(x, y, width, height);

        gc.setFill(Color.WHITE);
        gc.setFont(Font.font(11));
        gc.fillText(value, x + width + 6, y + height - 2);
    }
}
