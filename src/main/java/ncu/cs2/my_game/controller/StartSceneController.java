package ncu.cs2.my_game.controller;

import javafx.fxml.FXML;
import ncu.cs2.my_game.Main;

/**
 * 開始畫面控制器，對應 start-scene.fxml。
 */
public class StartSceneController {

    /** 點擊「開始遊戲」：進入 Level 1 並開始計時 */
    @FXML
    private void onStartGame() {
        Main.startLevel1();
    }

    /** 點擊「離開」：結束程式 */
    @FXML
    private void onExit() {
        System.exit(0);
    }
}
