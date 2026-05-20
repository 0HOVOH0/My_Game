package ncu.cs2.my_game.economy;

/**
 * 簡單金錢管理器，負責保存 Gold、消費與回復快照數值。
 */
public class CurrencyManager {

    private int gold;

    public int getGold() {
        return gold;
    }

    public void setGold(int gold) {
        this.gold = Math.max(0, gold);
    }

    public void addGold(int amount) {
        if (amount <= 0) return;
        gold += amount;
    }

    public boolean spendGold(int amount) {
        if (amount <= 0) return true;
        if (gold < amount) return false;
        gold -= amount;
        return true;
    }
}
