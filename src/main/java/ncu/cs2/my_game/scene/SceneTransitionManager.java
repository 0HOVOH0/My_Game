package ncu.cs2.my_game.scene;

import ncu.cs2.my_game.Config;
import ncu.cs2.my_game.entity.BossType;

/**
 * Small shared runtime state for scene changes. It prevents repeated portal
 * triggers and gives debug overlays one authoritative place to read flow state.
 */
public final class SceneTransitionManager {
    private static GameState currentGameState = GameState.MAIN_MENU;
    private static BossFightState bossFightState = BossFightState.NOT_STARTED;
    private static String currentSceneName = "START_MENU";
    private static String transitionName = "";
    private static boolean transitioning;
    private static boolean hasBossFightSnapshot;
    private static double transitionTimer;
    private static double portalCooldown;
    private static int currentLevel;
    private static int activeGameLoopCount;
    private static BossType currentBossType;

    private SceneTransitionManager() {}

    public static void resetForNewGame() {
        currentGameState = GameState.PLAYING;
        bossFightState = BossFightState.NOT_STARTED;
        currentSceneName = "LEVEL_1";
        transitionName = "";
        transitioning = false;
        hasBossFightSnapshot = false;
        transitionTimer = 0;
        portalCooldown = 0;
        currentLevel = 1;
        currentBossType = null;
        activeGameLoopCount = 0;
    }

    public static void showMainMenu() {
        currentGameState = GameState.MAIN_MENU;
        bossFightState = BossFightState.NOT_STARTED;
        currentSceneName = "START_MENU";
        transitionName = "";
        transitioning = false;
        hasBossFightSnapshot = false;
        transitionTimer = 0;
        portalCooldown = 0;
        currentLevel = 0;
        currentBossType = null;
        activeGameLoopCount = 0;
    }

    public static void registerScene(String sceneName, int level, GameState state) {
        currentSceneName = sceneName;
        currentLevel = level;
        currentGameState = state;
        transitioning = false;
        transitionName = "";
        transitionTimer = 0;
        portalCooldown = Config.PORTAL_COOLDOWN_SECONDS;
        activeGameLoopCount = 1;
    }

    public static void clearActiveLoop() {
        activeGameLoopCount = 0;
    }

    public static void tick(double dt) {
        if (portalCooldown > 0) {
            portalCooldown -= dt;
            if (portalCooldown < 0) portalCooldown = 0;
        }
        if (!transitioning) return;

        transitionTimer += dt;
        if (transitionTimer > Config.TRANSITION_TIMEOUT_SECONDS) {
            System.err.println("Transition timeout: " + transitionName + ". Forcing recovery.");
            transitioning = false;
            transitionName = "";
            transitionTimer = 0;
            portalCooldown = Config.PORTAL_COOLDOWN_SECONDS;
            currentGameState = currentSceneName.startsWith("BOSS")
                ? GameState.BOSS_FIGHT
                : GameState.PLAYING;
        }
    }

    public static boolean tryBeginTransition(String name) {
        if (transitioning || portalCooldown > 0) return false;
        transitioning = true;
        transitionName = name;
        transitionTimer = 0;
        currentGameState = GameState.TRANSITIONING;
        return true;
    }

    public static void finishTransition(GameState state) {
        transitioning = false;
        transitionName = "";
        transitionTimer = 0;
        portalCooldown = Config.PORTAL_COOLDOWN_SECONDS;
        currentGameState = state;
    }

    public static boolean isTransitioning() {
        return transitioning;
    }

    public static double getPortalCooldown() {
        return portalCooldown;
    }

    public static GameState getCurrentGameState() {
        return currentGameState;
    }

    public static String getCurrentSceneName() {
        return currentSceneName;
    }

    public static int getCurrentLevel() {
        return currentLevel;
    }

    public static int getActiveGameLoopCount() {
        return activeGameLoopCount;
    }

    public static BossType getCurrentBossType() {
        return currentBossType;
    }

    public static void setCurrentBossType(BossType bossType) {
        currentBossType = bossType;
    }

    public static BossFightState getBossFightState() {
        return bossFightState;
    }

    public static void setBossFightState(BossFightState state) {
        bossFightState = state;
    }

    public static void setPaused(boolean paused) {
        if (transitioning) return;
        currentGameState = paused
            ? GameState.PAUSED
            : (currentSceneName.startsWith("BOSS") ? GameState.BOSS_FIGHT : GameState.PLAYING);
    }

    public static boolean hasBossFightSnapshot() {
        return hasBossFightSnapshot;
    }

    public static void setBossFightSnapshotActive(boolean active) {
        hasBossFightSnapshot = active;
    }
}
