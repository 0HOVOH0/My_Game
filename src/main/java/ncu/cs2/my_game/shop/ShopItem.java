package ncu.cs2.my_game.shop;

import ncu.cs2.my_game.item.PickupType;

public class ShopItem {

    private final PickupType type;
    private final int price;
    private final int maxStock;
    private int remainingStock;

    public ShopItem(PickupType type, int price, int maxStock) {
        this.type = type;
        this.price = price;
        this.maxStock = Math.max(1, maxStock);
        this.remainingStock = this.maxStock;
    }

    public PickupType getType() {
        return type;
    }

    public int getPrice() {
        return price;
    }

    public int getMaxStock() {
        return maxStock;
    }

    public int getRemainingStock() {
        return remainingStock;
    }

    public boolean isSoldOut() {
        return remainingStock <= 0;
    }

    public boolean consumeOne() {
        if (isSoldOut()) return false;
        remainingStock--;
        return true;
    }

    public String getLabel() {
        if (isSoldOut()) {
            return type.getHudLabel() + " - " + price + "G   Sold Out";
        }
        return type.getHudLabel() + " - " + price + "G   "
            + remainingStock + "/" + maxStock + " left";
    }
}
