package ncu.cs2.my_game.item;

import javafx.scene.paint.Color;

public class IceScrollItem extends PickupItem {

    public IceScrollItem(double x, double y) {
        this(x, y, 1);
    }

    public IceScrollItem(double x, double y, int quantity) {
        super(x, y, PickupType.ICE_SCROLL, quantity);
    }

    @Override
    public void use(UseContext context) {
        context.shootIceProjectile();
    }

    @Override
    protected Color getFillColor() { return Color.DEEPSKYBLUE; }

    @Override
    protected String getSymbol() { return "I"; }
}
