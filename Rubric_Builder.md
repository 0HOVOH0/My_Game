# Rubric_Builder — Dark Continent Adventure

> 角色：品質衡量設計師。每次更新後根據當輪 bad_outputs 修訂標準。
> 格式：每個維度含 Pass / Fail 標準 + 正反例。

---

## 版本紀錄

| 版本 | 日期 | 說明 | bad_outputs |
|------|------|------|-------------|
| v0 | 2026-05-25 | 初版，探索現況並制定基準 | [] |
| v1 | 2026-05-25 | D1+D2 修正：完整按鍵提示、底部控制條、道具撿取飄字、Goal Door 提示強化 | [] |
| v2 | 2026-05-26 | D4 修正：GameOverOverlay 文字對齊；D2 修正：飄字精確置中；D3/D5 重新評估 | [] |
| v3 | 2026-05-26 | Bug 修正：Goal Door 提示框溢出；D6 程式碼評估通過 | [] |

---

## Dimension 1 — 操控明確性（Controls Clarity）

**定義**：玩家不查文件就能在 30 秒內理解所有操作。

| | 標準 |
|--|------|
| **Pass** | 遊戲畫面中有操控提示（Keybind Hint），涵蓋移動、攻擊、道具、跳躍；開始畫面說明至少移動+攻擊；道具快捷鍵在 HUD 清晰標示 |
| **Fail** | 任何功能鍵未在遊戲中標示；按鍵說明僅出現在開始畫面且不完整；物品格顯示 "Slot1 [U]:" 但玩家第一次不知道 U 是什麼 |

**正例（Pass）**：HUD 右下顯示「[J]攻擊 [K]火球 [U/I/O]使用道具 [B]背包」  
**反例（Fail，v0 現況）**：開始畫面只有「WASD 移動　Space/W 跳躍　J 攻擊」，K/U/I/O 完全未提示；遊戲中 HUD 只顯示 slot 標籤

---

## Dimension 2 — 回饋即時性（Feedback Immediacy）

**定義**：每個玩家動作在 0.1 秒內有視覺或音效回應。

| | 標準 |
|--|------|
| **Pass** | 攻擊有揮擊弧視覺效果；受傷有閃爍；撿到道具有提示文字；火球有冷卻動畫；炸彈爆炸有範圍視覺 |
| **Fail** | 任一主要動作（攻擊命中、受傷、撿取、使用道具）無視覺回應；道具使用後無 HUD 訊息確認 |

**正例（Pass）**：Player.drawAttackBox() 已有揮擊弧；火球有 ORANGERED 圓形  
**反例（Fail，v0 現況）**：撿到道具、使用藥水後無飄字提示；炸彈爆炸無視覺效果（僅傷害判定）

---

## Dimension 3 — 視覺一致性（Visual Consistency）

**定義**：所有場景使用同一套色調（dark/sci-fi）和字體規範，元素風格統一。

| | 標準 |
|--|------|
| **Pass** | 所有 FXML 場景 background 為 `#06080f` 系列深色；按鈕全部 monospace；Canvas 場景地板/平台顏色與主選單色調呼應；HUD 背景統一半透明深色面板 |
| **Fail** | Canvas 場景使用預設白色背景；不同場景按鈕字體不一致；FXML 場景與 Canvas 場景色調明顯衝突 |

**正例（Pass）**：start-scene.fxml `#06080f` 深色背景 + `#00e5ff` 藍色 monospace  
**正例（Pass）**：DungeonMapRenderer `#121219`、BossScene `#1a0025`（紫色氛圍），均為深色系一致  
**反例（Fail）**：Canvas 場景使用預設白色背景；FXML 按鈕使用非 monospace 字體

---

## Dimension 4 — 可玩性流暢度（Playflow Smoothness）

**定義**：新手玩家能在不查攻略的情況下完成 Level1→Level2→Shop 不死亡超過 3 次。

| | 標準 |
|--|------|
| **Pass** | Level1 有足夠平台讓新手練習移動與跳躍；敵人攻擊範圍明顯；商店流程自然；Boss 難度有階段提示；死亡後有 R 鍵重試提示 |
| **Fail** | 第一關出現即死陷阱；敵人追蹤範圍過大；死亡後玩家不知道如何重試；Boss 無視覺階段提示 |

**正例（Pass）**：BossScene 已有階段轉換閃紅光 + 文字提示；玩家死亡後 R 鍵重試  
**反例（Fail，v0 現況）**：R 鍵重試提示沒有在 HUD 死亡畫面上明顯標示；商店入口/出口無引導文字

---

## Dimension 5 — 重複性控制（Repetition Management）

**定義**：玩家在一個循環（Level1 → Boss 首殺）內不會感到「我又在做一樣的事」。

| | 標準 |
|--|------|
| **Pass** | 每輪 Level2 地圖、敵人配置有顯著差異；Boss 類型輪換（不重複連續出同一個）；商店每次商品有變化；普通關與 Boss 關視覺氛圍明顯不同 |
| **Fail** | 連續兩關地圖佈局幾乎相同；Boss 每輪都是同一個；商店商品固定不變 |

**正例（Pass）**：Level2 程序生成地圖 + 敵人配置；Boss 類型輪換已有 rollBossType() 防重複；Level1 亦使用 `PlatformDungeonGenerator` 程序生成，非固定地圖  
**正例（Pass）**：商店商品 v1.3 起已有隨機化（每次從商品池抽 3-6 樣，R 刷新）  
**反例（Fail）**：Boss 每輪都是同一個；商店商品固定不變（已排除）

---

## Dimension 6 — 難度合理性（Difficulty Balance）

**定義**：遊戲在「難但公平」區間，不會讓玩家感到無法掌控。

| | 標準 |
|--|------|
| **Pass** | 玩家有足夠魔力維持遠攻（regen 3.5/s，每 5.7 秒自然恢復一次火球）；近戰傷害 20 + 火球 25 可在合理時間擊敗普通敵人；陷阱傷害有預警；Boss 的 600 HP 在 rage 階段結束前可被打倒 |
| **Fail** | 玩家血量 100 + 受傷無敵 0.5s 導致接觸傷害秒殺；Boss DASH 傷害量與持續時間讓玩家無法迴避；藥水回血量 30 無法追上 Boss rage 階段傷害速度 |

**正例（Pass）**：INVINCIBLE_DURATION 0.5s 有效防止多重命中；Boss 接觸傷害 4 + Dash 傷害 7 不會秒殺；RAGE 火球 15/1.5s 給予足夠閃避窗口；玩家近戰 DPS ≈ 30.8，stage 1 Boss 680 HP 約 22 秒可打倒  
**反例（Fail）**：Boss rage 投射物每幀生成；Boss 接觸傷害高於玩家 HP 50%；Boss 衝刺一次致死

---

## 評分使用方式

對每個版本的輸出（bad_outputs）中標記哪些維度 Fail：

```
版本 vX 評估：
- D1 操控明確性：[ Pass / Fail ] — 原因：___
- D2 回饋即時性：[ Pass / Fail ] — 原因：___
- D3 視覺一致性：[ Pass / Fail ] — 原因：___
- D4 可玩性流暢度：[ Pass / Fail ] — 原因：___
- D5 重複性控制：[ Pass / Fail ] — 原因：___
- D6 難度合理性：[ Pass / Fail ] — 原因：___
```

---

## v0 現況評估（改善前基準）

| 維度 | 狀態 | 主要問題 |
|------|------|----------|
| D1 操控明確性 | **Fail** | 開始畫面說明不完整（缺 K/U/I/O/B/R）；遊戲中無全域操控提示 |
| D2 回饋即時性 | **部分Pass** | 攻擊弧、火球、閃爍已有；撿取道具/使用道具無飄字 |
| D3 視覺一致性 | **部分Pass** | FXML 場景風格統一；Canvas 場景背景顏色待確認 |
| D4 可玩性流暢度 | **部分Pass** | Boss 階段提示有；死亡後 R 鍵提示不夠明顯 |
| D5 重複性控制 | **部分Pass** | Level2 程序生成有效；Level1 固定無變化 |
| D6 難度合理性 | **待測試** | 需實際遊玩確認 Boss rage 平衡 |

---

## v1 改善記錄（2026-05-25）

### 本輪修改
| 檔案 | 改動內容 |
|------|----------|
| `start-scene.fxml` | 按鍵說明補全至 7 個（A/D/W/S/J/K/U/I/O/B/R） |
| `HudRenderer.java` | 新增 `drawControlsHint()` 底部按鍵提示條 |
| `HudRenderer.java` | 新增 `drawPickupNotice()` 道具撿取飄字 |
| `Level1Scene.java` | 加入控制提示條；改善 Goal Door 提示（金色+背景）|
| `Level2Scene.java` | 加入控制提示條；飄字計時；改善 Goal Door 提示 |
| `BossScene.java` | 加入控制提示條；道具撿取飄字 |
| `ShopScene.java` | 加入控制提示條 |

### v1 評估
| 維度 | 狀態 | 備註 |
|------|------|------|
| D1 操控明確性 | **Pass** | 所有場景底部顯示完整按鍵；開始畫面完整說明 |
| D2 回饋即時性 | **Pass** | 撿取道具/金幣/加血道具均有金色飄字 1.4 秒 |
| D3 視覺一致性 | **待測試** | 需實際遊玩確認 Canvas 場景整體色調 |
| D4 可玩性流暢度 | **部分Pass** | Goal Door 提示更清楚；R 鍵提示已存在（待確認顯眼度） |
| D5 重複性控制 | **部分Pass** | 未改動，待下輪處理 |
| D6 難度合理性 | **待測試** | 未改動，需實際遊玩確認 |

### 待下輪處理（不合理點）
1. GameOverOverlay 的 "按 R 重新開始" 文字置中算法使用固定偏移 `-173` 和 `-65`，在不同視窗縮放下會跑偏 → 改用 `gc.measureText()` 或相對計算
2. Level1 `drawGoalPrompt` 的背景框位置可能超出門邊界（提示框偏左 34px 但有時門靠近世界右端）
3. `drawPickupNotice` 的 `textW` 用 `length * 9.5` 估算中文寬度不準確 → 考慮改用固定居中或更大字寬係數

---

---

## v2 改善記錄（2026-05-26）

### 本輪修改
| 檔案 | 改動內容 |
|------|----------|
| `HudRenderer.java` | 新增 `drawGameOverOverlay(gc, title, message, hint)` 共用方法，改用 `TextAlignment.CENTER` |
| `HudRenderer.java` | `drawPickupNotice()` 改用 `TextAlignment.CENTER`，移除 `length * 9.5` 估算 |
| `Level1Scene.java` | `drawGameOverOverlay()` 改為呼叫 `HudRenderer.drawGameOverOverlay()` |
| `Level2Scene.java` | 同上 |
| `BossScene.java` | 同上 |
| `CLAUDE.md` | 補充 HudRenderer 方法列表與背景色規範 |
| `README.md` | 新增 v1.7 changelog 記錄 |

### v2 評估
| 維度 | 狀態 | 備註 |
|------|------|------|
| D1 操控明確性 | **Pass** | 維持 v1 結果 |
| D2 回饋即時性 | **Pass** | 飄字置中更精確（TextAlignment.CENTER） |
| D3 視覺一致性 | **Pass** | 程式碼確認：DungeonMapRenderer `#121219`、BossScene `#1a0025`，全場景深色一致 |
| D4 可玩性流暢度 | **Pass** | GAME OVER 文字對齊修正，不再因視窗縮放偏移 |
| D5 重複性控制 | **Pass** | 程式碼確認：Level1 亦為程序生成（PlatformDungeonGenerator）；商店 v1.3 已隨機化 |
| D6 難度合理性 | **待測試** | 未改動，需實際遊玩確認 |

### 待下輪處理
1. D6 難度：Boss rage 階段投射物密度與玩家 HP 100 的平衡感需確認
2. Goal Door `drawGoalPrompt` 背景框在門靠近右側世界邊緣時可能超出 canvas 寬度

---

## v3 改善記錄（2026-05-26）

### 本輪修改
| 檔案 | 改動內容 |
|------|----------|
| `HudRenderer.java` | 新增 `drawGoalPrompt(gc, screenCx, screenY)` 螢幕座標版本，X 軸 clamp 防止邊緣溢出，TextAlignment.CENTER |
| `Level1Scene.java` | 移除 translate 內的 drawGoalPrompt，改在 restore 後呼叫 HudRenderer 版本；移除無用 Font import |
| `Level2Scene.java` | 同上 |

### v3 評估（D6 程式碼分析）
| 數據 | 數值 | 評估 |
|------|------|------|
| 玩家 HP | 100 | 合理底數 |
| 玩家近戰 DPS | 20 damage × 1.54 atk/s = 30.8 | 足夠輸出 |
| Boss 接觸傷害 | 4/hit（0.5s 無敵限制） | 不秒殺 |
| Boss Dash 傷害 | 7/hit | 不秒殺（需 14 次才能擊殺） |
| Boss RAGE 火球 | 15/hit，每 1.5s | 玩家有充裕閃避時間 |
| Boss HP（stage 1） | 600 × 1.05 × 1.08 ≈ 680 | 約 22s 純近戰可打倒 |

### v3 Rubric 總評
| 維度 | 狀態 |
|------|------|
| D1 操控明確性 | **Pass** |
| D2 回饋即時性 | **Pass** |
| D3 視覺一致性 | **Pass** |
| D4 可玩性流暢度 | **Pass** |
| D5 重複性控制 | **Pass** |
| D6 難度合理性 | **Pass** — 程式碼分析數值合理 |

---

*所有維度已達 Pass，下輪以收到 bad_outputs 或發現新問題時更新。*
