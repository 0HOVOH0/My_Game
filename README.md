# My Game — 2D JavaFX 平台動作遊戲

一款以 JavaFX 21 開發的 2D 橫向捲軸動作遊戲，玩家需穿越兩個充滿陷阱的關卡，最終擊敗擁有三個戰鬥階段的 Boss。

---

## 操作說明

| 按鍵 | 動作 |
|------|------|
| `A` | 向左移動 |
| `D` | 向右移動 |
| `W` 或 `Space` | 跳躍（必須站在地面） |
| `J` | 近戰攻擊（每次揮擊只造成一次傷害） |
| `R` | Boss 關死亡後重新開始 |

---

## 遊戲流程

```
開始畫面 → Level 1 → Level 2 → Boss 戰 → 結算畫面
                                  ↑           ↓
                              R 鍵重啟     再玩一次
```

- **Level 1**：非直線地圖，右側 P4 是陷阱死路，正確路線需從 P3 往左跳
- **Level 2**：同地圖加入三名巡邏敵人與一個加血道具
- **Boss 戰**：三階段 AI（CHASE → DASH → RAGE），HP 低於 60% 啟動衝刺，低於 30% 進入狂暴並發射投射物

---

## 編譯與執行

**需求：**
- Java 21+
- Maven 3.8+

**執行：**

```bash
mvn javafx:run
```

**封裝為可執行 JAR：**

```bash
mvn package
java -jar target/My_Game-1.0-SNAPSHOT.jar
```

---

## 專案架構

```
src/main/java/ncu/cs2/my_game/
├── Main.java                    # JavaFX Application 主進入點、場景切換、計時器
├── Config.java                  # 全域常數（視窗、物理、血量、地板厚度等）
├── Launcher.java                # 非模組環境啟動用包裝
│
├── entity/
│   ├── Entity.java              # 基底類別：座標、速度、HP、碰撞框
│   ├── Player.java              # 玩家：鍵盤輸入、攻擊判定、無敵閃爍
│   ├── Enemy.java               # 普通敵人：巡邏 AI、接觸傷害冷卻
│   └── Boss.java                # Boss：FSM 驅動、投射物管理、DASH 攻擊框
│
├── fsm/
│   ├── StateMachine.java        # 泛型有限狀態機基底（stateTimer、transitionTo）
│   ├── BossState.java           # Boss 狀態列舉（IDLE/CHASE/DASH/RAGE/HURT/DEAD）
│   └── BossStateMachine.java    # Boss AI 邏輯：狀態轉換條件與各狀態行為
│
├── physics/
│   ├── Gravity.java             # 重力系統（v += a·t，限制最大落下速度 600 px/s）
│   └── Collision.java           # 碰撞工具：AABB 重疊檢測、單向平台落地判定
│
├── scene/
│   ├── Level1Scene.java         # 第一關：平台跳躍、終點門 → Level 2
│   ├── Level2Scene.java         # 第二關：敵人戰鬥、加血道具 → Boss 場景
│   ├── BossScene.java           # Boss 關：三階段戰鬥、勝負判定、R 鍵重啟
│   └── GameScene.java           # （開發用 Boss 測試場景）
│
└── controller/
    ├── StartSceneController.java # 開始畫面控制器
    ├── EndSceneController.java   # 結算畫面控制器（顯示通關時間）
    └── MainMenuController.java   # （舊版主選單，已由 StartScene 取代）

src/main/resources/ncu/cs2/my_game/
├── start-scene.fxml             # 開始畫面
├── end-scene.fxml               # 結算畫面
└── main-menu.fxml               # （舊版，保留備用）
```

---

## 技術亮點

### 1. 有限狀態機（FSM）— Boss AI

`StateMachine<S>` 提供泛型狀態機基底，`BossStateMachine` 繼承後實作六個狀態的完整轉換邏輯：

```
IDLE ──(1s)──► CHASE ──(HP<60% 且距離<120px)──► DASH ──(0.5s)──► CHASE
                 │                                                    ▲
                 └──(HP<30%)──► RAGE ─────────────────────────────────┘
任意狀態 ──(被攻擊)──► HURT ──(0.3s)──► CHASE
任意狀態 ──(HP=0)────► DEAD（終態）
```

RAGE 狀態每 1.5 秒發射一顆投射物，透過 `Runnable` 回調注入 FSM，使狀態機不直接依賴具體投射物類別。

### 2. deltaTime 物理系統

所有移動計算乘以 `deltaTime`（秒），確保速度與幀率無關。每幀 dt 上限 `Config.MAX_DELTA_TIME = 0.05s`，防止畫面暫停後大幀衝穿地板。

- 重力加速度：980 px/s²
- 最大落下速度：600 px/s（`Gravity.MAX_FALL_SPEED`，防止穿板）
- 單向平台（`Collision.checkPlatform`）：僅在從上方下落時觸發落地，允許從下方穿越

### 3. 精確單次攻擊判定

攻擊框存活 0.2 秒（約 12 幀）。透過 `attackLanded` 旗標確保每次 `J` 按鍵只對同一目標造成一次傷害：

- `player.canHit()`：正在攻擊 **且** 本次揮擊尚未命中
- `player.markHit()`：命中後設為 true，直到下次 `startAttack()` 重置

### 4. AABB 碰撞系統

所有實體繼承 `Entity`，統一提供 `getHitbox()` 回傳 `Rectangle2D`。場景只需呼叫兩個靜態方法：

```java
Collision.checkAABB(a.getHitbox(), b.getHitbox())   // 任意重疊判定
Collision.checkPlatform(entity, platform)            // 單向平台落地
```

### 5. 場景切換架構

Canvas 場景繼承 `AnimationTimer`，切換前呼叫 `this.stop()` 停止迴圈再建立新場景；FXML 場景透過 `Main.switchScene()` 載入。`Main.startLevel1()` 同時記錄 `gameStartMillis`，`Main.startEnd()` 計算並儲存通關秒數供 `EndSceneController` 顯示。

---

## TODO 清單

### 必須完成（影響遊戲可玩性）

- [ ] **Level 1 / Level 2 玩家死亡判定**：HP 歸零後玩家仍能移動，缺少 GAME OVER 畫面與 R 鍵重啟邏輯（BossScene 已實作，Level1/2 尚未）
- [ ] **跨關卡血量設計決策**：目前進入每關都重置為滿血 100 HP，依設計需求決定是否保留

### 加分項目（動畫、音效、美術）

- [ ] 玩家精靈圖動畫（跑步、跳躍、攻擊、受傷、閒置）
- [ ] Boss 精靈圖動畫（依 FSM 狀態切換幀：IDLE/CHASE/DASH/RAGE/HURT/DEAD）
- [ ] 敵人精靈圖動畫（巡邏、受傷、死亡）
- [ ] 音效：跳躍、攻擊命中、受傷、Boss 嘯叫、投射物飛行
- [ ] 背景音樂（Level 1/2 主題曲、Boss 戰 BGM）
- [ ] Tileset 地圖圖塊（地板、平台）
- [ ] 視差捲動背景圖層
- [ ] 終點門開門動畫與光暈閃爍特效
- [ ] 投射物火球特效精靈圖
- [ ] 開始/結算畫面背景動畫
- [ ] Tiled 地圖格式支援（從 `.tmx` 讀取平台座標，取代硬編碼陣列）
