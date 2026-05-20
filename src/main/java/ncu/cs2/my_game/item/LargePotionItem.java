package ncu.cs2.my_game.item;

import javafx.scene.paint.Color;

public class LargePotionItem extends PickupItem {

    private static final int HEAL_AMOUNT = 50;

    public LargePotionItem(double x, double y) {
        this(x, y, 1);
    }

    public LargePotionItem(double x, double y, int quantity) {
        super(x, y, PickupType.LARGE_POTION, quantity);
    }

    @Override
    public void use(UseContext context) {
        context.getPlayer().setHp(context.getPlayer().getHp() + HEAL_AMOUNT);
    }

    @Override
    protected Color getFillColor() { return Color.DARKGREEN; }

    @Override
    protected String getSymbol() { return "L"; }
}
