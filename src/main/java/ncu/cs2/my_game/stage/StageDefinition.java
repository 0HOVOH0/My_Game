package ncu.cs2.my_game.stage;

/**
 * 一次生成的關卡設定。現階段用於 Level2 模板，保留擴充到更多場景的空間。
 */
public class StageDefinition {

    private final StageType type;
    private final int difficulty;
    private final int explorationSupplyCount;
    private final double enemyDropBonus;
    private final long mapSeed;

    public StageDefinition(StageType type, int difficulty,
                           int explorationSupplyCount, double enemyDropBonus) {
        this(type, difficulty, explorationSupplyCount, enemyDropBonus, System.nanoTime());
    }

    public StageDefinition(StageType type, int difficulty,
                           int explorationSupplyCount, double enemyDropBonus,
                           long mapSeed) {
        this.type = type;
        this.difficulty = difficulty;
        this.explorationSupplyCount = explorationSupplyCount;
        this.enemyDropBonus = enemyDropBonus;
        this.mapSeed = mapSeed;
    }

    public StageType getType() {
        return type;
    }

    public int getDifficulty() {
        return difficulty;
    }

    public int getExplorationSupplyCount() {
        return explorationSupplyCount;
    }

    public double getEnemyDropBonus() {
        return enemyDropBonus;
    }

    public long getMapSeed() {
        return mapSeed;
    }

    public String getHudTitle() {
        return "STAGE " + difficulty + " - " + type.getLabel().toUpperCase();
    }
}
