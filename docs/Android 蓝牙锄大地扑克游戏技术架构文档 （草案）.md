# Android 蓝牙锄大地扑克游戏技术架构文档 (OOP & MVC 版)

## 1\. 架构总览与核心思想

本项目采用 **CS权威服务器架构**，并在单端内实现物理与逻辑上的前后端分离。在代码组织与工程范式上，严格遵循 **OOP（面向对象编程）** 与 **MVC（Model-View-Controller）** 架构。

### 1.1 核心设计原则

* **MVC 物理分离**：
  * **Model（模型）**：运行在服务端（房主），是唯一的数据源与规则实体。
  * **View（视图）**：运行在客户端，只负责数据的可视化呈现与用户交互事件的采集。
  * **Controller（控制器）**：分为 Client Controller（指令封装与发送）和 Server Controller（指令接收、调度 Model、触发广播）。
* **面向对象 (OOP) 驱动**：万物皆对象。扑克牌、玩家、牌桌、游戏阶段均封装为对象，通过接口（Interface）和抽象类（Abstract Class）定义行为，强调多态与封装。
* **设计模式应用**：在网络通信、状态流转、AI 调度等核心环节，大量引入 GOF 经典设计模式，以保证代码的开闭原则 (OCP)。

---

## 2\. 核心模块划分 (基于 MVC 拆解)

### 2.1 共用协议层 (Shared Protocol)

定义前后端共用的基础对象与通信契约。

* **实体类 (Entities)**：`Card` (牌), `Player` (玩家), `Room` (房间)。
* **命令对象 (Command)**：运用 **命令模式 (Command Pattern)**，将所有的上行请求封装为对象（如 `PlayCardCommand`, `PassCommand`）。
* **快照对象 (Snapshot)**：用于下发的数据传输对象 (DTO)。

### 2.2 Model 层 (权威游戏模型 - Server端)

管理所有的业务数据与核心规则。

* **状态管理**：`GameContext` 维护全局数据。运用 **状态模式 (State Pattern)** 管理游戏进程（`WaitReadyState`, `DealCardState`, `PlayerTurnState`, `GameOverState`）。
* **规则引擎**：运用 **策略模式 (Strategy Pattern)** 抽象牌型校验规则（如 `SingleCardRule`, `PairRule`, `BombRule`）。
* **防作弊投影**：对外提供 `getSnapshotForPlayer(playerId)` 接口，生成抹除对手底牌的局部视图。

### 2.3 Controller 层 (控制调度与网络 - 跨端)

* **Server Controller**：作为后端的入口。监听蓝牙请求，解析为 `Command` 对象，调用 Model 的行为方法。状态变更后，触发网络广播。
* **Client Controller**：作为前端的控制中枢。接收 View 的点击事件，执行本地初步校验，封装为 `Command` 并通过蓝牙发送；同时监听下行的 `Snapshot`，更新本地代理模型。

### 2.4 View 层 (前端展示 - Client端)

* **被动视图 (Passive View)**：运用 **观察者模式 (Observer Pattern)**，View 层只负责订阅 Client Controller 中维护的 `LocalProxyModel`。当数据变化时，UI 自动重绘。
* **UI 实体**：桌面 View、手牌 View、动画特效层。

### 2.5 AI 子系统 (ONNX 模块)

* **虚拟控制器**：运用 **适配器模式 (Adapter Pattern)**，将 AI 包装成一个实现了 `IPlayerController` 接口的对象。对于 Server Controller 来说，AI 与真实蓝牙玩家是多态的，无需区分处理。

---

## 3\. 核心设计模式落地场景

| 设计模式                  | 应用场景与解决的问题                                         |
| ------------------------- | ------------------------------------------------------------ |
| **命令模式 (Command)**    | 用于 C/S 架构的网络通信。把“出牌操作”封装成 `GameCommand` 接口的实现类。服务端收到后直接调用 `command.execute(model)`。 |
| **观察者模式 (Observer)** | 客户端 UI 监听数据的变化。服务端采用事件总线机制，当 Model 发生变化时，通知 Server Controller 执行状态广播。 |
| **状态模式 (State)**      | 解决游戏流程控制中海量的 `if-else`。将“准备阶段”、“出牌阶段”、“结算阶段”抽象为独立的 State 类，规范游戏生命周期。 |
| **策略模式 (Strategy)**   | 用于校验“出牌是否合法”。把单张、对子、顺子、炸弹的校验算法封装成不同的策略类，方便扩展新规则（如加入特殊癞子规则）。 |
| **适配器模式 (Adapter)**  | AI 推理输出的是一个高维浮点数组（Tensor）。通过 Adapter 将其转译为系统能识别的 `PlayCardCommand`，让 AI 伪装成普通客户端。 |
| **单例模式 (Singleton)**  | 全局唯一的设备管理类，如 `BluetoothConnectionManager`、`SoundManager`。 |
| **工厂方法 (Factory)**    | 牌堆的生成（`DeckFactory`），以及根据下行 JSON 动态实例化对应 Command 对象的解析器。 |

---

## 4\. 关键数据流转 (MVC 闭环)

1. **\[User -> View\]**：玩家点击屏幕选牌，点击“出牌”按钮。
2. **\[View -> Client Controller\]**：View 调用 `clientController.onRequestPlayCards(cards)`。
3. **\[Client Controller -> Network\]**：Controller 实例化 `PlayCommand(cards)`，序列化为 JSON 通过蓝牙发送。
4. **\[Network -> Server Controller\]**：服务端通过 **工厂模式** 将 JSON 反序列化为 `PlayCommand` 对象。
5. **\[Server Controller -> Model\]**：Controller 调用 Model 层执行 `command.execute(gameContext)`。Model 内部利用 **策略模式** 校验合法性，通过 **状态模式** 流转到下一玩家。
6. **\[Model -> Server Controller\]**：Model 状态更新触发 **观察者** 回调，Server Controller 抓取最新的安全状态投影。
7. **\[Server Controller -> Network -> View\]**：状态快照下发，客户端本地代理 Model 更新，View 观察到数据变化，刷新屏幕。

---

## 5\. 仓库文件夹结构草图 (Directory Sketch)

基于多模块架构，Android Studio 工程的目录结构如下：

```text
cdd-android/ (项目根目录)
├── build.gradle.kts             # 项目级 Gradle
├── settings.gradle.kts
└── app/                         # 【唯一的 Android 模块】
    ├── build.gradle.kts         # App 级 Gradle (在这里引入 ONNX 和 Compose 依赖)
    └── src/main/
        ├── assets/              # 存放 cdd_model.onnx 模型文件
        ├── res/                 # 图片、字符串等资源
        └── kotlin/com/qzfm/cdd/ # (或 java/com/qzfm/cdd/) 所有代码的根包
            │
            ├── CDDApp.kt        # 全局 Application 类
            │
            ├── model/           # 【M】模型层：纯纯的 Kotlin 数据与规则，不包含任何 Android API
            │   ├── entity/      # 基础实体：Card(牌), Player(玩家), Room(房间)
            │   ├── rule/        # 游戏规则引擎：牌型校验规则 (SingleRule, BombRule)
            │   └── engine/      # 权威状态机：GameEngine (管理全局手牌、判断输赢)
            │
            ├── view/            # 【V】视图层：只做 UI 渲染，不写任何游戏规则 (Compose)
            │   ├── activity/    # GameActivity (主界面入口)
            │   ├── widget/      # 自定义 UI 组件：CardView(扑克牌), TableView(牌桌)
            │   └── dialog/      # 结算弹窗、连接设备弹窗等
            │
            ├── controller/      # 【C】控制层：连接 View 和 Model，处理核心流程
            │   ├── client/      # 前端调度：接收玩家点击，向后发指令 (LocalPlayerController)
            │   └── server/      # 后端调度：处理所有人的出牌指令，更新 Model 并广播
            │
            ├── network/         # 【基础设施】蓝牙与通信协议
            │   ├── protocol/    # 前后端通信协议：GameCommand (上行指令), StateSnapshot (下行快照)
            │   └── bluetooth/   # 蓝牙连接管理：BluetoothServer, BluetoothClient
            │
            ├── ai/              # 【AI 子系统】ONNX 相关的都在这里
            │   ├── OnnxBot.kt   # 加载模型、推理逻辑 (虚拟的 AI Controller)
            │   └── TensorUtil.kt# 把游戏状态转换为 FloatArray 的工具类
            │
            └── utils/           # 通用工具类 (如日志、SharedPreferences、常量)
```