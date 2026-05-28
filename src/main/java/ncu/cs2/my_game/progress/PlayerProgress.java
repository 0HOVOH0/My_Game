package ncu.cs2.my_game.progress;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PlayerProgress {
    private int level = 1;
    private int exp = 0;
    private int nextExp = 100;
    private int maxHpBonus = 0;
    private int meleeDamageFlatBonus = 0;
    private double meleeDamageMultiplier = 1.0;
    private double manaRegenMultiplier = 1.0;
    private double fireballCooldownMultiplier = 1.0;
    private double speedMultiplier = 1.0;
    private double jumpMultiplier = 1.0;
    private double damageReduction = 0.0;
    private final List<TalentOption> pendingChoices = new ArrayList<>();

    public boolean addExp(int amount) {
        if (amount <= 0) return false;
        exp += amount;
        boolean leveled = false;
        while (exp >= nextExp) {
            exp -= nextExp;
            level++;
            maxHpBonus += 5;
            meleeDamageFlatBonus += 5;
            nextExp = calculateNextExp(level);
            leveled = true;
        }
        if (leveled) rollTalentChoices();
        return leveled;
    }

    private int calculateNextExp(int currentLevel) {
        return (int) Math.round(100 * Math.pow(1.32, currentLevel - 1));
    }

    private void rollTalentChoices() {
        pendingChoices.clear();
        List<TalentOption> pool = new ArrayList<>(List.of(TalentOption.values()));
        Collections.shuffle(pool);
        pendingChoices.addAll(pool.subList(0, Math.min(3, pool.size())));
    }

    public boolean hasPendingTalentChoice() {
        return !pendingChoices.isEmpty();
    }

    public List<TalentOption> getPendingChoices() {
        return Collections.unmodifiableList(pendingChoices);
    }

    public TalentOption chooseTalent(int index) {
        if (index < 0 || index >= pendingChoices.size()) return null;
        TalentOption selected = pendingChoices.get(index);
        selected.apply(this);
        pendingChoices.clear();
        return selected;
    }

    public int getLevel() { return level; }

    public int getExp() { return exp; }

    public int getNextExp() { return nextExp; }

    public int getMaxHpBonus() { return maxHpBonus; }

    public int getMeleeDamageFlatBonus() { return meleeDamageFlatBonus; }

    public double getMeleeDamageMultiplier() { return meleeDamageMultiplier; }

    public double getManaRegenMultiplier() { return manaRegenMultiplier; }

    public double getFireballCooldownMultiplier() { return fireballCooldownMultiplier; }

    public double getSpeedMultiplier() { return speedMultiplier; }

    public double getJumpMultiplier() { return jumpMultiplier; }

    public double getDamageReduction() { return damageReduction; }

    void addMaxHpBonus(int amount) {
        maxHpBonus += amount;
    }

    void addMeleeDamageMultiplier(double amount) {
        meleeDamageMultiplier += amount;
    }

    void addManaRegenMultiplier(double amount) {
        manaRegenMultiplier += amount;
    }

    void multiplyFireballCooldown(double multiplier) {
        fireballCooldownMultiplier = Math.max(0.65, fireballCooldownMultiplier * multiplier);
    }

    void addSpeedMultiplier(double amount) {
        speedMultiplier += amount;
    }

    void addJumpMultiplier(double amount) {
        jumpMultiplier += amount;
    }

    void addDamageReduction(double amount) {
        damageReduction = Math.min(0.35, damageReduction + amount);
    }
}
