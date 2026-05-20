package ncu.cs2.my_game.item;

/**
 * 背包中的單一格位。每格只能保存一種道具，但同類型可堆疊數量。
 */
public class InventorySlot {

    private PickupType type;
    private int count;

    public InventorySlot() {
        clear();
    }

    public InventorySlot(PickupType type, int count) {
        set(type, count);
    }

    public boolean isEmpty() {
        return type == null || count <= 0;
    }

    public PickupType getType() {
        return type;
    }

    public int getCount() {
        return isEmpty() ? 0 : count;
    }

    public void set(PickupType type, int count) {
        if (type == null || count <= 0) {
            clear();
            return;
        }
        this.type = type;
        this.count = count;
    }

    public void add(int amount) {
        if (isEmpty() || amount <= 0) return;
        count += amount;
    }

    public void decrement() {
        if (isEmpty()) return;
        count--;
        if (count <= 0) clear();
    }

    public void clear() {
        type = null;
        count = 0;
    }

    public InventorySlot copy() {
        return isEmpty() ? new InventorySlot() : new InventorySlot(type, count);
    }
}
