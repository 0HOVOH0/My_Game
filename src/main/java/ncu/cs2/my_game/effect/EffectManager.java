package ncu.cs2.my_game.effect;

import javafx.scene.canvas.GraphicsContext;
import ncu.cs2.my_game.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Owns transient visual effects and removes them after their lifetime ends.
 */
public class EffectManager {
    private final List<AnimatedEffect> effects = new ArrayList<>();

    public EffectManager() {}

    public void update(double dt) {
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
        // Slash visuals are now drawn by Player/Enemy with the same 150-degree
        // timing and reach as the melee hitbox. Keep this hook as a no-op so
        // scenes do not spawn the older, wider GIF slash on top.
    }

    public void clear() {
        for (AnimatedEffect effect : effects) {
            effect.destroy();
        }
        effects.clear();
    }

    public int getActiveEffectCount() {
        return effects.size();
    }

}
