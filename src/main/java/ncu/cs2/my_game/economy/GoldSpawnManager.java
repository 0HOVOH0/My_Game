package ncu.cs2.my_game.economy;

import javafx.geometry.Point2D;
import javafx.geometry.Rectangle2D;
import ncu.cs2.my_game.item.ItemSpawnResolver;
import ncu.cs2.my_game.item.PickupItem;

import java.util.ArrayList;
import java.util.List;

/**
 * 使用既有道具生成 resolver 產生金幣位置，避免金幣與其他地板物重疊。
 */
public class GoldSpawnManager {

    private final ItemSpawnResolver resolver;

    public GoldSpawnManager(Rectangle2D ground, Rectangle2D[] platforms) {
        resolver = new ItemSpawnResolver(ground, platforms);
    }

    public GoldSpawnManager(Rectangle2D ground, Rectangle2D[] platforms,
                            Rectangle2D[] solidObstacles) {
        resolver = new ItemSpawnResolver(ground, platforms, solidObstacles);
    }

    public GoldPickup spawn(int amount, double preferredX, double preferredY,
                            List<PickupItem> existingItems,
                            List<GoldPickup> existingGold) {
        List<Rectangle2D> extraOccupied = new ArrayList<>();
        for (GoldPickup gold : existingGold) {
            if (!gold.isPickedUp()) {
                extraOccupied.add(gold.getHitbox());
            }
        }

        Point2D point = resolver.findValidSpawnPosition(preferredX, preferredY,
            existingItems, extraOccupied);
        return new GoldPickup(point.getX(), point.getY(), amount);
    }
}
