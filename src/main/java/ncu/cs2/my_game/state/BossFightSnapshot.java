package ncu.cs2.my_game.state;

import ncu.cs2.my_game.entity.BossType;

/**
 * Captures the state from the moment the player enters the boss fight.
 * Retry restores this snapshot instead of creating a new randomized boss run.
 */
public class BossFightSnapshot {
    private final StageSnapshot stageSnapshot;
    private final BossType bossType;
    private final int selectedInventorySlot;

    public BossFightSnapshot(StageSnapshot stageSnapshot, BossType bossType,
                             int selectedInventorySlot) {
        this.stageSnapshot = stageSnapshot;
        this.bossType = bossType;
        this.selectedInventorySlot = selectedInventorySlot;
    }

    public StageSnapshot getStageSnapshot() {
        return stageSnapshot;
    }

    public BossType getBossType() {
        return bossType;
    }

    public int getSelectedInventorySlot() {
        return selectedInventorySlot;
    }
}
