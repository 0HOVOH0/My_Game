package ncu.cs2.my_game;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import ncu.cs2.my_game.scene.CanvasSceneSupport;
import ncu.cs2.my_game.scene.BossScene;
import ncu.cs2.my_game.scene.GameScene;
import ncu.cs2.my_game.scene.Level1Scene;
import ncu.cs2.my_game.scene.Level2Scene;
import ncu.cs2.my_game.scene.SceneTransitionManager;
import ncu.cs2.my_game.scene.ShopScene;
import ncu.cs2.my_game.map.MapPoolManager;
import ncu.cs2.my_game.map.TileMap;
import ncu.cs2.my_game.stage.StageDefinition;
import ncu.cs2.my_game.stage.StageGenerator;
import ncu.cs2.my_game.item.Inventory;
import ncu.cs2.my_game.economy.CurrencyManager;
import ncu.cs2.my_game.entity.BossType;

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

    /** 跨關卡持久化的玩家魔力。 */
    private static double persistedMana = Config.PLAYER_MAX_MANA;

    /** 跨 Level2 與 Boss 關保留的簡易背包 */
    private static Inventory inventory = new Inventory();

    /** 跨關卡保留的金幣。 */
    private static CurrencyManager currencyManager = new CurrencyManager();

    /** 隨機關卡生成器。 */
    private static StageGenerator stageGenerator = new StageGenerator();

    /** 一般關合法地圖池。 */
    private static MapPoolManager mapPoolManager = new MapPoolManager();

    /** 目前進度關卡編號。 */
    private static int stageNumber = 1;

    /** 每輪 Boss 前需要完成的一般關卡數。Level1 會計入第一輪第一關。 */
    private static final int NORMAL_STAGES_BEFORE_SHOP = 3;

    /** 本輪已完成的一般關卡數。 */
    private static int normalStagesInCycle = 0;

    /** 下一個一般關卡定義。 */
    private static StageDefinition nextStageDefinition = stageGenerator.nextStage();

    private static BossType lastBossType = null;

    private static BossType activeBossType = null;

    private static Runnable activeSceneCleanup = null;

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
        stage.setMinWidth(Config.MIN_WIDTH);
        stage.setMinHeight(Config.MIN_HEIGHT);
        stage.setResizable(true);
        stage.setFullScreenExitHint("");

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
            Parent root = loader.load();
            Scene scene = CanvasSceneSupport.createScaledScene(primaryStage, root);
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
        stopActiveSceneIfAny();
        SceneTransitionManager.resetForNewGame();
        persistedHp     = Config.PLAYER_MAX_HP;
        persistedMana   = Config.PLAYER_MAX_MANA;
        inventory       = new Inventory();
        currencyManager = new CurrencyManager();
        stageGenerator  = new StageGenerator();
        mapPoolManager  = new MapPoolManager();
        mapPoolManager.generateNormalMapPool();
        stageNumber     = 1;
        normalStagesInCycle = 1;
        lastBossType = null;
        activeBossType = null;
        nextStageDefinition = stageGenerator.nextStage();
        gameStartMillis = System.currentTimeMillis();
        new Level1Scene(primaryStage);
    }

    /**
     * 建立 Level2Scene 並啟動第二關。
     * Level2Scene 繼承 AnimationTimer，建構子內部會自動呼叫 start()。
     */
    public static void startLevel2() {
        stopActiveSceneIfAny();
        new Level2Scene(primaryStage, nextStageDefinition);
    }

    /**
     * 建立 ShopScene，固定插在 Boss 關之前。
     */
    public static void startShop() {
        stopActiveSceneIfAny();
        new ShopScene(primaryStage);
    }

    /**
     * 一般關卡完成後的節奏控制：Boss 前固定先進商店。
     */
    public static void completeNormalStage() {
        normalStagesInCycle++;
        stageNumber++;
        if (normalStagesInCycle >= NORMAL_STAGES_BEFORE_SHOP) {
            startShop();
            return;
        }
        nextStageDefinition = stageGenerator.nextStage();
        startLevel2();
    }

    /**
     * 建立 BossScene 並啟動 Boss 關。
     * 由 ShopScene 觸發，也作為玩家死亡後按 R 的重啟目標。
     */
    public static void startBoss() {
        stopActiveSceneIfAny();
        new BossScene(primaryStage);
    }

    /**
     * 計算通關時間並切換至結算畫面。
     * 由 BossScene 在 Boss 死亡後延遲 1 秒呼叫。
     */
    public static void startEnd() {
        stopActiveSceneIfAny();
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

    /** 回傳跨關卡持久化的玩家魔力。 */
    public static double getPersistedMana() {
        return persistedMana;
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

    /** 儲存玩家目前血量與魔力，供下一個場景讀取。 */
    public static void setPersistedPlayerState(int hp, double mana) {
        setPersistedHp(hp);
        persistedMana = Math.max(0.0, Math.min(mana, Config.PLAYER_MAX_MANA));
    }

    /** 回傳跨關卡背包 */
    public static Inventory getInventory() {
        return inventory;
    }

    public static int getGold() {
        return currencyManager.getGold();
    }

    public static void setGold(int gold) {
        currencyManager.setGold(gold);
    }

    public static void addGold(int amount) {
        currencyManager.addGold(amount);
    }

    public static boolean spendGold(int amount) {
        return currencyManager.spendGold(amount);
    }

    public static int getStageNumber() {
        return stageNumber;
    }

    public static StageDefinition getCurrentStageDefinition() {
        return nextStageDefinition;
    }

    public static TileMap pickNormalStageMap() {
        return mapPoolManager.pickNormalMap();
    }

    public static String getMapPoolPreviewDirectory() {
        return mapPoolManager.getPreviewDirectory().toAbsolutePath().toString();
    }

    public static void advanceAfterBoss() {
        stageNumber++;
        normalStagesInCycle = 0;
        nextStageDefinition = stageGenerator.nextStage();
        activeBossType = null;
        SceneTransitionManager.setCurrentBossType(null);
        SceneTransitionManager.setBossFightSnapshotActive(false);
    }

    public static void exitToMainMenu() {
        stopActiveSceneIfAny();
        activeBossType = null;
        SceneTransitionManager.showMainMenu();
        switchScene("start-scene.fxml");
    }

    public static void quitGame() {
        stopActiveSceneIfAny();
        Platform.exit();
    }

    public static BossType rollBossType() {
        BossType[] types = BossType.values();
        BossType picked = types[(int) (Math.random() * types.length)];
        if (lastBossType != null && picked == lastBossType) {
            picked = types[(picked.ordinal() + 1) % types.length];
        }
        lastBossType = picked;
        return picked;
    }

    public static BossType getOrCreateBossType() {
        if (activeBossType == null) {
            activeBossType = rollBossType();
        }
        SceneTransitionManager.setCurrentBossType(activeBossType);
        return activeBossType;
    }

    public static void registerActiveScene(String sceneName, int level,
                                           ncu.cs2.my_game.scene.GameState state,
                                           Runnable cleanup) {
        activeSceneCleanup = cleanup;
        SceneTransitionManager.registerScene(sceneName, level, state);
    }

    public static void clearActiveScene(Runnable cleanup) {
        if (activeSceneCleanup == cleanup) {
            activeSceneCleanup = null;
        }
        SceneTransitionManager.clearActiveLoop();
    }

    private static void stopActiveSceneIfAny() {
        Runnable cleanup = activeSceneCleanup;
        activeSceneCleanup = null;
        if (cleanup != null) {
            cleanup.run();
        }
        SceneTransitionManager.clearActiveLoop();
    }

    /** 程式進入點 */
    public static void main(String[] args) {
        launch();
    }
}
