package ncu.cs2.my_game.fsm;

/**
 * Boss 的所有可能狀態。
 * 由 BossStateMachine 控制狀態之間的轉換。
 */
public enum BossState {

    /** 原地待機，等待計時結束後開始追擊 */
    IDLE,

    /** 追擊玩家，血量低於 60% 且距離夠近時可轉入 DASH；低於 30% 可轉入 RAGE */
    CHASE,

    /** 快速衝刺，需血量低於 60%，持續 0.5 秒後回到 CHASE */
    DASH,

    /** 狂暴模式，需血量低於 30%，每 1.5 秒發射投射物 */
    RAGE,

    /** 受傷硬直，持續 0.3 秒後回到 CHASE */
    HURT,

    /** 死亡，停止所有行動 */
    DEAD
}
