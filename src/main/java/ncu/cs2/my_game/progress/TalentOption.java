package ncu.cs2.my_game.progress;

import java.util.function.Consumer;

public enum TalentOption {
    VITALITY("生命強化", "+20 最大生命", progress -> progress.addMaxHpBonus(20)),
    WARRIOR("戰士", "+10% 近戰傷害", progress -> progress.addMeleeDamageMultiplier(0.10)),
    MANA_FLOW("魔力回流", "+12% 魔力恢復", progress -> progress.addManaRegenMultiplier(0.12)),
    FIRE_MASTERY("火焰專精", "火球冷卻 -7%", progress -> progress.multiplyFireballCooldown(0.93)),
    SWIFT("迅捷", "+5% 移動速度", progress -> progress.addSpeedMultiplier(0.05)),
    DEFENSE("防禦", "-6% 受到傷害", progress -> progress.addDamageReduction(0.06)),
    JUMPER("跳躍強化", "+6% 跳躍高度", progress -> progress.addJumpMultiplier(0.06));

    private final String name;
    private final String description;
    private final Consumer<PlayerProgress> applier;

    TalentOption(String name, String description, Consumer<PlayerProgress> applier) {
        this.name = name;
        this.description = description;
        this.applier = applier;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public void apply(PlayerProgress progress) {
        applier.accept(progress);
    }
}
