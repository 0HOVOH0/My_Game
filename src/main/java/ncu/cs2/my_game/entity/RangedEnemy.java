package ncu.cs2.my_game.entity;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

import java.util.ArrayList;
import java.util.List;

/**
 * 遠程小兵：保留巡邏邏輯，玩家進入距離時朝玩家射出可躲避投射物。
 */
public class RangedEnemy extends Enemy {

    private static final double ATTACK_RANGE = 360.0;
    private static final double KEEP_DISTANCE = 170.0;
    private static final double COOLDOWN = 1.9;
    private static final double RANGED_DETECT_RANGE = 285.0;
    private static final double RANGED_CHASE_RANGE = 360.0;

    private final Player player;
    private final List<Fireball> projectiles = new ArrayList<>();
    private final double projectileSpeed;
    private final int projectileDamage;
    private double shootTimer;

    public RangedEnemy(double x, double y, double patrolLeft, double patrolRight,
                       Player player, double hpMultiplier, double damageMultiplier,
                       double speedMultiplier, double projectileSpeedMultiplier) {
        super(x, y, patrolLeft, patrolRight, hpMultiplier * 0.85,
              damageMultiplier, speedMultiplier * 0.85);
        this.player = player;
        this.projectileSpeed = 210.0 * projectileSpeedMultiplier;
        this.projectileDamage = Math.max(4, (int) Math.round(8 * damageMultiplier));
        this.shootTimer = COOLDOWN * 0.5;
    }

    @Override
    public void update(double deltaTime) {
        super.update(deltaTime);
        projectiles.removeIf(p -> !p.isAlive());
        for (Fireball projectile : projectiles) {
            projectile.update(deltaTime);
        }
        if (!isAlive() || isFrozen()) return;

        keepDistance(deltaTime);

        shootTimer -= deltaTime;
        if (shootTimer > 0) return;

        double dx = player.getX() - getX();
        double dy = player.getY() - getY();
        double distance = Math.sqrt(dx * dx + dy * dy);
        if (distance <= ATTACK_RANGE && hasLineOfSightToPlayer()) {
            shootAtPlayer();
            shootTimer = COOLDOWN;
        }
    }

    @Override
    public void draw(GraphicsContext gc) {
        super.draw(gc);
        if (isAlive()) {
            gc.setFill(Color.DARKVIOLET);
            gc.fillRect(getX() + 5, getY() + 18, getWidth() - 10, 7);
        }
        for (Fireball projectile : projectiles) {
            projectile.draw(gc);
        }
    }

    public List<Fireball> getProjectiles() {
        return projectiles;
    }

    public void clearProjectiles() {
        projectiles.clear();
    }

    @Override
    protected double getDetectRange() { return RANGED_DETECT_RANGE; }

    @Override
    protected double getChaseRange() { return RANGED_CHASE_RANGE; }

    @Override
    protected boolean usesMeleeSlash() {
        return false;
    }

    @Override
    protected boolean usesPatrolZoneDetection() {
        return false;
    }

    @Override
    protected boolean canLeavePatrolPlatformWhileChasing() {
        return false;
    }

    @Override
    protected boolean steersTowardPlayerWhileChasing() {
        return false;
    }

    private void keepDistance(double deltaTime) {
        if (!hasLineOfSightToPlayer()) return;

        double dx = player.getX() - getX();
        if (Math.abs(dx) >= KEEP_DISTANCE) return;

        double retreatDir = dx > 0 ? -1.0 : 1.0;
        double nextX = getX() + retreatDir * 55.0 * deltaTime;
        if (nextX < patrolLeft || nextX + getWidth() > patrolRight) {
            return;
        }
        setX(nextX);
    }

    private void shootAtPlayer() {
        double sx = getX() + getWidth() / 2.0 - 7;
        double sy = getY() + getHeight() * 0.62 - 7;
        double tx = player.getX() + player.getWidth() / 2.0;
        double ty = player.getY() + player.getHeight() / 2.0;
        projectiles.add(new Fireball(sx, sy, tx - sx, ty - sy,
            14.0, projectileSpeed, projectileDamage, Color.MEDIUMPURPLE, Color.WHITE));
    }
}
