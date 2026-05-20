package ncu.cs2.my_game.entity;

import javafx.geometry.Rectangle2D;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

import java.util.ArrayList;
import java.util.List;

public class SummonerBoss extends Boss {

    private final List<Enemy> minions = new ArrayList<>();
    private final double groundY;
    private final Rectangle2D[] surfaces;
    private double summonCooldown = 2.0;

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

        summonCooldown -= deltaTime;
        if (summonCooldown <= 0 && minions.size() < 4) {
            summonMinions();
            summonCooldown = 5.6;
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
        }
    }

    @Override
    public BossType getBossType() { return BossType.SUMMONER; }

    @Override
    public String getDisplayName() { return "CALLER SHADE"; }

    @Override
    public List<Enemy> getMinions() { return minions; }

    private void summonMinions() {
        Rectangle2D surface = chooseSummonSurface();
        double top = surface == null ? groundY : surface.getMinY();
        double minX = surface == null ? 20 : surface.getMinX() + 8;
        double maxX = surface == null ? 720 : surface.getMaxX() - Enemy.ENEMY_W - 8;
        double leftX = Math.max(minX, Math.min(getX() - 90, maxX));
        double rightX = Math.max(minX, Math.min(getX() + getWidth() + 45, maxX));
        addMinion(leftX, top, minX, maxX);
        if (minions.size() < 4) {
            addMinion(rightX, top, minX, maxX);
        }
    }

    private void addMinion(double spawnX, double surfaceTop, double minX, double maxX) {
        double patrolLeft = Math.max(minX, spawnX - 30);
        double patrolRight = Math.min(maxX + Enemy.ENEMY_W, spawnX + 120);
        boolean ranged = Math.random() < 0.4;
        if (ranged) {
            minions.add(new RangedEnemy(spawnX, surfaceTop - Enemy.ENEMY_H,
                patrolLeft, patrolRight, player, 0.45, 0.65, 0.9, 1.0));
        } else {
            minions.add(new Enemy(spawnX, surfaceTop - Enemy.ENEMY_H,
                patrolLeft, patrolRight, 0.55, 0.75, 1.0));
        }
    }

    private Rectangle2D chooseSummonSurface() {
        Rectangle2D best = null;
        double bestScore = Double.MAX_VALUE;
        double playerCenter = player.getX() + player.getWidth() / 2.0;
        for (Rectangle2D surface : surfaces) {
            if (surface.getWidth() < 68.0) continue;
            double center = surface.getMinX() + surface.getWidth() / 2.0;
            double score = Math.abs(center - playerCenter) + Math.random() * 120.0;
            if (score < bestScore) {
                bestScore = score;
                best = surface;
            }
        }
        return best;
    }
}
