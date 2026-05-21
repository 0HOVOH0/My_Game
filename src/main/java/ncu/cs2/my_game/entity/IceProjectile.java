package ncu.cs2.my_game.entity;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

public class IceProjectile extends Fireball {

    public static final double SIZE = 22.0;
    public static final double SPEED = 330.0;
    public static final double SLOW_DURATION = 3.0;
    public static final double SLOW_MULTIPLIER = 0.25;
    public static final double BOSS_SLOW_DURATION = 2.0;
    public static final double BOSS_SLOW_MULTIPLIER = 0.65;

    public IceProjectile(double x, double y, double dirX, double dirY) {
        super(x, y, dirX, dirY, SIZE, SPEED, 0, Color.LIGHTCYAN, Color.DEEPSKYBLUE, true);
    }

    @Override
    public void draw(GraphicsContext gc) {
        super.draw(gc);
    }
}
