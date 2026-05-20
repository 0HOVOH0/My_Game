package ncu.cs2.my_game.shop;

import ncu.cs2.my_game.item.PickupType;

public class ShopItem {

    private final PickupType type;
    private final int price;

    public ShopItem(PickupType type, int price) {
        this.type = type;
        this.price = price;
    }

    public PickupType getType() {
        return type;
    }

    public int getPrice() {
        return price;
    }

    public String getLabel() {
        return type.getHudLabel() + " - " + price + "G";
    }
}
