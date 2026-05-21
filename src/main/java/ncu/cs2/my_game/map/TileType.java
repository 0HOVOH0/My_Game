package ncu.cs2.my_game.map;

/**
 * Tile definitions for procedural platform dungeon levels.
 */
public enum TileType {
    EMPTY(false, false, false, false, false),
    FLOOR(true, true, false, false, false),
    WALL(true, false, false, false, false),
    PLATFORM(false, true, false, false, false),
    ONE_WAY_PLATFORM(false, true, true, false, false),
    LADDER(false, false, false, true, false),
    SPIKE(false, false, false, false, true),
    SPAWN(false, false, false, false, false),
    EXIT(false, false, false, false, false),
    DECORATION(false, false, false, false, false);

    private final boolean solid;
    private final boolean standable;
    private final boolean oneWay;
    private final boolean climbable;
    private final boolean hazardous;

    TileType(boolean solid, boolean standable, boolean oneWay, boolean climbable, boolean hazardous) {
        this.solid = solid;
        this.standable = standable;
        this.oneWay = oneWay;
        this.climbable = climbable;
        this.hazardous = hazardous;
    }

    public boolean isSolid() { return solid; }

    public boolean isStandable() { return standable; }

    public boolean isOneWay() { return oneWay; }

    public boolean isClimbable() { return climbable; }

    public boolean isHazardous() { return hazardous; }
}
