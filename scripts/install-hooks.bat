@echo off
setlocal

set "HOOK=.git\hooks\pre-commit"

if not exist ".git\hooks" mkdir ".git\hooks"

(
  echo #!/usr/bin/env sh
  echo set -eu
  echo.
  echo exec "$(git rev-parse --show-toplevel)/scripts/pre-commit.sh"
) > "%HOOK%"

echo [hook-install] Installed pre-commit hook.
echo [hook-install] It now forwards to scripts/pre-commit.sh

endlocal
