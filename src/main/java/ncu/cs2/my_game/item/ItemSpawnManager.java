package ncu.cs2.my_game.item;

import javafx.geometry.Point2D;
import javafx.geometry.Rectangle2D;

import java.util.List;

/**
 * 尋找安全的地板道具生成位置。
 * 使用有限候選點搜尋，避免重疊、超出地圖或掉到不可行走區域。
 */
public class ItemSpawnManager {

    private final ItemSpawnResolver resolver;

    public ItemSpawnManager(Rectangle2D ground, Rectangle2D[] platforms) {
        this.resolver = new ItemSpawnResolver(ground, platforms);
    }

    /**
     * 產生道具，若目標位置重疊或不安全會自動找附近位置。
     */
    public PickupItem spawn(PickupType type, double preferredX, double preferredY,
                            List<PickupItem> existingItems) {
        return spawn(type, preferredX, preferredY, 1, existingItems);
    }

    public PickupItem spawn(PickupType type, double preferredX, double preferredY, int quantity,
                            List<PickupItem> existingItems) {
        Point2D point = resolver.findValidSpawnPosition(preferredX, preferredY, existingItems);
        return type.create(point.getX(), point.getY(), quantity);
    }
}
