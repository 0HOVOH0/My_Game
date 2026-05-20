package ncu.cs2.my_game.physics;

import javafx.geometry.Rectangle2D;
import ncu.cs2.my_game.entity.Entity;

/**
 * 碰撞偵測靜態工具類別。
 * 所有方法皆為 static，不需要實例化即可使用。
 */
public final class Collision {

    // 防止實例化
    private Collision() {}

    // ─────────────────────────────────────────────────────────────────────────

    /**
     * 檢查兩個軸對齊矩形（AABB）是否發生重疊碰撞。
     *
     * @param a 第一個碰撞框
     * @param b 第二個碰撞框
     * @return 兩框有任何重疊時回傳 {@code true}
     */
    public static boolean checkAABB(Rectangle2D a, Rectangle2D b) {
        // 任一軸上無重疊則必定沒有碰撞（分離軸定理簡化版）
        if (a.getMaxX() <= b.getMinX()) return false;
        if (b.getMaxX() <= a.getMinX()) return false;
        if (a.getMaxY() <= b.getMinY()) return false;
        if (b.getMaxY() <= a.getMinY()) return false;
        return true;
    }

    // ─────────────────────────────────────────────────────────────────────────

    /**
     * 計算 a 相對於 b 在 X 軸上的碰撞深度（穿透量）。
     * 回傳值為有號數：負值代表 a 應向左移動，正值代表應向右移動。
     * 若兩框未碰撞則回傳 0。
     *
     * @param a 要被推開的碰撞框（通常是移動實體）
     * @param b 靜止的碰撞框（牆壁、敵人等）
     * @return X 軸穿透深度（像素），附帶推開方向
     */
    public static double getOverlapX(Rectangle2D a, Rectangle2D b) {
        if (!checkAABB(a, b)) return 0;

        // X 軸穿透量 = 兩框在 X 軸上的重疊長度
        double overlap = Math.min(a.getMaxX(), b.getMaxX())
                       - Math.max(a.getMinX(), b.getMinX());

        // 依據 a 的中心相對 b 的中心決定推開方向
        double aCenterX = a.getMinX() + a.getWidth()  / 2.0;
        double bCenterX = b.getMinX() + b.getWidth()  / 2.0;
        return aCenterX < bCenterX ? -overlap : overlap;
    }

    /**
     * 計算 a 相對於 b 在 Y 軸上的碰撞深度（穿透量）。
     * 回傳值為有號數：負值代表 a 應向上移動，正值代表應向下移動。
     * 若兩框未碰撞則回傳 0。
     *
     * @param a 要被推開的碰撞框（通常是移動實體）
     * @param b 靜止的碰撞框（地板、天花板等）
     * @return Y 軸穿透深度（像素），附帶推開方向
     */
    public static double getOverlapY(Rectangle2D a, Rectangle2D b) {
        if (!checkAABB(a, b)) return 0;

        // Y 軸穿透量 = 兩框在 Y 軸上的重疊長度
        double overlap = Math.min(a.getMaxY(), b.getMaxY())
                       - Math.max(a.getMinY(), b.getMinY());

        // 依據 a 的中心相對 b 的中心決定推開方向
        double aCenterY = a.getMinY() + a.getHeight() / 2.0;
        double bCenterY = b.getMinY() + b.getHeight() / 2.0;
        return aCenterY < bCenterY ? -overlap : overlap;
    }

    // ─────────────────────────────────────────────────────────────────────────

    /**
     * 單向平台碰撞：只判定實體從上方落下時踩到平台的情形。
     * 實體向上移動（velocityY &lt; 0）時穿越平台，不觸發碰撞。
     *
     * 判定條件：
     * <ol>
     *   <li>實體垂直速度 &ge; 0（向下或靜止）</li>
     *   <li>實體底部已落到平台頂面或以下</li>
     *   <li>實體底部尚未低於平台底面（未完全穿透）</li>
     *   <li>水平範圍與平台有重疊</li>
     * </ol>
     *
     * @param e        需要判定的移動實體
     * @param platform 平台的碰撞框
     * @return 符合從上方落下踩踏條件時回傳 {@code true}
     */
    public static boolean checkPlatform(Entity e, Rectangle2D platform) {
        // 向上移動時不與單向平台碰撞
        if (e.getVelocityY() < 0) return false;

        Rectangle2D hitbox = e.getHitbox();

        // 水平方向必須有重疊才算踩到平台
        boolean overlapH = hitbox.getMaxX() > platform.getMinX()
                        && hitbox.getMinX() < platform.getMaxX();
        if (!overlapH) return false;

        // 實體底部落在平台頂面到底面之間（允許部分穿透，但不允許完全穿越）
        double entityBottom  = hitbox.getMaxY();
        double platformTop   = platform.getMinY();
        double platformBottom = platform.getMaxY();

        return entityBottom >= platformTop && entityBottom <= platformBottom;
    }

    /**
     * 雙向實體碰撞解析，適用於牆、箱子、石柱等 solid obstacle。
     *
     * @return -1 = 從上方落地，-2 = 頭撞到底部，1/2 = 左右側推開，0 = 未碰撞
     */
    public static int resolveSolid(Entity e, Rectangle2D solid) {
        Rectangle2D hitbox = e.getHitbox();
        if (!checkAABB(hitbox, solid)) return 0;

        double overlapX = getOverlapX(hitbox, solid);
        double overlapY = getOverlapY(hitbox, solid);

        if (Math.abs(overlapX) < Math.abs(overlapY)) {
            e.setX(e.getX() + overlapX);
            e.setVelocityX(0);
            return overlapX < 0 ? 1 : 2;
        }

        e.setY(e.getY() + overlapY);
        e.setVelocityY(0);
        return overlapY < 0 ? -1 : -2;
    }
}
