module ncu.cs2.my_game {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.graphics;  // GraphicsContext、Rectangle2D 所需
    requires java.desktop;     // 地圖池 PNG 預覽輸出

    opens ncu.cs2.my_game to javafx.fxml;
    opens ncu.cs2.my_game.controller to javafx.fxml;

    exports ncu.cs2.my_game;
    exports ncu.cs2.my_game.entity;
    exports ncu.cs2.my_game.physics;
    exports ncu.cs2.my_game.controller;
    exports ncu.cs2.my_game.scene;
    exports ncu.cs2.my_game.fsm;
    exports ncu.cs2.my_game.item;
    exports ncu.cs2.my_game.economy;
    exports ncu.cs2.my_game.stage;
    exports ncu.cs2.my_game.shop;
    exports ncu.cs2.my_game.state;
}
