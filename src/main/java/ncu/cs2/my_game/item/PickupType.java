package ncu.cs2.my_game.item;

/**
 * 可撿取道具種類。
 */
public enum PickupType {
    SMALL_POTION("小藥水", "Potion(S)") {
        @Override
        public PickupItem create(double x, double y) {
            return new SmallPotionItem(x, y);
        }
    },
    LARGE_POTION("大藥水", "Potion(L)") {
        @Override
        public PickupItem create(double x, double y) {
            return new LargePotionItem(x, y);
        }
    },
    FIRE_SCROLL("火焰卷軸", "Fire Scroll") {
        @Override
        public PickupItem create(double x, double y) {
            return new FireScrollItem(x, y);
        }
    },
    BOMB("炸彈", "Bomb") {
        @Override
        public PickupItem create(double x, double y) {
            return new BombItem(x, y);
        }
    },
    ICE_SCROLL("冰凍卷軸", "Ice Scroll") {
        @Override
        public PickupItem create(double x, double y) {
            return new IceScrollItem(x, y);
        }
    };

    private final String displayName;
    private final String hudLabel;

    PickupType(String displayName, String hudLabel) {
        this.displayName = displayName;
        this.hudLabel = hudLabel;
    }

    public String getDisplayName() { return displayName; }

    public String getHudLabel() { return hudLabel; }

    public abstract PickupItem create(double x, double y);
}
