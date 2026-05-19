package ncu.cs2.my_game.item;

import javafx.scene.paint.Color;

public class BombItem extends PickupItem {

    private static final double RADIUS = 130.0;
    private static final int DAMAGE = 80;

    public BombItem(double x, double y) {
        super(x, y, PickupType.BOMB);
    }

    @Override
    public void use(UseContext context) {
        context.damageEnemiesNearPlayer(RADIUS, DAMAGE);
    }

    @Override
    protected Color getFillColor() { return Color.DIMGRAY; }

    @Override
    protected String getSymbol() { return "B"; }
}
