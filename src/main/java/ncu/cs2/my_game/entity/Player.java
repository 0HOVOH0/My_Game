package ncu.cs2.my_game.entity;

import javafx.geometry.Rectangle2D;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.KeyCode;
import javafx.scene.paint.Color;
import javafx.scene.shape.ArcType;
import ncu.cs2.my_game.Config;
import ncu.cs2.my_game.physics.Gravity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 玩家實體，繼承 Entity。
 * 負責處理鍵盤輸入、移動、跳躍、攻擊與受傷邏輯。
 */
public class Player extends Entity {

    // ── 攻擊常數 ─────────────────────────────────────────────────────────────

    /** 每次攻擊造成的傷害值 */
    public static final int    ATTACK_DAMAGE   = 20;

    /** 攻擊判定框持續時間（秒） */
    public static final double ATTACK_DURATION = 0.28;

    /** 近戰攻擊冷卻時間（秒），降低揮擊頻率。 */
    public static final double ATTACK_COOLDOWN = 0.65;

    /** 半圓揮擊半徑（像素） */
    public static final double ATTACK_ARC_RADIUS = 64.0;

    /** 揮砍動畫與實際判定角度（面向方向左右各 75 度）。 */
    public static final double ATTACK_ARC_DEGREES = 150.0;

    private static final double ATTACK_WINDUP = 0.08;
    private static final double ATTACK_ACTIVE_END = 0.18;

    /** 讓貼在玩家前半身的敵人也會被揮擊命中。 */
    private static final double ATTACK_FRONT_BODY_REACH = 18.0;

    /** 半圓揮擊碰撞 broad-phase 尺寸 */
    public static final double ATTACK_BOX_W    = ATTACK_ARC_RADIUS;
    public static final double ATTACK_BOX_H    = ATTACK_ARC_RADIUS * 2;

    // ── 火球常數 ─────────────────────────────────────────────────────────────

    /** 強化火球傷害 */
    public static final int EMPOWERED_FIREBALL_DAMAGE = 60;

    // ── 無敵常數 ─────────────────────────────────────────────────────────────

    /** 受傷後的無敵時間（秒） */
    public static final double INVINCIBLE_DURATION = 0.5;

    /** 閃爍效果切換間隔（秒）；越小閃得越快 */
    private static final double FLICKER_INTERVAL = 0.08;

    // ── 狀態 ─────────────────────────────────────────────────────────────────

    /** 是否站在地面上；為 true 時才能跳躍 */
    private boolean isOnGround;

    /** 面向方向；true = 右，false = 左 */
    private boolean facingRight;

    /** 攻擊動畫期間鎖定的面向；避免揮砍中途被 A/D 改變。 */
    private boolean attackFacingRight;

    /** 面向方向；供火球決定發射方向 */
    private Direction facingDirection;

    /** 目前是否處於蹲下狀態 */
    private boolean isCrouching;

    /** 目前是否正在攻擊（攻擊判定框存在中） */
    private boolean isAttacking;

    /** One-frame event consumed by scenes to spawn visual slash effects. */
    private boolean attackStarted;

    /**
     * 本次揮擊是否已命中過目標。
     * startAttack() 時重置為 false；場景呼叫 markHit() 後設為 true。
     * 確保每次 J 按鍵只對同一目標造成一次傷害，而非每幀連續扣血。
     */
    private boolean attackLanded;

    // ── 攻擊 ─────────────────────────────────────────────────────────────────

    /** 目前的攻擊判定框；不在攻擊狀態時為 null */
    private Rectangle2D attackBox;

    /** 攻擊判定框剩餘存在時間（秒） */
    private double attackTimer;

    /** 攻擊冷卻剩餘時間（秒） */
    private double attackCooldownTimer;

    // ── 火球 ─────────────────────────────────────────────────────────────────

    /** 目前仍存在的玩家火球 */
    private final List<Fireball> fireballs;

    /** 目前仍存在的冰球 */
    private final List<IceProjectile> iceProjectiles;

    /** 目前魔力量 */
    private double mana;

    /** 火球術剩餘冷卻時間（秒）；0 時可再次施放 */
    private double fireballCooldownTimer;

    /** 最近一次按 K 失敗是否因為魔力不足，用於 HUD 提示 */
    private boolean lastFireballFailedForMana;

    // ── 無敵與閃爍 ───────────────────────────────────────────────────────────

    /** 剩餘無敵時間（秒）；> 0 時不受傷害 */
    private double invincibleTimer;

    /** 閃爍計時器；歸零時切換顯示狀態 */
    private double flickerTimer;

    /** 目前幀是否渲染玩家；閃爍時交替 true/false */
    private boolean flickerVisible;

    // ── 鍵盤輸入 ─────────────────────────────────────────────────────────────

    /** 目前正在按住的按鍵集合；由外部呼叫 handleKeyPressed/Released 維護 */
    private final Set<KeyCode> activeKeys;

    private static final double CROUCH_HOLD_THRESHOLD = 0.18;
    private double sHoldTimer;
    private boolean sPendingCrouch;
    private boolean platformDropRequested;

    // ─────────────────────────────────────────────────────────────────────────

    /**
     * 建構子：初始化玩家位置，使用 Config 的最大血量。
     *
     * @param x 初始 X 座標
     * @param y 初始 Y 座標
     */
    public Player(double x, double y) {
        super(x, y, Config.PLAYER_WIDTH, Config.PLAYER_HEIGHT, Config.PLAYER_MAX_HP);
        facingRight    = true;
        attackFacingRight = true;
        facingDirection = Direction.RIGHT;
        isCrouching    = false;
        isOnGround     = false;
        isAttacking    = false;
        attackStarted   = false;
        attackLanded   = false;
        attackTimer    = 0;
        attackCooldownTimer = 0;
        fireballs      = new ArrayList<>();
        iceProjectiles = new ArrayList<>();
        mana           = Config.PLAYER_MAX_MANA;
        fireballCooldownTimer = 0;
        lastFireballFailedForMana = false;
        invincibleTimer = 0;
        flickerTimer   = 0;
        flickerVisible  = true;
        activeKeys     = new HashSet<>();
        sHoldTimer = 0;
        sPendingCrouch = false;
        platformDropRequested = false;
    }

    // ── update ────────────────────────────────────────────────────────────────

    /**
     * 每幀更新：依序處理重力、移動、攻擊計時、無敵計時，最後更新座標。
     *
     * @param deltaTime 距上一幀的時間差（秒）
     */
    @Override
    public void update(double deltaTime) {
        // 1. 套用重力加速度（修改 velocityY）
        Gravity.apply(this, deltaTime);

        // 2. 依當前按鍵決定水平速度
        applyMovementInput();
        updateCrouchHold(deltaTime);

        // 3. 更新座標
        x += velocityX * deltaTime;
        y += velocityY * deltaTime;

        // 4. 攻擊計時——同步攻擊框位置，時間到則清除
        updateAttack(deltaTime);

        // 5. 魔力、火球冷卻與投射物更新
        updateMana(deltaTime);
        updateFireballs(deltaTime);
        updateIceProjectiles(deltaTime);

        // 6. 無敵與閃爍計時
        updateInvincibility(deltaTime);
    }

    /**
     * 依 activeKeys 設定水平速度與面向方向。
     * A/D 同時按下時右鍵優先；放開後速度歸零。
     */
    private void applyMovementInput() {
        velocityX = 0;
        double speed = Config.PLAYER_SPEED;
        if (isCrouching) {
            speed *= Config.PLAYER_CROUCH_SPEED_MULTIPLIER;
        }

        if (activeKeys.contains(KeyCode.A)) {
            velocityX   = -speed;
            if (!isAttacking) {
                facingRight = false;
                facingDirection = Direction.LEFT;
            }
        }
        if (activeKeys.contains(KeyCode.D)) {
            // D 鍵後蓋 A 鍵，右方向優先
            velocityX   = speed;
            if (!isAttacking) {
                facingRight = true;
                facingDirection = Direction.RIGHT;
            }
        }
    }

    /**
     * 每幀更新攻擊判定框位置，並在時間到後清除攻擊狀態。
     * 攻擊框跟隨玩家移動，使近戰判定更自然。
     *
     * @param deltaTime 時間差（秒）
     */
    private void updateAttack(double deltaTime) {
        if (attackCooldownTimer > 0) {
            attackCooldownTimer -= deltaTime;
            if (attackCooldownTimer < 0) attackCooldownTimer = 0;
        }
        if (!isAttacking) return;

        attackTimer -= deltaTime;

        if (attackTimer <= 0) {
            // 攻擊結束，清除判定框
            isAttacking = false;
            attackBox   = null;
            return;
        }

        updateAttackArcBounds();
    }

    /**
     * 更新火球冷卻與火球位置，並清除已消失的火球。
     *
     * @param deltaTime 時間差（秒）
     */
    private void updateFireballs(double deltaTime) {
        if (fireballCooldownTimer > 0) {
            fireballCooldownTimer -= deltaTime;
            if (fireballCooldownTimer < 0) fireballCooldownTimer = 0;
        }

        fireballs.removeIf(fireball -> !fireball.isAlive());
        for (Fireball fireball : fireballs) {
            fireball.update(deltaTime);
        }
    }

    private void updateMana(double deltaTime) {
        if (mana < Config.PLAYER_MAX_MANA) {
            mana = Math.min(Config.PLAYER_MAX_MANA,
                            mana + Config.PLAYER_MANA_REGEN_PER_SEC * deltaTime);
        }
        if (lastFireballFailedForMana && mana >= Config.FIREBALL_MANA_COST) {
            lastFireballFailedForMana = false;
        }
    }

    /**
     * 更新無敵計時與閃爍顯示狀態。
     * 無敵結束後強制恢復顯示。
     *
     * @param deltaTime 時間差（秒）
     */
    private void updateInvincibility(double deltaTime) {
        if (invincibleTimer <= 0) return;

        invincibleTimer -= deltaTime;
        flickerTimer    -= deltaTime;

        // 每隔 FLICKER_INTERVAL 秒切換一次是否顯示
        if (flickerTimer <= 0) {
            flickerTimer   = FLICKER_INTERVAL;
            flickerVisible = !flickerVisible;
        }

        // 無敵結束後確保玩家可見
        if (invincibleTimer <= 0) {
            invincibleTimer = 0;
            flickerVisible  = true;
        }
    }

    // ── 鍵盤事件（由遊戲場景呼叫） ────────────────────────────────────────────

    /**
     * 按下按鍵時呼叫；處理跳躍與攻擊的瞬間觸發，
     * 持續性動作（移動）則透過 activeKeys 在 update 裡處理。
     *
     * @param key 被按下的按鍵
     */
    public void handleKeyPressed(KeyCode key) {
        activeKeys.add(key);

        updateFacingDirectionFromKey(key);

        // 跳躍：W 或空白鍵，且必須站在地面
        if ((key == KeyCode.W || key == KeyCode.SPACE) && isOnGround && !isCrouching) {
            velocityY  = Config.JUMP_FORCE;
            isOnGround = false;
        }

        // S：地面短按穿平台、長按蹲下；空中快速下落
        if (key == KeyCode.S) {
            if (isOnGround) {
                sHoldTimer = 0;
                sPendingCrouch = true;
            } else if (velocityY < Config.PLAYER_FAST_FALL_SPEED) {
                velocityY = Config.PLAYER_FAST_FALL_SPEED;
            }
        }

        // 攻擊：J 鍵，且目前不在攻擊狀態（避免動畫插入）
        if (key == KeyCode.J && !isAttacking && attackCooldownTimer <= 0) {
            startAttack();
        }

        // 火球術：K 鍵，依目前面向水平發射；需消耗 Mana 並有冷卻
        if (key == KeyCode.K) {
            castFireball();
        }
    }

    /**
     * 放開按鍵時呼叫；從 activeKeys 移除以停止持續動作。
     *
     * @param key 被放開的按鍵
     */
    public void handleKeyReleased(KeyCode key) {
        if (key == KeyCode.S && sPendingCrouch) {
            platformDropRequested = true;
            sPendingCrouch = false;
            sHoldTimer = 0;
        }
        activeKeys.remove(key);
    }

    private void updateCrouchHold(double deltaTime) {
        if (!sPendingCrouch) return;
        if (!activeKeys.contains(KeyCode.S) || !isOnGround) {
            sPendingCrouch = false;
            sHoldTimer = 0;
            return;
        }
        sHoldTimer += deltaTime;
        if (sHoldTimer >= CROUCH_HOLD_THRESHOLD) {
            startCrouch();
            sPendingCrouch = false;
            sHoldTimer = 0;
        }
    }

    private void updateIceProjectiles(double deltaTime) {
        iceProjectiles.removeIf(projectile -> !projectile.isAlive());
        for (IceProjectile projectile : iceProjectiles) {
            projectile.update(deltaTime);
        }
    }

    /**
     * 場景在確認頭頂空間足夠後呼叫，使玩家站起。
     */
    public void standUp() {
        if (!isCrouching) return;
        double oldHeight = height;
        height = Config.PLAYER_HEIGHT;
        y -= height - oldHeight;
        isCrouching = false;
    }

    private void startCrouch() {
        if (isCrouching) return;
        double oldHeight = height;
        height = Config.PLAYER_HEIGHT * Config.PLAYER_CROUCH_HEIGHT_MULTIPLIER;
        y += oldHeight - height;
        isCrouching = true;
    }

    /**
     * 發動攻擊：設定攻擊狀態、重置命中旗標，並建立初始攻擊判定框。
     * attackLanded 重置為 false，使本次揮擊可以再命中一次目標。
     */
    private void startAttack() {
        isAttacking  = true;
        attackStarted = true;
        attackFacingRight = facingRight;
        attackLanded = false;   // 新的揮擊開始，重置命中旗標
        attackTimer  = ATTACK_DURATION;
        attackCooldownTimer = ATTACK_COOLDOWN;

        updateAttackArcBounds();
    }

    private void updateAttackArcBounds() {
        boolean attackRight = getAttackFacingRight();
        double centerX = attackRight ? x + width : x;
        double centerY = y + height / 2.0;
        double boxX = attackRight ? x + width / 2.0 : centerX - ATTACK_ARC_RADIUS;
        double boxY = centerY - ATTACK_ARC_RADIUS;
        attackBox = new Rectangle2D(boxX, boxY,
            ATTACK_ARC_RADIUS + width / 2.0, ATTACK_ARC_RADIUS * 2);
    }

    /**
     * 施放火球術：若冷卻完成，從玩家中心附近依當前面向產生火球。
     */
    private void castFireball() {
        if (fireballCooldownTimer > 0) return;
        if (mana < Config.FIREBALL_MANA_COST) {
            lastFireballFailedForMana = true;
            return;
        }

        mana -= Config.FIREBALL_MANA_COST;
        lastFireballFailedForMana = false;
        spawnFireball(Config.FIREBALL_SIZE, Config.FIREBALL_SPEED, Config.FIREBALL_DAMAGE,
                      Color.ORANGERED, Color.YELLOW);
        fireballCooldownTimer = Config.FIREBALL_COOLDOWN;
    }

    /**
     * 立刻施放一次強化火球，不消耗一般火球冷卻。
     * 由火焰卷軸道具呼叫。
     */
    public void castEmpoweredFireball() {
        spawnFireball(26.0, 430.0, EMPOWERED_FIREBALL_DAMAGE,
                      Color.DARKORANGE, Color.WHITE);
    }

    public void castIceProjectile() {
        double size = IceProjectile.SIZE;
        double dirX = facingRight ? 1.0 : -1.0;
        double spawnX = facingRight ? x + width : x - size;
        double spawnY = y + (height - size) / 2.0;
        iceProjectiles.add(new IceProjectile(spawnX, spawnY, dirX, 0));
    }

    /**
     * 依目前面向產生火球。
     */
    private void spawnFireball(double fireballSize, double speed, int damage,
                               Color fillColor, Color strokeColor) {
        double dirX = facingRight ? 1.0 : -1.0;
        double spawnX = facingRight ? x + width : x - fireballSize;
        double spawnY = y + (height - fireballSize) / 2.0;

        fireballs.add(new Fireball(spawnX, spawnY, dirX, 0,
                                   fireballSize, speed, damage, fillColor, strokeColor,
                                   damage >= EMPOWERED_FIREBALL_DAMAGE));
    }

    /**
     * 使用最近按下的水平移動鍵更新面向。S 不再用於火球向下瞄準，避免與其他功能衝突。
     *
     * @param key 被按下的按鍵
     */
    private void updateFacingDirectionFromKey(KeyCode key) {
        switch (key) {
            case A -> {
                if (!isAttacking) {
                    facingRight = false;
                    facingDirection = Direction.LEFT;
                }
            }
            case D -> {
                if (!isAttacking) {
                    facingRight = true;
                    facingDirection = Direction.RIGHT;
                }
            }
            default -> {
                // 其他按鍵不改變面向
            }
        }
    }

    // ── 受傷 ─────────────────────────────────────────────────────────────────

    /**
     * 承受傷害：扣血並進入無敵時間。
     * 無敵時間內再次呼叫此方法不會扣血。
     *
     * @param amount 傷害值（正整數）
     */
    public void takeDamage(int amount) {
        // 無敵時間內忽略傷害
        if (invincibleTimer > 0) return;

        setHp(hp - amount);

        // 啟動無敵與閃爍計時
        invincibleTimer = INVINCIBLE_DURATION;
        flickerTimer    = FLICKER_INTERVAL;
        flickerVisible  = false;   // 受傷瞬間先隱藏，產生閃爍感
    }

    public void startInvincibility(double duration) {
        invincibleTimer = Math.max(invincibleTimer, duration);
        flickerTimer = FLICKER_INTERVAL;
        flickerVisible = true;
    }

    // ── draw ─────────────────────────────────────────────────────────────────

    /**
     * 繪製玩家與 HUD（血量條）。
     * 閃爍期間逢隱藏幀則跳過主體繪製，但血量條仍繪製。
     *
     * @param gc 畫布繪圖上下文
     */
    @Override
    public void draw(GraphicsContext gc) {
        // 閃爍隱藏幀：跳過玩家主體，但仍繪製 UI
        if (flickerVisible) {
            drawBody(gc);
        }

        // 攻擊判定框（除錯視覺化，待換成動畫後可移除）
        if (isAttacking && attackBox != null) {
            drawAttackBox(gc);
        }

        // 火球投射物
        for (Fireball fireball : fireballs) {
            fireball.draw(gc);
        }
        for (IceProjectile projectile : iceProjectiles) {
            projectile.draw(gc);
        }

        // 血量條永遠顯示
        drawHpBar(gc);
    }

    /**
     * 繪製玩家主體。
     * TODO: 動畫精靈圖待實作，目前以純色矩形代替。
     *
     * @param gc 畫布繪圖上下文
     */
    private void drawBody(GraphicsContext gc) {
        // TODO: 依 facingRight 與動作狀態切換精靈圖幀
        gc.setFill(Color.CORNFLOWERBLUE);
        gc.fillRect(x, y, width, height);

        // 以小三角形指示面向（待精靈圖後移除）
        gc.setFill(Color.WHITE);
        if (getVisualFacingRight()) {
            gc.fillPolygon(
                new double[]{x + width - 4, x + width - 12, x + width - 12},
                new double[]{y + height / 2.0, y + height / 2.0 - 6, y + height / 2.0 + 6},
                3
            );
        } else {
            gc.fillPolygon(
                new double[]{x + 4, x + 12, x + 12},
                new double[]{y + height / 2.0, y + height / 2.0 - 6, y + height / 2.0 + 6},
                3
            );
        }
    }

    /**
     * 繪製攻擊判定框（半透明黃色）。
     * TODO: 換成攻擊動畫效果後可改為特效粒子。
     *
     * @param gc 畫布繪圖上下文
     */
    private void drawAttackBox(GraphicsContext gc) {
        gc.save();
        gc.setGlobalAlpha(isAttackDamageActive() ? 0.50 : 0.20);
        gc.setFill(Color.YELLOW);
        double centerY = y + height / 2.0;
        boolean attackRight = getAttackFacingRight();
        double centerX = attackRight ? x + width : x;
        double arcX = centerX - ATTACK_ARC_RADIUS;
        double arcY = centerY - ATTACK_ARC_RADIUS;
        gc.fillArc(arcX, arcY, ATTACK_ARC_RADIUS * 2, ATTACK_ARC_RADIUS * 2,
                   attackRight ? -ATTACK_ARC_DEGREES / 2.0 : 180 - ATTACK_ARC_DEGREES / 2.0,
                   ATTACK_ARC_DEGREES, ArcType.ROUND);
        gc.restore();   // 恢復 alpha，避免影響後續繪製
    }

    /**
     * 繪製血量條於玩家頭頂上方。
     *
     * @param gc 畫布繪圖上下文
     */
    private void drawHpBar(GraphicsContext gc) {
        final double barH = 6;
        final double barY = y - barH - 4;   // 距頭頂 4 像素

        // 背景（深紅）
        gc.setFill(Color.DARKRED);
        gc.fillRect(x, barY, width, barH);

        // 剩餘血量（綠色）
        double ratio = (double) hp / maxHp;
        gc.setFill(Color.LIMEGREEN);
        gc.fillRect(x, barY, width * ratio, barH);
    }

    // ── Getter / Setter ───────────────────────────────────────────────────────

    /** 回傳是否站在地面 */
    public boolean isOnGround()     { return isOnGround; }

    /** 回傳面向是否為右方 */
    public boolean isFacingRight()  { return facingRight; }

    /** 回傳目前四方向面向 */
    public Direction getFacingDirection() { return facingDirection; }

    /** 回傳目前是否正在攻擊 */
    public boolean isAttacking()    { return isAttacking; }

    /** 回傳目前攻擊判定框；不在攻擊狀態時為 null */
    public Rectangle2D getAttackBox() { return attackBox; }

    public boolean isAttackHitting(Rectangle2D target) {
        if (!isAttackDamageActive() || attackBox == null || !attackBox.intersects(target)) return false;

        boolean attackRight = getAttackFacingRight();
        double centerX = attackRight ? x + width : x;
        double centerY = y + height / 2.0;
        double targetX = target.getMinX() + target.getWidth() / 2.0;
        double targetY = target.getMinY() + target.getHeight() / 2.0;
        double dx = targetX - centerX;
        double dy = targetY - centerY;
        double distance = Math.sqrt(dx * dx + dy * dy);
        if (distance <= ATTACK_ARC_RADIUS && isInsideFacingArc(dx, dy)) return true;

        double frontBodyX = attackRight ? x + width / 2.0 : x - ATTACK_FRONT_BODY_REACH;
        Rectangle2D frontBody = new Rectangle2D(frontBodyX, y,
            width / 2.0 + ATTACK_FRONT_BODY_REACH, height);
        return frontBody.intersects(target);
    }

    private boolean isInsideFacingArc(double dx, double dy) {
        boolean attackRight = getAttackFacingRight();
        if (attackRight && dx < 0) return false;
        if (!attackRight && dx > 0) return false;

        double angle = Math.toDegrees(Math.atan2(dy, Math.abs(dx)));
        return Math.abs(angle) <= ATTACK_ARC_DEGREES / 2.0;
    }

    private boolean getAttackFacingRight() {
        return isAttacking ? attackFacingRight : facingRight;
    }

    private boolean getVisualFacingRight() {
        return isAttacking ? attackFacingRight : facingRight;
    }

    /**
     * 回傳本次揮擊是否可以命中目標。
     * 條件：正在攻擊中（isAttacking）且本次揮擊尚未命中過（!attackLanded）。
     * 場景在每次揮擊的命中判定前呼叫此方法，避免攻擊框存活期間每幀連續扣血。
     *
     * @return 可以命中回傳 true，否則 false
     */
    public boolean canHit() { return isAttackDamageActive() && !attackLanded; }

    public boolean consumeAttackStarted() {
        boolean result = attackStarted;
        attackStarted = false;
        return result;
    }

    public boolean isAttackDamageActive() {
        if (!isAttacking) return false;
        double elapsed = ATTACK_DURATION - attackTimer;
        return elapsed >= ATTACK_WINDUP && elapsed <= ATTACK_ACTIVE_END;
    }

    /**
     * 標記本次揮擊已命中目標，後續幀不再觸發傷害。
     * 場景在成功命中後呼叫此方法，下次 startAttack() 時自動重置。
     */
    public void markHit() { attackLanded = true; }

    /** 回傳目前是否處於無敵狀態 */
    public boolean isInvincible()   { return invincibleTimer > 0; }

    /** 回傳玩家目前所有火球，供場景進行碰撞判定 */
    public List<Fireball> getFireballs() { return fireballs; }

    public List<IceProjectile> getIceProjectiles() { return iceProjectiles; }

    /** 回傳火球術是否可施放 */
    public boolean canCastFireball() {
        return fireballCooldownTimer <= 0 && mana >= Config.FIREBALL_MANA_COST;
    }

    /** 回傳火球術剩餘冷卻秒數 */
    public double getFireballCooldownTimer() { return fireballCooldownTimer; }

    public double getFireballCooldownRatio() {
        if (Config.FIREBALL_COOLDOWN <= 0) return 0;
        return Math.max(0, Math.min(1, fireballCooldownTimer / Config.FIREBALL_COOLDOWN));
    }

    public double getMana() { return mana; }

    public double getMaxMana() { return Config.PLAYER_MAX_MANA; }

    public void setMana(double mana) {
        this.mana = Math.max(0.0, Math.min(mana, Config.PLAYER_MAX_MANA));
    }

    public String getFireballStatusText() {
        if (lastFireballFailedForMana && mana < Config.FIREBALL_MANA_COST) {
            return "Not enough mana";
        }
        if (fireballCooldownTimer > 0) {
            return String.format("%.1fs", fireballCooldownTimer);
        }
        if (mana < Config.FIREBALL_MANA_COST) {
            return "Not enough mana";
        }
        return "Ready";
    }

    /**
     * 將玩家還原到關卡 checkpoint 起點。
     * 清除暫態輸入、攻擊、火球與無敵狀態，避免死亡前狀態污染重生後的關卡。
     */
    public void resetForCheckpoint(double startX, double startY, int checkpointHp) {
        resetForCheckpoint(startX, startY, checkpointHp, Config.PLAYER_MAX_MANA);
    }

    public void resetForCheckpoint(double startX, double startY, int checkpointHp, double checkpointMana) {
        x = startX;
        y = startY;
        velocityX = 0;
        velocityY = 0;
        hp = Math.max(0, Math.min(checkpointHp, maxHp));
        setMana(checkpointMana);
        isOnGround = false;
        facingRight = true;
        attackFacingRight = true;
        facingDirection = Direction.RIGHT;
        isCrouching = false;
        width = Config.PLAYER_WIDTH;
        height = Config.PLAYER_HEIGHT;
        isAttacking = false;
        attackStarted = false;
        attackLanded = false;
        attackBox = null;
        attackTimer = 0;
        attackCooldownTimer = 0;
        fireballs.clear();
        iceProjectiles.clear();
        fireballCooldownTimer = 0;
        lastFireballFailedForMana = false;
        invincibleTimer = 0;
        flickerTimer = 0;
        flickerVisible = true;
        activeKeys.clear();
        sHoldTimer = 0;
        sPendingCrouch = false;
        platformDropRequested = false;
    }

    /**
     * 由碰撞解析設定地面狀態。
     * 落地時傳入 true 並將 velocityY 歸零；離地時傳入 false。
     *
     * @param onGround 是否站在地面
     */
    public void setOnGround(boolean onGround) {
        this.isOnGround = onGround;
        if (onGround && velocityY > 0) {
            velocityY = 0;  // 落地時清除下落速度
        }
    }

    public boolean isCrouching() { return isCrouching; }

    public boolean wantsToStandUp() {
        return isCrouching && !activeKeys.contains(KeyCode.S);
    }

    public Rectangle2D getStandingHitbox() {
        double standingHeight = Config.PLAYER_HEIGHT;
        return new Rectangle2D(x, y + height - standingHeight, width, standingHeight);
    }

    public boolean consumePlatformDropRequest() {
        boolean requested = platformDropRequested;
        platformDropRequested = false;
        return requested;
    }

    /**
     * 四方向面向，用於火球術發射方向。
     */
    public enum Direction {
        LEFT(-1, 0),
        RIGHT(1, 0),
        UP(0, -1),
        DOWN(0, 1);

        private final double dx;
        private final double dy;

        Direction(double dx, double dy) {
            this.dx = dx;
            this.dy = dy;
        }
    }
}
