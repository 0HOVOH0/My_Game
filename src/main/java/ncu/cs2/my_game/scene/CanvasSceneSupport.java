package ncu.cs2.my_game.scene;

import javafx.beans.binding.Bindings;
import javafx.beans.binding.NumberBinding;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import ncu.cs2.my_game.Config;

/**
 * 建立會依視窗大小等比例縮放的 JavaFX Scene。
 * 遊戲邏輯仍使用基準解析度，避免碰撞座標受視窗縮放影響。
 */
public final class CanvasSceneSupport {

    private CanvasSceneSupport() {}

    public static Scene createScaledCanvasScene(Stage stage, Canvas canvas) {
        canvas.setWidth(Config.BASE_WIDTH);
        canvas.setHeight(Config.BASE_HEIGHT);
        return createScaledScene(stage, canvas);
    }

    public static Scene createScaledScene(Stage stage, Node content) {
        prepareFixedSizeContent(content);

        Group scaledContent = new Group(content);
        StackPane root = new StackPane(scaledContent);
        root.setStyle("-fx-background-color: black;");

        Scene scene = new Scene(root, Config.BASE_WIDTH, Config.BASE_HEIGHT, Color.BLACK);
        NumberBinding scale = Bindings.min(
            scene.widthProperty().divide(Config.BASE_WIDTH),
            scene.heightProperty().divide(Config.BASE_HEIGHT)
        );
        scaledContent.scaleXProperty().bind(scale);
        scaledContent.scaleYProperty().bind(scale);

        installFullscreenKeys(stage, scene);
        return scene;
    }

    private static void prepareFixedSizeContent(Node content) {
        if (content instanceof Region region) {
            region.setMinSize(Config.BASE_WIDTH, Config.BASE_HEIGHT);
            region.setPrefSize(Config.BASE_WIDTH, Config.BASE_HEIGHT);
            region.setMaxSize(Config.BASE_WIDTH, Config.BASE_HEIGHT);
        }
    }

    private static void installFullscreenKeys(Stage stage, Scene scene) {
        scene.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.F11) {
                stage.setFullScreen(!stage.isFullScreen());
                event.consume();
            } else if (event.getCode() == KeyCode.ESCAPE && stage.isFullScreen()) {
                stage.setFullScreen(false);
                event.consume();
            }
        });
    }
}
