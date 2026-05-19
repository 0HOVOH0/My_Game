package ncu.cs2.my_game;

/**
 * 全域常數設定檔，集中管理所有遊戲參數。
 * 使用 final class 並隱藏建構子，防止實例化。
 */
public final class Config {

    // ── 視窗設定 ──────────────────────────────────────────────────────────────

    /** 視窗寬度（像素） */
    public static final int WINDOW_WIDTH  = 800;

    /** 視窗高度（像素） */
    public static final int WINDOW_HEIGHT = 600;

    // ── 物理設定 ──────────────────────────────────────────────────────────────

    /** 重力加速度（像素 / 秒²），正值向下 */
    public static final double GRAVITY      = 980.0;

    /** 跳躍初速度（像素 / 秒），負值向上 */
    public static final double JUMP_FORCE   = -500.0;

    /** 玩家水平移動速度（像素 / 秒） */
    public static final double PLAYER_SPEED = 200.0;

    // ── 遊戲設定 ──────────────────────────────────────────────────────────────

    /** 目標幀率（FPS） */
    public static final int FPS          = 60;

    /** 玩家碰撞框寬度（像素） */
    public static final int PLAYER_WIDTH  = 28;

    /** 玩家碰撞框高度（像素） */
    public static final int PLAYER_HEIGHT = 42;

    /** 玩家最大血量 */
    public static final int PLAYER_MAX_HP = 100;

    /** Boss 最大血量 */
    public static final int BOSS_MAX_HP   = 600;

    /** 地板厚度（像素）；同時作為地板 Y 偏移量 */
    public static final int GROUND_THICKNESS = 50;

    /** 每幀 deltaTime 上限（秒），防止大幀跳躍破壞物理 */
    public static final double MAX_DELTA_TIME = 0.05;

    // 防止外部實例化
    private Config() {}
}
