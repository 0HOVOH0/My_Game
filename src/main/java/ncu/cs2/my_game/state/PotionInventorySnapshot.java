package ncu.cs2.my_game.state;

import ncu.cs2.my_game.item.InventorySlot;
import ncu.cs2.my_game.item.PotionInventory;

import java.util.ArrayList;
import java.util.List;

/**
 * Lightweight checkpoint state for dedicated potion slots.
 */
public class PotionInventorySnapshot {

    private final List<InventorySlot> slots;

    public PotionInventorySnapshot(PotionInventory inventory) {
        slots = new ArrayList<>(inventory.snapshotSlots());
    }

    public void restore(PotionInventory inventory) {
        inventory.restoreSlots(slots);
    }
}
