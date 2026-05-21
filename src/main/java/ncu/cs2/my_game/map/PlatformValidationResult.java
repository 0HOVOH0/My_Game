package ncu.cs2.my_game.map;

import javafx.geometry.Rectangle2D;

import java.util.List;

public record PlatformValidationResult(
    boolean valid,
    boolean spawnToExitReachable,
    int platformCount,
    int reachablePlatformCount,
    int unreachablePlatformCount,
    int mainPathLength,
    int branchCount,
    long seed,
    String reason,
    List<Rectangle2D> unreachablePlatforms
) {
    public double reachableRatio() {
        if (platformCount == 0) return 0.0;
        return (double) reachablePlatformCount / platformCount;
    }
}
