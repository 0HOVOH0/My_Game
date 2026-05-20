package ncu.cs2.my_game.state;

import ncu.cs2.my_game.economy.GoldPickup;

/**
 * 地板金幣快照，只保存位置與金額。
 */
public class GoldSnapshot {

    private final double x;
    private final double y;
    private final int amount;

    public GoldSnapshot(GoldPickup pickup) {
        this.x = pickup.getX();
        this.y = pickup.getY();
        this.amount = pickup.getAmount();
    }

    public GoldPickup createPickup() {
        return new GoldPickup(x, y, amount);
    }
}
