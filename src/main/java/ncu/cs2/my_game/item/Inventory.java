package ncu.cs2.my_game.item;

import ncu.cs2.my_game.Config;

import java.util.ArrayList;
import java.util.List;

/**
 * 簡單背包：保存固定數量 slot，每格一種道具，同類型可堆疊。
 */
public class Inventory {

    private final List<InventorySlot> slots;

    public Inventory() {
        this(Config.INVENTORY_SIZE);
    }

    public Inventory(int capacity) {
        slots = new ArrayList<>();
        for (int i = 0; i < capacity; i++) {
            slots.add(new InventorySlot());
        }
    }

    public boolean add(PickupType type) {
        return add(type, 1);
    }

    public boolean add(PickupType type, int amount) {
        if (type == null || amount <= 0) return false;

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

        slot.getType().create(0, 0).use(context);
        slot.decrement();
        return true;
    }

    public InventorySlot replaceSlot(int slotIndex, PickupType newType, int newCount) {
        InventorySlot slot = getSlot(slotIndex);
        if (slot == null || slot.isEmpty() || newType == null || newCount <= 0) return null;

        InventorySlot dropped = slot.copy();
        slot.set(newType, newCount);
        return dropped;
    }

    public int getCount(PickupType type) {
        int total = 0;
        for (InventorySlot slot : slots) {
            if (!slot.isEmpty() && slot.getType() == type) {
                total += slot.getCount();
            }
        }
        return total;
    }

    public boolean contains(PickupType type) {
        return findSlot(type) != null;
    }

    public boolean canAccept(PickupType type) {
        return contains(type) || findEmptySlot() != null;
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

    public PickupType getSlotType(int slotIndex) {
        InventorySlot slot = getSlot(slotIndex);
        return slot == null || slot.isEmpty() ? null : slot.getType();
    }

    public int getSlotCount(int slotIndex) {
        InventorySlot slot = getSlot(slotIndex);
        return slot == null ? 0 : slot.getCount();
    }

    /**
     * 建立目前背包 slot 的輕量快照。
     */
    public List<InventorySlot> snapshotSlots() {
        List<InventorySlot> snapshot = new ArrayList<>();
        for (InventorySlot slot : slots) {
            snapshot.add(slot.copy());
        }
        return snapshot;
    }

    /**
     * 將背包還原成指定 slot 狀態。
     */
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

    /**
     * 回傳所有道具種類，供 HUD 自動迭代顯示。
     */
    public PickupType[] getDisplayTypes() {
        return PickupType.values();
    }

    private InventorySlot findSlot(PickupType type) {
        for (InventorySlot slot : slots) {
            if (!slot.isEmpty() && slot.getType() == type) {
                return slot;
            }
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
