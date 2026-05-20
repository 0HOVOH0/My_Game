package ncu.cs2.my_game.state;

import ncu.cs2.my_game.item.Inventory;
import ncu.cs2.my_game.item.PickupItem;
import ncu.cs2.my_game.Main;
import ncu.cs2.my_game.economy.GoldPickup;

import java.util.ArrayList;
import java.util.List;

/**
 * 關卡初始狀態快照。
 * 保存足以重建關卡消耗狀態的輕量資料，而不是保存整個物件圖。
 */
public class StageSnapshot {

    private final double playerX;
    private final double playerY;
    private final int playerHp;
    private final double playerMana;
    private final int gold;
    private final InventorySnapshot inventorySnapshot;
    private final List<PickupSnapshot> pickupSnapshots;
    private final List<GoldSnapshot> goldSnapshots;
    private final List<EnemySnapshot> enemySnapshots;
    private final boolean hasHealthItem;
    private final double healthItemX;
    private final double healthItemY;
    private final double healthItemWidth;
    private final double healthItemHeight;

    public StageSnapshot(double playerX, double playerY, int playerHp, double playerMana,
                         Inventory inventory,
                         List<PickupItem> pickupItems,
                         List<EnemySnapshot> enemySnapshots,
                         boolean hasHealthItem,
                         double healthItemX, double healthItemY,
                         double healthItemWidth, double healthItemHeight) {
        this(playerX, playerY, playerHp, playerMana, inventory, pickupItems,
             new ArrayList<>(), enemySnapshots, hasHealthItem, healthItemX,
             healthItemY, healthItemWidth, healthItemHeight);
    }

    public StageSnapshot(double playerX, double playerY, int playerHp, double playerMana,
                         Inventory inventory,
                         List<PickupItem> pickupItems,
                         List<GoldPickup> goldPickups,
                         List<EnemySnapshot> enemySnapshots,
                         boolean hasHealthItem,
                         double healthItemX, double healthItemY,
                         double healthItemWidth, double healthItemHeight) {
        this.playerX = playerX;
        this.playerY = playerY;
        this.playerHp = playerHp;
        this.playerMana = playerMana;
        this.gold = Main.getGold();
        this.inventorySnapshot = new InventorySnapshot(inventory);
        this.pickupSnapshots = snapshotPickups(pickupItems);
        this.goldSnapshots = snapshotGold(goldPickups);
        this.enemySnapshots = new ArrayList<>(enemySnapshots);
        this.hasHealthItem = hasHealthItem;
        this.healthItemX = healthItemX;
        this.healthItemY = healthItemY;
        this.healthItemWidth = healthItemWidth;
        this.healthItemHeight = healthItemHeight;
    }

    public void restoreInventory(Inventory inventory) {
        inventorySnapshot.restore(inventory);
        Main.setGold(gold);
    }

    public List<PickupItem> createPickupItems() {
        List<PickupItem> items = new ArrayList<>();
        for (PickupSnapshot snapshot : pickupSnapshots) {
            items.add(snapshot.createItem());
        }
        return items;
    }

    public List<GoldPickup> createGoldPickups() {
        List<GoldPickup> items = new ArrayList<>();
        for (GoldSnapshot snapshot : goldSnapshots) {
            items.add(snapshot.createPickup());
        }
        return items;
    }

    public EnemySnapshot[] getEnemySnapshots() {
        return enemySnapshots.toArray(new EnemySnapshot[0]);
    }

    public double getPlayerX() { return playerX; }

    public double getPlayerY() { return playerY; }

    public int getPlayerHp() { return playerHp; }

    public double getPlayerMana() { return playerMana; }

    public int getGold() { return gold; }

    public boolean hasHealthItem() { return hasHealthItem; }

    public double getHealthItemX() { return healthItemX; }

    public double getHealthItemY() { return healthItemY; }

    public double getHealthItemWidth() { return healthItemWidth; }

    public double getHealthItemHeight() { return healthItemHeight; }

    private List<PickupSnapshot> snapshotPickups(List<PickupItem> pickupItems) {
        List<PickupSnapshot> snapshots = new ArrayList<>();
        for (PickupItem item : pickupItems) {
            snapshots.add(new PickupSnapshot(item));
        }
        return snapshots;
    }

    private List<GoldSnapshot> snapshotGold(List<GoldPickup> goldPickups) {
        List<GoldSnapshot> snapshots = new ArrayList<>();
        for (GoldPickup pickup : goldPickups) {
            snapshots.add(new GoldSnapshot(pickup));
        }
        return snapshots;
    }
}
