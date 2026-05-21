package ncu.cs2.my_game.effect;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;

/**
 * Visual-only timed effect drawn in world coordinates.
 */
public class AnimatedEffect {
    private final Image image;
    private final double x;
    private final double y;
    private final double width;
    private final double height;
    private final boolean flipHorizontal;
    private final double duration;
    private double elapsed;
    private boolean alive = true;

    public AnimatedEffect(Image image, double x, double y, double width, double height,
                          boolean flipHorizontal, double duration) {
        this.image = image;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.flipHorizontal = flipHorizontal;
        this.duration = duration;
    }

    public void update(double dt) {
        if (!alive) return;
        elapsed += dt;
        if (elapsed >= duration) {
            alive = false;
        }
    }

    public void draw(GraphicsContext gc) {
        if (!alive || image == null || image.isError()) return;
        gc.save();
        if (flipHorizontal) {
            gc.translate(x + width, y);
            gc.scale(-1, 1);
            gc.drawImage(image, 0, 0, width, height);
        } else {
            gc.drawImage(image, x, y, width, height);
        }
        gc.restore();
    }

    public boolean isAlive() {
        return alive;
    }

    public void destroy() {
        alive = false;
    }
}
