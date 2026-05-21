package ncu.cs2.my_game.effect;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import ncu.cs2.my_game.Config;
import ncu.cs2.my_game.entity.Player;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Owns transient visual effects and removes them after their lifetime ends.
 */
public class EffectManager {
    private final List<AnimatedEffect> effects = new ArrayList<>();
    private final Image slashImage;
    private final FrameAnimation slashAnimation;
    private double slashCooldown;

    public EffectManager() {
        slashAnimation = loadSlashAnimation();
        slashImage = slashAnimation == null ? loadSlashImage() : null;
        if (slashAnimation == null && slashImage != null) {
            System.out.println("Warning: slash.gif may not have transparent background. Use PNG sequence for best result.");
            System.out.println("Please replace slash.gif with transparent PNG sequence for correct rendering.");
        }
    }

    public void update(double dt) {
        if (slashCooldown > 0) {
            slashCooldown -= dt;
            if (slashCooldown < 0) slashCooldown = 0;
        }
        for (AnimatedEffect effect : effects) {
            effect.update(dt);
        }
        effects.removeIf(effect -> !effect.isAlive());
    }

    public void draw(GraphicsContext gc) {
        for (AnimatedEffect effect : effects) {
            effect.draw(gc);
        }
    }

    public void playSlash(Player player) {
        if (slashCooldown > 0) return;
        if (slashAnimation != null && !slashAnimation.isEmpty()) {
            effects.add(AttackEffect.slash(slashAnimation, player));
        } else if (slashImage != null && !slashImage.isError()) {
            effects.add(AttackEffect.slash(slashImage, player));
        } else {
            return;
        }
        slashCooldown = Config.SLASH_EFFECT_COOLDOWN;
    }

    public void clear() {
        for (AnimatedEffect effect : effects) {
            effect.destroy();
        }
        effects.clear();
        slashCooldown = 0;
    }

    public int getActiveEffectCount() {
        return effects.size();
    }

    private Image loadSlashImage() {
        try {
            var stream = EffectManager.class.getResourceAsStream(Config.SLASH_EFFECT_RESOURCE);
            if (stream != null) {
                return new Image(stream);
            }
            File fallback = new File(Config.SLASH_EFFECT_FALLBACK_FILE);
            if (fallback.exists()) {
                return new Image(fallback.toURI().toString());
            }
        } catch (RuntimeException ignored) {
            // Missing visual asset should not break combat.
        }
        return null;
    }

    private FrameAnimation loadSlashAnimation() {
        List<Image> frames = new ArrayList<>();
        for (int i = 1; i <= Config.SLASH_EFFECT_FRAME_COUNT; i++) {
            String path = String.format(Config.SLASH_EFFECT_FRAME_PATTERN, i);
            var stream = EffectManager.class.getResourceAsStream(path);
            if (stream == null) {
                frames.clear();
                break;
            }
            Image image = new Image(stream);
            if (image.isError()) {
                frames.clear();
                break;
            }
            frames.add(image);
        }
        if (frames.isEmpty()) return null;
        System.out.println("[EffectManager] Loaded transparent slash PNG sequence: "
            + frames.size() + " frames.");
        return new FrameAnimation(frames, Config.SLASH_EFFECT_DURATION);
    }
}
