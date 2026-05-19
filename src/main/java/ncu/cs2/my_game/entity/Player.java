package ncu.cs2.my_game.entity;

import javafx.geometry.Rectangle2D;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.KeyCode;
import javafx.scene.paint.Color;
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
    public static final double ATTACK_DURATION = 0.2;

    /** 攻擊判定框寬度（像素） */
    public static final double ATTACK_BOX_W    = 30.0;

    /** 攻擊判定框高度（像素） */
    public static final double ATTACK_BOX_H    = 34.0;

    // ── 火球常數 ─────────────────────────────────────────────────────────────

    /** 火球術冷卻時間（秒） */
    public static final double FIREBALL_COOLDOWN = 5.0;

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

    /** 面向方向；供火球決定發射方向 */
    private Direction facingDirection;

    /** 目前是否正在攻擊（攻擊判定框存在中） */
    private boolean isAttacking;

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

    // ── 火球 ─────────────────────────────────────────────────────────────────

    /** 目前仍存在的玩家火球 */
    private final List<Fireball> fireballs;

    /** 火球術剩餘冷卻時間（秒）；0 時可再次施放 */
    private double fireballCooldownTimer;

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
        facingDirection = Direction.RIGHT;
        isOnGround     = false;
        isAttacking    = false;
        attackLanded   = false;
        attackTimer    = 0;
        fireballs      = new ArrayList<>();
        fireballCooldownTimer = 0;
        invincibleTimer = 0;
        flickerTimer   = 0;
        flickerVisible  = true;
        activeKeys     = new HashSet<>();
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

        // 3. 更新座標
        x += velocityX * deltaTime;
        y += velocityY * deltaTime;

        // 4. 攻擊計時——同步攻擊框位置，時間到則清除
        updateAttack(deltaTime);

        // 5. 火球冷卻與投射物更新
        updateFireballs(deltaTime);

        // 6. 無敵與閃爍計時
        updateInvincibility(deltaTime);
    }

    /**
     * 依 activeKeys 設定水平速度與面向方向。
     * A/D 同時按下時右鍵優先；放開後速度歸零。
     */
    private void applyMovementInput() {
        velocityX = 0;

        if (activeKeys.contains(KeyCode.A)) {
            velocityX   = -Config.PLAYER_SPEED;
            facingRight = false;
            facingDirection = Direction.LEFT;
        }
        if (activeKeys.contains(KeyCode.D)) {
            // D 鍵後蓋 A 鍵，右方向優先
            velocityX   = Config.PLAYER_SPEED;
            facingRight = true;
            facingDirection = Direction.RIGHT;
        }
    }

    /**
     * 每幀更新攻擊判定框位置，並在時間到後清除攻擊狀態。
     * 攻擊框跟隨玩家移動，使近戰判定更自然。
     *
     * @param deltaTime 時間差（秒）
     */
    private void updateAttack(double deltaTime) {
        if (!isAttacking) return;

        attackTimer -= deltaTime;

        if (attackTimer <= 0) {
            // 攻擊結束，清除判定框
            isAttacking = false;
            attackBox   = null;
            return;
        }

        // 攻擊框緊貼玩家面向那側，垂直置中於玩家
        double boxX = facingRight ? x + width : x - ATTACK_BOX_W;
        double boxY = y + (height - ATTACK_BOX_H) / 2.0;
        attackBox = new Rectangle2D(boxX, boxY, ATTACK_BOX_W, ATTACK_BOX_H);
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
        if ((key == KeyCode.W || key == KeyCode.SPACE) && isOnGround) {
            velocityY  = Config.JUMP_FORCE;
            isOnGround = false;
        }

        // 攻擊：J 鍵，且目前不在攻擊狀態（避免動畫插入）
        if (key == KeyCode.J && !isAttacking) {
            startAttack();
        }

        // 火球術：F 鍵，依目前面向發射；有 5 秒冷卻
        if (key == KeyCode.F) {
            castFireball();
        }
    }

    /**
     * 放開按鍵時呼叫；從 activeKeys 移除以停止持續動作。
     *
     * @param key 被放開的按鍵
     */
    public void handleKeyReleased(KeyCode key) {
        activeKeys.remove(key);
    }

    /**
     * 發動攻擊：設定攻擊狀態、重置命中旗標，並建立初始攻擊判定框。
     * attackLanded 重置為 false，使本次揮擊可以再命中一次目標。
     */
    private void startAttack() {
        isAttacking  = true;
        attackLanded = false;   // 新的揮擊開始，重置命中旗標
        attackTimer  = ATTACK_DURATION;

        // 立即產生攻擊框（updateAttack 每幀也會同步位置）
        double boxX = facingRight ? x + width : x - ATTACK_BOX_W;
        double boxY = y + (height - ATTACK_BOX_H) / 2.0;
        attackBox = new Rectangle2D(boxX, boxY, ATTACK_BOX_W, ATTACK_BOX_H);
    }

    /**
     * 施放火球術：若冷卻完成，從玩家中心附近依當前面向產生火球。
     */
    private void castFireball() {
        if (fireballCooldownTimer > 0) return;

        spawnFireball(Fireball.SIZE, Fireball.SPEED, Fireball.DAMAGE,
                      Color.ORANGERED, Color.YELLOW);
        fireballCooldownTimer = FIREBALL_COOLDOWN;
    }

    /**
     * 立刻施放一次強化火球，不消耗一般火球冷卻。
     * 由火焰卷軸道具呼叫。
     */
    public void castEmpoweredFireball() {
        spawnFireball(26.0, 430.0, EMPOWERED_FIREBALL_DAMAGE,
                      Color.DARKORANGE, Color.WHITE);
    }

    /**
     * 依目前面向產生火球。
     */
    private void spawnFireball(double fireballSize, double speed, int damage,
                               Color fillColor, Color strokeColor) {
        double dirX = facingDirection.dx;
        double dirY = facingDirection.dy;

        double spawnX = switch (facingDirection) {
            case LEFT  -> x - fireballSize;
            case RIGHT -> x + width;
            case UP, DOWN -> x + (width - fireballSize) / 2.0;
        };
        double spawnY = switch (facingDirection) {
            case UP    -> y - fireballSize;
            case DOWN  -> y + height;
            case LEFT, RIGHT -> y + (height - fireballSize) / 2.0;
        };

        fireballs.add(new Fireball(spawnX, spawnY, dirX, dirY,
                                   fireballSize, speed, damage, fillColor, strokeColor));
    }

    /**
     * 使用最近按下的方向鍵更新面向；S 不移動，只用於向下施法瞄準。
     *
     * @param key 被按下的按鍵
     */
    private void updateFacingDirectionFromKey(KeyCode key) {
        switch (key) {
            case A -> {
                facingRight = false;
                facingDirection = Direction.LEFT;
            }
            case D -> {
                facingRight = true;
                facingDirection = Direction.RIGHT;
            }
            case W -> facingDirection = Direction.UP;
            case S -> facingDirection = Direction.DOWN;
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
        if (facingRight) {
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
        gc.setGlobalAlpha(0.45);
        gc.setFill(Color.YELLOW);
        gc.fillRect(attackBox.getMinX(), attackBox.getMinY(),
                    attackBox.getWidth(), attackBox.getHeight());
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

    /**
     * 回傳本次揮擊是否可以命中目標。
     * 條件：正在攻擊中（isAttacking）且本次揮擊尚未命中過（!attackLanded）。
     * 場景在每次揮擊的命中判定前呼叫此方法，避免攻擊框存活期間每幀連續扣血。
     *
     * @return 可以命中回傳 true，否則 false
     */
    public boolean canHit() { return isAttacking && !attackLanded; }

    /**
     * 標記本次揮擊已命中目標，後續幀不再觸發傷害。
     * 場景在成功命中後呼叫此方法，下次 startAttack() 時自動重置。
     */
    public void markHit() { attackLanded = true; }

    /** 回傳目前是否處於無敵狀態 */
    public boolean isInvincible()   { return invincibleTimer > 0; }

    /** 回傳玩家目前所有火球，供場景進行碰撞判定 */
    public List<Fireball> getFireballs() { return fireballs; }

    /** 回傳火球術是否可施放 */
    public boolean canCastFireball() { return fireballCooldownTimer <= 0; }

    /** 回傳火球術剩餘冷卻秒數 */
    public double getFireballCooldownTimer() { return fireballCooldownTimer; }

    /**
     * 將玩家還原到關卡 checkpoint 起點。
     * 清除暫態輸入、攻擊、火球與無敵狀態，避免死亡前狀態污染重生後的關卡。
     */
    public void resetForCheckpoint(double startX, double startY, int checkpointHp) {
        x = startX;
        y = startY;
        velocityX = 0;
        velocityY = 0;
        hp = Math.max(0, Math.min(checkpointHp, maxHp));
        isOnGround = false;
        facingRight = true;
        facingDirection = Direction.RIGHT;
        isAttacking = false;
        attackLanded = false;
        attackBox = null;
        attackTimer = 0;
        fireballs.clear();
        fireballCooldownTimer = 0;
        invincibleTimer = 0;
        flickerTimer = 0;
        flickerVisible = true;
        activeKeys.clear();
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
