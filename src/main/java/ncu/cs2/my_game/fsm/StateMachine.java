package ncu.cs2.my_game.fsm;

/**
 * 通用有限狀態機（FSM）抽象基底類別。
 * 子類別透過泛型參數指定狀態 enum，並實作 update() 中各狀態的具體行為。
 *
 * @param <S> 狀態 enum 型別（例如 BossState）
 */
public abstract class StateMachine<S extends Enum<S>> {

    /** 目前所在的狀態 */
    protected S currentState;

    /**
     * 在當前狀態停留的累計時間（秒）。
     * 每次呼叫 transitionTo() 時歸零，由 update() 中的子類別負責累加。
     */
    protected double stateTimer;

    // ─────────────────────────────────────────────────────────────────────────

    /**
     * 建構子：設定初始狀態，stateTimer 從 0 開始。
     *
     * @param initialState 進入狀態機時的起始狀態
     */
    protected StateMachine(S initialState) {
        this.currentState = initialState;
        this.stateTimer   = 0.0;
    }

    // ── 抽象方法 ──────────────────────────────────────────────────────────────

    /**
     * 每幀更新：子類別根據 currentState 呼叫對應的狀態邏輯。
     * 子類別應在方法開頭累加 stateTimer（{@code stateTimer += deltaTime}）。
     *
     * @param deltaTime 距上一幀的時間差（秒）
     */
    public abstract void update(double deltaTime);

    // ── 一般方法 ──────────────────────────────────────────────────────────────

    /**
     * 切換到指定狀態，並重置停留計時器。
     * 若目標狀態與當前狀態相同，仍會重置計時器（重新計算持續時間）。
     *
     * @param state 目標狀態
     */
    public void transitionTo(S state) {
        this.currentState = state;
        this.stateTimer   = 0.0;
    }

    // ── Getter ────────────────────────────────────────────────────────────────

    /** 回傳目前狀態 */
    public S getCurrentState() { return currentState; }

    /** 回傳在目前狀態停留的累計秒數 */
    public double getStateTimer() { return stateTimer; }
}
