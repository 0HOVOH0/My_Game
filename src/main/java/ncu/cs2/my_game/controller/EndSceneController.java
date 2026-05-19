package ncu.cs2.my_game.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import ncu.cs2.my_game.Main;

/**
 * 結算畫面控制器，對應 end-scene.fxml。
 * 從 Main.getElapsedSeconds() 讀取通關時間並顯示。
 */
public class EndSceneController {

    @FXML
    private Label timeLabel;

    /** FXML 載入完成後自動呼叫；填入通關時間 */
    @FXML
    private void initialize() {
        long seconds = Main.getElapsedSeconds();
        long minutes = seconds / 60;
        long secs    = seconds % 60;

        if (minutes > 0) {
            timeLabel.setText("通關時間：" + minutes + " 分 " + secs + " 秒");
        } else {
            timeLabel.setText("通關時間：" + secs + " 秒");
        }
    }

    /** 點擊「再玩一次」：回到 Level 1，計時重新開始 */
    @FXML
    private void onPlayAgain() {
        Main.startLevel1();
    }

    /** 點擊「離開」：結束程式 */
    @FXML
    private void onExit() {
        System.exit(0);
    }
}
