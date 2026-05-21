package ncu.cs2.my_game.effect;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;

/**
 * Transparent PNG frame animation effect.
 */
public class AnimatedSpriteEffect extends AnimatedEffect {
    private final FrameAnimation animation;
    private final double x;
    private final double y;
    private final double width;
    private final double height;
    private final boolean flipHorizontal;
    private double elapsed;
    private boolean alive = true;

    public AnimatedSpriteEffect(FrameAnimation animation, double x, double y,
                                double width, double height, boolean flipHorizontal) {
        super(null, x, y, width, height, flipHorizontal, animation.duration());
        this.animation = animation;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.flipHorizontal = flipHorizontal;
    }

    @Override
    public void update(double dt) {
        if (!alive) return;
        elapsed += dt;
        if (elapsed >= animation.duration()) alive = false;
    }

    @Override
    public void draw(GraphicsContext gc) {
        if (!alive) return;
        Image frame = animation.frameAt(elapsed);
        if (frame == null || frame.isError()) return;

        gc.save();
        if (flipHorizontal) {
            gc.translate(x + width, y);
            gc.scale(-1, 1);
            gc.drawImage(frame, 0, 0, width, height);
        } else {
            gc.drawImage(frame, x, y, width, height);
        }
        gc.restore();
    }

    @Override
    public boolean isAlive() {
        return alive;
    }

    @Override
    public void destroy() {
        alive = false;
    }
}
