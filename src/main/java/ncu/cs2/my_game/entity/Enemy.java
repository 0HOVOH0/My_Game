package ncu.cs2.my_game.entity;

import javafx.geometry.Rectangle2D;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import ncu.cs2.my_game.Config;
import ncu.cs2.my_game.effect.MeleeSlashRenderer;
import ncu.cs2.my_game.physics.Gravity;

/**
 * 普通敵人實體，繼承 Entity。
 * AI 行為：在指定的巡邏範圍內來回走動，碰到邊界自動轉向。
 * 與玩家接觸時造成傷害（附帶冷卻），被玩家攻擊命中則扣血，血量歸零後消失。
 */
public class Enemy extends Entity {

    // ── 常數 ─────────────────────────────────────────────────────────────────

    /** 碰撞框寬度（像素） */
    public static final double ENEMY_W = 28.0;

    /** 碰撞框高度（像素） */
    public static final double ENEMY_H = 36.0;

    /** 最大血量 */
    public static final int ENEMY_MAX_HP = 40;

    /** 接觸玩家造成的傷害量 */
    public static final int CONTACT_DAMAGE = 10;

    /** 接觸傷害冷卻時間（秒）；避免每幀都觸發傷害 */
    public static final double DAMAGE_COOLDOWN = 0.5;

    /** 巡邏移動速度（像素 / 秒） */
    private static final double PATROL_SPEED = 80.0;

    private static final double DETECT_RANGE = 215.0;
    private static final double CHASE_RANGE = 290.0;
    private static final double MEMORY_DURATION = 1.25;
    private static final double DROP_CHASE_MARGIN = 34.0;
    private static final double CHASE_JUMP_COOLDOWN = 1.25;
    private static final double STUCK_CHECK_INTERVAL = 0.75;
    private static final double STUCK_MIN_MOVE = 2.0;
    private static final double MELEE_RANGE = 46.0;
    private static final double MELEE_ARC_DEGREES = 150.0;
    private static final double MELEE_WINDUP = 0.22;
    private static final double MELEE_ACTIVE = 0.12;
    private static final double MELEE_RECOVERY = 0.28;
    private static final double MELEE_COOLDOWN = 1.25;

    // ── 巡邏邊界 ─────────────────────────────────────────────────────────────

    /**
     * 巡邏左邊界 X 座標（通常設為所在平台左端）。
     * 敵人 X 小於此值時轉為向右走。
     */
    protected final double patrolLeft;

    /**
     * 巡邏右邊界 X 座標（通常設為平台右端減去敵人寬度）。
     * 敵人 X 大於此值時轉為向左走。
     */
    protected final double patrolRight;

    // ── 狀態 ─────────────────────────────────────────────────────────────────

    /** 移動方向：+1.0 = 向右，-1.0 = 向左 */
    protected double moveDir;

    /**
     * 接觸傷害冷卻計時器（秒）。
     * 大於 0 時不對玩家施加傷害；每幀遞減至 0。
     */
    private double damageCooldown;

    /** 冰凍/緩速剩餘時間（秒） */
    private double slowTimer;
    private double freezeTimer;

    /** 目前速度倍率，1.0 表示正常速度 */
    private double speedMultiplier;
    private final double baseSpeedMultiplier;
    private final int contactDamage;
    private Player targetPlayer;
    private double memoryTimer;
    private boolean hasLineOfSight;
    private boolean onGround;
    private double chaseJumpCooldown;
    private double recoveryTimer;
    private double stuckTimer;
    private double lastResolvedX;
    private double meleeTimer;
    private double meleeCooldownTimer;
    private boolean meleeDamageApplied;
    private Rectangle2D[] navigationSurfaces = new Rectangle2D[0];
    private Rectangle2D[] movementBlockers = new Rectangle2D[0];

    // ─────────────────────────────────────────────────────────────────────────

    /**
     * 建構子：設定初始位置與巡邏範圍，初始向右走。
     *
     * @param x           初始 X 座標（左上角）
     * @param y           初始 Y 座標，建議設為平台頂面 - ENEMY_H
     * @param patrolLeft  巡邏左邊界 X（對應平台左端）
     * @param patrolRight 巡邏右邊界 X（對應平台右端 - ENEMY_W）
     */
    public Enemy(double x, double y, double patrolLeft, double patrolRight) {
        this(x, y, patrolLeft, patrolRight, 1.0, 1.0, 1.0);
    }

    public Enemy(double x, double y, double patrolLeft, double patrolRight,
                 double hpMultiplier, double damageMultiplier, double speedMultiplier) {
        super(x, y, ENEMY_W, ENEMY_H, ENEMY_MAX_HP);
        setMaxHp((int) Math.round(ENEMY_MAX_HP * hpMultiplier));
        setHp(getMaxHp());
        this.patrolLeft  = patrolLeft;
        this.patrolRight = patrolRight;
        this.moveDir     = 1.0;   // 初始向右
        this.damageCooldown = 0;
        this.slowTimer = 0;
        this.freezeTimer = 0;
        this.baseSpeedMultiplier = speedMultiplier;
        this.speedMultiplier = speedMultiplier;
        this.contactDamage = Math.max(1, (int) Math.round(CONTACT_DAMAGE * damageMultiplier));
        this.memoryTimer = 0;
        this.hasLineOfSight = false;
        this.onGround = false;
        this.chaseJumpCooldown = 0;
        this.recoveryTimer = 0;
        this.stuckTimer = 0;
        this.lastResolvedX = x;
        this.meleeTimer = 0;
        this.meleeCooldownTimer = 0;
        this.meleeDamageApplied = false;
    }

    // ── update ────────────────────────────────────────────────────────────────

    /**
     * 每幀更新：套用重力、水平巡邏移動、邊界轉向、傷害冷卻計時。
     * 死亡後不再更新任何狀態。
     *
     * @param deltaTime 時間差（秒）
     */
    @Override
    public void update(double deltaTime) {
        // 死亡後停止所有更新
        if (!isAlive()) return;

        // 1. 套用重力（累加 velocityY）
        Gravity.apply(this, deltaTime);

        if (freezeTimer > 0) {
            freezeTimer -= deltaTime;
            if (freezeTimer < 0) freezeTimer = 0;
            velocityX = 0;
            x += velocityX * deltaTime;
            y += velocityY * deltaTime;
            if (damageCooldown > 0) damageCooldown -= deltaTime;
            if (chaseJumpCooldown > 0) chaseJumpCooldown -= deltaTime;
            return;
        }

        // 2. 設定水平速度並更新位置
        if (recoveryTimer > 0) {
            recoveryTimer -= deltaTime;
            if (recoveryTimer < 0) recoveryTimer = 0;
        }

        boolean chasing = targetPlayer != null && memoryTimer > 0 && recoveryTimer <= 0;
        if (chasing) {
            double playerCenter = targetPlayer.getX() + targetPlayer.getWidth() / 2.0;
            double enemyCenter = x + width / 2.0;
            if (Math.abs(playerCenter - enemyCenter) > 8.0) {
                moveDir = playerCenter > enemyCenter ? 1.0 : -1.0;
            }
            tryJumpChase(playerCenter, enemyCenter);
        }
        if (chaseJumpCooldown > 0) {
            chaseJumpCooldown -= deltaTime;
            if (chaseJumpCooldown < 0) chaseJumpCooldown = 0;
        }
        updateMeleeTimers(deltaTime);
        avoidWallBeforeMove(chasing);
        velocityX = moveDir * PATROL_SPEED * speedMultiplier;
        x += velocityX * deltaTime;
        y += velocityY * deltaTime;

        // 3. 到達巡邏邊界時原地修正並反向
        if (!chasing && x <= patrolLeft) {
            x = patrolLeft;
            moveDir = 1.0;   // 轉向右
        } else if (!chasing && x + width >= patrolRight) {
            x = patrolRight - width;
            moveDir = -1.0;  // 轉向左
        }

        // 4. 傷害冷卻計時器遞減
        if (damageCooldown > 0) damageCooldown -= deltaTime;
        if (memoryTimer > 0) {
            memoryTimer -= deltaTime;
            if (memoryTimer < 0) memoryTimer = 0;
        }

        // 5. 緩速計時器遞減
        if (slowTimer > 0) {
            slowTimer -= deltaTime;
            if (slowTimer <= 0) {
                slowTimer = 0;
                speedMultiplier = baseSpeedMultiplier;
            }
        }
    }

    // ── 受傷 ─────────────────────────────────────────────────────────────────

    /**
     * 承受玩家攻擊傷害；直接扣血，無無敵時間（敵人設計不需要）。
     *
     * @param amount 傷害量（正整數）
     */
    public void takeDamage(int amount) {
        setHp(hp - amount);
    }

    /**
     * 暫時降低敵人巡邏速度。
     *
     * @param duration   持續時間（秒）
     * @param multiplier 速度倍率，0.5 表示半速
     */
    public void applySlow(double duration, double multiplier) {
        slowTimer = Math.max(slowTimer, duration);
        speedMultiplier = Math.min(speedMultiplier, baseSpeedMultiplier * multiplier);
    }

    public void applyFreeze(double duration) {
        freezeTimer = Math.max(freezeTimer, duration);
        velocityX = 0;
        meleeTimer = 0;
        meleeDamageApplied = false;
        meleeCooldownTimer = Math.max(meleeCooldownTimer, 0.35);
    }

    public boolean isFrozen() {
        return freezeTimer > 0;
    }

    public void updateAwareness(Player player, Rectangle2D[] blockers) {
        targetPlayer = player;
        double dx = (player.getX() + player.getWidth() / 2.0) - (x + width / 2.0);
        double dy = (player.getY() + player.getHeight() / 2.0) - (y + height / 2.0);
        double distance = Math.sqrt(dx * dx + dy * dy);
        boolean inDetectionRadius = distance <= getDetectRange();
        hasLineOfSight = inDetectionRadius && !isLineBlocked(player, blockers);
        if (hasLineOfSight) {
            memoryTimer = MEMORY_DURATION;
        } else if (inDetectionRadius) {
            memoryTimer = Math.min(memoryTimer, 0.2);
        } else if (distance > getChaseRange()) {
            memoryTimer = 0;
        }
    }

    public boolean hasLineOfSightToPlayer() {
        return hasLineOfSight;
    }

    protected boolean isChasingPlayer() {
        return memoryTimer > 0;
    }

    protected double getDetectRange() { return DETECT_RANGE; }

    protected double getChaseRange() { return CHASE_RANGE; }

    public void setNavigationSurfaces(Rectangle2D[] surfaces) {
        navigationSurfaces = surfaces == null ? new Rectangle2D[0] : surfaces;
    }

    public void setMovementBlockers(Rectangle2D[] blockers) {
        movementBlockers = blockers == null ? new Rectangle2D[0] : blockers;
    }

    public boolean shouldDropFromPlatform() {
        if (!onGround || recoveryTimer > 0 || !isChasingPlayer() || targetPlayer == null) return false;
        boolean playerBelow = targetPlayer.getY() > y + height + 28.0;
        if (!playerBelow) return false;

        double playerCenter = targetPlayer.getX() + targetPlayer.getWidth() / 2.0;
        double enemyCenter = x + width / 2.0;
        if (playerCenter > enemyCenter) {
            return x + width >= patrolRight - DROP_CHASE_MARGIN;
        }
        return x <= patrolLeft + DROP_CHASE_MARGIN;
    }

    private void tryJumpChase(double playerCenter, double enemyCenter) {
        if (!onGround || chaseJumpCooldown > 0 || recoveryTimer > 0 || targetPlayer == null) return;

        boolean playerHigher = targetPlayer.getY() + targetPlayer.getHeight()
            < y + height - 24.0;
        boolean closeEnough = Math.abs(playerCenter - enemyCenter) < 190.0;
        boolean reachableHeight = y - targetPlayer.getY() < 135.0;
        if (!playerHigher || !closeEnough || !reachableHeight || !hasLineOfSight) return;
        if (!hasReachableLandingSurface(playerCenter)) return;

        velocityY = Config.JUMP_FORCE * 0.72;
        onGround = false;
        chaseJumpCooldown = CHASE_JUMP_COOLDOWN;
    }

    public void recoverFromWall() {
        recoverFromWall(0);
    }

    public void recoverFromWall(int collisionSide) {
        if (recoveryTimer > 0.05) return;

        boolean activelyChasing = isChasingPlayer();
        if (activelyChasing && targetPlayer != null) {
            double playerCenter = targetPlayer.getX() + targetPlayer.getWidth() / 2.0;
            double enemyCenter = x + width / 2.0;
            if (Math.abs(playerCenter - enemyCenter) > 4.0) {
                moveDir = playerCenter > enemyCenter ? 1.0 : -1.0;
            }
        } else if (collisionSide == 1) {
            moveDir = -1.0;
        } else if (collisionSide == 2) {
            moveDir = 1.0;
        } else {
            moveDir *= -1.0;
        }

        if (!activelyChasing) {
            memoryTimer = 0;
        }
        recoveryTimer = activelyChasing ? 0.12 : 0.45;
        if (activelyChasing && onGround && chaseJumpCooldown <= 0
            && targetPlayer != null) {
            velocityY = Config.JUMP_FORCE * 0.74;
            onGround = false;
            chaseJumpCooldown = 0.80;
        }
    }

    private void avoidWallBeforeMove(boolean chasing) {
        if (!onGround || recoveryTimer > 0 || movementBlockers.length == 0) return;

        Rectangle2D wallAhead = getWallAhead(moveDir);
        if (wallAhead == null) return;
        int wallSide = moveDir > 0 ? 1 : 2;

        if (chasing && usesMeleeSlash() && tryVaultWall(wallAhead)) {
            return;
        }
        recoverFromWall(wallSide);
    }

    private Rectangle2D getWallAhead(double direction) {
        double probeOffset = direction > 0 ? 6.0 : -6.0;
        Rectangle2D probe = new Rectangle2D(x + probeOffset, y + 3.0,
            width, height - 6.0);
        for (Rectangle2D blocker : movementBlockers) {
            if (blocker.getHeight() <= 24.0) continue;
            if (probe.intersects(blocker)) {
                return blocker;
            }
        }
        return null;
    }

    private boolean tryVaultWall(Rectangle2D wall) {
        if (chaseJumpCooldown > 0 || targetPlayer == null) return false;

        double enemyFeet = y + height;
        double climbHeight = enemyFeet - wall.getMinY();
        double playerCenter = targetPlayer.getX() + targetPlayer.getWidth() / 2.0;
        double enemyCenter = x + width / 2.0;
        boolean playerBeyondWall = moveDir > 0
            ? playerCenter > wall.getMaxX()
            : playerCenter < wall.getMinX();
        boolean reasonableWall = climbHeight > 8.0 && climbHeight <= 155.0 && wall.getWidth() <= 190.0;
        boolean closeEnough = Math.abs(playerCenter - enemyCenter) <= 260.0;
        if (!playerBeyondWall || !reasonableWall || !closeEnough) return false;

        velocityY = Config.JUMP_FORCE * 0.82;
        onGround = false;
        recoveryTimer = 0;
        chaseJumpCooldown = 0.95;
        return true;
    }

    private boolean hasReachableLandingSurface(double playerCenter) {
        double playerFeet = targetPlayer.getY() + targetPlayer.getHeight();
        for (Rectangle2D surface : navigationSurfaces) {
            if (surface.getWidth() < ENEMY_W + 12) continue;
            boolean nearPlayerX = playerCenter >= surface.getMinX() - 18.0
                && playerCenter <= surface.getMaxX() + 18.0;
            boolean nearPlayerY = Math.abs(surface.getMinY() - playerFeet) <= 18.0;
            boolean jumpableHeight = y + height - surface.getMinY() <= 135.0;
            boolean jumpableDistance = Math.abs((surface.getMinX() + surface.getWidth() / 2.0)
                - (x + width / 2.0)) <= 210.0;
            if (nearPlayerX && nearPlayerY && jumpableHeight && jumpableDistance) {
                return true;
            }
        }
        return false;
    }

    private boolean isLineBlocked(Player player, Rectangle2D[] blockers) {
        if (blockers == null) return false;
        double x1 = x + width / 2.0;
        double y1 = y + height / 2.0;
        double x2 = player.getX() + player.getWidth() / 2.0;
        double y2 = player.getY() + player.getHeight() / 2.0;
        for (Rectangle2D blocker : blockers) {
            for (int i = 1; i < 10; i++) {
                double t = i / 10.0;
                double px = x1 + (x2 - x1) * t;
                double py = y1 + (y2 - y1) * t;
                if (blocker.contains(px, py)) return true;
            }
        }
        return false;
    }

    // ── 接觸傷害 ─────────────────────────────────────────────────────────────

    /**
     * 嘗試對玩家施加接觸傷害。
     * 冷卻計時器歸零前不重複傷害，防止每幀連續扣血。
     * 傷害實際是否生效由 Player.takeDamage() 的無敵機制決定。
     *
     * @param player 要攻擊的玩家
     * @return 本次是否觸發了傷害呼叫（冷卻中回傳 false）
     */
    public boolean tryDamagePlayer(Player player) {
        if (isFrozen()) return false;
        if (!usesMeleeSlash()) {
            if (damageCooldown > 0 || !getHitbox().intersects(player.getHitbox())) return false;

            player.takeDamage(contactDamage);
            damageCooldown = DAMAGE_COOLDOWN;
            return true;
        }

        if (meleeTimer <= 0 && meleeCooldownTimer <= 0 && isPlayerInMeleeArc(player)) {
            startMeleeAttack(player);
        }

        if (isMeleeActive() && !meleeDamageApplied && isPlayerInMeleeArc(player)) {
            player.takeDamage(contactDamage);
            meleeDamageApplied = true;
            return true;
        }
        return false;
    }

    protected boolean usesMeleeSlash() {
        return true;
    }

    // ── 地面狀態 ─────────────────────────────────────────────────────────────

    /**
     * 由平台碰撞解析呼叫：落地時清除垂直速度，防止持續累積下落速度。
     *
     * @param onGround true = 已踩到平台；false = 在空中（不做任何事）
     */
    public void setOnGround(boolean onGround) {
        this.onGround = onGround;
        if (onGround && velocityY > 0) velocityY = 0;
    }

    public void updateStuckRecovery(double deltaTime) {
        if (!isAlive() || recoveryTimer > 0) {
            lastResolvedX = x;
            stuckTimer = 0;
            return;
        }

        boolean tryingToMove = onGround && Math.abs(velocityX) > 1.0;
        double moved = Math.abs(x - lastResolvedX);
        if (tryingToMove && moved < STUCK_MIN_MOVE) {
            stuckTimer += deltaTime;
        } else {
            stuckTimer = Math.max(0, stuckTimer - deltaTime * 2.0);
        }
        lastResolvedX = x;

        if (stuckTimer >= STUCK_CHECK_INTERVAL) {
            recoverFromWall(moveDir > 0 ? 1 : 2);
            stuckTimer = 0;
        }
    }

    // ── draw ─────────────────────────────────────────────────────────────────

    /**
     * 繪製敵人本體（磚紅色矩形）、面向眼睛，以及頭頂血量條。
     * 死亡後不繪製任何內容，自然從畫面消失。
     *
     * @param gc 畫布繪圖上下文
     */
    @Override
    public void draw(GraphicsContext gc) {
        // 死亡後不渲染
        if (!isAlive()) return;

        // 本體（磚紅色）
        // TODO: 換成敵人精靈圖動畫
        gc.setFill(freezeTimer > 0 ? Color.PALETURQUOISE
            : slowTimer > 0 ? Color.LIGHTBLUE : Color.FIREBRICK);
        gc.fillRect(x, y, width, height);

        // 眼睛指示移動方向（往右眼睛靠右，往左眼睛靠左）
        double eyeX = moveDir > 0 ? x + width - 11 : x + 4;
        gc.setFill(Color.WHITE);
        gc.fillOval(eyeX, y + 7, 7, 7);
        gc.setFill(Color.BLACK);
        gc.fillOval(eyeX + 1.5, y + 8.5, 4, 4);

        // 頭頂血量條
        drawHpBar(gc);
        drawAlertIndicator(gc);
        drawMeleeSlash(gc);
    }

    private void updateMeleeTimers(double deltaTime) {
        if (meleeCooldownTimer > 0) {
            meleeCooldownTimer -= deltaTime;
            if (meleeCooldownTimer < 0) meleeCooldownTimer = 0;
        }
        if (meleeTimer > 0) {
            meleeTimer -= deltaTime;
            if (meleeTimer <= 0) {
                meleeTimer = 0;
            }
        }
    }

    private void startMeleeAttack(Player player) {
        moveDir = player.getX() + player.getWidth() / 2.0 > x + width / 2.0 ? 1.0 : -1.0;
        meleeTimer = MELEE_WINDUP + MELEE_ACTIVE + MELEE_RECOVERY;
        meleeCooldownTimer = MELEE_COOLDOWN;
        meleeDamageApplied = false;
    }

    private boolean isMeleeActive() {
        double elapsed = getMeleeElapsed();
        return elapsed >= MELEE_WINDUP && elapsed <= MELEE_WINDUP + MELEE_ACTIVE;
    }

    private boolean isPlayerInMeleeArc(Player player) {
        Rectangle2D target = player.getHitbox();
        double originX = moveDir > 0 ? x + width : x;
        double originY = y + height * 0.56;
        double targetX = target.getMinX() + target.getWidth() / 2.0;
        double targetY = target.getMinY() + target.getHeight() / 2.0;
        double dx = targetX - originX;
        double dy = targetY - originY;
        if (moveDir > 0 && dx < -width * 0.35) return false;
        if (moveDir < 0 && dx > width * 0.35) return false;
        double distance = Math.sqrt(dx * dx + dy * dy);
        if (distance > MELEE_RANGE) return false;
        double angle = Math.toDegrees(Math.atan2(dy, Math.abs(dx)));
        return Math.abs(angle) <= MELEE_ARC_DEGREES / 2.0;
    }

    private void drawAlertIndicator(GraphicsContext gc) {
        if (!isChasingPlayer() && !hasLineOfSight) return;
        double bob = Math.sin(System.nanoTime() / 120_000_000.0) * 2.0;
        gc.setFont(javafx.scene.text.Font.font("Arial", javafx.scene.text.FontWeight.BOLD, 22));
        double textX = x + width / 2.0 - 5.0;
        double textY = y - 14.0 + bob;
        gc.setStroke(Color.web("#5a3b00"));
        gc.setLineWidth(3.0);
        gc.strokeText("!", textX, textY);
        gc.setFill(Color.GOLD);
        gc.fillText("!", textX, textY);
    }

    private void drawMeleeSlash(GraphicsContext gc) {
        if (meleeTimer <= 0) return;
        double originX = moveDir > 0 ? x + width : x;
        double originY = y + height * 0.56;
        MeleeSlashRenderer.draw(gc,
            originX,
            originY,
            moveDir > 0,
            MELEE_RANGE,
            MELEE_ARC_DEGREES,
            getMeleeElapsed(),
            MELEE_WINDUP + MELEE_ACTIVE + MELEE_RECOVERY,
            MELEE_WINDUP,
            MELEE_WINDUP + MELEE_ACTIVE,
            null,
            Color.web("#ffdf6a")
        );
    }

    private double getMeleeElapsed() {
        return MELEE_WINDUP + MELEE_ACTIVE + MELEE_RECOVERY - meleeTimer;
    }

    /**
     * 在敵人頭頂上方繪製小型血量條。
     *
     * @param gc 畫布繪圖上下文
     */
    private void drawHpBar(GraphicsContext gc) {
        final double barH = 4;
        final double barY = y - barH - 3;

        // 背景（深紅底）
        gc.setFill(Color.DARKRED);
        gc.fillRect(x, barY, width, barH);

        // 剩餘血量前景（綠色）
        gc.setFill(Color.LIMEGREEN);
        gc.fillRect(x, barY, width * ((double) hp / maxHp), barH);
    }
}
