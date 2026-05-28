package ncu.cs2.my_game.entity;

import javafx.geometry.Rectangle2D;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class SummonerBoss extends Boss {

    private final List<Enemy> minions = new ArrayList<>();
    private final double groundY;
    private final Rectangle2D[] surfaces;
    private double summonCooldown = 3.0;
    private boolean summoning;
    private boolean summonReleased;
    private double summonCastTimer;
    private static final double SUMMON_WINDUP = 0.45;
    private static final double SUMMON_DURATION = 1.05;

    public SummonerBoss(double x, double y, Player player, int maxHp, double groundY) {
        this(x, y, player, maxHp, groundY, new Rectangle2D[0]);
    }

    public SummonerBoss(double x, double y, Player player, int maxHp,
                        double groundY, Rectangle2D[] surfaces) {
        super(x, y, player, maxHp);
        this.groundY = groundY;
        this.surfaces = surfaces == null ? new Rectangle2D[0] : surfaces;
    }

    @Override
    public void update(double deltaTime) {
        super.update(deltaTime);
        minions.removeIf(enemy -> !enemy.isAlive() || !isAlive());
        for (Enemy minion : minions) {
            minion.update(deltaTime);
        }
        if (!isAlive()) {
            minions.clear();
            return;
        }

        if (summoning) {
            summonCastTimer -= deltaTime;
            if (!summonReleased && summonCastTimer <= SUMMON_DURATION - SUMMON_WINDUP) {
                summonMinions();
                summonReleased = true;
            }
            if (summonCastTimer <= 0) {
                summoning = false;
                summonReleased = false;
                summonCooldown = 6.8;
            }
            return;
        }

        summonCooldown -= deltaTime;
        if (summonCooldown <= 0 && isOnGround() && !isFrozen()) {
            startSummonMode();
        }
    }

    @Override
    public void draw(GraphicsContext gc) {
        for (Enemy minion : minions) {
            minion.draw(gc);
        }
        super.draw(gc);
        if (isAlive()) {
            gc.setFill(Color.MEDIUMSEAGREEN);
            gc.fillOval(getX() + 14, getY() + 10, getWidth() - 28, 16);
            if (summoning) {
                gc.setStroke(Color.LIMEGREEN);
                gc.setLineWidth(3);
                gc.strokeOval(getX() - 10, getY() - 6, getWidth() + 20, getHeight() + 12);
            }
        }
    }

    @Override
    public BossType getBossType() { return BossType.SUMMONER; }

    @Override
    public String getDisplayName() { return "CALLER SHADE"; }

    @Override
    public List<Enemy> getMinions() { return minions; }

    @Override
    protected void clearOwnedEntities() {
        minions.clear();
    }

    @Override
    public double getChaseSpeedMultiplier() { return 0.80; }

    @Override
    public double getDashSpeedMultiplier() { return 0.82; }

    @Override
    public double getSpellCooldownMultiplier() { return 1.45; }

    @Override
    public double getDashMaxStartDistance() { return 240.0; }

    @Override
    protected boolean usesBossMelee() { return false; }

    @Override
    protected boolean isMovementLockedByAbility() {
        return summoning;
    }

    private void startSummonMode() {
        summoning = true;
        summonReleased = false;
        summonCastTimer = SUMMON_DURATION;
        setVelocityX(0);
    }

    private void summonMinions() {
        Rectangle2D surface = chooseSummonSurface();
        if (surface == null) return;
        if (minions.size() >= 4) {
            recallPatrollingMinions(surface);
            return;
        }
        double top = surface.getMinY();
        double minX = surface.getMinX() + 8;
        double maxX = surface.getMaxX() - Enemy.ENEMY_W - 8;
        double leftX = findValidSpawnX(surface, Math.max(minX, Math.min(getX() - 70, maxX)));
        double rightX = findValidSpawnX(surface, Math.max(minX, Math.min(getX() + getWidth() + 36, maxX)));
        if (!Double.isNaN(leftX)) addMinion(leftX, top, minX, maxX);
        if (minions.size() < 4) {
            if (!Double.isNaN(rightX)) addMinion(rightX, top, minX, maxX);
        }
    }

    private void addMinion(double spawnX, double surfaceTop, double minX, double maxX) {
        double patrolLeft = Math.max(minX, spawnX - 30);
        double patrolRight = Math.min(maxX + Enemy.ENEMY_W, spawnX + 120);
        boolean ranged = Math.random() < 0.22;
        Enemy minion;
        if (ranged) {
            minion = new RangedEnemy(spawnX, surfaceTop - Enemy.ENEMY_H,
                patrolLeft, patrolRight, player, 0.20, 0.45, 0.82, 0.9);
        } else {
            minion = new Enemy(spawnX, surfaceTop - Enemy.ENEMY_H,
                patrolLeft, patrolRight, 0.42, 0.50, 0.85);
        }
        minion.setMaxHp(18);
        minion.setHp(18);
        minions.add(minion);
    }

    private Rectangle2D chooseSummonSurface() {
        Rectangle2D best = null;
        double bestScore = Double.MAX_VALUE;
        double bossCenter = getX() + getWidth() / 2.0;
        double bossFeet = getY() + getHeight();
        for (Rectangle2D surface : surfaces) {
            if (surface.getWidth() < 86.0) continue;
            double center = surface.getMinX() + surface.getWidth() / 2.0;
            double samePlatformBonus = Math.abs(surface.getMinY() - bossFeet) < 24.0 ? -180.0 : 0.0;
            double verticalCost = Math.abs(surface.getMinY() - bossFeet) * 1.35;
            double horizontalCost = Math.abs(center - bossCenter);
            double score = samePlatformBonus + horizontalCost + verticalCost + Math.random() * 45.0;
            if (score < bestScore) {
                bestScore = score;
                best = surface;
            }
        }
        return best;
    }

    private double findValidSpawnX(Rectangle2D surface, double preferredX) {
        double minX = surface.getMinX() + 8;
        double maxX = surface.getMaxX() - Enemy.ENEMY_W - 8;
        double[] offsets = {0, -42, 42, -84, 84, -126, 126};
        for (double offset : offsets) {
            double x = Math.max(minX, Math.min(preferredX + offset, maxX));
            Rectangle2D hitbox = new Rectangle2D(x, surface.getMinY() - Enemy.ENEMY_H,
                Enemy.ENEMY_W, Enemy.ENEMY_H);
            if (isSpawnClear(hitbox, null)) {
                return x;
            }
        }
        return Double.NaN;
    }

    private boolean isSpawnClear(Rectangle2D hitbox) {
        return isSpawnClear(hitbox, null);
    }

    private boolean isSpawnClear(Rectangle2D hitbox, Enemy ignored) {
        for (Enemy minion : minions) {
            if (minion == ignored) continue;
            if (minion.isAlive() && hitbox.intersects(minion.getHitbox())) return false;
        }
        return !hitbox.intersects(getHitbox());
    }

    private void recallPatrollingMinions(Rectangle2D surface) {
        double bossCenter = getX() + getWidth() / 2.0;
        minions.stream()
            .filter(Enemy::isAlive)
            .sorted(Comparator.comparingDouble((Enemy enemy) ->
                Math.abs(enemy.getX() + enemy.getWidth() / 2.0 - bossCenter)).reversed())
            .limit(2)
            .forEach(minion -> recallMinionToSurface(minion, surface));
    }

    private void recallMinionToSurface(Enemy minion, Rectangle2D surface) {
        double minX = surface.getMinX() + 8;
        double maxX = surface.getMaxX() - Enemy.ENEMY_W - 8;
        double[] preferred = {
            getX() - Enemy.ENEMY_W - 22,
            getX() + getWidth() + 22,
            getX() + getWidth() / 2.0 - Enemy.ENEMY_W / 2.0
        };
        for (double baseX : preferred) {
            double x = findValidRecallX(surface, Math.max(minX, Math.min(baseX, maxX)), minion);
            if (Double.isNaN(x)) continue;
            minion.setX(x);
            minion.setY(surface.getMinY() - Enemy.ENEMY_H);
            minion.setVelocityX(0);
            minion.setVelocityY(0);
            return;
        }
    }

    private double findValidRecallX(Rectangle2D surface, double preferredX, Enemy ignored) {
        double minX = surface.getMinX() + 8;
        double maxX = surface.getMaxX() - Enemy.ENEMY_W - 8;
        double[] offsets = {0, -46, 46, -92, 92, -138, 138};
        for (double offset : offsets) {
            double x = Math.max(minX, Math.min(preferredX + offset, maxX));
            Rectangle2D hitbox = new Rectangle2D(x, surface.getMinY() - Enemy.ENEMY_H,
                Enemy.ENEMY_W, Enemy.ENEMY_H);
            if (isSpawnClear(hitbox, ignored)) return x;
        }
        return Double.NaN;
    }
}
