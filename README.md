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
| `F` | 施放火球術（依目前面向左 / 右 / 上 / 下發射，冷卻 5 秒） |
| `1` | 使用小補血藥水 |
| `2` | 使用大補血藥水 |
| `3` | 使用火焰卷軸（立刻發射一次強化火球） |
| `4` | 使用炸彈（對玩家附近敵人造成範圍傷害） |
| `5` | 使用冰凍卷軸（暫時降低普通敵人移動速度） |
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

**Windows 點兩下啟動：**

專案根目錄提供 `Run_Game.bat`，可直接雙擊啟動遊戲。

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
│   ├── Fireball.java            # 玩家火球術投射物：直線飛行、碰撞框、傷害
│   ├── Enemy.java               # 普通敵人：巡邏 AI、接觸傷害冷卻
│   └── Boss.java                # Boss：FSM 驅動、投射物管理、DASH 攻擊框
│
├── item/
│   ├── PickupItem.java          # 地板道具基底：碰撞框、繪製、撿取狀態
│   ├── PickupType.java          # 道具種類 enum 與 factory
│   ├── Inventory.java           # 簡易背包：紀錄道具數量與使用
│   ├── UseContext.java          # 道具使用時的玩家 / 敵人 / Boss 上下文
│   ├── SmallPotionItem.java     # 小補血藥水
│   ├── LargePotionItem.java     # 大補血藥水
│   ├── FireScrollItem.java      # 火焰卷軸
│   ├── BombItem.java            # 炸彈
│   └── IceScrollItem.java       # 冰凍卷軸
│
├── state/
│   ├── StageSnapshot.java       # 關卡初始狀態快照
│   ├── InventorySnapshot.java   # 背包數量快照
│   ├── PickupSnapshot.java      # 地板道具種類與位置快照
│   └── EnemySnapshot.java       # 普通敵人初始資料快照
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

### 6. 玩家火球術（Fireball）

玩家按 `F` 可施放火球術。火球由 `Player` 管理冷卻與生命週期，依最近面向的 `A/D/W/S` 方向發射；其中 `S` 只作為向下瞄準，不影響平台動作移動。`Fireball` 使用 AABB 碰撞框、固定速度直線移動，超出視窗自動消失。`Level2Scene` 會判定火球命中普通敵人並造成 25 點傷害；`BossScene` 與開發用 `GameScene` 會判定火球命中 Boss；各場景也會在火球撞到地板或平台時將其消滅。

### 7. Boss 遠程火球與道具系統

Boss 的遠程攻擊沿用 `Fireball`，由 `BossStateMachine` 控制施法時機。Boss 會在玩家位於有效距離內時隨機每 1-3 秒吐出紫紅色火球，玩家偏遠時冷卻變長，距離太遠則停止施法。Boss 火球會朝玩家目前位置飛行，命中玩家、撞到地形或超出視窗後消失。

`PickupItem` 提供可擴充地板道具架構，道具碰撞後加入 `Inventory`，並透過 `1-5` 快捷鍵使用。普通敵人死亡有機率掉小藥水或卷軸；Boss 死亡必掉一個稀有卷軸，並有機率掉大藥水。

### 8. 關卡快照與 HUD

Level 2 與 Boss 關進入時會建立 `StageSnapshot`，保存玩家起點與血量、背包數量、地板道具初始位置，以及敵人初始資料。玩家死亡後按 `R` 會 rollback 到剛進入該關時的狀態：敵人復活、掉落物消失、初始道具回原位、背包恢復、Boss 重建，避免死亡前的消耗狀態污染重生後的關卡。

HUD 會固定顯示火球術冷卻：可施放時顯示 `Fireball: Ready`，冷卻中顯示剩餘秒數。背包數量 HUD 由 `Inventory` 迭代 `PickupType.values()` 自動產生，未來新增道具時只要在 enum 補上類型與 HUD 名稱即可顯示。

---

## Changelog

### v0.3
- 新增 `StageSnapshot` 關卡初始快照，死亡重生可回復道具、掉落、敵人、Boss 與背包狀態
- 新增火球術冷卻 HUD，顯示 `Ready` 或剩餘秒數
- 新增自動迭代的物品數量 HUD，避免新增道具時到處手寫 UI
- 修正 Level 2 / Boss 關死亡重啟會保留已消耗道具與掉落狀態的問題

### v0.2
- 新增 Boss 火球攻擊
- 新增地板道具、背包、藥水、卷軸與一次性武器
- 新增普通敵人與 Boss 掉落系統
- 新增 `Run_Game.bat` 雙擊啟動檔

### v0.1
- 新增玩家火球術
- 新增玩家火球命中敵人 / Boss 與撞牆消失判定
- 更新 README 操作與架構說明

---

## 本次修改紀錄：玩家火球術

- 新增 `entity/Fireball.java`：定義玩家火球的尺寸、速度、傷害、AABB、繪製與消失狀態。
- 修改 `entity/Player.java`：新增 `Direction` 四方向面向、`F` 鍵施放、5 秒冷卻、火球清單更新與繪製。
- 修改 `scene/Level1Scene.java`：加入火球撞地板 / 平台消失的判定。
- 修改 `scene/Level2Scene.java`：加入火球命中普通敵人造成傷害，以及撞地板 / 平台消失。
- 修改 `scene/BossScene.java`：加入火球命中 Boss 造成傷害，以及撞地板 / 平台消失。
- 修改 `scene/GameScene.java`：讓開發用 Boss 測試場景也支援玩家火球命中 Boss。

## 本次修改紀錄：Boss 火球、道具、掉落、啟動檔

- 新增 `Run_Game.bat`：Windows 可雙擊啟動遊戲。
- 擴充 `entity/Fireball.java`：支援自訂大小、速度、傷害與顏色，讓玩家、Boss、卷軸共用同一套火球。
- 修改 `entity/Boss.java` 與 `fsm/BossStateMachine.java`：Boss 遠程火球改用共用 `Fireball`，並由 FSM 管理隨機冷卻、距離降頻與施法回調。
- 修改 `entity/Enemy.java`：新增 `applySlow()`，讓冰凍卷軸可暫時降低普通敵人移動速度。
- 新增 `item/` 道具系統：`PickupItem`、`PickupType`、`Inventory`、`UseContext` 與五種具體道具。
- 修改 `Main.java`：加入跨關卡背包，Level2 撿到的道具可帶進 Boss 關。
- 修改 `Level2Scene.java`：加入地板道具、撿取、背包快捷鍵、普通敵人掉落。
- 修改 `BossScene.java`：加入背包快捷鍵、Boss 火球撞牆/命中玩家判定、Boss 死亡掉落。
- 修改 `module-info.java`：匯出 `ncu.cs2.my_game.item` 套件。

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
