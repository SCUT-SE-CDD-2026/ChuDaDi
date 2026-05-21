# Project：ChuDaDi

## 简介
这是华南理工大学2026年软件工程-软件综合开发实训课程的开发实训学生项目。

基本要求是开发一款安卓APP实现扑克游戏"锄大地（The Big Two）"的玩法，包含基础界面、游戏功能、蓝牙联机和人机算法。

## 文档

见 `./docs`：

```
./docs
│  仓库初始化指南.md
│  架构文档.md             // 架构、核心模块和设计原则
│  任务书.md               // 指导老师发布的任务书
│  设计风格.md             // UI 和元素组件设计风格规范
│  页面清单.md             // 页面与流程设计
│  游戏规则（中文版）.md
│  字体规范.md
│  AI出牌策略文档.md
│  GameRule(English).md
│  pre-commit-hook协作说明.md
│
└─develop                  // 本地私人文档库，被 git 忽略
```

## 文件目录
单 Android 模块结构：
```text
./app                Android 应用模块
./.github/workflows  GitHub Actions CI 配置
./config/detekt      detekt 配置
./docs               项目文档
./gradle             Gradle Wrapper 与版本目录
./scripts            常用检查与 hook 安装脚本
```

`app/src/main/java/com/example/chudadi/` 代码包结构：
```text
├── ai/rulebased/policy|scoring   // 规则型 AI
├── controller/client|game|server // 控制层
├── model/game/engine|entity|rule|snapshot // 模型层
├── navigation                    // 页面路由
├── network/protocol              // 通信协议
└── ui/components|game|home|result|room|theme // 视图层
```

## 开发规划
1. MVP：简单界面和基础游戏功能，基于规则的人机算法
2. 阶段二：蓝牙联机 + 符合设计风格的界面
3. 阶段三：基于强化学习和 ONNX 的 AI 算法

## 技术栈

- Android Studio
- Kotlin / Java 17
- Gradle Wrapper + Kotlin DSL
- Android Gradle Plugin 9.1.0
- Version Catalog
- Jetpack Compose / Material 3 / Navigation Compose
- Lifecycle / ViewModel Compose
- Kotlin Coroutines（StateFlow / SharedFlow）
- kotlinx.serialization / Jetpack DataStore
- ONNX Runtime Android
- Android Bluetooth Classic API
- detekt / ktlint / Android Lint
- ProGuard（Release）
- ABI 分离（arm64-v8a、armeabi-v7a）

## 代码风格和约束
- 采用 Kotlin 官方代码风格（`kotlin.code.style=official`）
- 使用 `.editorconfig` 统一基础格式约束
- 使用 `detekt` 做静态检查
- 使用 `ktlint` 做格式检查与格式化
- 除非确实有必要，否则不允许跳过 pre-commit / pre-push hook 检查
- 当前工程以单模块起步，优先保持结构清晰，避免过早复杂拆分
- 联机部分采用房主权威式 C/S 模型，客户端只发送命令，房主负责规则校验和状态广播

### **MVC 架构**：
* **Model（模型）**：运行在服务端（房主），是唯一的数据源与规则实体。
* **View（视图）**：运行在客户端，只负责数据的可视化呈现与用户交互事件的采集。
* **Controller（控制器）**：分为 Client Controller（指令封装与发送）和 Server Controller（指令接收、调度 Model、触发广播）。

### **面向对象 (OOP) 驱动**：
万物皆对象。扑克牌、玩家、牌桌、游戏阶段均封装为对象，通过接口（Interface）和抽象类（Abstract Class）定义行为，强调多态与封装。

### **设计模式应用**：
在网络通信、状态流转、AI 调度等核心环节，大量引入 GOF 经典设计模式，以保证代码的开闭原则 (OCP)。

### Hook 与 CI
- **pre-commit**：`ktlintFormat + detekt`
- **pre-push**：`detekt + lintDebug + testDebugUnitTest`
- **安装**：`scripts\install-hooks.bat`（Windows）或 `sh scripts/install-hooks.sh`
- **GitHub Actions CI**：GitHub Actions 运行 `ktlintCheck + detekt + lint + test`

## 可用命令

以下命令均可追加 `--no-daemon --console=plain`：

- 构建调试包：
```bash
./gradlew.bat assembleDebug --no-daemon --console=plain
```

- 查看 Gradle 任务：
```bash
./gradlew.bat tasks --no-daemon --console=plain
```

- 基础检查：
```bash
./gradlew.bat help --no-daemon --console=plain
./gradlew.bat lint --no-daemon --console=plain
./gradlew.bat lintFix --no-daemon --console=plain
./gradlew.bat lintDebug --no-daemon --console=plain
./gradlew.bat detekt --no-daemon --console=plain
./gradlew.bat detektGenerateConfig --no-daemon --console=plain
./gradlew.bat ktlintCheck --no-daemon --console=plain
./gradlew.bat ktlintFormat --no-daemon --console=plain
./gradlew.bat test --no-daemon --console=plain
./gradlew.bat testDebugUnitTest --no-daemon --console=plain
```

- 常用构建与清理：
```bash
./gradlew.bat clean --no-daemon --console=plain
./gradlew.bat assembleDebug --no-daemon --console=plain
./gradlew.bat build --no-daemon --console=plain
```

- 查看环境与任务：
```bash
./gradlew.bat --version --no-daemon --console=plain
./gradlew.bat tasks --no-daemon --console=plain
./gradlew.bat properties --no-daemon --console=plain
```

- 脚本：
  - `scripts/install-hooks.bat/sh`   // 安装本地 Git hook
  - `scripts/pre-commit.sh`          // ktlintFormat + detekt
  - `scripts/ci-check.sh`            // 完整 CI 检查

说明：命令列表默认不携带代理参数，方便团队成员按各自环境直接使用。若本地访问 Google Maven 较慢或超时，需要自行按本机代理方案补充环境变量。

```bash
# 示例
export https_proxy=http://127.0.0.1:10808 && export http_proxy=http://127.0.0.1:10808 && export all_proxy=socks5://127.0.0.1:10808 && ./gradlew.bat test --no-daemon --console=plain
```

## Git 规范
提交或合并前必须 `git fetch/pull` 确保分支最新。
提交信息格式：`<type>(<scope>): <subject>`（subject 使用中文）
Type：`feat` | `fix` | `docs` | `style` | `refactor` | `perf` | `test` | `chore`

## 回复格式要求
使用中文回答用户。
