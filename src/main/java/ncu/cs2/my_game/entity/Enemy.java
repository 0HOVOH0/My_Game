package ncu.cs2.my_game.entity;

import javafx.geometry.Rectangle2D;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import ncu.cs2.my_game.Config;
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

    private static final double DETECT_RANGE = 245.0;
    private static final double CHASE_RANGE = 330.0;
    private static final double MEMORY_DURATION = 1.25;
    private static final double DROP_CHASE_MARGIN = 34.0;
    private static final double CHASE_JUMP_COOLDOWN = 1.25;

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

    /** 目前速度倍率，1.0 表示正常速度 */
    private double speedMultiplier;
    private final double baseSpeedMultiplier;
    private final int contactDamage;
    private Player targetPlayer;
    private double memoryTimer;
    private boolean hasLineOfSight;
    private boolean onGround;
    private double chaseJumpCooldown;
    private Rectangle2D[] navigationSurfaces = new Rectangle2D[0];

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
        this.baseSpeedMultiplier = speedMultiplier;
        this.speedMultiplier = speedMultiplier;
        this.contactDamage = Math.max(1, (int) Math.round(CONTACT_DAMAGE * damageMultiplier));
        this.memoryTimer = 0;
        this.hasLineOfSight = false;
        this.onGround = false;
        this.chaseJumpCooldown = 0;
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

        // 2. 設定水平速度並更新位置
        boolean chasing = targetPlayer != null && memoryTimer > 0;
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
        velocityX = moveDir * PATROL_SPEED * speedMultiplier;
        x += velocityX * deltaTime;
        y += velocityY * deltaTime;

        // 3. 到達巡邏邊界時原地修正並反向
        if (!chasing && x <= patrolLeft) {
            moveDir = 1.0;   // 轉向右
        } else if (!chasing && x + width >= patrolRight) {
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

    public void updateAwareness(Player player, Rectangle2D[] blockers) {
        targetPlayer = player;
        double dx = (player.getX() + player.getWidth() / 2.0) - (x + width / 2.0);
        double dy = (player.getY() + player.getHeight() / 2.0) - (y + height / 2.0);
        double distance = Math.sqrt(dx * dx + dy * dy);
        boolean inDetectionRadius = distance <= getDetectRange();
        hasLineOfSight = inDetectionRadius && !isLineBlocked(player, blockers);
        if (hasLineOfSight) {
            memoryTimer = MEMORY_DURATION;
        } else if (inDetectionRadius && Math.abs(dy) < 190.0) {
            memoryTimer = Math.max(memoryTimer, MEMORY_DURATION * 0.65);
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

    public boolean shouldDropFromPlatform() {
        if (!onGround || !isChasingPlayer() || targetPlayer == null) return false;
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
        if (!onGround || chaseJumpCooldown > 0 || targetPlayer == null) return;

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
        if (damageCooldown > 0) return false;

        player.takeDamage(contactDamage);
        damageCooldown = DAMAGE_COOLDOWN;   // 重置冷卻
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
        gc.setFill(slowTimer > 0 ? Color.LIGHTBLUE : Color.FIREBRICK);
        gc.fillRect(x, y, width, height);

        // 眼睛指示移動方向（往右眼睛靠右，往左眼睛靠左）
        double eyeX = moveDir > 0 ? x + width - 11 : x + 4;
        gc.setFill(Color.WHITE);
        gc.fillOval(eyeX, y + 7, 7, 7);
        gc.setFill(Color.BLACK);
        gc.fillOval(eyeX + 1.5, y + 8.5, 4, 4);

        // 頭頂血量條
        drawHpBar(gc);
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
