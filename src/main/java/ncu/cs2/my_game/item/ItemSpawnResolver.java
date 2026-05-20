package ncu.cs2.my_game.item;

import javafx.geometry.Point2D;
import javafx.geometry.Rectangle2D;
import ncu.cs2.my_game.Config;
import ncu.cs2.my_game.physics.Collision;

import java.util.ArrayList;
import java.util.List;

/**
 * 集中處理地板道具生成位置，避免掉落物彼此重疊或掉到不可行走區域。
 */
public class ItemSpawnResolver {

    private static final double SMALL_STEP = PickupItem.SIZE + 10.0;
    private static final double LARGE_STEP = PickupItem.SIZE + 14.0;
    private static final double OVERLAP_PADDING = 6.0;
    private static final int MAX_RING_RADIUS = 7;

    private final Rectangle2D ground;
    private final Rectangle2D[] platforms;
    private final double worldWidth;

    public ItemSpawnResolver(Rectangle2D ground, Rectangle2D[] platforms) {
        this.ground = ground;
        this.platforms = platforms;
        this.worldWidth = ground.getMaxX();
    }

    public Point2D findValidSpawnPosition(double preferredX, double preferredY,
                                          List<PickupItem> existingItems) {
        return findValidSpawnPosition(preferredX, preferredY, existingItems, List.of());
    }

    public Point2D findValidSpawnPosition(double preferredX, double preferredY,
                                          List<PickupItem> existingItems,
                                          List<Rectangle2D> extraOccupied) {
        double baseX = clampX(preferredX);
        double baseY = findSurfaceY(baseX, preferredY);
        Rectangle2D original = createHitbox(baseX, baseY);

        boolean originalOccupied = isSpawnOccupied(original, existingItems, extraOccupied);
        if (originalOccupied) {
            System.out.println("Item overlap detected -> relocate");
        }
        if (isValidSpawn(original, existingItems, extraOccupied)) {
            return new Point2D(baseX, baseY);
        }

        Point2D nearby = searchSmallOffsets(baseX, baseY, existingItems, extraOccupied);
        if (nearby != null) {
            logRelocation(nearby);
            return nearby;
        }

        Point2D ring = searchRings(baseX, baseY, existingItems, extraOccupied);
        if (ring != null) {
            logRelocation(ring);
            return ring;
        }

        Point2D fallback = findFallback(existingItems, extraOccupied);
        logRelocation(fallback);
        return fallback;
    }

    public boolean isSpawnOccupied(Rectangle2D hitbox, List<PickupItem> existingItems) {
        return isSpawnOccupied(hitbox, existingItems, List.of());
    }

    public boolean isSpawnOccupied(Rectangle2D hitbox, List<PickupItem> existingItems,
                                   List<Rectangle2D> extraOccupied) {
        Rectangle2D padded = expand(hitbox, OVERLAP_PADDING);
        for (PickupItem item : existingItems) {
            if (!item.isPickedUp() && Collision.checkAABB(padded, item.getHitbox())) {
                return true;
            }
        }
        for (Rectangle2D occupied : extraOccupied) {
            if (Collision.checkAABB(padded, occupied)) return true;
        }
        return false;
    }

    private Point2D searchSmallOffsets(double baseX, double baseY,
                                       List<PickupItem> existingItems,
                                       List<Rectangle2D> extraOccupied) {
        int[][] offsets = {
            {0, 0}, {1, 0}, {-1, 0}, {0, -1}, {0, 1},
            {1, -1}, {-1, -1}, {1, 1}, {-1, 1}
        };

        for (int[] offset : offsets) {
            Point2D point = resolveOffset(baseX, baseY, offset[0], offset[1], SMALL_STEP);
            if (isValidSpawn(createHitbox(point), existingItems, extraOccupied)) {
                return point;
            }
        }
        return null;
    }

    private Point2D searchRings(double baseX, double baseY,
                                List<PickupItem> existingItems,
                                List<Rectangle2D> extraOccupied) {
        for (int radius = 2; radius <= MAX_RING_RADIUS; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dy = -radius; dy <= radius; dy++) {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) != radius) continue;
                    Point2D point = resolveOffset(baseX, baseY, dx, dy, LARGE_STEP);
                    if (isValidSpawn(createHitbox(point), existingItems, extraOccupied)) {
                        return point;
                    }
                }
            }
        }
        return null;
    }

    private Point2D findFallback(List<PickupItem> existingItems,
                                 List<Rectangle2D> extraOccupied) {
        for (Rectangle2D surface : getSurfaces()) {
            double y = surface.getMinY() - PickupItem.SIZE;
            double start = Math.max(0, surface.getMinX());
            double end = Math.min(worldWidth - PickupItem.SIZE,
                                  surface.getMaxX() - PickupItem.SIZE);
            for (double x = start; x <= end; x += LARGE_STEP) {
                Rectangle2D hitbox = createHitbox(x, y);
                if (isValidSpawn(hitbox, existingItems, extraOccupied)) {
                    return new Point2D(x, y);
                }
            }
        }
        return new Point2D(clampX(24), ground.getMinY() - PickupItem.SIZE);
    }

    private Point2D resolveOffset(double baseX, double baseY, int dx, int dy, double step) {
        double x = clampX(baseX + dx * step);
        double targetY = baseY + dy * step;
        return new Point2D(x, findSurfaceY(x, targetY));
    }

    private boolean isValidSpawn(Rectangle2D hitbox, List<PickupItem> existingItems,
                                 List<Rectangle2D> extraOccupied) {
        if (hitbox.getMinX() < 0 || hitbox.getMaxX() > worldWidth) return false;
        if (hitbox.getMinY() < 0 || hitbox.getMaxY() > Config.WINDOW_HEIGHT) return false;
        if (!isSupported(hitbox)) return false;
        if (isSpawnOccupied(hitbox, existingItems, extraOccupied)) return false;
        return !intersectsTerrain(hitbox);
    }

    private boolean isSupported(Rectangle2D hitbox) {
        double bottom = hitbox.getMaxY();
        if (Math.abs(bottom - ground.getMinY()) < 0.5) return true;

        for (Rectangle2D platform : platforms) {
            boolean horizontal = hitbox.getMaxX() > platform.getMinX()
                              && hitbox.getMinX() < platform.getMaxX();
            if (horizontal && Math.abs(bottom - platform.getMinY()) < 0.5) {
                return true;
            }
        }
        return false;
    }

    private boolean intersectsTerrain(Rectangle2D hitbox) {
        Rectangle2D lifted = new Rectangle2D(hitbox.getMinX(), hitbox.getMinY(),
                                             hitbox.getWidth(), hitbox.getHeight() - 0.5);
        if (Collision.checkAABB(lifted, ground)) return true;
        for (Rectangle2D platform : platforms) {
            if (Collision.checkAABB(lifted, platform)) return true;
        }
        return false;
    }

    private double findSurfaceY(double x, double preferredY) {
        Rectangle2D bestSurface = ground;
        double bestDistance = Math.abs((ground.getMinY() - PickupItem.SIZE) - preferredY);

        for (Rectangle2D platform : platforms) {
            boolean horizontal = x + PickupItem.SIZE > platform.getMinX()
                              && x < platform.getMaxX();
            if (!horizontal) continue;

            double itemY = platform.getMinY() - PickupItem.SIZE;
            double distance = Math.abs(itemY - preferredY);
            if (distance < bestDistance) {
                bestDistance = distance;
                bestSurface = platform;
            }
        }
        return bestSurface.getMinY() - PickupItem.SIZE;
    }

    private List<Rectangle2D> getSurfaces() {
        List<Rectangle2D> surfaces = new ArrayList<>();
        surfaces.add(ground);
        for (Rectangle2D platform : platforms) {
            surfaces.add(platform);
        }
        return surfaces;
    }

    private Rectangle2D createHitbox(Point2D point) {
        return createHitbox(point.getX(), point.getY());
    }

    private Rectangle2D createHitbox(double x, double y) {
        return new Rectangle2D(x, y, PickupItem.SIZE, PickupItem.SIZE);
    }

    private Rectangle2D expand(Rectangle2D hitbox, double padding) {
        return new Rectangle2D(hitbox.getMinX() - padding, hitbox.getMinY() - padding,
                               hitbox.getWidth() + padding * 2,
                               hitbox.getHeight() + padding * 2);
    }

    private double clampX(double x) {
        return Math.max(0, Math.min(x, worldWidth - PickupItem.SIZE));
    }

    private void logRelocation(Point2D point) {
        System.out.printf("Spawn relocated to (%.1f, %.1f)%n", point.getX(), point.getY());
    }
}
