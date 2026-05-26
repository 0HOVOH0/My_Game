# My Game — 2D JavaFX 平台動作遊戲

一款以 JavaFX 21 開發的 2D 橫向捲軸動作遊戲，玩家需穿越兩個充滿陷阱的關卡，最終擊敗擁有三個戰鬥階段的 Boss。

---

## 操作說明

| 按鍵 | 動作 |
|------|------|
| `A` | 向左移動 |
| `D` | 向右移動 |
| `W` 或 `Space` | 跳躍（必須站在地面） |
| `S` | 地面蹲下 / 空中快速下落 |
| `J` | 近戰攻擊（每次揮擊只造成一次傷害） |
| `K` | 施放火球術（依目前左右面向發射，消耗 Mana 並有冷卻） |
| `B` | 開啟 / 關閉背包 |
| `E` | 接近商店時開啟 / 關閉商店 |
| `↑` / `↓` 或 `W` / `S` | 商店 UI 選擇商品 |
| `Enter` 或 `Space` | 商店 UI 購買 |
| `Enter` | 站在傳送門旁進入下一關 |
| `R` | 商店 UI 刷新商品 / Boss 關死亡後重新開始 |
| `Esc` | 關閉商店 UI / 暫停或恢復遊戲 |
| `Q` | 只在暫停選單開啟時退出遊戲 |
| `U` | 使用 / 替換 Slot1 |
| `I` | 使用 / 替換 Slot2 |
| `O` | 使用 / 替換 Slot3 |

---

## 遊戲流程

```
開始畫面 → Level 1 → 隨機一般關 ×2 → 商店關 → Boss 戰
                         ↑                    ↓
                         └──── 下一輪一般關 ×3 ←
```

- **Level 1**：非直線地圖，右側 P4 是陷阱死路，正確路線需從 P3 往左跳
- **隨機一般關**：沿用 Level 2 模板，依 `StageGenerator` 隨機成為戰鬥、平台、探索或精英關
- **商店關**：Boss 前固定出現的安全區，可花 Gold 購買補給
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
│   ├── ItemSpawnManager.java    # 安全道具生成入口
│   ├── ItemSpawnResolver.java   # 位置解析：八方向、ring search、fallback、重疊檢查
│   ├── Inventory.java           # Slot 背包：最多三種道具、同類型 stack、使用 / 替換
│   ├── InventorySlot.java       # 背包單格資料：道具種類與數量
│   ├── UseContext.java          # 道具使用時的玩家 / 敵人 / Boss 上下文
│   ├── SmallPotionItem.java     # 小補血藥水
│   ├── LargePotionItem.java     # 大補血藥水
│   ├── FireScrollItem.java      # 火焰卷軸
│   ├── BombItem.java            # 炸彈
│   └── IceScrollItem.java       # 冰凍卷軸
│
├── economy/
│   ├── CurrencyManager.java     # Gold 數值管理
│   ├── GoldPickup.java          # 地板金幣掉落物
│   └── GoldSpawnManager.java    # 金幣安全生成，沿用道具位置 resolver
│
├── shop/
│   ├── ShopItem.java            # 商店商品資料：道具種類與價格
│   └── ShopManager.java         # 商店商品清單
│
├── stage/
│   ├── StageType.java           # 一般關 / 探索 / 精英 / 商店 / Boss 類型
│   ├── StageDefinition.java     # 一次生成出的關卡參數
│   └── StageGenerator.java      # 隨機關卡生成，避免連續重複
│
├── state/
│   ├── StageSnapshot.java       # 關卡初始狀態快照
│   ├── InventorySnapshot.java   # 背包數量快照
│   ├── PickupSnapshot.java      # 地板道具種類與位置快照
│   ├── GoldSnapshot.java        # 地板金幣位置與金額快照
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
│   ├── Level2Scene.java         # 隨機一般關模板：敵人、探索物資、金幣掉落
│   ├── ShopScene.java           # Boss 前商店關：E 開店、Gold 購物
│   ├── BossScene.java           # Boss 關：三階段戰鬥、掉落、R 鍵重啟
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

玩家按 `K` 可施放火球術。火球由 `Player` 管理 Mana、冷卻與生命週期，依最近水平面向的 `A/D` 方向發射。`Fireball` 使用 AABB 碰撞框、固定速度直線移動，超出視窗自動消失。`Level2Scene` 會判定火球命中普通敵人並造成 25 點傷害；`BossScene` 與開發用 `GameScene` 會判定火球命中 Boss；各場景也會在火球撞到地板或平台時將其消滅。

### 7. Boss 遠程火球與道具系統

Boss 的遠程攻擊沿用 `Fireball`，由 `BossStateMachine` 控制施法時機。Boss 會在玩家位於有效距離內時隨機每 1-3 秒吐出紫紅色火球，玩家偏遠時冷卻變長，距離太遠則停止施法。Boss 火球會朝玩家目前位置飛行，命中玩家、撞到地形或超出視窗後消失。

`PickupItem` 提供可擴充地板道具架構，道具碰撞後加入 `Inventory`。背包目前有 3 個 slot，每格只能保存一種 item type，但同類型可堆疊；`U/I/O` 分別使用 Slot1/2/3。若背包已滿且玩家站在新的地板道具上，`U/I/O` 會直接把地板道具與指定 slot 交換，被替換的整疊道具會掉在玩家腳邊。普通敵人死亡有機率掉小藥水或卷軸；Boss 死亡必掉一個稀有卷軸，並有機率掉大藥水。`ItemSpawnManager` 會委派 `ItemSpawnResolver` 用原點、八方向、小半徑、ring search 與 fallback 掃描安全位置，讓掉落物優先在附近分散，避免重疊、超出地圖或掉到不可行走區域。

### 8. 關卡快照與 HUD

Level 2 與 Boss 關進入時會建立 `StageSnapshot`，保存玩家起點與血量、背包數量、地板道具初始位置，以及敵人初始資料。玩家死亡後按 `R` 會 rollback 到剛進入該關時的狀態：敵人復活、掉落物消失、初始道具回原位、背包恢復、Boss 重建，避免死亡前的消耗狀態污染重生後的關卡。

HUD 會固定顯示火球術圖示：可施放時亮起並顯示外框光效，冷卻中會灰化並以遮罩顯示進度，也會用小字提示剩餘秒數或 Mana 不足。右上角固定顯示 Slot1/2/3 與 `U/I/O` 對應鍵，按 `B` 可開啟背包 overlay。當背包已滿且腳下有新道具時，畫面會顯示 Backpack Full 替換提示，直接按 `U/I/O` 即可交換指定 slot。

### 9. 蹲下與快速下落

玩家在地面按住 `S` 會蹲下，碰撞框高度減半、移動速度減半，仍可左右移動。放開 `S` 時會先檢查頭頂是否有平台阻擋，空間足夠才恢復站立。玩家在空中按 `S` 會增加向下速度，形成自然的快速下落，不會瞬移或繞過平台碰撞。

### 10. Gold、商店與隨機關卡

普通敵人死亡會掉落 1-10 Gold，Boss 死亡會掉落 30-100 Gold。金幣是獨立地板掉落物，不進背包，玩家碰到後直接加入右上角 Gold UI。`StageSnapshot` 會保存進關時 Gold 與地板金幣狀態，死亡 rollback 時一併恢復。

`StageGenerator` 會在一般關卡中隨機選擇戰鬥、平台、探索與精英類型，避免連續重複；第一輪流程為 `Level1 -> 一般關 -> 一般關 -> Shop -> Boss`，之後每輪固定 `一般關 x3 -> Shop -> Boss`，確保 Boss 前一定有商店。探索關會用現有 anti-overlap spawn system 額外放置少量藥水、炸彈、卷軸或 Gold。

商店關是安全區，接近櫃台按 `E` 開啟商店，使用 `W/S` 或方向鍵選商品，`Enter/Space` 購買，`Esc` 關閉。價格目前為：Potion(S) 20G、Potion(L) 45G、Fire Scroll 40G、Ice Scroll 45G、Bomb 50G。

### 11. 長地圖、敵人成長與 Boss 類型

一般關世界寬度擴充為 1600px，Canvas 仍維持 800px，使用 `cameraX` 跟隨玩家。平台、敵人、道具與金幣只在 viewport 附近才繪製，碰撞仍使用完整世界座標，避免長地圖一次畫出所有內容。

敵人會依關卡進度成長：Enemy HP 約 `base x (1 + stage x 0.08)`，接觸傷害約 `base x (1 + stage x 0.05)`，速度小幅提升並設上限。Boss HP 約 `base x 2.0 x (1 + stage x 0.15)`，比原本更耐打但不直接提高秒殺能力。

Boss 關會隨機出現火球 Boss、地刺 Boss 或召喚 Boss，並避免連續兩次同類型。地刺 Boss 會在玩家附近地面先顯示紅色警告，再延遲生成尖刺；召喚 Boss 會定期叫出低血量小怪，小怪不掉 Gold 或道具，Boss 死亡時會清除。

### 12. 戰鬥特效、掩體與道具修正

近戰攻擊改為依面向顯示半透明半圓 slash effect，判定也使用前方半圓範圍，避免打到身後。攻擊冷卻調整為 0.65 秒，攻擊動畫與命中時間共用 `ATTACK_DURATION`。

一般關新增石柱、箱子與半牆等掩體，主要用來阻擋火球、冰球與遠程小兵投射物，不影響主路線通行。後段地圖額外加入平台與跳板，讓 1600px 長地圖後半段有更多高低差與探索點。

Bomb 使用後會在玩家位置生成 `BombEntity`，2 秒後爆炸，半徑 150px，傷害 85，會傷害普通敵人、遠程敵人、Boss 與 Boss 召喚小怪，玩家自身不受傷。Ice Scroll 目前改為玩家周圍 160px 範圍控場，小兵與召喚物冰凍 2.6 秒，Boss 冰凍 1.3 秒。火球碰撞框約放大 22%，提升命中率但不改變視覺大小。

---

## Changelog

### v3.0 — Version C 地牢架構：迷宮磚塊 + 走廊設計（2026-05-26）
- **全新 `PlatformDungeonGenerator` 架構**：從浮島式平台改為「大型實心 WALL 磚塊 + 走廊」設計，地圖更有地牢層次感
- **`generateDungeonBlocks()`**：生成 5–7 個寬 8–11 磚的大型 WALL 矩形（高度從頂部延伸至 y=17）。高度從左到右整體上升（大方向），但有 25% 機率往下走一塊，形成迷宮感
- **`addCorridorClimbers()`**：每段走廊（寬 5–8 磚）在中央放置 ONE_WAY_PLATFORM 踏腳石，每 3 磚高度差一層（96px ≈ 跳躍上限的 75%），連接地板（y=15）到各磚塊頂面
- **`clearBlockAirspace()`**：取代舊版 `clearMainRouteAirspace`，保護磚塊內部不被頭部淨空程序挖穿；僅清除非磚塊 WALL 與 DECORATION 裝飾磚
- **出生點改至地板左側**：玩家從地圖左下角出發（樓層地面），需利用走廊梯台爬升至各磚塊頂面，再跳躍到達出口
- **`isInsideDungeonBlock()` 防護**：地板逃生平台、走廊清空、BFS 跳躍驗證均跳過磚塊內部實心位置
- **`clearSpawnSafeZone` 修正**：不再清除 WALL 磚（防止把磚塊結構挖空），只清除 SPIKE 與 DECORATION
- **出口位於最後一個磚塊頂面右側**：距離出生點遠端，高度近地圖頂部（topY = 2–6）
- **`validateGeneratedMap` 適配**：地板逃生驗證跳過「tile y=15 是 WALL（玩家走廊側向逃脫）」位置，避免磚塊內部虛報失敗

### v2.5 — 卡死根治（移除垂直支撐柱 + 走廊保障）

### v2.4 — 卡死根治：移除陷阱牆、地板走廊保障
- **移除 `maybeAddVerticalSupport`**：平台下方隨機垂直牆柱讓玩家從平台邊緣落下後受困於側牆夾縫，無法繼續移動，完全移除
- **移除 `addPlatformLedge`**：平台正下方兩格 WALL 同樣製造低天花板陷阱（PLAYER_HEIGHT=42px，tile y=16 像素 512–543 與玩家頭部像素 ≈534 重疊），完全移除
- **新增 `clearFloorCorridor()`**：建圖最末端強制清除 tile y=16 與 y=17 全行所有 WALL/DECORATION/SPIKE，確保玩家站在地板（y=18）時頭部不受任何障礙物影響
- **新增第三次 `clearMainRouteAirspace`**：在逃生平台（y=15）與接力平台（y=12）放置後補一次清空，確保這些後置平台的頭部空間同樣被清除
- **L 型牆地板錨點再次修正**：anchorY 從 `getHeightTiles()-4`（=15）改為 `getHeightTiles()-6`（=13），防止 L 型牆橫臂落在 y=15 封堵逃生平台位置
- **`buildShell` 柱子再稀疏化**：間距 7-12 格→10-16 格，高度 6-12 格→3-7 格，放置機率 78%→60%，減少柱子出現在跳躍走廊的機會

### v2.3 — 動線可達性強化（強制起始上升 + 地板逃生平台）
- **強制起始三個平台向上**：路線生成前三個平台強制 `dy = -(1 + random.nextInt(2))`，確保玩家一開始就往上半區域走，解決「剛開始走不上去上半」問題
- **地板逃生平台 `addFloorEscapePlatforms()`**：每 8 格掃描，若附近無低平台（y ≥ 13），自動在 y=14 插入 3 格寬單向平台，確保從地板跌落後永遠有辦法跳回路線
- **平台牆腳 `addPlatformLedge()`**：每個主路線平台下方 2 格填充 WALL，形成厚石板感，增加地牢層次視覺
- **Shell 壁柱底部留空**：`buildShell` 停止高度從 y=17 改為 y=14（`getHeightTiles() - 4`），確保地板附近不被壁柱封死
- **導引牆跳過前 20%**：`addGuidingWalls` 新增 `spawnClearEnd` 保護，出生區不插入引導牆，導引牆同樣停在 y=14 以上
- **地圖驗證加入地板逃生檢查**：`validateGeneratedMap()` 每 8 格驗證地板逃生覆蓋，缺乏逃生路徑的地圖會被拒絕

### v2.1 — 地圖系統初步調整
- **出口距離約束**：新增 `MIN_EXIT_DISTANCE_FROM_SPAWN_TILES`，確保出口不會緊鄰出生點
- **分支平台優化**：`addBranches` 的分支 Y 範圍限制在可跳躍高度差內，移除過遠分支
- **接頭平台改善**：`addConnectorPlatforms` 置中高度計算優化，避免連接平台落在不合理位置
- **報告系統**：新增 `printGenerationReport` 輸出平台數、不可達比例、種子等調試資訊

### v2.0 — 程序地圖系統全面重寫（4 Zone + BFS 驗證）
- **全新四區段路線系統（Zone 1–4）**：地圖橫向分為四段，路線中心高度從 Zone 1（低）逐漸上升至 Zone 4（高），取代原本固定平台配置
- **BFS 可達性驗證**：`validatePlatformMap()` 以廣度優先搜尋從出生點出發，確認出口可達、70% 平台可利用率、所有獎勵區可達
- **物理跳躍約束 `canJumpBetween`**：依玩家實際 JUMP_FORCE / GRAVITY 計算最大水平距離（167px）與最大垂直上升（108px），只接受物理上可達的平台對
- **上層路線 `addUpperRoute`**：從主路線最高點延伸 3–4 個跳石至 y=4–8 區域，形成可探索的高空層
- **L 型牆 `addLShapedWalls`**：新增迷宮感 L 型牆結構，替代舊版簡單方塊牆
- **導引牆 `addGuidingWalls`**：垂直引導牆依 Zone 高度計算頂點，引導玩家向上，不封堵主要走廊
- **地圖預生成池**：`MapPoolManager` 啟動時生成 12 張合法地圖，關卡抽選時直接取用，避免每次進關重新生成
- **`PlayerStats` 計算封裝**：跳躍距離、高度等物理極限統一由 `PlayerStats.fromConfig()` 計算

### v2.2 — 地圖動線全面重構（有上有下 + 牆作為地板）
- **出口鎖定主路線**：`chooseExitPlatform()` 只從 main-route 快照中挑選，排除 `addUpperRoute` 孤立跳石，根除「第一關跳不上出口」問題
- **分段 Zone 提前啟動爬升**：出發高度 y=15 → y=13，Zone 分割點改為 22/47/72%，爬升分散到整段地圖
  - Zone 1（0-22%）y=9-15，center=12；Zone 2（22-47%）y=6-13，center=9；Zone 3（47-72%）y=4-11，center=7；Zone 4（72-100%）y=4-8，center=5
- **導引牆擴展至前 55% 地圖**：`addGuidingWalls()` 覆蓋範圍 30%→55%，牆數 2-3→4-6 道，高度 5-8→6-10 格
- **新增 `addWallFloorSections()`**：前 65% 地圖插入 5-7 塊橫向牆板（5-8 格寬、2 格厚），玩家必須跳上去，配合側邊垂直支撐形成地牢房間結構感
- **`buildShell` 左半加密**：左半段壁柱更密、更高（7-10 格），偶爾 2 格寬；右半段維持稀疏
- 所有改動通過 BFS 可達性驗證、70% 利用率、出口最小距離要求

### v1.7.1 — Bug 修復
- 修復玩家死亡後仍能進入下一關的問題（`Level1Scene`：`damagePlayerOnTraps()` 後加死亡提前返回）
- 修復敵人巡邏超出邊界後只改方向、未修正位置的問題（`Enemy`：轉向時同步將 `x` 貼回邊界）
- 修復 `checkEnemyDrops` 迴圈缺少 null 檢查，理論上可能造成 NPE（`Level2Scene`）
- 修復炸彈落地判定迭代 platforms / covers 陣列缺少 null 元素防護（`BombEntity`）

### v1.7 — UI 介面改善合併
- 統一所有場景使用 `HudRenderer.drawGameOverOverlay()`，以 `TextAlignment.CENTER` 取代硬編碼像素偏移，文字置中精確
- GAME OVER 畫面新增副標題提示（Level 1/2 說明重啟範圍，Boss 關說明狀態恢復）
- 所有場景（Level 1、Level 2、ShopScene、BossScene）底部新增固定操作提示欄
- Level 2 與 BossScene 新增拾取浮動提示，撿到藥水/卷軸/金幣時顯示金色浮字 1.4 秒
- 終點門提示改為使用 `HudRenderer.drawGoalPrompt()`，帶黑色底框與 X 軸邊界夾緊，移出鏡頭 translate 區塊避免右端溢出
- 開始畫面操作說明更新為雙行完整版（含道具快捷鍵 U/I/O、背包 B、暫停 P、重試 R）

### v1.6.7
- 修復 Boss 死亡後炸彈爆炸特效殘留
- 地刺 Boss 施法時仍更新重力、投射物與受傷狀態，避免卡空中或白色殘留
- Boss 位於玩家正上方平台時會主動下落追擊
- 召喚 Boss 召喚改為施法模式，期間停止水平行動且只會在落地時開始
- 強化小兵撞牆追擊時的小跳脫困
- Ice Scroll 使用時顯示冰凍範圍

### v1.6.6
- 修復普通關 solid tile 碰撞與渲染不同步造成的不可見地形問題
- 主路徑牆體生成後會再次清理可通行空間，降低牆壁阻斷終點路線
- 提高普通關分支與跳板生成量，讓後段有更多可跳躍路線
- 修復地刺 Boss 模式觸發過嚴，落地後更穩定進入 Spike Mode
- Ice Scroll 改為半徑 160px 範圍冰凍
- Boss 冰凍時間改為普通敵人的 50%

### v1.6.5
- 修復 L 型牆穿透問題，普通關 WALL tile 現在會一對一建立 collider
- 修復空氣牆問題，移除 Boss 房額外 cover collider 並同步渲染 / 碰撞來源
- 地刺 Boss 重構 Spike Mode，施法期間停止移動
- 地刺 Boss 新增 Self Spike Burst，以 Boss 為中心同步產生地刺
- 地刺同步生成並保留預警
- 降低召喚 Boss 小兵血量，召喚物可被玩家近戰一擊擊殺
- 優化召喚位置邏輯，優先 Boss 同平台與鄰近可站立平台
- 降低所有 Boss 強度與技能頻率
- 降低 Boss 碰撞傷害與 Dash 傷害
- 普通 Boss 新增近戰揮砍
- 普通 Boss 衝刺距離提升
- Boss 房移除所有牆壁
- 優化 Boss 房戰鬥流暢度

### v1.6
- 新增預生成地圖池系統
- 遊戲開始時生成 10-15 張合法普通關
- 普通關改為隨機抽選地圖
- 新增 Reachability Validation 作為地圖池收錄條件
- 優化地圖品質穩定性
- 小兵與道具改為抽圖後於場景內後生成
- 避免最近 3 張普通關地圖連續重複
- 自動輸出普通關地圖池 PNG 預覽至 `generated-map-pool/`

### v1.5.4
- Q 鍵改為僅可於暫停時退出
- 修復開始遊戲需多次點擊的事件重入問題
- 傳送門改為 ENTER 互動
- 修復傳送門 W 與跳躍同鍵造成的切場景風險
- 優化跳板間距，減少平台過密
- 增加牆壁數量與大小
- 新增大量 L 型牆作為迷宮式掩體
- 修復道具與地刺 / hazard 重疊問題

### v1.5.3
- 移除舊半圓形近戰特效與額外 GIF slash 疊加
- 優化斬擊角度，視覺軌跡同步 150° hitbox
- 修復斬擊尺寸與 melee reach 不一致
- 同步 melee reach 與動畫顯示範圍
- 更新 projectile 消除判定所對應的可視斬擊範圍
- 近戰小兵同步使用新的共用近戰視覺邏輯

### v1.5
- 修復平台上小兵生成與巡邏區會穿過牆體，導致卡在跳板或平台牆邊的問題
- 縮短地刺寬度並降低單次地刺數量，保留可躲避空間
- 地刺 Boss 現在可在玩家所在牆頂生成地刺
- Boss 關死亡復活時固定原本 Boss 類型，不再重新抽 Boss
- 強化小兵生成檢查：平台有效巡邏寬度不足時不生成小兵
- 普通關與 Boss 房的牆壁 / 跳板生成會保留至少一個玩家寬度的通行空間
- 普通關後半段新增更多非階梯式跳板，降低固定模板感

### v1.4
- 修復小兵卡牆，加入碰撞後水平位移檢查與 stuck recovery
- 新增近戰小兵揮砍攻擊，包含前搖、有效幀、收招與 cooldown
- 新增敵人驚嘆號提示，小兵與 Boss 偵測玩家時會顯示
- 增加後段地圖跳板，難度提升後中後段會追加平台
- 提高牆壁高度，後期牆體更能阻擋視線與 projectile
- 新增天花板牆，作為高處掩體與 projectile 阻擋
- 火焰卷軸強化火球可穿透多個敵人
- 冰凍卷軸冰球可穿透多個敵人
- 卷軸 projectile 可穿過跳板
- projectile 改為受真正牆壁、掩體與地面阻擋
- 修復空中炸彈掉落邏輯，炸彈落地後才開始倒數
- 修復近戰動畫與 hitbox 不一致
- 攻擊範圍由 180° 改為 150°
- 擴大 melee reach 至 64 px
- 攻擊判定與動畫有效幀同步
- 更新 projectile 消除範圍，使用新的 150° 揮砍判定

### v1.3
- 縮短小兵偵測距離，近戰小兵改為中短距離、遠程小兵只略遠於近戰
- 修復小兵卡牆，撞到牆側邊時進入自然 stuck recovery
- 新增小兵脫困行為：反向、小跳、放棄目前追擊記憶並回到巡邏
- 降低 Boss 追擊與衝刺速度約 15%
- 優化牆壁生成，右側終點門附近保留 safe zone
- 修復終點門與牆重疊 / 貼門問題
- 商店改為隨機商品，每次從商品池抽出 3-6 樣
- 新增商品購買上限與 Sold Out 顯示
- 新增 `R` 鍵刷新商店商品
- 新增刷新價格成長與商品價格 stage scaling

### v1.2
- Boss 房增加跳板間距，平台與牆頂加長，保留 Boss / 玩家可到達路徑
- Boss 死後不再直接換關，改為在最高合法跳板生成終點門
- 地刺可生成於玩家所在跳板或附近可站立 surface
- 召喚物可出生於地面、跳板或牆頂，生成時保證站在 surface top
- 縮短小兵追擊距離，近戰與遠程使用不同 detection / chase range
- 修復小兵無意義跳躍，跳躍前檢查視線、高度差與可達距離
- 新增巡邏模式，失去玩家後平滑回到附近巡邏，不瞬移回原點
- 修復遠程小兵瞬移，移除追擊結束時的直接位置拉回
- 增加地圖平台與 Boss 房平台的長度隨機性
- 新增可通關路徑約束，Boss 房平台高度與間距控制在跳躍能力內
- 新增 `S` 鍵平台下落
- 新增短按 / 長按 `S` 判定：短按穿過平台，長按蹲下

### v1.1
- 增加跳板長度隨機性，依關卡類型與難度調整短 / 中 / 長平台比例
- 優化小兵視野，使用半徑偵測、line-of-sight 與短暫追擊記憶
- 近戰小兵可跨平台追擊，玩家在下層時會從平台邊緣追下去
- 近戰小兵可跳躍追擊高處玩家，並加入跳躍冷卻避免亂跳
- 修復 Boss 房可行走性，平台距離與高度控制在 Boss 可跳躍範圍內
- 增加 Boss 房可站立掩體與更多平台
- 優化 Boss 跳板移動，Boss 可踩平台與牆頂追擊
- 降低 Boss 血量曲線，保留關卡成長但縮短戰鬥時間
- 強化 Boss 衝刺判定，衝刺前檢查落地、距離、高低差與落點
- 防止 Boss 衝刺撞牆，使用 dash path 預測取消不安全衝刺
- 禁止 Boss 空中衝刺

### v1.0
- 優化牆壁碰撞
- 優化牆壁生成
- 終點門新增右下生成
- 強化近戰判定
- 新增近戰消彈
- Boss 房新增更多地形
- Boss 可跳板追擊
- 強化地刺 Boss
- 召喚 Boss 可召喚遠程小兵
- 強化小兵 AI
- 修復遠程小兵子彈問題
- 修正遠程小兵持槍位置

### v0.9
- 新增近戰攻擊動畫
- 新增掩體與障礙物
- 增加後段跳板
- 修復炸彈功能
- 新增爆炸範圍傷害
- 修復/新增冰凍卷軸效果
- 新增冰球
- 提高火球與冰球命中率

### v0.8
- 移除 Boss 死亡黃色畫面
- 提高 Boss 血量
- 新增地刺 Boss
- 新增召喚型 Boss
- 新增遠程小兵
- 增加敵人數量
- 普通關卡加長
- 新增畫面外延遲渲染
- 主角近戰改為半圓揮擊
- 降低近戰攻擊速度
- 移除終點門提示
- 新增敵人成長系統

### v0.7
- 新增金幣系統
- 新增右上角 Gold UI
- 新增商店關
- 新增 Boss 前商店規律
- 新增可消耗 Gold 購物
- 新增隨機關卡生成
- 新增探索地圖掉落
- 新增地圖物資生成

### v0.6
- 新增三種物品限制背包
- 新增滿背包替換機制
- 新增 `U/I/O` 快速替換
- 新增地板 swap 系統，被替換的整疊道具會掉回玩家腳邊
- 新增掉落物圖示：小藥水、大藥水、火焰卷軸、冰凍卷軸、炸彈
- 更新背包 UI，加入固定 Slot HUD、`B` 鍵 overlay 與滿背包提示
- 修正蹲下時仍可跳躍的問題
- 強化道具生成防重疊邏輯，加入 debug log 與安全 fallback

### v0.4
- 優化道具生成位置避免重疊
- 新增 `ItemSpawnManager`，提供有限候選點搜尋、重疊檢查與安全 fallback
- 新增 `S` 鍵蹲下系統，碰撞框縮小、速度降低，站起前檢查頭頂空間
- 新增空中 `S` 快速下落
- 更改快捷鍵：`K` 施放火球術，`U/I/O` 使用道具
- 降低 Mana 回復速度到原本約 44%，減少火球連發
- 火球冷卻 UI 改成技能圖示、冷卻遮罩與 ready glow

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
- 修改 `entity/Player.java`：新增 `Direction` 四方向面向、`K` 鍵施放、Mana 消耗與冷卻、火球清單更新與繪製。
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
