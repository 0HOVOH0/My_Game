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
import ncu.cs2.my_game.item.PotionInventory;
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
    private final PotionInventory potionInventory;
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
    private boolean transitioning;
    private boolean portalEnterRequested;

    // 浮板動畫
    private final double[] platformBaseY;
    private double platformTime;

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
            new Rectangle2D(160, 435, 130, 20),
            new Rectangle2D(490, 405, 130, 20)
        };
        platformBaseY = new double[platforms.length];
        for (int i = 0; i < platforms.length; i++) {
            platformBaseY[i] = platforms[i].getMinY();
        }
        shopCounter = new Rectangle2D(335, GROUND_Y - 54, 130, 54);
        exitDoor = new Rectangle2D(Config.WINDOW_WIDTH - 72, GROUND_Y - 120, 46, 120);

        inventory = Main.getInventory();
        potionInventory = Main.getPotionInventory();
        pickupItems = new ArrayList<>();
        itemSpawnManager = new ItemSpawnManager(ground, platforms);
        shopManager = new ShopManager(Main.getStageNumber());
        Main.registerActiveScene("SHOP", Main.getStageNumber(), GameState.PLAYING, this::cleanup);

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
        SceneTransitionManager.tick(dt);

        update(dt);
        render();
    }

    private void handleKeyPressed(KeyCode key) {
        if (SceneTransitionManager.isTransitioning()) return;
        if (shopOpen) {
            handleShopKey(key);
            return;
        }

        if (isPortalEnterKey(key) && isNearExitDoor()) {
            portalEnterRequested = true;
            return;
        }
        player.handleKeyPressed(key);
        if (key == KeyCode.B) inventoryOpen = !inventoryOpen;
        if (key == KeyCode.E && isNearShop()) shopOpen = true;
        handleInventoryKey(key);
        handlePotionKey(key);
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
        if (key == KeyCode.R) {
            refreshShop();
            return;
        }
        int slotIndex = keyToSlotIndex(key);
        if (slotIndex >= 0) {
            selectedInventorySlot = slotIndex;
            buySelectedItem();
        }
    }

    private void update(double dt) {
        // 浮板動畫：兩塊浮板以正弦波上下飄動，相位差 π 讓它們反向運動
        platformTime += dt;
        for (int i = 0; i < platforms.length; i++) {
            double phase = i * Math.PI;
            double offsetY = Math.sin(platformTime * 1.1 + phase) * 13.0;
            platforms[i] = new Rectangle2D(
                platforms[i].getMinX(),
                platformBaseY[i] + offsetY,
                platforms[i].getWidth(),
                platforms[i].getHeight()
            );
        }

        player.update(dt);
        updatePlayerPlatformDrop(dt);
        resolveCollisions();
        if (player.wantsToStandUp()) player.standUp();
        player.setX(Math.max(0, Math.min(player.getX(), Config.WINDOW_WIDTH - player.getWidth())));

        checkPickupItems();
        if (messageTimer > 0) messageTimer -= dt;

        if (!transitioning
            && Collision.checkAABB(player.getHitbox(), exitDoor)
            && portalEnterRequested
            && player.isOnGround()
            && SceneTransitionManager.tryBeginTransition("SHOP_TO_BOSS")) {
            transitioning = true;
            Main.setPersistedPlayerState(player.getHp(), player.getMana());
            stop();
            Main.startBoss();
        }
        portalEnterRequested = false;
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
        if (item.isSoldOut()) {
            showMessage("Sold Out");
            return;
        }
        if (!Main.spendGold(item.getPrice())) {
            showMessage("Not enough Gold");
            return;
        }

        boolean accepted = item.getType().isPotion()
            ? potionInventory.add(item.getType(), 1)
            : inventory.add(item.getType());
        if (accepted) {
            item.consumeOne();
            showMessage("Bought " + item.getType().getHudLabel());
            return;
        }

        if (item.getType().isPotion()) {
            Main.addGold(item.getPrice());
            showMessage("Potion slots full");
            return;
        }

        InventorySlot dropped = inventory.replaceSlot(selectedInventorySlot, item.getType(), 1);
        if (dropped == null) {
            Main.addGold(item.getPrice());
            showMessage("Backpack full");
            return;
        }
        item.consumeOne();
        addPickup(dropped.getType(), player.getX(),
            player.getY() + player.getHeight() - PickupItem.SIZE,
            dropped.getCount());
        showMessage("Bought and replaced Slot" + (selectedInventorySlot + 1));
    }

    private void refreshShop() {
        int cost = shopManager.getRefreshCost();
        if (!Main.spendGold(cost)) {
            showMessage("Need " + cost + " Gold");
            return;
        }
        shopManager.refreshShop();
        selectedShopIndex = Math.min(selectedShopIndex, shopManager.getItems().size() - 1);
        showMessage("Shop refreshed");
    }

    private void checkPickupItems() {
        for (PickupItem item : pickupItems) {
            if (item.isPickedUp()) continue;
            if (Collision.checkAABB(player.getHitbox(), item.getHitbox())) {
                boolean accepted = item.getType().isPotion()
                    ? potionInventory.add(item.getType(), item.getQuantity())
                    : inventory.add(item.getType(), item.getQuantity());
                if (accepted) {
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

    private void handlePotionKey(KeyCode key) {
        int slotIndex = switch (key) {
            case N -> 0;
            case M -> 1;
            default -> -1;
        };
        if (slotIndex >= 0) {
            potionInventory.useSlot(slotIndex, new UseContext(player, null, null));
        }
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

    private boolean isNearExitDoor() {
        Rectangle2D area = new Rectangle2D(exitDoor.getMinX() - 12, exitDoor.getMinY() - 12,
            exitDoor.getWidth() + 24, exitDoor.getHeight() + 24);
        return Collision.checkAABB(player.getHitbox(), area);
    }

    private boolean isPortalEnterKey(KeyCode key) {
        return key == KeyCode.ENTER;
    }

    private void showMessage(String text) {
        message = text;
        messageTimer = 1.5;
    }

    private void render() {
        // ── 背景：地牢石壁 ─────────────────────────────────────────────────────
        gc.setFill(Color.web("#121219"));
        gc.fillRect(0, 0, Config.WINDOW_WIDTH, Config.WINDOW_HEIGHT);

        drawDungeonWall();
        drawStoneFloor();
        drawPillar(30,  36, GROUND_Y - 60);
        drawPillar(734, 36, GROUND_Y - 60);
        drawTorch(58,  GROUND_Y - 220);
        drawTorch(710, GROUND_Y - 220);
        drawShopBuilding();
        drawFloatingPlatforms();
        drawExitDoor();

        for (PickupItem item : pickupItems) item.draw(gc);
        player.draw(gc);

        HudRenderer.drawPlayerStatus(gc, player, "SHOP");
        HudRenderer.drawGold(gc, Main.getGold());
        HudRenderer.drawInventorySlots(gc, inventory, selectedInventorySlot);
        HudRenderer.drawPotionSlots(gc, potionInventory);
        HudRenderer.drawControlsHint(gc);
        if (inventoryOpen) HudRenderer.drawInventoryOverlay(gc, inventory, selectedInventorySlot);
        if (isNearShop() && !shopOpen) drawOpenPrompt();
        if (isNearExitDoor()) drawExitPrompt();
        if (shopOpen) drawShopUI();
        if (messageTimer > 0) drawMessage();
    }

    /** 後方石壁：深色磚塊紋路 */
    private void drawDungeonWall() {
        int tileW = 64, tileH = 32;
        for (int row = 0; row * tileH < GROUND_Y; row++) {
            for (int col = 0; col * tileW < Config.WINDOW_WIDTH + tileW; col++) {
                int offsetX = (row % 2 == 0) ? 0 : tileW / 2;
                double x = col * tileW - offsetX;
                double y = row * tileH;
                gc.setFill(Color.web(row % 5 == 0 ? "#16161f" : "#1a1a24"));
                gc.fillRect(x + 1, y + 1, tileW - 2, tileH - 2);
                gc.setStroke(Color.web("#0e0e16"));
                gc.setLineWidth(1);
                gc.strokeRect(x, y, tileW, tileH);
            }
        }
    }

    /** 地板：石板磚格 */
    private void drawStoneFloor() {
        double gy = GROUND_Y;
        double fh = ground.getHeight();
        int tileW = 64;
        gc.setFill(Color.web("#252530"));
        gc.fillRect(0, gy, Config.WINDOW_WIDTH, fh);
        gc.setStroke(Color.web("#1a1a22"));
        gc.setLineWidth(1);
        for (int col = 0; col * tileW < Config.WINDOW_WIDTH; col++) {
            gc.strokeLine(col * tileW, gy, col * tileW, gy + fh);
        }
        gc.strokeLine(0, gy + fh / 2, Config.WINDOW_WIDTH, gy + fh / 2);
        // 上沿高光
        gc.setFill(Color.web("#363645"));
        gc.fillRect(0, gy, Config.WINDOW_WIDTH, 4);
    }

    /** 石柱 */
    private void drawPillar(double x, double width, double topY) {
        double h = GROUND_Y - topY;
        // 柱身
        gc.setFill(Color.web("#252530"));
        gc.fillRect(x, topY, width, h);
        // 左邊高光
        gc.setFill(Color.web("#333340"));
        gc.fillRect(x, topY, 5, h);
        // 右邊陰影
        gc.setFill(Color.web("#18181f"));
        gc.fillRect(x + width - 5, topY, 5, h);
        // 頂蓋
        gc.setFill(Color.web("#3e3e50"));
        gc.fillRect(x - 5, topY, width + 10, 12);
        gc.setFill(Color.web("#4a4a5c"));
        gc.fillRect(x - 5, topY, width + 10, 4);
        // 底座
        gc.setFill(Color.web("#3e3e50"));
        gc.fillRect(x - 5, GROUND_Y - 12, width + 10, 12);
    }

    /** 火炬 */
    private void drawTorch(double cx, double y) {
        // 炬柄
        gc.setFill(Color.web("#5a3a1a"));
        gc.fillRect(cx - 3, y, 6, 28);
        // 火焰底（橙色）
        gc.setFill(Color.web("#e06000", 0.85));
        gc.fillOval(cx - 8, y - 14, 16, 20);
        // 火焰芯（黃色）
        gc.setFill(Color.web("#ffe040", 0.9));
        gc.fillOval(cx - 4, y - 10, 8, 12);
        // 暈光
        gc.setFill(Color.web("#ff8800", 0.12));
        gc.fillOval(cx - 22, y - 28, 44, 50);
    }

    /** 商店建築：石造牆面 + 招牌 */
    private void drawShopBuilding() {
        double sx = shopCounter.getMinX();
        double sy = shopCounter.getMinY();
        double sw = shopCounter.getWidth();
        double sh = shopCounter.getHeight();

        // 左牆柱
        gc.setFill(Color.web("#252530"));
        gc.fillRect(sx - 18, sy - 80, 18, sh + 80);
        gc.setFill(Color.web("#333340"));
        gc.fillRect(sx - 18, sy - 80, 4, sh + 80);
        // 右牆柱
        gc.setFill(Color.web("#252530"));
        gc.fillRect(sx + sw, sy - 80, 18, sh + 80);
        gc.setFill(Color.web("#18181f"));
        gc.fillRect(sx + sw + 14, sy - 80, 4, sh + 80);

        // 頂樑
        gc.setFill(Color.web("#2e2e3c"));
        gc.fillRect(sx - 22, sy - 84, sw + 40, 12);
        gc.setFill(Color.web("#3e3e50"));
        gc.fillRect(sx - 22, sy - 84, sw + 40, 4);

        // 招牌木板
        gc.setFill(Color.web("#5a3a1a"));
        gc.fillRoundRect(sx + 4, sy - 68, sw - 8, 32, 4, 4);
        gc.setStroke(Color.web("#8b5a2b"));
        gc.setLineWidth(1.5);
        gc.strokeRoundRect(sx + 4, sy - 68, sw - 8, 32, 4, 4);
        gc.setFill(Color.GOLD);
        gc.setFont(javafx.scene.text.Font.font("Arial", javafx.scene.text.FontWeight.BOLD, 14));
        gc.fillText("SHOP", sx + 36, sy - 46);

        // 金幣圖示
        gc.setFill(Color.GOLD);
        gc.fillOval(sx + 12, sy - 64, 18, 18);
        gc.setFill(Color.web("#b8860b"));
        gc.setFont(javafx.scene.text.Font.font(10));
        gc.fillText("G", sx + 17, sy - 51);

        // 石造櫃台
        gc.setFill(Color.web("#2a2a38"));
        gc.fillRect(sx, sy, sw, sh);
        gc.setFill(Color.web("#363648"));
        gc.fillRect(sx, sy, sw, 5);
        gc.setFill(Color.web("#1e1e28"));
        gc.fillRect(sx, sy + sh - 5, sw, 5);
        gc.setStroke(Color.web("#4a4a60"));
        gc.setLineWidth(1);
        gc.strokeRect(sx, sy, sw, sh);
    }

    /** 浮板：石板外形，含正面厚度與高光 */
    private void drawFloatingPlatforms() {
        for (Rectangle2D p : platforms) {
            double x = p.getMinX(), y = p.getMinY();
            double w = p.getWidth(), h = p.getHeight();
            int face = 6; // 正面厚度

            // 正面（石板前側）
            gc.setFill(Color.web("#1e1e28"));
            gc.fillRect(x, y + h - face, w, face);

            // 主石面
            gc.setFill(Color.web("#383848"));
            gc.fillRect(x, y, w, h - face);

            // 頂面高光
            gc.setFill(Color.web("#4e4e62"));
            gc.fillRect(x, y, w, 4);

            // 石塊紋路（短直線）
            gc.setStroke(Color.web("#2a2a38"));
            gc.setLineWidth(1);
            gc.strokeLine(x + w / 3, y + 4, x + w / 3, y + h - face - 2);
            gc.strokeLine(x + w * 2 / 3, y + 4, x + w * 2 / 3, y + h - face - 2);

            // 左右邊角陰影
            gc.setFill(Color.web("#18181f"));
            gc.fillRect(x, y, 3, h);
            gc.fillRect(x + w - 3, y, 3, h);
        }
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
        double x = exitDoor.getMinX();
        double y = exitDoor.getMinY();
        double w = exitDoor.getWidth();
        double h = exitDoor.getHeight();

        // 血紅背光暈（壓迫感）
        gc.save();
        gc.setGlobalAlpha(0.10 + 0.04 * Math.sin(platformTime * 1.6));
        gc.setFill(Color.web("#cc0000"));
        gc.fillOval(x - 32, y - 18, w + 64, h + 36);
        gc.restore();

        // 石拱兩側柱
        gc.setFill(Color.web("#1e1e28"));
        gc.fillRect(x - 12, y, 12, h + 2);
        gc.fillRect(x + w, y, 12, h + 2);
        gc.fillRoundRect(x - 14, y - 10, w + 28, 22, 6, 6);
        // 拱頂高光
        gc.setFill(Color.web("#333340"));
        gc.fillRect(x - 14, y - 10, w + 28, 4);
        // 拱柱左側亮邊
        gc.setFill(Color.web("#2e2e3c"));
        gc.fillRect(x - 12, y, 3, h);
        gc.fillRect(x + w + 9, y, 3, h);

        // 門洞：深黑帶微紅
        gc.setFill(Color.web("#080005"));
        gc.fillRect(x, y, w, h);

        // 深紅裂縫
        // 爪痕（三道斜線，替代十字）
        double mX = x + w / 2.0;
        double mY = y + h / 2.0;
        gc.save();
        gc.setGlobalAlpha(0.55);
        gc.setStroke(Color.web("#990000"));
        gc.setLineWidth(2.0);
        gc.strokeLine(mX - 8, mY - 22, mX + 10, mY + 22);
        gc.strokeLine(mX - 14, mY - 22, mX + 4,  mY + 22);
        gc.strokeLine(mX - 2,  mY - 22, mX + 16, mY + 22);
        gc.restore();

        // 門洞內紅色暈光
        gc.save();
        gc.setGlobalAlpha(0.18 + 0.06 * Math.sin(platformTime * 2.1));
        gc.setFill(Color.web("#cc1100"));
        gc.fillOval(x - 6, y + h * 0.25, w + 12, h * 0.55);
        gc.restore();

        // 描邊（深暗紅）
        gc.setStroke(Color.web("#550000"));
        gc.setLineWidth(2.5);
        gc.strokeRect(x, y, w, h);

        // 鏈環裝飾
        gc.setFill(Color.web("#3a2808"));
        int[] chainY = {12, 32, 52};
        for (int cy : chainY) {
            gc.fillOval(x - 9, y + cy, 7, 7);
            gc.fillOval(x + w + 2, y + cy, 7, 7);
        }

        // 上方 BOSS 標籤
        gc.setFill(Color.web("#cc2020"));
        gc.setFont(Font.font("Arial", javafx.scene.text.FontWeight.BOLD, 11));
        gc.fillText("BOSS", x + 6, y - 14);
    }

    private void drawOpenPrompt() {
        gc.setFill(Color.WHITE);
        gc.setFont(Font.font(14));
        gc.fillText("E = Open Shop", shopCounter.getMinX() + 8, shopCounter.getMinY() - 38);
    }

    private void drawExitPrompt() {
        gc.setFill(Color.web("#ffaaaa"));
        gc.setFont(Font.font(14));
        gc.fillText("Press Enter", exitDoor.getMinX() - 32, exitDoor.getMinY() - 28);
    }

    private void drawShopUI() {
        double x = 230;
        double y = 120;
        double w = 340;
        double h = 310;
        gc.setFill(Color.web("#080a12", 0.92));
        gc.fillRoundRect(x, y, w, h, 8, 8);
        gc.setStroke(Color.GOLD);
        gc.setLineWidth(2);
        gc.strokeRoundRect(x, y, w, h, 8, 8);

        gc.setFill(Color.WHITE);
        gc.setFont(Font.font(18));
        gc.fillText("Shop", x + 18, y + 30);
        gc.setFont(Font.font(12));
        gc.fillText("Enter/Space: Buy    R: Refresh (" + shopManager.getRefreshCost() + "G)    Esc: Close",
            x + 18, y + 54);
        gc.fillText("Items refresh each shop. Stock resets after refresh.", x + 18, y + 72);

        for (int i = 0; i < shopManager.getItems().size(); i++) {
            ShopItem item = shopManager.get(i);
            double rowY = y + 105 + i * 30;
            if (i == selectedShopIndex) {
                gc.setFill(Color.web("#3a3216"));
                gc.fillRoundRect(x + 14, rowY - 18, w - 28, 24, 5, 5);
            }
            item.getType().drawIcon(gc, x + 24, rowY - 17, 20);
            gc.setFill(item.isSoldOut() ? Color.GRAY : Color.WHITE);
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

    private void cleanup() {
        stop();
        pickupItems.clear();
        player.getFireballs().clear();
        player.getIceProjectiles().clear();
    }
}
