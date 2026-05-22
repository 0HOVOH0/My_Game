package ncu.cs2.my_game.map;

/**
 * Tunable platform generation settings. Later stages can use stricter values.
 */
public record PlatformGenerationConfig(
    int minPlatformWidth,
    int maxPlatformWidth,
    int minGapX,
    int maxGapX,
    int minHeightDelta,
    int maxHeightDelta,
    double maxJumpDistanceX,
    double maxJumpHeight,
    double branchChance,
    int branchPlatformCount,
    double chestPlatformChance,
    double enemyPlatformChance,
    double movingPlatformChance,
    double oneWayPlatformChance
) {
    public static PlatformGenerationConfig forLevel(int levelIndex, PlayerStats stats) {
        int difficulty = Math.max(0, levelIndex - 1);
        return new PlatformGenerationConfig(
            3,
            Math.min(8, 6 + difficulty),
            3 + difficulty / 3,
            Math.min(7, 5 + difficulty / 3),
            -2,
            Math.min(3, 2 + difficulty / 4),
            stats.maxHorizontalJumpDistance(),
            stats.maxVerticalJumpHeight() * 0.85,
            Math.min(0.88, 0.56 + difficulty * 0.04),
            Math.min(7, 3 + difficulty / 3),
            Math.min(0.55, 0.25 + difficulty * 0.03),
            Math.min(0.70, 0.38 + difficulty * 0.04),
            Math.min(0.35, difficulty * 0.03),
            Math.min(0.65, 0.35 + difficulty * 0.03)
        );
    }
}
