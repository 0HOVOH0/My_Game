package ncu.cs2.my_game.physics;

import ncu.cs2.my_game.Config;
import ncu.cs2.my_game.entity.Entity;

/**
 * 重力系統靜態工具類別。
 * 每幀呼叫 {@link #apply} 即可對實體施加重力加速度。
 */
public final class Gravity {

    /**
     * 落下速度上限（像素 / 秒）。
     * 限制最大下落速度，防止高速時穿透地板（隧道效應）。
     */
    public static final double MAX_FALL_SPEED = 600.0;

    // 防止實例化
    private Gravity() {}

    // ─────────────────────────────────────────────────────────────────────────

    /**
     * 對實體套用重力加速度，並限制最大下落速度。
     *
     * <p>每幀將 {@link Config#GRAVITY}（像素 / 秒²）累加到實體的垂直速度，
     * 模擬自由落體。若速度超過 {@link #MAX_FALL_SPEED}，則截斷至上限值。</p>
     *
     * <p>呼叫時機：在 Entity.update() 裡，位移計算之前先呼叫此方法更新速度。</p>
     *
     * @param e         要施加重力的實體
     * @param deltaTime 距上一幀的時間差（秒），應由遊戲迴圈傳入
     */
    public static void apply(Entity e, double deltaTime) {
        // 依照 v = v₀ + a·t 累加重力加速度
        double newVY = e.getVelocityY() + Config.GRAVITY * deltaTime;

        // 限制最大下落速度，避免過快穿透薄地板（隧道效應）
        if (newVY > MAX_FALL_SPEED) {
            newVY = MAX_FALL_SPEED;
        }

        e.setVelocityY(newVY);
    }
}
