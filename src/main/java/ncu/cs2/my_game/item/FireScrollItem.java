package ncu.cs2.my_game.item;

import javafx.scene.paint.Color;

public class FireScrollItem extends PickupItem {

    public FireScrollItem(double x, double y) {
        this(x, y, 1);
    }

    public FireScrollItem(double x, double y, int quantity) {
        super(x, y, PickupType.FIRE_SCROLL, quantity);
    }

    @Override
    public void use(UseContext context) {
        context.getPlayer().castEmpoweredFireball();
    }

    @Override
    protected Color getFillColor() { return Color.ORANGERED; }

    @Override
    protected String getSymbol() { return "F"; }
}
