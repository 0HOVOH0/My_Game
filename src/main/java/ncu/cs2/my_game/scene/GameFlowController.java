package ncu.cs2.my_game.scene;

import javafx.application.Platform;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import ncu.cs2.my_game.Config;
import ncu.cs2.my_game.Main;

import java.util.List;

/**
 * Shared pause, restart, quit, and debug overlay controller for canvas scenes.
 */
public class GameFlowController {
    private final Runnable restartLevel;
    private final Runnable restartGame;
    private final Runnable exitToMenu;
    private final Runnable quitGame;
    private boolean paused;
    private boolean debugVisible;

    public GameFlowController(Runnable restartLevel, Runnable restartGame,
                              Runnable exitToMenu, Runnable quitGame) {
        this.restartLevel = restartLevel;
        this.restartGame = restartGame;
        this.exitToMenu = exitToMenu;
        this.quitGame = quitGame;
    }

    public boolean handleKeyPressed(KeyEvent event) {
        KeyCode key = event.getCode();
        if (key == KeyCode.F3) {
            debugVisible = !debugVisible;
            event.consume();
            return true;
        }
        if (key == KeyCode.ESCAPE || key == KeyCode.P) {
            paused = !paused;
            SceneTransitionManager.setPaused(paused);
            event.consume();
            return true;
        }
        if (key == KeyCode.R) {
            if (event.isShiftDown()) {
                restartGame.run();
            } else {
                restartLevel.run();
            }
            event.consume();
            return true;
        }
        if (key == KeyCode.M) {
            if (paused) {
                exitToMenu.run();
                event.consume();
                return true;
            }
            return false;   // not paused → let M pass through as potion key
        }
        if (key == KeyCode.Q) {
            if (paused) {
                quitGame.run();
            }
            event.consume();
            return true;
        }
        return false;
    }

    public boolean isPaused() {
        return paused;
    }

    public boolean isDebugVisible() {
        return debugVisible;
    }

    public void setPaused(boolean paused) {
        this.paused = paused;
        SceneTransitionManager.setPaused(paused);
    }

    public void drawPauseOverlay(GraphicsContext gc) {
        if (!paused) return;

        gc.save();
        gc.setGlobalAlpha(0.72);
        gc.setFill(Color.BLACK);
        gc.fillRect(0, 0, Config.WINDOW_WIDTH, Config.WINDOW_HEIGHT);
        gc.restore();

        double panelW = 360;
        double panelH = 250;
        double x = (Config.WINDOW_WIDTH - panelW) / 2.0;
        double y = (Config.WINDOW_HEIGHT - panelH) / 2.0;
        gc.setFill(Color.web("#151923"));
        gc.fillRoundRect(x, y, panelW, panelH, 14, 14);
        gc.setStroke(Color.web("#8af0ff"));
        gc.setLineWidth(2);
        gc.strokeRoundRect(x, y, panelW, panelH, 14, 14);

        gc.setFill(Color.WHITE);
        gc.setFont(Font.font("Arial", FontWeight.BOLD, 32));
        gc.fillText("PAUSED", x + 112, y + 48);

        gc.setFont(Font.font("Arial", 16));
        gc.setFill(Color.web("#d7edf2"));
        gc.fillText("ESC / P        Resume", x + 58, y + 92);
        gc.fillText("R              Restart Level", x + 58, y + 122);
        gc.fillText("Shift + R      Restart Game", x + 58, y + 152);
        gc.fillText("F3             Debug Overlay", x + 58, y + 182);
        gc.fillText("M              Exit to Menu", x + 58, y + 212);
        gc.fillText("Q              Quit Game", x + 58, y + 236);
    }

    public void drawDebugOverlay(GraphicsContext gc, List<String> lines) {
        if (!debugVisible) return;

        gc.save();
        gc.setGlobalAlpha(0.78);
        gc.setFill(Color.web("#05070a"));
        gc.fillRect(8, 96, 310, 18 + lines.size() * 15);
        gc.restore();

        gc.setFill(Color.web("#8af0ff"));
        gc.setFont(Font.font("Consolas", 11));
        double y = 112;
        for (String line : lines) {
            gc.fillText(line, 16, y);
            y += 15;
        }
    }

    public static GameFlowController forScene(Runnable cleanup, Runnable restartLevel) {
        return new GameFlowController(
            () -> {
                cleanup.run();
                restartLevel.run();
            },
            () -> {
                cleanup.run();
                Main.startLevel1();
            },
            () -> {
                cleanup.run();
                Main.exitToMainMenu();
            },
            () -> {
                cleanup.run();
                Platform.exit();
            }
        );
    }
}
