package ncu.cs2.my_game.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import ncu.cs2.my_game.Main;

/**
 * 開始畫面控制器，對應 start-scene.fxml。
 */
public class StartSceneController {
    @FXML
    private Button startButton;

    private boolean startRequested;

    /** 點擊「開始遊戲」：進入 Level 1 並開始計時 */
    @FXML
    private void onStartGame() {
        if (startRequested) return;
        startRequested = true;
        if (startButton != null) {
            startButton.setDisable(true);
        }
        Main.startLevel1();
    }

    /** 點擊「離開」：結束程式 */
    @FXML
    private void onExit() {
        Main.quitGame();
    }
}
