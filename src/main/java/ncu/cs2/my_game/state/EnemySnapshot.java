package ncu.cs2.my_game.state;

import ncu.cs2.my_game.entity.Enemy;

/**
 * 普通敵人初始資料快照。
 */
public class EnemySnapshot {

    private final double x;
    private final double y;
    private final double patrolLeft;
    private final double patrolRight;

    public EnemySnapshot(double x, double y, double patrolLeft, double patrolRight) {
        this.x = x;
        this.y = y;
        this.patrolLeft = patrolLeft;
        this.patrolRight = patrolRight;
    }

    public Enemy createEnemy() {
        return new Enemy(x, y, patrolLeft, patrolRight);
    }
}
