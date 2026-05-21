package ncu.cs2.my_game.scene;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import ncu.cs2.my_game.Config;
import ncu.cs2.my_game.entity.Boss;

/**
 * Draws and clears Boss-specific UI. Canvas UI is cleared by simply not drawing it.
 */
public final class BossUIManager {
    private static final double BOSS_BAR_W = 500.0;
    private static final double BOSS_BAR_H = 18.0;
    private static final double BOSS_BAR_Y = 82.0;

    private BossUIManager() {}

    public static void drawBossHealthBar(GraphicsContext gc, Boss boss) {
        if (boss == null || boss.isDeathHandled() || !boss.isAlive()) return;

        final double barX = (Config.WINDOW_WIDTH - BOSS_BAR_W) / 2.0;
        final double barY = BOSS_BAR_Y;

        gc.setFill(Color.LIGHTGRAY);
        gc.setFont(Font.font(11));
        String bossName = boss.getDisplayName();
        gc.fillText(bossName,
            barX + BOSS_BAR_W / 2.0 - bossName.length() * 3.2,
            barY - 2);

        gc.setFill(Color.web("#4a0000"));
        gc.fillRect(barX, barY, BOSS_BAR_W, BOSS_BAR_H);

        double ratio = Math.max(0.0, Math.min(1.0, (double) boss.getHp() / boss.getMaxHp()));
        Color barColor = ratio > 0.6 ? Color.web("#e53935")
            : ratio > 0.3 ? Color.web("#ff6d00")
            : Color.web("#b71c1c");
        gc.setFill(barColor);
        gc.fillRect(barX, barY, BOSS_BAR_W * ratio, BOSS_BAR_H);

        gc.setStroke(Color.web("#7f0000"));
        gc.setLineWidth(1.5);
        gc.strokeRect(barX, barY, BOSS_BAR_W, BOSS_BAR_H);

        gc.setFill(Color.LIGHTGRAY);
        gc.setFont(Font.font(11));
        gc.fillText(boss.getHp() + " / " + boss.getMaxHp(),
            barX + BOSS_BAR_W + 6, barY + 13);
    }

    public static void clearBossUI() {
        // Canvas rendering has no retained nodes; clearing means no draw call next frame.
    }
}
