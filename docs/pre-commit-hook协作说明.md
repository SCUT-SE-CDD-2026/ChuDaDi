# Pre-commit Hook 协作说明

## 1. 文档目的

本文档说明当前仓库的 `pre-commit hook` 实现、作用和团队成员的使用方式，便于统一提交前检查流程。

## 2. 当前实现

当前本地 Git `pre-commit hook` 会转调仓库中的脚本：

- `scripts/pre-commit.sh`

脚本当前执行的检查为：

- `./gradlew ktlintFormat detekt`

含义如下：

- `ktlintFormat`：自动修复 Kotlin 和 Kotlin DSL 的格式问题
- `detekt`：执行 Kotlin 静态分析检查

## 3. 触发时机

- 当开发者执行 `git commit` 时，若本地已正确安装 `pre-commit hook`，脚本会自动运行
- 若检查失败，本次提交会被拦截

## 4. 运行时输出

脚本开始时会输出：

- `Running pre-commit hook: ktlintFormat + detekt`

若成功，会输出：

- `Hook passed.`

若失败，会输出简短提示，提醒开发者先修复问题再重新提交。

## 5. 失败时如何处理

常见处理方式：

- 先运行 `./gradlew.bat ktlintFormat`
- 再运行 `./gradlew.bat detekt`
- 如有 Android Lint 可自动修复项，可运行 `./gradlew.bat lintFix`
- 修复完成后重新执行 `git commit`

## 6. 安装说明

当前仓库中的 hook 逻辑脚本已放在版本库内，但 `.git/hooks/` 本身不受 Git 管理。

因此，新成员拉取仓库后，仍需在本机安装本地 hook。当前做法是让本地 `.git/hooks/pre-commit` 转调：

- `scripts/pre-commit.sh`

推荐直接使用安装脚本：

- Windows：`scripts\install-hooks.bat`
- Git Bash / macOS / Linux：`sh scripts/install-hooks.sh`

## 7. 团队建议

- 不要绕过 `pre-commit hook` 作为日常提交方式
- 提交前优先保证 `ktlintFormat`、`detekt`、`lint`、`test` 可正常运行
- 若本地访问远程依赖较慢，应按各自环境补充代理设置，但不要把个人代理参数写死进团队默认命令
