# Project：ChuDaDi

## 简介

这是华南理工大学2026年软件工程-软件综合开发实训课程的开发实训学生项目。

基本要求是开发一款安卓APP实现扑克游戏“锄大地（The Big Two）”的玩法。需要包含基础界面和游戏功能，蓝牙联机功能和人机算法。

## 文档

文档见`./docs`

## 文件目录

当前采用单 Android 模块结构：

```text
./app                Android 应用模块
./config/detekt      detekt 配置
./docs               项目文档
./gradle             Gradle Wrapper 与版本目录
./scripts            常用检查脚本
```

## 开发规划

MVP：基础界面和游戏功能，基于规则的人机算法

阶段二：实现蓝牙联机功能

阶段三：实现基于强化学习和ONNX的AI人机算法

阶段四（额外扩展）：实现通过云服务器联机的功能

## 技术栈

- Android Studio
- Kotlin
- Gradle Wrapper + Kotlin DSL
- Version Catalog (`gradle/libs.versions.toml`)
- Jetpack Compose
- Material 3
- Navigation Compose
- Lifecycle / ViewModel Compose
- Kotlin Coroutines
- StateFlow / SharedFlow
- kotlinx.serialization
- Jetpack DataStore
- ONNX Runtime Android
- Android Bluetooth Classic API
- Android Lint
- detekt
- ktlint

## 代码风格和约束

- 采用 Kotlin 官方代码风格（`kotlin.code.style=official`）
- 使用 `.editorconfig` 统一基础格式约束
- 使用 `detekt` 做静态检查
- 使用 `ktlint` 做格式检查与格式化
- 当前工程以单模块起步，优先保持结构清晰，避免过早复杂拆分
- 联机部分采用房主权威式 C/S 模型，客户端只发送命令，房主负责规则校验和状态广播

## 可用命令

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
./gradlew.bat lint
./gradlew.bat lintFix
./gradlew.bat detekt
./gradlew.bat detektGenerateConfig
./gradlew.bat ktlintCheck
./gradlew.bat ktlintFormat
./gradlew.bat test
```

- 常用构建与清理：

```bash
./gradlew.bat clean
./gradlew.bat assembleDebug --no-daemon --console=plain
./gradlew.bat build --no-daemon --console=plain
```

- 查看环境与任务：

```bash
./gradlew.bat --version
./gradlew.bat tasks --no-daemon --console=plain
./gradlew.bat properties --no-daemon --console=plain
```

- 脚本：
  - `scripts/pre-commit.sh`
  - `scripts/ci-check.sh`

说明：命令列表默认不携带代理参数，方便团队成员按各自环境直接使用。若本地访问 Google Maven 较慢或超时，需要自行按本机代理方案补充环境变量。

```
# 示例
export https_proxy=http://127.0.0.1:10808 && export http_proxy=http://127.0.0.1:10808 && export all_proxy=socks5://127.0.0.1:10808 && ./gradlew.bat test --no-daemon --console=plain
```

## Git 规范

提交或合并前必须使用`git fetch/git pull`确保分支处于最新状态。

### 提交信息格式

```
<type>(<scope>): <subject> //subject使用中文

<body>

<footer>
```

**Type**:

- `feat`: 新功能
- `fix`: 修复
- `docs`: 文档
- `style`: 格式（不影响代码）
- `refactor`: 重构
- `perf`: 性能优化
- `test`: 测试
- `chore`: 构建/工具


## 回复格式要求

使用中文回答用户。
