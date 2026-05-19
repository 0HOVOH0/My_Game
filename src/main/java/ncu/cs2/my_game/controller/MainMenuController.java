package ncu.cs2.my_game.controller;

import javafx.fxml.FXML;
import ncu.cs2.my_game.Main;

/**
 * 主選單控制器，對應 main-menu.fxml 的事件處理。
 */
public class MainMenuController {

    /**
     * 按下「開始遊戲」按鈕時觸發。
     * 呼叫 Main.startLevel1() 切換至第一關場景。
     */
    @FXML
    private void onStartGame() {
        Main.startLevel1();
    }

    /**
     * 按下「離開」按鈕時觸發。
     */
    @FXML
    private void onExit() {
        javafx.application.Platform.exit();
    }
}
