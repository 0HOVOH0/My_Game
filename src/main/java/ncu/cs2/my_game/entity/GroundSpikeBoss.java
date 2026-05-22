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
    private SpikeMode spikeMode = SpikeMode.COOLDOWN;
    private double modeTimer = 1.4;
    private double burstTimer = 0;

    private enum SpikeMode {
        COOLDOWN,
        PLAYER_SPIKE,
        SELF_SPIKE
    }

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
        if (isFrozen()) {
            super.update(deltaTime);
            spikes.removeIf(spike -> !spike.isAlive());
            for (GroundSpike spike : spikes) {
                spike.update(deltaTime);
            }
            return;
        }
        super.update(deltaTime);
        spikes.removeIf(spike -> !spike.isAlive());
        for (GroundSpike spike : spikes) {
            spike.update(deltaTime);
        }
        if (!isAlive()) return;

        updateSpikeMode(deltaTime);
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

    @Override
    public double getChaseSpeedMultiplier() { return 0.78; }

    @Override
    public double getDashSpeedMultiplier() { return 0.82; }

    @Override
    public double getSpellCooldownMultiplier() { return 1.55; }

    @Override
    public double getDashMaxStartDistance() { return 240.0; }

    @Override
    protected boolean usesBossMelee() { return false; }

    @Override
    protected boolean isMovementLockedByAbility() {
        return spikeMode == SpikeMode.PLAYER_SPIKE || spikeMode == SpikeMode.SELF_SPIKE;
    }

    private void updateSpikeMode(double deltaTime) {
        modeTimer -= deltaTime;
        if (burstTimer > 0) burstTimer -= deltaTime;

        if (spikeMode == SpikeMode.COOLDOWN) {
            if (modeTimer <= 0) {
                if (getVelocityY() > 80.0) {
                    modeTimer = 0.35;
                    return;
                }
                spikeMode = Math.random() < 0.64 ? SpikeMode.PLAYER_SPIKE : SpikeMode.SELF_SPIKE;
                modeTimer = spikeMode == SpikeMode.PLAYER_SPIKE
                    ? 2.0 + Math.random() * 3.0
                    : 1.15;
                burstTimer = 0;
            }
            return;
        }

        if (spikeMode == SpikeMode.PLAYER_SPIKE) {
            if (burstTimer <= 0) {
                castPlayerSpikeBurst();
                burstTimer = 0.88;
            }
            if (modeTimer <= 0) enterCooldown();
            return;
        }

        if (spikeMode == SpikeMode.SELF_SPIKE) {
            if (burstTimer <= 0) {
                castSelfSpikeBurst();
                burstTimer = 999;
            }
            if (modeTimer <= 0) enterCooldown();
        }
    }

    private void enterCooldown() {
        spikeMode = SpikeMode.COOLDOWN;
        modeTimer = 2.4 + Math.random() * 1.6;
        burstTimer = 0;
    }

    private void castPlayerSpikeBurst() {
        double playerCenter = player.getX() + player.getWidth() / 2.0;
        Rectangle2D surface = findSurfaceNearPlayer(playerCenter);
        double surfaceY = surface == null ? groundY : surface.getMinY();
        int maxCount = surface == null ? 3 : Math.max(1, (int) (surface.getWidth() / 110.0));
        int count = Math.min(maxCount, 2 + (int) (Math.random() * 2));
        double spacing = 78.0;
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

    private void castSelfSpikeBurst() {
        Rectangle2D surface = findSurfaceNearX(getX() + getWidth() / 2.0);
        double surfaceY = surface == null ? groundY : surface.getMinY();
        double center = getX() + getWidth() / 2.0;
        double[] offsets = {-112, -56, 56, 112};
        for (double offset : offsets) {
            double rawX = center + offset - GroundSpike.WIDTH / 2.0;
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

    private Rectangle2D findSurfaceNearX(double x) {
        Rectangle2D best = null;
        double bestScore = Double.MAX_VALUE;
        for (Rectangle2D surface : surfaces) {
            if (!isSpikeSurface(surface)) continue;
            boolean horizontallyNear = x >= surface.getMinX() - 90
                && x <= surface.getMaxX() + 90;
            if (!horizontallyNear) continue;
            double score = Math.abs(surface.getMinY() - (getY() + getHeight()))
                + Math.abs((surface.getMinX() + surface.getWidth() / 2.0) - x) * 0.1;
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
