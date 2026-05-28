package ncu.cs2.my_game.entity;

import javafx.geometry.Rectangle2D;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import ncu.cs2.my_game.Config;
import ncu.cs2.my_game.effect.MeleeSlashRenderer;
import ncu.cs2.my_game.fsm.BossState;
import ncu.cs2.my_game.fsm.BossStateMachine;
import ncu.cs2.my_game.physics.Gravity;

import java.util.ArrayList;
import java.util.List;

/**
 * Boss 實體，繼承 Entity，AI 行為由 BossStateMachine 驅動。
 * 負責整合 FSM 更新、投射物生命週期管理，以及繪製邏輯。
 */
public class Boss extends Entity {

    // ── 尺寸常數 ─────────────────────────────────────────────────────────────

    /** Boss 碰撞框寬度（像素） */
    public static final double BOSS_WIDTH  = 60.0;

    /** Boss 碰撞框高度（像素） */
    public static final double BOSS_HEIGHT = 80.0;

    /** DASH 攻擊框在前方的延伸長度（像素） */
    private static final double DASH_BOX_EXTENT = 40.0;
    private static final double MELEE_RANGE = 82.0;
    private static final double MELEE_ARC_DEGREES = 150.0;
    private static final double MELEE_WINDUP = 0.24;
    private static final double MELEE_ACTIVE = 0.14;
    private static final double MELEE_RECOVERY = 0.36;
    private static final double MELEE_COOLDOWN = 1.45;
    private static final int MELEE_DAMAGE = 8;

    // ── 依賴 ─────────────────────────────────────────────────────────────────

    /** 玩家參照；傳給 FSM 用於追蹤位置，以及決定投射物方向 */
    protected final Player player;

    /** Boss 專屬有限狀態機，管理所有 AI 狀態轉換 */
    private final BossStateMachine fsm;

    /** 目前存活的投射物列表；由 shootProjectile() 新增，update() 清除出界的 */
    protected final List<Fireball> projectiles;
    private double slowTimer;
    private double freezeTimer;
    private double slowMultiplier = 1.0;
    private boolean onGround;
    private double jumpCooldown;
    private Rectangle2D[] dashBlockers = new Rectangle2D[0];
    private Rectangle2D[] dashSurfaces = new Rectangle2D[0];
    private double dashWorldWidth = Config.WINDOW_WIDTH;
    private boolean isDead;
    private boolean deathHandled;
    private double meleeTimer;
    private double meleeCooldownTimer;
    private boolean meleeDamageApplied;
    private boolean meleeFacingRight;

    // ─────────────────────────────────────────────────────────────────────────

    /**
     * 建構子：建立 Boss 實體並初始化狀態機。
     * FSM 的射擊回調直接指向 {@link #shootProjectile()}，
     * 使 RAGE 狀態可在不知道外部容器的情況下觸發投射物。
     *
     * @param x      初始 X 座標
     * @param y      初始 Y 座標
     * @param player 玩家參照（FSM 追蹤位置用）
     */
    public Boss(double x, double y, Player player) {
        this(x, y, player, Config.BOSS_MAX_HP);
    }

    public Boss(double x, double y, Player player, int maxHp) {
        super(x, y, BOSS_WIDTH, BOSS_HEIGHT, maxHp);
        this.player      = player;
        this.projectiles = new ArrayList<>();
        this.onGround    = false;
        this.jumpCooldown = 0.8;
        this.isDead = false;
        this.deathHandled = false;
        this.meleeTimer = 0;
        this.meleeCooldownTimer = MELEE_COOLDOWN * 0.5;
        this.meleeDamageApplied = false;
        this.meleeFacingRight = true;
        this.freezeTimer = 0;
        // 將 this::shootProjectile 傳入 FSM，讓 RAGE 狀態直接呼叫
        this.fsm         = new BossStateMachine(this, player, this::shootProjectile);
    }

    // ── update ────────────────────────────────────────────────────────────────

    /**
     * 每幀更新：FSM → 重力 → 位移 → 投射物清理。
     * FSM 負責設定 velocityX；重力修改 velocityY；最後整合位移。
     *
     * @param deltaTime 時間差（秒）
     */
    @Override
    public void update(double deltaTime) {
        capturePreviousPosition();
        if (isDead || deathHandled) return;

        if (freezeTimer > 0) {
            freezeTimer -= deltaTime;
            if (freezeTimer < 0) freezeTimer = 0;
            velocityX = 0;
            meleeTimer = 0;
            Gravity.apply(this, deltaTime);
            y += velocityY * deltaTime;
            projectiles.removeIf(p -> !p.isAlive());
            for (Fireball p : projectiles) {
                p.update(deltaTime);
            }
            return;
        }

        // 1. FSM 決定水平速度與狀態轉換
        fsm.update(deltaTime);
        updateMelee(deltaTime);
        if (isMovementLockedByAbility()) {
            velocityX = 0;
        }
        if (slowTimer > 0) {
            slowTimer -= deltaTime;
            if (slowTimer <= 0) {
                slowTimer = 0;
                slowMultiplier = 1.0;
            }
        }
        if (jumpCooldown > 0) jumpCooldown -= deltaTime;
        tryJumpTowardPlayer();

        // 2. 套用重力（累加 velocityY）
        Gravity.apply(this, deltaTime);
        if (isMovementLockedByAbility()) {
            velocityX = 0;
        }

        // 3. 依速度更新座標
        x += velocityX * slowMultiplier * deltaTime;
        y += velocityY * deltaTime;

        // 4. 移除已出界或被消滅的投射物，再更新剩餘的
        projectiles.removeIf(p -> !p.isAlive());
        for (Fireball p : projectiles) {
            p.update(deltaTime);
        }
    }

    private void updateMelee(double deltaTime) {
        if (!usesBossMelee()) return;
        if (meleeCooldownTimer > 0) {
            meleeCooldownTimer -= deltaTime;
            if (meleeCooldownTimer < 0) meleeCooldownTimer = 0;
        }
        if (meleeTimer > 0) {
            meleeTimer -= deltaTime;
            if (meleeTimer < 0) meleeTimer = 0;
        }

        if (getCurrentState() == BossState.DASH) return;
        if (meleeTimer <= 0 && meleeCooldownTimer <= 0 && isPlayerInMeleeArc()) {
            startBossMelee();
        }
        if (isBossMeleeActive() && !meleeDamageApplied && isPlayerInMeleeArc()) {
            player.takeDamage(MELEE_DAMAGE);
            meleeDamageApplied = true;
        }
    }

    // ── 受傷 ─────────────────────────────────────────────────────────────────

    /**
     * 承受傷害：委派給 FSM 處理扣血與狀態切換。
     * FSM 的 onHit() 會自動判斷是否切換至 HURT 或 DEAD。
     *
     * @param amount 傷害量（正整數）
     */
    public void takeDamage(int amount) {
        if (isDead || deathHandled) return;
        fsm.onHit(amount);
        if (!isAlive()) {
            isDead = true;
            velocityX = 0;
            velocityY = 0;
        }
    }

    public void applySlow(double duration, double multiplier) {
        slowTimer = Math.max(slowTimer, duration);
        slowMultiplier = Math.min(slowMultiplier, multiplier);
    }

    public void applyFreeze(double duration) {
        freezeTimer = Math.max(freezeTimer, duration);
        velocityX = 0;
        meleeTimer = 0;
        meleeDamageApplied = false;
    }

    public boolean isFrozen() {
        return freezeTimer > 0;
    }

    public void setOnGround(boolean onGround) {
        this.onGround = onGround;
        if (onGround && velocityY > 0) velocityY = 0;
    }

    protected boolean isOnGround() {
        return onGround;
    }

    public boolean shouldDropFromPlatform() {
        if (isMovementLockedByAbility() || !onGround || player == null) return false;
        double playerBottom = player.getY() + player.getHeight();
        boolean playerBelow = player.getY() > y + height + 26.0;
        double bossCenter = x + width / 2.0;
        double playerCenter = player.getX() + player.getWidth() / 2.0;
        boolean closeEnough = Math.abs(playerCenter - bossCenter) < 170.0;
        return playerBelow && closeEnough && playerBottom > y + height + 44.0;
    }

    public void configureDashNavigation(Rectangle2D[] blockers,
                                        Rectangle2D[] surfaces,
                                        double worldWidth) {
        dashBlockers = blockers == null ? new Rectangle2D[0] : blockers;
        dashSurfaces = surfaces == null ? new Rectangle2D[0] : surfaces;
        dashWorldWidth = worldWidth;
    }

    public boolean canStartDashToward(Entity target, double dashSpeed, double dashDuration) {
        if (!onGround) return false;

        double bossCenterX = x + width / 2.0;
        double targetCenterX = target.getX() + target.getWidth() / 2.0;
        double dx = targetCenterX - bossCenterX;
        double absDx = Math.abs(dx);
        double absDy = Math.abs((target.getY() + target.getHeight() / 2.0) - (y + height / 2.0));
        if (absDx < 70.0 || absDx > getDashMaxStartDistance()) return false;
        if (absDy > 95.0) return false;

        double dir = dx >= 0 ? 1.0 : -1.0;
        double travel = dashSpeed * dashDuration;
        int samples = 8;
        for (int i = 1; i <= samples; i++) {
            double testX = x + dir * travel * i / samples;
            Rectangle2D predicted = new Rectangle2D(testX, y, width, height);
            if (predicted.getMinX() < 0 || predicted.getMaxX() > dashWorldWidth) return false;
            for (Rectangle2D blocker : dashBlockers) {
                if (predicted.intersects(blocker)) return false;
            }
        }

        double endX = x + dir * travel;
        Rectangle2D footProbe = new Rectangle2D(endX + 4, y + height, width - 8, 18);
        for (Rectangle2D surface : dashSurfaces) {
            if (footProbe.intersects(surface)) return true;
        }
        return false;
    }

    public double getChaseSpeedMultiplier() { return 0.88; }

    public double getDashSpeedMultiplier() { return 1.32; }

    public double getDashDurationMultiplier() { return 1.12; }

    public double getSpellCooldownMultiplier() { return 1.25; }

    public double getDashMaxStartDistance() { return 340.0; }

    protected boolean usesBossMelee() { return true; }

    protected boolean isMovementLockedByAbility() { return false; }

    private void tryJumpTowardPlayer() {
        if (isMovementLockedByAbility() || !onGround || jumpCooldown > 0) return;

        double bossCenter = x + width / 2.0;
        double playerCenter = player.getX() + player.getWidth() / 2.0;
        double horizontalDistance = Math.abs(playerCenter - bossCenter);
        boolean playerHigher = player.getY() + player.getHeight() < y + height - 28.0;
        if (playerHigher && horizontalDistance < 260.0) {
            velocityY = Config.JUMP_FORCE * 1.18;
            onGround = false;
            jumpCooldown = 1.15;
        }
    }

    // ── 投射物 ────────────────────────────────────────────────────────────────

    /**
     * 產生一顆朝玩家方向飛的投射物，加入 projectiles 列表。
     * 由 BossStateMachine 的 RAGE 狀態透過回調觸發，每 1.5 秒一次。
     * 投射物從 Boss 的水平中心、垂直中心飛出，Y 軸固定不受重力影響。
     */
    public void shootProjectile() {
        if (isDead || deathHandled || !isAlive()) return;
        double bossCenterX = x + width / 2.0;
        double bossCenterY = y + height / 2.0;
        double playerCenterX = player.getX() + player.getWidth() / 2.0;
        double playerCenterY = player.getY() + player.getHeight() / 2.0;
        double dirX = playerCenterX - bossCenterX;
        double dirY = playerCenterY - bossCenterY;

        // 生成位置：Boss 中心偏移以對齊投射物中心
        double size = 22.0;
        double spawnX  = x + (width  - size) / 2.0;
        double spawnY  = y + (height - size) / 2.0;

        projectiles.add(new Fireball(spawnX, spawnY, dirX, dirY,
                                     size, 280.0, 15, Color.PURPLE, Color.RED));
    }

    // ── 攻擊框 ───────────────────────────────────────────────────────────────

    /**
     * 回傳 Boss 當前的攻擊判定框。
     * 只有 DASH 狀態才有攻擊框（衝刺撞擊判定）；其他狀態回傳 null。
     * 攻擊框延伸到 Boss 前方 {@value #DASH_BOX_EXTENT} 像素處。
     *
     * @return DASH 中的攻擊矩形，或 null
     */
    public Rectangle2D getAttackBox() {
        if (isDead || deathHandled) return null;
        if (fsm.getCurrentState() != BossState.DASH) return null;

        // 依 velocityX 決定衝刺方向；若速度恰好為 0 則以玩家位置判斷
        boolean movingRight = (velocityX != 0) ? velocityX > 0
                                               : player.getX() > x;
        double boxX = movingRight ? x + width : x - DASH_BOX_EXTENT;
        return new Rectangle2D(boxX, y, DASH_BOX_EXTENT, height);
    }

    // ── draw ─────────────────────────────────────────────────────────────────

    /**
     * 繪製 Boss 本體、血量條、狀態標籤，以及所有存活的投射物。
     *
     * @param gc 畫布繪圖上下文
     */
    @Override
    public void draw(GraphicsContext gc) {
        if (isDead || deathHandled || !isAlive()) return;

        drawBody(gc);
        drawAttackBoxDebug(gc);
        drawHpBar(gc);
        drawAlertIndicator(gc);

        // 繪製所有存活的投射物
        for (Fireball p : projectiles) {
            p.draw(gc);
        }
    }

    private void drawBody(GraphicsContext gc) {
        boolean facingRight = (player.getX() + player.getWidth() / 2.0) > (x + width / 2.0);
        boolean frozen = freezeTimer > 0;
        BossState state = fsm.getCurrentState();

        // 依狀態決定配色
        Color bodyDark = frozen ? Color.web("#3a6080") : switch (state) {
            case IDLE  -> Color.web("#252535");
            case CHASE -> Color.web("#2a1010");
            case DASH  -> Color.web("#2a2008");
            case RAGE  -> Color.web("#1a0505");
            case HURT  -> Color.web("#c8c8c8");
            default    -> Color.web("#1a1a1a");
        };
        Color bodyMid = frozen ? Color.web("#5898b0") : switch (state) {
            case IDLE  -> Color.web("#383850");
            case CHASE -> Color.web("#4a2020");
            case DASH  -> Color.web("#4a3810");
            case RAGE  -> Color.web("#3a0a0a");
            case HURT  -> Color.WHITE;
            default    -> Color.web("#2a2a2a");
        };
        Color eyeColor = frozen ? Color.web("#a0e8ff") : switch (state) {
            case IDLE  -> Color.web("#7070cc");
            case CHASE -> Color.web("#ff4444");
            case DASH  -> Color.web("#ffcc00");
            case RAGE  -> Color.web("#ff1111");
            case HURT  -> Color.web("#ff8888");
            default    -> Color.web("#555555");
        };
        Color auraColor = frozen ? Color.DEEPSKYBLUE : switch (state) {
            case CHASE -> Color.web("#cc1100");
            case DASH  -> Color.web("#cc8800");
            case RAGE  -> Color.web("#ff1100");
            case HURT  -> Color.web("#ff4444");
            default    -> Color.TRANSPARENT;
        };
        double auraAlpha = frozen ? 0.20 : switch (state) {
            case CHASE -> 0.13;
            case DASH  -> 0.16;
            case RAGE  -> 0.22;
            case HURT  -> 0.28;
            default    -> 0.0;
        };

        // ── 光暈（在最底層） ───────────────────────────────────────
        if (auraAlpha > 0) {
            gc.save();
            gc.setGlobalAlpha(auraAlpha);
            gc.setFill(auraColor);
            gc.fillOval(x - 14, y - 8, width + 28, height + 16);
            gc.restore();
        }

        // ── 角（頭頂三角） ─────────────────────────────────────────
        gc.setFill(Color.web("#120a18"));
        gc.fillPolygon(
            new double[]{x + 6,  x + 17, x + 3},
            new double[]{y + 18, y + 18, y - 6}, 3);
        gc.fillPolygon(
            new double[]{x + width - 17, x + width - 6, x + width - 3},
            new double[]{y + 18, y + 18, y - 6}, 3);
        gc.setFill(bodyMid);
        gc.fillRect(x + 5,          y + 14, 4, 4);
        gc.fillRect(x + width - 9,  y + 14, 4, 4);

        // ── 頭盔 ───────────────────────────────────────────────────
        gc.setFill(bodyDark);
        gc.fillRoundRect(x + 5, y + 4, width - 10, 22, 5, 5);
        // 頭盔橫條（護目槽）
        gc.setFill(bodyMid);
        gc.fillRect(x + 4, y + 12, width - 8, 8);
        // 眼睛發光
        double eL = facingRight ? x + 8  : x + width - 24;
        double eR = facingRight ? x + 20 : x + width - 12;
        gc.save();
        gc.setGlobalAlpha(0.88);
        gc.setFill(eyeColor);
        gc.fillOval(eL, y + 13, 10, 6);
        gc.fillOval(eR, y + 13, 10, 6);
        gc.restore();

        // ── 護肩 ───────────────────────────────────────────────────
        gc.setFill(bodyDark);
        gc.fillRoundRect(x - 6,          y + 24, 14, 22, 3, 3);
        gc.fillRoundRect(x + width - 8,  y + 24, 14, 22, 3, 3);
        gc.setFill(bodyMid);
        gc.fillRect(x - 6,         y + 24, 14, 5);
        gc.fillRect(x + width - 8, y + 24, 14, 5);

        // ── 軀幹裝甲 ──────────────────────────────────────────────
        gc.setFill(bodyDark);
        gc.fillRect(x, y + 24, width, 30);
        // 甲板橫線
        gc.setFill(bodyMid);
        gc.fillRect(x + 4, y + 26, width - 8, 3);
        gc.fillRect(x + 4, y + 36, width - 8, 3);
        // 中線縱槽
        gc.fillRect(x + width / 2.0 - 2, y + 24, 4, 30);
        // 胸口魔晶（發光圓）
        gc.save();
        gc.setGlobalAlpha(0.75);
        gc.setFill(eyeColor);
        gc.fillOval(x + width / 2.0 - 9, y + 40, 18, 18);
        gc.setGlobalAlpha(0.55);
        gc.setFill(bodyDark);
        gc.fillOval(x + width / 2.0 - 6, y + 43, 12, 12);
        gc.restore();

        // ── 下半身 / 披風 ─────────────────────────────────────────
        gc.setFill(bodyDark);
        gc.fillRect(x + 4, y + 54, width - 8, 26);
        // 腿部分割陰影
        gc.save();
        gc.setGlobalAlpha(0.55);
        gc.setFill(Color.web("#06020a"));
        gc.fillRect(x + width / 2.0 - 2, y + 54, 4, 26);
        gc.restore();
        // 下緣帶
        gc.setFill(bodyMid);
        gc.fillRect(x + 4, y + 54, width - 8, 4);

        drawMeleeSlash(gc);
    }

    private void startBossMelee() {
        meleeFacingRight = player.getX() + player.getWidth() / 2.0 > x + width / 2.0;
        meleeTimer = MELEE_WINDUP + MELEE_ACTIVE + MELEE_RECOVERY;
        meleeCooldownTimer = MELEE_COOLDOWN;
        meleeDamageApplied = false;
        velocityX *= 0.35;
    }

    private boolean isBossMeleeActive() {
        double elapsed = getMeleeElapsed();
        return elapsed >= MELEE_WINDUP && elapsed <= MELEE_WINDUP + MELEE_ACTIVE;
    }

    private double getMeleeElapsed() {
        return MELEE_WINDUP + MELEE_ACTIVE + MELEE_RECOVERY - meleeTimer;
    }

    private boolean isPlayerInMeleeArc() {
        Rectangle2D target = player.getHitbox();
        double originX = meleeFacingRight ? x + width : x;
        double originY = y + height * 0.55;
        double targetX = target.getMinX() + target.getWidth() / 2.0;
        double targetY = target.getMinY() + target.getHeight() / 2.0;
        double dx = targetX - originX;
        double dy = targetY - originY;
        if (meleeFacingRight && dx < -width * 0.25) return false;
        if (!meleeFacingRight && dx > width * 0.25) return false;
        double distance = Math.sqrt(dx * dx + dy * dy);
        if (distance > MELEE_RANGE) return false;
        double angle = Math.toDegrees(Math.atan2(dy, Math.abs(dx)));
        return Math.abs(angle) <= MELEE_ARC_DEGREES / 2.0;
    }

    private void drawMeleeSlash(GraphicsContext gc) {
        if (!usesBossMelee() || meleeTimer <= 0) return;
        double originX = meleeFacingRight ? x + width : x;
        double originY = y + height * 0.55;
        MeleeSlashRenderer.draw(gc, originX, originY, meleeFacingRight,
            MELEE_RANGE, MELEE_ARC_DEGREES, getMeleeElapsed(),
            MELEE_WINDUP + MELEE_ACTIVE + MELEE_RECOVERY,
            MELEE_WINDUP, MELEE_WINDUP + MELEE_ACTIVE,
            null, Color.web("#ffb74d"));
    }

    /**
     * DASH 狀態下繪製攻擊框（半透明金色），用於除錯確認判定位置。
     * TODO: 換成衝刺特效後可移除。
     *
     * @param gc 畫布繪圖上下文
     */
    private void drawAttackBoxDebug(GraphicsContext gc) {
        Rectangle2D atkBox = getAttackBox();
        if (atkBox == null) return;

        gc.save();
        gc.setGlobalAlpha(0.4);
        gc.setFill(Color.GOLD);
        gc.fillRect(atkBox.getMinX(), atkBox.getMinY(),
                    atkBox.getWidth(), atkBox.getHeight());
        gc.restore();
    }

    /**
     * 在 Boss 頭頂繪製血量條；顏色隨血量比例從綠漸變至紅。
     *
     * @param gc 畫布繪圖上下文
     */
    private void drawHpBar(GraphicsContext gc) {
        final double barH = 8;
        final double barY = y - barH - 4;

        // 背景（深紅底）
        gc.setFill(Color.DARKRED);
        gc.fillRect(x, barY, width, barH);

        // 前景依血量比例變色
        double ratio    = (double) hp / maxHp;
        Color  barColor = ratio > 0.60 ? Color.LIMEGREEN
                        : ratio > 0.30 ? Color.ORANGE
                                       : Color.RED;
        gc.setFill(barColor);
        gc.fillRect(x, barY, width * ratio, barH);
    }

    private void drawAlertIndicator(GraphicsContext gc) {
        if (fsm.getCurrentState() == ncu.cs2.my_game.fsm.BossState.IDLE
            || fsm.getCurrentState() == ncu.cs2.my_game.fsm.BossState.DEAD) {
            return;
        }
        double bob = Math.sin(System.nanoTime() / 120_000_000.0) * 2.0;
        gc.setFont(javafx.scene.text.Font.font("Arial", javafx.scene.text.FontWeight.BOLD, 26));
        double textX = x + width / 2.0 - 6.0;
        double textY = y - 20.0 + bob;
        gc.setStroke(Color.web("#5a3b00"));
        gc.setLineWidth(3.0);
        gc.strokeText("!", textX, textY);
        gc.setFill(Color.GOLD);
        gc.fillText("!", textX, textY);
    }

    /**
     * 在血量條上方顯示當前 FSM 狀態名稱（除錯用）。
     * TODO: 換成動畫後移除。
     *
     * @param gc 畫布繪圖上下文
     */
    private void drawStateLabel(GraphicsContext gc) {
        gc.setFill(Color.LIGHTYELLOW);
        gc.fillText(fsm.getCurrentState().name(), x + 2, y - 14);
    }

    // ── Getter ────────────────────────────────────────────────────────────────

    /** 回傳目前 FSM 狀態（供 GameScene 判斷勝負條件） */
    public BossState getCurrentState() { return fsm.getCurrentState(); }

    public boolean isDead() { return isDead || deathHandled || !isAlive(); }

    public boolean isDeathHandled() { return deathHandled; }

    public boolean handleDeathCleanup() {
        if (deathHandled) return false;
        deathHandled = true;
        isDead = true;
        hp = 0;
        velocityX = 0;
        velocityY = 0;
        projectiles.clear();
        clearOwnedEntities();
        return true;
    }

    protected void clearOwnedEntities() {
        // Subclasses clear spikes or minions here.
    }

    @Override
    public Rectangle2D getHitbox() {
        if (isDead || deathHandled) {
            return new Rectangle2D(x, y, 0, 0);
        }
        return super.getHitbox();
    }

    public BossType getBossType() { return BossType.FIREBALL; }

    public String getDisplayName() { return "DARK OVERLORD"; }

    public List<GroundSpike> getGroundSpikes() { return List.of(); }

    public List<Enemy> getMinions() { return List.of(); }

    /**
     * 回傳所有存活的投射物列表（供 GameScene 做玩家碰撞判定）。
     * 命中玩家後請呼叫 {@link Projectile#destroy()} 標記消滅，
     * 下一幀 update() 的 removeIf 會自動清除。
     */
    public List<Fireball> getProjectiles() { return projectiles; }

    // ═════════════════════════════════════════════════════════════════════════
    // Projectile 內部類別
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Boss 發射的投射物。
     * 沿水平方向飛行，飛出畫面邊界後自動標記為消滅。
     * 設計為 {@code public static} 以便 GameScene 直接存取碰撞與傷害資訊。
     */
    public static class Projectile {

        /** 投射物的方形尺寸（像素） */
        public static final double SIZE   = 16.0;

        /** 飛行速度（像素 / 秒） */
        public static final double SPEED  = 300.0;

        /** 命中玩家造成的傷害值 */
        public static final int    DAMAGE = 15;

        // ── 狀態 ──────────────────────────────────────────────────────────────

        /** 目前 X 座標（左上角） */
        private double x;

        /** 目前 Y 座標（左上角），水平飛行故不隨重力改變 */
        private double y;

        /** 飛行方向：+1.0 = 向右，-1.0 = 向左 */
        private final double dirX;

        /** 是否仍存活；false 時由 Boss.update() 移除 */
        private boolean alive;

        // ─────────────────────────────────────────────────────────────────────

        /**
         * 建構子：設定初始位置與飛行方向。
         *
         * @param x    初始 X 座標
         * @param y    初始 Y 座標
         * @param dirX 飛行方向（+1 向右，-1 向左）
         */
        public Projectile(double x, double y, double dirX) {
            this.x     = x;
            this.y     = y;
            this.dirX  = dirX;
            this.alive = true;
        }

        /**
         * 每幀更新：沿 dirX 方向水平移動，超出畫面邊界時自我消滅。
         *
         * @param deltaTime 時間差（秒）
         */
        public void update(double deltaTime) {
            x += dirX * SPEED * deltaTime;

            // 超出左右邊界即消滅（上下不判定，投射物水平飛行）
            if (x + SIZE < 0 || x > Config.WINDOW_WIDTH) {
                alive = false;
            }
        }

        /**
         * 繪製投射物（橘紅色圓形）。
         * TODO: 換成火球或魔法特效精靈圖。
         *
         * @param gc 畫布繪圖上下文
         */
        public void draw(GraphicsContext gc) {
            if (!alive) return;
            gc.setFill(Color.ORANGERED);
            gc.fillOval(x, y, SIZE, SIZE);

            // 繪製發光外框增強辨識度
            gc.setStroke(Color.YELLOW);
            gc.setLineWidth(1.5);
            gc.strokeOval(x, y, SIZE, SIZE);
        }

        /**
         * 取得投射物的軸對齊碰撞框（AABB），供碰撞判定使用。
         *
         * @return 碰撞矩形
         */
        public Rectangle2D getHitbox() {
            return new Rectangle2D(x, y, SIZE, SIZE);
        }

        /**
         * 標記此投射物為已消滅（例如命中玩家後由 GameScene 呼叫）。
         * 下一幀 Boss.update() 的 removeIf 會將其從列表移除。
         */
        public void destroy() { alive = false; }

        /** 回傳投射物是否仍存活 */
        public boolean isAlive() { return alive; }

        /** 回傳命中傷害值 */
        public int getDamage() { return DAMAGE; }
    }
}
