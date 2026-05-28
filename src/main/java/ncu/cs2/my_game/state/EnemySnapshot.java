package ncu.cs2.my_game.state;

import ncu.cs2.my_game.entity.Enemy;
import ncu.cs2.my_game.entity.Player;
import ncu.cs2.my_game.entity.RangedEnemy;

/**
 * 普通敵人初始資料快照。
 */
public class EnemySnapshot {

    private final double x;
    private final double y;
    private final double patrolLeft;
    private final double patrolRight;
    private final boolean ranged;
    private final double hpMultiplier;
    private final double damageMultiplier;
    private final double speedMultiplier;
    private final double projectileSpeedMultiplier;

    public EnemySnapshot(double x, double y, double patrolLeft, double patrolRight) {
        this(x, y, patrolLeft, patrolRight, false, 1.0, 1.0, 1.0, 1.0);
    }

    public EnemySnapshot(double x, double y, double patrolLeft, double patrolRight,
                         boolean ranged, double hpMultiplier, double damageMultiplier,
                         double speedMultiplier, double projectileSpeedMultiplier) {
        this.x = x;
        this.y = y;
        this.patrolLeft = patrolLeft;
        this.patrolRight = patrolRight;
        this.ranged = ranged;
        this.hpMultiplier = hpMultiplier;
        this.damageMultiplier = damageMultiplier;
        this.speedMultiplier = speedMultiplier;
        this.projectileSpeedMultiplier = projectileSpeedMultiplier;
    }

    public Enemy createEnemy() {
        return new Enemy(x, y, patrolLeft, patrolRight, hpMultiplier, damageMultiplier, speedMultiplier);
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public Enemy createEnemy(Player player) {
        if (ranged) {
            return new RangedEnemy(x, y, patrolLeft, patrolRight, player,
                hpMultiplier, damageMultiplier, speedMultiplier, projectileSpeedMultiplier);
        }
        return createEnemy();
    }
}
