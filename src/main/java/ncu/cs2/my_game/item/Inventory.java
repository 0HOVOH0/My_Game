package ncu.cs2.my_game.item;

import java.util.EnumMap;
import java.util.Map;

/**
 * 簡單背包：依道具種類記錄數量，使用時由道具類別套用效果。
 */
public class Inventory {

    private final Map<PickupType, Integer> counts = new EnumMap<>(PickupType.class);

    public void add(PickupType type) {
        counts.put(type, getCount(type) + 1);
    }

    public boolean use(PickupType type, UseContext context) {
        int count = getCount(type);
        if (count <= 0) return false;

        type.create(0, 0).use(context);
        counts.put(type, count - 1);
        return true;
    }

    public int getCount(PickupType type) {
        return counts.getOrDefault(type, 0);
    }

    /**
     * 建立目前背包數量的淺量快照。
     * PickupType 是 enum，可安全共用；數量值為 immutable Integer。
     */
    public Map<PickupType, Integer> snapshotCounts() {
        return new EnumMap<>(counts);
    }

    /**
     * 將背包還原成指定數量。
     *
     * @param snapshot 由 snapshotCounts() 建立的數量快照
     */
    public void restoreCounts(Map<PickupType, Integer> snapshot) {
        counts.clear();
        counts.putAll(snapshot);
    }

    /**
     * 回傳所有道具種類，供 HUD 自動迭代顯示。
     */
    public PickupType[] getDisplayTypes() {
        return PickupType.values();
    }
}
