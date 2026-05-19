package ncu.cs2.my_game.item;

import ncu.cs2.my_game.entity.Boss;
import ncu.cs2.my_game.entity.Enemy;
import ncu.cs2.my_game.entity.Player;

/**
 * 道具使用時需要的場景上下文。
 */
public class UseContext {

    private final Player player;
    private final Enemy[] enemies;
    private final Boss boss;

    public UseContext(Player player, Enemy[] enemies, Boss boss) {
        this.player = player;
        this.enemies = enemies;
        this.boss = boss;
    }

    public Player getPlayer() { return player; }

    /**
     * 對玩家附近的敵人造成範圍傷害。
     */
    public void damageEnemiesNearPlayer(double radius, int damage) {
        double cx = player.getX() + player.getWidth() / 2.0;
        double cy = player.getY() + player.getHeight() / 2.0;

        if (enemies != null) {
            for (Enemy enemy : enemies) {
                if (enemy == null || !enemy.isAlive()) continue;
                if (distance(cx, cy, enemy) <= radius) {
                    enemy.takeDamage(damage);
                }
            }
        }

        if (boss != null && boss.isAlive() && distance(cx, cy, boss) <= radius) {
            boss.takeDamage(damage);
        }
    }

    /**
     * 暫時降低所有敵人的移動速度。
     */
    public void slowEnemies(double duration, double multiplier) {
        if (enemies != null) {
            for (Enemy enemy : enemies) {
                if (enemy != null && enemy.isAlive()) {
                    enemy.applySlow(duration, multiplier);
                }
            }
        }
    }

    private double distance(double cx, double cy, ncu.cs2.my_game.entity.Entity entity) {
        double ex = entity.getX() + entity.getWidth() / 2.0;
        double ey = entity.getY() + entity.getHeight() / 2.0;
        double dx = ex - cx;
        double dy = ey - cy;
        return Math.sqrt(dx * dx + dy * dy);
    }
}
