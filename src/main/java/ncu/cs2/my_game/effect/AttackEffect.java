package ncu.cs2.my_game.effect;

import javafx.scene.image.Image;
import ncu.cs2.my_game.Config;
import ncu.cs2.my_game.entity.Player;

/**
 * Factory for player slash effects. This does not participate in combat damage.
 */
public final class AttackEffect {
    private AttackEffect() {}

    public static AnimatedEffect slash(Image image, Player player) {
        boolean facingRight = player.isFacingRight();
        double effectX = facingRight
            ? player.getX() + player.getWidth() - 10.0
            : player.getX() - Config.SLASH_EFFECT_WIDTH + 10.0;
        double effectY = player.getY() + player.getHeight() * 0.25;

        return new AnimatedEffect(
            image,
            effectX,
            effectY,
            Config.SLASH_EFFECT_WIDTH,
            Config.SLASH_EFFECT_HEIGHT,
            !facingRight,
            Config.SLASH_EFFECT_DURATION
        );
    }

}
