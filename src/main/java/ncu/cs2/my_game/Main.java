package ncu.cs2.my_game;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import ncu.cs2.my_game.scene.BossScene;
import ncu.cs2.my_game.scene.GameScene;
import ncu.cs2.my_game.scene.Level1Scene;
import ncu.cs2.my_game.scene.Level2Scene;

import java.io.IOException;

/**
 * 遊戲主進入點，繼承 JavaFX Application。
 * 負責初始化視窗並提供全域場景切換功能。
 */
public class Main extends Application {

    /** 主視窗參考，供靜態方法 switchScene() 使用 */
    private static Stage primaryStage;

    /** Level 1 開始時記錄的毫秒時間戳；用於計算通關時間 */
    private static long gameStartMillis = 0;

    /** 本次通關所花費的秒數；由 startEnd() 計算，EndSceneController 讀取 */
    private static long elapsedSeconds = 0;

    /**
     * 跨關卡持久化的玩家血量。
     * startLevel1() 時重置為滿血；關卡切換（L1→L2→Boss）時透過
     * setPersistedHp() 帶入目前血量，下一關建立 Player 後呼叫
     * player.setHp(getPersistedHp()) 恢復。
     */
    private static int persistedHp = Config.PLAYER_MAX_HP;

    /**
     * JavaFX 啟動方法，初始化視窗並載入主選單場景
     */
    @Override
    public void start(Stage stage) {
        primaryStage = stage;

        // 設定視窗基本屬性
        stage.setTitle("My Game");
        stage.setWidth(Config.WINDOW_WIDTH);
        stage.setHeight(Config.WINDOW_HEIGHT);
        stage.setResizable(false);

        // 載入初始場景（開始畫面）
        switchScene("start-scene.fxml");
        stage.show();
    }

    /**
     * 切換目前顯示的場景。
     * FXML 檔案須放在 resources/ncu/cs2/my_game/ 目錄下。
     *
     * @param fxmlName FXML 檔案名稱，例如 "main-menu.fxml"
     */
    public static void switchScene(String fxmlName) {
        try {
            FXMLLoader loader = new FXMLLoader(Main.class.getResource(fxmlName));
            Scene scene = new Scene(loader.load(), Config.WINDOW_WIDTH, Config.WINDOW_HEIGHT);
            primaryStage.setScene(scene);
        } catch (IOException e) {
            // TODO: 加入正式的錯誤提示畫面，取代 stderr 輸出
            System.err.println("無法載入場景：" + fxmlName);
            e.printStackTrace();
        }
    }

    /**
     * 建立 GameScene 並切換至遊戲畫面（Boss 戰測試用）。
     */
    public static void startGame() {
        new GameScene(primaryStage).start();
    }

    /**
     * 建立 Level1Scene 並啟動第一關。
     * 同時重置血量與計時器（Level1 永遠是全新開局）。
     */
    public static void startLevel1() {
        persistedHp     = Config.PLAYER_MAX_HP;
        gameStartMillis = System.currentTimeMillis();
        new Level1Scene(primaryStage);
    }

    /**
     * 建立 Level2Scene 並啟動第二關。
     * Level2Scene 繼承 AnimationTimer，建構子內部會自動呼叫 start()。
     */
    public static void startLevel2() {
        new Level2Scene(primaryStage);
    }

    /**
     * 建立 BossScene 並啟動 Boss 關。
     * 由 Level2Scene 的終點門觸發，也作為玩家死亡後按 R 的重啟目標。
     */
    public static void startBoss() {
        new BossScene(primaryStage);
    }

    /**
     * 計算通關時間並切換至結算畫面。
     * 由 BossScene 在 Boss 死亡後延遲 1 秒呼叫。
     */
    public static void startEnd() {
        elapsedSeconds = (System.currentTimeMillis() - gameStartMillis) / 1000L;
        switchScene("end-scene.fxml");
    }

    /**
     * 回傳本次通關所花費的秒數。
     * 由 EndSceneController.initialize() 讀取以顯示通關時間。
     *
     * @return 通關秒數
     */
    public static long getElapsedSeconds() {
        return elapsedSeconds;
    }

    /**
     * 回傳跨關卡持久化的玩家血量。
     * 由各關卡場景建立 Player 後呼叫，以恢復上一關結束時的血量。
     */
    public static int getPersistedHp() {
        return persistedHp;
    }

    /**
     * 儲存玩家目前血量，供下一個場景讀取。
     * 由關卡終點門觸發，傳入 player.getHp()。
     *
     * @param hp 要持久化的血量（自動限制在 [1, PLAYER_MAX_HP]）
     */
    public static void setPersistedHp(int hp) {
        persistedHp = Math.max(1, Math.min(hp, Config.PLAYER_MAX_HP));
    }

    /** 程式進入點 */
    public static void main(String[] args) {
        launch();
    }
}
