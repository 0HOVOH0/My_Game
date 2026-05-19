package ncu.cs2.my_game.state;

import ncu.cs2.my_game.item.Inventory;
import ncu.cs2.my_game.item.PickupType;

import java.util.EnumMap;
import java.util.Map;

/**
 * 背包數量快照。
 */
public class InventorySnapshot {

    private final Map<PickupType, Integer> counts;

    public InventorySnapshot(Inventory inventory) {
        this.counts = new EnumMap<>(inventory.snapshotCounts());
    }

    public void restore(Inventory inventory) {
        inventory.restoreCounts(counts);
    }
}
