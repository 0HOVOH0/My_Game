package ncu.cs2.my_game.item;

import java.util.ArrayList;
import java.util.List;

/**
 * Two dedicated stackable slots for potion consumables.
 */
public class PotionInventory {

    public static final int CAPACITY = 2;

    private final List<InventorySlot> slots;

    public PotionInventory() {
        slots = new ArrayList<>();
        for (int i = 0; i < CAPACITY; i++) {
            slots.add(new InventorySlot());
        }
    }

    public boolean add(PickupType type, int amount) {
        if (type == null || !type.isPotion() || amount <= 0) return false;

        InventorySlot existing = findSlot(type);
        if (existing != null) {
            existing.add(amount);
            return true;
        }
        InventorySlot empty = findEmptySlot();
        if (empty == null) return false;
        empty.set(type, amount);
        return true;
    }

    public boolean useSlot(int slotIndex, UseContext context) {
        InventorySlot slot = getSlot(slotIndex);
        if (slot == null || slot.isEmpty()) return false;
        PickupItem item = slot.getType().create(0, 0);
        if (!item.canUse(context)) return false;
        item.use(context);
        slot.decrement();
        return true;
    }

    public InventorySlot replaceSlot(int slotIndex, PickupType newType, int newCount) {
        InventorySlot slot = getSlot(slotIndex);
        if (slot == null || slot.isEmpty() || newType == null
                || !newType.isPotion() || newCount <= 0) {
            return null;
        }
        InventorySlot dropped = slot.copy();
        slot.set(newType, newCount);
        return dropped;
    }

    public boolean contains(PickupType type) {
        return findSlot(type) != null;
    }

    public boolean canAccept(PickupType type) {
        return type != null && type.isPotion()
            && (contains(type) || findEmptySlot() != null);
    }

    public boolean isFull() {
        return findEmptySlot() == null;
    }

    public int getCapacity() {
        return slots.size();
    }

    public InventorySlot getSlot(int index) {
        if (index < 0 || index >= slots.size()) return null;
        return slots.get(index);
    }

    public List<InventorySlot> snapshotSlots() {
        List<InventorySlot> snapshot = new ArrayList<>();
        for (InventorySlot slot : slots) {
            snapshot.add(slot.copy());
        }
        return snapshot;
    }

    public void restoreSlots(List<InventorySlot> snapshot) {
        for (InventorySlot slot : slots) {
            slot.clear();
        }
        int limit = Math.min(slots.size(), snapshot.size());
        for (int i = 0; i < limit; i++) {
            InventorySlot saved = snapshot.get(i);
            slots.get(i).set(saved.getType(), saved.getCount());
        }
    }

    private InventorySlot findSlot(PickupType type) {
        for (InventorySlot slot : slots) {
            if (!slot.isEmpty() && slot.getType() == type) return slot;
        }
        return null;
    }

    private InventorySlot findEmptySlot() {
        for (InventorySlot slot : slots) {
            if (slot.isEmpty()) return slot;
        }
        return null;
    }
}
