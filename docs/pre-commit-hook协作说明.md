# Pre-commit / Pre-push Hook 协作说明

## 1. 文档目的

本文档说明当前仓库的 `pre-commit hook` 与 `pre-push hook` 实现、作用和团队成员的使用方式，便于统一提交前检查流程。

## 2. 当前实现

当前本地 Git hook 会转调仓库中的脚本：

- `scripts/pre-commit.sh`
- `scripts/pre-push.sh`

脚本当前执行的检查为：

- `./gradlew ktlintFormat detekt --daemon --console=plain`
- `./gradlew lintDebug testDebugUnitTest --daemon --console=plain`

含义如下：

- `ktlintFormat`：自动修复 Kotlin 和 Kotlin DSL 的格式问题，尽量在提交前直接收敛格式噪音
- `detekt`：执行 Kotlin 静态分析检查，把 Kotlin 侧质量问题尽量前移到 commit 阶段
- `lintDebug`：执行 Debug 变体的 Android 静态检查，保留 API 兼容性、资源清理和组件暴露问题检查，同时降低全变体 lint 的本地等待时间
- `testDebugUnitTest`：执行 Debug 单元测试，作为推送前兜底校验，比全量 `test` 更轻

这样拆分的目的：

- `pre-commit` 一次 Gradle 调用内同时完成 Kotlin 格式修复和静态分析，尽量在更早阶段拦住问题
- `pre-push` 专注 Android lint 和单元测试，避免每次 push 再重复执行 `detekt`
- CI 仍建议继续执行更完整的 `ktlintCheck detekt lint test`

## 3. 当前质量规则

### 3.1 detekt 新增规则

当前 `detekt` 基于默认配置继续启用以下方向的规则：

- 复杂度控制：`LongMethod`、`LargeClass`、`CyclomaticComplexMethod`、`NestedBlockDepth`、`ComplexCondition`
- 控制流约束：`ReturnCount`、`LoopWithTooManyJumpStatements`
- 异常处理：`SwallowedException`、`TooGenericExceptionCaught`、`PrintStackTrace`、`RethrowCaughtException`
- 潜在缺陷：`UnsafeCast`
- 命名补充：`FunctionNaming` 对 `@Composable` 与 `@Preview` 注解函数放宽命名限制

这些规则的主要目的：

- 避免游戏规则判断、状态流转和后续联机逻辑出现过长、过深、过难维护的方法
- 避免出现吞异常、直接打印堆栈、捕获过于宽泛异常等低质量异常处理方式
- 在 Kotlin 代码层面尽早发现可疑写法和坏味道

### 3.2 detekt 当前豁免项

结合当前阶段和现有代码基线，保留了以下豁免：

- `UndocumentedPublicClass` 与 `UndocumentedPublicFunction` 继续关闭，不强制要求公开 API 编写 KDoc
- `MagicNumber` 采用有限启用而不是全局严格启用：
  - 对 `**/ui/**` 目录豁免，避免大量 Compose 布局数值直接报错
  - 忽略 `-1`、`0`、`1`、`2`、`3`、`4`、`5`、`13`、`26`、`39`、`52` 等当前项目中的常见基础数值
  - 忽略属性声明、伴生对象属性、命名参数和注解中的数值
- `TopLevelPropertyNaming` 当前关闭，避免主题文件中的颜色与排版顶层属性立即触发大面积改名

说明：

- 该配置仍会对非 UI 目录中的业务逻辑魔法数进行一定约束
- 如果后续主题、常量或命名规范统一完成，可再重新评估是否开启 `TopLevelPropertyNaming`

### 3.3 Android lint 当前规则

当前 `lint` 已通过 Gradle 配置统一以下策略：

- `abortOnError = true`：发现 error 级问题直接失败
- `checkReleaseBuilds = true`：发布构建同样执行 lint
- 输出 `xml`、`html`、`sarif` 报告，便于本地查看和 CI 归档

当前重点关注的 error 级问题：

- `NewApi`
- `InlinedApi`
- `ObsoleteSdkInt`
- `ExportedReceiver`
- `ExportedService`

当前保留的豁免项：

- `TypographyFractions`
- `MissingTranslation`
- `ContentDescription`

当前附加关注的 warning 项：

- `UnusedResources`

说明：

- 现阶段不把国际化和无障碍作为阻断门槛
- 仍保留对 Android API 兼容性、资源冗余和组件安全的基本约束

### 3.4 ktlint 当前规则

`ktlint` 继续保持轻量，主要负责格式一致性，不承担复杂语义检查。

当前新增或明确的配置包括：

- `ignoreFailures = false`，格式问题不会被静默忽略
- 启用 `PLAIN` 与 `CHECKSTYLE` 报告器，兼顾终端可读性和 CI 解析
- 排除 `**/build/**` 与 `**/generated/**` 目录
- `.editorconfig` 中明确允许 trailing comma：
  - `ij_kotlin_allow_trailing_comma = true`
  - `ij_kotlin_allow_trailing_comma_on_call_site = true`

保留的基础格式约束包括：

- Kotlin 官方代码风格
- `max_line_length = 120`
- 统一使用 4 空格缩进

## 4. 触发时机

- 当开发者执行 `git commit` 时，若本地已正确安装 `pre-commit hook`，会自动运行 `scripts/pre-commit.sh`
- 当开发者执行 `git push` 时，若本地已正确安装 `pre-push hook`，会自动运行 `scripts/pre-push.sh`
- 任一检查失败时，对应的提交或推送会被拦截

补充说明：

- `pre-commit` 会直接运行 `ktlintFormat`，如果自动修复了格式，开发者需要重新确认变更并重新 `git add`
- `pre-commit` 同时运行 `detekt`，因此 Kotlin 代码中的复杂度、异常处理和潜在缺陷问题会在 commit 阶段被拦截

## 5. 运行时输出

脚本开始时会输出：

- `Running pre-commit hook: ktlintFormat + detekt`
- `Running pre-push hook: lintDebug + testDebugUnitTest`

若成功，会输出：

- `Hook passed.`

若失败，会输出简短提示，提醒开发者先修复问题再重新提交。

## 6. 失败时如何处理

常见处理方式：

- 先运行 `./gradlew.bat ktlintFormat`
- 再运行 `./gradlew.bat detekt`
- 再运行 `./gradlew.bat lintDebug`
- 最后运行 `./gradlew.bat testDebugUnitTest`
- 如有 Android Lint 可自动修复项，可运行 `./gradlew.bat lintFix`
- 修复完成后重新执行 `git commit` 或 `git push`

## 7. 安装说明

当前仓库中的 hook 逻辑脚本已放在版本库内，但 `.git/hooks/` 本身不受 Git 管理。

因此，新成员拉取仓库后，仍需在本机安装本地 hook。当前做法是让本地 hook 转调：

- `scripts/pre-commit.sh`
- `scripts/pre-push.sh`

推荐直接使用安装脚本：

- Windows：`scripts\install-hooks.bat`
- Git Bash / macOS / Linux：`sh scripts/install-hooks.sh`

安装完成后，会在本地生成：

- `.git/hooks/pre-commit`
- `.git/hooks/pre-push`

## 8. 团队建议

- 不要绕过 `pre-commit hook` 作为日常提交方式
- 不要绕过 `pre-push hook` 作为日常推送方式
- 提交前优先保证 `ktlintFormat`、`detekt` 可正常运行；推送前优先保证 `lintDebug`、`testDebugUnitTest` 可正常运行
- 合并前或 CI 中仍建议执行完整的 `./gradlew.bat ktlintCheck detekt lint test`
- 若本地访问远程依赖较慢，应按各自环境补充代理设置，但不要把个人代理参数写死进团队默认命令
