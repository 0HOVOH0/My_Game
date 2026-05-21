package ncu.cs2.my_game.map;

import java.util.List;

public record MapValidationResult(
    boolean isValid,
    String reason,
    List<String> errors,
    int unreachablePlatformCount,
    boolean spawnToExitReachable,
    boolean hasSpawnHazard,
    boolean exitTooCloseToSpawn,
    int retryCount
) {
    public static MapValidationResult valid(int unreachablePlatformCount,
                                            boolean spawnToExitReachable,
                                            int retryCount) {
        return new MapValidationResult(true, "ok", List.of(), unreachablePlatformCount,
            spawnToExitReachable, false, false, retryCount);
    }

    public static MapValidationResult invalid(List<String> errors,
                                              int unreachablePlatformCount,
                                              boolean spawnToExitReachable,
                                              boolean hasSpawnHazard,
                                              boolean exitTooCloseToSpawn,
                                              int retryCount) {
        String reason = errors.isEmpty() ? "unknown" : errors.get(0);
        return new MapValidationResult(false, reason, List.copyOf(errors), unreachablePlatformCount,
            spawnToExitReachable, hasSpawnHazard, exitTooCloseToSpawn, retryCount);
    }
}
