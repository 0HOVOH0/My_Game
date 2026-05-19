package ncu.cs2.my_game.fsm;

import ncu.cs2.my_game.entity.Entity;

/**
 * Boss 專用狀態機，繼承 StateMachine&lt;BossState&gt;。
 *
 * <p>狀態轉換圖：</p>
 * <pre>
 *  IDLE ──(1s)──▶ CHASE ──(距離&lt;80 且 HP&lt;60%)──▶ DASH ──(0.5s)──▶ CHASE
 *                  │                                                   ▲
 *                  └──(HP&lt;30%)──▶ RAGE ─────────────────────────────┘
 *                                                (HURT 受任意狀態觸發)
 *  任意狀態 ──(被攻擊)──▶ HURT ──(0.3s)──▶ CHASE
 *  任意狀態 ──(HP=0)────▶ DEAD（終態）
 * </pre>
 */
public class BossStateMachine extends StateMachine<BossState> {

    // ── Boss AI 常數 ──────────────────────────────────────────────────────────

    /** IDLE 持續時間（秒） */
    private static final double IDLE_DURATION      = 1.0;

    /** HURT 硬直時間（秒） */
    private static final double HURT_DURATION      = 0.3;

    /** DASH 衝刺時間（秒） */
    private static final double DASH_DURATION      = 0.5;

    /** RAGE 射擊間隔（秒） */
    private static final double RAGE_FIRE_INTERVAL = 1.5;

    /** Boss 可施放遠程火球的最大距離 */
    private static final double CAST_RANGE = 560.0;

    /** 玩家偏遠時的施法降頻距離 */
    private static final double FAR_CAST_RANGE = 380.0;

    /** DASH 結束後的冷卻時間（秒）；防止返回 CHASE 後立即再次衝刺 */
    private static final double DASH_COOLDOWN = 2.5;

    /** CHASE 移動速度（像素 / 秒） */
    private static final double CHASE_SPEED        = 90.0;

    /** DASH 衝刺速度（像素 / 秒） */
    private static final double DASH_SPEED         = 180.0;

    /** 觸發 DASH 的玩家距離閾值（像素）；設為 120 以確保玩家在攻擊範圍內時能可靠觸發 */
    private static final double DASH_TRIGGER_RANGE = 120.0;

    /** 進入 DASH 的 HP 上限比例（60%） */
    private static final double DASH_HP_THRESHOLD  = 0.60;

    /** 進入 RAGE 的 HP 上限比例（30%） */
    private static final double RAGE_HP_THRESHOLD  = 0.30;

    // ── 依賴參考 ─────────────────────────────────────────────────────────────

    /** Boss 實體，控制其速度與 HP */
    private final Entity boss;

    /** 玩家實體，用於讀取位置以計算追擊方向 */
    private final Entity player;

    /**
     * 發射投射物的回調函式。
     * 由外部（GameScene）傳入，確保 BossStateMachine 不直接依賴具體的投射物類別。
     * 範例：{@code () -> projectiles.add(new Projectile(boss.getX(), boss.getY(), dirX))}
     */
    private final Runnable onFireProjectile;

    // ── 計時器 ───────────────────────────────────────────────────────────────

    /** RAGE 狀態的射擊累計計時器（秒），進入 RAGE 時歸零 */
    private double rageFireTimer;

    /** 一般遠程施法冷卻（秒） */
    private double spellCooldown;

    /** DASH 冷卻剩餘時間（秒）；> 0 時 CHASE 不會觸發 DASH */
    private double dashCooldown = 0;

    // ─────────────────────────────────────────────────────────────────────────

    /**
     * 建構子：注入 Boss、玩家實體與射擊回調，初始狀態為 IDLE。
     *
     * @param boss              Boss 實體
     * @param player            玩家實體（只讀取位置）
     * @param onFireProjectile  發射投射物的回調；RAGE 時每 1.5 秒觸發一次
     */
    public BossStateMachine(Entity boss, Entity player, Runnable onFireProjectile) {
        super(BossState.IDLE);
        this.boss             = boss;
        this.player           = player;
        this.onFireProjectile = onFireProjectile;
        this.rageFireTimer    = 0.0;
        this.spellCooldown    = randomSpellCooldown(0);
    }

    // ── update ────────────────────────────────────────────────────────────────

    /**
     * 每幀更新：累加 stateTimer，再依當前狀態呼叫對應邏輯。
     * DEAD 狀態直接跳過，避免任何額外計算。
     *
     * @param deltaTime 時間差（秒）
     */
    @Override
    public void update(double deltaTime) {
        // 死亡後停止所有更新
        if (currentState == BossState.DEAD) return;

        // 累加當前狀態的停留時間
        stateTimer += deltaTime;

        // DASH 冷卻遞減（在所有狀態中持續計時）
        if (dashCooldown > 0) dashCooldown -= deltaTime;
        if (spellCooldown > 0) spellCooldown -= deltaTime;

        switch (currentState) {
            case IDLE  -> updateIdle();
            case CHASE -> updateChase();
            case DASH  -> updateDash();
            case RAGE  -> updateRage(deltaTime);
            case HURT  -> updateHurt();
            case DEAD  -> { /* 已在上方攔截 */ }
        }
    }

    // ── 各狀態邏輯 ────────────────────────────────────────────────────────────

    /**
     * IDLE：Boss 原地停止，等待 {@value #IDLE_DURATION} 秒後切換至 CHASE。
     */
    private void updateIdle() {
        // 待機時速度歸零
        boss.setVelocityX(0);

        // 等待時間結束後開始追擊
        if (stateTimer >= IDLE_DURATION) {
            transitionTo(BossState.CHASE);
        }
    }

    /**
     * CHASE：朝玩家方向以 {@value #CHASE_SPEED} px/s 移動。
     *
     * <p>轉換條件（RAGE 優先於 DASH）：</p>
     * <ul>
     *   <li>HP &lt; 30% → RAGE</li>
     *   <li>HP &lt; 60% 且玩家距離 &lt; {@value #DASH_TRIGGER_RANGE} → DASH</li>
     * </ul>
     */
    private void updateChase() {
        double dx       = player.getX() - boss.getX();
        double distance = Math.abs(dx);

        // 朝玩家方向移動
        boss.setVelocityX(dx > 0 ? CHASE_SPEED : -CHASE_SPEED);

        // RAGE 優先：血量低於 30% 立即進入狂暴
        if (getHpRatio() < RAGE_HP_THRESHOLD) {
            rageFireTimer = 0;    // 進入 RAGE 時重置射擊計時器，避免立即發射
            transitionTo(BossState.RAGE);
            return;
        }

        // DASH：血量低於 60%、距離夠近，且冷卻結束才能衝刺
        if (distance < DASH_TRIGGER_RANGE && getHpRatio() < DASH_HP_THRESHOLD && dashCooldown <= 0) {
            transitionTo(BossState.DASH);
            return;
        }

        tryCastSpell(distance);
    }

    /**
     * DASH：以 {@value #DASH_SPEED} px/s 朝玩家方向快速衝刺。
     * 持續 {@value #DASH_DURATION} 秒後速度歸零並回到 CHASE。
     */
    private void updateDash() {
        double dx = player.getX() - boss.getX();
        boss.setVelocityX(dx > 0 ? DASH_SPEED : -DASH_SPEED);

        // 衝刺時間結束，停止並回到 CHASE，啟動冷卻
        if (stateTimer >= DASH_DURATION) {
            boss.setVelocityX(0);
            dashCooldown = DASH_COOLDOWN;
            transitionTo(BossState.CHASE);
        }
    }

    /**
     * RAGE：狂暴模式，仍以 CHASE 速度追擊玩家，並每 {@value #RAGE_FIRE_INTERVAL} 秒發射投射物。
     * RAGE 為持久狀態，不會自動離開（只有 HURT / DEAD 可打斷）。
     *
     * @param deltaTime 時間差（秒），用於累加射擊計時器
     */
    private void updateRage(double deltaTime) {
        // 繼續追擊玩家
        double dx = player.getX() - boss.getX();
        boss.setVelocityX(dx > 0 ? CHASE_SPEED : -CHASE_SPEED);

        // 累加射擊計時器，達到間隔則觸發發射回調
        rageFireTimer += deltaTime;
        if (rageFireTimer >= RAGE_FIRE_INTERVAL) {
            rageFireTimer = 0;
            onFireProjectile.run();   // 通知外部產生投射物
        }

        tryCastSpell(Math.abs(dx));
    }

    /**
     * HURT：硬直狀態，停止移動，持續 {@value #HURT_DURATION} 秒後回到 CHASE。
     */
    private void updateHurt() {
        // 硬直時強制停止
        boss.setVelocityX(0);

        // 硬直結束後回到追擊
        if (stateTimer >= HURT_DURATION) {
            transitionTo(BossState.CHASE);
        }
    }

    // ── 外部觸發 API ──────────────────────────────────────────────────────────

    /**
     * 通知 Boss 受到傷害。
     *
     * <p>扣血後判斷：</p>
     * <ol>
     *   <li>HP &le; 0 → 切換至 DEAD（終態）</li>
     *   <li>否則 → 切換至 HURT（不可在 HURT/DEAD 期間再次觸發）</li>
     * </ol>
     *
     * @param damage 扣除的血量（正整數）
     */
    public void onHit(int damage) {
        // 死亡後不再接受傷害
        if (currentState == BossState.DEAD) return;

        // 套用傷害
        boss.setHp(boss.getHp() - damage);

        // HP 歸零進入終態
        if (!boss.isAlive()) {
            boss.setVelocityX(0);
            transitionTo(BossState.DEAD);
            return;
        }

        // 非硬直中才觸發硬直（避免硬直被重置）
        if (currentState != BossState.HURT) {
            transitionTo(BossState.HURT);
        }
    }

    // ── 私有工具 ──────────────────────────────────────────────────────────────

    /**
     * 計算 Boss 目前的 HP 比例。
     *
     * @return hp / maxHp，範圍 [0.0, 1.0]
     */
    private double getHpRatio() {
        return (double) boss.getHp() / boss.getMaxHp();
    }

    /**
     * 依玩家距離決定是否施放火球；距離太遠時停止施法，
     * 偏遠時增加下一次施法冷卻。
     */
    private void tryCastSpell(double distance) {
        if (distance > CAST_RANGE) return;
        if (spellCooldown > 0) return;

        onFireProjectile.run();
        spellCooldown = randomSpellCooldown(distance);
    }

    /**
     * 產生 1-3 秒基礎冷卻；玩家偏遠時額外增加 1.5 秒。
     */
    private double randomSpellCooldown(double distance) {
        double cooldown = 1.0 + Math.random() * 2.0;
        if (distance > FAR_CAST_RANGE) cooldown += 1.5;
        return cooldown;
    }
}
