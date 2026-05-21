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
    private double slashCooldown;

    public EffectManager() {
        slashImage = loadSlashImage();
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
        if (slashImage != null && !slashImage.isError()) {
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
}
