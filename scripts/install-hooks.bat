@echo off
setlocal

set "PRE_COMMIT_HOOK=.git\hooks\pre-commit"
set "PRE_PUSH_HOOK=.git\hooks\pre-push"

if not exist ".git\hooks" mkdir ".git\hooks"

(
  echo #!/usr/bin/env sh
  echo set -eu
  echo.
  echo exec "$(git rev-parse --show-toplevel)/scripts/pre-commit.sh"
) > "%PRE_COMMIT_HOOK%"

(
  echo #!/usr/bin/env sh
  echo set -eu
  echo.
  echo exec "$(git rev-parse --show-toplevel)/scripts/pre-push.sh"
) > "%PRE_PUSH_HOOK%"

echo [hook-install] Installed pre-commit hook.
echo [hook-install] It now forwards to scripts/pre-commit.sh
echo [hook-install] Installed pre-push hook.
echo [hook-install] It now forwards to scripts/pre-push.sh

endlocal
