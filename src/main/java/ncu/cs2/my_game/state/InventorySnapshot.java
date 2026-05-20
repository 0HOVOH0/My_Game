package ncu.cs2.my_game.state;

import ncu.cs2.my_game.item.Inventory;
import ncu.cs2.my_game.item.InventorySlot;

import java.util.ArrayList;
import java.util.List;

/**
 * 背包數量快照。
 */
public class InventorySnapshot {

    private final List<InventorySlot> slots;

    public InventorySnapshot(Inventory inventory) {
        this.slots = new ArrayList<>(inventory.snapshotSlots());
    }

    public void restore(Inventory inventory) {
        inventory.restoreSlots(slots);
    }
}
