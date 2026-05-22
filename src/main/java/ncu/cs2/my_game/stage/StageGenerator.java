package ncu.cs2.my_game.stage;

import java.util.Random;

/**
 * 簡單關卡生成器。每輪 Boss 前由 Main 固定插入 Shop，本類別只負責一般關卡。
 */
public class StageGenerator {

    private final Random random = new Random();
    private StageType lastType = StageType.COMBAT;
    private int generatedCount = 0;

    public StageDefinition nextStage() {
        generatedCount++;
        StageType type = rollType();
        lastType = type;

        int supplies = switch (type) {
            case EXPLORATION -> 4;
            case PLATFORM -> 2;
            case ELITE -> 1;
            default -> 2;
        };
        double dropBonus = type == StageType.ELITE ? 0.18 : type == StageType.EXPLORATION ? 0.08 : 0.0;
        return new StageDefinition(type, generatedCount, supplies, dropBonus, random.nextLong());
    }

    private StageType rollType() {
        StageType[] pool = {StageType.COMBAT, StageType.PLATFORM, StageType.EXPLORATION, StageType.ELITE};
        for (int attempts = 0; attempts < 6; attempts++) {
            StageType candidate = pool[random.nextInt(pool.length)];
            if (candidate != lastType) return candidate;
        }
        return StageType.COMBAT;
    }
}
