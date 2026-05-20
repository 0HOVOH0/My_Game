package ncu.cs2.my_game.item;

import javafx.scene.paint.Color;

public class SmallPotionItem extends PickupItem {

    private static final int HEAL_AMOUNT = 20;

    public SmallPotionItem(double x, double y) {
        this(x, y, 1);
    }

    public SmallPotionItem(double x, double y, int quantity) {
        super(x, y, PickupType.SMALL_POTION, quantity);
    }

    @Override
    public void use(UseContext context) {
        context.getPlayer().setHp(context.getPlayer().getHp() + HEAL_AMOUNT);
    }

    @Override
    protected Color getFillColor() { return Color.LIMEGREEN; }

    @Override
    protected String getSymbol() { return "S"; }
}
