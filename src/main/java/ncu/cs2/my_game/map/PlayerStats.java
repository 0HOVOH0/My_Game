package ncu.cs2.my_game.map;

import ncu.cs2.my_game.Config;

/**
 * Platform movement limits used by dungeon reachability checks.
 */
public record PlayerStats(
    double maxHorizontalJumpDistance,
    double maxVerticalJumpHeight,
    double playerWidth,
    double playerHeight,
    double gravity,
    double moveSpeed,
    boolean doubleJump,
    boolean canJumpThroughOneWayPlatform
) {
    public static PlayerStats fromConfig() {
        double jumpHeight = (Config.JUMP_FORCE * Config.JUMP_FORCE) / (2.0 * Config.GRAVITY);
        double airTime = 2.0 * Math.abs(Config.JUMP_FORCE) / Config.GRAVITY;
        double jumpDistance = Config.PLAYER_SPEED * airTime * 0.82;
        return new PlayerStats(
            jumpDistance,
            jumpHeight,
            Config.PLAYER_WIDTH,
            Config.PLAYER_HEIGHT,
            Config.GRAVITY,
            Config.PLAYER_SPEED,
            false,
            true
        );
    }
}
