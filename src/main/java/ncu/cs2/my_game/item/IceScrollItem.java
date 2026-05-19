package ncu.cs2.my_game.item;

import javafx.scene.paint.Color;

public class IceScrollItem extends PickupItem {

    private static final double DURATION = 5.0;
    private static final double SPEED_MULTIPLIER = 0.45;

    public IceScrollItem(double x, double y) {
        super(x, y, PickupType.ICE_SCROLL);
    }

    @Override
    public void use(UseContext context) {
        context.slowEnemies(DURATION, SPEED_MULTIPLIER);
    }

    @Override
    protected Color getFillColor() { return Color.DEEPSKYBLUE; }

    @Override
    protected String getSymbol() { return "I"; }
}
