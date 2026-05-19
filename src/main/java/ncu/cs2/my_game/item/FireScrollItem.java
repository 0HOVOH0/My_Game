package ncu.cs2.my_game.item;

import javafx.scene.paint.Color;

public class FireScrollItem extends PickupItem {

    public FireScrollItem(double x, double y) {
        super(x, y, PickupType.FIRE_SCROLL);
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
