package ncu.cs2.my_game.stage;

public enum StageType {
    COMBAT("Combat"),
    PLATFORM("Platform"),
    EXPLORATION("Exploration"),
    ELITE("Elite"),
    SHOP("Shop"),
    BOSS("Boss");

    private final String label;

    StageType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
