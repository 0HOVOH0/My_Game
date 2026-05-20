package ncu.cs2.my_game;

/**
 * 全域常數設定檔，集中管理所有遊戲參數。
 * 使用 final class 並隱藏建構子，防止實例化。
 */
public final class Config {

    // ── 視窗設定 ──────────────────────────────────────────────────────────────

    /** 遊戲邏輯與 Canvas 渲染的基準寬度（像素）。 */
    public static final int BASE_WIDTH  = 800;

    /** 遊戲邏輯與 Canvas 渲染的基準高度（像素）。 */
    public static final int BASE_HEIGHT = 600;

    /** 視窗最小寬度（像素）。 */
    public static final int MIN_WIDTH = 640;

    /** 視窗最小高度（像素）。 */
    public static final int MIN_HEIGHT = 480;

    /** 舊有程式相容用：目前等同於 BASE_WIDTH。 */
    public static final int WINDOW_WIDTH  = BASE_WIDTH;

    /** 舊有程式相容用：目前等同於 BASE_HEIGHT。 */
    public static final int WINDOW_HEIGHT = BASE_HEIGHT;

    // ── 物理設定 ──────────────────────────────────────────────────────────────

    /** 重力加速度（像素 / 秒²），正值向下 */
    public static final double GRAVITY      = 980.0;

    /** 跳躍初速度（像素 / 秒），負值向上 */
    public static final double JUMP_FORCE   = -500.0;

    /** 玩家水平移動速度（像素 / 秒） */
    public static final double PLAYER_SPEED = 200.0;

    /** 玩家蹲下時水平速度倍率 */
    public static final double PLAYER_CROUCH_SPEED_MULTIPLIER = 0.5;

    /** 玩家蹲下時碰撞框高度倍率 */
    public static final double PLAYER_CROUCH_HEIGHT_MULTIPLIER = 0.5;

    /** 空中按 S 觸發快速下落的最小下落速度 */
    public static final double PLAYER_FAST_FALL_SPEED = 760.0;

    // ── 遊戲設定 ──────────────────────────────────────────────────────────────

    /** 目標幀率（FPS） */
    public static final int FPS          = 60;

    /** 玩家碰撞框寬度（像素） */
    public static final int PLAYER_WIDTH  = 28;

    /** 玩家碰撞框高度（像素） */
    public static final int PLAYER_HEIGHT = 42;

    /** 玩家最大血量 */
    public static final int PLAYER_MAX_HP = 100;

    /** 玩家最大魔力量 */
    public static final double PLAYER_MAX_MANA = 100.0;

    /** 玩家每秒自動恢復的魔力量 */
    public static final double PLAYER_MANA_REGEN_PER_SEC = 3.5;

    /** 背包可攜帶的不同道具種類數量。 */
    public static final int INVENTORY_SIZE = 3;

    /** 火球施放冷卻時間（秒） */
    public static final double FIREBALL_COOLDOWN = 2.0;

    /** 每顆火球消耗的魔力量 */
    public static final double FIREBALL_MANA_COST = 20.0;

    /** 玩家火球大小（像素） */
    public static final double FIREBALL_SIZE = 18.0;

    /** 玩家火球飛行速度（像素 / 秒） */
    public static final double FIREBALL_SPEED = 360.0;

    /** 玩家火球傷害 */
    public static final int FIREBALL_DAMAGE = 25;

    /** Boss 最大血量 */
    public static final int BOSS_MAX_HP   = 600;

    /** 地板厚度（像素）；同時作為地板 Y 偏移量 */
    public static final int GROUND_THICKNESS = 50;

    /** 每幀 deltaTime 上限（秒），防止大幀跳躍破壞物理 */
    public static final double MAX_DELTA_TIME = 0.05;

    // 防止外部實例化
    private Config() {}
}
