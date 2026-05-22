package ncu.cs2.my_game.effect;

import javafx.geometry.Rectangle2D;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.shape.StrokeLineCap;

/**
 * Shared visual renderer for melee attacks.
 * Combat still lives in the entity classes; this only mirrors the active arc.
 */
public final class MeleeSlashRenderer {
    private static final double TRAIL_DEGREES = 16.0;

    private MeleeSlashRenderer() {}

    public static void draw(GraphicsContext gc,
                            double originX,
                            double originY,
                            boolean facingRight,
                            double reach,
                            double arcDegrees,
                            double elapsed,
                            double duration,
                            double windupEnd,
                            double activeEnd,
                            Rectangle2D frontBodyBox,
                            Color color) {
        if (duration <= 0 || reach <= 0 || arcDegrees <= 0) return;

        double visualStart = Math.max(0, windupEnd * 0.45);
        double visualEnd = Math.min(duration, activeEnd + (duration - activeEnd) * 0.45);
        double sweepProgress = clamp((elapsed - visualStart) / Math.max(0.001, visualEnd - visualStart),
            0.0, 1.0);
        double activeAlpha = elapsed >= windupEnd && elapsed <= activeEnd ? 1.0 : 0.45;
        double centerAngle = -arcDegrees / 2.0 + arcDegrees * sweepProgress;
        double halfArc = arcDegrees / 2.0;
        double innerReach = reach * 0.28;

        gc.save();
        gc.setLineCap(StrokeLineCap.ROUND);

        for (int i = 0; i < 5; i++) {
            double trailT = i / 4.0;
            double angle = clamp(centerAngle - TRAIL_DEGREES + TRAIL_DEGREES * 2.0 * trailT,
                -halfArc, halfArc);
            double alpha = (0.16 + 0.18 * trailT) * activeAlpha;
            double lineWidth = 2.0 + 4.0 * trailT;
            drawSlashStroke(gc, originX, originY, facingRight, angle,
                innerReach, reach, color, alpha, lineWidth);
        }

        if (frontBodyBox != null) {
            gc.setGlobalAlpha(0.12 * activeAlpha);
            gc.setFill(color);
            gc.fillRoundRect(frontBodyBox.getMinX(), frontBodyBox.getMinY() + frontBodyBox.getHeight() * 0.18,
                frontBodyBox.getWidth(), frontBodyBox.getHeight() * 0.64, 8.0, 8.0);
        }

        gc.restore();
    }

    private static void drawSlashStroke(GraphicsContext gc,
                                        double originX,
                                        double originY,
                                        boolean facingRight,
                                        double localDegrees,
                                        double innerReach,
                                        double outerReach,
                                        Color color,
                                        double alpha,
                                        double lineWidth) {
        double radians = Math.toRadians(localDegrees);
        double direction = facingRight ? 1.0 : -1.0;
        double startX = originX + Math.cos(radians) * innerReach * direction;
        double startY = originY + Math.sin(radians) * innerReach;
        double endX = originX + Math.cos(radians) * outerReach * direction;
        double endY = originY + Math.sin(radians) * outerReach;

        gc.setGlobalAlpha(alpha);
        gc.setStroke(color);
        gc.setLineWidth(lineWidth);
        gc.strokeLine(startX, startY, endX, endY);

        gc.setGlobalAlpha(alpha * 0.35);
        gc.setStroke(Color.WHITE);
        gc.setLineWidth(Math.max(1.0, lineWidth * 0.35));
        gc.strokeLine(startX, startY, endX, endY);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
