@echo off
setlocal enabledelayedexpansion

:: 获取 Git 仓库根目录
for /f "tokens=*" %%i in ('git rev-parse --show-toplevel 2^>nul') do set REPO_ROOT=%%i
if "%REPO_ROOT%"=="" (
    echo [ERROR] This is not a git repository.
    pause
    exit /b 1
)

set REPO_ROOT=%REPO_ROOT:/=\%
cd /d "%REPO_ROOT%"

set LINK_PATH=.claude\skills
set TARGET_PATH=.agents\skills


:: 强制更新，删除旧的链接或文件
if exist "%LINK_PATH%" (
    echo [INFO] Detected existing file/link at %LINK_PATH%, cleaning up...
    :: 如果是Junction 或 目录 用 rmdir，如果是文件用 del
    if exist "%LINK_PATH%\" (
        rmdir "%LINK_PATH%"
    ) else (
        del /f /q "%LINK_PATH%"
    )
)
:: 创建目录联接 (/J)
echo [INFO] Creating Windows Directory Junction...
if not exist ".claude" mkdir .claude
mklink /J "%LINK_PATH%" "%TARGET_PATH%"
