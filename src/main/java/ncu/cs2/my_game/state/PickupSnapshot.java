package ncu.cs2.my_game.state;

import ncu.cs2.my_game.item.PickupItem;
import ncu.cs2.my_game.item.PickupType;

/**
 * 地板道具快照，只保存可重建所需的種類與位置。
 */
public class PickupSnapshot {

    private final PickupType type;
    private final double x;
    private final double y;

    public PickupSnapshot(PickupItem item) {
        this(item.getType(), item.getX(), item.getY());
    }

    public PickupSnapshot(PickupType type, double x, double y) {
        this.type = type;
        this.x = x;
        this.y = y;
    }

    public PickupItem createItem() {
        return type.create(x, y);
    }
}
