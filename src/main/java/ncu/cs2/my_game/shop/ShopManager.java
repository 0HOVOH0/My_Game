package ncu.cs2.my_game.shop;

import ncu.cs2.my_game.item.PickupType;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 商店商品管理。每次商店從商品池抽樣，並依關卡數調整價格。
 */
public class ShopManager {

    private static final int MIN_ITEMS = 3;
    private static final int MAX_ITEMS = 6;
    private static final double BOSS_SHOP_MARKUP = 1.10;

    private final int stageNumber;
    private final Random random = new Random();
    private final List<ShopItem> items = new ArrayList<>();
    private int refreshCount;
    private String lastSignature = "";

    public ShopManager(int stageNumber) {
        this.stageNumber = Math.max(1, stageNumber);
        rerollItems();
    }

    public List<ShopItem> getItems() {
        return items;
    }

    public ShopItem get(int index) {
        if (index < 0 || index >= items.size()) return null;
        return items.get(index);
    }

    public int getRefreshCost() {
        int stageBase = 10 + Math.max(0, stageNumber - 3) * 4;
        int refreshScaling = refreshCount * 10;
        int repeatPenalty = refreshCount >= 2 ? (refreshCount - 1) * 5 : 0;
        return stageBase + refreshScaling + repeatPenalty;
    }

    public void refreshShop() {
        refreshCount++;
        rerollItems();
    }

    public int getRefreshCount() {
        return refreshCount;
    }

    private void rerollItems() {
        List<ShopPoolEntry> pool = createPool();
        int desiredCount = MIN_ITEMS + random.nextInt(MAX_ITEMS - MIN_ITEMS + 1);
        desiredCount = Math.min(desiredCount, pool.size());

        List<ShopItem> rolled = new ArrayList<>();
        for (int i = 0; i < desiredCount; i++) {
            ShopPoolEntry entry = takeWeighted(pool);
            rolled.add(new ShopItem(entry.type, scaledPrice(entry.basePrice),
                randomStock(entry.minStock, entry.maxStock)));
        }

        String signature = signatureOf(rolled);
        if (signature.equals(lastSignature) && !pool.isEmpty()) {
            ShopPoolEntry entry = takeWeighted(pool);
            rolled.set(rolled.size() - 1, new ShopItem(entry.type, scaledPrice(entry.basePrice),
                randomStock(entry.minStock, entry.maxStock)));
            signature = signatureOf(rolled);
        }

        items.clear();
        items.addAll(rolled);
        lastSignature = signature;
    }

    private ShopPoolEntry takeWeighted(List<ShopPoolEntry> pool) {
        int totalWeight = 0;
        for (ShopPoolEntry entry : pool) totalWeight += entry.weight;

        int roll = random.nextInt(Math.max(1, totalWeight));
        int running = 0;
        for (int i = 0; i < pool.size(); i++) {
            ShopPoolEntry entry = pool.get(i);
            running += entry.weight;
            if (roll < running) {
                pool.remove(i);
                return entry;
            }
        }
        return pool.remove(pool.size() - 1);
    }

    private int scaledPrice(int basePrice) {
        double stageMultiplier = 1.0 + (stageNumber - 1) * 0.08;
        return roundToFive(basePrice * stageMultiplier * BOSS_SHOP_MARKUP);
    }

    private int randomStock(int min, int max) {
        return min + random.nextInt(max - min + 1);
    }

    private int roundToFive(double value) {
        return Math.max(5, (int) Math.round(value / 5.0) * 5);
    }

    private String signatureOf(List<ShopItem> rolled) {
        StringBuilder builder = new StringBuilder();
        for (ShopItem item : rolled) {
            if (builder.length() > 0) builder.append('|');
            builder.append(item.getType().name());
        }
        return builder.toString();
    }

    private List<ShopPoolEntry> createPool() {
        List<ShopPoolEntry> pool = new ArrayList<>();
        pool.add(new ShopPoolEntry(PickupType.SMALL_POTION, 20, 2, 3, 40));
        pool.add(new ShopPoolEntry(PickupType.LARGE_POTION, 45, 1, 2, 24));
        pool.add(new ShopPoolEntry(PickupType.FIRE_SCROLL, 40, 1, 2, 24));
        pool.add(new ShopPoolEntry(PickupType.ICE_SCROLL, 45, 1, 2, 20));
        pool.add(new ShopPoolEntry(PickupType.BOMB, 50, 1, 2, 26));
        return pool;
    }

    private static class ShopPoolEntry {
        private final PickupType type;
        private final int basePrice;
        private final int minStock;
        private final int maxStock;
        private final int weight;

        private ShopPoolEntry(PickupType type, int basePrice, int minStock, int maxStock, int weight) {
            this.type = type;
            this.basePrice = basePrice;
            this.minStock = minStock;
            this.maxStock = maxStock;
            this.weight = weight;
        }
    }
}
