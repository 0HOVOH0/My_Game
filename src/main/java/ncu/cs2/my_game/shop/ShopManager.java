package ncu.cs2.my_game.shop;

import ncu.cs2.my_game.item.PickupType;

import java.util.List;

/**
 * 商店商品清單。未來新增商品只需在此擴充。
 */
public class ShopManager {

    private final List<ShopItem> items = List.of(
        new ShopItem(PickupType.SMALL_POTION, 20),
        new ShopItem(PickupType.LARGE_POTION, 45),
        new ShopItem(PickupType.FIRE_SCROLL, 40),
        new ShopItem(PickupType.ICE_SCROLL, 45),
        new ShopItem(PickupType.BOMB, 50)
    );

    public List<ShopItem> getItems() {
        return items;
    }

    public ShopItem get(int index) {
        if (index < 0 || index >= items.size()) return null;
        return items.get(index);
    }
}
