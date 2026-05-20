package ncu.cs2.my_game.item;

import javafx.scene.paint.Color;

public class BombItem extends PickupItem {

    public BombItem(double x, double y) {
        this(x, y, 1);
    }

    public BombItem(double x, double y, int quantity) {
        super(x, y, PickupType.BOMB, quantity);
    }

    @Override
    public void use(UseContext context) {
        context.placeBomb();
    }

    @Override
    protected Color getFillColor() { return Color.DIMGRAY; }

    @Override
    protected String getSymbol() { return "B"; }
}
