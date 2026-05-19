package ncu.cs2.my_game.scene;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import ncu.cs2.my_game.Config;
import ncu.cs2.my_game.entity.Player;

/** 共用玩家 HUD 繪製工具。 */
public final class HudRenderer {

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

        gc.setFill(Color.WHITE);
        gc.setFont(Font.font(12));
        gc.fillText("Fireball: " + player.getFireballStatusText(), 12, 68);

        gc.setFill(Color.LIGHTGRAY);
        gc.setFont(Font.font(12));
        gc.fillText(title, Config.WINDOW_WIDTH / 2.0 - title.length() * 3.6, 20);
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
