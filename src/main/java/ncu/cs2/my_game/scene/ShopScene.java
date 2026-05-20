package ncu.cs2.my_game.scene;

import javafx.animation.AnimationTimer;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.KeyCode;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import ncu.cs2.my_game.Config;
import ncu.cs2.my_game.Main;
import ncu.cs2.my_game.entity.Fireball;
import ncu.cs2.my_game.entity.Player;
import ncu.cs2.my_game.item.Inventory;
import ncu.cs2.my_game.item.InventorySlot;
import ncu.cs2.my_game.item.ItemSpawnManager;
import ncu.cs2.my_game.item.PickupItem;
import ncu.cs2.my_game.item.UseContext;
import ncu.cs2.my_game.physics.Collision;
import ncu.cs2.my_game.shop.ShopItem;
import ncu.cs2.my_game.shop.ShopManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Boss 前固定出現的安全商店關。
 */
public class ShopScene extends AnimationTimer {

    private static final double GROUND_Y = Config.WINDOW_HEIGHT - Config.GROUND_THICKNESS;

    private final Stage stage;
    private final GraphicsContext gc;
    private final Player player;
    private final Rectangle2D ground;
    private final Rectangle2D[] platforms;
    private final Rectangle2D shopCounter;
    private final Rectangle2D exitDoor;
    private final Inventory inventory;
    private final List<PickupItem> pickupItems;
    private final ItemSpawnManager itemSpawnManager;
    private final ShopManager shopManager;

    private boolean shopOpen;
    private boolean inventoryOpen;
    private int selectedShopIndex;
    private int selectedInventorySlot;
    private String message = "";
    private double messageTimer;
    private long lastNano;
    private double playerPlatformDropTimer;

    public ShopScene(Stage stage) {
        this.stage = stage;

        Canvas canvas = new Canvas(Config.WINDOW_WIDTH, Config.WINDOW_HEIGHT);
        gc = canvas.getGraphicsContext2D();
        Scene scene = CanvasSceneSupport.createScaledCanvasScene(stage, canvas);
        Fireball.setWorldBounds(Config.WINDOW_WIDTH, Config.WINDOW_HEIGHT);

        player = new Player(70, 460);
        player.setHp(Main.getPersistedHp());
        player.setMana(Main.getPersistedMana());

        ground = new Rectangle2D(0, GROUND_Y, Config.WINDOW_WIDTH, Config.GROUND_THICKNESS);
        platforms = new Rectangle2D[] {
            new Rectangle2D(180, 440, 120, 16),
            new Rectangle2D(500, 410, 120, 16)
        };
        shopCounter = new Rectangle2D(335, GROUND_Y - 54, 130, 54);
        exitDoor = new Rectangle2D(Config.WINDOW_WIDTH - 72, GROUND_Y - 120, 46, 120);

        inventory = Main.getInventory();
        pickupItems = new ArrayList<>();
        itemSpawnManager = new ItemSpawnManager(ground, platforms);
        shopManager = new ShopManager();

        scene.setOnKeyPressed(e -> handleKeyPressed(e.getCode()));
        scene.setOnKeyReleased(e -> player.handleKeyReleased(e.getCode()));

        stage.setScene(scene);
        start();
    }

    @Override
    public void handle(long now) {
        if (lastNano == 0) {
            lastNano = now;
            return;
        }
        double dt = (now - lastNano) / 1_000_000_000.0;
        lastNano = now;
        if (dt > Config.MAX_DELTA_TIME) dt = Config.MAX_DELTA_TIME;

        update(dt);
        render();
    }

    private void handleKeyPressed(KeyCode key) {
        if (shopOpen) {
            handleShopKey(key);
            return;
        }

        player.handleKeyPressed(key);
        if (key == KeyCode.B) inventoryOpen = !inventoryOpen;
        if (key == KeyCode.E && isNearShop()) shopOpen = true;
        handleInventoryKey(key);
    }

    private void handleShopKey(KeyCode key) {
        if (key == KeyCode.ESCAPE || key == KeyCode.E) {
            shopOpen = false;
            return;
        }
        if (key == KeyCode.UP || key == KeyCode.W) {
            selectedShopIndex = Math.max(0, selectedShopIndex - 1);
            return;
        }
        if (key == KeyCode.DOWN || key == KeyCode.S) {
            selectedShopIndex = Math.min(shopManager.getItems().size() - 1, selectedShopIndex + 1);
            return;
        }
        if (key == KeyCode.ENTER || key == KeyCode.SPACE) {
            buySelectedItem();
            return;
        }
        int slotIndex = keyToSlotIndex(key);
        if (slotIndex >= 0) {
            selectedInventorySlot = slotIndex;
            buySelectedItem();
        }
    }

    private void update(double dt) {
        player.update(dt);
        updatePlayerPlatformDrop(dt);
        resolveCollisions();
        if (player.wantsToStandUp()) player.standUp();
        player.setX(Math.max(0, Math.min(player.getX(), Config.WINDOW_WIDTH - player.getWidth())));

        checkPickupItems();
        if (messageTimer > 0) messageTimer -= dt;

        if (Collision.checkAABB(player.getHitbox(), exitDoor)) {
            Main.setPersistedPlayerState(player.getHp(), player.getMana());
            stop();
            Main.startBoss();
        }
    }

    private void resolveCollisions() {
        if (Collision.checkPlatform(player, ground)) {
            player.setY(ground.getMinY() - player.getHeight());
            player.setOnGround(true);
            return;
        }
        if (playerPlatformDropTimer <= 0) {
            for (Rectangle2D platform : platforms) {
                if (Collision.checkPlatform(player, platform)) {
                    player.setY(platform.getMinY() - player.getHeight());
                    player.setOnGround(true);
                    return;
                }
            }
        }
        player.setOnGround(false);
    }

    private void updatePlayerPlatformDrop(double dt) {
        if (playerPlatformDropTimer > 0) {
            playerPlatformDropTimer -= dt;
            if (playerPlatformDropTimer < 0) playerPlatformDropTimer = 0;
        }
        if (player.consumePlatformDropRequest()) {
            playerPlatformDropTimer = 0.22;
            player.setY(player.getY() + 5);
            player.setOnGround(false);
        }
    }

    private void buySelectedItem() {
        ShopItem item = shopManager.get(selectedShopIndex);
        if (item == null) return;
        if (!Main.spendGold(item.getPrice())) {
            showMessage("Not enough Gold");
            return;
        }

        if (inventory.add(item.getType())) {
            showMessage("Bought " + item.getType().getHudLabel());
            return;
        }

        InventorySlot dropped = inventory.replaceSlot(selectedInventorySlot, item.getType(), 1);
        if (dropped == null) {
            Main.addGold(item.getPrice());
            showMessage("Backpack full");
            return;
        }
        addPickup(dropped.getType(), player.getX(),
            player.getY() + player.getHeight() - PickupItem.SIZE,
            dropped.getCount());
        showMessage("Bought and replaced Slot" + (selectedInventorySlot + 1));
    }

    private void checkPickupItems() {
        for (PickupItem item : pickupItems) {
            if (item.isPickedUp()) continue;
            if (Collision.checkAABB(player.getHitbox(), item.getHitbox())) {
                if (inventory.add(item.getType(), item.getQuantity())) {
                    item.markPickedUp();
                }
            }
        }
        pickupItems.removeIf(PickupItem::isPickedUp);
    }

    private void handleInventoryKey(KeyCode key) {
        int slotIndex = keyToSlotIndex(key);
        if (slotIndex < 0) return;
        selectedInventorySlot = slotIndex;
        inventory.useSlot(slotIndex, new UseContext(player, null, null));
    }

    private int keyToSlotIndex(KeyCode key) {
        return switch (key) {
            case U -> 0;
            case I -> 1;
            case O -> 2;
            default -> -1;
        };
    }

    private void addPickup(ncu.cs2.my_game.item.PickupType type, double x, double y, int quantity) {
        pickupItems.add(itemSpawnManager.spawn(type, x, y, quantity, pickupItems));
    }

    private boolean isNearShop() {
        return player.getHitbox().intersects(shopCounter);
    }

    private void showMessage(String text) {
        message = text;
        messageTimer = 1.5;
    }

    private void render() {
        gc.setFill(Color.web("#152022"));
        gc.fillRect(0, 0, Config.WINDOW_WIDTH, Config.WINDOW_HEIGHT);

        gc.setFill(Color.web("#5a3a1a"));
        gc.fillRect(ground.getMinX(), ground.getMinY(), ground.getWidth(), ground.getHeight());
        gc.setFill(Color.web("#3f6f76"));
        for (Rectangle2D platform : platforms) {
            gc.fillRect(platform.getMinX(), platform.getMinY(), platform.getWidth(), platform.getHeight());
        }

        drawShopCounter();
        drawExitDoor();
        for (PickupItem item : pickupItems) item.draw(gc);
        player.draw(gc);

        HudRenderer.drawPlayerStatus(gc, player, "SHOP");
        HudRenderer.drawGold(gc, Main.getGold());
        HudRenderer.drawInventorySlots(gc, inventory, selectedInventorySlot);
        if (inventoryOpen) HudRenderer.drawInventoryOverlay(gc, inventory, selectedInventorySlot);
        if (isNearShop() && !shopOpen) drawOpenPrompt();
        if (shopOpen) drawShopUI();
        if (messageTimer > 0) drawMessage();
    }

    private void drawShopCounter() {
        gc.setFill(Color.web("#6d4c41"));
        gc.fillRect(shopCounter.getMinX(), shopCounter.getMinY(),
            shopCounter.getWidth(), shopCounter.getHeight());
        gc.setFill(Color.GOLD);
        gc.fillOval(shopCounter.getMinX() + 18, shopCounter.getMinY() - 28, 28, 28);
        gc.setFill(Color.LIGHTGRAY);
        gc.setFont(Font.font(13));
        gc.fillText("SHOP", shopCounter.getMinX() + 44, shopCounter.getMinY() - 10);
    }

    private void drawExitDoor() {
        gc.setFill(Color.web("#263238"));
        gc.fillRect(exitDoor.getMinX(), exitDoor.getMinY(), exitDoor.getWidth(), exitDoor.getHeight());
        gc.setStroke(Color.PURPLE);
        gc.setLineWidth(3);
        gc.strokeRect(exitDoor.getMinX(), exitDoor.getMinY(), exitDoor.getWidth(), exitDoor.getHeight());
    }

    private void drawOpenPrompt() {
        gc.setFill(Color.WHITE);
        gc.setFont(Font.font(14));
        gc.fillText("E = Open Shop", shopCounter.getMinX() + 8, shopCounter.getMinY() - 38);
    }

    private void drawShopUI() {
        double x = 230;
        double y = 120;
        double w = 340;
        double h = 250;
        gc.setFill(Color.web("#080a12", 0.92));
        gc.fillRoundRect(x, y, w, h, 8, 8);
        gc.setStroke(Color.GOLD);
        gc.setLineWidth(2);
        gc.strokeRoundRect(x, y, w, h, 8, 8);

        gc.setFill(Color.WHITE);
        gc.setFont(Font.font(18));
        gc.fillText("Shop", x + 18, y + 30);
        gc.setFont(Font.font(12));
        gc.fillText("Item | Price    Enter/Space: Buy    Esc: Close", x + 18, y + 54);

        for (int i = 0; i < shopManager.getItems().size(); i++) {
            ShopItem item = shopManager.get(i);
            double rowY = y + 82 + i * 30;
            if (i == selectedShopIndex) {
                gc.setFill(Color.web("#3a3216"));
                gc.fillRoundRect(x + 14, rowY - 18, w - 28, 24, 5, 5);
            }
            item.getType().drawIcon(gc, x + 24, rowY - 17, 20);
            gc.setFill(Color.WHITE);
            gc.fillText(item.getLabel(), x + 54, rowY);
        }
    }

    private void drawMessage() {
        gc.setFill(Color.web("#000000", 0.72));
        gc.fillRoundRect(300, 392, 200, 32, 6, 6);
        gc.setFill(Color.WHITE);
        gc.setFont(Font.font(13));
        gc.fillText(message, 316, 412);
    }
}
