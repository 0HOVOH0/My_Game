# 2D Platform Game - Final Project

## 專案概述
JavaFX 2D 橫版平台跳躍遊戲，包含三個關卡與 Boss 戰。
大一計算機實習期末專題，三人合作開發。

## 技術規格
- JDK 21
- JavaFX SDK 21
- IDE: IntelliJ IDEA
- 建構工具：Maven（javafx-maven-plugin 0.0.8）

## 專案結構
src/
├── main/
│   ├── Main.java              # 程式進入點
│   ├── GameLoop.java          # AnimationTimer 主迴圈
│   ├── Config.java            # 全域常數（視窗大小、重力、FPS）
│   ├── entity/
│   │   ├── Entity.java        # 抽象基底類別
│   │   ├── Player.java        # 玩家角色
│   │   ├── Enemy.java         # 普通敵人
│   │   └── Boss.java          # Boss 角色
│   ├── fsm/
│   │   ├── StateMachine.java  # 有限狀態機基底
│   │   ├── BossState.java     # Boss 狀態 enum
│   │   └── BossStateMachine.java
│   ├── physics/
│   │   ├── Gravity.java       # 重力系統
│   │   └── Collision.java     # AABB 碰撞判定
│   ├── scene/
│   │   ├── StartScene.java    # 開始畫面
│   │   ├── Level1Scene.java   # 關卡一
│   │   ├── Level2Scene.java   # 關卡二
│   │   ├── BossScene.java     # Boss 關卡
│   │   └── EndScene.java      # 結算畫面
│   └── ui/
│       ├── HealthBar.java     # 血量條元件
│       └── HUD.java           # 遊戲中 UI
└── resources/
├── images/
├── audio/
└── fxml/

## 程式規範
- 所有常數集中放在 Config.java，禁止 magic number
- Entity 為所有遊戲物件的祖先類別
- 場景切換統一透過 Main.java 的 switchScene() 方法
- 遊戲迴圈使用 deltaTime 確保 Framerate Independence
- 碰撞判定統一使用 Collision.java 的 checkAABB() 方法

## 物理參數（Config.java）
- GRAVITY = 980.0
- JUMP_FORCE = -500.0
- PLAYER_SPEED = 200.0
- FPS = 60

## Boss FSM 狀態
- IDLE：站立等待
- CHASE：追蹤玩家（血量 100~60%）
- DASH：衝刺攻擊（血量 60~30%）
- RAGE：狂暴模式，加入投射物（血量 30~0%）
- HURT：受傷硬直
- DEAD：死亡

## 注意事項
- 保留所有原有註解，不要刪除 TODO
- 新增功能前先確認 Config.java 有沒有對應常數
- 場景切換記得停止前一個場景的 AnimationTimer