package ncu.cs2.my_game.entity;

import javafx.geometry.Rectangle2D;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

import java.util.ArrayList;
import java.util.List;

public class GroundSpikeBoss extends Boss {

    private final List<GroundSpike> spikes = new ArrayList<>();
    private final double groundY;
    private final Rectangle2D[] surfaces;
    private double spikeCooldown = 1.2;

    public GroundSpikeBoss(double x, double y, Player player, int maxHp, double groundY) {
        this(x, y, player, maxHp, groundY, new Rectangle2D[0]);
    }

    public GroundSpikeBoss(double x, double y, Player player, int maxHp,
                           double groundY, Rectangle2D[] surfaces) {
        super(x, y, player, maxHp);
        this.groundY = groundY;
        this.surfaces = surfaces == null ? new Rectangle2D[0] : surfaces;
    }

    @Override
    public void update(double deltaTime) {
        super.update(deltaTime);
        spikes.removeIf(spike -> !spike.isAlive());
        for (GroundSpike spike : spikes) {
            spike.update(deltaTime);
        }
        if (!isAlive()) return;

        spikeCooldown -= deltaTime;
        if (spikeCooldown <= 0) {
            castSpikePattern();
            spikeCooldown = 2.8;
        }
    }

    @Override
    public void draw(GraphicsContext gc) {
        for (GroundSpike spike : spikes) {
            spike.draw(gc);
        }
        super.draw(gc);
        if (isAlive()) {
            gc.setFill(Color.DARKRED);
            gc.fillRect(getX() + 8, getY() + 10, getWidth() - 16, 10);
        }
    }

    @Override
    public BossType getBossType() { return BossType.GROUND_SPIKE; }

    @Override
    public String getDisplayName() { return "SPIKE TYRANT"; }

    @Override
    public List<GroundSpike> getGroundSpikes() { return spikes; }

    @Override
    protected void clearOwnedEntities() {
        spikes.clear();
    }

    private void castSpikePattern() {
        double playerCenter = player.getX() + player.getWidth() / 2.0;
        Rectangle2D surface = findSurfaceNearPlayer(playerCenter);
        double surfaceY = surface == null ? groundY : surface.getMinY();
        int maxCount = surface == null ? 4 : Math.max(1, (int) (surface.getWidth() / 86.0));
        int count = Math.min(maxCount, 2 + (int) (Math.random() * 3));
        double spacing = 62.0;
        double start = playerCenter - (count - 1) * spacing / 2.0;
        for (int i = 0; i < count; i++) {
            double jitter = (Math.random() - 0.5) * 12.0;
            double rawX = start + i * spacing + jitter - GroundSpike.WIDTH / 2.0;
            double targetX = surface == null
                ? Math.max(20, Math.min(rawX, 730))
                : Math.max(surface.getMinX(), Math.min(rawX, surface.getMaxX() - GroundSpike.WIDTH));
            spikes.add(new GroundSpike(targetX, surfaceY));
        }
    }

    private Rectangle2D findSurfaceNearPlayer(double playerCenter) {
        Rectangle2D best = null;
        double bestScore = Double.MAX_VALUE;
        double playerBottom = player.getY() + player.getHeight();
        for (Rectangle2D surface : surfaces) {
            if (!isSpikeSurface(surface)) continue;
            boolean horizontallyNear = playerCenter >= surface.getMinX() - 70
                && playerCenter <= surface.getMaxX() + 70;
            if (!horizontallyNear) continue;
            double score = Math.abs(surface.getMinY() - playerBottom)
                + Math.abs((surface.getMinX() + surface.getWidth() / 2.0) - playerCenter) * 0.15;
            if (score < bestScore) {
                bestScore = score;
                best = surface;
            }
        }
        return best;
    }

    private boolean isSpikeSurface(Rectangle2D surface) {
        if (surface.getWidth() < GroundSpike.WIDTH + 12.0) return false;
        if (surface.getMinY() <= 0) return false;
        return true;
    }
}
